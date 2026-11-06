package fr.f5pbg.phonesdrlink;

import java.io.RandomAccessFile;

// Essai : spectre moyen 1400-1600 Hz et niveau par seconde d'une fenêtre WSPR enregistrée (pompage de CAG, nombre de raies).
public class Dspec {
    public static void main(String[] a) throws Exception {
        RandomAccessFile f = new RandomAccessFile(a[0], "r");
        byte[] b = new byte[(int) f.length() - 44];
        f.seek(44); f.readFully(b);
        int n = b.length / 2;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = (short) ((b[2 * i] & 255) | (b[2 * i + 1] << 8));
        StringBuilder sb = new StringBuilder("RMS par 5 s (dB) : ");
        for (int s = 0; s + 60000 <= n; s += 60000) { double e = 0; for (int i = s; i < s + 60000; i++) e += w[i] * w[i]; sb.append(String.format("%.0f ", 10 * Math.log10(e / 60000))); }
        System.out.println(sb);
        int N = 8192; WsjtCore.Fft fft = new WsjtCore.Fft(N);
        double[] P = new double[N / 2]; int cnt = 0;
        double[] xr = new double[N], xi = new double[N];
        for (int s = 36000; s + N <= n - 36000; s += 4096) {
            for (int i = 0; i < N; i++) { double h = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / N); xr[i] = w[s + i] * h; xi[i] = 0; }
            fft.run(xr, xi);
            for (int k = 0; k < N / 2; k++) P[k] += xr[k] * xr[k] + xi[k] * xi[k];
            cnt++;
        }
        double df = 12000.0 / N;
        int k0 = (int) (1390 / df), k1 = (int) (1610 / df);
        double[] seg = new double[k1 - k0]; for (int k = k0; k < k1; k++) seg[k - k0] = P[k] / cnt;
        double[] srt = seg.clone(); java.util.Arrays.sort(srt); double med = srt[srt.length / 2];
        System.out.println("mediane (bruit) = " + String.format("%.1f", 10 * Math.log10(med)) + " dB ; raies > +5 dB :");
        int raies = 0;
        for (int k = k0 + 1; k < k1 - 1; k++) {
            double v = seg[k - k0];
            if (v > med * 3.2 && v >= seg[k - k0 - 1] && v >= seg[k - k0 + 1]) { raies++; System.out.println(String.format("  %.1f Hz  +%.1f dB", k * df, 10 * Math.log10(v / med))); }
        }
        System.out.println(raies + " raies");
        // niveau moyen hors bande 600-1300 et 1700-2800 pour comparer au bruit
        double o = 0; int oc = 0; for (int k = (int) (600 / df); k < (int) (1300 / df); k++) { o += P[k] / cnt; oc++; }
        System.out.println("bruit 600-1300 Hz moyen : " + String.format("%.1f", 10 * Math.log10(o / oc)) + " dB (mediane 1400-1600 : " + String.format("%.1f", 10 * Math.log10(med)) + ")");
    }
}
