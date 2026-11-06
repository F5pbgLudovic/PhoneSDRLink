package fr.f5pbg.phonesdrlink;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Base des décodeurs à tranches longues (WSPR 120 s, JT9 60 s) : même principe que Ft8Engine. Le son (12 kHz, 16 bits) arrive par morceaux
// dans un tampon circulaire ; quand la tranche UTC est assez avancée, une fenêtre d'analyse (un peu avant le début de la tranche) est
// décodée dans un fil à part. Le décalage médian des signaux décodés recale la fenêtre.
abstract class SlotDecoder {
    static final int RATE = 12000;
    final int slotMs, preMs, readyMs, winN;
    final String name;
    final short[] ring = new short[RATE * 150];
    long total, anchorMs, lastSlot = Long.MIN_VALUE;
    boolean anchored, running;
    double shiftSec;
    int slotsDone, totalMsgs;
    final Handler h = new Handler(Looper.getMainLooper());
    ExecutorService ex;
    final Ft8Engine.Listener ls;
    static java.io.File dumpDir;   // essai : si le fichier « dump-slots » existe dans ce dossier, chaque fenêtre analysée y est enregistrée (WAV 12 kHz)

    /** Résultat d'un décodage : lignes d'affichage et décalages (s) des signaux trouvés. */
    static final class Result {
        final ArrayList<String> lines = new ArrayList<String>();
        final ArrayList<Double> dts = new ArrayList<Double>();
    }

    SlotDecoder(String name, int slotMs, int preMs, int readyMs, Ft8Engine.Listener l) {
        this.name = name;
        this.slotMs = slotMs;
        this.preMs = preMs;
        this.readyMs = readyMs;
        winN = (int) ((long) RATE * (preMs + readyMs) / 1000);
        ls = l;
    }

    /** Décode la fenêtre (début = début de tranche − preMs). slotStartMs : début de tranche (ms UTC), t : texte de l'heure. */
    void dumpWindow(short[] w, long slotStart, String t) {
        try {
            if (dumpDir == null || !new java.io.File(dumpDir, "dump-slots").exists()) return;
            java.io.DataOutputStream o = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.io.FileOutputStream(new java.io.File(dumpDir, "slot-" + name + "-" + t.trim() + ".wav"))));
            int bytes = w.length * 2;
            o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + bytes)); o.writeBytes("WAVEfmt "); o.writeInt(Integer.reverseBytes(16));
            o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1)); o.writeInt(Integer.reverseBytes(RATE)); o.writeInt(Integer.reverseBytes(RATE * 2));
            o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16)); o.writeBytes("data"); o.writeInt(Integer.reverseBytes(bytes));
            for (short v : w) o.writeShort(Short.reverseBytes(v));
            o.close();
        } catch (Exception e) { }
    }

    abstract Result decode(short[] win, int n, long slotStartMs, String t);

    abstract String timeText(Calendar c);

    void start() {
        synchronized (this) { total = 0; anchored = false; lastSlot = Long.MIN_VALUE; shiftSec = 0; slotsDone = 0; totalMsgs = 0; running = true; }
        ex = Executors.newSingleThreadExecutor();
        h.removeCallbacks(tick);
        h.postDelayed(tick, 500);
    }

    void stop() {
        running = false;
        h.removeCallbacks(tick);
        if (ex != null) ex.shutdownNow();
    }

    // Morceau de son : 16 bits petit-boutiste.
    void feed(byte[] b, int len) {
        int n = len / 2;
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (!running) return;
            if (!anchored) { anchorMs = now - n * 1000L / RATE; anchored = true; }
            else {
                long expected = anchorMs + total * 1000L / RATE + n * 1000L / RATE;
                if (now - expected > 2500) anchorMs += now - expected;   // le son s'est arrêté un moment (changement de page…) : on recale
            }
            for (int i = 0; i < n; i++) {
                ring[(int) (total % ring.length)] = (short) ((b[2 * i] & 255) | (b[2 * i + 1] << 8));
                total++;
            }
        }
    }

    final Runnable tick = new Runnable() {
        public void run() {
            if (!running) return;
            try { check(); } catch (Exception e) { }
            h.postDelayed(this, 500);
        }
    };

    void check() {
        final long now = System.currentTimeMillis();
        short[] w = null;
        final long s;
        synchronized (this) {
            if (!anchored) return;
            long shiftMs = (long) (shiftSec * 1000);
            s = Math.floorDiv(now - shiftMs - readyMs, (long) slotMs);
            if (s <= lastSlot) return;
            long startIdx = ((s * slotMs + shiftMs - preMs) - anchorMs) * RATE / 1000;
            lastSlot = s;
            if (startIdx < 0 || startIdx + winN > total || total - startIdx > ring.length) return;   // pas assez de son pour cette tranche
            w = new short[winN];
            for (int i = 0; i < winN; i++) w[i] = ring[(int) ((startIdx + i) % ring.length)];
        }
        final short[] win = w;
        final long slotStart = s * slotMs;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        c.setTimeInMillis(slotStart);
        final String t = timeText(c);
        final int parity = (int) Math.floorMod(s, 2L);
        try {
            ex.execute(new Runnable() {
                public void run() {
                    dumpWindow(win, slotStart, t);
                    Result r;
                    try { r = decode(win, win.length, slotStart, t); } catch (Throwable e) { r = new Result(); }
                    final Result res = r;
                    h.post(new Runnable() {
                        public void run() {
                            if (!running) return;
                            slotsDone++;
                            totalMsgs += res.lines.size();
                            if (!res.dts.isEmpty()) {
                                Double[] a = res.dts.toArray(new Double[0]);
                                Arrays.sort(a);
                                double med = a[a.length / 2];
                                synchronized (SlotDecoder.this) { shiftSec = Math.max(-1.5, Math.min(1.5, shiftSec + 0.5 * med)); }
                            }
                            if (ls != null) {
                                ls.onLines(res.lines, parity);
                                ls.onStatus(Lang.F("{3} : {0} tranches, {1} messages, retard {2} s", "{3}: {0} slots, {1} messages, delay {2} s", slotsDone, totalMsgs, String.format(Locale.US, "%.1f", shiftSec), name));
                            }
                        }
                    });
                }
            });
        } catch (Exception e) { }
    }
}
