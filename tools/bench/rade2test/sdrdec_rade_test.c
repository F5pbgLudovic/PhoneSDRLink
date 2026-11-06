/* Essai de bouclage RADE V2 (temporaire, hors de l'application finale) : émetteur V2 -> bruit -> récepteur de PhoneSDRLink. */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <stdio.h>
#include "../sdrdec_rade.h"
#include "rade_tx_v2.h"

static float gauss(void) {
    float u1 = (rand() + 1.0f) / (RAND_MAX + 2.0f), u2 = (rand() + 1.0f) / (RAND_MAX + 2.0f);
    return sqrtf(-2.0f * logf(u1)) * cosf(6.2831853f * u2);
}

JNIEXPORT jstring JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeLoopTest(JNIEnv *env, jclass cls, jint snr10, jint seconds) {
    (void)cls;
    static char msg[512];
    rade_tx_v2_state *tx = (rade_tx_v2_state *)calloc(1, sizeof(*tx));
    if (!tx || rade_tx_v2_init(tx, 1) != 0) return (*env)->NewStringUTF(env, "init TX V2 impossible");
    sdrdec_rade *rx = sdrdec_rade_new_v(1);
    if (!rx) return (*env)->NewStringUTF(env, "init RX V2 impossible");
    int nfeat = rade_tx_v2_n_features_in();
    int nmf = RADE_V2_NMF;
    float *feat = (float *)calloc((size_t)nfeat, sizeof(float));
    RADE_COMP *txo = (RADE_COMP *)calloc((size_t)nmf, sizeof(RADE_COMP));
    short *outpcm = (short *)calloc(16000 * 2, sizeof(short));
    float snr = snr10 / 10.0f;
    long frames = (long)seconds * 8000 / nmf, synced = 0, speech = 0, total = 0;
    double snrsum = 0;
    float *blk = (float *)calloc((size_t)nmf, sizeof(float));
    double psig = 0;
    srand(7);
    for (long f = 0; f < frames; f++) {
        /* traits du vocodeur : au hasard mais bornés (ni parole ni silence : seule la couche modem est essayée) */
        for (int i = 0; i < nfeat; i++) feat[i] = ((i % 36) < 20) ? 0.3f * gauss() : 0.0f;
        int n = rade_tx_v2_process(tx, txo, feat);
        for (int i = 0; i < n; i++) { blk[i] = txo[i].real; psig += (double)blk[i] * blk[i]; }
        if (f == 20) psig = psig / ((double)n * 21);   /* puissance moyenne du signal émis */
        if (f == 20) { /* fixe le bruit d'après la puissance mesurée */ }
        static double sigma = 0;
        if (f == 21 && psig > 0) sigma = sqrt(psig / pow(10.0, snr / 10.0) * 2500.0 / 4000.0 * 1.0);
        for (int i = 0; i < n; i++) blk[i] = 0.25f * (blk[i] + (float)sigma * gauss());
        int sync = 0, snrd = 0; float foff = 0;
        int w = sdrdec_rade_process(rx, blk, n, outpcm, 16000 * 2, &sync, &snrd, &foff);
        total++; if (sync) { synced++; snrsum += snrd; } speech += w;
    }
    snprintf(msg, sizeof msg, "V2 boucle SNR %.1f dB : %ld/%ld blocs synchronisés, SNR estimé moyen %.1f dB, %ld échantillons de parole", snr, synced, total, synced ? snrsum / synced : 0.0, speech);
    free(blk); free(outpcm); free(txo); free(feat); free(tx); sdrdec_rade_free(rx);
    return (*env)->NewStringUTF(env, msg);
}
