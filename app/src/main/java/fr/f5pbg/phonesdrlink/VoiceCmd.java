package fr.f5pbg.phonesdrlink;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Commande vocale du scan : reconnaissance hors ligne (Vosk) de quelques mots seulement — « stop », « start », « plus un », « moins un » —
// avec une grammaire fermée. Le micro n'est ouvert que pendant une session de scan (démarrée et arrêtée par MainActivity) ; le modèle
// de langue (français, 40 Mo) est téléchargé une seule fois, à la demande, dans le dossier privé de l'application, puis libéré de la mémoire
// à la fin de chaque session.
final class VoiceCmd {
    // Langue de l'application → modèle Vosk (petit modèle de cette langue) et ordres dans cette langue. Les langues sans modèle utilisent l'anglais.
    // { code, nom du modèle, mot « plus », mot « moins », phrase « plus », phrase « moins » } ; « stop » et « start » sont les mêmes partout.
    static final String[][] LANGS = {
        { "fr", "vosk-model-small-fr-0.22", "plus", "moins", "plus un", "moins un" },
        { "en", "vosk-model-small-en-us-0.15", "plus", "minus", "plus one", "minus one" },
        { "de", "vosk-model-small-de-0.15", "plus", "minus", "plus eins", "minus eins" },
        { "es", "vosk-model-small-es-0.42", "más", "menos", "más uno", "menos uno" },
        { "it", "vosk-model-small-it-0.22", "più", "meno", "più uno", "meno uno" },
        { "nl", "vosk-model-small-nl-0.22", "plus", "min", "plus een", "min een" },
        { "pt", "vosk-model-small-pt-0.3", "mais", "menos", "mais um", "menos um" },
        { "pl", "vosk-model-small-pl-0.22", "plus", "minus", "plus jeden", "minus jeden" },
        { "cs", "vosk-model-small-cs-0.4-rhasspy", "plus", "minus", "plus jedna", "minus jedna" } };

    static String[] spec(String lang) {
        for (String[] l : LANGS) if (l[0].equals(lang)) return l;
        return LANGS[1];   // anglais
    }

    static String grammar(String[] s) { return "[\"stop\", \"start\", \"" + s[4] + "\", \"" + s[5] + "\", \"[unk]\"]"; }


    interface Cmd { void on(String cmd); }       // « stop », « start », « plus », « moins »
    interface Progress { void on(int percent, String error); }   // error != null : échec ; percent 100 : terminé

    private Model model;
    private SpeechService svc;
    private Recognizer rec;
    private int gen;
    private boolean loading;
    private String[] sp = LANGS[1];
    private long lastCmdMs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    static File dir(Context c, String lang) { return new File(c.getFilesDir(), "vosk-" + spec(lang)[0]); }

    static boolean ready(Context c, String lang) { return new File(dir(c, lang), "am/final.mdl").exists(); }

    // Supprime les modèles de toutes les langues (dossiers « vosk-xx »).
    static void deleteAll(Context c) {
        File[] l = c.getFilesDir().listFiles();
        if (l != null) for (File f : l) if (f.getName().startsWith("vosk-")) deleteTree(f);
    }

    private static void deleteTree(File f) {
        File[] l = f.listFiles();
        if (l != null) for (File x : l) deleteTree(x);
        f.delete();
    }

