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

// Décodage FT8 (tranches de 15 s), FT4 (7,5 s) ou FT2 (3,75 s ; FT4 à symboles deux fois plus courts) en continu : le son (12 kHz, 16 bits) arrive par morceaux ; à la fin de chaque
// tranche (alignée sur l'heure UTC) la tranche écoulée est décodée par ft8_lib dans un fil à part. Le retard du son (réseau,
// WebSDR) est appris à partir du DT des messages décodés : la fenêtre d'analyse est décalée d'autant. La parité de la tranche
// (paire / impaire) permet d'afficher les deux périodes en deux couleurs.
final class Ft8Engine {
    interface Listener { void onLines(ArrayList<String> lines, int parity); void onStatus(String s); }

    static final int RATE = 12000;
    final boolean ft4;      // vrai pour FT4 et FT2 : l'heure est affichée avec le dixième de seconde
    final int mode;         // 0 FT8, 1 FT4, 2 FT2 (même numérotation que le décodeur C)
    final int slotMs, slotN;
    final String name;
    final short[] ring = new short[RATE * 45];
    long total, anchorMs, lastSlot = Long.MIN_VALUE;
    boolean anchored, running;
    double shiftSec;
    int slotsDone, totalMsgs;
    final Handler h = new Handler(Looper.getMainLooper());
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    final Listener ls;

    Ft8Engine(int mode, Listener l) {
        this.mode = mode;
        this.ft4 = mode != 0;
        slotMs = mode == 2 ? 3750 : mode == 1 ? 7500 : 15000;
        slotN = RATE * slotMs / 1000;
        name = mode == 2 ? "FT2" : mode == 1 ? "FT4" : "FT8";
        ls = l;
    }

    void start() {
        synchronized (this) { total = 0; anchored = false; lastSlot = Long.MIN_VALUE; shiftSec = 0; slotsDone = 0; totalMsgs = 0; running = true; }
        Decoders.ft8Reset();
        h.removeCallbacks(tick);
        h.postDelayed(tick, 500);
    }

    void stop() {
        running = false;
        h.removeCallbacks(tick);
        ex.shutdown();
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
        short[] slot0 = null;   // le tampon n'est alloué que lorsqu'une tranche est à décoder (pas toutes les 0,5 s)
        final long s;
        synchronized (this) {
            if (!anchored) return;
            long shiftMs = (long) (shiftSec * 1000);
            s = Math.floorDiv(now - shiftMs - slotMs - 300, (long) slotMs);
            if (s <= lastSlot) return;
            long startIdx = (s * slotMs + shiftMs - anchorMs) * RATE / 1000;
            if (startIdx < 0 || startIdx + slotN > total || total - startIdx > ring.length) { lastSlot = s; return; }   // pas assez de son pour cette tranche
            slot0 = new short[slotN];
            for (int i = 0; i < slotN; i++) slot0[i] = ring[(int) ((startIdx + i) % ring.length)];
            lastSlot = s;
        }
        final short[] slot = slot0;
        final long slotStart = s * slotMs;
        final int parity = (int) (Math.floorMod(s, 2L));
        ex.execute(new Runnable() {
            public void run() {
                final String res = Decoders.ft8Decode(slot, slotN, RATE, mode);
                final ArrayList<String> lines = new ArrayList<String>();
                final ArrayList<Double> dts = new ArrayList<Double>();
                Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
                c.setTimeInMillis(slotStart);
                String t = String.format(Locale.US, ft4 ? "%02d%02d%02d.%d" : "%02d%02d%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND), c.get(Calendar.MILLISECOND) / 100);
                if (!ft4) t = t.substring(0, 6);
                for (String ln : res.split("\n")) {
                    String[] p = ln.split("\\|", 4);
                    if (p.length < 4) continue;
                    double dt = 0;
                    try { dt = Double.parseDouble(p[1]) - 0.5; } catch (NumberFormatException e) { }
                    dts.add(dt);
                    lines.add(String.format(Locale.US, "%s %+5.1f %4s  %s", t, dt, p[2], p[3]));
                }
                h.post(new Runnable() {
                    public void run() {
                        if (!running) return;
                        slotsDone++;
                        totalMsgs += lines.size();
                        if (dts.size() >= 2) {   // on cale la fenêtre sur le retard médian observé
                            Double[] a = dts.toArray(new Double[0]);
                            Arrays.sort(a);
                            double med = a[a.length / 2];
                            synchronized (Ft8Engine.this) { shiftSec = Math.max(-0.5, Math.min(slotMs / 1000.0 / 2, shiftSec + 0.7 * med)); }
                        }
                        if (ls != null) { ls.onLines(lines, parity); ls.onStatus(Lang.F("{3} : {0} tranches, {1} messages, retard {2} s", "{3}: {0} slots, {1} messages, delay {2} s", slotsDone, totalMsgs, String.format(Locale.US, "%.1f", shiftSec), name)); }
                    }
                });
            }
        });
    }
}
