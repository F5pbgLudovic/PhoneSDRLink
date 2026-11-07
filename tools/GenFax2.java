import java.io.*;
import java.util.Random;
public class GenFax2 {
  static double ph = 0; static short[] buf = new short[8000*400]; static int n = 0; static Random rnd = new Random(5);
  static void tone(double f, int cnt) { for (int i = 0; i < cnt; i++) { ph += 2*Math.PI*f/8000; put(); } }
  static void put() { double v = Math.sin(ph)*0.5 + rnd.nextGaussian()*0.03; buf[n++] = (short)Math.max(-32767, Math.min(32767, v*32767)); }
  static double g(double x, int y) {
    if (x < 0.0066 || x > 0.9934) return 0.0;
    if (x < 0.33) return x/0.33;
    if (x < 0.66) return (((int)((x-0.33)/0.02)) % 2 == 0) ? 0.1 : 0.9;
    double cx = 0.83, cy = 75, dx = (x-cx)*1809/2.0, dy = y-cy;
    return Math.sqrt(dx*dx+dy*dy) < 50 ? 0.0 : 1.0;
  }
  public static void main(String[] a) throws Exception {
    String mode = a.length > 3 ? a[3] : ""; boolean weak = mode.contains("weak"), mid = mode.contains("mid"); double lpm = 120, linesamp = 4000 * (1 + Double.parseDouble(a[1]));
    for (int i = 0; i < 16000; i++) { ph += 2*Math.PI*1900/8000*0; put(); } // bruit seul 2 s (phase fixe)
    // tonalité de départ 300 Hz : alternance noir/blanc, 5 s
    for (int i = 0; i < (mid ? 1234 : 40000); i++) { double f = ((int)(i/ (8000.0/300/2)) % 2 == 0) ? 1500 : 2300; ph += 2*Math.PI*f/8000; put(); }
    // phasage 20 lignes
    int lines = mid ? 0 : 20, img = Integer.parseInt(a[2]);
    double pos = 0;
    for (int L = 0; L < lines; L++) for (int i = 0; i < (int)Math.round((L+1)*linesamp) - (int)Math.round(L*linesamp); i++) {
       double fr = (double) i / linesamp; double f = fr < 0.05 ? (weak ? 1750 : 1500) : (weak ? 2020 : 2300); ph += 2*Math.PI*f/8000; put(); }
    // image
    for (int L = 0; L < img; L++) { int cnt = (int)Math.round((L+1)*linesamp) - (int)Math.round(L*linesamp);
       for (int i = 0; i < cnt; i++) { double fr = (double) i / cnt; double f = 1500 + 800 * g(fr, L); ph += 2*Math.PI*f/8000; put(); } }
    // tonalité d'arrêt 450 Hz, 5 s
    for (int i = 0; i < 40000; i++) { double f = ((int)(i/ (8000.0/450/2)) % 2 == 0) ? 1500 : 2300; ph += 2*Math.PI*f/8000; put(); }
    for (int i = 0; i < 16000; i++) put();
    try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(a[0])))) {
      int len = n*2; o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36+len)); o.writeBytes("WAVEfmt "); o.writeInt(Integer.reverseBytes(16));
      o.writeShort(Short.reverseBytes((short)1)); o.writeShort(Short.reverseBytes((short)1)); o.writeInt(Integer.reverseBytes(8000)); o.writeInt(Integer.reverseBytes(16000));
      o.writeShort(Short.reverseBytes((short)2)); o.writeShort(Short.reverseBytes((short)16)); o.writeBytes("data"); o.writeInt(Integer.reverseBytes(len));
      for (int i = 0; i < n; i++) o.writeShort(Short.reverseBytes(buf[i]));
    }
    System.out.println("samples " + n);
  }
}
