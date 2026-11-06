package fr.f5pbg.phonesdrlink;

import java.util.Locale;

// Décodeur SSTV à détection automatique du mode (code VIS) : Robot 36 et 72, Martin M1 et M2, Scottie S1, S2 et DX, PD50, PD90,
// PD120, PD160, PD180, PD240, PD290 et Wraase SC2-180. Le son (8 kHz) est ramené en bande de base autour de 1700 Hz, filtré (FIR à
// 31 coefficients), et la fréquence instantanée vient de la différence de phase entre deux échantillons ; on garde la phase cumulée
// dans un anneau de 16 s, ce qui donne la fréquence moyenne de n'importe quel intervalle (un pixel) par une simple différence.
// 1) Recherche du code VIS : premier 1900 Hz (au moins 120 ms), impulsion de 10 ms à 1200 Hz, second 1900 Hz (au moins 200 ms), puis
//    10 bits de 30 ms (départ, 7 bits de données, parité paire, arrêt) lus au milieu de chaque bit. Le décalage d'accord est mesuré
//    sur le second 1900 Hz.
// 2) Image : chaque bloc (ligne, ou paire de lignes en PD) commence par une impulsion de synchro 1200 Hz retrouvée dans une fenêtre
//    autour de l'instant attendu (filtre adapté, dernier maximum) ; la période réelle des blocs est estimée sur toute l'image, ce qui
//    corrige la dérive d'horloge (inclinaison). Les pixels sont les moyennes de fréquence (1500 Hz = noir, 2300 Hz = blanc).
final class SstvEngine extends TextEngine {
    interface Images {
        void begin(String name, int w, int h, int[] px);   // une image commence (le tableau se remplit au fil des blocs)
        void rows(int done);                                // nombre de lignes terminées
        void end(boolean complete, int rows);               // fin : image complète, ou signal perdu
    }

    static final int RING = 1 << 17, MASK = RING - 1, FIRN = 31;
    static final double CENTER = 1700.0, SPMS = 8.0;      // 8 échantillons par ms
    static final int RGB = 0, R36 = 1, R72 = 2, PD = 3, GRAY = 4;
    static final int R = 0, G = 1, B = 2, Y = 3, CR = 4, CB = 5, Y1 = 6;

    static final class Mode {
        final String name; final int vis, w, kind;
        int h, rep = 1;      // rep : chaque ligne reçue est répétée rep fois (Martin M3 et M4, Scottie S3 et S4, Robot 12 et 8 N&B : moitié moins de lignes)
        final double syncMs, periodMs, scanMs;
        double[] st, len; int[] role;
        boolean scottie;
        Mode(String n, int v, int w, int h, int kind, double sync, double period, double scan) { name = n; vis = v; this.w = w; this.h = h; this.kind = kind; syncMs = sync; periodMs = period; scanMs = scan; }
        Mode ch(double[] s, double[] l, int[] r) { st = s; len = l; role = r; return this; }
        Mode twice() { rep = 2; return this; }
    }

    static final Mode[] MODES = build();

