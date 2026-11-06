package fr.f5pbg.phonesdrlink;

import java.io.*;
import java.util.*;

// Banc d'essai JT9 : encodeur, essais avec bruit, décalages, plusieurs signaux, bruit seul, enregistrement réel.
public class Djt9 {
    static final int R = 12000;
    static final long NBASE = 262177560L;

    static long ngFor(String g) {
        g = g.trim();
        if (g.length() == 4 && Character.isLetter(g.charAt(0)) && Character.isLetter(g.charAt(1)) && Character.isDigit(g.charAt(2))) {
            int L = g.charAt(0) - 'A', La = g.charAt(1) - 'A', d = g.charAt(2) - '0', dd = g.charAt(3) - '0';
            return (179 - 10 * L - d) * 180 + 10 * La + dd;
        }
        if (g.isEmpty()) return 32401;
        if (g.equals("RO")) return 32401 + 61;
        if (g.equals("RRR")) return 32401 + 62;
        if (g.equals("73")) return 32401 + 63;
        if (g.startsWith("R-")) return 32401 + 30 + Integer.parseInt(g.substring(2));
        if (g.startsWith("-")) return 32401 + Integer.parseInt(g.substring(1));
        throw new IllegalArgumentException(g);
    }

    static int[] tones(String c1, String c2, String grid) {
        long n1 = c1.equals("CQ") ? NBASE + 1 : c1.equals("QRZ") ? NBASE + 2 : c1.equals("DE") ? 267796945L : Dwspr.packCall(c1);
        long n2 = Dwspr.packCall(c2), ng = ngFor(grid);
        int[] bits = new int[103];
        for (int i = 0; i < 28; i++) bits[i] = (int) ((n1 >> (27 - i)) & 1);
        for (int i = 0; i < 28; i++) bits[28 + i] = (int) ((n2 >> (27 - i)) & 1);
        for (int i = 0; i < 16; i++) bits[56 + i] = (int) ((ng >> (15 - i)) & 1);
        int[] enc = WsjtCore.encode(bits, 103);
        int[] tx = new int[207];
        for (int i = 0; i < 206; i++) tx[Jt9Engine.J0[i]] = enc[i];
        int[] t = new int[85];
        int d = 0;
        for (int k = 0; k < 85; k++) {
            if (Jt9Engine.SYNC[k]) t[k] = 0;
            else {
                int b = (tx[3 * d] << 2) | (tx[3 * d + 1] << 1) | tx[3 * d + 2];
                t[k] = (b ^ (b >> 1)) + 1;
                d++;
            }
        }
        return t;
    }

    static final int WIN = 56500 * 12;   // 56,5 s ; début de tranche à 2,5 s ; signal nominal à 3,5 s

    static ArrayList<Jt9Engine.Msg> run(double[] buf) {
        return Jt9Engine.decodeWindow(Dwspr.toShorts(buf), buf.length, 3500);
    }

    static String show(ArrayList<Jt9Engine.Msg> l) {
        StringBuilder sb = new StringBuilder();
        for (Jt9Engine.Msg m : l) sb.append(String.format(Locale.US, "[%s snr%d dt%+.1f f%.1f e%d] ", m.text, m.snr, m.dt, m.freq, m.errs));
        return sb.length() == 0 ? "(rien)" : sb.toString();
    }

