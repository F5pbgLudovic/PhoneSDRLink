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
    static native void radeFree(long h);
    static native int radeProcess(long h, short[] in8k, int n, short[] out16k, int[] status);

    private Decoders() { }
}
