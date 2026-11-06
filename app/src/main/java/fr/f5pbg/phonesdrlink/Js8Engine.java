package fr.f5pbg.phonesdrlink;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

// Décodeur JS8Call (réception seule), cinq vitesses : Lent (30 s), Normal (15 s), Rapide (10 s), Turbo (6 s), Ultra (4 s).
// Couche physique : FSK à 8 tonalités (79 symboles : trois suites de synchro de Costas de 7 symboles aux positions 0, 36 et 72, 58 symboles de
// données), code LDPC (174,87) (décodage par propagation de croyance), CRC de 12 bits, message de 72 bits (12 caractères de 6 bits) + 3 bits de type
// de trame. Les symboles 7 à 35 portent les 87 premiers bits du mot de code (parité), les symboles 43 à 71 les 87 suivants (message) ; la tonalité
// est la valeur binaire directe des trois bits (pas de code de Gray). Les matrices et le CRC (polynôme 0xC06, valeur finale xor 42) sont ceux de
// JS8Call (GPL v3). Le texte des trames est interprété par Js8Text.
// Méthode : spectrogramme (FFT de 2 symboles, pas d'un quart de symbole) ; « énergie sur les tonalités de Costas / énergie des autres tonalités »
// pour chaque fréquence et chaque instant ; pour chaque candidat : tranche du spectre complet ramenée en bande de base (taux 12000/dec),
// recherche fine de l'instant et de la fréquence sur les trois suites de Costas, démodulation non cohérente des 8 tonalités de chaque symbole,
// logarithmes de vraisemblance par bit, propagation de croyance, contrôle du CRC.
final class Js8Engine extends SlotDecoder {
    // vitesses : 0 Lent (JS8E), 1 Normal (JS8A), 2 Rapide (JS8B), 3 Turbo (JS8C), 4 Ultra (JS8I)
    static final int[] NSPS = { 3840, 1920, 1200, 600, 384 };
    static final int[] PERIOD = { 30000, 15000, 10000, 6000, 4000 };
    static final int[] DELAY = { 500, 500, 200, 100, 100 };
    static final int[] NDS = { 32, 32, 20, 12, 12 };            // échantillons par symbole en bande de base
    static final boolean[] ORIGINAL = { false, true, false, false, false };
    static final String[] NAMES = { "JS8 Lent", "JS8 Normal", "JS8 Rapide", "JS8 Turbo", "JS8 Ultra" };
    static final int PRE_MS = 1000;
    static final int[][] COSTAS_ORIG = { { 4, 2, 5, 6, 1, 3, 0 }, { 4, 2, 5, 6, 1, 3, 0 }, { 4, 2, 5, 6, 1, 3, 0 } };
    static final int[][] COSTAS_MOD = { { 0, 6, 2, 3, 5, 4, 1 }, { 1, 5, 0, 2, 3, 6, 4 }, { 2, 5, 0, 6, 4, 1, 3 } };

    static double SYNC_MIN = 1.35;     // seuil du score de synchro grossière (1 = bruit)
    static int MAX_CAND = 70;
    static double LLR_SCALE = 1.0;
    static boolean AMPLITUDE = false;
    static int MIN_SYNC = 6;           // suites de Costas retrouvées (sur 21) pour accepter un décodage

