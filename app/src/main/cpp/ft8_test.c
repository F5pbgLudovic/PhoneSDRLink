/* Essai en ligne de commande (adb shell) : ft8_test fichier.wav  (WAV 16 bits mono 12000 Hz) */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "sdrdec_ft8.h"
int main(int argc, char **argv) {
    if (argc < 2) return 1;
    FILE *f = fopen(argv[1], "rb");
    if (!f) { perror("open"); return 2; }
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 44, SEEK_SET);
    int n = (int)((sz - 44) / 2);
    short *s = malloc(n * 2); float *x = malloc(n * sizeof(float));
    if (fread(s, 2, n, f) != (size_t)n) return 3;
    for (int i = 0; i < n; i++) x[i] = s[i] / 32768.0f;
    static char out[8192];
    int k = sdrdec_ft8_decode(x, n, 12000, 0, out, sizeof(out));
    printf("%d messages\n%s", k, out);
    return 0;
}
