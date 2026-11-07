package fr.f5pbg.phonesdrlink;

import java.util.Locale;

// Décodeur WEFAX (fax météo HF, 60 / 90 / 120 / 240 lignes par minute) : modulation de fréquence d'une sous-porteuse de 1900 Hz,
// 1500 Hz = noir, 2300 Hz = blanc (réception en USB, fréquence affichée = porteuse − 1,9 kHz). Le son (8 kHz) est ramené en bande de
// base autour de 1900 Hz, filtré (FIR à 31 coefficients), et la fréquence instantanée vient de la différence de phase entre deux
// échantillons ; la phase cumulée est gardée dans un anneau de 16 s, ce qui donne la fréquence moyenne de n'importe quel intervalle
// (un pixel) par une simple différence (même principe que le décodeur SSTV).
// 1) Recherche du phasage : avant l'image, l'émetteur envoie une vingtaine de lignes « blanches » dont les 5 premiers % sont noirs.
//    Le son est découpé en blocs de la durée d'une ligne ; quand quatre blocs de suite montrent une seule impulsion noire de 3 à 7 %,
//    on s'accroche : les instants des impulsions sont ajustés par une droite (moindres carrés) qui donne le début de ligne ET la
//    durée réelle d'une ligne (correction de la dérive d'horloge, donc de l'inclinaison de l'image).
// 2) Image : dès qu'une ligne n'est plus une ligne de phasage (au moins 6 vues), chaque ligne est découpée en 1809 points (IOC 576),
//    chacun étant la moyenne de fréquence sur sa durée. Fin : tonalité d'arrêt (450 Hz), ou perte du signal.
// La plage de l'image est agrandie par paliers de 200 lignes (jusqu'à 2400) ; au-delà, l'image est enregistrée et une nouvelle commence.
final class WefaxEngine extends TextEngine {
    interface Out {
        void begin(int w, int h, int[] px, boolean fresh);   // une image commence (fresh) ou son tableau a grandi (!fresh)
        void rows(int done);                                  // nombre de lignes terminées
        void end(boolean complete, int rows);                 // fin : image complète, ou signal perdu
    }

    static final int W = 1809, H0 = 200, HSTEP = 200, HMAX = 2400;
    static final int[] LPM = { 60, 90, 120, 240 };
    static final int RING = 1 << 17, MASK = RING - 1, FIRN = 31, B = 400;
    static final double CENTER = 1900.0;
    static final int IDLE = 0, PHASING = 1, IMAGE = 2;

    // Réglages (voir MainActivity.applyFaxSettings)
    volatile int lpmIdx = 2;
    volatile boolean invert;
    volatile int cmd;                  // 1 = démarrer l'image tout de suite, 2 = terminer l'image en cours
    volatile int lossMs = 10000;       // signal absent pendant ce temps : image terminée
    volatile int tol;                  // détection du phasage : 0 normale, 1 un peu tolérante, 2 très tolérante
    volatile boolean tone;             // la tonalité de départ (300 / 675 Hz) arme une détection tolérante du phasage
    volatile int align;                // image lancée à la main : 0 pas de recalage, 1 sur un cadre noir, 2 sur une marge blanche

    final Out out;
    final SpecFind sf = new SpecFind();

    // démodulation
    final double[] cum = new double[RING];
    final double[] mr = new double[32], mi = new double[32];
    final double[] fir = new double[FIRN];
    double ph, pr, pi, cumv;
    long n;
    int mp;
    boolean init;

    // état
    int state = IDLE;
    boolean sig;
    long since;
    double bt;                          // début du prochain bloc de recherche (IDLE)
    final double[] tps = new double[256];   // instants (en échantillons) des impulsions de phasage retenues
    int m;
    double a0, bp;                      // droite des impulsions : instant de l'impulsion numéro k = a0 + k * bp
    final double[] v = new double[B];
    final boolean[] bl = new boolean[B];
    // image
    double t0, P;
    int row, h;
    int[] px;
    int stopHits;
    long lost, imgStart;
    String lastStatus = "";
    long toneUntil;                     // la tonalité de départ a été entendue : phasage attendu jusqu'à cet échantillon
    int toneHits;
    boolean alignPending;
    static final int ALIGN_ROWS = 24;
    int dbgN;

