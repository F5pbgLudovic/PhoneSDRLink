package fr.f5pbg.phonesdrlink;

import java.util.Random;

// Essai SSTV : réception d'une image dont le début (VIS compris) manque. On coupe le début du son à un endroit quelconque ; le décodeur doit
// trouver le mode par les impulsions de synchro, puis décoder les lignes restantes (la première ligne reçue devient la ligne 0 de l'image).
public class Dsstv3 {
    static void run(String mode, double skipFrac, double snr) {
        Dsstv.skew = 1.0; Dsstv.off = 0;
        Dsstv.gen(mode, false);
        final int W = Dsstv.W, H = Dsstv.H;
        final int[][] src = Dsstv.src;
        int start = (int) (Dsstv.cnt * skipFrac) + 1234;
        final String[] name = { "" };
        final int[][] px = new int[1][];
        final int[] rows = { 0 };
        final boolean[] done = { false };
        final String[] st = { "" };
        SstvEngine e = new SstvEngine(new TextEngine.Listener() {
            public void onText(String s) { }
            public void onStatus(String s) { st[0] = s; }
        }, new SstvEngine.Images() {
            public void begin(String n, int w, int h, int[] p) { name[0] = n; px[0] = p; }
            public void rows(int d) { rows[0] = d; }
            public void end(boolean c, int rr) { done[0] = true; rows[0] = rr; }
        });
        Random r = new Random(9);
        double sigma = 0.5 / Math.sqrt(2) / Math.pow(10, snr / 20.0) * Math.sqrt(8000.0 / 2400);
        short[] ch = new short[1000]; int k = 0;
        for (int i = start; i < Dsstv.cnt; i++) {
            double v = Dsstv.buf[i] + sigma * r.nextGaussian();
            ch[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
            if (k == ch.length) { e.feed(ch, k); k = 0; }
        }
        String res;
        if (px[0] == null) res = "AUCUNE IMAGE";
        else {
            // décalage de ligne : celui qui minimise l'erreur de la ligne 3 de l'image reçue (canal G)
            int bestK = -1; double bestE = 1e9;
            int rr = 3;
            for (int kk = 0; kk + rr < H; kk++) {
                double err = 0; int nn = 0;
                for (int x = 8; x < W - 8; x += 2) { int v = px[0][rr * W + x]; err += Math.abs(((v >> 8) & 255) - src[1][(kk + rr) * W + x]); nn++; }
                err /= nn;
                if (err < bestE) { bestE = err; bestK = kk; }
            }
            res = String.format("%s, %d lignes reçues, 1re ligne = ligne %d de la source (erreur G %.1f)", name[0], rows[0], bestK, bestE);
        }
        System.out.println(String.format("%-15s coupé à %2.0f %% SNR %2.0f : %s %s", mode, skipFrac * 100, snr, mode.equals(name[0]) ? "OK" : "MODE FAUX", res));
    }

    // WAV 8 kHz (injection dans le téléphone : --es decwav sstv:chemin) : Martin M2 sans son début
    static void wav(String dir) throws Exception {
        Dsstv.skew = 1.0; Dsstv.off = 0;
        Dsstv.gen("Martin M2", false);
        int start = (int) (Dsstv.cnt * 0.35) + 1234, n = Dsstv.cnt - start;
        java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(dir + "/sstv-blind.wav")));
        o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + 2 * n)); o.writeBytes("WAVEfmt "); o.writeInt(Integer.reverseBytes(16));
        o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1)); o.writeInt(Integer.reverseBytes(8000)); o.writeInt(Integer.reverseBytes(16000));
        o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16)); o.writeBytes("data"); o.writeInt(Integer.reverseBytes(2 * n));
        for (int i = start; i < Dsstv.cnt; i++) o.writeShort(Short.reverseBytes((short) (Dsstv.buf[i] * 30000)));
        o.close();
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("wav")) { wav(a[1]); return; }
        String[] modes = { "Martin M1", "Martin M2", "Scottie S1", "Scottie S2", "Scottie DX", "Wraase SC2-180", "Robot 36", "Robot 72", "PD50", "PD90", "PD120", "PD160", "PD180", "PD240", "PD290", "Wraase SC2-120", "Pasokon P3", "Pasokon P5", "Pasokon P7", "Robot 24 BW", "Robot 8 BW" };
        for (String m : modes) run(m, 0.35, 30);
        run("Robot 36", 0.2, 15); run("Martin M2", 0.4, 15); run("PD120", 0.3, 20);
        // bruit seul (5 minutes, deux niveaux) : aucune image ne doit commencer
        for (double amp : new double[] { 0.05, 0.4 }) {
            final int[] begins = { 0 };
            SstvEngine e = new SstvEngine(new TextEngine.Listener() { public void onText(String s) { } public void onStatus(String s) { } }, new SstvEngine.Images() {
                public void begin(String n, int w, int h, int[] p) { begins[0]++; }
                public void rows(int d) { }
                public void end(boolean c, int rr) { }
            });
            Random r = new Random(11);
            short[] ch = new short[1000];
            for (int i = 0; i < 300 * 8; i++) { for (int j = 0; j < 1000; j++) ch[j] = (short) (Math.max(-1, Math.min(1, amp * r.nextGaussian())) * 30000); e.feed(ch, 1000); }
            System.out.println("bruit seul amplitude " + amp + " (300 s) : " + begins[0] + " image(s) démarrée(s)");
        }
    }
}
