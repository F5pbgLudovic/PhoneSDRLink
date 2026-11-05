package fr.f5pbg.phonesdrlink;

import java.util.Locale;

// Décodeur CW : suivi de la tonalité (200 à 1200 Hz) ou tonalité imposée, enveloppe par Goertzel (largeur de filtre réglable),
// seuil adaptatif réglable, vitesse apprise ou imposée, seuils de fin de lettre et de fin de mot réglables.
// Quand la tonalité est trouvée (ou change), la dernière seconde de son est rejouée pour ne pas perdre le début.
final class CwEngine extends TextEngine {
    static final int HOP = 64;                      // 8 ms de pas
    static final int BUF = 8192;                    // 1 s de mémoire
    final float[] ring = new float[BUF];
    long total;
    double tone = 0;                                // Hz (0 = pas encore trouvée)
    double peak = 1e-9, floor = -1, dot = 80, dash = 240;   // enveloppe, vitesse (ms)
    boolean on, spaced, replay;
    double runMs;                                   // durée de l'état courant
    String sym = "";
    int sinceTone, charsOut, toneTick;

    // Réglages (modifiables pendant le décodage, voir MainActivity.applyCwSettings)
    volatile boolean autoTone = true, autoSpeed = true;
    volatile int manualTone = 700;      // Hz
    volatile int bandwidth = 80;        // Hz : largeur du filtre (fenêtre de Goertzel plus longue = filtre plus étroit)
    volatile int manualWpm = 18;
    volatile int threshold = 55;        // % entre le bruit et la crête
    volatile double charGap = 2.0;      // fin de lettre : silence supérieur à ce nombre de points
    volatile double wordGap = 5.0;      // fin de mot : silence supérieur à ce nombre de points
    volatile int squelch = 5;           // la crête doit dépasser ainsi le bruit pour décoder
    volatile boolean autoBw = true;     // filtre adapté à la vitesse (environ 2 / durée d'un point, de 25 à 250 Hz)
    volatile boolean afc = true;        // la tonalité suit la dérive du signal
    volatile int agc = 1;               // 0 lent, 1 moyen, 2 rapide : rapidité de suivi des évanouissements

    static final String[] MORSE = {
        ".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.", "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--..",
        "-----", ".----", "..---", "...--", "....-", ".....", "-....", "--...", "---..", "----.",
        ".-.-.-", "--..--", "..--..", "-..-.", "-...-", ".-.-.", "-.--.", "-.--.-", ".----.", "---...", "-....-", ".-..-.", "...-.-", "...-..-", ".--.-." };
    static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,?/=+(){}'':-\"*$@";

    CwEngine(Listener l) { super(l); }

    int win() {
        double bw = autoBw && calibrated ? Math.max(30, Math.min(300, 3000.0 / Math.max(20, dot))) : bandwidth;
        return Math.max(24, Math.min(800, (int) (1.2 * RATE / Math.max(20, bw))));
    }

    void feed(short[] s, int n) {
        for (int i = 0; i < n; i++) {
            ring[(int) (total % BUF)] = s[i] / 32768f;
            total++;
            if (total % HOP == 0) step();
        }
    }

    final float[] wbuf = new float[800];   // fenêtre réutilisée à chaque pas (évite une allocation toutes les 8 ms)

    float[] windowInto(long end, int len) {
        long start = end - len;
        for (int i = 0; i < len; i++) wbuf[i] = ring[(int) (((start + i) % BUF + BUF) % BUF)];
        return wbuf;
    }

    float[] windowAt(long end, int len) {
        float[] w = new float[len];
        long start = end - len;
        for (int i = 0; i < len; i++) w[i] = ring[(int) (((start + i) % BUF + BUF) % BUF)];
        return w;
    }

    void newTone(double f) {
        tone = f; peak = 1e-9; floor = -1; on = false; runMs = 0; sym = ""; replay = true; warm = 12; pending = 0;
        calibrated = false; rec.clear(); ons.clear();
        if (!autoSpeed) setManualSpeed();
    }

    void setManualSpeed() { dot = 1200.0 / Math.max(5, manualWpm); dash = 3 * dot; calibrated = true; }

    // Recherche de la tonalité : le pic le plus net entre 200 et 1200 Hz (toutes les 0,25 s). Il doit dépasser de beaucoup le bruit
    // voisin (±200 Hz) : avec un filtre étroit du WebSDR, le bruit seul fait aussi un « pic » dans la bande passante.
    void findTone() {
        if (!autoTone) {
            if (tone != manualTone) newTone(manualTone);
            return;
        }
        if (total < BUF) return;
        float[] w = windowAt(total, 4096);
        int n = 41;
        double[] e = new double[n];
        int bi = 0;
        for (int i = 0; i < n; i++) { e[i] = goertzel(w, 0, 4096, 200 + 25.0 * i); if (e[i] > e[bi]) bi = i; }
        double side = 0;
        int cnt = 0;
        for (int d = 2; d <= 8; d++) {
            if (bi - d >= 0) { side += e[bi - d]; cnt++; }
            if (bi + d < n) { side += e[bi + d]; cnt++; }
        }
        side = cnt > 0 ? side / cnt : 1e9;
        double best = e[bi], bf = 200 + 25.0 * bi;
        if (best > 25 * side && best > 1e-4) {
            double bb = best, ff = bf;
            for (double f = bf - 24; f <= bf + 24; f += 4) { double x = goertzel(w, 0, 4096, f); if (x > bb) { bb = x; ff = f; } }
            if (tone == 0 || Math.abs(ff - tone) > 30) newTone(ff);
            else tone = 0.7 * tone + 0.3 * ff;
            sinceTone = 0;
            if (ls != null) ls.onStatus(String.format(Locale.US, "CW : %.0f Hz, %.0f WPM", tone, 1200.0 / dot));
        } else if (++sinceTone > 24) tone = 0;
    }