    // matrice de contrôle : voisins (indices de bits 0 à 173) de chacun des 87 contrôles
    static final int[][] NM = {
        {0,29,59,88,117,146}, {1,30,60,89,118,146}, {2,31,61,90,119,147}, {3,32,62,91,120,148}, {1,33,63,92,121,149}, {4,32,64,93,122,147},
        {5,33,65,94,123,150}, {6,34,66,95,119,151}, {7,35,67,96,124,152}, {8,36,68,97,125,151}, {9,37,69,98,126,153}, {10,38,70,99,125,154},
        {11,39,60,100,127,144}, {9,32,59,94,127,155}, {12,40,71,96,125,156}, {12,41,72,89,128,155}, {13,38,73,98,129,157}, {14,42,74,101,130,158},
        {15,42,70,102,117,159}, {16,43,75,97,129,155}, {17,44,59,95,131,160}, {18,45,72,82,132,161}, {11,37,76,101,133,162}, {18,46,77,103,134,146},
        {0,31,76,104,135,163}, {19,47,72,105,122,162}, {20,40,78,106,136,164}, {21,41,65,107,137,151}, {17,41,79,108,138,153}, {22,48,80,109,134,165},
        {15,49,81,90,128,157}, {2,47,62,106,123,166}, {5,50,66,110,133,154}, {23,34,76,99,121,161}, {19,44,75,111,139,156}, {20,35,63,91,129,158},
        {7,51,82,110,117,165}, {20,52,83,112,137,167}, {24,50,78,88,121,157}, {21,43,74,106,132,154,171}, {8,53,83,89,140,168}, {21,53,84,109,135,160},
        {7,36,64,101,128,169}, {18,38,84,113,138,149}, {25,54,70,92,141,166}, {26,55,64,95,132,159,173}, {27,30,85,99,116,170}, {27,51,69,103,131,143},
        {23,56,67,94,136,141}, {6,29,71,109,142,150}, {3,50,75,114,126,167}, {15,44,86,113,124,171}, {14,29,85,114,122,149}, {22,45,63,90,143,172},
        {22,34,74,112,144,152}, {13,40,86,107,116,148,169}, {24,39,84,93,123,158}, {24,57,68,115,142,173}, {28,42,60,115,131,161}, {14,57,87,111,120,163},
        {3,58,71,113,118,162,172}, {26,46,85,97,133,152}, {4,43,77,108,140}, {9,45,68,102,135,164}, {8,49,58,92,127,163}, {13,56,57,108,119,165},
        {16,54,61,115,124,153}, {2,53,69,100,139,169}, {0,35,81,107,126,173}, {4,52,80,104,139}, {28,52,66,98,141,172}, {17,48,73,96,114,166},
        {1,56,62,102,137,156}, {25,37,78,111,134,170}, {10,51,65,87,118,147}, {19,39,67,116,140,159}, {10,47,80,88,145,168}, {28,46,79,91,145,171},
        {5,31,86,103,144,168}, {26,33,73,105,130,164}, {11,55,83,87,138}, {12,55,61,110,145,170}, {25,36,79,104,143,150}, {16,30,81,112,120,160},
        {27,48,58,93,136}, {6,54,82,100,130,167}, {23,49,77,105,142,148}
    };
    // matrice génératrice (87 lignes de 87 bits, en hexadécimal) : seulement pour le banc d'essai (émission de signaux de test)
    static final String[] GEN = {
"23bba830e23b6b6f50982e", "1f8e55da218c5df3309052", "ca7b3217cd92bd59a5ae20", "56f78313537d0f4382964e",
"6be396b5e2e819e373340c", "293548a138858328af4210", "cb6c6afcdc28bb3f7c6e86", "3f2a86f5c5bd225c961150",
"849dd2d63673481860f62c", "56cdaec6e7ae14b43feeee", "04ef5cfa3766ba778f45a4", "c525ae4bd4f627320a3974",
"41fd9520b2e4abeb2f989c", "7fb36c24085a34d8c1dbc4", "40fc3e44bb7d2bb2756e44", "d38ab0a1d2e52a8ec3bc76",
"3d0f929ef3949bd84d4734", "45d3814f504064f80549ae", "f14dbf263825d0bd04b05e", "db714f8f64e8ac7af1a76e",
"8d0274de71e7c1a8055eb0", "51f81573dd4049b082de14", "d8f937f31822e57c562370", "b6537f417e61d1a7085336",
"ecbd7c73b9cd34c3720c8a", "3d188ea477f6fa41317a4e", "1ac4672b549cd6dba79bcc", "a377253773ea678367c3f6",
"0dbd816fba1543f721dc72", "ca4186dd44c3121565cf5c", "29c29dba9c545e267762fe", "1616d78018d0b4745ca0f2",
"fe37802941d66dde02b99c", "a9fa8e50bcb032c85e3304", "83f640f1a48a8ebc0443ea", "3776af54ccfbae916afde6",
"a8fc906976c35669e79ce0", "f08a91fb2e1f78290619a8", "cc9da55fe046d0cb3a770c", "d36d662a69ae24b74dcbd8",
"40907b01280f03c0323946", "d037db825175d851f3af00", "1bf1490607c54032660ede", "0af7723161ec223080be86",
"eca9afa0f6b01d92305edc", "7a8dec79a51e8ac5388022", "9059dfa2bb20ef7ef73ad4", "6abb212d9739dfc02580f2",
"f6ad4824b87c80ebfce466", "d747bfc5fd65ef70fbd9bc", "612f63acc025b6ab476f7c", "05209a0abb530b9e7e34b0",
"45b7ab6242b77474d9f11a", "6c280d2a0523d9c4bc5946", "f1627701a2d692fd9449e6", "8d9071b7e7a6a2eed6965e",
"bf4f56e073271f6ab4bf80", "c0fc3ec4fb7d2bb2756644", "57da6d13cb96a7689b2790", "a9fa2eefa6f8796a355772",
"164cc861bdd803c547f2ac", "cc6de59755420925f90ed2", "a0c0033a52ab6299802fd2", "b274db8abd3c6f396ea356",
"97d4169cb33e7435718d90", "81cfc6f18c35b1e1f17114", "481a2a0df8a23583f82d6c", "081c29a10d468ccdbcecb6",
"2c4142bf42b01e71076acc", "a6573f3dc8b16c9d19f746", "c87af9a5d5206abca532a8", "012dee2198eba82b19a1da",
"b1ca4ea2e3d173bad4379c", "b33ec97be83ce413f9acc8", "5b0f7742bca86b8012609a", "37d8e0af9258b9e8c5f9b2",
"35ad3fb0faeb5f1b0c30dc", "6114e08483043fd3f38a8a", "cd921fdf59e882683763f6", "95e45ecd0135aca9d6e6ae",
"2e547dd7a05f6597aac516", "14cd0f642fc0c5fe3a65ca", "3a0a1dfd7eee29c2e827e0", "c8b5dffc335095dcdcaf2a",
"3dd01a59d86310743ec752", "8abdb889efbe39a510a118", "3f231f212055371cf3e2a2", 
    };

