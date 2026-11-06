#include <jni.h>
#include <stdlib.h>
#include <math.h>
#include <stdint.h>
#include "sdrdec_ft8.h"

/* static native String ft8Decode(short[] pcm, int n, int rate) : lignes « snr|dt|freq|texte » */
JNIEXPORT jstring JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_ft8Decode(JNIEnv *env, jclass cls, jshortArray pcm, jint n, jint rate, jint ft4) {
    (void)cls;
    jsize len = (*env)->GetArrayLength(env, pcm);
    if (n > len) n = len;
    if (n <= 0) return (*env)->NewStringUTF(env, "");
    jshort *s = (*env)->GetShortArrayElements(env, pcm, NULL);
    float *x = (float *)malloc(sizeof(float) * (size_t)n);
    if (!x) { (*env)->ReleaseShortArrayElements(env, pcm, s, JNI_ABORT); return (*env)->NewStringUTF(env, ""); }
    for (int i = 0; i < n; i++) x[i] = s[i] / 32768.0f;
    (*env)->ReleaseShortArrayElements(env, pcm, s, JNI_ABORT);
    char *out = (char *)malloc(16384);
    jstring r;
    if (out) { sdrdec_ft8_decode(x, n, rate, ft4, out, 16384); r = (*env)->NewStringUTF(env, out); free(out); }
    else r = (*env)->NewStringUTF(env, "");
    free(x);
    return r;
}

JNIEXPORT void JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_ft8Reset(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    sdrdec_ft8_reset();
}

#ifdef SDRDEC_RADE
#include "sdrdec_rade.h"

/* static native long radeNew() ; static native void radeFree(long h) ;
 * static native int radeProcess(long h, short[] in8k, int n, short[] out16k, int[] status) : status = { sync, snr_db, foff_dixiemes_Hz } */
JNIEXPORT jlong JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeNew(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jlong)(intptr_t)sdrdec_rade_new();
}

JNIEXPORT jlong JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeNewV(JNIEnv *env, jclass cls, jint v2) {
    (void)env; (void)cls;
    return (jlong)(intptr_t)sdrdec_rade_new_v(v2);
}

JNIEXPORT void JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeFree(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls;
    sdrdec_rade_free((sdrdec_rade *)(intptr_t)h);
}

JNIEXPORT jint JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeProcess(JNIEnv *env, jclass cls, jlong h, jshortArray in, jint n, jshortArray out, jintArray status) {
    (void)cls;
    sdrdec_rade *s = (sdrdec_rade *)(intptr_t)h;
    if (!s || n <= 0) return 0;
    jsize len = (*env)->GetArrayLength(env, in);
    if (n > len) n = len;
    jsize outmax = (*env)->GetArrayLength(env, out);
    jshort *pin = (*env)->GetShortArrayElements(env, in, NULL);
    float *x = (float *)malloc(sizeof(float) * (size_t)n);
    if (!x) { (*env)->ReleaseShortArrayElements(env, in, pin, JNI_ABORT); return 0; }
    for (int i = 0; i < n; i++) x[i] = pin[i] / 32768.0f;
    (*env)->ReleaseShortArrayElements(env, in, pin, JNI_ABORT);
    short *tmp = (short *)malloc(sizeof(short) * (size_t)outmax);
    int sync = 0, snr = 0; float foff = 0;
    int w = tmp ? sdrdec_rade_process(s, x, n, tmp, outmax, &sync, &snr, &foff) : 0;
    if (w > 0) (*env)->SetShortArrayRegion(env, out, 0, w, tmp);
    if (status) { jint st[3] = { sync, snr, (jint)lroundf(foff * 10.0f) }; (*env)->SetIntArrayRegion(env, status, 0, 3, st); }
    free(tmp); free(x);
    return w;
}
#else
JNIEXPORT jlong JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeNew(JNIEnv *env, jclass cls) { (void)env; (void)cls; return 0; }
JNIEXPORT jlong JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeNewV(JNIEnv *env, jclass cls, jint v2) { (void)env; (void)cls; (void)v2; return 0; }
JNIEXPORT void JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeFree(JNIEnv *env, jclass cls, jlong h) { (void)env; (void)cls; (void)h; }
JNIEXPORT jint JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_radeProcess(JNIEnv *env, jclass cls, jlong h, jshortArray in, jint n, jshortArray out, jintArray status) {
    (void)env; (void)cls; (void)h; (void)in; (void)n; (void)out; (void)status; return 0;
}
#endif
