package fr.f5pbg.phonesdrlink;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Bouton « Ressources Processeur / RAM » de SET : mesures en temps réel de l'application (processeur global et par fil d'exécution,
// mémoire, état du téléphone). Aucune mesure ne tourne hors de cette fenêtre : la minuterie démarre à l'ouverture et s'arrête à la fermeture.
final class DlgPerf {
    private DlgPerf() { }

    static void show(final MainActivity a) {
        LinearLayout col = Ui.column(a);
        final Dialog d = Ui.window(a, Lang.T("Ressources Processeur / RAM", "CPU / RAM resources"), Ui.scroll(a, col), false);
        col.addView(Ui.small(a, Lang.T("Mesures en direct, uniquement tant que cette fenêtre est ouverte.", "Live measurements, only while this window is open.")));
        final TextView tv = new TextView(a);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextSize(12);
        tv.setTextColor(Ui.fg());
        tv.setPadding(Ui.dp(a, 4), Ui.dp(a, 8), Ui.dp(a, 4), Ui.dp(a, 8));
        tv.setText("…");
        col.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(Ui.small(a, Lang.T("Le processus de la page WebSDR (WebView) est séparé : l'application ne peut pas le mesurer.", "The WebSDR page process (WebView) is separate: the application cannot measure it.")));
        final Handler h = new Handler(Looper.getMainLooper());
        final Sampler s = new Sampler();
        final Runnable tick = new Runnable() {
            int n;
            public void run() {
                tv.setText(s.report(a, n++ % 2 == 0));
                h.postDelayed(this, 1000);
            }
        };
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface x) { h.removeCallbacks(tick); }
        });
        d.show();
        h.post(tick);
    }

    // Échantillonneur : garde les mesures précédentes pour calculer des pourcentages entre deux relevés.
    static final class Sampler {
        final long tck = tick();
        final int cores = Runtime.getRuntime().availableProcessors();
        long lastWall, lastProc;
        final Map<String, Long> lastThread = new HashMap<String, Long>();
        String memText = "";
        float peakCpu;

        static long tick() { try { return Os.sysconf(OsConstants._SC_CLK_TCK); } catch (Throwable e) { return 100; } }

        // Temps processeur (ticks) d'après un fichier /proc/.../stat ; le nom du fil est entre parenthèses.
        static long[] stat(File f, String[] name) {
            try {
                FileInputStream in = new FileInputStream(f);
                byte[] b = new byte[1024];
                int n = in.read(b);
                in.close();
                String t = new String(b, 0, Math.max(0, n));
                int p = t.lastIndexOf(')');
                if (name != null) name[0] = t.substring(t.indexOf('(') + 1, p);
                String[] f2 = t.substring(p + 2).split(" ");
                return new long[] { Long.parseLong(f2[11]) + Long.parseLong(f2[12]) };   // utime + stime
            } catch (Throwable e) { return null; }
        }

        String report(Context c, boolean withMem) {
            StringBuilder s = new StringBuilder();
            long wall = SystemClock.elapsedRealtime();
            long[] pr = stat(new File("/proc/self/stat"), null);
            double dt = Math.max(1, wall - lastWall) / 1000.0;
            // ---- processeur
            s.append("── CPU ──\n");
            if (pr != null) {
                double cpu = lastWall == 0 ? 0 : (pr[0] - lastProc) / (double) tck / dt * 100.0;
                if (lastWall != 0) peakCpu = Math.max(peakCpu, (float) cpu);
                s.append(String.format(Locale.US, "App      %5.1f %%  (100 %% = 1 core, %d cores)\n", cpu, cores));
                s.append(String.format(Locale.US, "Peak     %5.1f %%\n", peakCpu));
                s.append(String.format(Locale.US, "Total    %.1f s\n", pr[0] / (double) tck));
                lastProc = pr[0];
            }
            File[] tasks = new File("/proc/self/task").listFiles();
            if (tasks != null) {
                s.append("Threads  ").append(tasks.length).append("\n");
                final Map<String, Double> pct = new HashMap<String, Double>();
                for (File t : tasks) {
                    String[] nm = new String[1];
                    long[] v = stat(new File(t, "stat"), nm);
                    if (v == null) continue;
                    String key = t.getName() + " " + nm[0];
                    Long old = lastThread.get(key);
                    lastThread.put(key, v[0]);
                    if (old != null && lastWall != 0) pct.put(key, (v[0] - old) / (double) tck / dt * 100.0);
                }
                ArrayList<String> keys = new ArrayList<String>(pct.keySet());
                Collections.sort(keys, new Comparator<String>() { public int compare(String x, String y) { return Double.compare(pct.get(y), pct.get(x)); } });
                for (int i = 0; i < Math.min(6, keys.size()); i++) {
                    if (pct.get(keys.get(i)) < 0.05) break;
                    String k = keys.get(i);
                    s.append(String.format(Locale.US, "  %5.1f %%  %s\n", pct.get(k), k.substring(k.indexOf(' ') + 1)));
                }
            }
            String[] la = readLine("/proc/loadavg");
            if (la != null && la.length >= 3) s.append("Load     ").append(la[0]).append(" ").append(la[1]).append(" ").append(la[2]).append("\n");
            lastWall = wall;
            // ---- mémoire (relevé plus coûteux : une fois sur deux)
            if (withMem || memText.isEmpty()) memText = memory(c);
            s.append(memText);
            // ---- téléphone
            s.append("── System ──\n");
            s.append(system(c));
            MainActivity m = c instanceof MainActivity ? (MainActivity) c : null;
            if (m != null) {
                s.append("WebSDR   ").append(m.sdrKind.isEmpty() ? "-" : m.sdrKind + " " + m.freq + " Hz " + m.mode).append("\n");
                s.append("Eco      ").append(m.st.eco ? "ON" : "OFF").append("   Decoder ").append(m.decMode != 0 ? "ON" : "OFF").append("\n");
            }
            File[] fds = new File("/proc/self/fd").listFiles();
            if (fds != null) s.append("Files    ").append(fds.length).append(" open\n");
            return s.toString();
        }

        // Batterie, chaleur et réseau (mémoire et tas sont déjà donnés plus haut).
        static String system(Context c) {
            StringBuilder s = new StringBuilder();
            try {
                android.content.Intent b = c.registerReceiver(null, new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
                if (b != null) {
                    int lv = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1), sc = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
                    s.append(String.format(Locale.US, "Battery  %d %%  %.1f °C%s\n", lv * 100 / Math.max(1, sc), b.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0, b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0 ? "  (plugged)" : ""));
                }
            } catch (Throwable e) { }
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.os.PowerManager pm = (android.os.PowerManager) c.getSystemService(Context.POWER_SERVICE);
                    s.append("Thermal  ").append(pm.getCurrentThermalStatus()).append("  (0 = normal, 3 = severe)\n");
                }
            } catch (Throwable e) { }
            try {
                android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
                android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                s.append("Network  ").append(nc == null ? "none" : nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ? "mobile" : "other").append("\n");
            } catch (Throwable e) { }
            return s.toString();
        }

        static String[] readLine(String path) {
            try {
                FileInputStream in = new FileInputStream(path);
                byte[] b = new byte[256];
                int n = in.read(b);
                in.close();
                return new String(b, 0, Math.max(0, n)).trim().split(" ");
            } catch (Throwable e) { return null; }
        }

        static String memory(Context c) {
            StringBuilder s = new StringBuilder("── RAM ──\n");
            try {
                Debug.MemoryInfo mi = new Debug.MemoryInfo();
                Debug.getMemoryInfo(mi);
                s.append(String.format(Locale.US, "App PSS  %d MB\n", mi.getTotalPss() / 1024));
                s.append(String.format(Locale.US, "  Java %d  Native %d  Other %d MB\n", mi.dalvikPss / 1024, mi.nativePss / 1024, mi.otherPss / 1024));
                String g = mi.getMemoryStat("summary.graphics"), cd = mi.getMemoryStat("summary.code"), st = mi.getMemoryStat("summary.stack");
                if (g != null) s.append(String.format(Locale.US, "  Graphics %d  Code %d  Stack %d MB\n", Long.parseLong(g) / 1024, cd == null ? 0 : Long.parseLong(cd) / 1024, st == null ? 0 : Long.parseLong(st) / 1024));
            } catch (Throwable e) { }
            Runtime r = Runtime.getRuntime();
            s.append(String.format(Locale.US, "Java heap %d / %d MB\n", (r.totalMemory() - r.freeMemory()) >> 20, r.maxMemory() >> 20));
            s.append(String.format(Locale.US, "Native heap %d MB\n", Debug.getNativeHeapAllocatedSize() >> 20));
            try {
                ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(m);
                s.append(String.format(Locale.US, "Phone RAM %d MB free / %d MB%s\n", m.availMem >> 20, m.totalMem >> 20, m.lowMemory ? "  (LOW)" : ""));
                s.append(String.format(Locale.US, "Low limit %d MB   App limit %d MB\n", m.threshold >> 20, am.getMemoryClass()));
            } catch (Throwable e) { }
            return s.toString();
        }
    }
}
