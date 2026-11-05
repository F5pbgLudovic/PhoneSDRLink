package fr.f5pbg.phonesdrlink;

import java.util.Locale;

// Décodeur RTTY : recherche de la paire de tonalités (décalage 170, 200, 425, 450 ou 850 Hz) ou paire imposée, deux filtres de
// Goertzel fenêtrés, puis des décodeurs asynchrones en parallèle (débits 45,45 / 50 / 75 bauds ou un débit imposé, polarité normale /
// inversée ou imposée) ; on affiche celui dont les trames (départ, 5 bits, arrêt) sont le plus souvent valides. Alphabet Baudot
// (lettres / chiffres). Quand la paire de tonalités est trouvée, la dernière seconde de son est rejouée pour ne pas perdre le début.
final class RttyEngine extends TextEngine {
    static final int HOP = 20, WIN = 120, BUF = 8192;
    static final double[] BAUDS = { 45.45, 50.0, 75.0 };
    static final double[] SHIFTS = { 170, 200, 425, 450, 850 };
    final float[] ring = new float[BUF];
    final double[] hann = new double[WIN];
    long total;
    double fLo, fHi;                // paire de tonalités (0 = pas trouvée)
    int tick, miss, bestTick, statTick;
    boolean replay;
    Uart[] u;
    int best = -1;

    // Réglages (voir MainActivity.applyRttySettings) ; 0 = automatique
    volatile double baudSetting = 0;
    volatile int shiftSetting = 0;
    volatile int polSetting = 0;        // 0 les deux, 1 normale (mark = tonalité basse), 2 inversée
    volatile boolean uos = true;        // « unshift on space » : une espace ramène en lettres
    volatile int squelch = 15;          // les deux tonalités doivent dépasser ainsi le bruit
    volatile int freqSetting = 0;       // tonalité basse imposée (Hz)
    volatile boolean afc = true;        // la paire de tonalités suit la dérive

    static final String LTRS = "\u0000E\nA SIU\rDRJNFCKTZLWHYPQOBG\u0001MXV\u0002";
    static final String FIGS = "\u00003\n- \u000787\r$4',!:(5\")2#6019?&\u0001./;\u0002";

    final class Uart {
        final double baud, bitHops;
        final boolean reversed;
        int state;                  // 0 attente du repos (mark), 1 repos, 2 trame en cours
        double t, t0;
        int k, code;
        boolean figs;
        double score, qual, q;      // trames valides (pondérées), confiance moyenne aux instants d'échantillonnage
        StringBuilder out = new StringBuilder();
        Uart(double b, boolean rev) { baud = b; reversed = rev; bitHops = RATE / b / HOP; }

        // v > 0 : tonalité basse dominante.
        void hop(double v) {
            boolean mark = reversed ? v < 0 : v > 0;
            boolean strong = Math.abs(v) > 0.2;
            t += 1;
            if (state == 0) { if (mark && strong) state = 1; return; }
            if (state == 1) { if (!mark && strong) { state = 2; t0 = t; k = 0; code = 0; } return; }
            double tk = t0 + (k + 0.5) * bitHops;
            if (t < tk) return;
            q += Math.abs(v);
            if (k == 0) {                       // bit de départ : doit être « space »
                if (mark) { state = 1; score -= 0.5; return; }
            } else if (k <= 5) {
                if (mark) code |= 1 << (k - 1);
            } else {                            // bit d'arrêt : doit être « mark »
                state = 0;
                qual = 0.9 * qual + 0.1 * (q / 7); q = 0;
                if (mark) { score = score * 0.98 + 1; character(code); } else score = score * 0.98 - 1;
                return;
            }
            k++;
        }

        void character(int c) {
            if (c == 31) { figs = false; return; }
            if (c == 27) { figs = true; return; }
            if (c == 4 && uos) figs = false;   // « unshift on space » (comme fldigi par défaut)
            char ch = (figs ? FIGS : LTRS).charAt(c);
            if (ch == '\r' || ch == 0 || (ch < 8 && ch != '\n')) return;
            out.append(ch);
        }
    }

