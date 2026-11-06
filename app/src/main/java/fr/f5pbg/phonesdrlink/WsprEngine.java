package fr.f5pbg.phonesdrlink;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

// Décodeur WSPR (réception seule) : tranches de 120 s qui commencent à chaque minute paire UTC (+1 s), quatre tonalités (FSK à 4 états,
// 1,4648 Hz d'écart, 162 symboles de 0,6827 s) entre 1400 et 1600 Hz, message de 50 bits (indicatif, locator de 4 caractères, puissance en dBm)
// protégé par un code convolutif (K=32, r=1/2) décodé par l'algorithme de Fano. Seuls les messages de type 1 (indicatif standard + locator
// + puissance) sont décodés ; les types 2 et 3 (préfixes, indicatifs hachés) sont ignorés.
// Méthode : bande de base à 375 Hz autour de 1500 Hz ; spectrogramme (fenêtre d'un symbole) pour trouver (fréquence, instant) par corrélation
// avec le vecteur de synchro ; affinage par démodulation directe (4 DFT par symbole) ; métriques souples ; Fano ; contrôle de plausibilité.
final class WsprEngine extends SlotDecoder {
    static final int[] PR3 = {
        1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 0,
        0, 1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1,
        0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 0, 1,
        1, 0, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 0, 1,
        0, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 1, 0, 0, 0, 1, 0,
        0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1,
        0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1, 1,
        0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 0, 1, 1, 0,
        0, 0 };
    static final int[] J0 = WsjtCore.bitRevTable(162);
    static final double DF = 12000.0 / 8192.0;   // écart entre tonalités (Hz)
    static final double FS = 375.0;              // taux de la bande de base
    static final int SYM = 256;                  // échantillons par symbole à 375 Hz
    static final int HOP = 32;
    static final int MAX_ERR = 72;               // erreurs de bits durs tolérées sur 162 (le bruit seul en donne ~81)
    static final long MAX_CYCLES = 81L * 3000;
    static double CAND_MIN = 0.07;
    static int MAX_CAND = 40;
    static int EXCL = 3;                         // candidats : écart minimal (bins de 0,73 Hz) entre deux maxima de corrélation

    static final class Msg {
        String text; int snr, errs; double dt, freq;
    }

    WsprEngine(Ft8Engine.Listener l) { super("WSPR", 120000, 3000, 118000, l); }

