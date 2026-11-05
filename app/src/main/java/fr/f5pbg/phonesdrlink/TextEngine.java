package fr.f5pbg.phonesdrlink;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Décodeur de texte au fil de l'eau (CW, RTTY) : reçoit le son à 8000 Hz, renvoie des caractères.
// Le calcul se fait dans un fil à part (feedBytes et post) : les rappels du Listener arrivent donc depuis ce fil.
abstract class TextEngine {
    static final int RATE = 8000;
    interface Listener { void onText(String s); void onStatus(String s); }
    protected Listener ls;
    private ExecutorService ex;
    TextEngine(Listener l) { ls = l; }

    private synchronized ExecutorService exec() {
        if (ex == null) ex = Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
            public Thread newThread(Runnable r) { Thread t = new Thread(r, "dectext"); t.setPriority(Thread.NORM_PRIORITY); return t; }
        });
        return ex;
    }

    // Morceau de son : 16 bits petit-boutiste.
    void feedBytes(byte[] b, int len) {
        final int n = len / 2;
        final short[] s = new short[n];
        for (int i = 0; i < n; i++) s[i] = (short) ((b[2 * i] & 255) | (b[2 * i + 1] << 8));
        try { exec().execute(new Runnable() { public void run() { feed(s, n); } }); } catch (Exception e) { }
    }

    // Exécute r dans le fil du décodeur (changement de réglage sans course avec le calcul).
    void post(Runnable r) {
        try { exec().execute(r); } catch (Exception e) { }
    }

    synchronized void shutdown() {
        if (ex != null) { ex.shutdownNow(); ex = null; }
    }

    abstract void feed(short[] s, int n);

    /** Énergie de Goertzel à la fréquence f (Hz) sur x[off .. off+n[ (échantillons 8 kHz). */
    static double goertzel(float[] x, int off, int n, double f) {
        double w = 2.0 * Math.PI * f / RATE, c = 2.0 * Math.cos(w), s1 = 0, s2 = 0;
        for (int i = 0; i < n; i++) { double s0 = x[off + i] + c * s1 - s2; s2 = s1; s1 = s0; }
        return s1 * s1 + s2 * s2 - c * s1 * s2;
    }
}
