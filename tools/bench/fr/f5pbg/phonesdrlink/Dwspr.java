package fr.f5pbg.phonesdrlink;

import java.io.*;
import java.util.*;

// Banc d'essai WSPR : encodeur, génération de WAV (12 kHz), essais avec bruit, décalages, plusieurs signaux, bruit seul, enregistrement réel.
public class Dwspr {
    static final int R = 12000;
    static final String CH = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ ";

    static long packCall(String call) {
        call = call.toUpperCase();
        if (call.length() < 3 || (!Character.isDigit(call.charAt(2)) && Character.isDigit(call.charAt(1)))) call = " " + call;
        while (call.length() < 6) call += " ";
        long n = CH.indexOf(call.charAt(0));
        n = n * 36 + CH.indexOf(call.charAt(1));
        n = n * 10 + CH.indexOf(call.charAt(2));
        for (int i = 3; i < 6; i++) { char c = call.charAt(i); n = n * 27 + (c == ' ' ? 26 : c - 'A'); }
        return n;
    }

    static int[] wsprTones(String call, String grid, int dbm) {
        long n1 = packCall(call);
        int L = grid.charAt(0) - 'A', La = grid.charAt(1) - 'A', d = grid.charAt(2) - '0', dd = grid.charAt(3) - '0';
        long ng = (179 - 10 * L - d) * 180 + 10 * La + dd;
        long n2 = ng * 128 + dbm + 64;
        int[] bits = new int[81];
        for (int i = 0; i < 28; i++) bits[i] = (int) ((n1 >> (27 - i)) & 1);
        for (int i = 0; i < 22; i++) bits[28 + i] = (int) ((n2 >> (21 - i)) & 1);
        int[] enc = WsjtCore.encode(bits, 81);
        int[] tx = new int[162];
        for (int p = 0; p < 162; p++) tx[WsprEngine.J0[p]] = enc[p];
        int[] tones = new int[162];
        for (int k = 0; k < 162; k++) tones[k] = WsprEngine.PR3[k] + 2 * tx[k];
        return tones;
    }

    /** Ajoute un signal FSK à buf : f0 = fréquence de la tonalité 0 (Hz), début en secondes, amplitude amp. */
    static void addTones(double[] buf, int[] tones, double f0, double startSec, double amp, double symLen, double df) {
        double ph = 0;
        int s0 = (int) Math.round(startSec * R);
        for (int k = 0; k < tones.length; k++) {
            int a = s0 + (int) Math.round(k * symLen), b = s0 + (int) Math.round((k + 1) * symLen);
            double w = 2 * Math.PI * (f0 + tones[k] * df) / R;
            for (int i = a; i < b; i++) { if (i >= 0 && i < buf.length) buf[i] += amp * Math.sin(ph); ph += w; }
        }
    }

    // amplitude d'une sinusoïde pour un SNR (dB) dans 2500 Hz, bruit blanc d'écart-type sigma
    static double ampFor(double snrDb, double sigma) {
        double noise2500 = sigma * sigma * 2500.0 / 6000.0;
        return Math.sqrt(2 * noise2500 * Math.pow(10, snrDb / 10));
    }

    static short[] toShorts(double[] b) {
        short[] s = new short[b.length];
        for (int i = 0; i < b.length; i++) s[i] = (short) Math.max(-32768, Math.min(32767, Math.round(b[i])));
        return s;
    }

    static double[] noise(int n, double sigma, long seed) {
        Random r = new Random(seed);
        double[] b = new double[n];
        for (int i = 0; i < n; i++) b[i] = sigma * r.nextGaussian();
        return b;
    }