    WefaxEngine(Listener l, Out o) {
        super(l);
        out = o;
        status(Lang.T("attente du signal…", "waiting for a signal…"));
    }

    double pnom() { return RATE * 60.0 / LPM[Math.max(0, Math.min(LPM.length - 1, lpmIdx))]; }

    void status(String s) {
        String t = "WEFAX " + LPM[Math.max(0, Math.min(LPM.length - 1, lpmIdx))] + " : " + s;
        if (!t.equals(lastStatus)) { lastStatus = t; ls.onStatus(t); }
    }

    void initFir() {
        double fc = 650.0 / RATE, sum = 0;
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

    // Fréquence moyenne (Hz) entre les échantillons a et b, tonalités inversées si demandé.
    double freqAvg(double a, double b) {
        if (b - a < 1e-6) b = a + 1;
        double f = (cumAt(b) - cumAt(a)) / (b - a) * RATE / (2 * Math.PI) + CENTER;
        return invert ? 3800.0 - f : f;
    }

    void feed(short[] s, int cnt) {
        if (!init) initFir();
        sf.add(s, cnt);
        for (int k = 0; k < cnt; k++) {
            double x = s[k] / 32768.0;
            ph += 2 * Math.PI * CENTER / RATE;
            if (ph > 2 * Math.PI) ph -= 2 * Math.PI;
            mr[mp] = x * Math.cos(ph);
            mi[mp] = -x * Math.sin(ph);
            double yr = 0, yi = 0;
            for (int j = 0; j < FIRN; j++) { int q = (mp - j) & 31; yr += fir[j] * mr[q]; yi += fir[j] * mi[q]; }
            mp = (mp + 1) & 31;
            double inc = Math.atan2(yi * pr - yr * pi, yr * pr + yi * pi);
            pr = yr; pi = yi;
            cumv += inc;
            cum[(int) (n & MASK)] = cumv;
            n++;
        }
        since += cnt;
        if (since >= 2000) { since = 0; control(); }
        process();
    }

    // Toutes les 0,25 s : présence du signal, tonalité d'arrêt, état.
    void control() {
        double ratio = sf.ratio(CENTER, 1100, 400);
        sig = ratio >= 2.5;
        if (HellEngine.debug && ++dbgN % 4 == 0) android.util.Log.i("PSDR", "FAX state=" + state + " ratio=" + String.format(Locale.US, "%.2f", ratio) + " m=" + m + " row=" + row + " bp=" + String.format(Locale.US, "%.2f", bp));
        int c = cmd;
        if (c != 0) {
            cmd = 0;
            if (c == 1 && state != IMAGE) { startImage(n, pnom()); alignPending = align > 0; }
            else if (c == 2) { if (state == IMAGE) finish(false, 0); else { state = IDLE; m = 0; bt = n; } }
        }
        if (state == IMAGE) {
            if (!sig) lost += 2000; else lost = 0;
            if (lost > (long) lossMs * RATE / 1000) { finish(false, 0); return; }
            if (n > 1000 && stopTone()) stopHits++; else stopHits = 0;
            if (stopHits >= 6) { finish(true, (int) Math.ceil(1.5 * LPM[lpmIdx] / 60.0)); return; }
            long sec = (n - imgStart) / RATE;
            status(String.format(Locale.US, Lang.T("image : %d lignes (%d:%02d)", "picture: %d lines (%d:%02d)"), row, sec / 60, sec % 60));
        } else if (state == PHASING) {
            status(String.format(Locale.US, Lang.T("phasage : %d lignes", "phasing: %d lines"), m));
        } else {
            if (tone && state == IDLE && sig) {
                if (toneFrac(300.0, 800) > 0.5 || toneFrac(675.0, 960) > 0.5) toneHits++; else toneHits = 0;
                if (toneHits >= 4) toneUntil = n + 90L * RATE;
            }
            if (n < toneUntil) status(Lang.T("tonalité de départ entendue : recherche du phasage…", "start tone heard: looking for the phasing…"));
            else status(sig ? Lang.T("recherche du phasage…", "looking for the phasing…") : Lang.T("attente du signal…", "waiting for a signal…"));
        }
    }

    // Tonalité d'arrêt : alternance noir / blanc à 450 Hz sur les 100 dernières ms.
    final float[] tx = new float[1024];
    boolean stopTone() { return toneFrac(450.0, 800) > 0.5; }

    // Part de l'énergie (écart de fréquence instantanée) à la fréquence f sur les len derniers échantillons (len = nombre entier de périodes).
    double toneFrac(double f, int len) {
        double mean = 0;
        for (int i = 0; i < len; i++) {
            long q = n - len - 1 + i;   // (la case n n est pas encore écrite)
            tx[i] = (float) ((cum[(int) ((q + 1) & MASK)] - cum[(int) (q & MASK)]) * RATE / (2 * Math.PI));
            mean += tx[i];
        }
        mean /= len;
        double e = 0;
        for (int i = 0; i < len; i++) { tx[i] -= mean; e += tx[i] * (double) tx[i]; }
        if (e < len * 40.0 * 40.0) return 0;   // pas de variation franche : pas de tonalité
        return goertzel(tx, 0, len, f) / ((len / 2.0) * e);
    }

    void process() {
        boolean busy = true;
        while (busy) {
            busy = false;
            if (state == IDLE) {
                double pn = pnom();
                if (!sig) { bt = n; m = 0; break; }
                if (bt < n - RING + 4 * pn) bt = n - 2 * pn;
                if (n >= bt + pn + 2) { idleBlock(pn); busy = true; }
            } else if (state == PHASING) {
                double s = a0 + m * bp - 0.025 * bp;
                if (s < n - RING + 4 * bp) { state = IDLE; m = 0; bt = n; break; }
                if (n >= s + bp + 2) { phaseLine(s); busy = true; }
            } else {
                if (t0 < n - RING + 4 * P) t0 = n - 2 * P;
                if (n >= t0 + P + 2) { imageLine(); busy = true; }
            }
        }
    }

    // Profil d'une ligne en B parties égales (fréquences moyennes).
    void profile(double s, double p) {
        for (int i = 0; i < B; i++) v[i] = freqAvg(s + i * p / B, s + (i + 1) * p / B);
    }

    // Une seule impulsion noire de 3 à 7,5 % dans une ligne blanche ? Renvoie son centre (en parties, éventuellement hors de 0..B), sinon -1.
    static final int[] BLK_MIN = { 12, 8, 6 }, BLK_MAX = { 30, 40, 60 }, WH_MIN = { 330, 290, 250 }, LOCK = { 4, 4, 3 }, IMG_MIN = { 6, 5, 4 };
    static final double[] BLACK_T = { 1700, 1800, 1850 }, WHITE_T = { 2100, 2000, 1950 };
    int lvl() { return Math.max(Math.max(0, Math.min(2, tol)), n < toneUntil ? 1 : 0); }

    double phasingLike() {
        int blk = 0, wh = 0, starts = 0, first = -1, L = lvl();
        for (int i = 0; i < B; i++) {
            bl[i] = v[i] < BLACK_T[L];
            if (bl[i]) blk++;
            if (v[i] > WHITE_T[L]) wh++;
        }
        if (blk < BLK_MIN[L] || blk > BLK_MAX[L] || wh < WH_MIN[L]) return -1;
        for (int i = 0; i < B; i++) if (bl[i] && !bl[(i + B - 1) % B]) { starts++; first = i; }
        if (starts != 1) return -1;
        double sw = 0, sc = 0;
        for (int j = -3; j < blk + 3; j++) {
            int i = ((first + j) % B + B) % B;
            double w = Math.max(0, Math.min(1, (1900.0 - v[i]) / 400.0));
            sw += w; sc += w * (first + j);
        }
        return sw > 0 ? sc / sw : -1;
    }

    void add(double tp) {
        if (m == tps.length) { System.arraycopy(tps, 1, tps, 0, tps.length - 1); m--; }
        tps[m++] = tp;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < m; i++) { sx += i; sy += tps[i]; sxx += (double) i * i; sxy += i * tps[i]; }
        double pn = pnom();
        if (m >= 2) {
            bp = (m * sxy - sx * sy) / (m * sxx - sx * sx);
            bp = Math.max(pn * 0.992, Math.min(pn * 1.008, bp));
        } else bp = pn;
        a0 = sy / m - bp * sx / m;
    }