    static Mode[] build() {
        java.util.ArrayList<Mode> l = new java.util.ArrayList<Mode>();
        // Martin : synchro 4,862 ms, 0,572 ms de palier, puis G, B, R séparés de 0,572 ms
        l.add(martin("Martin M1", 44, 146.432));
        l.add(martin("Martin M2", 40, 73.216));
        // Scottie : séparateur 1,5 ms, G, séparateur 1,5 ms, B, synchro 9 ms, palier 1,5 ms, R (instants relatifs au début de la synchro)
        l.add(scottie("Scottie S1", 60, 138.240));
        l.add(scottie("Scottie S2", 56, 88.064));
        l.add(scottie("Scottie DX", 76, 345.6));
        // Wraase SC2-180 : synchro 5,5225 ms, palier 0,5 ms, R, G, B de 235 ms sans séparateur
        l.add(new Mode("Wraase SC2-180", 55, 320, 256, RGB, 5.5225, 5.5225 + 0.5 + 705, 235).ch(
            new double[] { 6.0225 + 470, 6.0225, 6.0225 + 235 }, new double[] { 235, 235, 235 }, new int[] { B, R, G }));
        // Robot : synchro 9 ms, palier 3 ms, Y ; en 36 : séparateur 4,5 ms + palier 1,5 ms + chrominance 44 ms (R-Y ou B-Y une ligne sur deux)
        l.add(new Mode("Robot 36", 8, 320, 240, R36, 9, 150, 88).ch(new double[] { 12, 106 }, new double[] { 88, 44 }, new int[] { Y, CR }));
        l.add(new Mode("Robot 72", 12, 320, 240, R72, 9, 300, 138).ch(new double[] { 12, 156, 231 }, new double[] { 138, 69, 69 }, new int[] { Y, CR, CB }));
        l.add(pd("PD50", 93, 320, 256, 91.520));
        l.add(pd("PD90", 99, 320, 256, 170.240));
        l.add(pd("PD120", 95, 640, 496, 121.600));
        l.add(pd("PD160", 98, 512, 400, 195.584));
        l.add(pd("PD180", 96, 640, 496, 183.040));
        l.add(pd("PD240", 97, 640, 496, 244.480));
        l.add(pd("PD290", 94, 800, 616, 228.800));
        // Modes ajoutés ensuite (tableau de slowrx et de l'encodeur libsstvenc ; S3 et S4 d'après la famille Scottie, non vérifiés sur un vrai signal)
        l.add(martin("Martin M3", 36, 146.432).twice());
        l.add(martin("Martin M4", 32, 73.216).twice());
        l.add(scottie("Scottie S3", 52, 138.240).twice());
        l.add(scottie("Scottie S4", 48, 88.064).twice());
        double w1 = 320 * 0.489039081;
        l.add(new Mode("Wraase SC2-120", 63, 320, 256, RGB, 5.5225, 5.5225 + 0.5 + 3 * w1, w1).ch(
            new double[] { 6.0225 + 2 * w1, 6.0225, 6.0225 + w1 }, new double[] { w1, w1, w1 }, new int[] { B, R, G }));
        l.add(pasokon("Pasokon P3", 113, 5.208, 1.042, 0.2083));
        l.add(pasokon("Pasokon P5", 114, 7.813, 1.563, 0.3125));
        l.add(pasokon("Pasokon P7", 115, 10.417, 2.083, 0.4167));
        // Robot noir et blanc : synchro 7 ms puis les pixels (pas d'intervalle)
        l.add(new Mode("Robot 24 BW", 10, 320, 240, GRAY, 7, 100, 93.12).ch(new double[] { 7 }, new double[] { 93.12 }, new int[] { Y }));
        l.add(new Mode("Robot 12 BW", 6, 320, 240, GRAY, 7, 100, 93.12).ch(new double[] { 7 }, new double[] { 93.12 }, new int[] { Y }).twice());
        l.add(new Mode("Robot 8 BW", 2, 320, 240, GRAY, 7, 66.9, 59.9).ch(new double[] { 7 }, new double[] { 59.9 }, new int[] { Y }).twice());
        return l.toArray(new Mode[0]);
    }

    static Mode martin(String n, int vis, double scan) {
        double s0 = 4.862 + 0.572;
        return new Mode(n, vis, 320, 256, RGB, 4.862, s0 + 3 * (scan + 0.572), scan).ch(
            new double[] { s0 + scan + 0.572 + scan + 0.572, s0, s0 + scan + 0.572 }, new double[] { scan, scan, scan }, new int[] { R, G, B });
    }

    static Mode scottie(String n, int vis, double scan) {
        Mode m = new Mode(n, vis, 320, 256, RGB, 9, 3 * scan + 13.5, scan).ch(
            new double[] { 10.5, -scan, -2 * scan - 1.5 }, new double[] { scan, scan, scan }, new int[] { R, B, G });
        m.scottie = true;
        return m;
    }

    // Pasokon : synchro, intervalle, puis R, G, B de 640 pixels séparés par un intervalle (même durée que celui qui suit la synchro)
    static Mode pasokon(String n, int vis, double sync, double gap, double px) {
        double scan = 640 * px;
        return new Mode(n, vis, 640, 496, RGB, sync, sync + gap + 3 * (scan + gap), scan).ch(
            new double[] { sync + gap, sync + gap + scan + gap, sync + gap + 2 * (scan + gap) }, new double[] { scan, scan, scan }, new int[] { R, G, B });
    }