    static void writeWav(String path, short[] s) throws IOException {
        DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
        int n = s.length * 2;
        o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + n)); o.writeBytes("WAVEfmt ");
        o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1));
        o.writeInt(Integer.reverseBytes(R)); o.writeInt(Integer.reverseBytes(R * 2)); o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16));
        o.writeBytes("data"); o.writeInt(Integer.reverseBytes(n));
        for (short v : s) o.writeShort(Short.reverseBytes(v));
        o.close();
    }

    static short[] readWav(String path) throws IOException {
        byte[] b = java.nio.file.Files.readAllBytes(new File(path).toPath());
        int n = (b.length - 44) / 2;
        short[] s = new short[n];
        for (int i = 0; i < n; i++) s[i] = (short) ((b[44 + 2 * i] & 255) | (b[45 + 2 * i] << 8));
        return s;
    }

    // fenêtre WSPR : 121 s, le début de tranche est à 3 s ; signal nominal à 4 s
    static final int WIN = 121 * R;

    static ArrayList<WsprEngine.Msg> run(double[] buf) {
        return WsprEngine.decodeWindow(toShorts(buf), buf.length, 4000);
    }

    static String show(ArrayList<WsprEngine.Msg> l) {
        StringBuilder sb = new StringBuilder();
        for (WsprEngine.Msg m : l) sb.append(String.format(Locale.US, "[%s snr%d dt%+.1f f%.1f e%d] ", m.text, m.snr, m.dt, m.freq, m.errs));
        return sb.length() == 0 ? "(rien)" : sb.toString();
    }

    public static void main(String[] a) throws Exception {
        double sigma = 3000;
        long t0 = System.currentTimeMillis();
        String what = a.length > 0 ? a[0] : "all";
        if (what.equals("wav")) {   // WAV de démonstration : a[1] = dossier
            double[] b = noise(WIN, sigma, 9);
            addTones(b, wsprTones("F5PBG", "JN18", 37), 1500, 4.0, ampFor(-18, sigma), 8192, WsprEngine.DF);
            writeWav(a[1] + "/wspr.wav", toShorts(b));
            return;
        }
        double[] b = noise(WIN, 50, 1);
        addTones(b, wsprTones("K1JT", "FN20", 30), 1500, 4.0, ampFor(0, 50), 8192, WsprEngine.DF);
        System.out.println("1 propre : " + show(run(b)));
        for (int snr = -18; snr >= -30; snr -= 2) {
            int ok = 0, tries = 6;
            for (int tr = 0; tr < tries; tr++) {
                b = noise(WIN, sigma, 100 + tr + snr * 7);
                addTones(b, wsprTones("F5PBG", "JN18", 37), 1470 + tr * 11.3, 4.0 + tr * 0.31, ampFor(snr, sigma), 8192, WsprEngine.DF);
                ArrayList<WsprEngine.Msg> l = run(b);
                for (WsprEngine.Msg m : l) if (m.text.equals("F5PBG JN18 37")) ok++;
                if (tr == 0) System.out.println("   SNR " + snr + " : " + show(l));
            }
            System.out.println("2 SNR " + snr + " dB : " + ok + "/" + tries);
        }
        double[][] cfg = { { 1403, 4.0 }, { 1594, 4.0 }, { 1500, 6.6 }, { 1500, 1.6 } };
        for (double[] c : cfg) {
            b = noise(WIN, sigma, 77);
            addTones(b, wsprTones("DL1ABC", "JO62", 23), c[0], c[1], ampFor(-20, sigma), 8192, WsprEngine.DF);
            System.out.println("3 f=" + c[0] + " t=" + c[1] + " : " + show(run(b)));
        }
        b = noise(WIN, sigma, 78);
        {
            int[] tones = wsprTones("W1AW", "FN31", 10);
            double ph = 0; int s0 = 4 * R;
            double amp = ampFor(-18, sigma);
            for (int k = 0; k < 162; k++) {
                int aa = s0 + k * 8192, bb = aa + 8192;
                double w = 2 * Math.PI * (1500 + 1.2 * k / 161.0 + tones[k] * WsprEngine.DF) / R;
                for (int i = aa; i < bb; i++) { b[i] += amp * Math.sin(ph); ph += w; }
            }
        }
        System.out.println("3 dérive 1,2 Hz : " + show(run(b)));
        b = noise(WIN, sigma, 5);
        String[][] ms = { { "F5PBG", "JN18", "37" }, { "K1ABC", "FN42", "30" }, { "VK2XYZ", "QF56", "23" }, { "G4ABC", "IO91", "10" }, { "PA0XY", "JO22", "7" }, { "9A1ZZ", "JN85", "3" } };
        double[] fr = { 1410, 1442, 1475, 1511, 1545, 1580 }, tt = { 4.0, 4.4, 3.7, 4.9, 4.1, 3.5 }, sn = { -10, -14, -18, -22, -24, -12 };
        for (int i = 0; i < ms.length; i++) addTones(b, wsprTones(ms[i][0], ms[i][1], Integer.parseInt(ms[i][2])), fr[i], tt[i], ampFor(sn[i], sigma), 8192, WsprEngine.DF);
        System.out.println("4 multi : " + show(run(b)));
        int fa = 0, N = a.length > 1 ? Integer.parseInt(a[1]) : 20;
        for (int i = 0; i < N; i++) {
            ArrayList<WsprEngine.Msg> l = run(noise(WIN, sigma, 1000 + i));
            if (!l.isEmpty()) { fa += l.size(); System.out.println("  FAUX : " + show(l)); }
        }
        System.out.println("5 bruit seul : " + fa + " faux décodages sur " + N + " fenêtres");
        File f = new File("C:/Users/Ludovic/AppData/Local/Temp/wsjt/150426_0918.wav");
        if (!f.exists()) f = new File("/tmp/wsjt/150426_0918.wav");
        if (f.exists()) {
            short[] s = readWav(f.getPath());
            short[] w = new short[WIN];
            System.arraycopy(s, 0, w, 3 * R, Math.min(s.length, WIN - 3 * R));   // début de fichier = début de tranche
            ArrayList<WsprEngine.Msg> l = WsprEngine.decodeWindow(w, w.length, 4000);
            System.out.println("6 enregistrement réel : " + l.size() + " messages");
            for (WsprEngine.Msg m : l) System.out.println(String.format(Locale.US, "   %4d %+5.1f %8.1f  %s  (e%d)", m.snr, m.dt, m.freq, m.text, m.errs));
        }
        System.out.println("durée " + (System.currentTimeMillis() - t0) / 1000 + " s");
    }
}
