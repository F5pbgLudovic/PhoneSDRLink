package fr.f5pbg.phonesdrlink;

import java.util.Random;

// Essai : suit les messages d'état et les débuts / fins d'image d'une réception SSTV sans début (diagnostic). Arguments : mode, SNR, sensibilité.
public class Dsstv5 {
    public static void main(String[] a) {
        String mode = a[0]; double snr = Double.parseDouble(a[1]); int sens = Integer.parseInt(a[2]);
        Dsstv.skew = 1.0; Dsstv.off = 0;
        Dsstv.gen(mode, false);
        final long[] t = { 0 };
        SstvEngine e = new SstvEngine(new TextEngine.Listener() { public void onText(String s) { }
            public void onStatus(String s) { System.out.println(String.format("%6.1f s  %s", t[0] / 8000.0, s)); } }, new SstvEngine.Images() {
            public void begin(String n, int w, int h, int[] p) { System.out.println(String.format("%6.1f s  BEGIN %s", t[0] / 8000.0, n)); }
            public void rows(int d) { }
            public void end(boolean c, int rr) { System.out.println(String.format("%6.1f s  END complete=%b rows=%d", t[0] / 8000.0, c, rr)); }
        });
        e.sens = sens;
        Random r = new Random(21);
        double sigma = 0.5 / Math.sqrt(2) / Math.pow(10, snr / 20.0) * Math.sqrt(8000.0 / 2400);
        short[] ch = new short[1000]; int k = 0;
        int start = a.length > 3 ? (int) (Dsstv.cnt * 0.3) + 777 : 0;
        for (int i = start; i < Dsstv.cnt; i++) {
            ch[k++] = (short) (Math.max(-1, Math.min(1, Dsstv.buf[i] + sigma * r.nextGaussian())) * 30000);
            t[0] = i - start;
            if (k == ch.length) { e.feed(ch, k); k = 0; if (a.length > 4 && (i - start) % 16000 < 1000 && (i - start) < 200000) System.out.println("  t=" + (i - start) / 8000 + " np=" + e.np + " vs=" + e.vs + " lead=" + e.lead + " pRun=" + e.pRun); }
        }
    }
}
