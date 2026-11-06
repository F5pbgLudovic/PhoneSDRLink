package fr.f5pbg.phonesdrlink;

import java.util.Random;

// Essai « perte data » : une image Robot 36 / Martin M2 / PD120 dont le signal disparaît (bruit seul) pendant 4, 10 ou 40 s au milieu, puis revient (le temps passe : les lignes du trou sont perdues). Pour chaque niveau de perte data : nombre d'images produites et lignes reçues.
public class Dsstv6 {
    static String one(String mode, double gapSec, int loss) {
        Dsstv.skew = 1.0; Dsstv.off = 0;
        Dsstv.gen(mode, false);
        final int H = Dsstv.H;
        int cutAt = (int) (Dsstv.cnt * 0.4), gap = (int) (gapSec * 8000);
        final int[] begins = { 0 }, rows = { 0 };
        final StringBuilder sb = new StringBuilder();
        SstvEngine e = new SstvEngine(new TextEngine.Listener() { public void onText(String s) { } public void onStatus(String s) { } }, new SstvEngine.Images() {
            public void begin(String n, int w, int h, int[] p) { begins[0]++; rows[0] = 0; }
            public void rows(int d) { rows[0] = d; }
            public void end(boolean c, int rr) { sb.append(rr).append(c ? "c " : " "); }
        });
        e.setLevels(5, loss);
        Random r = new Random(4);
        double sigma = 0.5 / Math.sqrt(2) / Math.pow(10, 30 / 20.0) * Math.sqrt(8000.0 / 2400);
        short[] ch = new short[1000]; int k = 0;
        for (int i = 0; i < Dsstv.cnt; i++) {
            double v;
            if (i < cutAt) v = Dsstv.buf[i];
            else if (i < cutAt + gap) v = 0.3 * r.nextGaussian();
            else v = Dsstv.buf[i];
            v += sigma * r.nextGaussian();
            ch[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
            if (k == ch.length) { e.feed(ch, k); k = 0; }
        }
        return String.format("%d image(s), fins : %s(rows en cours %d/%d)", begins[0], sb, rows[0], H);
    }

    public static void main(String[] a) {
        for (String m : new String[] { "Robot 36", "Martin M2", "PD120" })
            for (double g : new double[] { 4, 10, 40 }) {
                System.out.println(String.format("%-10s trou de %2.0f s :", m, g));
                for (int L : new int[] { 1, 4, 7, 10 }) System.out.println(String.format("     perte data %2d : %s", L, one(m, g, L)));
            }
    }
}
