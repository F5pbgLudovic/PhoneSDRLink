package fr.f5pbg.phonesdrlink;

import java.util.Random;

// Essai SSTV : signaux faibles. Pour chaque niveau de sensibilité (0 à 4), mode et rapport signal/bruit : l'image est-elle reconnue (code VIS) et
// décodée (lignes reçues, erreur moyenne) ? Puis 600 s de bruit seul par niveau : fausses détections. Option « blind » : début de l'image coupé.
public class Dsstv4 {
    static String one(String mode, double snr, int sens, boolean blind) {
        Dsstv.skew = 1.0; Dsstv.off = 0;
        Dsstv.gen(mode, false);
        final int W = Dsstv.W, H = Dsstv.H;
        final int[][] src = Dsstv.src;
        final String[] name = { "" };
        final int[][] px = new int[1][];
        final int[] rows = { 0 };
        SstvEngine e = new SstvEngine(new TextEngine.Listener() { public void onText(String s) { } public void onStatus(String s) { } }, new SstvEngine.Images() {
            public void begin(String n, int w, int h, int[] p) { if (px[0] == null) { name[0] = n; px[0] = p; } }
            public void rows(int d) { rows[0] = Math.max(rows[0], d); }
            public void end(boolean c, int rr) { rows[0] = Math.max(rows[0], rr); }
        });
        e.sens = sens;
        Random r = new Random(21);
        double sigma = 0.5 / Math.sqrt(2) / Math.pow(10, snr / 20.0) * Math.sqrt(8000.0 / 2400);
        short[] ch = new short[1000]; int k = 0;
        int start = blind ? (int) (Dsstv.cnt * 0.3) + 777 : 0;
        for (int i = start; i < Dsstv.cnt; i++) {
            double v = Dsstv.buf[i] + sigma * r.nextGaussian();
            ch[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
            if (k == ch.length) { e.feed(ch, k); k = 0; }
        }
        if (px[0] == null || !name[0].equals(mode)) return "  --  ";
        int shift = 0;
        if (blind) { // décalage de ligne retrouvé sur la ligne 3
            double bestE = 1e9; int bk = 0;
            for (int kk = 0; kk + 3 < H; kk++) { double err = 0; int nn = 0; for (int x = 8; x < W - 8; x += 2) { err += Math.abs(((px[0][3 * W + x] >> 8) & 255) - src[1][(kk + 3) * W + x]); nn++; } err /= nn; if (err < bestE) { bestE = err; bk = kk; } }
            shift = bk;
        }
        double err = 0; int nn = 0;
        for (int y = 2; y < rows[0] - 2 && y + shift < H; y++) for (int x = 4; x < W - 4; x += 2) { err += Math.abs(((px[0][y * W + x] >> 8) & 255) - src[1][(y + shift) * W + x]); nn++; }
        return String.format("%3d/%2.0f", rows[0], nn > 0 ? err / nn : -1);
    }

    public static void main(String[] a) {
        String[] modes = { "Martin M2", "Robot 36", "Scottie S2", "PD120" };
        boolean blind = a.length > 0 && a[0].equals("blind");
        double[] snrs = { 12, 9, 6, 3 };
        System.out.println((blind ? "Sans début (blind)" : "Avec code VIS") + " : lignes reçues / erreur moyenne du vert, par sensibilité 0..4");
        for (String m : modes) for (double s : snrs) {
            StringBuilder sb = new StringBuilder(String.format("%-11s SNR %2.0f : ", m, s));
            for (int L = 0; L <= 4; L++) sb.append(one(m, s, L, blind)).append("  ");
            System.out.println(sb);
        }
        for (int L = 0; L <= 4; L++) {
            final int[] begins = { 0 };
            SstvEngine e = new SstvEngine(new TextEngine.Listener() { public void onText(String s) { } public void onStatus(String s) { } }, new SstvEngine.Images() {
                public void begin(String n, int w, int h, int[] p) { begins[0]++; }
                public void rows(int d) { }
                public void end(boolean c, int rr) { }
            });
            e.sens = L;
            Random r = new Random(33);
            short[] ch = new short[1000];
            for (int i = 0; i < 600 * 8; i++) { for (int j = 0; j < 1000; j++) ch[j] = (short) (Math.max(-1, Math.min(1, 0.2 * r.nextGaussian())) * 30000); e.feed(ch, 1000); }
            System.out.println("bruit seul 600 s, sensibilité " + L + " : " + begins[0] + " fausse(s) détection(s)");
        }
    }
}
