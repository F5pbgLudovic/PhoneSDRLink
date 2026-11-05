package fr.f5pbg.phonesdrlink;
public class Dt3 {
    public static void main(String[] a) {
        final StringBuilder o = new StringBuilder();
        // même génération que Dt.run mais en gardant le moteur
        java.util.Random rnd = new java.util.Random(7);
        double baud = 50.0, mark = 2295, space = 2125;
        String msg = "CQ CQ DE F5PBG F5PBG K";
        double bit = 8000 / baud, ph = 0, acc = 0;
        java.util.ArrayList<Integer> bits = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 120; i++) bits.add(1);
        for (char ch : msg.toCharArray()) { int c = RttyEngine.LTRS.indexOf(ch); if (ch == ' ') c = 4; if (c < 0) continue; bits.add(0); for (int k = 0; k < 5; k++) bits.add((c >> k) & 1); bits.add(1); bits.add(1); }
        for (int i = 0; i < 40; i++) bits.add(1);
        RttyEngine e = new RttyEngine(new TextEngine.Listener() { public void onText(String s) { o.append(s); } public void onStatus(String s) { } });
        short[] chunk = new short[1024]; int k = 0;
        for (int b : bits) { double f = b == 1 ? mark : space; acc += bit; while (acc >= 1) { acc -= 1; ph += 2 * Math.PI * f / 8000; double v = 0.5 * Math.sin(ph) + 0.04 * rnd.nextGaussian(); chunk[k++] = (short) (v * 30000); if (k == 1024) { e.feed(chunk, k); k = 0; } } }
        System.out.println("[" + o + "] best=" + e.best + " fLo=" + e.fLo + " fHi=" + e.fHi);
        for (int i = 0; i < e.u.length; i++) System.out.println(e.u[i].baud + (e.u[i].reversed ? " inv " : " nor ") + e.u[i].score + " out=" + e.u[i].out);
    }
}
