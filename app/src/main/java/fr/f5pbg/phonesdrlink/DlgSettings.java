package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.graphics.Color;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.io.File;
import java.io.OutputStream;

// Bouton SET : mode sombre, mise à jour, langue, notch automatique (DNF), réducteur de bruit (DNR), nom donné aux WebSDR,
// import / export des réglages, réinitialisation.
final class DlgSettings {
    static void show(final MainActivity a) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        final Dialog d = Ui.window(a, "SET", Ui.scroll(a, col), false);

        TextView ver = Ui.label(a, "PhoneSDRLink V" + a.versionName() + " @F5PBG 2026");
        ver.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ver.setTextColor(0xFFFFEF00);   // jaune canari
        ver.setBackground(Ui.round(0xFF1B2128, 10, a));
        ver.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        col.addView(ver, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 0));
        add(col, a, Lang.T("Décodeur FT8 / FT4 / CW / RTTY / FreeDV…", "FT8 / FT4 / CW / RTTY / FreeDV decoder…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); DlgDecoder.show(a); }
        });
        col.addView(toggle(a, "FreeDV auto", st.freedvAuto, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.freedvAuto = on; st.save(); a.applyFreedvAuto(); }
        }));
        col.addView(Ui.small(a, Lang.T("Écoute classique en permanence ; dès que des trames FreeDV RADE sont décodées, le son bascule sur la parole décodée. Coupé pendant qu'un autre décodeur est actif, remis en route dès qu'il s'arrête.", "Normal listening all the time; as soon as FreeDV RADE frames are decoded, the sound switches to the decoded speech. Suspended while another decoder is active, resumed as soon as it stops.")));
        col.addView(toggle(a, Lang.T("Mode sombre", "Dark mode"), st.dark, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.dark = on; st.save(); d.dismiss(); a.recreate(); }
        }));
        col.addView(toggle(a, Lang.T("Interdire l'extinction de l'écran", "Keep the screen on"), st.keepScreen, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.keepScreen = on; st.save(); a.applyKeepScreen(); }
        }));
        col.addView(toggle(a, Lang.T("Zoom du spectre (1 kHz par mm)", "Spectrum zoom (1 kHz per mm)"), st.spectrumZoom, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.spectrumZoom = on; st.save(); if (on) a.applySpectrumZoom(); }
        }));
        col.addView(levelRow(a, toggle(a, Lang.T("Notch automatique (DNF)", "Auto notch (DNF)"), st.autoNotch, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.autoNotch = on; st.save(); a.applyAudio(); }
        }), st.notchLevel, new Ui.Index() { public void chose(int i) { st.notchLevel = i + 1; st.save(); a.applyAudio(); } }));
        col.addView(levelRow(a, toggle(a, Lang.T("Réducteur de bruit (DNR)", "Noise reducer (DNR)"), st.autoDnr, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.autoDnr = on; st.save(); a.applyAudio(); }
        }), st.dnrLevel, new Ui.Index() { public void chose(int i) { st.dnrLevel = i + 1; st.save(); a.applyAudio(); } }));
        col.addView(toggle(a, Lang.T("Réinitialisation automatique WEBSDR", "Automatic WebSDR reset"), st.autoReload, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.autoReload = on; st.save(); a.applyAudio(); }
        }));
        col.addView(Ui.small(a, Lang.T("Recharge le WebSDR si le son est coupé depuis 30 s (sauf volume à zéro ou mute).", "Reloads the WebSDR if the sound has been lost for 30 s (unless volume is zero or muted).")));
        col.addView(toggle(a, Lang.T("Boutons du casque (arrêt / départ du scan)", "Headset buttons (stop / start the scan)"), st.headsetStop, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.headsetStop = on; st.save(); a.applyHeadset(); }
        }));
        col.addView(Ui.small(a, Lang.T("Bouton de l'oreillette ou du casque : arrête le scan, puis le relance ; un double appui quand il est arrêté recule d'un pas. Décochée : les boutons du casque sont ignorés.", "Earphone or headset button: stops the scan, then starts it again; a double press while it is stopped goes back one step. Unticked: the headset buttons are ignored.")));
        add(col, a, Lang.T("Couleurs…", "Colours…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); showColors(a); }
        });

        add(col, a, Lang.T("Langue :", "Language:") + " " + name(st.language), new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                Ui.choose(a, Lang.T("Langue :", "Language:"), Lang.NAMES, new Ui.Index() {
                    public void chose(int i) { st.language = Lang.CODES[i]; st.save(); a.recreate(); }
                });
            }
        });
        add(col, a, Lang.T("Vérifier les mises à jour", "Check for updates"), new View.OnClickListener() {
            public void onClick(View v) { a.checkUpdate(true); }
        });
        col.addView(toggle(a, Lang.T("Vérifier les mises à jour au démarrage", "Check for updates at startup"), st.checkUpdates, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.checkUpdates = on; st.save(); }
        }));
        add(col, a, Lang.T("Nom ou indicatif donné aux WebSDR", "Name or callsign for WebSDRs") + " : " + st.callsign, new View.OnClickListener() {
            public void onClick(View v) {
                d.dismiss();
                Ui.input(a, Lang.T("Nom ou indicatif donné aux WebSDR qui en demandent un (KiwiSDR, websdr.org) :", "Name or callsign given to WebSDRs that ask for one (KiwiSDR, websdr.org):"),
                    "", st.callsign, new Ui.Text() {
                        public void got(String text) {
                            String c = Store.clean(text);
                            if (c.isEmpty()) { Ui.toast(a, Lang.T("L'indicatif ne peut pas être vide.", "The callsign cannot be empty.")); return; }
                            st.callsign = c;
                            st.save();
                            a.updateIdentity();
                            a.web.reload();
                        }
                    });
            }
        });
        add(col, a, Lang.T("Exporter tous les réglages…", "Export all settings…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); export(a); }
        });
        add(col, a, Lang.T("Importer des réglages…", "Import settings…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); importSettings(a); }
        });
        Button reset = add(col, a, Lang.T("Réinitialiser le programme…", "Reset the program…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); reset(a); }
        });
        Ui.paint(reset, false, 0);
        d.show();
    }

    static String name(String code) {
        for (int i = 0; i < Lang.CODES.length; i++) if (Lang.CODES[i].equals(code)) return Lang.NAMES[i];
        return code;
    }

    // Coche suivie d'un petit menu déroulant de niveau (1 = réglage d'origine, 10 = le plus fort).

    // ---- Couleurs de l'écran (boutons, fréquence, réglette) ----
    static final int[] PALETTE = {
        0xFF000000, 0xFF333333, 0xFF666666, 0xFF999999, 0xFFCCCCCC, 0xFFFFFFFF,
        0xFF7F0000, 0xFFC62828, 0xFFF44336, 0xFFFF7043, 0xFFFF9800, 0xFFFFC107,
        0xFFFFEB3B, 0xFFCDDC39, 0xFF8BC34A, 0xFF4CAF50, 0xFF2E7D32, 0xFF004D40,
        0xFF009688, 0xFF00BCD4, 0xFF4FC3F7, 0xFF2196F3, 0xFF0B5FA5, 0xFF1A237E,
        0xFF3F51B5, 0xFF673AB7, 0xFF9C27B0, 0xFFE91E63, 0xFF880E4F, 0xFF795548 };

    // Couleurs interdites : un texte ne peut pas prendre la couleur de son fond ; le fond général ne peut pas prendre celle du fond des boutons.
    static boolean forb(int id, int c) {
        switch (id) {
            case 0: return c == Ui.btnTx() || c == Ui.bg();
            case 1: return c == Ui.btn();
            case 2: return c == Ui.freqBg();
            case 3: return c == Ui.freqTx();
            case 4: return c == Ui.barBg();
            case 5: return c == Ui.barFgText();
            case 7: return c == Ui.ft8B() || c == Ui.panel();
            case 8: return c == Ui.ft8A() || c == Ui.panel();
            default: return c == Ui.btn();
        }
    }
    static void setColor(Store st, int id, int c) {
        switch (id) { case 0: st.colBtnBg = c; break; case 1: st.colBtnTx = c; break; case 2: st.colFreqTx = c; break; case 3: st.colFreqBg = c; break; case 4: st.colBarFg = c; break; case 5: st.colBarBg = c; break; case 7: st.colFt8A = c; break; case 8: st.colFt8B = c; break; default: st.colBg = c; }
    }

    static void showColors(MainActivity a) {
        LinearLayout col = Ui.column(a);
        Dialog cd = Ui.window(a, Lang.T("Couleurs", "Colours"), Ui.scroll(a, col), false);
        colors(col, a, cd);
        cd.show();
    }

    static void colors(LinearLayout col, final MainActivity a, final Dialog d) {
        final Store st = a.st;
        col.addView(Ui.small(a, Lang.T("Couleurs de l'écran principal :", "Main screen colours:")));
        String[] fr = { "Arrière plan général", "Arrière plan boutons", "Texte boutons", "Texte fréquence", "Arrière plan fréquence", "Avant plan réglette", "Arrière plan réglette" };
        String[] en = { "General background", "Button background", "Button text", "Frequency text", "Frequency background", "Tuning bar foreground", "Tuning bar background" };
        for (int i = 0; i < 7; i++) {
            final int id = i == 0 ? 6 : i - 1;   // « Arrière plan général » en premier
            Button b = Ui.button(a, Lang.T(fr[i], en[i]), new View.OnClickListener() {
                public void onClick(View v) { pick(a, d, id); }
            });
            if (id == 6) {
                android.graphics.drawable.GradientDrawable g = Ui.round(Ui.bg(), 10, a);
                g.setStroke(Ui.dp(a, 1), 0x88888888);
                b.setBackground(g);
                int bgc = Ui.bg(), lum = (Color.red(bgc) * 30 + Color.green(bgc) * 59 + Color.blue(bgc) * 11) / 100;
                b.setTextColor(lum > 128 ? Color.BLACK : Color.WHITE);
            }
            if (id == 2 || id == 3) { b.setTextColor(Ui.freqTx()); b.setBackground(Ui.round(Ui.freqBg(), 10, a)); }
            if (id == 4 || id == 5) { b.setTextColor(Ui.barFgText()); b.setBackground(Ui.round(Ui.barBg(), 10, a)); }
            col.addView(b, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        }
        col.addView(Ui.small(a, Lang.T("Couleurs du décodeur (deux périodes FT8 / FT4) :", "Decoder colours (the two FT8 / FT4 periods):")));
        for (int i = 0; i < 2; i++) {
            final int id = 7 + i;
            Button b = Ui.button(a, i == 0 ? Lang.T("Décodeur : période 1", "Decoder: period 1") : Lang.T("Décodeur : période 2", "Decoder: period 2"), new View.OnClickListener() {
                public void onClick(View v) { pick(a, d, id); }
            });
            b.setTextColor(id == 7 ? Ui.ft8A() : Ui.ft8B());
            b.setBackground(Ui.round(Ui.panel(), 10, a));
            col.addView(b, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        }
        col.addView(Ui.button(a, Lang.T("Couleurs par défaut", "Default colours"), new View.OnClickListener() {
            public void onClick(View v) {
                st.colBtnBg = st.colBtnTx = st.colFreqTx = st.colFreqBg = st.colBarFg = st.colBarBg = st.colBg = st.colFt8A = st.colFt8B = 0;
                applyColors(a, d, null);
            }
        }), Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
    }

    static void applyColors(MainActivity a, Dialog settings, Dialog palette) {
        a.st.save();
        Ui.loadColors(a.st);
        if (palette != null) palette.dismiss();
        settings.dismiss();
        Ui.reopenColors = true;
        a.recreate();
    }

    // Palette de couleurs : la couleur de son vis-à-vis (texte/fond) est grisée et refusée, sinon le texte ne se verrait plus.
    static void pick(final MainActivity a, final Dialog settings, final int id) {
        LinearLayout col = Ui.column(a);
        final Dialog p = Ui.window(a, Lang.T("Choisir une couleur", "Choose a colour"), Ui.scroll(a, col), false);
        for (int r = 0; r < PALETTE.length / 6; r++) {
            LinearLayout row = Ui.row(a);
            for (int k = 0; k < 6; k++) {
                final int c = PALETTE[r * 6 + k];
                View sw = new View(a);
                android.graphics.drawable.GradientDrawable g = Ui.round(c, 8, a);
                g.setStroke(Ui.dp(a, 1), 0x88888888);
                sw.setBackground(g);
                boolean same = forb(id, c);
                sw.setAlpha(same ? 0.2f : 1f);
                sw.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (forb(id, c)) { Ui.toast(a, Lang.T("Même couleur que son vis-à-vis (texte invisible) : choisissez-en une autre", "Same colour as its counterpart (text would vanish): pick another one")); return; }
                        setColor(a.st, id, c);
                        applyColors(a, settings, p);
                    }
                });
                row.addView(sw, Ui.lp(0, Ui.dp(a, 48), 1, a, 3));
            }
            col.addView(row);
        }
        // Couleur personnalisée : trois valeurs de 0 à 255 (rouge, vert, bleu).
        int cur;
        switch (id) { case 0: cur = Ui.btn(); break; case 1: cur = Ui.btnTx(); break; case 2: cur = Ui.freqTx(); break; case 3: cur = Ui.freqBg(); break; case 4: cur = Ui.barFgText(); break; case 5: cur = Ui.barBg(); break; case 7: cur = Ui.ft8A(); break; case 8: cur = Ui.ft8B(); break; default: cur = Ui.bg(); }
        final android.widget.EditText[] rgb = new android.widget.EditText[3];
        final LinearLayout custom = Ui.row(a);
        String[] hints = { "R", Lang.T("V", "G"), "B" };
        int[] init = { Color.red(cur), Color.green(cur), Color.blue(cur) };
        for (int i = 0; i < 3; i++) {
            rgb[i] = Ui.edit(a, String.valueOf(init[i]), hints[i] + " (0-255)", android.text.InputType.TYPE_CLASS_NUMBER);
            rgb[i].setGravity(Gravity.CENTER);
            custom.addView(rgb[i], Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 3));
        }
        custom.addView(Ui.button(a, "OK", new View.OnClickListener() {
            public void onClick(View v) {
                int[] n = new int[3];
                for (int i = 0; i < 3; i++) {
                    try { n[i] = Math.max(0, Math.min(255, Integer.parseInt(rgb[i].getText().toString().trim()))); } catch (Exception e) { n[i] = 0; }
                }
                int c = Color.rgb(n[0], n[1], n[2]);
                if (forb(id, c)) { Ui.toast(a, Lang.T("Même couleur que son vis-à-vis (texte invisible) : choisissez-en une autre", "Same colour as its counterpart (text would vanish): pick another one")); return; }
                setColor(a.st, id, c);
                applyColors(a, settings, p);
            }
        }), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 3));
        custom.setVisibility(View.GONE);
        CheckBox use = Ui.check(a, Lang.T("Couleur personnalisée (RVB)", "Custom colour (RGB)"), false);
        use.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { custom.setVisibility(on ? View.VISIBLE : View.GONE); }
        });
        col.addView(use);
        col.addView(custom);
        p.show();
    }
    static LinearLayout levelRow(MainActivity a, CheckBox box, int level, final Ui.Index onChoose) {
        LinearLayout r = Ui.row(a);
        r.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        java.util.ArrayList<String> n = new java.util.ArrayList<String>();
        for (int i = 1; i <= 10; i++) n.add(String.valueOf(i));
        final Spinner s = Ui.spinner(a, n);
        s.setSelection(Math.max(0, Math.min(9, level - 1)));
        final int[] cur = { level - 1 };
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (pos != cur[0]) { cur[0] = pos; onChoose.chose(pos); } }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        r.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    static CheckBox toggle(MainActivity a, String text, boolean on, CompoundButton.OnCheckedChangeListener l) {
        CheckBox c = Ui.check(a, text, on);
        c.setPadding(c.getPaddingLeft(), Ui.dp(a, 10), 0, Ui.dp(a, 10));
        c.setOnCheckedChangeListener(l);
        return c;
    }

    static Button add(LinearLayout col, MainActivity a, String text, View.OnClickListener l) {
        Button b = Ui.button(a, text, l);
        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        col.addView(b, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 3));
        return b;
    }

    // ------------------------------------------------------------------ export / import

    static void export(final MainActivity a) {
        a.pickFile(true, "PhoneSDRLink-settings.ini", new MainActivity.FileCb() {
            public void done(Uri uri) {
                try {
                    a.st.freqHz = a.freq;
                    a.st.mode = a.mode;
                    OutputStream o = a.getContentResolver().openOutputStream(uri, "wt");
                    o.write(a.st.exportText().getBytes("UTF-8"));
                    o.close();
                    Ui.message(a, Lang.T("Exporter tous les réglages…", "Export all settings…"), Lang.F("Réglages exportés vers « {0} »", "Settings exported to \"{0}\"", uri.getLastPathSegment()));
                } catch (Exception e) {
                    Ui.message(a, Lang.T("Exporter tous les réglages…", "Export all settings…"), Lang.T("Export impossible des réglages : ", "Cannot export the settings: ") + e.getMessage());
                }
            }
        });
    }

    static void importSettings(final MainActivity a) {
        a.pickFile(false, null, new MainActivity.FileCb() {
            public void done(Uri uri) {
                String text;
                try { text = Store.readAll(a.getContentResolver().openInputStream(uri)); }
                catch (Exception e) { Ui.message(a, Lang.T("Importer des réglages…", "Import settings…"), Lang.T("Import impossible des réglages : ", "Cannot import the settings: ") + e.getMessage()); return; }
                if (!text.contains("Fav=") && !text.contains("Mem=") && !text.contains("ScanProfile=") && !text.contains("Language=") && !text.contains("ScanType=")) {
                    Ui.message(a, Lang.T("Importer des réglages…", "Import settings…"), Lang.T("Import : ce fichier ne contient pas de réglages SDRLink ou PhoneSDRLink", "Import: this file does not contain SDRLink or PhoneSDRLink settings"));
                    return;
                }
                askImport(a, text);
            }
        });
    }

    static void askImport(final MainActivity a, final String text) {
        LinearLayout col = Ui.column(a);
        final Dialog d = Ui.window(a, Lang.T("Importer des réglages…", "Import settings…"), Ui.scroll(a, col), false);
        col.addView(Ui.label(a, Lang.T("Que faut-il importer de ce fichier ?", "What should be imported from this file?")));
        final CheckBox cfg = Ui.check(a, Lang.T("Configuration (affichage, langue, nom…)", "Configuration (display, language, name…)"), true);
        final CheckBox web = Ui.check(a, Lang.T("WebSDR (favoris et listes de favoris)", "WebSDR (favorites and favorite lists)"), has(text, "Fav="));
        final CheckBox mem = Ui.check(a, Lang.T("Mémoires de fréquences", "Frequency memories"), has(text, "Mem="));
        final CheckBox scn = Ui.check(a, Lang.T("Scan (réglage, zones non scannées et profils de scan)", "Scan (setting, skipped ranges and scan profiles)"), has(text, "Scan"));
        cfg.setChecked(has(text, "Language=") || has(text, "DarkMode="));
        for (CheckBox c : new CheckBox[] { cfg, web, mem, scn }) {
            if (!c.isChecked() && c != cfg) { c.setEnabled(false); c.setText(c.getText() + Lang.T(" — absent du fichier", " — not in the file")); }
            col.addView(c);
        }
        col.addView(Ui.label(a, Lang.T("Favoris, mémoires et profils de scan déjà dans l'application :", "Favorites, memories and scan profiles already in the app:")));
        final RadioGroup g = new RadioGroup(a);
        final RadioButton keep = new RadioButton(a), repl = new RadioButton(a);
        keep.setText(Lang.T("les garder et ajouter ceux du fichier (sans doublons)", "keep them and add those of the file (no duplicates)"));
        repl.setText(Lang.T("les supprimer et les remplacer par ceux du fichier", "delete them and replace them with those of the file"));
        keep.setTextColor(Ui.fg());
        repl.setTextColor(Ui.fg());
        g.addView(keep);
        g.addView(repl);
        keep.setChecked(true);
        col.addView(g);
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.END);
        r.addView(Ui.button(a, Lang.T("Annuler", "Cancel"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        r.addView(Ui.button(a, Lang.T("Importer", "Import"), new View.OnClickListener() {
            public void onClick(View v) {
                int cats = (cfg.isChecked() ? Store.IMP_CONFIG : 0) | (web.isChecked() && web.isEnabled() ? Store.IMP_WEBSDR : 0)
                    | (mem.isChecked() && mem.isEnabled() ? Store.IMP_MEMS : 0) | (scn.isChecked() && scn.isEnabled() ? Store.IMP_SCAN : 0);
                if (cats == 0) return;
                d.dismiss();
                if (keep.isChecked()) a.st.merge(text, cats); else a.st.parse(text, cats);
                a.st.save();
                a.recreate();
            }
        }));
        col.addView(r);
        d.show();
    }

    static boolean has(String text, String key) { return text.contains(key); }

    // ------------------------------------------------------------------ réinitialisation

    static void reset(final MainActivity a) {
        LinearLayout col = Ui.column(a);
        final Dialog d = Ui.window(a, Lang.T("Réinitialiser le programme", "Reset the program"), Ui.scroll(a, col), false);
        final CheckBox full = Ui.check(a, Lang.T("Réinitialisation totale (tous les réglages, mémoires et favoris sont perdus)", "Full reset (all settings, favorites and memories are lost)"), false);
        col.addView(full);
        col.addView(Ui.label(a, Lang.T("Réinitialisation partielle :", "Partial reset:")));
        final CheckBox cfg = Ui.check(a, Lang.T("Configuration (affichage, langue, nom…)", "Configuration (display, language, name…)"), false);
        final CheckBox web = Ui.check(a, Lang.T("WebSDR (favoris et listes de favoris, retour à ceux du départ)", "WebSDR (favorites and favorite lists, back to the starting ones)"), false);
        final CheckBox mem = Ui.check(a, Lang.T("Mémoires de fréquences", "Frequency memories"), false);
        final CheckBox scn = Ui.check(a, Lang.T("Scan (réglage, zones non scannées et profils de scan)", "Scan (setting, skipped ranges and scan profiles)"), false);
        final CheckBox brw = Ui.check(a, Lang.T("Données du navigateur intégré (cache, cookies, réglages gardés par les sites WebSDR)", "Built-in browser data (cache, cookies, settings kept by the WebSDR sites)"), false);
        col.addView(cfg);
        col.addView(web);
        col.addView(mem);
        col.addView(scn);
        col.addView(brw);
        col.addView(Ui.small(a, Lang.T("À essayer si les pages WebSDR ne s'affichent plus (fenêtre vide, comme sans Internet).", "Worth trying if the WebSDR pages no longer show (empty window, as if there were no Internet).")));
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.END);
        r.addView(Ui.button(a, Lang.T("Annuler", "Cancel"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        r.addView(Ui.button(a, Lang.T("Réinitialiser", "Reset"), new View.OnClickListener() {
            public void onClick(View v) {
                final boolean f = full.isChecked();
                final boolean c1 = cfg.isChecked(), c2 = web.isChecked(), c3 = mem.isChecked(), c4 = scn.isChecked(), c5 = brw.isChecked();
                if (!f && !c1 && !c2 && !c3 && !c4 && !c5) return;
                d.dismiss();
                String msg = f ? Lang.T("Réinitialisation totale : tous les réglages, favoris, mémoires et scans seront perdus.", "Full reset: all settings, favorites, memories and scans will be lost.")
                    : Lang.T("Les éléments cochés seront remis comme à la première installation.", "The checked items will be set back as at first installation.");
                Ui.confirm(a, Lang.T("⚠ Attention", "⚠ Warning"), msg + "\n\n" + Lang.T("Continuer ?", "Continue?"), new Ui.Yes() {
                    public void yes() { doReset(a, f, c1, c2, c3, c4, c5); }
                });
            }
        }));
        col.addView(r);
        d.show();
    }

    static void doReset(MainActivity a, boolean full, boolean cfg, boolean web, boolean mem, boolean scn, boolean brw) {
        Store st = a.st;
        if (full) {
            File f = Store.file;
            if (f.exists()) f.delete();
            Store n = new Store();
            n.language = Lang.systemDefault();
            Store.I = n;
            brw = true;
        } else {
            if (cfg) { st.defaultsConfig(); st.language = Lang.systemDefault(); }
            if (web) st.defaultsWebSdr();
            if (mem) st.defaultsMems();
            if (scn || mem) st.defaultsScan();
        }
        if (brw) {
            a.web.clearCache(true);
            a.web.clearHistory();
            CookieManager.getInstance().removeAllCookies(null);
            WebStorage.getInstance().deleteAllData();
        }
        Store.I.save();
        a.recreate();
    }
}
