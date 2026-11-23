package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

// Égaliseur 10 bandes de réception (1.34), identique à celui de SDRLink 1.54 : 31 Hz à 10 kHz, ±12 dB, filtres en cloche
// du navigateur dans la page. Ouvert par un clic long sur le pas, sous le mode. Réglage enregistré dans Store.fxRx, au format
// de la clé FxRx de SDRLink (« 100;g1,..,g10;seuil,... ») : il passe tel quel dans les exports et s'importe dans SDRLink.
final class DlgEq {
    static final int[] FREQ = { 31, 60, 115, 220, 420, 800, 1500, 2800, 5300, 10000 };
    static final double[][] PRESETS = {
        { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
        { -6, -5, -3, -1, 0, 1, 3, 4, 2, 0 },
        { -12, -10, -6, -2, 0, 0, 0, 0, 0, 0 },
        { 0, 2, 4, 3, 1, 0, -1, -2, -3, -4 },
        { 0, 0, 0, 0, 0, 0, -1, -3, -6, -9 },
        { -12, -9, -5, -1, 2, 4, 4, 2, -2, -8 } };
    static final String DEF_COMP = "-18.0,3.0,3.0,-1.0,5.0,150.0,-50.0,150.0";   // compresseur et noise gate de SDRLink : gardés tels quels

    final MainActivity a;
    final Store st;
    boolean on, filling;
    final double[] g = new double[10];
    String flags = "000", tail = DEF_COMP;
    final SeekBar[] sb = new SeekBar[10];
    final TextView[] lv = new TextView[10];
    View curve;
    Dialog d;

    DlgEq(MainActivity a) { this.a = a; this.st = a.st; parse(st.fxRx); }

    static void show(MainActivity a) { new DlgEq(a).open(); }

    void parse(String s) {
        try {
            String[] p = (s == null ? "" : s).split(";");
            if (p.length >= 3 && p[0].length() >= 3) {
                on = p[0].charAt(0) == '1'; flags = "0" + p[0].substring(1, 3);
                String[] v = p[1].split(",");
                for (int i = 0; i < 10 && i < v.length; i++) g[i] = clamp(Double.parseDouble(v[i].trim()));
                tail = p[2];
            }
        } catch (Exception e) { }
    }

    static double clamp(double v) { return Math.max(-12, Math.min(12, v)); }

    String build() {
        StringBuilder b = new StringBuilder();
        b.append(on ? '1' : '0').append(flags.substring(1)).append(';');
        for (int i = 0; i < 10; i++) { if (i > 0) b.append(','); b.append(String.format(Locale.US, "%.1f", g[i])); }
        return b.append(';').append(tail).toString();
    }

    // appel de la page : __sdrlink.setEq(marche, [10 gains en dB])
    static String pageCall(String fx) {
        DlgEq q = new DlgEq(null, fx);
        StringBuilder b = new StringBuilder("if(window.__sdrlink&&__sdrlink.setEq)__sdrlink.setEq(" + q.on + ",[");
        for (int i = 0; i < 10; i++) { if (i > 0) b.append(','); b.append(String.format(Locale.US, "%.1f", q.g[i])); }
        return b.append("]);").toString();
    }

    private DlgEq(MainActivity a, String fx) { this.a = a; this.st = null; parse(fx); }

    void push() {
        st.fxRx = build();
        a.js(pageCall(st.fxRx));
        if (curve != null) curve.invalidate();
    }

    void setAll(double[] v) {
        filling = true;
        for (int i = 0; i < 10; i++) { g[i] = v[i]; sb[i].setProgress((int) Math.round(v[i] * 10) + 120); lv[i].setText(db(v[i])); }
        filling = false;
        push(); st.save();
    }

    static String db(double v) { return (v > 0 ? "+" : "") + String.format(Locale.US, "%.1f", v) + " dB"; }

    static String hz(int f) { return f >= 1000 ? (f / 1000.0 == Math.floor(f / 1000.0) ? String.valueOf(f / 1000) : String.valueOf(f / 1000.0)) + " kHz" : f + " Hz"; }

    void open() {
        final Context c = a;
        LinearLayout col = Ui.column(c);
        col.setPadding(Ui.dp(c, 14), Ui.dp(c, 6), Ui.dp(c, 14), Ui.dp(c, 10));
        CheckBox chk = Ui.check(c, Lang.T("Égaliseur actif", "Equalizer on"), on);
        chk.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean v) { if (filling) return; on = v; push(); st.save(); }
        });
        col.addView(chk);

        LinearLayout r = Ui.row(c);
        ArrayList<String> names = new ArrayList<String>();
        names.add(Lang.T("Préréglage…", "Preset…"));
        names.add(Lang.T("À plat", "Flat"));
        names.add(Lang.T("Voix claire (présence)", "Clear voice (presence)"));
        names.add(Lang.T("Graves coupés", "Bass cut"));
        names.add(Lang.T("Chaleureux", "Warm"));
        names.add(Lang.T("Aigus adoucis", "Softened treble"));
        names.add(Lang.T("DX (médium renforcé)", "DX (boosted midrange)"));
        final Spinner sp = Ui.spinner(c, names);
        r.addView(sp, Ui.lp(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1, c, 2));
        col.addView(r);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (pos > 0) { setAll(PRESETS[pos - 1]); sp.setSelection(0); } }
            public void onNothingSelected(AdapterView<?> p) { }
        });

        curve = new View(c) {
            protected void onDraw(Canvas cv) { drawCurve(cv, getWidth(), getHeight()); }
        };
        col.addView(curve, Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 150), 0, c, 4));

        for (int i = 0; i < 10; i++) {
            final int k = i;
            LinearLayout row = Ui.row(c);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView f = Ui.small(c, hz(FREQ[i]));
            row.addView(f, new LinearLayout.LayoutParams(Ui.dp(c, 64), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            SeekBar s = new SeekBar(c);
            s.setMax(240);
            s.setProgress((int) Math.round(g[i] * 10) + 120);
            s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar b, int p, boolean user) {
                    if (filling) return;
                    g[k] = Math.round((p - 120) / 5.0) / 2.0;   // pas de 0,5 dB
                    lv[k].setText(db(g[k]));
                    push();
                }
                public void onStartTrackingTouch(SeekBar b) { }
                public void onStopTrackingTouch(SeekBar b) { st.save(); }
            });
            sb[i] = s;
            row.addView(s, new LinearLayout.LayoutParams(0, Ui.dp(c, 40), 1f));
            lv[i] = Ui.small(c, db(g[i]));
            lv[i].setGravity(android.view.Gravity.END);
            row.addView(lv[i], new LinearLayout.LayoutParams(Ui.dp(c, 70), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            col.addView(row);
        }

        Button close = Ui.button(c, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } });
        col.addView(close, Ui.lp(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 0, c, 6));
        d = Ui.window(c, Lang.T("Égaliseur 10 bandes — PhoneSDRLink", "10-band equalizer — PhoneSDRLink"), Ui.scroll(c, col), true);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() { public void onDismiss(android.content.DialogInterface x) { st.save(); } });
        d.show();
    }

    // réponse en dB à la fréquence f : somme des filtres en cloche (RBJ, Q 1,41) à 48 kHz, comme SDRLink
    double response(double f) {
        double fs = 48000, tot = 0, w = 2 * Math.PI * f / fs;
        double cw = Math.cos(w), sw = Math.sin(w), c2 = Math.cos(2 * w), s2 = Math.sin(2 * w);
        for (int i = 0; i < 10; i++) {
            if (Math.abs(g[i]) < 0.05) continue;
            double A = Math.pow(10, g[i] / 40), w0 = 2 * Math.PI * FREQ[i] / fs, al = Math.sin(w0) / (2 * 1.41), cs = Math.cos(w0), a0 = 1 + al / A;
            double b0 = (1 + al * A) / a0, b1 = -2 * cs / a0, b2 = (1 - al * A) / a0, a1 = -2 * cs / a0, a2 = (1 - al / A) / a0;
            double nr = b0 + b1 * cw + b2 * c2, ni = -(b1 * sw + b2 * s2), dr = 1 + a1 * cw + a2 * c2, di = -(a1 * sw + a2 * s2);
            tot += 10 * Math.log10((nr * nr + ni * ni) / (dr * dr + di * di));
        }
        return tot;
    }

    // courbe de 20 Hz à 12 kHz (échelle logarithmique), ±12 dB
    void drawCurve(Canvas cv, int w, int h) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000);
        cv.drawRect(0, 0, w, h, p);
        float L = Ui.dp(a, 30), R = w - Ui.dp(a, 8), T = Ui.dp(a, 8), B = h - Ui.dp(a, 18), W = R - L, H = B - T;
        double span = Math.log10(600);
        p.setTextSize(Ui.dp(a, 10));
        p.setStrokeWidth(1);
        for (int dd = -12; dd <= 12; dd += 6) {
            float y = T + H * (12 - dd) / 24f;
            p.setColor(0xFF323232); cv.drawLine(L, y, R, y, p);
            p.setColor(0xFF969696); cv.drawText((dd > 0 ? "+" : "") + dd, 2, y + Ui.dp(a, 3), p);
        }
        for (int i = 0; i < 10; i++) {
            float x = L + W * (float) (Math.log10(FREQ[i] / 20.0) / span);
            p.setColor(0xFF323232); cv.drawLine(x, T, x, B, p);
            if (i % 2 == 0) { p.setColor(0xFF969696); String t = FREQ[i] >= 1000 ? (FREQ[i] / 1000.0) + "k" : String.valueOf(FREQ[i]); cv.drawText(t.replace(".0k", "k"), x - Ui.dp(a, 8), h - Ui.dp(a, 4), p); }
        }
        p.setColor(0xFFA0D732); p.setStrokeWidth(Ui.dp(a, 2));
        float px = 0, py = 0;
        for (int i = 0; i < 200; i++) {
            double f = 20 * Math.pow(600, i / 199.0), v = Math.max(-12, Math.min(12, on ? response(f) : 0));
            float x = L + W * i / 199f, y = T + H * (float) ((12 - v) / 24.0);
            if (i > 0) cv.drawLine(px, py, x, y, p);
            px = x; py = y;
        }
        p.setColor(0xFFFFFFFF);
        for (int i = 0; i < 10; i++) cv.drawCircle(L + W * (float) (Math.log10(FREQ[i] / 20.0) / span), T + H * (float) ((12 - g[i]) / 24.0), Ui.dp(a, 2.5f), p);
    }
}
