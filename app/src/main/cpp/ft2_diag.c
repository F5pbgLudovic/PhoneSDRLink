/* Diagnostic FT2 : ft2_diag fichier.wav debut_s  -> meilleurs candidats de synchro (sans seuil) */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "ft8/decode.h"
#include "ft8/message.h"
#include "common/monitor.h"
int main(int argc, char **argv) {
    FILE *f = fopen(argv[1], "rb"); fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    unsigned char *raw = malloc(sz); fread(raw, 1, sz, f);
    long off = 44;
    for (long i = 12; i + 8 < sz; ) { long len = raw[i+4] | (raw[i+5] << 8) | (raw[i+6] << 16) | ((long)raw[i+7] << 24); if (!memcmp(raw + i, "data", 4)) { off = i + 8; break; } i += 8 + len + (len & 1); }
    int n = (int)((sz - off) / 2); float *x = malloc(n * 4);
    for (int i = 0; i < n; i++) x[i] = (short)(raw[off + 2*i] | (raw[off + 2*i + 1] << 8)) / 32768.0f;
    double t0 = atof(argv[2]); int s0 = (int)(t0 * 12000);
    float sp = argc > 3 ? atof(argv[3]) : 0.024f;
    monitor_config_t cfg = { .f_min = 200, .f_max = 3000, .sample_rate = 12000, .time_osr = 2, .freq_osr = 2, .protocol = FTX_PROTOCOL_FT4, .symbol_period = sp, .slot_time = 3.75f, .scan_max = 56 };
    monitor_t mon; monitor_init(&mon, &cfg);
    for (int pos = s0; pos + mon.block_size <= n && mon.wf.num_blocks < mon.wf.max_blocks; pos += mon.block_size) monitor_process(&mon, x + pos);
    ftx_candidate_t c[10]; int nc = ftx_find_candidates(&mon.wf, 10, c, 0);
    for (int i = 0; i < nc; i++) {
        float fr = (mon.min_bin + c[i].freq_offset + (float)c[i].freq_sub / 2) / mon.symbol_period;
        float tm = (c[i].time_offset + (float)c[i].time_sub / 2) * mon.symbol_period;
        ftx_message_t m; ftx_decode_status_t st;
        int ok = ftx_decode_candidate(&mon.wf, &c[i], 25, &m, &st);
        printf("score %3d  f %7.1f  t %5.2f  ldpc_errors %d crc %04x/%04x ok=%d\n", c[i].score, fr, tm, st.ldpc_errors, st.crc_extracted, st.crc_calculated, ok);
    }
    return 0;
}
