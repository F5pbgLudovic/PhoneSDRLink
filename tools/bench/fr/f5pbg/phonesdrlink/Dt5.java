package fr.f5pbg.phonesdrlink;
import java.util.ArrayList; import java.util.Random;
public class Dt5 {
    static String go(double baud, double mark, double space, double setBaud, int setShift, int setPol, int setFreq) {
        final StringBuilder out = new StringBuilder();
        RttyEngine e = new RttyEngine(new TextEngine.Listener() { public void onText(String s) { out.append(s); } public void onStatus(String s) { } });
        e.baudSetting = setBaud; e.shiftSetting = setShift; e.polSetting = setPol; e.freqSetting = setFreq; e.settingsChanged();
        Random rnd = new Random(7);
        double bit = 8000 / baud, ph = 0, acc = 0;
        ArrayList<Integer> bits = new ArrayList<Integer>();
        for (int i = 0; i < 120; i++) bits.add(1);
        for (char ch : "CQ CQ DE F5PBG K".toCharArray()) { int c = RttyEngine.LTRS.indexOf(ch); if (ch == ' ') c = 4; if (c < 0) continue; bits.add(0); for (int k = 0; k < 5; k++) bits.add((c >> k) & 1); bits.add(1); bits.add(1); }
        for (int i = 0; i < 40; i++) bits.add(1);
        short[] chunk = new short[1024]; int k = 0;
        for (int b : bits) { double f = b == 1 ? mark : space; acc += bit; while (acc >= 1) { acc -= 1; ph += 2 * Math.PI * f / 8000; double v = 0.5 * Math.sin(ph) + 0.04 * rnd.nextGaussian(); chunk[k++] = (short) (v * 30000); if (k == 1024) { e.feed(chunk, k); k = 0; } } }
        return out.toString();
    }
    public static void main(String[] a) {
        System.out.println("auto            : [" + go(45.45, 2125, 2295, 0, 0, 0, 0) + "]");
        System.out.println("baud 45.45 fixe : [" + go(45.45, 2125, 2295, 45.45, 0, 0, 0) + "]");
        System.out.println("baud 50 sur 45  : [" + go(45.45, 2125, 2295, 50, 0, 0, 0) + "]  (doit être faux)");
        System.out.println("50 baud, 50 fixe: [" + go(50, 2125, 2295, 50, 170, 0, 0) + "]");
        System.out.println("inverse, pol=2  : [" + go(45.45, 2295, 2125, 0, 0, 2, 0) + "]");
        System.out.println("inverse, pol=1  : [" + go(45.45, 2295, 2125, 0, 0, 1, 0) + "]  (doit être faux)");
        System.out.println("shift 425 impose: [" + go(45.45, 1445, 1870, 0, 425, 0, 0) + "]");
        System.out.println("freq manuelle   : [" + go(45.45, 2125, 2295, 0, 170, 0, 2125) + "]");
    }
}
