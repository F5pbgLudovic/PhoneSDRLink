package fr.f5pbg.phonesdrlink;

import java.util.ArrayList;

// Essai du chemin complet des classes WsprEngine / Jt9Engine : feed() par morceaux, choix de la tranche UTC, fil de calcul, écouteur.
// L'horloge d'ancrage est réglée à la main (champs du paquet) pour que l'enregistrement réel tombe dans la tranche choisie par check().
public class Dengine {
    static void feedAll(SlotDecoder e, short[] pcm) {
        int chunk = 4096;
        byte[] b = new byte[chunk * 2];
        for (int i = 0; i < pcm.length; i += chunk) {
            int n = Math.min(chunk, pcm.length - i);
            for (int j = 0; j < n; j++) { b[2 * j] = (byte) pcm[i + j]; b[2 * j + 1] = (byte) (pcm[i + j] >> 8); }
            e.feed(b, n * 2);
        }
    }

    static void test(SlotDecoder e, String wav, int lead) throws Exception {
        final ArrayList<String> lines = new ArrayList<String>();
        final ArrayList<String> st = new ArrayList<String>();
        e.start();
        short[] w = Dwspr.readWav(wav);
        long now = System.currentTimeMillis();
        long s = Math.floorDiv(now - e.readyMs, (long) e.slotMs);
        short[] pcm = new short[12000 * (lead + e.slotMs / 1000 + 5)];
        System.arraycopy(w, 0, pcm, 12000 * lead, Math.min(w.length, pcm.length - 12000 * lead));
        feedAll(e, pcm);
        synchronized (e) { e.anchorMs = s * e.slotMs - lead * 1000L; e.lastSlot = Long.MIN_VALUE; e.shiftSec = 0; }
        e.check();
        Thread.sleep(25000);
        e.stop();
    }

    public static void main(String[] a) throws Exception {
        final long t0 = System.currentTimeMillis();
        Ft8Engine.Listener l = new Ft8Engine.Listener() {
            public void onLines(ArrayList<String> lines, int parity) { for (String x : lines) System.out.println("   LIGNE " + x); }
            public void onStatus(String s) { System.out.println("   STATUT " + s); }
        };
        String dir = "C:/Users/Ludovic/AppData/Local/Temp/wsjt/";
        System.out.println("WSPR (enregistrement réel 150426_0918, lead 3 s) :");
        test(new WsprEngine(l), dir + "150426_0918.wav", 3);
        System.out.println("JT9 (enregistrement réel 130418_1742, lead 2,5 s) :");
        test(new Jt9Engine(l), dir + "130418_1742.wav", 3);
        System.out.println("fini " + (System.currentTimeMillis() - t0) / 1000 + " s");
        System.exit(0);
    }
}