    static final int NCHK = 87;
    static final int[] EDGE_START = new int[NCHK + 1];
    static final int[] EDGE_BIT;
    static final int[] BIT_EDGE_START = new int[175];
    static final int[] BIT_EDGES;     // pour chaque bit, indices des arêtes
    static {
        int ne = 0;
        for (int i = 0; i < NCHK; i++) { EDGE_START[i] = ne; ne += NM[i].length; }
        EDGE_START[NCHK] = ne;
        EDGE_BIT = new int[ne];
        int[] cnt = new int[174];
        for (int i = 0; i < NCHK; i++) for (int j = 0; j < NM[i].length; j++) { EDGE_BIT[EDGE_START[i] + j] = NM[i][j]; cnt[NM[i][j]]++; }
        int s = 0;
        for (int b = 0; b < 174; b++) { BIT_EDGE_START[b] = s; s += cnt[b]; }
        BIT_EDGE_START[174] = s;
        BIT_EDGES = new int[s];
        int[] fill = new int[174];
        for (int e = 0; e < ne; e++) { int b = EDGE_BIT[e]; BIT_EDGES[BIT_EDGE_START[b] + fill[b]++] = e; }
    }

    static final class Msg {
        String chars; int i3, snr, nsync, s0, di; int[] tone; double dt, freq;
        String text;
    }

    final int speed;
    Js8Engine(int speed, Ft8Engine.Listener l) {
        super(NAMES[speed], PERIOD[speed], PRE_MS, DELAY[speed] + (int) (79000L * NSPS[speed] / 12000) + 1500, l);
        this.speed = speed;
    }

