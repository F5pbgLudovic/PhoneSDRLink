package fr.f5pbg.phonesdrlink;

// Décodeurs numériques en code natif (app/src/main/cpp) : FT8 (ft8_lib) et FreeDV RADE V1 (radae_nopy + vocodeur FARGAN d'Opus).
final class Decoders {
    static boolean loaded;
    static {
        try { System.loadLibrary("sdrdec"); loaded = true; } catch (Throwable t) { loaded = false; }
    }

    /** Lignes « snr|dt|freq|texte » pour une tranche FT8 (échantillons 16 bits mono, 12000 Hz de préférence). */
    static native String ft8Decode(short[] pcm, int n, int rate, int ft4);
    static native void ft8Reset();

    /** FreeDV RADE (64 bits seulement : radeNew renvoie 0 ailleurs). Entrée 8000 Hz, sortie parole 16000 Hz ; status = { sync, snr_dB, décalage en 0,1 Hz }. */
    static native long radeNew();
    /** v2 = 1 : RADE V2 (FreeDV 3.0, en développement, sources de rade_c). */
    static native long radeNewV(int v2);
    /** Essai de bouclage V2 (présent seulement dans les versions d essai de la bibliothèque) : texte du résultat. */
    static native String radeLoopTest(int snr10, int seconds);
    static native void radeFree(long h);
    static native int radeProcess(long h, short[] in8k, int n, short[] out16k, int[] status);

    /** Olivia / Contestia (réception, bibliothèque MFSK de Pawel Jalocha, comme fldigi). centre en Hz (audio), thr10 = seuil de synchro en dixièmes (30 = 3,0). 0 si échec. */
    static native long oliviaNew(int tones, int bw, int margin, int integ, int contestia, int center, int reverse, int thr10);
    static native void oliviaFree(long h);
    static native int oliviaCenter(long h, int center);
    /** Renvoie le nombre de caractères mis dans out ; st = { rapport signal/bruit de synchro, décalage de fréquence (Hz) }. */
    static native int oliviaProcess(long h, short[] in, int n, byte[] out, double[] st);
    /** Essai de bouclage (émetteur + bruit + récepteur) : « texte|rapport|décalage ». */
    static native String oliviaLoopTest(int tones, int bw, int contestia, String text, int snr10, int dfreq);

    /** Fichier WAV d'essai Olivia / Contestia (voir olivia_jni.cpp) ; renvoie le nombre d'échantillons. */
    static native int oliviaMakeWav(int tones, int bw, int contestia, String text, int snr10, int center, String path);

    private Decoders() { }
}
