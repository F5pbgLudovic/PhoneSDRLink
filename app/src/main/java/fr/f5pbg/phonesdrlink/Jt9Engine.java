package fr.f5pbg.phonesdrlink;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

// Décodeur JT9 (sous-mode A, réception seule) : tranches de 60 s (signal de 49 s à partir de 1 s), FSK à 9 tonalités (1,736 Hz d'écart,
// 85 symboles de 0,576 s dont 16 de synchro sur la tonalité 0), message de 72 bits (comme JT65 : deux indicatifs de 28 bits + locator,
// report ou 73 sur 16 bits) protégé par le même code convolutif K=32 r=1/2 que WSPR (décodeur de Fano), entrelacement par inversion de bits,
// code de Gray sur les groupes de 3 bits. Seuls les messages standard sont décodés (CQ / QRZ / DE ou indicatif, indicatif, locator / report / RRR / 73) ;
// le texte libre et les indicatifs composés sont ignorés.
// Méthode : spectrogramme (FFT 16384 sur un symbole, pas d'un demi-symbole) ; la tonalité 0 n'est jamais émise sur les symboles de données, donc
// « énergie aux positions de synchro − énergie aux positions de données » repère fréquence et instant ; affinage en bande de base (750 Hz) ;
// démodulation des 9 tonalités ; métriques souples par bit ; Fano ; contrôle de plausibilité.
final class Jt9Engine extends SlotDecoder {
    static final int[] ISYNC_POS = { 1, 2, 5, 10, 16, 23, 33, 35, 51, 52, 55, 60, 66, 73, 83, 85 };   // positions (à partir de 1) des symboles de synchro
    static final boolean[] SYNC = new boolean[85];
    static {
        for (int p : ISYNC_POS) SYNC[p - 1] = true;
    }
    static final int[] J0 = WsjtCore.bitRevTable(206);
    static final int SYMLEN = 6912;
    static final double DF = 12000.0 / SYMLEN;
    static final int BSYM = 432;            // échantillons par symbole à 750 Hz
    static final int NFFT = 16384, HOP = 3456;
    static double MAX_ERR_FRAC = 0.40;      // part de bits durs en désaccord tolérée (le bruit seul : 0,5)
    static double CAND_MIN = 1.6;
    static int MAX_CAND = 20;
    static double SYMFAC = 60;
    static final long MAX_CYCLES = 103L * 3000;
    static final long NBASE = 262177560L;

    static final class Msg {
        String text; int snr, errs; double dt, freq;
    }

    Jt9Engine(Ft8Engine.Listener l) { super("JT9", 60000, 2500, 54000, l); }

