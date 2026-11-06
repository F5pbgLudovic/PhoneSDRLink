package fr.f5pbg.phonesdrlink;

import java.util.ArrayList;
import java.util.Random;

// Banc d'essai SSTV : génère le son (FM, 8 kHz) d'une image test pour plusieurs modes, avec bruit et erreur d'horloge, le fait décoder
// par SstvEngine et affiche l'erreur moyenne par canal (0..255) et le nombre de lignes décodées.
public class Dsstv {
    static double[] buf = new double[9_000_000];
    static int cnt;
    static double ph, acc;
    static double skew = 1.0, off = 0;

    static void tone(double f, double ms) {
        acc += ms * 8 * skew;
        int n = (int) Math.floor(acc);
        acc -= n;
        for (int i = 0; i < n; i++) { ph += 2 * Math.PI * (f + off) / 8000; buf[cnt++] = 0.5 * Math.sin(ph); }
    }

    static double fv(int v) { return 1500 + 800.0 * v / 255; }
    static void scan(int[] v, double ms) { for (int x : v) tone(fv(x), ms / v.length); }

    static int[][] src;      // [3][w*h] R, G, B
    static int W, H;

    static void makeImage(int w, int h) {
        W = w; H = h;
        src = new int[3][w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            src[0][y * w + x] = (int) (30 + 190.0 * x / w);
            src[1][y * w + x] = (int) (30 + 190.0 * y / h);
            src[2][y * w + x] = (int) (128 + 80 * Math.sin(x / 25.0 + y / 40.0));
        }
    }