    static Mode pd(String n, int vis, int w, int h, double scan) {
        return new Mode(n, vis, w, h, PD, 20, 22.08 + 4 * scan, scan).ch(
            new double[] { 22.08, 22.08 + scan, 22.08 + 2 * scan, 22.08 + 3 * scan }, new double[] { scan, scan, scan, scan }, new int[] { Y, CR, CB, Y1 });
    }

    static Mode byVis(int v) { for (Mode m : MODES) if (m.vis == v) return m; return null; }

    // Sensibilité (0 = d'origine … 4 = signaux très faibles) : élargit les tolérances de fréquence et de synchro, allonge la moyenne de fréquence
    // et la patience avant d'abandonner une image ; au prix de plus de fausses détections.
    volatile double sens = 1;   // réglage 1 à 10 de l'application -> 0 à 4 (voir setLevels)
    volatile int lossMs = 12000;   // temps pendant lequel on continue une image sans synchro avant d'y renoncer
    int tv1() { return (int) (130 + 25 * sens); }
    int tv2() { return (int) (150 + 25 * sens); }
    int tb() { return (int) (110 + 15 * sens); }
    int tp() { return (int) (140 + 15 * sens); }
    int ts() { return (int) (160 + 15 * sens); }
    double sf() { return 0.5 - 0.06 * sens; }
    int win() { return 16 + (int) (2 * sens); }

    static final int[] LOSS_S = { 3, 5, 8, 12, 18, 25, 35, 50, 75, 120 };   // « perte data » 1 à 10 : secondes

    /** Réglages de l'application : sensibilité 1 à 10 et perte data 1 à 10. */
    void setLevels(int sensitivity, int loss) {
        sens = (Math.max(1, Math.min(10, sensitivity)) - 1) * 4.0 / 9.0;
        lossMs = LOSS_S[Math.max(1, Math.min(10, loss)) - 1] * 1000;
    }

    final Images im;
    SstvEngine(Listener l, Images im) { super(l); this.im = im; }

    // ---- démodulation
    final double[] cum = new double[RING];
    final double[] mr = new double[32], mi = new double[32];
    final double[] fir = new double[FIRN];
    double ph, pr, pi, cumv;
    long n;                 // nombre d'échantillons reçus
    int mp;
    boolean init;