    RttyEngine(Listener l) {
        super(l);
        for (int i = 0; i < WIN; i++) hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * (i + 0.5) / WIN);
        resetUarts();
    }

    void resetUarts() {
        double[] bauds = baudSetting > 0 ? new double[] { baudSetting } : BAUDS;
        java.util.ArrayList<Uart> l = new java.util.ArrayList<Uart>();
        for (double b : bauds) {
            if (polSetting != 2) l.add(new Uart(b, false));
            if (polSetting != 1) l.add(new Uart(b, true));
        }
        u = l.toArray(new Uart[0]);
        best = -1;
    }

    // Appelé quand un réglage change : les décodeurs sont refaits, la paire de tonalités est cherchée de nouveau.
    void settingsChanged() {
        fLo = fHi = 0;
        miss = 0;
        resetUarts();
    }

    void feed(short[] s, int n) {
        for (int i = 0; i < n; i++) {
            ring[(int) (total % BUF)] = s[i] / 32768f;
            total++;
            if (total % HOP == 0) step();
        }
    }

    final float[] wbuf = new float[WIN];   // fenêtre réutilisée à chaque pas (évite une allocation toutes les 2,5 ms)

    float[] windowInto(long end) {
        long start = end - WIN;
        for (int i = 0; i < WIN; i++) wbuf[i] = (float) (ring[(int) (((start + i) % BUF + BUF) % BUF)] * hann[i]);
        return wbuf;
    }

    float[] windowAt(long end, int len) {
        float[] w = new float[len];
        long start = end - len;
        for (int i = 0; i < len; i++) w[i] = ring[(int) (((start + i) % BUF + BUF) % BUF)];
        return w;
    }

    // Toutes les 0,25 s : recherche des deux tonalités sur la dernière demi-seconde.
    void findTones() {
        if (total < BUF) return;
        if (fLo != 0 && !afc && freqSetting == 0) return;   // sans AFC la paire trouvée reste en place
        float[] w = windowAt(total, 4096);
        int n = 97;
        double[] e = new double[n], sorted = new double[n];
        for (int i = 0; i < n; i++) { e[i] = goertzel(w, 0, 4096, 500 + 25.0 * i); sorted[i] = e[i]; }
        java.util.Arrays.sort(sorted);
        double med = sorted[n / 2] + 1e-12;
        if (freqSetting > 0) {   // tonalité basse imposée : on cherche seulement la seconde
            double lo = freqSetting, hi = lo + (shiftSetting > 0 ? shiftSetting : 170), bestE = 0;
            if (shiftSetting == 0) for (double s : SHIFTS) { double x = goertzel(w, 0, 4096, lo + s); if (x > bestE) { bestE = x; hi = lo + s; } }
            if (fLo != lo || fHi != hi) { fLo = lo; fHi = hi; replay = true; resetUarts(); }
            miss = 0;
            status();
            return;
        }
        double bestE = 0;
        int bi = -1, bs = -1;
        for (int i = 0; i < n; i++) for (int si = 0; si < SHIFTS.length; si++) {
            if (shiftSetting > 0 && Math.abs(SHIFTS[si] - shiftSetting) > 1e-6) continue;
            int j = i + (int) Math.round(SHIFTS[si] / 25.0);
            if (j >= n) continue;
            double m = Math.min(e[i], e[j]);
            if (m > bestE) { bestE = m; bi = i; bs = si; }
        }
        if (shiftSetting > 0 && bi < 0) {   // décalage imposé qui n'est pas dans la liste : on le traite comme une valeur libre
            for (int i = 0; i < n; i++) {
                int j = i + (int) Math.round(shiftSetting / 25.0);
                if (j >= n) continue;
                double m = Math.min(e[i], e[j]);
                if (m > bestE) { bestE = m; bi = i; bs = -1; }
            }
        }
        if (bi >= 0 && bestE > squelch * med) {
            double sh = bs >= 0 ? SHIFTS[bs] : shiftSetting;
            double a = refine(w, 500 + 25.0 * bi), b = refine(w, 500 + 25.0 * bi + sh);
            if (fLo == 0 || Math.abs(a - fLo) > 40 || Math.abs(b - fHi) > 40) { fLo = a; fHi = b; replay = true; resetUarts(); }
            else { fLo = 0.8 * fLo + 0.2 * a; fHi = 0.8 * fHi + 0.2 * b; }
            miss = 0;
            status();
        } else if (++miss > 80) { fLo = fHi = 0; }
    }

    void status() {
        if (ls == null || fLo == 0) return;
        if (best >= 0) ls.onStatus(String.format(Locale.US, "RTTY : %.0f / %.0f Hz (%.0f Hz), %.2f bauds%s", fLo, fHi, fHi - fLo, u[best].baud, u[best].reversed ? ", " + Lang.T("inversé", "reversed") : ""));
        else ls.onStatus(String.format(Locale.US, "RTTY : %.0f / %.0f Hz (%.0f Hz)", fLo, fHi, fHi - fLo));
    }

    double refine(float[] w, double f) {
        double bb = 0, bf = f;
        for (double x = f - 12; x <= f + 12; x += 4) { double e = goertzel(w, 0, 4096, x); if (e > bb) { bb = e; bf = x; } }
        return bf;
    }

    void step() {
        if (++tick >= 32) { tick = 0; findTones(); }
        if (++bestTick >= 50) { bestTick = 0; selectBest(); if (++statTick >= 8) { statTick = 0; status(); } }
        if (fLo == 0) return;
        if (replay) {   // paire nouvellement trouvée : on traite de nouveau la dernière seconde
            replay = false;
            for (long e = total - 7000; e < total; e += HOP) process(e);
            selectBest();
        }
        process(total);
    }

    void process(long end) {
        float[] w = windowInto(end);
        double a = goertzel(w, 0, WIN, fLo), b = goertzel(w, 0, WIN, fHi);
        double v = (a - b) / (a + b + 1e-12);
        for (Uart x : u) x.hop(v);
        flushBest();
    }

    void selectBest() {
        int bi = -1;
        double bs = 0;     // au moins six trames valides ; à score voisin, la confiance départage (le bon débit échantillonne au milieu des bits)
        for (int i = 0; i < u.length; i++) if (u[i].score > 6.0) { double s = u[i].score + 8 * u[i].qual; if (s > bs) { bs = s; bi = i; } }
        if (bi != best && (best < 0 || bi < 0 || u[bi].score + 8 * u[bi].qual > u[best].score + 8 * u[best].qual + 2)) best = bi;
    }

    // Les caractères du décodeur retenu sont émis ; ceux des autres sont jetés. Avant d'en retenir un, on garde les caractères en attente.
    void flushBest() {
        if (best < 0) return;
        StringBuilder o = u[best].out;
        if (o.length() > 0 && ls != null) { ls.onText(o.toString()); o.setLength(0); }
        for (int i = 0; i < u.length; i++) if (i != best && u[i].out.length() > 200) u[i].out.setLength(0);
    }
}
