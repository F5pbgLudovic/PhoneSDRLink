package fr.f5pbg.phonesdrlink;

import java.util.ArrayList;
import java.util.Random;

public class Dt {
    static String run(String kind, double noise, String msg, double baud, double mark, double space, int stopBits, int leadIdle) {
        final StringBuilder out = new StringBuilder();
        TextEngine.Listener l = new TextEngine.Listener() {
            public void onText(String s) { out.append(s); }
            public void onStatus(String s) { }
        };
        Random rnd = new Random(7);
        ArrayList<Double> sig = new ArrayList<Double>();
        if (kind.equals("cw")) {
            double dot = 1.2 / baud * 8000, ph = 0;
            for (int i = 0; i < 16000; i++) sig.add(0.0);
            for (char ch : msg.toCharArray()) {
                if (ch == ' ') { for (int i = 0; i < (int) (4 * dot); i++) sig.add(0.0); continue; }
                String m = CwEngine.MORSE[CwEngine.CHARS.indexOf(ch)];
                for (char e : m.toCharArray()) {
                    int len = (int) ((e == '.' ? 1 : 3) * dot);
                    for (int i = 0; i < len; i++) { ph += 2 * Math.PI * mark / 8000; sig.add(0.5 * Math.sin(ph)); }
                    for (int i = 0; i < (int) dot; i++) sig.add(0.0);
                }
                for (int i = 0; i < (int) (2 * dot); i++) sig.add(0.0);
            }
            for (int i = 0; i < 16000; i++) sig.add(0.0);
        } else {
            double bit = 8000 / baud, ph = 0, acc = 0;
            ArrayList<Integer> bits = new ArrayList<Integer>();
            for (int i = 0; i < leadIdle; i++) bits.add(1);
            for (char ch : msg.toCharArray()) {
                int c = RttyEngine.LTRS.indexOf(ch);
                if (ch == ' ') c = 4;
                if (c < 0) continue;
                bits.add(0);
                for (int k = 0; k < 5; k++) bits.add((c >> k) & 1);
                for (int s = 0; s < stopBits; s++) bits.add(1);
            }
            for (int i = 0; i < 40; i++) bits.add(1);
            for (int b : bits) {
                double f = b == 1 ? mark : space;
                acc += bit;
                while (acc >= 1) { acc -= 1; ph += 2 * Math.PI * f / 8000; sig.add(0.5 * Math.sin(ph)); }
            }
        }
        TextEngine eng = kind.equals("cw") ? new CwEngine(l) : new RttyEngine(l);
        short[] chunk = new short[1024];
        int k = 0;
        for (int i = 0; i < sig.size(); i++) {
            double v = sig.get(i) + noise * rnd.nextGaussian();
            chunk[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
            if (k == chunk.length) { eng.feed(chunk, k); k = 0; }
        }
        if (k > 0) eng.feed(chunk, k);
        return out.toString();
    }

    public static void main(String[] a) {
        System.out.println("CW 18wpm   : [" + run("cw", 0.04, "CQ CQ DE F5PBG F5PBG PSE K", 18, 700, 0, 0, 0) + "]");
        System.out.println("CW 25wpm   : [" + run("cw", 0.08, "TEST DE F4XYZ 599 73", 25, 620, 0, 0, 0) + "]");
        System.out.println("RTTY 45    : [" + run("rtty", 0.04, "RYRYRYRY CQ CQ DE F5PBG F5PBG K", 45.45, 2125, 2295, 2, 120) + "]");
        System.out.println("RTTY 45 1.5: [" + run("rtty", 0.04, "CQ TEST DE F5PBG F5PBG TEST K THE QUICK BROWN FOX", 45.45, 2125, 2295, 2, 120) + "]");
        System.out.println("RTTY 50 inv: [" + run("rtty", 0.04, "CQ CQ DE F5PBG F5PBG K", 50.0, 2295, 2125, 2, 120) + "]");
        System.out.println("RTTY 75    : [" + run("rtty", 0.04, "CQ CQ DE F5PBG F5PBG K", 75.0, 1445, 1275, 2, 120) + "]");
    }
}
