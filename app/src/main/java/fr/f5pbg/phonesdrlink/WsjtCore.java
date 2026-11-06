package fr.f5pbg.phonesdrlink;

// Outils communs aux décodeurs WSPR et JT9 : FFT, passage en bande de base, code convolutif K=32 r=1/2 et décodeur de Fano.
final class WsjtCore {
    static final int POLY1 = 0xf2d05351, POLY2 = 0xe4613c47;

    // ---------------------------------------------------------------- FFT complexe (taille puissance de 2)
    static final class Fft {
        final int n;
        final double[] cs, sn;
        final int[] rev;
        Fft(int n) {
            this.n = n;
            cs = new double[n / 2];
            sn = new double[n / 2];
            for (int i = 0; i < n / 2; i++) { cs[i] = Math.cos(2 * Math.PI * i / n); sn[i] = -Math.sin(2 * Math.PI * i / n); }
            rev = new int[n];
            int bits = Integer.numberOfTrailingZeros(n);
            for (int i = 0; i < n; i++) rev[i] = Integer.reverse(i) >>> (32 - bits);
        }
        void run(double[] re, double[] im) {
            for (int i = 0; i < n; i++) {
                int j = rev[i];
                if (j > i) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
            }
            for (int len = 2; len <= n; len <<= 1) {
                int half = len >> 1, step = n / len;
                for (int i = 0; i < n; i += len) {
                    for (int k = 0, w = 0; k < half; k++, w += step) {
                        int a = i + k, b = a + half;
                        double xr = re[b] * cs[w] - im[b] * sn[w], xi = re[b] * sn[w] + im[b] * cs[w];
                        re[b] = re[a] - xr; im[b] = im[a] - xi;
                        re[a] += xr; im[a] += xi;
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- bande de base
    static double[] lowpass(double cut, double fs, int taps) {
        double[] h = new double[taps];
        int m = taps / 2;
        double wc = 2 * Math.PI * cut / fs, sum = 0;
        for (int i = 0; i < taps; i++) {
            int k = i - m;
            double s = k == 0 ? wc / Math.PI : Math.sin(wc * k) / (Math.PI * k);
            double w = 0.42 - 0.5 * Math.cos(2 * Math.PI * i / (taps - 1)) + 0.08 * Math.cos(4 * Math.PI * i / (taps - 1));
            h[i] = s * w;
            sum += h[i];
        }
        for (int i = 0; i < taps; i++) h[i] /= sum;
        return h;
    }

    /** Multiplie x[off .. off+n[ par exp(-j 2π fc t), filtre (sinc fenêtrée, coupure cut Hz, taps impair) et décime par dec (taux 12000/dec). */
    static void mixDecimate(short[] x, int off, int n, double fc, int dec, double cut, int taps, double[] re, double[] im, int nOut) {
        double[] h = lowpass(cut, 12000.0, taps);
        int m = taps / 2;
        double dw = 2 * Math.PI * fc / 12000.0;
        final int CH = 512;
        double[] cr = new double[CH * dec + taps], ci = new double[CH * dec + taps];
        for (int o0 = 0; o0 < nOut; o0 += CH) {
            int o1 = Math.min(nOut, o0 + CH);
            int i0 = o0 * dec - m, len = (o1 - o0 - 1) * dec + taps;
            double ph = dw * i0;
            double pr = Math.cos(ph), pi = -Math.sin(ph), rr = Math.cos(dw), ri = -Math.sin(dw);
            for (int i = 0; i < len; i++) {
                int idx = i0 + i;
                double v = idx >= 0 && idx < n ? x[off + idx] : 0;
                cr[i] = v * pr;
                ci[i] = v * pi;
                double nr = pr * rr - pi * ri, ni = pr * ri + pi * rr;
                pr = nr; pi = ni;
            }
            for (int o = o0; o < o1; o++) {
                int a = (o - o0) * dec;
                double sr = 0, si = 0;
                for (int k = 0; k < taps; k++) { sr += h[k] * cr[a + k]; si += h[k] * ci[a + k]; }
                re[o] = sr; im[o] = si;
            }
        }
    }

    /** Filtre passe-bas (coupure cut Hz, taux fs) et décimation par dec d'un signal complexe. */
    static void decimate(double[] xr, double[] xi, int n, double fs, int dec, double cut, int taps, double[] re, double[] im, int nOut) {
        double[] h = lowpass(cut, fs, taps);
        int m = taps / 2;
        for (int o = 0; o < nOut; o++) {
            int a = o * dec - m;
            double sr = 0, si = 0;
            for (int k = 0; k < taps; k++) {
                int idx = a + k;
                if (idx < 0 || idx >= n) continue;
                sr += h[k] * xr[idx]; si += h[k] * xi[idx];
            }
            re[o] = sr; im[o] = si;
        }
    }

    // ---------------------------------------------------------------- code convolutif
    static int parity(int v) { return Integer.bitCount(v) & 1; }

    /** bits[0..nb[ (info + queue de zéros) -> 2*nb symboles (0/1). */
    static int[] encode(int[] bits, int nb) {
        int[] s = new int[2 * nb];
        int st = 0;
        for (int i = 0; i < nb; i++) {
            st = (st << 1) | bits[i];
            s[2 * i] = parity(st & POLY1);
            s[2 * i + 1] = parity(st & POLY2);
        }
        return s;
    }

    // Table de métrique (wsprd / WSJT-X, 256 valeurs : indexée par le symbole reçu 0..255, pour le bit 1 ; le bit 0 utilise 255-i).
    static final float[] METRIC = {
        0.9999f, 0.9998f, 0.9998f, 0.9998f, 0.9998f, 0.9998f, 0.9997f, 0.9997f, 0.9997f, 0.9997f,
        0.9997f, 0.9996f, 0.9996f, 0.9996f, 0.9995f, 0.9995f, 0.9994f, 0.9994f, 0.9994f, 0.9993f,
        0.9993f, 0.9992f, 0.9991f, 0.9991f, 0.9990f, 0.9989f, 0.9988f, 0.9988f, 0.9988f, 0.9986f,
        0.9985f, 0.9984f, 0.9983f, 0.9982f, 0.9980f, 0.9979f, 0.9977f, 0.9976f, 0.9974f, 0.9971f,
        0.9969f, 0.9968f, 0.9965f, 0.9962f, 0.9960f, 0.9957f, 0.9953f, 0.9950f, 0.9947f, 0.9941f,
        0.9937f, 0.9933f, 0.9928f, 0.9922f, 0.9917f, 0.9911f, 0.9904f, 0.9897f, 0.9890f, 0.9882f,
        0.9874f, 0.9863f, 0.9855f, 0.9843f, 0.9832f, 0.9819f, 0.9806f, 0.9792f, 0.9777f, 0.9760f,
        0.9743f, 0.9724f, 0.9704f, 0.9683f, 0.9659f, 0.9634f, 0.9609f, 0.9581f, 0.9550f, 0.9516f,
        0.9481f, 0.9446f, 0.9406f, 0.9363f, 0.9317f, 0.9270f, 0.9218f, 0.9160f, 0.9103f, 0.9038f,
        0.8972f, 0.8898f, 0.8822f, 0.8739f, 0.8647f, 0.8554f, 0.8457f, 0.8357f, 0.8231f, 0.8115f,
        0.7984f, 0.7854f, 0.7704f, 0.7556f, 0.7391f, 0.7210f, 0.7038f, 0.6840f, 0.6633f, 0.6408f,
        0.6174f, 0.5939f, 0.5678f, 0.5410f, 0.5137f, 0.4836f, 0.4524f, 0.4193f, 0.3850f, 0.3482f,
        0.3132f, 0.2733f, 0.2315f, 0.1891f, 0.1435f, 0.0980f, 0.0493f, 0.0000f, -0.0510f, -0.1052f,
        -0.1593f, -0.2177f, -0.2759f, -0.3374f, -0.4005f, -0.4599f, -0.5266f, -0.5935f, -0.6626f, -0.7328f,
        -0.8051f, -0.8757f, -0.9498f, -1.0271f, -1.1019f, -1.1816f, -1.2642f, -1.3459f, -1.4295f, -1.5077f,
        -1.5958f, -1.6818f, -1.7647f, -1.8548f, -1.9387f, -2.0295f, -2.1152f, -2.2154f, -2.3011f, -2.3904f,
        -2.4820f, -2.5786f, -2.6730f, -2.7652f, -2.8616f, -2.9546f, -3.0526f, -3.1445f, -3.2445f, -3.3416f,
        -3.4357f, -3.5325f, -3.6324f, -3.7313f, -3.8225f, -3.9209f, -4.0248f, -4.1278f, -4.2261f, -4.3193f,
        -4.4220f, -4.5262f, -4.6214f, -4.7242f, -4.8234f, -4.9245f, -5.0298f, -5.1250f, -5.2232f, -5.3267f,
        -5.4332f, -5.5342f, -5.6431f, -5.7270f, -5.8401f, -5.9350f, -6.0407f, -6.1418f, -6.2363f, -6.3384f,
        -6.4536f, -6.5429f, -6.6582f, -6.7433f, -6.8438f, -6.9478f, -7.0789f, -7.1894f, -7.2714f, -7.3815f,
        -7.4810f, -7.5575f, -7.6852f, -7.8071f, -7.8580f, -7.9724f, -8.1000f, -8.2207f, -8.2867f, -8.4017f,
        -8.5287f, -8.6347f, -8.7082f, -8.8319f, -8.9448f, -9.0355f, -9.1885f, -9.2095f, -9.2863f, -9.4186f,
        -9.5064f, -9.6386f, -9.7207f, -9.8286f, -9.9453f, -10.0701f, -10.1735f, -10.3001f, -10.2858f, -10.5427f,
        -10.5982f, -10.7361f, -10.7042f, -10.9212f, -11.0097f, -11.0469f, -11.1155f, -11.2812f, -11.3472f, -11.4988f,
        -11.5327f, -11.6692f, -11.9376f, -11.8606f, -12.1372f, -13.2539f };

    // Les symboles reçus sont des octets 0..255 : 128 = indécis ; > 127 = « 1 » probable. mettab[b][r] = métrique du symbole émis b, reçu r.
    // (Dans la table de wsprd, l'index 0 de mettab correspond au bit émis 0 : valeur élevée quand le reçu est petit.) On suit la même convention.
    static final int[][] MT = new int[2][256];
    static {
        for (int i = 0; i < 256; i++) {
            MT[0][i] = (int) Math.round(10 * (METRIC[i] - 0.45));
            MT[1][i] = (int) Math.round(10 * (METRIC[255 - i] - 0.45));
        }
    }

    /**
     * Décodeur séquentiel de Fano (algorithme de Karn). sym : 2*nbits octets 0..255 ; nbits : bits d'information + queue (les 31 derniers sont à zéro).
     * Renvoie la métrique finale ou Integer.MIN_VALUE en cas d'échec (limite de cycles) ; data[] reçoit les bits (0/1).
     */
    static int fano(int[] sym, int nbits, int delta, long maxCycles, int[] data) {
        final int tail = nbits - 31;
        int[] enc = new int[nbits + 1], gam = new int[nbits + 1], ibr = new int[nbits + 1];
        int[][] tm = new int[2][nbits + 1];     // métriques des branches classées (0 : meilleure)
        int[] tb = new int[nbits + 1];          // bit de la meilleure branche (la seconde porte l'autre bit)
        int np = 0, t = 0;
        enc[0] = 0; gam[0] = 0; ibr[0] = 0;
        branch(sym, 0, enc[0], tm, tb, tail);
        long cycles = 0;
        while (true) {
            if (++cycles > maxCycles) return Integer.MIN_VALUE;
            int ng = gam[np] + tm[ibr[np]][np];
            if (ng >= t) {
                // pas en avant
                if (gam[np] < t + delta) { while (ng >= t + delta) t += delta; }
                int bit = ibr[np] == 0 ? tb[np] : 1 - tb[np];
                data[np] = bit;
                int ne = (enc[np] << 1) | bit;
                np++;
                gam[np] = ng; enc[np] = ne;
                if (np == nbits) return ng;
                ibr[np] = 0;
                branch(sym, np, ne, tm, tb, tail);
                continue;
            }
            // pas en arrière
            while (true) {
                if (np == 0 || gam[np - 1] < t) {
                    t -= delta;
                    ibr[np] = 0;
                    break;
                }
                np--;
                if (ibr[np] == 0 && np < tail) { ibr[np] = 1; break; }
            }
        }
    }

    // Métriques des deux branches du noeud k (état e) : la meilleure d'abord. Dans la queue, seule la branche « 0 » existe.
    private static void branch(int[] sym, int k, int e, int[][] tm, int[] tb, int tail) {
        int r1 = sym[2 * k], r2 = sym[2 * k + 1];
        int s0 = e << 1, s1 = s0 | 1;
        int m0 = MT[parity(s0 & POLY1)][r1] + MT[parity(s0 & POLY2)][r2];
        int m1 = MT[parity(s1 & POLY1)][r1] + MT[parity(s1 & POLY2)][r2];
        if (k >= tail) { tm[0][k] = m0; tm[1][k] = Integer.MIN_VALUE / 4; tb[k] = 0; return; }
        if (m0 >= m1) { tm[0][k] = m0; tm[1][k] = m1; tb[k] = 0; }
        else { tm[0][k] = m1; tm[1][k] = m0; tb[k] = 1; }
    }

    // ---------------------------------------------------------------- entrelacement par inversion de bits (WSPR : 162 sur 256 ; JT9 : 206 sur 256)
    static int[] bitRevTable(int count) {
        int[] j0 = new int[count];
        int k = 0;
        for (int i = 0; i < 256; i++) {
            int n = Integer.reverse(i) >>> 24;
            if (n < count) j0[k++] = n;
        }
        return j0;
    }

    /** Normalisation des valeurs souples v[] (positif = « 1 ») vers des octets 0..255 centrés sur 128, écart-type ramené à fac. */
    static int[] toBytes(double[] v, double fac) {
        int n = v.length;
        double mean = 0, sq = 0;
        for (int i = 0; i < n; i++) mean += v[i];
        mean /= n;
        for (int i = 0; i < n; i++) sq += (v[i] - mean) * (v[i] - mean);
        double sd = Math.sqrt(sq / n);
        if (sd < 1e-12) sd = 1;
        int[] o = new int[n];
        for (int i = 0; i < n; i++) {
            double x = (v[i] - mean) * fac / sd;
            o[i] = (int) Math.max(0, Math.min(255, Math.round(x + 128)));
        }
        return o;
    }

    private WsjtCore() { }
}