    void step() {
        if (++toneTick >= 32) {
            toneTick = 0;
            findTone();
            if (!autoTone && ls != null && tone != 0) ls.onStatus(String.format(Locale.US, "CW : %.0f Hz, %.0f WPM", tone, 1200.0 / dot));
        }
        if (tone == 0) { runMs = 0; return; }
        if (replay) {   // tonalité nouvellement trouvée : on traite de nouveau la dernière seconde
            replay = false;
            for (long e = total - 8000; e < total; e += HOP) process(e);
        }
        process(total);
    }

    int warm, pending, afcTick;

    void process(long end) {
        int win = win();
        float[] w = windowInto(end, win);
        double e = Math.sqrt(goertzel(w, 0, win, tone)) / win;   // amplitude de la tonalité
        if (floor < 0) { floor = e; peak = e; }
        peak = Math.max(e, peak * (agc == 0 ? 0.9998 : agc == 1 ? 0.9995 : 0.998));
        if (!on) floor += 0.02 * (Math.min(e, floor * 3) - floor);   // moyenne lente du bruit, hors signal
        if (afc && on && warm == 0 && (++afcTick & 3) == 0) {   // AFC : on compare l'énergie juste au-dessus et juste au-dessous de la tonalité
            double eL = goertzel(w, 0, win, tone - 4), eH = goertzel(w, 0, win, tone + 4);
            if (eH > 1.15 * eL) tone = Math.min(1200, tone + 0.5);
            else if (eL > 1.15 * eH) tone = Math.max(200, tone - 0.5);
        }
        double t = threshold / 100.0, range = peak - floor;
        boolean sig = (peak > squelch * floor) && (on ? e > floor + (t - 0.1) * range : e > floor + t * range);
        double ms = HOP * 1000.0 / RATE;
        if (warm > 0) { warm--; on = sig; runMs = 0; pending = 0; return; }   // mise en route : on apprend seulement les niveaux
        if (sig == on) { pending = 0; runMs += ms; if (!on) gapCheck(); return; }
        if (++pending < 2) { runMs += ms; return; }   // un changement d'état doit durer deux pas (16 ms)
        if (on) element(runMs); else gap(runMs);
        on = sig;
        runMs = pending * ms;
        pending = 0;
    }

    // Au départ (vitesse automatique), la vitesse n'est pas connue : on enregistre les huit premiers éléments, on en tire la durée
    // d'un point (quart inférieur) et d'un trait, puis on décode de nouveau ce qui a été enregistré.
    boolean calibrated;
    final java.util.ArrayList<double[]> rec = new java.util.ArrayList<double[]>();
    final java.util.ArrayList<Double> ons = new java.util.ArrayList<Double>();

    void calibrate() {
        java.util.ArrayList<Double> s = new java.util.ArrayList<Double>(ons);
        java.util.Collections.sort(s);
        dot = s.get(s.size() / 4);
        double sum = 0;
        int n = 0;
        for (double d : s) if (d > 2.0 * dot) { sum += d; n++; }
        dash = n > 0 ? sum / n : 3 * dot;
        if (dash < 2.2 * dot || dash > 4.5 * dot) dash = 3 * dot;
        calibrated = true;
        java.util.ArrayList<double[]> r = new java.util.ArrayList<double[]>(rec);
        rec.clear(); ons.clear();
        for (double[] ev : r) { if (ev[0] == 1) element(ev[1]); else gap(ev[1]); }
    }

    static boolean debug;

    void element(double d) {
        if (debug) System.out.println(String.format("  on %.0f ms (dot %.0f dash %.0f)", d, dot, dash));
        if (d < 22) return;   // parasite (un point à 50 mots par minute dure 24 ms)
        if (!calibrated) { rec.add(new double[] { 1, d }); ons.add(d); if (ons.size() >= 8) calibrate(); return; }
        double thr = Math.sqrt(dot * dash);
        if (d < thr) { sym += "."; if (autoSpeed) dot = 0.8 * dot + 0.2 * Math.max(25, d); }
        else { sym += "-"; if (autoSpeed) dash = 0.8 * dash + 0.2 * Math.min(1200, d); }
        if (autoSpeed) {
            if (dash < 2.2 * dot) dash = 3 * dot;   // garde-fous : le trait vaut environ trois points
            if (dash > 4.5 * dot) dash = 3 * dot;
        }
    }

    void gap(double d) {
        if (!calibrated) { rec.add(new double[] { 0, d }); return; }
        if (debug) System.out.println(String.format("  gap %.0f ms", d));
        if (d > charGap * dot) flush();
        if (d > wordGap * dot && !spaced && charsOut > 0) emit(" ");   // espace entre deux mots (cas des éléments rejoués)
        spaced = false;
    }

    // Espace long sans signal : on termine la lettre, puis on met un espace entre les mots.
    void gapCheck() {
        if (!calibrated) return;
        if (!sym.isEmpty() && runMs > charGap * dot) flush();
        if (!spaced && charsOut > 0 && runMs > wordGap * dot) { emit(" "); spaced = true; }
    }

    void flush() {
        if (sym.isEmpty()) return;
        int k = -1;
        for (int i = 0; i < MORSE.length; i++) if (MORSE[i].equals(sym)) { k = i; break; }
        emit(k >= 0 ? String.valueOf(CHARS.charAt(k)) : "*");
        sym = "";
        spaced = false;
    }

    void emit(String s) {
        charsOut++;
        if (ls != null) ls.onText(s);
    }
}
