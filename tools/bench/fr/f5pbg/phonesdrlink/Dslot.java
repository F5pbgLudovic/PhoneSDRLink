package fr.f5pbg.phonesdrlink;

import java.io.RandomAccessFile;
import java.util.ArrayList;

// Essai : décode une fenêtre WSPR enregistrée par le téléphone (dump-slots) et montre l'effet de CAND_MIN / MAX_CAND.
public class Dslot {
    public static void main(String[] a) throws Exception {
        RandomAccessFile f = new RandomAccessFile(a[0], "r");
        byte[] b = new byte[(int) f.length() - 44];
        f.seek(44); f.readFully(b);
        short[] w = new short[b.length / 2];
        long sq = 0; int mx = 0;
        for (int i = 0; i < w.length; i++) { w[i] = (short) ((b[2 * i] & 255) | (b[2 * i + 1] << 8)); sq += (long) w[i] * w[i]; mx = Math.max(mx, Math.abs(w[i])); }
        System.out.println("echantillons " + w.length + " rms " + Math.round(Math.sqrt(sq / (double) w.length)) + " max " + mx);
        int[] ex = { 6, 4, 3, 2, 1 };
        for (int e : ex) {
            double cm = 0.05; WsprEngine.EXCL = e; WsprEngine.CAND_MIN = cm; WsprEngine.MAX_CAND = 60;
            long t0 = System.currentTimeMillis();
            ArrayList<WsprEngine.Msg> r = WsprEngine.decodeWindow(w, w.length, 4000);
            System.out.println("EXCL " + e + " : " + r.size() + " messages (" + (System.currentTimeMillis() - t0) + " ms)");
            for (WsprEngine.Msg m : r) System.out.println(String.format("  %+4d %+5.1f %4d  %s", m.snr, m.dt, Math.round(m.freq), m.text));
        }
    }
}