    void idleBlock(double pn) {
        double s = bt;
        bt += pn;
        profile(s, pn);
        double c = phasingLike();
        if (c < 0) { m = 0; return; }
        double cand = s + c * pn / B, tp = cand;
        if (m > 0) {
            double exp = tps[m - 1] + pn;
            tp = cand + Math.round((exp - cand) / pn) * pn;
            if (Math.abs(tp - exp) > 0.03 * pn) { m = 0; tp = cand; }
        }
        add(tp);
        if (m >= LOCK[lvl()]) state = PHASING;
    }

    void phaseLine(double s) {
        profile(s, bp);
        double c = phasingLike();
        if (c >= 0) {
            double dev = c - 0.025 * B;
            dev -= B * Math.round(dev / B);
            add(s + (0.025 * B + dev) * bp / B);
        } else if (m >= IMG_MIN[lvl()]) startImage(s, bp);
        else { state = IDLE; bt = s + bp; m = 0; }
    }

    void startImage(double s, double p) {
        state = IMAGE;
        t0 = s; P = p;
        row = 0; h = H0;
        px = new int[W * h];
        stopHits = 0; lost = 0; imgStart = n;
        out.begin(W, h, px, true);
    }

    void imageLine() {
        double s = t0;
        t0 += P;
        if (row >= h) {
            if (h < HMAX) {
                h += HSTEP;
                px = java.util.Arrays.copyOf(px, W * h);
                out.begin(W, h, px, false);
            } else {
                out.end(true, row);
                row = 0; h = H0;
                px = new int[W * h];
                out.begin(W, h, px, true);
            }
        }
        int base = row * W;
        double pw = P / W;
        for (int j = 0; j < W; j++) {
            double f = freqAvg(s + j * pw, s + (j + 1) * pw);
            int g = (int) Math.max(0, Math.min(255, (f - 1500.0) * 255.0 / 800.0));
            px[base + j] = 0xFF000000 | (g * 0x010101);
        }
        row++;
        if (alignPending && row >= ALIGN_ROWS) doAlign();
        out.rows(row);
    }

