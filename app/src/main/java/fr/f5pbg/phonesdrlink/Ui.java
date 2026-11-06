package fr.f5pbg.phonesdrlink;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

// Petits outils d'interface : couleurs du thème clair/sombre, boutons, fenêtres de dialogue (tout est dessiné par le code).
final class Ui {
    static boolean dark = true;

    static int bg() { return cBg != 0 ? cBg : dark ? 0xFF101418 : 0xFFF2F4F7; }
    static int panel() { return dark ? 0xFF1B2128 : 0xFFFFFFFF; }
    static int fg() { return dark ? 0xFFE8ECF0 : 0xFF14181C; }
    static int sub() { return dark ? 0xFF9AA5B1 : 0xFF5A6570; }
    // Couleurs choisies dans SET (0 = couleur d'origine du thème).
    static boolean reopenColors;
    static int cBtnBg, cBtnTx, cFreqTx, cFreqBg, cBarFg, cBarBg, cBg, cFt8A, cFt8B, cDec;
    // Décodeur : les deux périodes (paire et impaire) de FT8 / FT4 s'affichent en deux couleurs.
    static int decCol() { return cDec != 0 ? cDec : dark ? 0xFFFF5252 : 0xFFC62828; }   // nom du mode numérique actif, dans la case de la fréquence (rouge par défaut)
    static int ft8A() { return cFt8A != 0 ? cFt8A : dark ? 0xFFFFD54F : 0xFF8D6E00; }
    static int ft8B() { return cFt8B != 0 ? cFt8B : dark ? 0xFFFFFFFF : 0xFF14181C; }
    static int btn() { return cBtnBg != 0 ? cBtnBg : dark ? 0xFF2B3540 : 0xFFDDE3EA; }
    static int btnTx() { return cBtnTx != 0 ? cBtnTx : fg(); }
    static int freqTx() { return cFreqTx != 0 ? cFreqTx : accent(); }
    static int freqBg() { return cFreqBg != 0 ? cFreqBg : panel(); }
    static int barBg() { return cBarBg != 0 ? cBarBg : btn(); }
    static int barFgTicks() { return cBarFg != 0 ? cBarFg : sub(); }
    static int barFgText() { return cBarFg != 0 ? cBarFg : fg(); }
    static void loadColors(Store s) { cBtnBg = s.colBtnBg; cBtnTx = s.colBtnTx; cFreqTx = s.colFreqTx; cFreqBg = s.colFreqBg; cBarFg = s.colBarFg; cBarBg = s.colBarBg; cBg = s.colBg; cFt8A = s.colFt8A; cFt8B = s.colFt8B; cDec = s.colDec; }
    static int accent() { return dark ? 0xFF4FC3F7 : 0xFF0B5FA5; }
    static int on() { return dark ? 0xFF2E7D32 : 0xFF2E7D32; }
    static int danger() { return 0xFFC62828; }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Button button(Context c, String text, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(btnTx());
        b.setBackground(round(btn(), 10, c));
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        b.setSingleLine(false);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    // Aspect d'un bouton « allumé » (mute actif, scan en cours…).
    static void paint(Button b, boolean active, int activeColor) {
        Context c = b.getContext();
        b.setBackground(round(active ? activeColor : btn(), 10, c));
        b.setTextColor(active ? Color.WHITE : btnTx());
    }

    static TextView label(Context c, String text) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(fg());
        t.setTextSize(15);
        return t;
    }

    static TextView small(Context c, String text) {
        TextView t = label(c, text);
        t.setTextSize(12);
        t.setTextColor(sub());
        return t;
    }

    static EditText edit(Context c, String text, String hint, int inputType) {
        EditText e = new EditText(c);
        e.setText(text);
        e.setHint(hint);
        e.setTextColor(fg());
        e.setHintTextColor(sub());
        e.setInputType(inputType);
        e.setSingleLine(true);
        return e;
    }

    static CheckBox check(Context c, String text, boolean on) {
        CheckBox b = new CheckBox(c);
        b.setText(text);
        b.setChecked(on);
        b.setTextColor(fg());
        b.setTextSize(15);
        return b;
    }

