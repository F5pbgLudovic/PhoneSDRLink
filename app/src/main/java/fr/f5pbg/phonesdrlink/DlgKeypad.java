package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// Clavier numérique pour taper une fréquence en kHz (de 1 800 kHz à 440 000 kHz, soit 1,8 à 440 MHz), comme dans SDRLink.
final class DlgKeypad {
    static void show(final MainActivity a) {
        final StringBuilder txt = new StringBuilder(Memory.kHz(a.freq));
        final boolean[] fresh = { true };
        LinearLayout col = Ui.column(a);
        final TextView shown = new TextView(a);
        shown.setTextSize(34);
        shown.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        shown.setTextColor(Ui.accent());
        shown.setGravity(Gravity.END);
        final TextView preview = Ui.small(a, "");
        preview.setGravity(Gravity.END);
        col.addView(shown);
        col.addView(preview);
        final Dialog d = Ui.window(a, Lang.T("Fréquence (kHz) :", "Frequency (kHz):"), Ui.scroll(a, col), false);
        final Runnable refresh = new Runnable() {
            public void run() {
                shown.setText(txt.length() == 0 ? "0" : txt.toString());
                long hz = parse(txt.toString());
                boolean ok = hz >= Store.MIN_HZ && hz <= Store.MAX_HZ;
                preview.setText(hz > 0 ? "= " + MainActivity.formatFreq(hz) + (ok ? "" : "   ✗ 1.800.000 – 440.000.000") : "1.800.000 – 440.000.000");
                preview.setTextColor(hz > 0 && !ok ? Ui.danger() : Ui.sub());
            }
        };
        refresh.run();
        String[][] keys = { { "7", "8", "9" }, { "4", "5", "6" }, { "1", "2", "3" }, { ".", "0", "⌫" } };
        for (String[] rowKeys : keys) {
            LinearLayout r = Ui.row(a);
            for (final String k : rowKeys) {
                Button b = Ui.button(a, k, new View.OnClickListener() {
                    public void onClick(View v) {
                        if (k.equals("⌫")) { if (txt.length() > 0) txt.deleteCharAt(txt.length() - 1); fresh[0] = false; }
                        else {
                            if (fresh[0]) { txt.setLength(0); fresh[0] = false; }
                            if (k.equals(".") && txt.indexOf(".") >= 0) return;
                            if (txt.length() < 11) txt.append(k.equals(".") && txt.length() == 0 ? "0." : k);
                        }
                        refresh.run();
                    }
                });
                b.setTextSize(24);
                if (k.equals("⌫")) b.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) { txt.setLength(0); refresh.run(); return true; }
                });
                r.addView(b, Ui.lp(0, Ui.dp(a, 58), 1, a, 3));
            }
            col.addView(r);
        }
        // Historique « Mem » : 20 dernières fréquences/modes, enregistrement automatique réglable, bouton Store
        LinearLayout hr = Ui.row(a);
        final java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        final Spinner mem = Ui.spinner(a, names);
        final Runnable fill = new Runnable() {
            public void run() {
                names.clear();
                names.add("Mem");
                for (Memory m : a.st.hist) names.add(Memory.kHz(m.hz) + " kHz  " + (m.mode.isEmpty() ? "" : MainActivity.modeLabel(m.mode)));
                ((android.widget.ArrayAdapter) mem.getAdapter()).notifyDataSetChanged();
                mem.setSelection(0, false);
            }
        };
        fill.run();
        mem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (i < 1 || i > a.st.hist.size()) return;
                Memory m = a.st.hist.get(i - 1);
                d.dismiss();
                if (a.scanning) a.stopScan(null);
                a.applyMemory(m);
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        hr.addView(mem, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 3));
        hr.addView(Ui.button(a, "Store", new View.OnClickListener() {
            public void onClick(View v) {
                a.st.addHist(a.freq, a.mode);
                a.st.save();
                fill.run();
                Ui.toast(a, Lang.T("Mémorisé : ", "Stored: ") + Memory.kHz(a.freq) + " kHz");
            }
        }));
        final int[] mins = { 0, 1, 2, 3, 5, 10, 15, 20, 30 };
        java.util.ArrayList<String> mn = new java.util.ArrayList<String>();
        int cur = 0;
        for (int i = 0; i < mins.length; i++) { mn.add("Auto " + mins[i]); if (mins[i] == a.st.histMin) cur = i; }
        Spinner iv = Ui.spinner(a, mn, " min");
        iv.setSelection(cur, false);
        iv.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int i, long id) {
                if (mins[i] == a.st.histMin) return;
                a.st.histMin = mins[i];
                a.st.save();
                a.histSchedule();
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        hr.addView(iv, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 3));
        col.addView(hr);
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.END);
        r.addView(Ui.button(a, Lang.T("Annuler", "Cancel"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        r.addView(Ui.button(a, "OK", new View.OnClickListener() {
            public void onClick(View v) {
                long hz = parse(txt.toString());
                if (hz < Store.MIN_HZ || hz > Store.MAX_HZ) {
                    Ui.toast(a, Lang.T("Fréquence invalide : tapez une valeur en kHz, par ex. 7074 ou 14074.5", "Invalid frequency: type a value in kHz, e.g. 7074 or 14074.5"));
                    return;
                }
                d.dismiss();
                if (a.scanning) a.stopScan(null);
                a.tune(hz);
            }
        }));
        col.addView(r);
        d.show();
    }

    // Texte en kHz → Hz (0 si illisible).
    static long parse(String s) {
        try { return Math.round(Double.parseDouble(s.trim()) * 1000.0); } catch (Exception e) { return 0; }
    }
}