    // Télécharge le modèle puis le décompresse (sans le dossier racine du zip) ; p reçoit l'avancement sur le fil de l'interface.
    static void download(final Context c, final String lang, final Progress p) {
        final Handler ui = new Handler(Looper.getMainLooper());
        new Thread(new Runnable() {
            public void run() {
                File tmp = new File(c.getFilesDir(), "voskdl.tmp");
                String err = null;
                try {
                    deleteTree(tmp);
                    HttpURLConnection h = (HttpURLConnection) new URL("https://alphacephei.com/vosk/models/" + spec(lang)[1] + ".zip").openConnection();
                    h.setConnectTimeout(15000);
                    h.setReadTimeout(30000);
                    h.setInstanceFollowRedirects(true);
                    final long total = h.getContentLength();
                    InputStream in = h.getInputStream();
                    // Compte des octets lus du zip : l'avancement suit le flux d'entrée, pas les fichiers écrits.
                    final long[] got = { 0 };
                    InputStream counted = new java.io.FilterInputStream(in) {
                        public int read(byte[] b, int o, int l) throws java.io.IOException { int n = super.read(b, o, l); if (n > 0) got[0] += n; return n; }
                    };
                    ZipInputStream z = new ZipInputStream(counted);
                    ZipEntry e;
                    byte[] buf = new byte[65536];
                    int last = -1;
                    String root = tmp.getCanonicalPath() + File.separator;
                    while ((e = z.getNextEntry()) != null) {
                        String name = e.getName();
                        int s = name.indexOf('/');
                        if (s < 0) continue;
                        name = name.substring(s + 1);
                        if (name.isEmpty()) continue;
                        File out = new File(tmp, name);
                        if (!out.getCanonicalPath().startsWith(root)) throw new Exception("zip");   // chemin hors du dossier : refusé
                        if (e.isDirectory()) { out.mkdirs(); continue; }
                        out.getParentFile().mkdirs();
                        FileOutputStream o = new FileOutputStream(out);
                        int n;
                        while ((n = z.read(buf)) > 0) {
                            o.write(buf, 0, n);
                            final int pc = total > 0 ? (int) Math.min(99, got[0] * 100 / total) : 0;
                            if (pc != last) { last = pc; ui.post(new Runnable() { public void run() { p.on(pc, null); } }); }
                        }
                        o.close();
                    }
                    z.close();
                    if (!new File(tmp, "am/final.mdl").exists()) throw new Exception("modèle incomplet");
                    deleteTree(dir(c, lang));
                    if (!tmp.renameTo(dir(c, lang))) throw new Exception("renommage");
                } catch (Exception e) { err = String.valueOf(e.getMessage()); }
                final String fe = err;
                if (fe != null) deleteTree(tmp);
                ui.post(new Runnable() { public void run() { p.on(fe == null ? 100 : 0, fe); } });
            }
        }).start();
    }

    // Ouvre le micro et commence à écouter (le chargement du modèle se fait hors du fil de l'interface).
    void start(final Context c, final String lang, final Cmd cb) {
        sp = spec(lang);
        if (svc != null || model != null || loading) return;
        loading = true;
        final int g = ++gen;
        new Thread(new Runnable() {
            public void run() {
                try {
                    LibVosk.setLogLevel(LogLevel.WARNINGS);
                    final Model m = new Model(dir(c, lang).getAbsolutePath());
                    final Recognizer r = new Recognizer(m, 16000.0f, grammar(sp));
                    ui.post(new Runnable() {
                        public void run() {
                            loading = false;
                            if (g != gen) { r.close(); m.close(); return; }   // arrêté entre-temps
                            try {
                                model = m; rec = r;
                                svc = new SpeechService(r, 16000.0f);
                                svc.startListening(new RecognitionListener() {
                                    public void onPartialResult(String s) { }
                                    public void onResult(String s) { handle(s, cb); }
                                    public void onFinalResult(String s) { handle(s, cb); }
                                    public void onError(Exception e) { DiagLog.add("Commande vocale : erreur " + e); }
                                    public void onTimeout() { }
                                });
                                DiagLog.add("Commande vocale : micro ouvert");
                            } catch (Exception e) { DiagLog.add("Commande vocale : " + e); stop(); }
                        }
                    });
                } catch (Throwable e) { DiagLog.add("Commande vocale : modèle non chargé : " + e); ui.post(new Runnable() { public void run() { loading = false; } }); }
            }
        }).start();
    }

    private void handle(String json, Cmd cb) {
        try {
            String t = new JSONObject(json).optString("text", "").trim();
            if (t.isEmpty()) return;
            String cmd = t.contains(sp[3]) ? "moins" : t.contains(sp[2]) ? "plus" : t.contains("stop") ? "stop" : t.contains("start") ? "start" : null;
            long now = System.currentTimeMillis();
            if (cmd == null || now - lastCmdMs < 1200) return;   // [unk] ou répétition immédiate
            lastCmdMs = now;
            DiagLog.add("Commande vocale entendue : « " + t + " »");
            cb.on(cmd);
        } catch (Exception e) { }
    }

    // Ferme le micro et libère le modèle (la mémoire revient à l'application).
    void stop() {
        gen++;
        try { if (svc != null) { svc.stop(); svc.shutdown(); } } catch (Throwable e) { }
        try { if (rec != null) rec.close(); } catch (Throwable e) { }
        try { if (model != null) model.close(); } catch (Throwable e) { }
        svc = null; rec = null; model = null;
    }

    boolean active() { return svc != null || model != null || loading; }
}
