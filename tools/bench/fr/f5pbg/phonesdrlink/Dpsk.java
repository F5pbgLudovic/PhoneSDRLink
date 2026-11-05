package fr.f5pbg.phonesdrlink;

import java.util.Random;

// Banc d'essai PSK31 / PSK63 : table Varicode (sans « 00 », sans préfixe commun), puis signaux synthétiques (forme d'onde en cosinus
// surélevé) avec bruit : fréquence trouvée automatiquement, fréquence décalée, AFC, 63 bauds, bruit seul.
public class Dpsk {
    static String message = "CQ CQ DE F5PBG F5PBG PSE K\r\nTest 73 de F4XYZ = 599 ?";

    static double[] make(String msg, double f, double baud, double snrDb, double lead, Random rnd, boolean noSignal) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < (int) (baud * lead); i++) b.append("0");   // préambule : inversions de phase
        for (char c : msg.toCharArray()) { b.append(PskEngine.VARI[c]); b.append("00"); }
        for (int i = 0; i < (int) baud; i++) b.append("1");
        for (int i = 0; i < (int) (baud * 1.0); i++) b.append("0");
        int sps = (int) Math.round(8000 / baud);
        int n = b.length() * sps;
        double[] x = new double[n];
        double sign = 1, prev = 1, ph = 0;
        double sig = 0.2, sigma = sig / Math.sqrt(2) / Math.pow(10, snrDb / 20.0) * Math.sqrt(8000.0 / 2400);   // SNR dans 2,4 kHz
        for (int k = 0; k < b.length(); k++) {
            if (b.charAt(k) == '0') sign = -sign;
            for (int j = 0; j < sps; j++) {
                double u = (j + 0.5) / sps;   // on interpole entre le centre du symbole précédent et le centre de celui-ci
                double a = prev * (1 + Math.cos(Math.PI * u)) / 2 + sign * (1 - Math.cos(Math.PI * u)) / 2;
                ph += 2 * Math.PI * f / 8000;
                x[k * sps + j] = noSignal ? 0 : sig * a * Math.cos(ph);
            }
            prev = sign;
        }
        for (int i = 0; i < n; i++) x[i] += sigma * rnd.nextGaussian();
        return x;
    }

    static String run(double[] x, boolean auto, int manual, int baud, boolean afc, StringBuilder status) {
        final StringBuilder out = new StringBuilder();
        PskEngine e = new PskEngine(new TextEngine.Listener() {
            public void onText(String s) { out.append(s); }
            public void onStatus(String s) { status.append(s).append(" | "); }
        });
        e.autoFreq = auto; e.manualFreq = manual; e.baud = baud; e.afc = afc;
        short[] ch = new short[1024];
        int k = 0;
        for (double v : x) {
            ch[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
            if (k == ch.length) { e.feed(ch, k); k = 0; }
        }
        if (k > 0) e.feed(ch, k);
        return out.toString().replace("\n", "/");
    }

    public static void main(String[] a) {
        // table
        int bad = 0;
        for (int i = 0; i < 128; i++) {
            if (PskEngine.VARI[i].contains("00") || PskEngine.VARI[i].startsWith("0") || PskEngine.VARI[i].endsWith("0")) { System.out.println("code avec 00 : " + i); bad++; }
        }
        System.out.println("Table Varicode : " + (bad == 0 ? "OK" : bad + " erreurs"));
        Random r = new Random(5);
        String exp = message.replace("\r\n", "/");
        double[][] cases = { { 1000, 31.25, 20, 0 }, { 1337.6, 31.25, 12, 0 }, { 2214, 31.25, 8, 0 }, { 1500, 62.5, 20, 1 }, { 640, 31.25, 5, 0 } };
        for (double[] c : cases) {
            StringBuilder st = new StringBuilder();
            double[] x = make(message, c[0], c[1], c[2], 3, r, false);
            String got = run(x, true, 1000, (int) c[1], true, st);
            System.out.println("auto " + c[0] + " Hz " + c[1] + " bauds SNR " + c[2] + " dB : [" + got + "] " + (got.contains(exp.substring(5)) ? "OK" : "ECART") + "  " + st);
        }
        {   // fréquence manuelle décalée de 9 Hz, AFC active puis inactive
            for (int afc = 1; afc >= 0; afc--) {
                StringBuilder st = new StringBuilder();
                double[] x = make(message, 1209, 31.25, 15, 3, r, false);
                String got = run(x, false, 1200, 31, afc == 1, st);
                System.out.println("manuel 1200 pour 1209 Hz AFC=" + afc + " : [" + got + "] " + st);
            }
        }
        {   // bruit seul
            StringBuilder st = new StringBuilder();
            double[] x = make(message, 1000, 31.25, 5, 3, r, true);
            String got = run(x, true, 1000, 31, true, st);
            System.out.println("bruit seul : [" + got + "] " + st);
        }
    }
}