    // Image lancée en cours de route : le début de ligne est quelconque. On cherche, sur les premières lignes moyennées, la bande la plus sombre (cadre noir de part et d'autre du raccord)
    // ou la plus claire (marge blanche) ; son centre est la limite de ligne. Les lignes déjà lues sont tournées, les suivantes sont décalées.
    void doAlign() {
        alignPending = false;
        int wd = 45;
        double[] pr = new double[W];
        double tot = 0;
        for (int r = 0; r < row; r++) for (int j = 0; j < W; j++) pr[j] += px[r * W + j] & 255;
        for (int j = 0; j < W; j++) { pr[j] /= row; tot += pr[j]; }
        double mean = tot / W, sum = 0;
        for (int j = 0; j < wd; j++) sum += pr[j];
        double best = sum;
        int bi = 0;
        boolean dark = align != 2;
        for (int j = 1; j < W; j++) {
            sum += pr[(j + wd - 1) % W] - pr[j - 1];
            if (dark ? sum < best : sum > best) { best = sum; bi = j; }
        }
        if (Math.abs(best / wd - mean) < 20) return;   // rien de net : on ne touche pas
        int c = (bi + wd / 2) % W;
        int[] tmp = new int[W];
        for (int r = 0; r < row; r++) {
            for (int j = 0; j < W; j++) tmp[j] = px[r * W + (j + c) % W];
            System.arraycopy(tmp, 0, px, r * W, W);
        }
        t0 += c * P / W;
        out.begin(W, h, px, false);
    }

    // Fin de l'image : on enlève les dernières lignes si la tonalité d'arrêt les a salies.
    void finish(boolean complete, int trim) {
        int rows = Math.max(0, row - trim);
        state = IDLE;
        m = 0; bt = n;
        px = null;
        out.end(complete, rows);
    }

    @Override void onClose() {
        if (state == IMAGE) finish(false, 0);
    }
}
