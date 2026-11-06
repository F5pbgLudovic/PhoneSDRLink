package fr.f5pbg.phonesdrlink;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.LinkedBlockingQueue;

// Réception FreeDV RADE V1 : le son du WebSDR (8 kHz, 16 bits) est décodé par le code natif ; la parole obtenue (16 kHz) est
// jouée sur une AudioTrack. Le fil de décodage est séparé du fil principal (le décodage demande du calcul).
final class RadeEngine {
    interface Listener { void onStatus(String s); void onEvent(String line); void onVoice(boolean on); }

    static final int RATE = 8000;
    final Listener ls;
    final Handler h = new Handler(Looper.getMainLooper());
    final LinkedBlockingQueue<short[]> q = new LinkedBlockingQueue<short[]>();
    volatile boolean running;
    long handle;
    AudioTrack track;
    Thread worker;
    int syncCount, lostCount;
    long lastVoiceMs;
    boolean voiceOn;
    short[] outBuf = new short[0];

    final boolean v2;   // RADE V2 (FreeDV 3.0, en développement) au lieu de V1 (FreeDV 2.x)
    RadeEngine(boolean v2, Listener l) { this.v2 = v2; ls = l; }

    boolean start() {
        handle = Decoders.radeNewV(v2 ? 1 : 0);
        if (handle == 0) return false;
        try {
            int minBuf = AudioTrack.getMinBufferSize(16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(Math.max(minBuf, 16000 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM).build();
            track.play();
        } catch (Exception e) { track = null; }
        running = true;
        worker = new Thread(new Runnable() { public void run() { loop(); } }, "rade");
        worker.setPriority(Thread.MAX_PRIORITY - 1);
        worker.start();
        return true;
    }

    void stop() {
        running = false;
        q.clear();
        q.offer(new short[0]);
        try { if (worker != null) worker.join(1500); } catch (InterruptedException e) { }
        if (track != null) { try { track.stop(); } catch (Exception e) { } try { track.release(); } catch (Exception e) { } track = null; }
        if (handle != 0) { Decoders.radeFree(handle); handle = 0; }
    }

    // Morceau de son : 16 bits petit-boutiste, 8000 Hz.
    void feed(byte[] b, int len) {
        if (!running) return;
        int n = len / 2;
        short[] s = new short[n];
        for (int i = 0; i < n; i++) s[i] = (short) ((b[2 * i] & 255) | (b[2 * i + 1] << 8));
        if (q.size() > 60) { q.clear(); }   // le décodage n'arrive pas à suivre : on jette le retard accumulé
        q.offer(s);
    }

    void loop() {
        boolean lastSync = false;
        long lastMs = 0;
        int[] st = new int[3];
        while (running) {
            short[] in;
            try { in = q.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS); } catch (InterruptedException e) { break; }
            if (!running) break;
            if (in == null) {   // plus de son depuis une demi-seconde : la parole décodée s'arrête, on rend le son du WebSDR
                if (voiceOn && System.currentTimeMillis() - lastVoiceMs >= 1500) {
                    voiceOn = false;
                    if (track != null) { try { track.pause(); track.flush(); track.play(); } catch (Exception e) { } }
                    h.post(new Runnable() { public void run() { if (running && ls != null) ls.onVoice(false); } });
                }
                continue;
            }
            if (in.length == 0) continue;
            if (outBuf.length < in.length * 2 + 8000) outBuf = new short[in.length * 2 + 8000];   // tampon réutilisé d'un morceau à l'autre
            short[] out = outBuf;
            int w = Decoders.radeProcess(handle, in, in.length, out, st);
            long nowV = System.currentTimeMillis();
            if (w > 0) lastVoiceMs = nowV;
            boolean voice = lastVoiceMs > 0 && nowV - lastVoiceMs < 1500;   // de la parole décodée vient d'arriver : le son du WebSDR est remplacé par elle
            if (voice != voiceOn) {
                voiceOn = voice;
                if (!voice && track != null) { try { track.pause(); track.flush(); track.play(); } catch (Exception e) { } }
                final boolean vv = voice;
                h.post(new Runnable() { public void run() { if (running && ls != null) ls.onVoice(vv); } });
            }
            if (w > 0 && track != null && voiceOn) track.write(out, 0, w);
            boolean sync = st[0] != 0;
            long now = System.currentTimeMillis();
            if (sync != lastSync) {
                lastSync = sync;
                if (sync) syncCount++; else lostCount++;
                final String ev = clock(now) + (sync ? "  " + Lang.F("synchronisé (SNR {0} dB)", "in sync (SNR {0} dB)", st[1]) : "  " + Lang.T("signal perdu", "signal lost"));
                h.post(new Runnable() { public void run() { if (running && ls != null) ls.onEvent(ev); } });
            }
            if (now - lastMs > 1000) {
                lastMs = now;
                final String s = sync ? String.format(Locale.US, (v2 ? "RADE V2" : "RADE") + " : %s, SNR %d dB, %s %+.1f Hz", Lang.T("synchronisé", "in sync"), st[1], Lang.T("décalage", "offset"), st[2] / 10.0)
                                      : (v2 ? "RADE V2" : "RADE") + " : " + Lang.T("recherche du signal…", "searching for the signal…");
                h.post(new Runnable() { public void run() { if (running && ls != null) ls.onStatus(s); } });
            }
        }
    }

    static String clock(long ms) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        c.setTimeInMillis(ms);
        return String.format(Locale.US, "%02d%02d%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
    }
}
