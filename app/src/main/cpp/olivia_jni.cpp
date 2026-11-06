// Décodeur Olivia / Contestia pour PhoneSDRLink : enveloppe JNI autour de la bibliothèque MFSK de Pawel Jalocha (jalocha/*.h,
// reprise de fldigi, GNU GPL). Réception seule dans l'application ; l'émetteur sert à l'essai de bouclage (oliviaLoopTest).
#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string>
#include <vector>
#include "jalocha/pj_mfsk.h"

namespace {
struct Oli {
    MFSK_Receiver<double> rx;
    int tones, bw, margin, integ, contestia, reverse;
    double thr;
    std::vector<double> buf;
};

int firstCarrierOffset(int tones, int bw, int contestia) {
    return contestia ? bw / 2 : (int)(bw * (1.0 - 0.5 / tones) / 2.0);
}

int oliPreset(Oli *o, int center) {
    MFSK_Receiver<double> &r = o->rx;
    r.bContestia = o->contestia != 0;
    r.Tones = (size_t)o->tones;
    r.Bandwidth = (size_t)o->bw;
    r.SyncMargin = (size_t)o->margin;
    r.SyncIntegLen = (size_t)o->integ;
    r.SyncThreshold = o->thr;
    r.SampleRate = 8000;
    r.InputSampleRate = 8000;
    int off = firstCarrierOffset(o->tones, o->bw, o->contestia);
    if (o->reverse) { r.FirstCarrierMultiplier = (float)((center + off) / 500.0); r.Reverse = 1; }
    else { r.FirstCarrierMultiplier = (float)((center - off) / 500.0); r.Reverse = 0; }
    return r.Preset();
}
}

