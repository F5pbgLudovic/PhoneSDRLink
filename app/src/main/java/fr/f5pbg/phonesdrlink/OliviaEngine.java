package fr.f5pbg.phonesdrlink;

import java.util.Locale;

// Décodeur Olivia et Contestia (MFSK à tons multiples et correction d'erreurs) : le calcul est fait par la bibliothèque MFSK de Pawel Jalocha
// (code natif, la même que celle de fldigi). Ici : réglages, recherche automatique du centre du signal dans le spectre (SpecFind), affichage du texte
// et de l'état (synchro, rapport signal/bruit, décalage de fréquence). Réception seule.
final class OliviaEngine extends TextEngine {
    final boolean contestia;

    // Réglages (voir MainActivity.applyOliviaSettings)
    volatile int tones = 32, bw = 1000;     // nombre de tons, bande en Hz
    volatile int margin = 8, integ = 4;     // marge de recherche de fréquence (en tons) et durée d'intégration de la synchro
    volatile int thr10 = 30;                // seuil de synchro (rapport signal/bruit) en dixièmes
    volatile int centerSetting = 0;         // 0 = centre trouvé automatiquement, sinon centre imposé (Hz)
    volatile boolean reverse;               // signal inversé (bande latérale opposée)

    long h;
    int centre = 1500, cand, candHits;
    final SpecFind sf = new SpecFind();
    final byte[] out = new byte[1024];
    final double[] st = new double[2];
    long since, statAt;
    boolean locked;
    String lastStatus = "";
    int curTones, curBw, dbgN;

    OliviaEngine(Listener l, boolean contestia) {
        super(l);
        this.contestia = contestia;
        status(Lang.T("recherche d'un signal…", "searching for a signal…"));
    }

    String name() { return (contestia ? "Contestia " : "Olivia ") + curTones + "/" + curBw; }

    void status(String s) {
        if (!s.equals(lastStatus)) { lastStatus = s; ls.onStatus(s); }
    }

    // Appelé dans le fil du décodeur quand un réglage change : le décodeur natif est refait.
    void settingsChanged() {
        if (h != 0) { Decoders.oliviaFree(h); h = 0; }
        locked = false;
        cand = 0; candHits = 0;
        build();
    }

    void build() {
        if (centerSetting > 0) centre = centerSetting;
        curTones = tones; curBw = bw;
        h = Decoders.oliviaNew(tones, bw, margin, integ, contestia ? 1 : 0, centre, reverse ? 1 : 0, thr10);
        if (h == 0) status(Lang.T("Olivia : réglages refusés", "Olivia: settings refused"));
        else status(name() + " : " + String.format(Locale.US, Lang.T("à l'écoute à %d Hz", "listening at %d Hz"), centre));
    }

    @Override void onClose() {
        if (h != 0) { Decoders.oliviaFree(h); h = 0; }
    }

    void feed(short[] s, int n) {
        if (!Decoders.loaded) return;
        if (h == 0) { build(); if (h == 0) return; }
        if (centerSetting > 0 && centerSetting != centre) { centre = centerSetting; Decoders.oliviaCenter(h, centre); }
        sf.add(s, n);
        int k = Decoders.oliviaProcess(h, s, n, out, st);
        if (k > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < k; i++) {
                int c = out[i] & 255;
                if (c == 10 || c == 13) { if (c == 10) sb.append('\n'); }
                else if (c >= 32 && c < 127) sb.append((char) c);
            }
            if (sb.length() > 0) ls.onText(sb.toString());
        }
        locked = st[0] >= thr10 / 10.0;
        since += n;
        if (since >= 4000) { since = 0; autoCentre(); }
        if (statAt++ % 4 == 0) report();
    }

    // Sans synchro et en mode automatique : on cherche dans le spectre la zone de la bande du signal ; on ne bouge que si la même zone
    // ressort deux fois de suite (le décodeur natif repart de zéro à chaque changement de centre).
    void autoCentre() {
        if (centerSetting > 0 || locked) { cand = 0; candHits = 0; return; }
        double[] b = sf.best(bw);
        if (b[0] <= 0 || b[1] < 1.8) { cand = 0; candHits = 0; return; }
        int c = (int) Math.round(b[0] / 5) * 5;
        if (Math.abs(c - centre) <= Math.max(5, bw / (double) tones)) { cand = 0; candHits = 0; return; }   // déjà dans la tolérance du décodeur
        if (cand != 0 && Math.abs(c - cand) <= Math.max(10, bw / (double) tones)) candHits++; else { cand = c; candHits = 1; }
        if (candHits >= 2) {
            centre = c;
            Decoders.oliviaCenter(h, centre);
            cand = 0; candHits = 0;
        }
    }

    void report() {
        if (HellEngine.debug && ++dbgN % 10 == 0) android.util.Log.i("PSDR", "OLI " + name() + " centre=" + centre + " snr=" + String.format(Locale.US, "%.2f", st[0]) + " off=" + String.format(Locale.US, "%.1f", st[1]) + " locked=" + locked);
        if (locked) {
            double db = 10 * Math.log10(Math.max(st[0], 1e-6));
            status(name() + " : " + String.format(Locale.US, Lang.T("synchro, S/B %.0f dB, écart %+.0f Hz, centre %d Hz", "in sync, S/N %.0f dB, offset %+.0f Hz, centre %d Hz"), db, st[1], centre));
        } else {
            status(name() + " : " + String.format(Locale.US, Lang.T("recherche d'un signal… (centre %d Hz)", "searching for a signal… (centre %d Hz)"), centre));
        }
    }

    /** Débit approximatif en caractères par seconde (64 symboles par bloc FEC, log2(tons) caractères par bloc). */
    static double charsPerSecond(int tones, int bw, boolean contestia) {
        int bits = (int) Math.round(Math.log(tones) / Math.log(2));
        double baud = bw / (double) tones;
        return bits * baud / (contestia ? 32.0 : 64.0);
    }
}