    static int[] row(int c, int y) { int[] o = new int[W]; for (int x = 0; x < W; x++) o[x] = src[c][y * W + x]; return o; }
    static int cl(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    static int[][] ycc(int y) {
        int[][] o = new int[3][W];
        for (int x = 0; x < W; x++) {
            double r = src[0][y * W + x], g = src[1][y * W + x], b = src[2][y * W + x];
            double yy = (g + 0.71 * 127.5 + 0.71 * r / 1.4 + 0.33 * 127.97 + 0.33 * b / 1.78 - 132.6) / 1.6925;
            o[0][x] = cl(yy); o[1][x] = cl(127.5 + (r - yy) / 1.4); o[2][x] = cl(127.97 + (b - yy) / 1.78);
        }
        return o;
    }
    static int[] avg(int[] a, int[] b) { int[] o = new int[a.length]; for (int i = 0; i < o.length; i++) o[i] = (a[i] + b[i]) / 2; return o; }
    static int[] half(int[] a) { int[] o = new int[a.length / 2]; for (int i = 0; i < o.length; i++) o[i] = (a[2 * i] + a[2 * i + 1]) / 2; return o; }

    static void vis(int code) {
        tone(1900, 300); tone(1200, 10); tone(1900, 300); tone(1200, 30);
        int ones = 0;
        for (int j = 0; j < 7; j++) { int b = (code >> j) & 1; ones += b; tone(b == 1 ? 1100 : 1300, 30); }
        tone((ones & 1) == 1 ? 1100 : 1300, 30);
        tone(1200, 30);
    }

    static void gen(String mode, boolean initialScottieSync) {
        cnt = 0; ph = 0; acc = 0;
        for (int i = 0; i < 4000; i++) buf[cnt++] = 0;
        SstvEngine.Mode m = null;
        for (SstvEngine.Mode k : SstvEngine.MODES) if (k.name.equals(mode)) m = k;
        makeImage(m.w, m.h / m.rep);
        vis(m.vis);
        double sc = m.scanMs;
        if (mode.startsWith("Martin")) {
            for (int y = 0; y < H; y++) {
                tone(1200, 4.862); tone(1500, 0.572);
                scan(row(1, y), sc); tone(1500, 0.572); scan(row(2, y), sc); tone(1500, 0.572); scan(row(0, y), sc); tone(1500, 0.572);
            }
        } else if (mode.startsWith("Scottie")) {
            if (initialScottieSync) { tone(1200, 9); tone(1500, 1.5); } else tone(1500, 1.5);
            for (int y = 0; y < H; y++) {
                scan(row(1, y), sc); tone(1500, 1.5); scan(row(2, y), sc);
                tone(1200, 9); tone(1500, 1.5); scan(row(0, y), sc); tone(1500, 1.5);
            }
        } else if (mode.startsWith("Wraase")) {
            for (int y = 0; y < H; y++) { tone(1200, 5.5225); tone(1500, 0.5); scan(row(0, y), sc); scan(row(1, y), sc); scan(row(2, y), sc); }
        } else if (mode.startsWith("Pasokon")) {
            double gap = (m.periodMs - m.syncMs - 3 * sc) / 4;
            for (int y = 0; y < H; y++) { tone(1200, m.syncMs); tone(1500, gap); scan(row(0, y), sc); tone(1500, gap); scan(row(1, y), sc); tone(1500, gap); scan(row(2, y), sc); tone(1500, gap); }
        } else if (mode.endsWith("BW")) {
            for (int y = 0; y < H; y++) { tone(1200, m.syncMs); scan(ycc(y)[0], sc); }
        } else if (mode.equals("Robot 36")) {
            for (int p = 0; p < H / 2; p++) {
                int[][] a = ycc(2 * p), b = ycc(2 * p + 1);
                tone(1200, 9); tone(1500, 3); scan(a[0], 88); tone(1500, 4.5); tone(1900, 1.5); scan(half(avg(a[1], b[1])), 44);
                tone(1200, 9); tone(1500, 3); scan(b[0], 88); tone(2300, 4.5); tone(1900, 1.5); scan(half(avg(a[2], b[2])), 44);
            }
        } else if (mode.equals("Robot 72")) {
            for (int y = 0; y < H; y++) {
                int[][] a = ycc(y);
                tone(1200, 9); tone(1500, 3); scan(a[0], 138); tone(1500, 4.5); tone(1900, 1.5); scan(a[1], 69); tone(2300, 4.5); tone(1900, 1.5); scan(a[2], 69);
            }
        } else if (mode.startsWith("PD")) {
            for (int p = 0; p < H / 2; p++) {
                int[][] a = ycc(2 * p), b = ycc(2 * p + 1);
                tone(1200, 20); tone(1500, 2.08); scan(a[0], sc); scan(avg(a[1], b[1]), sc); scan(avg(a[2], b[2]), sc); scan(b[0], sc);
            }
        }
        for (int i = 0; i < 16000; i++) buf[cnt++] = 0;
    }

    static void run(String mode, boolean sdxSync, double snr, double skewv, double tuneOff) {
        skew = skewv; off = tuneOff;
        gen(mode, sdxSync);
        Random r = new Random(3);
        final int[][] res = new int[1][];
        final String[] st = { "" };
        final int[] rows = { 0 }, dims = new int[2];
        final String[] name = { "" };
        final boolean[] done = { false };
        SstvEngine e = new SstvEngine(new TextEngine.Listener() {
            public void onText(String s) { }
            public void onStatus(String s) { st[0] = s; }
        }, new SstvEngine.Images() {
            public void begin(String n, int w, int h, int[] px) { name[0] = n; dims[0] = w; dims[1] = h; res[0] = px; }
            public void rows(int d) { rows[0] = d; }
            public void end(boolean c, int rws) { done[0] = c; rows[0] = rws; }
        });
        double sigma = 0.5 / Math.sqrt(2) / Math.pow(10, snr / 20.0) * Math.sqrt(8000.0 / 2400);
        short[] ch = new short[1000];
        int k = 0;
        for (int i = 0; i < cnt; i++) {
            double v = buf[i];
            v += sigma * r.nextGaussian();
            ch[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
            if (k == ch.length) { e.feed(ch, k); k = 0; }
        }
        double[] err = new double[3];
        int n = 0;
        if (res[0] != null && done[0]) {
            for (int y = 2; y < H - 2; y++) for (int x = 4; x < W - 4; x++) {
                int rp = Math.max(1, res[0].length / (W * H));
                int val = lastPx(res[0], (y * rp) * W + x);
                if (mode.endsWith("BW")) { int lum = ycc(y)[0][x]; err[0] += Math.abs(((val >> 16) & 255) - lum); err[1] += Math.abs(((val >> 8) & 255) - lum); err[2] += Math.abs((val & 255) - lum); n++; continue; }
                err[0] += Math.abs(((val >> 16) & 255) - src[0][y * W + x]);
                err[1] += Math.abs(((val >> 8) & 255) - src[1][y * W + x]);
                err[2] += Math.abs((val & 255) - src[2][y * W + x]);
                n++;
            }
        }
        System.out.println(String.format("%-15s SNR %2.0f dB horloge x%.4f : %s %s lignes %d/%d  erreur R %.1f G %.1f B %.1f  [%s]", mode, snr, skewv, name[0], done[0] ? "complète" : "INCOMPLÈTE", rows[0], H,
            n > 0 ? err[0] / n : -1, n > 0 ? err[1] / n : -1, n > 0 ? err[2] / n : -1, st[0]));
    }

    static int lastPx(int[] a, int i) { return a[i]; }

    public static void main(String[] a) {
        String[] only = a.length > 0 ? a : new String[] { "Martin M3", "Martin M4", "Scottie S3", "Scottie S4", "Wraase SC2-120", "Pasokon P3", "Pasokon P5", "Pasokon P7", "Robot 24 BW", "Robot 12 BW", "Robot 8 BW", "Martin M1", "Martin M2", "Scottie S1", "Scottie S2", "Scottie DX", "Wraase SC2-180", "Robot 36", "Robot 72", "PD50", "PD90", "PD120", "PD160", "PD180", "PD240", "PD290" };
        for (String m : only) { run(m, false, 40, 1.0, 0); }
        run("Scottie S2", true, 40, 1.0, 0);
        run("Martin M2", false, 25, 1.0003, 0);
        run("Robot 36", false, 25, 0.9997, 0);
        run("PD50", false, 15, 1.0005, 0);
        run("Martin M2", false, 30, 1.0, 45);
        run("Robot 36", false, 30, 1.0, -60);
        run("PD120", false, 30, 1.0002, 35);
    }
}
