package fr.f5pbg.phonesdrlink;

import java.util.Random;

// Essai SSTV : une image interrompue puis une autre qui commence (avec ou sans silence entre les deux) ; le décodeur doit abandonner la première
// (enregistrée partielle) et décoder la seconde ; après un silence long, il doit se remettre à l'écoute.
public class Dsstv2 {
    static double[] cut(String mode, double fraction) {
        Dsstv.skew = 1.0; Dsstv.off = 0;
        Dsstv.gen(mode, false);
        int n = (int) (Dsstv.cnt * fraction);
        double[] o = new double[n];
        System.arraycopy(Dsstv.buf, 0, o, 0, n);
        return o;
    }

    static void scenario(String title, String m1, double frac, double gapSec, String m2) {
        double[] a = cut(m1, frac);
        Dsstv.gen(m2, false);
        double[] b = new double[Dsstv.cnt];
        System.arraycopy(Dsstv.buf, 0, b, 0, Dsstv.cnt);
        final int W2 = Dsstv.W, H2 = Dsstv.H;
        final int[][] src2 = Dsstv.src;
        Random r = new Random(5);
        final StringBuilder log = new StringBuilder();
        final int[] cur = { 0 }, rowsAt = { 0 };
        final int[][] pxs = new int[4][];
        final String[] names = new String[4];
        final boolean[] comp = new boolean[4];
        final int[] rws = new int[4];
        SstvEngine e = new SstvEngine(new TextEngine.Listener() {
            public void onText(String s) { }
            public void onStatus(String s) { }
        }, new SstvEngine.Images() {
            public void begin(String n, int w, int h, int[] px) { cur[0]++; names[cur[0]] = n; pxs[cur[0]] = px; }
            public void rows(int d) { rowsAt[0] = d; }
            public void end(boolean c, int rr) { comp[cur[0]] = c; rws[cur[0]] = rr; }
        });
        double sigma = 0.5 / Math.sqrt(2) / Math.pow(10, 30 / 20.0) * Math.sqrt(8000.0 / 2400);
        short[] ch = new short[1000]; int k = 0;
        int total = a.length + (int) (gapSec * 8000) + b.length;
        for (int i = 0; i < total; i++) {
            double v;
            if (i < a.length) v = a[i];
            else if (i < a.length + (int) (gapSec * 8000)) v = 0.3 * r.nextGaussian();   // bruit seul
            else v = b[i - a.length - (int) (gapSec * 8000)];
            v += sigma * r.nextGaussian();
            ch[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
            if (k == ch.length) { e.feed(ch, k); k = 0; }
        }
        StringBuilder sb = new StringBuilder(title + " : ");
        for (int i = 1; i <= cur[0]; i++) sb.append(String.format("[image %d %s %s %d lignes] ", i, names[i], comp[i] ? "complète" : "interrompue", rws[i]));
        if (cur[0] >= 1 && comp[cur[0]] && names[cur[0]].equals(m2)) {
            long err = 0; int nn = 0;
            for (int y = 2; y < H2 - 2; y++) for (int x = 4; x < W2 - 4; x++) { int v = pxs[cur[0]][y * W2 + x]; err += Math.abs(((v >> 8) & 255) - src2[1][y * W2 + x]); nn++; }
            sb.append(String.format("erreur G %.1f", err / (double) nn));
        } else sb.append("SECONDE IMAGE NON DÉCODÉE");
        System.out.println(sb);
    }

    public static void main(String[] x) {
        scenario("A  Robot 36 coupée à 40 %, la suivante commence tout de suite (Martin M2)", "Robot 36", 0.4, 0.0, "Martin M2");
        scenario("B  Martin M1 coupée à 30 %, 1 s de bruit, puis PD90", "Martin M1", 0.3, 1.0, "PD90");
        scenario("C  PD120 coupée à 20 %, 3 s de bruit, puis Robot 72", "PD120", 0.2, 3.0, "Robot 72");
        scenario("D  Scottie S1 coupée à 50 %, 40 s de bruit, puis Martin M2", "Scottie S1", 0.5, 40.0, "Martin M2");
    }
}
