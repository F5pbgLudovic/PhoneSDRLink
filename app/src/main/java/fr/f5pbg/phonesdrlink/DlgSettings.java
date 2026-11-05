package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
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
        col.addView(toggle(a, Lang.T("Mode sombre", "Dark mode"), st.dark, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.dark = on; st.save(); d.dismiss(); a.recreate(); }
        }));
        col.addView(toggle(a, Lang.T("Interdire l'extinction de l'écran", "Keep the screen on"), st.keepScreen, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.keepScreen = on; st.save(); a.applyKeepScreen(); }
        }));
        col.addView(toggle(a, Lang.T("Zoom du spectre (1 kHz par mm)", "Spectrum zoom (1 kHz per mm)"), st.spectrumZoom, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.spectrumZoom = on; st.save(); if (on) a.applySpectrumZoom(); }
        }));
        col.addView(toggle(a, Lang.T("Notch automatique (DNF)", "Auto notch (DNF)"), st.autoNotch, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.autoNotch = on; st.save(); a.applyAudio(); }
        }));
        col.addView(toggle(a, Lang.T("Réducteur de bruit (DNR)", "Noise reducer (DNR)"), st.autoDnr, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.autoDnr = on; st.save(); a.applyAudio(); }
        }));

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
