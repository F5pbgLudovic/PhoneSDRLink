package fr.f5pbg.phonesdrlink;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Journal de diagnostic, très léger (quelques lignes par minute) : sert à comprendre après coup un blocage ou une fermeture.
// Fichier diag.log dans le dossier public de l'application (Android/data/fr.f5pbg.phonesdrlink/files), 96 Ko au plus.
// Contenu : raison de la fin du processus précédent (Android la garde), plantages Java, manque de mémoire, page WebSDR perdue,
// et un point par minute (mémoire libre, température, réseau, retard éventuel du fil principal).
final class DiagLog {
    static final int MAX = 96 * 1024;
    static File file;
    static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private DiagLog() { }

    static void init(Context c) {
        try {
            File d = c.getExternalFilesDir(null);
            if (d == null) d = c.getFilesDir();
            file = new File(d, "diag.log");
        } catch (Throwable e) { return; }
        try {
            final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(Thread t, Throwable e) {
                    add("PLANTAGE fil " + t.getName() + " : " + android.util.Log.getStackTraceString(e));
                    if (old != null) old.uncaughtException(t, e);
                }
            });
        } catch (Throwable e) { }
        exitReasons(c);
    }

    // Pourquoi le processus précédent s'est arrêté (Android 11 et plus) : une seule fois par fin.
    static void exitReasons(Context c) {
        if (Build.VERSION.SDK_INT < 30) return;
        try {
            SharedPreferences sp = c.getSharedPreferences("diag", Context.MODE_PRIVATE);
            long seen = sp.getLong("exitTs", 0), top = seen;
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> l = am.getHistoricalProcessExitReasons(null, 0, 6);
            for (int i = l.size() - 1; i >= 0; i--) {
                ApplicationExitInfo e = l.get(i);
                if (e.getTimestamp() <= seen) continue;
                top = Math.max(top, e.getTimestamp());
                add("FIN PRÉCÉDENTE " + DF.format(new Date(e.getTimestamp())) + " " + e.getProcessName() + " : " + reason(e.getReason())
                        + " (code " + e.getReason() + ", statut " + e.getStatus() + ") importance=" + e.getImportance() + " pss=" + (e.getPss() >> 10) + "Mo rss=" + (e.getRss() >> 10) + "Mo"
                        + (e.getDescription() != null && e.getDescription().length() > 0 ? " " + e.getDescription() : ""));
            }
            if (top > seen) sp.edit().putLong("exitTs", top).apply();
        } catch (Throwable e) { }
    }

    static String reason(int r) {
        switch (r) {
            case 1: return "arrêt volontaire";
            case 2: return "tuée par le système";
            case 3: return "signal";
            case 4: return "ERREUR JAVA";
            case 5: return "ERREUR NATIVE";
            case 6: return "ANR (application ne répond plus)";
            case 7: return "initialisation";
            case 8: return "MANQUE DE MÉMOIRE";
            case 9: return "permission retirée";
            case 10: return "dépendance";
            case 11: return "utilisateur (arrêt forcé)";
            case 12: return "utilisateur (données effacées)";
            case 13: return "autre arrêt par le système";
            case 14: return "mise en cache";
            default: return "inconnue";
        }
    }

    // Une ligne d'état : mémoire du téléphone, chaleur, batterie, réseau.
    static String state(Context c) {
        StringBuilder s = new StringBuilder();
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            s.append("mem libre ").append(mi.availMem >> 20).append(" Mo").append(mi.lowMemory ? " (MÉMOIRE BASSE)" : "");
        } catch (Throwable e) { }
        try {
            Runtime r = Runtime.getRuntime();
            s.append(" ; tas ").append((r.totalMemory() - r.freeMemory()) >> 20).append("/").append(r.maxMemory() >> 20).append(" Mo");
        } catch (Throwable e) { }
        try {
            Intent b = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                s.append(" ; batterie ").append(lv * 100 / Math.max(1, sc)).append(" % ").append(b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0).append(" °C");
            }
        } catch (Throwable e) { }
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                int t = pm.getCurrentThermalStatus();
                if (t > 0) s.append(" ; CHALEUR niveau ").append(t);
            }
        } catch (Throwable e) { }
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            s.append(" ; réseau ").append(nc == null ? "aucun" : nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "mobile" : "autre");
        } catch (Throwable e) { }
        return s.toString();
    }

    static synchronized void add(String line) {
        if (file == null) return;
        try {
            if (file.length() > MAX) {   // on garde la seconde moitié
                byte[] all = new byte[(int) file.length()];
                java.io.FileInputStream in = new java.io.FileInputStream(file);
                int n = 0, k;
                while (n < all.length && (k = in.read(all, n, all.length - n)) > 0) n += k;
                in.close();
                int from = n / 2;
                while (from < n && all[from] != '\n') from++;
                FileOutputStream o = new FileOutputStream(file, false);
                o.write(all, Math.min(n, from + 1), Math.max(0, n - from - 1));
                o.close();
            }
            FileOutputStream o = new FileOutputStream(file, true);
            o.write((DF.format(new Date()) + " " + line + "\n").getBytes("UTF-8"));
            o.close();
        } catch (Throwable e) { }
    }
}
