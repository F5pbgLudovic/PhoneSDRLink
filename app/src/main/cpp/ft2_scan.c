/* Banc FT2 : ft2_scan fichier.wav r0 r1 pas  -> pour chaque rapport de rééchantillonnage r (y[i] = x[i*r]) et chaque début de tranche, affiche les décodages valides */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "sdrdec_ft8.h"
static float cub(const float *x, int n, double p) {
    int i = (int)p; double t = p - i;
    if (i < 1 || i + 2 >= n) return 0;
    double a = x[i-1], b = x[i], c = x[i+1], d = x[i+2];
    return (float)(b + 0.5*t*(c - a + t*(2*a - 5*b + 4*c - d + t*(3*(b - c) + d - a))));
}
int main(int argc, char **argv) {
    FILE *f = fopen(argv[1], "rb"); fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    unsigned char *raw = malloc(sz); fread(raw, 1, sz, f);
    long off = 44;
    for (long i = 12; i + 8 < sz; ) { long len = raw[i+4] | (raw[i+5] << 8) | (raw[i+6] << 16) | ((long)raw[i+7] << 24); if (!memcmp(raw + i, "data", 4)) { off = i + 8; break; } i += 8 + len + (len & 1); }
    int n = (int)((sz - off) / 2); float *x = malloc(n * 4);
    for (int i = 0; i < n; i++) x[i] = (short)(raw[off + 2*i] | (raw[off + 2*i + 1] << 8)) / 32768.0f;
    double r0 = atof(argv[2]), r1 = atof(argv[3]), st = atof(argv[4]);
    int mode = argc > 5 ? atoi(argv[5]) : 2;
    float *y = malloc(n * 4); static char out[8192];
    for (double r = r0; r <= r1 + 1e-9; r += st) {
        int m = (int)((n - 3) / r);
        for (int i = 0; i < m; i++) y[i] = cub(x, n, i * r);
        int found = 0;
        for (int s = 0; s + 45000 <= m; s += 3000) {
            int k = sdrdec_ft8_decode(y + s, 45000, 12000, mode, out, sizeof(out));
            if (k) { printf("r=%.4f debut %.2f s : %d\n%s", r, s / 12000.0, k, out); found++; }
        }
        if (!found) printf("r=%.4f : rien\n", r);
    }
    return 0;
}
