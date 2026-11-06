/* Essai en ligne de commande (adb shell) : ft8_test fichier.wav [mode]  (WAV 16 bits mono 12000 Hz ; mode 0 = FT8, 1 = FT4, 2 = FT2) */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "sdrdec_ft8.h"
int main(int argc, char **argv) {
    if (argc < 2) return 1;
    FILE *f = fopen(argv[1], "rb");
    if (!f) { perror("open"); return 2; }
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    unsigned char *raw = malloc(sz);
    if (fread(raw, 1, sz, f) != (size_t)sz) return 3;
    long off = 44;
    for (long i = 12; i + 8 < sz; ) {   /* cherche le bloc « data » (il peut y avoir un bloc LIST avant) */
        long len = raw[i + 4] | (raw[i + 5] << 8) | (raw[i + 6] << 16) | ((long)raw[i + 7] << 24);
        if (memcmp(raw + i, "data", 4) == 0) { off = i + 8; break; }
        i += 8 + len + (len & 1);
    }
    int n = (int)((sz - off) / 2);
    float *x = malloc(n * sizeof(float));
    for (int i = 0; i < n; i++) x[i] = (short)(raw[off + 2 * i] | (raw[off + 2 * i + 1] << 8)) / 32768.0f;
    static char out[8192];
    int mode = argc > 2 ? atoi(argv[2]) : 0;
    int slot = mode == 2 ? 45000 : (mode == 1 ? 90000 : 180000);
    int step = argc > 3 ? atoi(argv[3]) : 0;   /* pas de balayage en échantillons (0 = une seule tranche au début) */
    if (step <= 0) { int k = sdrdec_ft8_decode(x, n < slot ? n : slot, 12000, mode, out, sizeof(out)); printf("%d messages\n%s", k, out); return 0; }
    for (int s = 0; s + slot <= n; s += step) {
        int k = sdrdec_ft8_decode(x + s, slot, 12000, mode, out, sizeof(out));
        if (k) printf("[debut %.2f s] %d messages\n%s", s / 12000.0, k, out);
    }
    return 0;
}