extern "C" {

JNIEXPORT jlong JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_oliviaNew(JNIEnv *env, jclass cls, jint tones, jint bw, jint margin, jint integ, jint contestia, jint center, jint reverse, jint thr10) {
    (void)env; (void)cls;
    Oli *o = new Oli();
    o->tones = tones; o->bw = bw; o->margin = margin; o->integ = integ; o->contestia = contestia; o->reverse = reverse;
    o->thr = thr10 / 10.0;
    if (oliPreset(o, center) < 0) { delete o; return 0; }
    return (jlong)(intptr_t)o;
}

JNIEXPORT void JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_oliviaFree(JNIEnv *env, jclass cls, jlong h) {
    (void)env; (void)cls;
    delete (Oli *)(intptr_t)h;
}

// Nouvelle fréquence centrale (le décodeur repart de zéro). Renvoie 0 si bon.
JNIEXPORT jint JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_oliviaCenter(JNIEnv *env, jclass cls, jlong h, jint center) {
    (void)env; (void)cls;
    Oli *o = (Oli *)(intptr_t)h;
    return o ? oliPreset(o, center) : -1;
}

// Son 8000 Hz (16 bits) -> caractères décodés dans out ; st = { rapport signal/bruit de la synchro, décalage de fréquence en Hz }.
JNIEXPORT jint JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_oliviaProcess(JNIEnv *env, jclass cls, jlong h, jshortArray in, jint n, jbyteArray out, jdoubleArray st) {
    (void)cls;
    Oli *o = (Oli *)(intptr_t)h;
    if (!o) return 0;
    jshort *p = env->GetShortArrayElements(in, 0);
    if ((int)o->buf.size() < n) o->buf.resize(n);
    for (int i = 0; i < n; i++) o->buf[i] = p[i] / 32768.0;
    env->ReleaseShortArrayElements(in, p, JNI_ABORT);
    o->rx.Process(&o->buf[0], (size_t)n);
    jint cap = env->GetArrayLength(out), k = 0;
    uint8_t c;
    jbyte *q = env->GetByteArrayElements(out, 0);
    while (o->rx.GetChar(c) > 0) { if (k < cap) q[k++] = (jbyte)c; }
    env->ReleaseByteArrayElements(out, q, 0);
    jdouble s[2] = { (double)o->rx.SignalToNoiseRatio(), (double)o->rx.FrequencyOffset() };
    env->SetDoubleArrayRegion(st, 0, 2, s);
    return k;
}

// Fichier WAV d'essai (8000 Hz, 16 bits, mono) : texte Olivia / Contestia centré sur centre Hz, bruit à snr10 (dixièmes de dB dans 2,5 kHz), 8 s de silence bruité avant et 12 s après.
JNIEXPORT jint JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_oliviaMakeWav(JNIEnv *env, jclass cls, jint tones, jint bw, jint contestia, jstring text, jint snr10, jint center, jstring path) {
    (void)cls;
    const char *tx = env->GetStringUTFChars(text, 0);
    std::string msg(tx);
    env->ReleaseStringUTFChars(text, tx);
    const char *pp = env->GetStringUTFChars(path, 0);
    std::string fn(pp);
    env->ReleaseStringUTFChars(path, pp);
    MFSK_Transmitter<double> t;
    t.Default();
    t.bContestia = contestia != 0;
    t.Tones = (size_t)tones; t.Bandwidth = (size_t)bw; t.SampleRate = 8000; t.OutputSampleRate = 8000;
    int off = firstCarrierOffset(tones, bw, contestia);
    t.FirstCarrierMultiplier = (float)((center - off) / 500.0); t.Reverse = 0;
    if (t.Preset() < 0) return -1;
    std::vector<double> sig(8000 * 8, 0.0), ob((size_t)t.MaxOutputLen + 16);
    t.Start();
    t.PutChar(0);
    for (size_t i = 0; i < msg.size(); i++) t.PutChar((uint8_t)msg[i]);
    t.Stop();
    int guard = 0;
    while (t.Running() && guard++ < 400000) { int len = t.Output(&ob[0]); for (int i = 0; i < len; i++) sig.push_back(ob[i]); }
    size_t sigEnd = sig.size();
    for (int i = 0; i < 8000 * 12; i++) sig.push_back(0);
    double ps = 0;
    for (size_t i = 8000 * 8; i < sigEnd; i++) ps += sig[i] * sig[i];
    ps /= (double)(sigEnd - 8000 * 8);
    double sigma = sqrt(ps * 4000.0 / 2500.0 / pow(10.0, snr10 / 100.0));
    unsigned long long s = 88172645463325252ULL;
    FILE *f = fopen(fn.c_str(), "wb");
    if (!f) return -2;
    uint32_t n = (uint32_t)sig.size(), dataLen = n * 2, riff = 36 + dataLen, rate = 8000, brate = 16000;
    uint16_t one = 1, two = 2, sixteen = 16; uint32_t sixteen32 = 16;
    fwrite("RIFF", 1, 4, f); fwrite(&riff, 4, 1, f); fwrite("WAVEfmt ", 1, 8, f); fwrite(&sixteen32, 4, 1, f); fwrite(&one, 2, 1, f); fwrite(&one, 2, 1, f);
    fwrite(&rate, 4, 1, f); fwrite(&brate, 4, 1, f); fwrite(&two, 2, 1, f); fwrite(&sixteen, 2, 1, f); fwrite("data", 1, 4, f); fwrite(&dataLen, 4, 1, f);
    for (size_t i = 0; i < sig.size(); i++) {
        double g = 0;
        for (int k = 0; k < 12; k++) { s ^= s << 13; s ^= s >> 7; s ^= s << 17; g += (double)(s >> 11) / 9007199254740992.0; }
        double v = (sig[i] + sigma * (g - 6.0)) * 6000.0;
        if (v > 32000) v = 32000; if (v < -32000) v = -32000;
        int16_t o = (int16_t)v;
        fwrite(&o, 2, 1, f);
    }
    fclose(f);
    return (jint)n;
}

// Essai de bouclage : émet text (Olivia ou Contestia), ajoute du bruit (rapport signal/bruit en dixièmes de dB dans 2,5 kHz), décode avec une fréquence
// centrale de réception décalée de dfreq Hz. Renvoie « texte décodé|rapport|décalage ».
JNIEXPORT jstring JNICALL Java_fr_f5pbg_phonesdrlink_Decoders_oliviaLoopTest(JNIEnv *env, jclass cls, jint tones, jint bw, jint contestia, jstring text, jint snr10, jint dfreq) {
    (void)cls;
    const char *tx = env->GetStringUTFChars(text, 0);
    std::string msg(tx);
    env->ReleaseStringUTFChars(text, tx);
    MFSK_Transmitter<double> t;
    t.Default();
    t.bContestia = contestia != 0;
    t.Tones = (size_t)tones; t.Bandwidth = (size_t)bw; t.SampleRate = 8000; t.OutputSampleRate = 8000;
    int off = firstCarrierOffset(tones, bw, contestia);
    t.FirstCarrierMultiplier = (float)((1500 - off) / 500.0); t.Reverse = 0;
    if (t.Preset() < 0) return env->NewStringUTF("erreur Preset émetteur");
    std::vector<double> sig;
    std::vector<double> ob((size_t)t.MaxOutputLen + 16);
    t.Start();
    t.PutChar(0);
    for (size_t i = 0; i < msg.size(); i++) t.PutChar((uint8_t)msg[i]);
    t.Stop();
    int guard = 0;
    while (t.Running() && guard++ < 200000) { int len = t.Output(&ob[0]); for (int i = 0; i < len; i++) sig.push_back(ob[i]); }
    for (int i = 0; i < 8000 * 6; i++) sig.push_back(0);
    double ps = 0;
    for (size_t i = 0; i < sig.size(); i++) ps += sig[i] * sig[i];
    ps /= sig.size();
    // puissance moyenne du signal pendant l'émission : on compte les 6 s de silence en moins
    ps = ps * sig.size() / (sig.size() - 8000.0 * 6);
    double snr = pow(10.0, snr10 / 100.0);
    double sigma = sqrt(ps * 4000.0 / 2500.0 / snr);
    unsigned long long s = 88172645463325252ULL;
    Oli *o = new Oli();
    o->tones = tones; o->bw = bw; o->margin = 8; o->integ = 4; o->contestia = contestia; o->reverse = 0; o->thr = 3.0;
    if (oliPreset(o, 1500 + dfreq) < 0) { delete o; return env->NewStringUTF("erreur Preset récepteur"); }
    std::string outStr;
    double lastSnr = 0, lastOff = 0, maxSnr = 0;
    std::vector<double> blk;
    for (size_t i = 0; i < sig.size(); i += 1024) {
        size_t n = sig.size() - i < 1024 ? sig.size() - i : 1024;
        blk.resize(n);
        for (size_t j = 0; j < n; j++) {
            double g = 0;
            for (int k = 0; k < 12; k++) { s ^= s << 13; s ^= s >> 7; s ^= s << 17; g += (double)(s >> 11) / 9007199254740992.0; }
            blk[j] = sig[i + j] + sigma * (g - 6.0);
        }
        o->rx.Process(&blk[0], n);
        uint8_t c;
        while (o->rx.GetChar(c) > 0) if (c > 7) outStr.push_back((char)c);
        lastSnr = o->rx.SignalToNoiseRatio(); lastOff = o->rx.FrequencyOffset();
        if (lastSnr > maxSnr) maxSnr = lastSnr;
    }
    delete o;
    char tail[96];
    snprintf(tail, sizeof tail, "|%.1f|%.1f", maxSnr, lastOff);
    return env->NewStringUTF((outStr + tail).c_str());
}

}