    void initFir() {
        double fc = 1000.0 / RATE, sum = 0;
        for (int i = 0; i < FIRN; i++) {
            double x = i - (FIRN - 1) / 2.0;
            double s = x == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * x) / (Math.PI * x);
            fir[i] = s * (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (FIRN - 1)));
            sum += fir[i];
        }
        for (int i = 0; i < FIRN; i++) fir[i] /= sum;
        init = true;
    }

    double cumAt(double x) {
        long i = (long) Math.floor(x);
        double f = x - i;
        if (i + 1 >= n) { i = n - 2; f = 1; }
        return cum[(int) (i & MASK)] * (1 - f) + cum[(int) ((i + 1) & MASK)] * f;
    }

    // Fréquence moyenne (Hz) entre les échantillons a et b (brute, sans correction d'accord).
    double freqAvg(double a, double b) {
        if (b - a < 1e-6) b = a + 1;
        return (cumAt(b) - cumAt(a)) / (b - a) * RATE / (2 * Math.PI) + CENTER;
    }

    void feed(short[] s, int cnt) {
        if (!init) initFir();
        for (int k = 0; k < cnt; k++) {
            double x = s[k] / 32768.0;
            ph += 2 * Math.PI * CENTER / RATE;
            if (ph > 2 * Math.PI) ph -= 2 * Math.PI;
            mr[mp] = x * Math.cos(ph);
            mi[mp] = -x * Math.sin(ph);
            double yr = 0, yi = 0;
            for (int j = 0; j < FIRN; j++) { int q = (mp - j) & 31; yr += fir[j] * mr[q]; yi += fir[j] * mi[q]; }
            mp = (mp + 1) & 31;
            double inc = Math.atan2(yi * pr - yr * pi, yr * pr + yi * pi);   // arg(y · conj(y précédent))
            pr = yr; pi = yi;
            cumv += inc;
            cum[(int) (n & MASK)] = cumv;
            n++;
            if (img != null) imgStep();
            if ((n & 7) == 0 && n > 400) { visStep(); pulseStep(); }   // le code VIS est cherché aussi pendant une image : une nouvelle image qui commence interrompt la précédente
        }
    }

    // ---- code VIS
    int vs, lead, miss, brk, lead2;
    long tStart;
    double ofs;             // décalage d'accord (Hz) : fréquence mesurée − fréquence nominale

    static boolean near(double f, double c, double w) { return Math.abs(f - c) < w; }

    void visReset() { vs = 0; lead = 0; miss = 0; brk = 0; lead2 = 0; }

    void visStep() {
        double f = freqAvg(n - win(), n - 1);
        switch (vs) {
            case 0:
                if (near(f, 1900, tv1())) { lead++; miss = 0; }
                else if (lead >= 120 && f < 1500) { vs = 1; brk = 1; miss = 0; }
                else if (++miss > 2 + sens) { lead = 0; miss = 0; }
                break;
            case 1:
                if (near(f, 1200, tv2())) { brk++; miss = 0; }
                else if (brk >= 3 && brk <= 22 && near(f, 1900, tv2())) { vs = 2; lead2 = 1; miss = 0; }
                else if (++miss > 2 + sens) visReset();
                break;
            case 2:
                if (near(f, 1900, tv2())) { if (++lead2 > 700) visReset(); miss = 0; }   // un début de code VIS dure 300 ms : au-delà, ce n est pas un VIS
                else if (++miss > 2 + sens && f < 1550) {
                    if (lead2 >= 200) {
                        tStart = n - 8L * miss - 8;
                        double lf = freqAvg(tStart - 150 * SPMS, tStart - 30 * SPMS);
                        ofs = Math.abs(lf - 1900) < 120 ? lf - 1900 : 0;
                        vs = 3;
                    } else visReset();
                } else if (miss > 8 + 2 * sens) visReset();
                break;
            case 3:
                if (n >= tStart + (long) (300 * SPMS) + 24) visDecode();
                break;
        }
    }

    void visDecode() {
        double[] bit = new double[10];
        for (int j = 0; j < 10; j++) bit[j] = freqAvg(tStart + (30 * j + 8) * SPMS, tStart + (30 * j + 22) * SPMS) - ofs;
        boolean ok = near(bit[0], 1200, tb());
        int code = 0, ones = 0;
        for (int j = 1; j <= 8; j++) {
            if (!(near(bit[j], 1100, tb()) || near(bit[j], 1300, tb()))) ok = false;
            int b = bit[j] < 1200 ? 1 : 0;
            ones += b;
            if (j <= 7) code |= b << (j - 1);
        }
        if ((ones & 1) != 0) ok = false;
        Mode m = ok ? byVis(code) : null;
        visReset();
        if (m == null) { if (ok) ls.onStatus("SSTV : " + String.format(Locale.US, Lang.T("mode non pris en charge (VIS %d)", "unsupported mode (VIS %d)"), code)); return; }
        startImage(m, tStart + (long) (300 * SPMS));
    }

    // ---- image en cours de route (sans code VIS) : les impulsions de synchro à 1200 Hz sont notées (durée et instant) ; une suite de cinq
    // impulsions au rythme exact d'un mode (ligne, ou paire de lignes en PD) et de la bonne durée donne le mode, et la réception commence
    // à cette ligne (le haut de l'image manque : la première ligne reçue devient la première ligne de l'image).
    static final int NP = 48;
    final long[] pT = new long[NP];
    final int[] pL = new int[NP];
    final double[] pF = new double[NP];
    int np, pRun;
    long pStart;
    double pSum;

    void pulseStep() {
        double f = freqAvg(n - win(), n - 1);
        if (near(f, 1200, tp())) {
            if (pRun == 0) { pStart = n - 8; pSum = 0; }
            pRun++;
            pSum += f;
        } else if (pRun > 0) {
            int effLen = pRun + win() / 8 - 2;   // la moyenne de fréquence raccourcit l impulsion vue (fenêtre plus longue aux hauts niveaux de sensibilité)
            if (effLen >= 3 && effLen <= 26) {
                if (np == NP) { System.arraycopy(pT, 1, pT, 0, NP - 1); System.arraycopy(pL, 1, pL, 0, NP - 1); System.arraycopy(pF, 1, pF, 0, NP - 1); np--; }
                pT[np] = pStart; pL[np] = effLen; pF[np] = pSum / pRun; np++;
            }
            pRun = 0;
        }
        if ((n & 2047) == 0 && np >= 5 && vs != 3 && (img == null || misses >= 4)) blindStep();   // au repos, ou signal perdu pendant une image : on cherche le mode
    }

    void blindStep() {
        Mode best = null;
        int bestChain = 0;
        long bestLast = 0;
        double bestOfs = 0;
        for (Mode m : MODES) {
            double P = m.periodMs * SPMS, tol = 3 * SPMS + 0.002 * P, wl = Math.max(2.5, 0.3 * m.syncMs);
            int j = -1;
            for (int i = np - 1; i >= 0; i--) if (Math.abs(pL[i] - m.syncMs) <= wl) { j = i; break; }
            if (j < 0) continue;
            long last = pT[j];
            int chain = 0, skips = 0;
            double[] fr = new double[NP];
            fr[0] = pF[j];
            for (int i = j - 1; i >= 0; i--) {
                double d = pT[j] - pT[i];
                if (Math.abs(pL[i] - m.syncMs) > wl) { if (d < P - tol) continue; else break; }
                if (Math.abs(d - P) <= tol) { fr[++chain] = pF[i]; j = i; }
                else if (d < P - tol) skips++;
                else break;
            }
            if (chain >= 4 && skips <= 1 && chain > bestChain) {
                java.util.Arrays.sort(fr, 0, chain + 1);
                bestChain = chain; best = m; bestLast = last; bestOfs = fr[(chain + 1) / 2] - 1200;
            }
        }
        if (best == null) return;
        if (Math.abs(bestOfs) < 120) ofs = bestOfs;
        if (img != null) {   // signal perdu pendant une image : il faut des impulsions plus récentes que la dernière synchro trouvée
            if (bestLast <= sLast + (long) (0.5 * period) || n - bestLast > 6 * 8000L) return;
            if (best == md) { resync(bestLast); return; }   // même mode : on reprend l'image à la bonne ligne (les lignes perdues restent grises)
            finish(false);                                    // autre mode : la première image est gardée telle quelle, la nouvelle commence
        }
        startBlind(best, bestLast);
    }

    void resync(long syncStart) {
        int k = kLast + (int) Math.round((syncStart - sLast) / period);
        if (k < blockIdx || k >= blocksTotal()) return;
        blockIdx = k; sLast = syncStart; kLast = k; expected = syncStart; hw = 12 * SPMS;
        misses = 0; pendY = null; awaitSync = true;
        ls.onStatus("SSTV : " + md.name + " " + Lang.T("— signal retrouvé, ligne", "— signal found again, line") + " " + k);
    }

    void startBlind(Mode m, long syncStart) {
        np = 0; pRun = 0;
        md = m;
        px = new int[m.w * m.h];
        java.util.Arrays.fill(px, 0xFF202020);
        blockIdx = 0; rowsDone = 0; misses = 0; foundCount = 0; kLast = 0; pendY = null;
        period = m.periodMs * SPMS;
        expected = syncStart;
        hw = 12 * SPMS;
        awaitSync = true;
        img = this;
        im.begin(m.name, m.w, m.h, px);
        ls.onStatus("SSTV : " + m.name + " (" + m.w + "×" + m.h + ") " + Lang.T("— début manquant, réception en cours de route", "— start missing, picked up in progress"));
    }

    // ---- image
    Mode md;
    Object img;             // non nul pendant une image
    int[] px;
    int blockIdx, rowsDone, misses, foundCount;
    long s0found, sLast;
    int kLast;
    double period, expected, hw;
    boolean awaitSync;
    long curS;
    int[] pendY = null, pendCr = null;
    int pendRow;

    void startImage(Mode m, long visEnd) {
        if (img != null) finish(false);   // une autre image commence : la précédente est abandonnée (enregistrée partielle si assez longue)
        np = 0; pRun = 0;
        md = m;
        px = new int[m.w * m.h];
        java.util.Arrays.fill(px, 0xFF202020);
        blockIdx = 0; rowsDone = 0; misses = 0; foundCount = 0; kLast = 0; pendY = null;
        period = m.periodMs * SPMS;
        if (m.scottie) expected = visEnd + (2 * m.scanMs + 7) * SPMS; else expected = visEnd + 2 * SPMS;
        hw = 10 * SPMS;
        awaitSync = true;
        img = this;
        im.begin(m.name, m.w, m.h, px);
        ls.onStatus("SSTV : " + m.name + " (" + m.w + "×" + m.h + ")");
    }

    static final int PAIR = 2;

    int blocksTotal() { return md.kind == PD ? md.h / 2 : md.h / md.rep; }

    void imgStep() {
        int L = (int) Math.round(md.syncMs * SPMS);
        if (awaitSync) {
            if (n < expected + hw + L + 24) return;
            long s = findSync(expected, hw, L);
            if (s < 0) {
                if (blockIdx == 0) { finish(false); return; }
                misses++;
                s = Math.round(expected);
            } else {
                misses = 0;
                if (foundCount == 0) { s0found = s; kLast = blockIdx; }
                foundCount++;
                sLast = s; kLast = blockIdx;
                double fs = freqAvg(s + L * 0.25, s + L * 0.75);
                if (near(fs, 1200, 150)) ofs += 0.3 * ((fs - 1200) - ofs);   // l'accord suit les impulsions de synchro
                if (blockIdx >= 6) {
                    double pm = (s - s0found) / (double) (blockIdx - firstK());
                    if (Math.abs(pm - md.periodMs * SPMS) < 0.004 * md.periodMs * SPMS) period = pm;
                }
            }
            if (misses * md.periodMs > lossMs) { finish(false); return; }   // signal perdu depuis trop longtemps : retour à l'écoute
            curS = s;
            awaitSync = false;
        }
        double after = 0;
        for (int i = 0; i < md.st.length; i++) after = Math.max(after, md.st[i] + md.len[i]);
        if (md.kind == R36) after = 150;
        if (n < curS + after * SPMS + 16) return;
        if (misses <= 6) decodeBlock(curS);   // au-delà de quelques lignes sans synchro, on ne dessine plus du bruit : les lignes restent grises
        blockIdx++;
        if (blockIdx >= blocksTotal()) { finish(true); return; }
        expected = sLast + (blockIdx - kLast) * period;
        hw = (6 + 3 * Math.min(misses, 5)) * SPMS;
        awaitSync = true;
        if (blockIdx % 8 == 0) ls.onStatus("SSTV : " + md.name + " " + rowsDone + " / " + md.h);
    }

    int firstK() { return 0; }

    // Début de l'impulsion de synchro (échantillon) dans [exp − hw, exp + hw], ou −1.
    long findSync(double exp, double hwin, int L) {
        long lo = Math.round(exp - hwin), hi = Math.round(exp + hwin);
        int span = (int) (hi - lo) + L + 1;
        int[] pre = new int[span + 1];
        for (int i = 0; i < span; i++) {
            long x = lo + i;
            double f = freqAvg(x - 3, x + 1) - ofs;
            pre[i + 1] = pre[i] + (near(f, 1200, ts()) ? 1 : -1);
        }
        long best = -1;
        int bs = Integer.MIN_VALUE;
        for (long c = lo; c <= hi; c++) {
            int sc = pre[(int) (c - lo) + L] - pre[(int) (c - lo)];
            if (sc >= bs) { bs = sc; best = c; }
        }
        return bs >= sf() * L ? best : -1;
    }

    // Pixels d'un canal : cnt valeurs 0..255 (1500 Hz = 0, 2300 Hz = 255).
    int[] pixels(long s, double aMs, double lenMs, int cnt) {
        int[] o = new int[cnt];
        double pw = lenMs * SPMS / cnt;
        for (int i = 0; i < cnt; i++) {
            double t0 = s + aMs * SPMS + i * pw, mid = t0 + pw / 2, half = Math.max(pw / 2, 1.5);
            double f = freqAvg(mid - half, mid + half) - ofs;
            int v = (int) Math.round((f - 1500) / 800.0 * 255);
            o[i] = v < 0 ? 0 : v > 255 ? 255 : v;
        }
        return o;
    }

    static int clip(int v) { return v < 0 ? 0 : v > 255 ? 255 : v; }

    void put(int row0, int[] a, int[] b, int[] c, boolean ycc) {
        int w = md.w;
        for (int q = 0; q < md.rep; q++) {
            int row = row0 * md.rep + q;
            if (row >= md.h) break;
            for (int x = 0; x < w; x++) {
                int r, g, bl;
                if (ycc) {
                    int y = a[x], cr = b[x], cb = c[x];
                    r = clip((100 * y + 140 * cr - 17850) / 100);
                    g = clip((100 * y - 71 * cr - 33 * cb + 13260) / 100);
                    bl = clip((100 * y + 178 * cb - 22780) / 100);
                } else { r = a[x]; g = b[x]; bl = c[x]; }
                px[row * w + x] = 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
        }
    }

    static int[] wide(int[] half, int w) { int[] o = new int[w]; for (int i = 0; i < w; i++) o[i] = half[Math.min(half.length - 1, i / 2)]; return o; }

    void decodeBlock(long s) {
        int w = md.w;
        switch (md.kind) {
            case RGB: {
                int[][] c = new int[3][];
                for (int i = 0; i < 3; i++) c[md.role[i]] = pixels(s, md.st[i], md.len[i], w);
                put(blockIdx, c[R], c[G], c[B], false);
                rowsDone = Math.max(rowsDone, (blockIdx + 1) * md.rep);
                break;
            }
            case GRAY: {
                int[] g = pixels(s, md.st[0], md.len[0], w);
                put(blockIdx, g, g, g, false);
                rowsDone = Math.max(rowsDone, (blockIdx + 1) * md.rep);
                break;
            }
            case R72: {
                int[] y = pixels(s, md.st[0], md.len[0], w), cr = pixels(s, md.st[1], md.len[1], w), cb = pixels(s, md.st[2], md.len[2], w);
                put(blockIdx, y, cr, cb, true);
                rowsDone = Math.max(rowsDone, blockIdx + 1);
                break;
            }
            case R36: {
                int[] y = pixels(s, 12, 88, w);
                double sep = freqAvg(s + 100.5 * SPMS, s + 103.5 * SPMS) - ofs;
                int[] ch = wide(pixels(s, 106, 44, w / 2), w);
                if (sep < 1900) { pendY = y; pendCr = ch; pendRow = blockIdx; }          // ligne paire : R-Y
                else if (pendY != null && pendRow == blockIdx - 1) {                      // ligne impaire : B-Y, on écrit la paire
                    put(pendRow, pendY, pendCr, ch, true);
                    put(blockIdx, y, pendCr, ch, true);
                    rowsDone = Math.max(rowsDone, blockIdx + 1);
                    pendY = null;
                }
                break;
            }
            case PD: {
                int[] y0 = pixels(s, md.st[0], md.len[0], w), cr = pixels(s, md.st[1], md.len[1], w), cb = pixels(s, md.st[2], md.len[2], w), y1 = pixels(s, md.st[3], md.len[3], w);
                put(2 * blockIdx, y0, cr, cb, true);
                put(2 * blockIdx + 1, y1, cr, cb, true);
                rowsDone = Math.max(rowsDone, 2 * blockIdx + 2);
                break;
            }
        }
        im.rows(rowsDone);
    }

    void finish(boolean complete) {
        if (img != null) {
            im.end(complete, rowsDone);
            ls.onStatus("SSTV : " + (complete ? Lang.T("image terminée", "image complete") : Lang.T("signal perdu", "signal lost")) + " (" + md.name + ")");
        }
        img = null;
        px = null;
        visReset();
        np = 0; pRun = 0;   // les impulsions de cette image ne doivent pas déclencher une nouvelle réception
    }
}
