package fr.f5pbg.phonesdrlink;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Random;

// Banc d'essai JS8 : Dj8 wav <vitesse> <fichier.wav>   (décode un enregistrement 12 kHz : tranche commençant au début du fichier)
//                    Dj8 synth <vitesse> <snr dB> [n]  (signaux synthétiques avec bruit)
//                    Dj8 noise <vitesse> <n>           (bruit seul : aucun décodage attendu)
public class Dj8 {
    static short[] readWav(String path) throws Exception {
        RandomAccessFile f = new RandomAccessFile(path, "r");
        byte[] b = new byte[(int) f.length()];
        f.readFully(b);
        f.close();
        int n = (b.length - 44) / 2;
        short[] s = new short[n];
        for (int i = 0; i < n; i++) s[i] = (short) ((b[44 + 2 * i] & 255) | (b[45 + 2 * i] << 8));
        return s;
    }

    static int SHIFT = 0;
    static short[] window(short[] x, int speed) {
        int pre = Js8Engine.PRE_MS * 12;
        int ready = Js8Engine.DELAY[speed] + (int) (79000L * Js8Engine.NSPS[speed] / 12000) + 1500;
        int n = (Js8Engine.PRE_MS + ready) * 12;
        short[] w = new short[n];
        for (int i = 0; i < x.length && pre + SHIFT + i < n; i++) w[pre + SHIFT + i] = x[i];
        return w;
    }

    static void show(ArrayList<Js8Engine.Msg> l, int speed) {
        for (Js8Engine.Msg m : l) {
            String tx = null;
            try { tx = Js8Text.frameText(m.chars, m.i3, speed >= 3); } catch (Throwable e) { tx = "(erreur " + e + ")"; }
            System.out.printf("  snr %+3d dt %+5.2f f %7.1f sync %2d  i3=%d [%s]  %s%n", m.snr, m.dt, m.freq, m.nsync, m.i3, m.chars, tx);
        }
    }

    /** Signal : tonalités à f0, début t0 s, amplitude a (phase continue). */
    static void add(double[] x, int[] tone, int speed, double f0, double t0, double amp) {
        int nsps = Js8Engine.NSPS[speed];
        double sp = 12000.0 / nsps, ph = 0;
        int start = (int) Math.round(t0 * 12000);
        for (int k = 0; k < 79; k++) {
            double f = f0 + tone[k] * sp;
            for (int i = 0; i < nsps; i++) {
                int idx = start + k * nsps + i;
                ph += 2 * Math.PI * f / 12000;
                if (idx >= 0 && idx < x.length) x[idx] += amp * Math.sin(ph);
            }
        }
    }

    static String rnd(Random r) {
        String a = Js8Engine.ALPHABET;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(a.charAt(r.nextInt(64)));
        return sb.toString();
    }

    public static void main(String[] a) throws Exception {
        try { java.io.FileInputStream fi = new java.io.FileInputStream("app/src/main/assets/js8jsc.bin"); Js8Text.loadJsc(fi); fi.close(); } catch (Exception e) { System.out.println("JSC : " + e); }
        String mode = a[0];
        int speed = Integer.parseInt(a[1]);
        for (int i = 2; i < a.length; i++) {
            if (a[i].startsWith("amp=")) Js8Engine.AMPLITUDE = a[i].equals("amp=1");
            if (a[i].startsWith("scale=")) Js8Engine.LLR_SCALE = Double.parseDouble(a[i].substring(6));
            if (a[i].startsWith("sync=")) Js8Engine.SYNC_MIN = Double.parseDouble(a[i].substring(5));
            if (a[i].startsWith("minsync=")) Js8Engine.MIN_SYNC = Integer.parseInt(a[i].substring(8));
            if (a[i].startsWith("cand=")) Js8Engine.MAX_CAND = Integer.parseInt(a[i].substring(5));
            if (a[i].startsWith("shift=")) SHIFT = Integer.parseInt(a[i].substring(6));
            if (a[i].startsWith("passes=")) Js8Engine.PASSES = Integer.parseInt(a[i].substring(7));
        }
        if (mode.equals("wav")) {
            short[] w = window(readWav(a[2]), speed);
            long t = System.currentTimeMillis();
            ArrayList<Js8Engine.Msg> l = Js8Engine.decodeWindow(w, w.length, speed, Js8Engine.PRE_MS + Js8Engine.DELAY[speed]);
            System.out.println(a[2] + " : " + l.size() + " messages en " + (System.currentTimeMillis() - t) + " ms");
            show(l, speed);
        } else if (mode.equals("synth")) {
            double snr = Double.parseDouble(a[2]);
            int cnt = a.length > 3 && !a[3].contains("=") ? Integer.parseInt(a[3]) : 6;
            Random r = new Random(7);
            int ok = 0;
            double sp = 12000.0 / Js8Engine.NSPS[speed];
            // bruit blanc gaussien de 2500 Hz : variance pour que la puissance signal / puissance bruit (2500 Hz) = snr
            double sigPow = 0.5;                       // amplitude 1, sinus
            double noiseVar = sigPow / Math.pow(10, snr / 10) * (6000.0 / 2500.0);   // bruit sur 6000 Hz de bande (12 kHz réels)
            for (int c = 0; c < cnt; c++) {
                String msg = rnd(r);
                int i3 = r.nextInt(8);
                int[] tone = Js8Engine.encode(msg, i3, Js8Engine.ORIGINAL[speed]);
                short[] ww = new short[Js8Engine.PRE_MS * 12 + (int) (20 * 12000)];
                double[] x = new double[(int) (20 * 12000)];
                double f0 = 600 + r.nextDouble() * 1500;
                double t0 = Js8Engine.DELAY[speed] / 1000.0 + (r.nextDouble() - 0.5) * 1.6;
                add(x, tone, speed, f0, t0, 1.0);
                Js8Engine.TRUTH = tone; Js8Engine.TRUTHF = f0;
                double sd = Math.sqrt(noiseVar);
                short[] xs = new short[x.length];
                for (int i = 0; i < x.length; i++) xs[i] = (short) Math.round((x[i] + r.nextGaussian() * sd) * (4000 / Math.max(sd, 0.01)));
                short[] w = window(xs, speed);
                ArrayList<Js8Engine.Msg> l = Js8Engine.decodeWindow(w, w.length, speed, Js8Engine.PRE_MS + Js8Engine.DELAY[speed]);
                boolean found = false;
                for (Js8Engine.Msg m : l) if (m.chars.equals(msg) && m.i3 == i3) found = true;
                if (found) ok++;
                System.out.printf("%s f0=%.1f t0=%.2f : %s (%d decodes)%n", msg, f0, t0, found ? "OK" : "--", l.size());
                if (!found || l.size() > 1) show(l, speed);
            }
            System.out.println("SNR " + snr + " dB : " + ok + "/" + cnt);
        } else if (mode.equals("noise")) {
            int cnt = Integer.parseInt(a[2]);
            Random r = new Random(11);
            int bad = 0;
            for (int c = 0; c < cnt; c++) {
                short[] xs = new short[20 * 12000];
                for (int i = 0; i < xs.length; i++) xs[i] = (short) Math.round(r.nextGaussian() * 3000);
                short[] w = window(xs, speed);
                ArrayList<Js8Engine.Msg> l = Js8Engine.decodeWindow(w, w.length, speed, Js8Engine.PRE_MS + Js8Engine.DELAY[speed]);
                if (!l.isEmpty()) { bad += l.size(); show(l, speed); }
            }
            System.out.println("bruit seul : " + bad + " faux décodages sur " + cnt + " fenêtres");
        }
    }
}