    String timeText(Calendar c) { return String.format(Locale.US, "%02d%02d%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND)); }

    Result decode(short[] win, int n, long slotStartMs, String t) {
        Result r = new Result();
        for (Msg m : decodeWindow(win, n, preMs + 1000)) {
            r.lines.add(String.format(Locale.US, "%s %+4d %+5.1f %4d  %s", t, m.snr, m.dt, Math.round(m.freq), m.text));
            r.dts.add(m.dt);
        }
        return r;
    }

    // ------------------------------------------------------------------ décodage d'une fenêtre (12 kHz)
    static ArrayList<Msg> decodeWindow(short[] win, int n, int nominalMs) {
        ArrayList<Msg> out = new ArrayList<Msg>();
        int nf = (n - SYMLEN) / HOP + 1;
        if (nf < 2 * 84 + 4) return out;
        final int bLo = (int) (300 / (12000.0 / NFFT)), bHi = (int) (2900 / (12000.0 / NFFT)), nb = bHi - bLo + 1;
        float[][] P = new float[nf][nb];
        WsjtCore.Fft fft = new WsjtCore.Fft(NFFT);
        double[] xr = new double[NFFT], xi = new double[NFFT];
        for (int m = 0; m < nf; m += 2) {   // deux trames par FFT complexe (réelle + imaginaire)
            boolean two = m + 1 < nf;
            for (int i = 0; i < SYMLEN; i++) { xr[i] = win[m * HOP + i]; xi[i] = two ? win[(m + 1) * HOP + i] : 0; }
            for (int i = SYMLEN; i < NFFT; i++) { xr[i] = 0; xi[i] = 0; }
            fft.run(xr, xi);
            for (int b = 0; b < nb; b++) {
                int k = b + bLo, k2 = (NFFT - k) & (NFFT - 1);
                double ar = (xr[k] + xr[k2]) * 0.5, ai = (xi[k] - xi[k2]) * 0.5;
                P[m][b] = (float) (ar * ar + ai * ai);
                if (two) {
                    double cr = (xi[k] + xi[k2]) * 0.5, ci = (xr[k] - xr[k2]) * 0.5;
                    P[m + 1][b] = (float) (cr * cr + ci * ci);
                }
            }
        }
        int nomF = (int) Math.round(nominalMs * 12.0 / HOP);
        int mLo = Math.max(0, nomF - 11), mHi = Math.min(nf - 1 - 2 * 84, nomF + 11);
        if (mHi < mLo) return out;
        int nbt = nb - 19;   // le signal occupe 9 tonalités : 8 × 1,736 Hz = 19 bins
        double[] bestR = new double[nbt];
        int[] bestM = new int[nbt];
        final int span = 0;
        for (int b = 0; b < nbt; b++) {
            double br0 = -1e9; int bm = mLo;
            for (int m0 = mLo; m0 <= mHi; m0++) {
                double s1 = 0, s2 = 0, tot = 1e-30;
                for (int k = 0; k < 85; k++) {
                    double p = P[m0 + 2 * k][b + span];
                    tot += p;
                    if (SYNC[k]) s1 += p; else s2 += p;
                }
                double rr = (s1 / 16 - s2 / 69) / (tot / 85);
                if (rr > br0) { br0 = rr; bm = m0; }
            }
            bestR[b] = br0; bestM[b] = bm;
        }
        P = null;
        ArrayList<int[]> cands = new ArrayList<int[]>();
        boolean[] used = new boolean[nbt];
        for (int c = 0; c < MAX_CAND; c++) {
            int bi0 = -1; double bv = CAND_MIN;
            for (int i = 0; i < nbt; i++) if (!used[i] && bestR[i] > bv) { bv = bestR[i]; bi0 = i; }
            if (bi0 < 0) break;
            for (int i = Math.max(0, bi0 - 8); i <= Math.min(nbt - 1, bi0 + 8); i++) used[i] = true;
            cands.add(new int[] { bi0 + span + bLo, bestM[bi0] });
        }
        for (int[] c : cands) {
            double f0 = c[0] * 12000.0 / NFFT;   // fréquence (Hz) de la tonalité 0, précision 0,37 Hz
            Msg m = decodeCandidate(win, n, f0, c[1] * HOP, nominalMs);
            if (m == null) continue;
            boolean dup = false;
            for (Msg o : out) if (o.text.equals(m.text)) { dup = true; if (m.snr > o.snr) { o.snr = m.snr; o.dt = m.dt; o.freq = m.freq; } }
            if (!dup) out.add(m);
        }
        return out;
    }

    static Msg decodeCandidate(short[] win, int n, double f0, int tStart, int nominalMs) {
        double fc = f0 + 4 * DF;   // centre des 9 tonalités
        int nbb = n / 16;
        double[] br = new double[nbb], bi = new double[nbb];
        WsjtCore.mixDecimate(win, 0, n, fc, 16, 150, 321, br, bi, nbb);
        int t0 = tStart / 16;
        // affinage sur la tonalité 0 : fréquence ±1 Hz, instant ±200 échantillons (±0,27 s)
        double bf = -4 * DF, bs = -1e9; int bt = t0;
        for (int dt = -216; dt <= 216; dt += 24) {
            for (int df = -4; df <= 4; df++) {
                double f = -4 * DF + df * 0.25;
                int tt = t0 + dt;
                if (tt < 0 || tt + 85 * BSYM > nbb) continue;
                double s = syncScore(br, bi, f, tt);
                if (s > bs) { bs = s; bf = f; bt = tt; }
            }
        }
        // second affinage plus fin
        double f1 = bf; int t1 = bt;
        for (int dt = -20; dt <= 20; dt += 5) {
            for (int df = -2; df <= 2; df++) {
                double f = f1 + df * 0.1;
                int tt = t1 + dt;
                if (tt < 0 || tt + 85 * BSYM > nbb) continue;
                double s = syncScore(br, bi, f, tt);
                if (s > bs) { bs = s; bf = f; bt = tt; }
            }
        }
        if (bt < 0 || bt + 85 * BSYM > nbb) return null;
        double[] a = new double[85 * 9];
        demod(br, bi, bf, bt, a);
        // métriques souples : 3 bits par symbole de données (code de Gray)
        double[] v = new double[207];
        int d = 0;
        for (int k = 0; k < 85; k++) {
            if (SYNC[k]) continue;
            double[] best1 = { -1, -1, -1 }, best0 = { -1, -1, -1 };
            for (int b = 0; b < 8; b++) {
                int tone = (b ^ (b >> 1)) + 1;
                double am = a[9 * k + tone];
                for (int m = 0; m < 3; m++) {
                    if (((b >> (2 - m)) & 1) == 1) { if (am > best1[m]) best1[m] = am; } else if (am > best0[m]) best0[m] = am;
                }
            }
            for (int m = 0; m < 3; m++) v[3 * d + m] = best1[m] - best0[m];
            d++;
        }
        double[] dv = new double[206];
        for (int i = 0; i < 206; i++) dv[i] = v[J0[i]];
        int[] sym = WsjtCore.toBytes(dv, SYMFAC);
        int[] data = new int[103];
        int met = WsjtCore.fano(sym, 103, 60, MAX_CYCLES, data);
        if (met == Integer.MIN_VALUE) return null;
        int[] enc = WsjtCore.encode(data, 103);
        int errs = 0;
        for (int i = 0; i < 206; i++) if ((sym[i] > 127 ? 1 : 0) != enc[i]) errs++;
        if (errs > MAX_ERR_FRAC * 206) return null;
        String text = unpack(data);
        if (text == null) return null;
        // SNR : puissance de la tonalité émise contre le bruit moyen des huit autres
        int[] tx = new int[207];
        for (int i = 0; i < 206; i++) tx[J0[i]] = enc[i];
        double sig = 0, noi = 0;
        d = 0;
        for (int k = 0; k < 85; k++) {
            int tone;
            if (SYNC[k]) tone = 0;
            else {
                int b = (tx[3 * d] << 2) | (tx[3 * d + 1] << 1) | tx[3 * d + 2];
                tone = (b ^ (b >> 1)) + 1;
                d++;
            }
            double tot = 0;
            for (int j = 0; j < 9; j++) tot += a[9 * k + j] * a[9 * k + j];
            double ps = a[9 * k + tone] * a[9 * k + tone];
            sig += ps;
            noi += (tot - ps) / 8;
        }
        sig /= 85; noi /= 85;
        double pe = Math.max(sig - noi, noi * 0.01);
        Msg m = new Msg();
        m.text = text;
        m.errs = errs;
        m.snr = (int) Math.round(Math.max(-35, Math.min(30, 10 * Math.log10(pe / noi) - 10 * Math.log10(2500.0 / DF))));
        m.dt = bt / 750.0 - nominalMs / 1000.0;
        m.freq = fc + bf;   // fc + bf = fréquence de la tonalité 0 après affinage
        return m;
    }

    // énergie de la tonalité 0 (fréquence f en Hz dans la bande de base) : (synchro − données) normalisée
    static double syncScore(double[] br, double[] bi, double f, int t) {
        double w = -2 * Math.PI * f / 750.0, wr = Math.cos(w), wi = Math.sin(w);
        double s1 = 0, s2 = 0, tot = 1e-30;
        for (int k = 0; k < 85; k++) {
            double pr = 1, pi = 0, ar = 0, ai = 0;
            int s = t + k * BSYM;
            for (int i = 0; i < BSYM; i++) {
                double xr = br[s + i], xi = bi[s + i];
                ar += xr * pr - xi * pi; ai += xr * pi + xi * pr;
                double nr = pr * wr - pi * wi; pi = pr * wi + pi * wr; pr = nr;
            }
            double p = ar * ar + ai * ai;
            tot += p;
            if (SYNC[k]) s1 += p; else s2 += p;
        }
        return (s1 / 16 - s2 / 69) / (tot / 85);
    }

    static final double[] TC = new double[BSYM], TS = new double[BSYM];
    static {
        for (int i = 0; i < BSYM; i++) { TC[i] = Math.cos(2 * Math.PI * i / BSYM); TS[i] = -Math.sin(2 * Math.PI * i / BSYM); }
    }

    /** Amplitudes des 9 tonalités pour chacun des 85 symboles : a[9k + j]. f0 : fréquence de la tonalité 0 (bande de base). */
    static void demod(double[] br, double[] bi, double f0, int t, double[] a) {
        double w = -2 * Math.PI * f0 / 750.0, wr = Math.cos(w), wi = Math.sin(w);
        double[] yr = new double[BSYM], yi = new double[BSYM];
        for (int k = 0; k < 85; k++) {
            double pr = 1, pi = 0;
            int s = t + k * BSYM;
            for (int i = 0; i < BSYM; i++) {
                double xr = br[s + i], xi = bi[s + i];
                yr[i] = xr * pr - xi * pi; yi[i] = xr * pi + xi * pr;
                double nr = pr * wr - pi * wi; pi = pr * wi + pi * wr; pr = nr;
            }
            for (int j = 0; j < 9; j++) {
                double sr = 0, si = 0;
                int idx = 0;
                for (int i = 0; i < BSYM; i++) {
                    sr += yr[i] * TC[idx] - yi[i] * TS[idx];
                    si += yr[i] * TS[idx] + yi[i] * TC[idx];
                    idx += j; if (idx >= BSYM) idx -= BSYM;
                }
                a[9 * k + j] = Math.sqrt(sr * sr + si * si);
            }
        }
    }

    // ------------------------------------------------------------------ message de 72 bits (JT65 / JT9) ; null si non pris en charge ou invalide
    static String unpack(int[] bits) {
        long nc1 = 0, nc2 = 0, ng = 0;
        for (int i = 0; i < 28; i++) nc1 = (nc1 << 1) | bits[i];
        for (int i = 28; i < 56; i++) nc2 = (nc2 << 1) | bits[i];
        for (int i = 56; i < 72; i++) ng = (ng << 1) | bits[i];
        if (ng >= 32768) return null;   // texte libre : non pris en charge
        String c1, c2, g;
        if (nc1 < NBASE) {
            c1 = WsprEngine.callFrom(nc1);
            if (c1 == null) return null;
        } else if (nc1 == NBASE + 1) c1 = "CQ";
        else if (nc1 == NBASE + 2) c1 = "QRZ";
        else if (nc1 == 267796945L) c1 = "DE";
        else if (nc1 >= NBASE + 3 && nc1 <= NBASE + 1002) c1 = String.format(Locale.US, "CQ %03d", nc1 - NBASE - 3);
        else return null;
        if (nc2 >= NBASE) return null;
        c2 = WsprEngine.callFrom(nc2);
        if (c2 == null) return null;
        if (ng < 32400) {
            int dlat = (int) (ng % 180) - 90;
            int dlong = (int) (ng / 180) * 2 - 180 + 2;
            int nlong = (int) (60.0 * (180.0 - dlong) / 5.0);
            int g1 = nlong / 240, g2 = (nlong - 240 * g1) / 24;
            int nlat = (int) (60.0 * (dlat + 90) / 2.5);
            int g3 = nlat / 240, g4 = (nlat - 240 * g3) / 24;
            g = "" + WsprEngine.CH.charAt(10 + g1) + WsprEngine.CH.charAt(10 + g3) + WsprEngine.CH.charAt(g2) + WsprEngine.CH.charAt(g4);
        } else {
            int r = (int) (ng - 32401);
            if (r == 0) g = "";
            else if (r >= 1 && r <= 30) g = String.format(Locale.US, "-%02d", r);
            else if (r >= 31 && r <= 60) g = String.format(Locale.US, "R-%02d", r - 30);
            else if (r == 61) g = "RO";
            else if (r == 62) g = "RRR";
            else if (r == 63) g = "73";
            else return null;
        }
        return (c1 + " " + c2 + (g.isEmpty() ? "" : " " + g)).trim();
    }
}
