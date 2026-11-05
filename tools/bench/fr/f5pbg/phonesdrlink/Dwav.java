package fr.f5pbg.phonesdrlink;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.Random;

// Écrit des fichiers WAV de test (8 kHz, 16 bits, mono) pour les essais sur le téléphone : am start ... --es decwav psk:/chemin.wav ou sstv:/chemin.wav
// Usage : java Dwav <dossier>   → psk31.wav (PSK31 à 1234 Hz, 12 dB) et sstv-m2.wav (Martin M2, 30 dB)
public class Dwav {
    static void wav(String path, double[] x, int n, double sigma) throws Exception {
        Random r = new Random(1);
        DataOutputStream o = new DataOutputStream(new FileOutputStream(path));
        int bytes = n * 2;
        o.writeBytes("RIFF"); o.writeInt(Integer.reverseBytes(36 + bytes)); o.writeBytes("WAVEfmt ");
        o.writeInt(Integer.reverseBytes(16)); o.writeShort(Short.reverseBytes((short) 1)); o.writeShort(Short.reverseBytes((short) 1));
        o.writeInt(Integer.reverseBytes(8000)); o.writeInt(Integer.reverseBytes(16000)); o.writeShort(Short.reverseBytes((short) 2)); o.writeShort(Short.reverseBytes((short) 16));
        o.writeBytes("data"); o.writeInt(Integer.reverseBytes(bytes));
        for (int i = 0; i < n; i++) {
            double v = x[i] + sigma * r.nextGaussian();
            o.writeShort(Short.reverseBytes((short) (Math.max(-1, Math.min(1, v)) * 30000)));
        }
        o.close();
    }

    public static void main(String[] a) throws Exception {
        String d = a[0];
        double[] p = Dpsk.make("CQ CQ DE F5PBG F5PBG PSE K\r\nTest PSK31 73 de F4XYZ = 599 ?", 1234, 31.25, 12, 3, new Random(2), false);
        wav(d + "/psk31.wav", p, p.length, 0);
        Dsstv.skew = 1.0; Dsstv.off = 0;
        Dsstv.gen("Martin M2", false);
        wav(d + "/sstv-m2.wav", Dsstv.buf, Dsstv.cnt, 0.5 / Math.sqrt(2) / Math.pow(10, 30 / 20.0) * Math.sqrt(8000.0 / 2400));
        System.out.println("ok");
    }
}