    String timeText(Calendar c) { return String.format(Locale.US, "%02d%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)); }

    Result decode(short[] win, int n, long slotStartMs, String t) {
        Result r = new Result();
        for (Msg m : decodeWindow(win, n, preMs + 1000)) {
            r.lines.add(String.format(Locale.US, "%s %+4d %+5.1f %4d  %s", t, m.snr, m.dt, Math.round(m.freq), m.text));
            r.dts.add(m.dt);
        }
        return r;
    }

    // ------------------------------------------------------------------ décodage d'une fenêtre (12 kHz) ; nominalMs : instant nominal du début du signal dans la fenêtre
    static ArrayList<Msg> decodeWindow(short[] win, int n, int nominalMs) {
        ArrayList<Msg> out = new ArrayList<Msg>();
        int n1 = n / 8;
        double[] r1 = new double[n1], i1 = new double[n1];
        WsjtCore.mixDecimate(win, 0, n, 1500, 8, 500, 97, r1, i1, n1);
        int nb = n1 / 4;
        double[] br = new double[nb], bi = new double[nb];
        WsjtCore.decimate(r1, i1, n1, 1500, 4, 165, 161, br, bi, nb);
        r1 = null; i1 = null;
        int nf = (nb - SYM) / HOP + 1;
        if (nf < 8 * 162 + 10) return out;
        // spectrogramme : bins de 0,732 Hz (FFT de 512 sur 256 échantillons)
        float[][] P = new float[nf][512];
        WsjtCore.Fft fft = new WsjtCore.Fft(512);
        double[] xr = new double[512], xi = new double[512];
        for (int m = 0; m < nf; m++) {
            for (int i = 0; i < SYM; i++) { xr[i] = br[m * HOP + i]; xi[i] = bi[m * HOP + i]; }
            for (int i = SYM; i < 512; i++) { xr[i] = 0; xi[i] = 0; }
            fft.run(xr, xi);
            float[] p = P[m];
            for (int b = 0; b < 512; b++) p[b] = (float) (xr[b] * xr[b] + xi[b] * xi[b]);
        }
        int nomF = (int) Math.round(nominalMs * FS / 1000.0 / HOP);
        int mLo = Math.max(0, nomF - 36), mHi = Math.min(nf - 1 - 8 * 161, nomF + 36);
        final int bLo = -200, bHi = 200 - 8;
        double[] bestR = new double[bHi - bLo + 1];
        int[] bestM = new int[bHi - bLo + 1];
        for (int b0 = bLo; b0 <= bHi; b0++) {
            int i0 = b0 & 511, i1b = (b0 + 2) & 511, i2 = (b0 + 4) & 511, i3 = (b0 + 6) & 511;
            double br0 = -1; int bm = mLo;
            for (int m0 = mLo; m0 <= mHi; m0++) {
                double sc = 0, nr = 1e-30;
                for (int k = 0; k < 162; k++) {
                    float[] p = P[m0 + 8 * k];
                    double d = p[i1b] + p[i3] - p[i0] - p[i2];
                    sc += PR3[k] == 1 ? d : -d;
                    nr += p[i0] + p[i1b] + p[i2] + p[i3];
                }
                double rr = sc / nr;
                if (rr > br0) { br0 = rr; bm = m0; }
            }
            bestR[b0 - bLo] = br0; bestM[b0 - bLo] = bm;
        }
        P = null;
        // candidats : maxima de la corrélation, espacés d'au moins 6 bins
        ArrayList<int[]> cands = new ArrayList<int[]>();
        boolean[] used = new boolean[bestR.length];
        for (int c = 0; c < MAX_CAND; c++) {
            int bi0 = -1; double bv = CAND_MIN;
            for (int i = 0; i < bestR.length; i++) if (!used[i] && bestR[i] > bv) { bv = bestR[i]; bi0 = i; }
            if (bi0 < 0) break;
            for (int i = Math.max(0, bi0 - EXCL); i <= Math.min(bestR.length - 1, bi0 + EXCL); i++) used[i] = true;
            cands.add(new int[] { bi0 + bLo, bestM[bi0] });
        }
        double[] pw = new double[162 * 4];
        for (int[] c : cands) {
            double f0 = c[0] * FS / 512.0;
            int t0 = c[1] * HOP;
            // affinage : fréquence ±0,37 Hz, instant ±16 échantillons
            double bf = f0, bs = -2; int bt = t0;
            for (int dt = -16; dt <= 16; dt += 4) {
                for (int df = -3; df <= 3; df++) {
                    double f = f0 + df * 0.12;
                    int tt = t0 + dt;
                    if (tt < 0 || tt + 162 * SYM > nb) continue;
                    double s = demod(br, bi, f, tt, 0, pw);
                    if (s > bs) { bs = s; bf = f; bt = tt; }
                }
            }
            double[] drifts = { 0, 1, -1, 2, -2 };
            for (double drift : drifts) {
                Msg m = tryDecode(br, bi, bf, bt, drift, pw);
                if (m == null) continue;
                m.dt = bt / FS - nominalMs / 1000.0;
                m.freq = 1500 + bf + 1.5 * DF;
                boolean dup = false;
                for (Msg o : out) if (o.text.equals(m.text)) { dup = true; if (m.snr > o.snr) { o.snr = m.snr; o.dt = m.dt; o.freq = m.freq; } }
                if (!dup) out.add(m);
                break;
            }
        }
        return out;
    }

    /** Démodulation directe à partir de l'instant t (échantillons à 375 Hz) : pw[4k+j] = puissance de la tonalité j au symbole k. Renvoie le score de synchro normalisé. */
    static double demod(double[] br, double[] bi, double f0, int t, double drift, double[] pw) {
        double[] cs = TC, sn = TS;
        double sc = 0, nr = 1e-30;
        for (int k = 0; k < 162; k++) {
            double f = f0 + drift * (k - 80.5) / 161.0;
            double w = -2 * Math.PI * f / FS, wr = Math.cos(w), wi = Math.sin(w);
            double pr = 1, pi = 0;
            double a0r = 0, a0i = 0, a1r = 0, a1i = 0, a2r = 0, a2i = 0, a3r = 0, a3i = 0;
            int s = t + k * SYM;
            for (int n = 0; n < SYM; n++) {
                double xr = br[s + n], xi = bi[s + n];
                double yr = xr * pr - xi * pi, yi = xr * pi + xi * pr;
                a0r += yr; a0i += yi;
                int i1 = n & 255, i2 = (2 * n) & 255, i3 = (3 * n) & 255;
                a1r += yr * cs[i1] - yi * sn[i1]; a1i += yr * sn[i1] + yi * cs[i1];
                a2r += yr * cs[i2] - yi * sn[i2]; a2i += yr * sn[i2] + yi * cs[i2];
                a3r += yr * cs[i3] - yi * sn[i3]; a3i += yr * sn[i3] + yi * cs[i3];
                double nr2 = pr * wr - pi * wi; pi = pr * wi + pi * wr; pr = nr2;
            }
            double p0 = a0r * a0r + a0i * a0i, p1 = a1r * a1r + a1i * a1i, p2 = a2r * a2r + a2i * a2i, p3 = a3r * a3r + a3i * a3i;
            pw[4 * k] = p0; pw[4 * k + 1] = p1; pw[4 * k + 2] = p2; pw[4 * k + 3] = p3;
            double d = p1 + p3 - p0 - p2;
            sc += PR3[k] == 1 ? d : -d;
            nr += p0 + p1 + p2 + p3;
        }
        return sc / nr;
    }

    // tonalité j du symbole : exp(-j 2π j n / 256)
    static final double[] TC = new double[256], TS = new double[256];
    static {
        for (int i = 0; i < 256; i++) { TC[i] = Math.cos(2 * Math.PI * i / 256); TS[i] = -Math.sin(2 * Math.PI * i / 256); }
    }

    static Msg tryDecode(double[] br, double[] bi, double f, int t, double drift, double[] pw) {
        demod(br, bi, f, t, drift, pw);
        double[] v = new double[162];
        for (int k = 0; k < 162; k++) {
            int s = PR3[k];
            v[k] = Math.sqrt(pw[4 * k + s + 2]) - Math.sqrt(pw[4 * k + s]);
        }
        double[] d = new double[162];
        for (int p = 0; p < 162; p++) d[p] = v[J0[p]];
        int[] sym = WsjtCore.toBytes(d, 50);
        int[] data = new int[81];
        int met = WsjtCore.fano(sym, 81, 60, MAX_CYCLES, data);
        if (met == Integer.MIN_VALUE) return null;
        int[] enc = WsjtCore.encode(data, 81);
        int errs = 0;
        for (int p = 0; p < 162; p++) if ((sym[p] > 127 ? 1 : 0) != enc[p]) errs++;
        if (errs > MAX_ERR) return null;
        String text = unpack(data);
        if (text == null) return null;
        // SNR (référence 2500 Hz) : puissance de la tonalité émise contre le bruit moyen des trois autres
        int[] tx = new int[162];
        for (int p = 0; p < 162; p++) tx[J0[p]] = enc[p];
        double sig = 0, noi = 0;
        for (int k = 0; k < 162; k++) {
            int tone = PR3[k] + 2 * tx[k];
            double tot = pw[4 * k] + pw[4 * k + 1] + pw[4 * k + 2] + pw[4 * k + 3];
            sig += pw[4 * k + tone];
            noi += (tot - pw[4 * k + tone]) / 3;
        }
        sig /= 162; noi /= 162;
        double ps = Math.max(sig - noi, noi * 0.01);
        Msg m = new Msg();
        m.text = text;
        m.errs = errs;
        m.snr = (int) Math.round(Math.max(-35, Math.min(30, 10 * Math.log10(ps / noi) - 10 * Math.log10(2500.0 / DF))));
        return m;
    }

    // ------------------------------------------------------------------ message de 50 bits (type 1) ; null si invalide
    static final String CH = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ ";

    static String unpack(int[] bits) {
        long n1 = 0, n2 = 0;
        for (int i = 0; i < 28; i++) n1 = (n1 << 1) | bits[i];
        for (int i = 28; i < 50; i++) n2 = (n2 << 1) | bits[i];
        if (n1 >= 262177560L) return null;
        long ng = n2 >> 7;
        int ntype = (int) (n2 & 127) - 64;
        if (ng >= 32400 || ntype < 0 || ntype > 60 || (ntype % 10 != 0 && ntype % 10 != 3 && ntype % 10 != 7)) return null;
        String call = callFrom(n1);
        if (call == null) return null;
        int dlat = (int) (ng % 180) - 90;
        int dlong = (int) (ng / 180) * 2 - 180 + 2;
        int nlong = (int) (60.0 * (180.0 - dlong) / 5.0);
        int g1 = nlong / 240, g2 = (nlong - 240 * g1) / 24;
        int nlat = (int) (60.0 * (dlat + 90) / 2.5);
        int g3 = nlat / 240, g4 = (nlat - 240 * g3) / 24;
        String grid = "" + CH.charAt(10 + g1) + CH.charAt(10 + g3) + CH.charAt(g2) + CH.charAt(g4);
        return call + " " + grid + " " + ntype;
    }

    /** Indicatif standard de 28 bits : texte sans espaces, ou null s il est invalide. */
    static String callFrom(long n1) {
        if (n1 < 0 || n1 >= 262177560L) return null;
        char[] c = new char[6];
        long n = n1;
        c[5] = CH.charAt((int) (n % 27) + 10); n /= 27;
        c[4] = CH.charAt((int) (n % 27) + 10); n /= 27;
        c[3] = CH.charAt((int) (n % 27) + 10); n /= 27;
        c[2] = CH.charAt((int) (n % 10)); n /= 10;
        c[1] = CH.charAt((int) (n % 36)); n /= 36;
        c[0] = CH.charAt((int) n);
        String call = new String(c);
        return plausibleCall(call) ? call.trim() : null;
    }

    /** Indicatif de 6 caractères (complété par des espaces) : le 3e est un chiffre ; espaces seulement au début (en 1re place) et à la fin. */
    static boolean plausibleCall(String c) {
        if (c.length() != 6 || !Character.isDigit(c.charAt(2))) return false;
        char c0 = c.charAt(0), c1 = c.charAt(1);
        if (c1 == ' ') return false;
        if (c0 == ' ' && !Character.isLetter(c1)) return false;
        if (Character.isDigit(c0) && !Character.isLetter(c1)) return false;
        boolean sp = false;
        int letters = 0;
        for (int i = 3; i < 6; i++) {
            char x = c.charAt(i);
            if (x == ' ') sp = true;
            else if (sp || !Character.isLetter(x)) return false;
            else letters++;
        }
        return letters >= 1;
    }
}
