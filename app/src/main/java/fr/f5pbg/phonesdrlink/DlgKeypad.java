package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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