    public static void main(String[] a) throws Exception {
        double sigma = 3000;
        long t0 = System.currentTimeMillis();
        String what = a.length > 0 ? a[0] : "all";
        if (what.equals("wav")) {   // WAV de démonstration : a[1] = dossier (60 s, signal à 1 s)
            double[] b = Dwspr.noise(60 * R, sigma, 9);
            Dwspr.addTones(b, tones("CQ", "F5PBG", "JN18"), 1200, 1.0, Dwspr.ampFor(-18, sigma), Jt9Engine.SYMLEN, Jt9Engine.DF);
            Dwspr.writeWav(a[1] + "/jt9.wav", Dwspr.toShorts(b));
            return;
        }
        double[] b = Dwspr.noise(WIN, 50, 1);
        Dwspr.addTones(b, tones("CQ", "K1JT", "FN20"), 1500, 3.5, Dwspr.ampFor(0, 50), Jt9Engine.SYMLEN, Jt9Engine.DF);
        System.out.println("1 propre : " + show(run(b)));
        String[] snrLabel = new String[0];
        double sf = a.length > 2 ? Double.parseDouble(a[2]) : -1;
        if (sf > 0) Jt9Engine.SYMFAC = sf;
        for (int snr = -14; snr >= -28; snr -= 2) {
            int ok = 0, tries = 6;
            for (int tr = 0; tr < tries; tr++) {
                b = Dwspr.noise(WIN, sigma, 200 + tr + snr * 7);
                Dwspr.addTones(b, tones("F5PBG", "K1ABC", "R-12"), 800 + tr * 211.3, 3.5 + tr * 0.27 - 0.5, Dwspr.ampFor(snr, sigma), Jt9Engine.SYMLEN, Jt9Engine.DF);
                ArrayList<Jt9Engine.Msg> l = run(b);
                for (Jt9Engine.Msg m : l) if (m.text.equals("F5PBG K1ABC R-12")) ok++;
                if (tr == 0) System.out.println("   SNR " + snr + " : " + show(l));
            }
            System.out.println("2 SNR " + snr + " dB : " + ok + "/" + tries);
        }
        double[][] cfg = { { 330, 3.5 }, { 2850, 3.5 }, { 1500, 6.3 }, { 1500, 1.0 } };
        for (double[] c : cfg) {
            b = Dwspr.noise(WIN, sigma, 77);
            Dwspr.addTones(b, tones("DL1ABC", "JA1XYZ", "PM95"), c[0], c[1], Dwspr.ampFor(-18, sigma), Jt9Engine.SYMLEN, Jt9Engine.DF);
            System.out.println("3 f=" + c[0] + " t=" + c[1] + " : " + show(run(b)));
        }
        b = Dwspr.noise(WIN, sigma, 5);
        String[][] ms = { { "CQ", "F5PBG", "JN18" }, { "K1ABC", "W9XYZ", "-05" }, { "VK2XYZ", "G4ABC", "RRR" }, { "G4ABC", "VK2XYZ", "73" }, { "QRZ", "PA0XY", "JO22" }, { "9A1ZZ", "EA3ABC", "R-20" } };
        double[] fr = { 600, 900, 1250, 1600, 1950, 2300 }, tt = { 3.5, 3.9, 3.0, 4.4, 3.6, 2.9 }, sn = { -10, -14, -18, -22, -24, -12 };
        for (int i = 0; i < ms.length; i++) Dwspr.addTones(b, tones(ms[i][0], ms[i][1], ms[i][2]), fr[i], tt[i], Dwspr.ampFor(sn[i], sigma), Jt9Engine.SYMLEN, Jt9Engine.DF);
        System.out.println("4 multi : " + show(run(b)));
        int fa = 0, N = a.length > 1 ? Integer.parseInt(a[1]) : 20;
        for (int i = 0; i < N; i++) {
            ArrayList<Jt9Engine.Msg> l = run(Dwspr.noise(WIN, sigma, 3000 + i));
            if (!l.isEmpty()) { fa += l.size(); System.out.println("  FAUX : " + show(l)); }
        }
        System.out.println("5 bruit seul : " + fa + " faux décodages sur " + N + " fenêtres");
        File f = new File("C:/Users/Ludovic/AppData/Local/Temp/wsjt/130418_1742.wav");
        if (!f.exists()) f = new File("/tmp/wsjt/130418_1742.wav");
        if (f.exists()) {
            short[] s = Dwspr.readWav(f.getPath());
            short[] w = new short[WIN];
            System.arraycopy(s, 0, w, 2500 * 12, Math.min(s.length, WIN - 2500 * 12));   // début de fichier = début de tranche
            ArrayList<Jt9Engine.Msg> l = Jt9Engine.decodeWindow(w, w.length, 3500);
            System.out.println("6 enregistrement réel : " + l.size() + " messages");
            for (Jt9Engine.Msg m : l) System.out.println(String.format(Locale.US, "   %4d %+5.1f %8.1f  %s  (e%d)", m.snr, m.dt, m.freq, m.text, m.errs));
        }
        System.out.println("durée " + (System.currentTimeMillis() - t0) / 1000 + " s");
    }
}