    static Spinner spinner(Context c, List<String> items) {
        Spinner s = new Spinner(c);
        ArrayAdapter<String> a = new ArrayAdapter<String>(c, android.R.layout.simple_spinner_item, items) {
            public View getView(int p, View v, ViewGroup g) { TextView t = (TextView) super.getView(p, v, g); t.setTextColor(fg()); return t; }
            public View getDropDownView(int p, View v, ViewGroup g) {
                TextView t = (TextView) super.getDropDownView(p, v, g);
                t.setTextColor(fg());
                t.setBackgroundColor(panel());
                t.setPadding(dp(getContext(), 14), dp(getContext(), 12), dp(getContext(), 14), dp(getContext(), 12));
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        return s;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight, Context c, int marginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, weight);
        int m = dp(c, marginDp);
        p.setMargins(m, m, m, m);
        return p;
    }

    static ScrollView scroll(Context c, View content) {
        ScrollView s = new ScrollView(c);
        s.addView(content);
        return s;
    }

    // Fenêtre de dialogue occupant presque tout l'écran, avec le fond du thème.
    static Dialog window(Context c, String title, View content, boolean fullHeight) {
        Dialog d = new Dialog(c, dark ? android.R.style.Theme_DeviceDefault_Dialog : android.R.style.Theme_DeviceDefault_Light_Dialog);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(panel());
        if (title != null) {
            TextView t = label(c, title);
            t.setTextSize(18);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 6));
            root.addView(t);
        }
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fullHeight ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT, fullHeight ? 1f : 0f));
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(round(panel(), 14, c));
            int width = (int) (c.getResources().getDisplayMetrics().widthPixels * 0.96f);
            int height = fullHeight ? (int) (c.getResources().getDisplayMetrics().heightPixels * 0.92f) : WindowManager.LayoutParams.WRAP_CONTENT;
            w.setLayout(width, height);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    static void toast(Context c, String msg) { Toast.makeText(c, msg, Toast.LENGTH_SHORT).show(); }

    interface Text { void got(String text); }

    static void input(Context c, String title, String hint, String initial, final Text cb) { input(c, title, hint, initial, "OK", Lang.T("Annuler", "Cancel"), cb); }

    // Même fenêtre avec des libellés de boutons au choix ; le clavier s'ouvre tout de suite, le curseur est à la fin du texte.
    static void input(Context c, String title, String hint, String initial, String okLabel, String cancelLabel, final Text cb) {
        final EditText e = edit(c, initial, hint, InputType.TYPE_CLASS_TEXT);
        e.setSelection(e.getText().length());
        LinearLayout col = column(c);
        col.addView(e);
        final Dialog d = window(c, title, col, false);
        LinearLayout r = row(c);
        r.setGravity(Gravity.END);
        r.addView(button(c, cancelLabel, new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        r.addView(button(c, okLabel, new View.OnClickListener() { public void onClick(View v) { d.dismiss(); cb.got(e.getText().toString()); } }));
        col.addView(r);
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        d.show();
        e.requestFocus();
        e.postDelayed(new Runnable() { public void run() { android.view.inputmethod.InputMethodManager m = (android.view.inputmethod.InputMethodManager) e.getContext().getSystemService(Context.INPUT_METHOD_SERVICE); if (m != null) m.showSoftInput(e, 0); } }, 150);
    }

    interface Yes { void yes(); }

    static void confirm(Context c, String title, String message, final Yes cb) { confirm(c, title, message, "OK", Lang.T("Annuler", "Cancel"), cb); }

    static void confirm(Context c, String title, String message, String yesLabel, String noLabel, final Yes cb) {
        LinearLayout col = column(c);
        col.addView(label(c, message));
        final Dialog d = window(c, title, col, false);
        LinearLayout r = row(c);
        r.setGravity(Gravity.END);
        r.addView(button(c, noLabel, new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        Button ok = button(c, yesLabel, new View.OnClickListener() { public void onClick(View v) { d.dismiss(); cb.yes(); } });
        r.addView(ok);
        col.addView(r);
        d.show();
    }

    static void message(Context c, String title, String message) {
        LinearLayout col = column(c);
        col.addView(label(c, message));
        final Dialog d = window(c, title, col, false);
        LinearLayout r = row(c);
        r.setGravity(Gravity.END);
        r.addView(button(c, "OK", new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        col.addView(r);
        d.show();
    }

    interface Index { void chose(int index); }

    // Liste de choix : un appui choisit et ferme.
    static void choose(Context c, String title, String[] items, final Index cb) {
        LinearLayout col = column(c);
        final Dialog d = window(c, title, scroll(c, col), false);
        for (int i = 0; i < items.length; i++) {
            final int k = i;
            Button b = button(c, items[i], new View.OnClickListener() { public void onClick(View v) { d.dismiss(); cb.chose(k); } });
            b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            col.addView(b, lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, c, 3));
        }
        d.show();
    }
}