    String timeText(Calendar c) { return String.format(Locale.US, "%02d%02d%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)); }

    Result decode(short[] win, int n, long slotStartMs, String t) {
        Result r = new Result();
        for (Msg m : decodeWindow(win, n, speed, PRE_MS + DELAY[speed])) {
            String tx = null;
            try { tx = Js8Text.frameText(m.chars, m.i3, speed >= 3); } catch (Throwable e) { }
            if (tx == null) tx = "?" + m.chars;
            r.lines.add(String.format(Locale.US, "%s %+4d %+5.1f %4d  %s", t, m.snr, m.dt, Math.round(m.freq), tx));
            r.dts.add(m.dt);
        }
        return r;
    }

    // ------------------------------------------------------------------ FFT à base mixte (facteurs 2, 3 et 5)
    static final class MFft {
        final int n;
        final double[] cr, ci;
        final double[] tr = new double[8], ti = new double[8];
        MFft(int n) {
            this.n = n;
            cr = new double[n];
            ci = new double[n];
            for (int i = 0; i < n; i++) { double a = -2 * Math.PI * i / n; cr[i] = Math.cos(a); ci[i] = Math.sin(a); }
        }
        static boolean ok(int n) {
            for (int p : new int[] { 2, 3, 5 }) while (n % p == 0) n /= p;
            return n == 1;
        }
        /** y = TFD(x) (x, y distincts). */
        void run(double[] xr, double[] xi, double[] yr, double[] yi) { rec(xr, xi, 0, 1, n, yr, yi, 0); }
        private void rec(double[] xr, double[] xi, int xo, int xs, int m, double[] yr, double[] yi, int yo) {
            if (m == 1) { yr[yo] = xr[xo]; yi[yo] = xi[xo]; return; }
            int p = m % 2 == 0 ? 2 : m % 3 == 0 ? 3 : 5;
            int q = m / p, gstep = n / m;
            for (int r = 0; r < p; r++) rec(xr, xi, xo + r * xs, xs * p, q, yr, yi, yo + r * q);
            if (p == 2) {
                for (int k = 0; k < q; k++) {
                    int a = yo + k, b = a + q, w = k * gstep;
                    double br = yr[b] * cr[w] - yi[b] * ci[w], bi = yr[b] * ci[w] + yi[b] * cr[w];
                    double ar = yr[a], ai = yi[a];
                    yr[a] = ar + br; yi[a] = ai + bi; yr[b] = ar - br; yi[b] = ai - bi;
                }
                return;
            }
            int pstep = n / p;
            for (int k = 0; k < q; k++) {
                for (int r = 0; r < p; r++) {
                    int idx = yo + r * q + k, w = r * k * gstep;
                    tr[r] = yr[idx] * cr[w] - yi[idx] * ci[w];
                    ti[r] = yr[idx] * ci[w] + yi[idx] * cr[w];
                }
                for (int o = 0; o < p; o++) {
                    double sr = 0, si = 0;
                    for (int r = 0; r < p; r++) {
                        int w = ((r * o) % p) * pstep;
                        sr += tr[r] * cr[w] - ti[r] * ci[w];
                        si += tr[r] * ci[w] + ti[r] * cr[w];
                    }
                    yr[yo + o * q + k] = sr; yi[yo + o * q + k] = si;
                }
            }
        }
    }

    // ------------------------------------------------------------------ CRC et mots de code
    static int crc12(int[] bits88) {   // 88 bits (un bit par case) : reste modulo le polynôme x^12 + 0xC06, valeur finale xor 42
        int rem = 0;
        for (int i = 0; i < 88; i++) {
            int top = (rem >> 11) & 1;
            rem = ((rem << 1) | bits88[i]) & 0xfff;
            if (top != 0) rem ^= 0xc06;
        }
        return rem ^ 42;
    }

    /** 87 bits décodés : CRC valide ? */
    static boolean crcOk(int[] d) {
        int[] b = new int[88];
        for (int i = 0; i < 75; i++) b[i] = d[i];
        int crc = 0;
        for (int i = 75; i < 87; i++) crc = (crc << 1) | d[i];
        return crc12(b) == crc;
    }

    /** Propagation de croyance. llr > 0 : le bit vaut plutôt 1. Renvoie le nombre de contrôles non satisfaits à la fin (0 : mot de code valide) ; cw reçoit le mot. */
    static int bp(double[] llr, int[] cw, int maxIter) {
        final int ne = EDGE_START[NCHK];
        double[] L = new double[174];
        for (int i = 0; i < 174; i++) L[i] = -Math.max(-25, Math.min(25, llr[i]));   // convention : L > 0 favorise le bit 0
        double[] tov = new double[ne];       // contrôle -> bit, par arête
        double[] toc = new double[ne];       // bit -> contrôle
        double[] zn = new double[174];
        int nclast = 0, ncnt = 0, nc = NCHK;
        for (int it = 0; it <= maxIter; it++) {
            for (int b = 0; b < 174; b++) {
                double s = L[b];
                for (int k = BIT_EDGE_START[b]; k < BIT_EDGE_START[b + 1]; k++) s += tov[BIT_EDGES[k]];
                zn[b] = s;
                cw[b] = s < 0 ? 1 : 0;
            }
            nc = 0;
            for (int c = 0; c < NCHK; c++) {
                int p = 0;
                for (int e = EDGE_START[c]; e < EDGE_START[c + 1]; e++) p ^= cw[EDGE_BIT[e]];
                nc += p;
            }
            if (nc == 0) return 0;
            if (it > 0) {
                int nd = nc - nclast;
                ncnt = nd < 0 ? 0 : ncnt + 1;
                if (ncnt >= 5 && it >= 10 && nc > 15) return nc;
            }
            nclast = nc;
            for (int c = 0; c < NCHK; c++)
                for (int e = EDGE_START[c]; e < EDGE_START[c + 1]; e++) {
                    double v = zn[EDGE_BIT[e]] - tov[e];
                    toc[e] = Math.tanh(v / 2);
                }
            for (int c = 0; c < NCHK; c++) {
                for (int e = EDGE_START[c]; e < EDGE_START[c + 1]; e++) {
                    double prod = 1;
                    for (int f = EDGE_START[c]; f < EDGE_START[c + 1]; f++) if (f != e) prod *= toc[f];
                    prod = Math.max(-0.999999, Math.min(0.999999, prod));
                    tov[e] = 2 * atanh(prod);
                }
            }
        }
        return nc;
    }

    static double atanh(double x) { return 0.5 * Math.log((1 + x) / (1 - x)); }

    // ------------------------------------------------------------------ décodage d'une fenêtre (12 kHz)
    /** win : n échantillons ; nominalMs : instant nominal du premier symbole dans la fenêtre (début de tranche − début de fenêtre + délai de départ). */
    static int PASSES = 2;

    static ArrayList<Msg> decodeWindow(short[] win, int n, int speed, int nominalMs) {
        ArrayList<Msg> out = new ArrayList<Msg>();
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = win[i];
        for (int pass = 0; pass < PASSES; pass++) {
            x = decodePass(x, n, speed, nominalMs, out, pass + 1 < PASSES);
            if (x == null) break;
        }
        return out;
    }

    /** Une passe : ajoute les messages nouveaux a out ; renvoie la fenetre privee des signaux decodes (ou null). */
    static double[] decodePass(double[] win, int n, int speed, int nominalMs, ArrayList<Msg> out, boolean residual) {
        final int nsps = NSPS[speed], hop = nsps / 4, nfft = 2 * nsps, nds = NDS[speed], dec = nsps / nds;
        final double sp = 12000.0 / nsps, binHz = 12000.0 / nfft;
        int nfr = (n - nsps) / hop + 1;
        if (nfr < 4 * 79 + 8) return null;
        final int bLo = (int) Math.ceil(150 / binHz), bHi = (int) (3100 / binHz), nb = bHi - bLo + 1;
        // ---- spectrogramme (deux trames réelles par FFT complexe)
        float[][] P = new float[nfr][nb];
        MFft ft = new MFft(nfft);
        double[] w = new double[nsps];
        for (int i = 0; i < nsps; i++) { double s = Math.sin(Math.PI * (i + 0.5) / nsps); w[i] = s * s; }
        double[] xr = new double[nfft], xi = new double[nfft], yr = new double[nfft], yi = new double[nfft];
        for (int m = 0; m < nfr; m += 2) {
            boolean two = m + 1 < nfr;
            for (int i = 0; i < nsps; i++) { xr[i] = win[m * hop + i] * w[i]; xi[i] = two ? win[(m + 1) * hop + i] * w[i] : 0; }
            for (int i = nsps; i < nfft; i++) { xr[i] = 0; xi[i] = 0; }
            ft.run(xr, xi, yr, yi);
            for (int b = 0; b < nb; b++) {
                int k = b + bLo, k2 = (nfft - k) % nfft;
                double ar = (yr[k] + yr[k2]) * 0.5, ai = (yi[k] - yi[k2]) * 0.5;
                P[m][b] = (float) (ar * ar + ai * ai);
                if (two) {
                    double cr = (yi[k] + yi[k2]) * 0.5, ci = (yr[k] - yr[k2]) * 0.5;
                    P[m + 1][b] = (float) (cr * cr + ci * ci);
                }
            }
        }
        // ---- synchro grossière
        int[][] cs = ORIGINAL[speed] ? COSTAS_ORIG : COSTAS_MOD;
        int fNom = (int) Math.round(nominalMs * 12.0 / hop);
        int rng = (int) Math.round(2000 * 12.0 / hop);
        int tLo = Math.max(0, fNom - rng), tHi = Math.min(nfr - 1 - 4 * 78 - 3, fNom + rng);
        if (tHi < tLo) return null;
        int nb0 = nb - 14;
        double[] score = new double[nb0];
        int[] tbest = new int[nb0];
        for (int b0 = 0; b0 < nb0; b0++) {
            double best = -1; int bt = tLo;
            for (int t0 = tLo; t0 <= tHi; t0++) {
                double num = 0, tot = 0;
                for (int blk = 0; blk < 3; blk++) {
                    for (int i = 0; i < 7; i++) {
                        float[] row = P[t0 + 4 * (blk * 36 + i)];
                        num += row[b0 + 2 * cs[blk][i]];
                        for (int c = 0; c < 8; c++) tot += row[b0 + 2 * c];
                    }
                }
                double r = num / ((tot - num) / 7 + 1e-30);
                if (r > best) { best = r; bt = t0; }
            }
            score[b0] = best; tbest[b0] = bt;
        }
        P = null;
        ArrayList<int[]> cands = new ArrayList<int[]>();
        boolean[] taken = new boolean[nb0];
        for (int c = 0; c < MAX_CAND; c++) {
            int bi = -1; double bv = SYNC_MIN;
            for (int b = 0; b < nb0; b++) if (!taken[b] && score[b] > bv) {
                // maximum local sur ±2 cases
                boolean peak = true;
                for (int d = -2; d <= 2; d++) if (d != 0 && b + d >= 0 && b + d < nb0 && score[b + d] > score[b]) { peak = false; break; }
                if (peak) { bv = score[b]; bi = b; }
            }
            if (bi < 0) break;
            for (int b = Math.max(0, bi - 2); b <= Math.min(nb0 - 1, bi + 2); b++) taken[b] = true;
            cands.add(new int[] { bi + bLo, tbest[bi] });
        }
        if (cands.isEmpty()) return null;
        // ---- spectre complet de la fenêtre (longueur composée de facteurs 2, 3 et 5, multiple de dec)
        int m0 = (n + dec - 1) / dec;
        while (!MFft.ok(m0)) m0++;
        final int M = m0, N1 = M * dec;
        MFft big = new MFft(N1);
        double[] bxr = new double[N1], bxi = new double[N1], byr = new double[N1], byi = new double[N1];
        for (int i = 0; i < n; i++) bxr[i] = win[i];
        big.run(bxr, bxi, byr, byi);
        bxr = null; bxi = null;
        MFft small = new MFft(M);
        double[] sr = new double[M], si = new double[M], br = new double[M], bi2 = new double[M];
        // twiddles de la recherche fine
        final int ND = 17;
        double[][][] ec = new double[ND][8][nds], es = new double[ND][8][nds];
        double[] deltas = new double[ND];
        double fs2 = 12000.0 / dec;
        for (int d = 0; d < ND; d++) {
            deltas[d] = (d - 8) * sp / 16;
            for (int t = 0; t < 8; t++) {
                double f = (t - 3.5) * sp + deltas[d];
                for (int k = 0; k < nds; k++) { double a = -2 * Math.PI * f * (k + 0.5) / fs2; ec[d][t][k] = Math.cos(a); es[d][t][k] = Math.sin(a); }
            }
        }
        final double kHz = N1 / 12000.0;   // cases du spectre complet par Hz
        int found = 0;
        for (int[] c : cands) {
            double f0 = c[0] * binHz;
            double fc = f0 + 3.5 * sp;
            int kc = (int) Math.round(fc * kHz);
            int hwp = (int) Math.round(4.5 * sp * kHz), hwt = (int) Math.round(5.5 * sp * kHz);
            java.util.Arrays.fill(sr, 0); java.util.Arrays.fill(si, 0);
            for (int d = -hwt; d <= hwt; d++) {
                int k = kc + d;
                if (k < 0 || k >= N1) continue;
                int ad = Math.abs(d);
                double wt = ad <= hwp ? 1 : 0.5 * (1 + Math.cos(Math.PI * (ad - hwp) / (double) (hwt - hwp)));
                int j = ((d % M) + M) % M;
                // conjuguée (pour la TFD inverse par TFD directe)
                sr[j] = byr[k] * wt; si[j] = -byi[k] * wt;
            }
            small.run(sr, si, br, bi2);
            for (int j = 0; j < M; j++) { br[j] = br[j] / N1; bi2[j] = -bi2[j] / N1; }
            Msg msg = decodeCandidate(br, bi2, M, speed, c[1] * (nds / 4), ec, es, deltas, sp, dec, f0, cs, nominalMs);
            if (msg == null) continue;
            boolean dup = false;
            for (Msg o : out) if (o.chars.equals(msg.chars) && o.i3 == msg.i3) { dup = true; if (msg.snr > o.snr) { o.snr = msg.snr; o.dt = msg.dt; o.freq = msg.freq; } break; }
            if (dup) continue;
            out.add(msg);
            found++;
            if (!residual) continue;
            // soustraction du signal decode dans le spectre complet (modele : amplitude complexe par symbole)
            double[] mr = new double[M], mi = new double[M], fr = new double[M], fi = new double[M];
            for (int k = 0; k < 79; k++) {
                int st = msg.s0 + k * nds, t = msg.tone[k];
                double ar = 0, ai = 0;
                double[] cc = ec[msg.di][t], ss = es[msg.di][t];
                for (int q = 0; q < nds; q++) { double xr2 = br[st + q], xi2 = bi2[st + q]; ar += xr2 * cc[q] - xi2 * ss[q]; ai += xr2 * ss[q] + xi2 * cc[q]; }
                ar /= nds; ai /= nds;
                for (int q = 0; q < nds; q++) { mr[st + q] = ar * cc[q] + ai * ss[q]; mi[st + q] = ai * cc[q] - ar * ss[q]; }
            }
            small.run(mr, mi, fr, fi);
            for (int d = -hwp; d <= hwp; d++) {
                int k = kc + d;
                if (k < 1 || k >= N1) continue;
                int jj = ((d % M) + M) % M;
                byr[k] -= dec * fr[jj]; byi[k] -= dec * fi[jj];
                byr[N1 - k] -= dec * fr[jj]; byi[N1 - k] += dec * fi[jj];
            }
        }
        if (!residual || found == 0) return null;
        double[] ir = new double[N1], ii = new double[N1], zr = new double[N1], zi = new double[N1];
        for (int i = 0; i < N1; i++) { ir[i] = byr[i]; ii[i] = -byi[i]; }
        big.run(ir, ii, zr, zi);
        double[] res = new double[n];
        for (int i = 0; i < n; i++) res[i] = zr[i] / N1;
        return res;
    }

    // variantes de calcul des LLR essayees l'une apres l'autre : { 1 = amplitude / 0 = puissance, facteur }
    static final double[][] VARIANTS = { { 1, 1.0 }, { 1, 1.7 }, { 0, 1.0 }, { 1, 0.7 } };

    static double[] llrs(double[][] S, double sigma2, boolean amp, double scale) {
        double[] llr = new double[174];
        double[] wv = new double[8];
        for (int j = 0; j < 58; j++) {
            int k = j < 29 ? 7 + j : 43 + (j - 29);
            for (int t = 0; t < 8; t++) wv[t] = amp ? Math.sqrt(S[k][t] / sigma2) * 2 : 0.5 * S[k][t] / sigma2;
            for (int b = 0; b < 3; b++) {
                int sh = 2 - b;
                double m1 = -1e30, m0 = -1e30;
                for (int t = 0; t < 8; t++) { if (((t >> sh) & 1) == 1) { if (wv[t] > m1) m1 = wv[t]; } else if (wv[t] > m0) m0 = wv[t]; }
                double s1 = 0, s0 = 0;
                for (int t = 0; t < 8; t++) { if (((t >> sh) & 1) == 1) s1 += Math.exp(wv[t] - m1); else s0 += Math.exp(wv[t] - m0); }
                llr[3 * j + b] = ((m1 + Math.log(s1)) - (m0 + Math.log(s0))) * scale;
            }
        }
        return llr;
    }

    static int[] TRUTH;   // essai : tonalités vraies
    static double TRUTHF;

    static Msg decodeCandidate(double[] br, double[] bi, int M, int speed, int s0c, double[][][] ec, double[][][] es, double[] deltas,
                               double sp, int dec, double f0, int[][] cs, int nominalMs) {
        final int nds = NDS[speed], ND = deltas.length;
        // ---- recherche fine instant / décalage de fréquence sur les 21 symboles de Costas
        double best = -1; int bs0 = s0c, bd = 8;
        double[] pw = new double[8];
        for (int s0 = s0c - nds / 3; s0 <= s0c + nds / 3; s0++) {
            if (s0 < 0 || s0 + 79 * nds > M) continue;
            for (int d = 0; d < ND; d++) {
                double num = 0, tot = 0;
                for (int blk = 0; blk < 3; blk++) for (int i = 0; i < 7; i++) {
                    int st = s0 + (blk * 36 + i) * nds;
                    tones(br, bi, st, nds, ec[d], es[d], pw);
                    double a = 0;
                    for (int t = 0; t < 8; t++) a += pw[t];
                    double e = pw[cs[blk][i]];
                    num += e; tot += a - e;
                }
                double r = num / (tot / 7 + 1e-30);
                if (r > best) { best = r; bs0 = s0; bd = d; }
            }
        }
        if (best < 1.2) return null;
        // ---- démodulation des 79 symboles
        double[][] S = new double[79][8];
        for (int k = 0; k < 79; k++) {
            if (bs0 + k * nds + nds > M) return null;
            tones(br, bi, bs0 + k * nds, nds, ec[bd], es[bd], S[k]);
        }
        if (TRUTH != null && Math.abs(f0 + deltas[bd] - TRUTHF) < 6) {
            int er = 0;
            for (int k = 0; k < 79; k++) { int am = 0; for (int t = 1; t < 8; t++) if (S[k][t] > S[k][am]) am = t; if (am != TRUTH[k]) er++; }
            System.out.printf("   [debug] f=%.2f (vrai %.2f) s0=%d delta=%.2f sync=%.2f erreurs de symbole %d/79%n", f0 + deltas[bd], TRUTHF, bs0, deltas[bd], best, er);
        }
        // bruit : médiane des puissances sur les symboles de données
        double[] all = new double[58 * 8];
        int q = 0;
        for (int k = 0; k < 79; k++) {
            if ((k >= 36 && k < 43) || k < 7 || k >= 72) continue;
            for (int t = 0; t < 8; t++) all[q++] = S[k][t];
        }
        java.util.Arrays.sort(all);
        double sigma2 = all[all.length / 2] / 0.693 + 1e-30;
        int[] cw = new int[174];
        boolean good = false;
        for (int v = 0; v < VARIANTS.length && !good; v++) {
            double[] llr = llrs(S, sigma2, VARIANTS[v][0] != 0, VARIANTS[v][1]);
            if (bp(llr, cw, 30) != 0) continue;
            int[] dd = new int[87];
            for (int i2 = 0; i2 < 87; i2++) dd[i2] = cw[87 + i2];
            if (crcOk(dd)) good = true;
        }
        if (!good) return null;
        int[] d = new int[87];
        for (int i = 0; i < 87; i++) d[i] = cw[87 + i];
        // ---- tonalités du mot de code et contrôle de la synchro
        int[] tone = new int[79];
        for (int blk = 0; blk < 3; blk++) for (int i = 0; i < 7; i++) tone[blk * 36 + i] = cs[blk][i];
        for (int j = 0; j < 58; j++) {
            int k = j < 29 ? 7 + j : 43 + (j - 29);
            tone[k] = (cw[3 * j] << 2) | (cw[3 * j + 1] << 1) | cw[3 * j + 2];
        }
        int nsync = 0;
        for (int blk = 0; blk < 3; blk++) for (int i = 0; i < 7; i++) {
            int k = blk * 36 + i, am = 0;
            for (int t = 1; t < 8; t++) if (S[k][t] > S[k][am]) am = t;
            if (am == cs[blk][i]) nsync++;
        }
        if (nsync < MIN_SYNC) return null;
        double xs = 0, xn = 0;
        for (int k = 0; k < 79; k++) { xs += S[k][tone[k]]; xn += S[k][(tone[k] + 4) & 7]; }
        double r = Math.max((xs - xn) / (xn + 1e-30), 1e-3);
        Msg m = new Msg();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            int v = 0;
            for (int b = 0; b < 6; b++) v = (v << 1) | d[i * 6 + b];
            sb.append(ALPHABET.charAt(v));
        }
        m.chars = sb.toString();
        m.i3 = (d[72] << 2) | (d[73] << 1) | d[74];
        m.nsync = nsync;
        m.s0 = bs0; m.di = bd; m.tone = tone;
        m.snr = (int) Math.round(10 * Math.log10(r * sp / 2500));
        m.dt = (bs0 * (double) dec * 1000 / 12000 - nominalMs) / 1000.0;
        m.freq = f0 + deltas[bd];
        return m;
    }

    static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-+";

    /** Puissances des 8 tonalités du symbole commençant à l'échantillon st (bande de base). */
    static void tones(double[] br, double[] bi, int st, int nds, double[][] ec, double[][] es, double[] out) {
        for (int t = 0; t < 8; t++) {
            double[] c = ec[t], s = es[t];
            double ar = 0, ai = 0;
            for (int k = 0; k < nds; k++) {
                double xr = br[st + k], xi = bi[st + k];
                ar += xr * c[k] - xi * s[k];
                ai += xr * s[k] + xi * c[k];
            }
            out[t] = ar * ar + ai * ai;
        }
    }

    // ------------------------------------------------------------------ émission (banc d'essai uniquement)
    /** 79 tonalités pour 12 caractères (alphabet JS8) et un type de trame. */
    static int[] encode(String chars, int i3, boolean original) {
        int[] bits = new int[88];
        for (int i = 0; i < 12; i++) {
            int v = ALPHABET.indexOf(chars.charAt(i));
            for (int b = 0; b < 6; b++) bits[i * 6 + b] = (v >> (5 - b)) & 1;
        }
        bits[72] = (i3 >> 2) & 1; bits[73] = (i3 >> 1) & 1; bits[74] = i3 & 1;
        int crc = crc12(bits);
        for (int i = 0; i < 12; i++) bits[75 + i] = (crc >> (11 - i)) & 1;
        int[] par = new int[87];
        for (int i = 0; i < 87; i++) {
            int p = 0;
            String row = GEN[i];
            for (int j = 0; j < 87; j++) {
                int nib = Character.digit(row.charAt(j / 4), 16);
                if (((nib >> (3 - (j % 4))) & 1) != 0) p ^= bits[j];
            }
            par[i] = p;
        }
        int[][] cs = original ? COSTAS_ORIG : COSTAS_MOD;
        int[] tone = new int[79];
        for (int blk = 0; blk < 3; blk++) for (int i = 0; i < 7; i++) tone[blk * 36 + i] = cs[blk][i];
        for (int j = 0; j < 29; j++) {
            tone[7 + j] = (par[3 * j] << 2) | (par[3 * j + 1] << 1) | par[3 * j + 2];
            tone[43 + j] = (bits[3 * j] << 2) | (bits[3 * j + 1] << 1) | bits[3 * j + 2];
        }
        return tone;
    }
}
