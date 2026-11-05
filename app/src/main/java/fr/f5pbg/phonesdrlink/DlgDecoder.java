package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

// Fenêtre « Décodeur » : lance ou arrête le décodage FT8, FT4, CW, RTTY ou FreeDV RADE du son du WebSDR et montre le texte décodé.
// Tant que le décodeur tourne, le bouton SET de l'écran principal ramène ici (le bouton « Réglages (SET)… » ouvre les réglages).
// Les derniers messages restent aussi visibles en bas de l'écran principal (case à cocher ci-dessous). En CW, un panneau de
// réglages (tonalité, filtre, vitesse, seuils…) s'affiche sous le texte décodé.
final class DlgDecoder {
    static ScrollView cwBox, rttyBox, pskBox, sstvBox;
    static View sstvImgBox;

    // Panneaux de réglages et image selon le décodeur choisi.
    static void showPanels(int mode) {
        if (cwBox != null) cwBox.setVisibility(mode == MainActivity.DEC_CW ? View.VISIBLE : View.GONE);
        if (rttyBox != null) rttyBox.setVisibility(mode == MainActivity.DEC_RTTY ? View.VISIBLE : View.GONE);
        if (pskBox != null) pskBox.setVisibility(mode == MainActivity.DEC_PSK ? View.VISIBLE : View.GONE);
        if (sstvBox != null) sstvBox.setVisibility(mode == MainActivity.DEC_SSTV ? View.VISIBLE : View.GONE);
        if (sstvImgBox != null) sstvImgBox.setVisibility(mode == MainActivity.DEC_SSTV ? View.VISIBLE : View.GONE);
    }

    static void show(final MainActivity a) {
        LinearLayout col = Ui.column(a);
        final TextView help = Ui.small(a, Lang.T("Le son du WebSDR est analysé en continu, même coupé. FT8 / FT4 : mode USB ou DATA, sur une fréquence FT8 ou FT4 (7.074 / 7.0475, 14.074 / 14.080 MHz…) ; l'heure du téléphone doit être exacte. CW : mode CW ou USB, sur un signal CW. RTTY : mode USB ou LSB (au moins 500 Hz de bande), sur un signal RTTY (14.080 à 14.099 MHz…). PSK31 : mode USB, sur un signal PSK31 (14.070, 7.035, 3.580 MHz…), recherche automatique de la fréquence audio. SSTV : mode USB (au moins 2,4 kHz de bande), sur une fréquence SSTV (14.230, 7.033, 3.730 MHz…) ; le mode est reconnu tout seul, l'image s'affiche et s'enregistre à la fin. FreeDV RADE : mode USB, sur un signal RADE (14.236 MHz…) ; la parole décodée remplace le son du WebSDR.",
                "The WebSDR sound is analysed continuously, even when muted. FT8 / FT4: USB or DATA mode, on an FT8 or FT4 frequency (7.074 / 7.0475, 14.074 / 14.080 MHz…); the phone clock must be accurate. CW: CW or USB mode, on a CW signal. RTTY: USB or LSB mode (at least 500 Hz bandwidth), on an RTTY signal (14.080 to 14.099 MHz…). PSK31: USB mode, on a PSK31 signal (14.070, 7.035, 3.580 MHz…), the audio frequency is found automatically. SSTV: USB mode (at least 2.4 kHz bandwidth), on an SSTV frequency (14.230, 7.033, 3.730 MHz…); the mode is recognised automatically, the picture is shown and saved when it ends. FreeDV RADE: USB mode, on a RADE signal (14.236 MHz…); the decoded speech replaces the WebSDR sound."));
        col.addView(help);
        if (a.decMode != 0) help.setVisibility(View.GONE);   // l'aide ne sert qu'au premier lancement : on rend la place au texte décodé
        final TextView state = Ui.label(a, "");
        final Dialog[] dd = new Dialog[1];
        LinearLayout r1 = Ui.row(a), r2 = Ui.row(a), r3 = Ui.row(a);
        r1.addView(modeButton(a, help, "FT8", MainActivity.DEC_FT8), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r1.addView(modeButton(a, help, "FT4", MainActivity.DEC_FT4), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r1.addView(modeButton(a, help, "CW", MainActivity.DEC_CW), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r1.addView(modeButton(a, help, "RTTY", MainActivity.DEC_RTTY), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(modeButton(a, help, "PSK31", MainActivity.DEC_PSK), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(modeButton(a, help, "SSTV", MainActivity.DEC_SSTV), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(modeButton(a, help, "FreeDV RADE", MainActivity.DEC_RADE), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f, a, 2));
        r3.addView(Ui.button(a, Lang.T("Arrêter", "Stop"), new View.OnClickListener() { public void onClick(View v) { a.decoderStopUser(); } }), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r3.addView(Ui.button(a, Lang.T("Réglages (SET)…", "Settings (SET)…"), new View.OnClickListener() {
            public void onClick(View v) { if (dd[0] != null) dd[0].dismiss(); DlgSettings.show(a); }
        }), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f, a, 2));
        col.addView(r1);
        col.addView(r2);
        col.addView(r3);
        final CheckBox ov = Ui.check(a, Lang.T("Afficher les derniers messages sur l'écran principal", "Show the latest messages on the main screen"), a.st.decOverlay);
        ov.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { a.st.decOverlay = on; a.st.save(); a.decRefresh(); }
        });
        col.addView(ov);
        col.addView(state);
        // SSTV : l'image reçue (se remplit ligne par ligne)
        final ImageView pic = new ImageView(a);
        pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pic.setBackgroundColor(0xFF101010);
        a.sstvDlgImg = pic;
        if (a.sstvBmp != null) pic.setImageBitmap(a.sstvBmp);
        sstvImgBox = pic;
        col.addView(pic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.34f)));
        pic.setVisibility(a.decMode == MainActivity.DEC_SSTV ? View.VISIBLE : View.GONE);
        final TextView log = new TextView(a);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(11);
        log.setTextColor(Ui.fg());
        log.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        final ScrollView sv = Ui.scroll(a, log);
        col.addView(Ui.small(a, Lang.T("Heure UTC · DT (s) · fréquence audio (Hz) · message (FT8, FT4) ; heure UTC · texte (CW, RTTY, PSK31) ; heure UTC · mode de l'image (SSTV)", "UTC time · DT (s) · audio frequency (Hz) · message (FT8, FT4); UTC time · text (CW, RTTY, PSK31); UTC time · picture mode (SSTV)")));
        col.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        // Réglages CW sous le texte décodé
        LinearLayout cw = Ui.column(a);
        cwPanel(a, cw);
        cwBox = Ui.scroll(a, cw);
        col.addView(cwBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.30f)));
        cwBox.setVisibility(a.decMode == MainActivity.DEC_CW ? View.VISIBLE : View.GONE);
        LinearLayout rt = Ui.column(a);
        rttyPanel(a, rt);
        rttyBox = Ui.scroll(a, rt);
        col.addView(rttyBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.30f)));
        rttyBox.setVisibility(a.decMode == MainActivity.DEC_RTTY ? View.VISIBLE : View.GONE);
        LinearLayout ps = Ui.column(a);
        pskPanel(a, ps);
        pskBox = Ui.scroll(a, ps);
        col.addView(pskBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.30f)));
        pskBox.setVisibility(a.decMode == MainActivity.DEC_PSK ? View.VISIBLE : View.GONE);
        LinearLayout ss = Ui.column(a);
        sstvPanel(a, ss);
        sstvBox = Ui.scroll(a, ss);
        col.addView(sstvBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sstvBox.setVisibility(a.decMode == MainActivity.DEC_SSTV ? View.VISIBLE : View.GONE);
        final Dialog d = Ui.window(a, Lang.T("Décodeur", "Decoder"), col, true);
        dd[0] = d;
        a.decView = log;
        a.decScroll = sv;
        a.decState = state;
        state.setText(a.decStatus);
        a.decRefresh();
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface x) { a.decView = null; a.decScroll = null; a.decState = null; a.sstvDlgImg = null; cwBox = null; rttyBox = null; pskBox = null; sstvBox = null; sstvImgBox = null; }
        });
        d.show();
    }

    static Button modeButton(final MainActivity a, final TextView help, String name, final int mode) {
        return Ui.button(a, name, new View.OnClickListener() {
            public void onClick(View v) {
                help.setVisibility(View.GONE);
                showPanels(mode);
                a.decoderStart(mode);
            }
        });
    }

    interface Fmt { String text(int v); }
    interface Change { void set(int v); }

    // Une ligne « texte + curseur » : v est la valeur affichée (min..max) ; le texte suit le curseur.
    static SeekBar seek(final MainActivity a, LinearLayout box, final TextView label, final int min, int max, int value, final Fmt fmt, final Change ch) {
        label.setText(fmt.text(value));
        SeekBar sb = new SeekBar(a);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                label.setText(fmt.text(p + min));
                if (fromUser) { ch.set(p + min); a.applyDecSettings(); }
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { a.st.save(); }
        });
        box.addView(label);
        box.addView(sb);
        return sb;
    }

    static void cwPanel(final MainActivity a, final LinearLayout box) {
        final Store st = a.st;
        box.addView(Ui.small(a, Lang.T("Réglages CW (appliqués tout de suite) :", "CW settings (applied immediately):")));
        final CheckBox autoTone = Ui.check(a, Lang.T("Tonalité automatique", "Automatic tone"), st.cwAutoTone);
        final CheckBox autoSpeed = Ui.check(a, Lang.T("Vitesse automatique", "Automatic speed"), st.cwAutoSpeed);
        autoTone.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.cwAutoTone = on; st.save(); a.applyCwSettings(); }
        });
        autoSpeed.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.cwAutoSpeed = on; st.save(); a.applyCwSettings(); }
        });
        box.addView(autoTone);
        seek(a, box, Ui.label(a, ""), 200, 1200, st.cwTone, new Fmt() { public String text(int v) { return Lang.F("Tonalité : {0} Hz", "Tone: {0} Hz", v); } },
            new Change() { public void set(int v) { st.cwTone = v; st.cwAutoTone = false; autoTone.setChecked(false); } });
        final CheckBox autoBw = Ui.check(a, Lang.T("Filtre adapté à la vitesse", "Filter follows the speed"), st.cwAutoBw);
        autoBw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.cwAutoBw = on; st.save(); a.applyCwSettings(); }
        });
        box.addView(autoBw);
        seek(a, box, Ui.label(a, ""), 30, 400, st.cwBw, new Fmt() { public String text(int v) { return Lang.F("Filtre : {0} Hz", "Filter: {0} Hz", v); } },
            new Change() { public void set(int v) { st.cwBw = v; } });
        box.addView(autoSpeed);
        seek(a, box, Ui.label(a, ""), 5, 60, st.cwWpm, new Fmt() { public String text(int v) { return Lang.F("Vitesse : {0} mots/min", "Speed: {0} WPM", v); } },
            new Change() { public void set(int v) { st.cwWpm = v; st.cwAutoSpeed = false; autoSpeed.setChecked(false); } });
        seek(a, box, Ui.label(a, ""), 30, 80, st.cwThr, new Fmt() { public String text(int v) { return Lang.F("Seuil de détection : {0} %", "Detection threshold: {0} %", v); } },
            new Change() { public void set(int v) { st.cwThr = v; } });
        seek(a, box, Ui.label(a, ""), 3, 30, st.cwSq, new Fmt() { public String text(int v) { return Lang.F("Squelch (signal / bruit) : ×{0}", "Squelch (signal / noise): ×{0}", v); } },
            new Change() { public void set(int v) { st.cwSq = v; } });
        seek(a, box, Ui.label(a, ""), 15, 40, st.cwChar, new Fmt() { public String text(int v) { return Lang.F("Fin de lettre : {0} points", "Letter gap: {0} dots", String.format(Locale.US, "%.1f", v / 10.0)); } },
            new Change() { public void set(int v) { st.cwChar = v; } });
        seek(a, box, Ui.label(a, ""), 40, 90, st.cwWord, new Fmt() { public String text(int v) { return Lang.F("Fin de mot : {0} points", "Word gap: {0} dots", String.format(Locale.US, "%.1f", v / 10.0)); } },
            new Change() { public void set(int v) { st.cwWord = v; } });
        final CheckBox afc = Ui.check(a, Lang.T("Suivi de dérive (AFC)", "Drift tracking (AFC)"), st.cwAfc);
        afc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.cwAfc = on; st.save(); a.applyCwSettings(); }
        });
        box.addView(afc);
        seek(a, box, Ui.label(a, ""), 0, 2, st.cwAgc, new Fmt() { public String text(int v) { return Lang.F("AGC (évanouissements) : {0}", "AGC (fading): {0}", v == 0 ? Lang.T("lent", "slow") : v == 1 ? Lang.T("moyen", "medium") : Lang.T("rapide", "fast")); } },
            new Change() { public void set(int v) { st.cwAgc = v; } });
        box.addView(Ui.button(a, Lang.T("Valeurs par défaut", "Default values"), new View.OnClickListener() {
            public void onClick(View v) {
                st.cwDefaults();
                st.save();
                a.applyCwSettings();
                box.removeAllViews();
                cwPanel(a, box);
            }
        }));
    }

    interface Pick { void chose(int index); }

    // Une ligne « texte + liste déroulante ».
    static void choice(final MainActivity a, LinearLayout box, String label, String[] items, int selected, final Pick p) {
        box.addView(Ui.small(a, label));
        java.util.ArrayList<String> l = new java.util.ArrayList<String>(java.util.Arrays.asList(items));
        final android.widget.Spinner sp = Ui.spinner(a, l);
        sp.setSelection(selected);
        final int[] cur = { selected };
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> par, View v, int pos, long id) { if (pos != cur[0]) { cur[0] = pos; p.chose(pos); } }
            public void onNothingSelected(android.widget.AdapterView<?> par) { }
        });
        box.addView(sp);
    }

    static final int[] RTTY_BAUDS = { 0, 4545, 5000, 5688, 7500, 10000 };
    static final int[] RTTY_SHIFTS = { 0, 85, 170, 200, 425, 450, 850 };

    static int indexOf(int[] a, int v) { for (int i = 0; i < a.length; i++) if (a[i] == v) return i; return 0; }

    static void rttyPanel(final MainActivity a, final LinearLayout box) {
        final Store st = a.st;
        box.addView(Ui.small(a, Lang.T("Réglages RTTY (appliqués tout de suite) :", "RTTY settings (applied immediately):")));
        String auto = Lang.T("Automatique", "Automatic");
        choice(a, box, Lang.T("Vitesse (débit en bauds)", "Speed (baud rate)"), new String[] { auto, "45,45", "50", "56,88", "75", "100" }, indexOf(RTTY_BAUDS, st.rttyBaud),
            new Pick() { public void chose(int i) { st.rttyBaud = RTTY_BAUDS[i]; st.save(); a.applyRttySettings(); } });
        choice(a, box, Lang.T("Décalage entre les deux tonalités", "Shift between the two tones"), new String[] { auto, "85 Hz", "170 Hz", "200 Hz", "425 Hz", "450 Hz", "850 Hz" }, indexOf(RTTY_SHIFTS, st.rttyShift),
            new Pick() { public void chose(int i) { st.rttyShift = RTTY_SHIFTS[i]; st.save(); a.applyRttySettings(); } });
        choice(a, box, Lang.T("Polarité", "Polarity"), new String[] { auto, Lang.T("Normale (mark = tonalité basse)", "Normal (mark = low tone)"), Lang.T("Inversée", "Reversed") }, st.rttyPol,
            new Pick() { public void chose(int i) { st.rttyPol = i; st.save(); a.applyRttySettings(); } });
        final CheckBox autoFreq = Ui.check(a, Lang.T("Fréquence automatique", "Automatic frequency"), st.rttyFreq == 0);
        autoFreq.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.rttyFreq = on ? 0 : 2125; st.save(); a.applyRttySettings(); }
        });
        box.addView(autoFreq);
        seek(a, box, Ui.label(a, ""), 500, 2900, st.rttyFreq == 0 ? 2125 : st.rttyFreq, new Fmt() { public String text(int v) { return Lang.F("Tonalité basse : {0} Hz", "Low tone: {0} Hz", v); } },
            new Change() { public void set(int v) { st.rttyFreq = v; autoFreq.setChecked(false); a.applyRttySettings(); } });
        final CheckBox afc = Ui.check(a, Lang.T("Suivi de dérive (AFC)", "Drift tracking (AFC)"), st.rttyAfc);
        afc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.rttyAfc = on; st.save(); a.applyRttySettings(); }
        });
        box.addView(afc);
        final CheckBox uos = Ui.check(a, Lang.T("Une espace ramène en lettres (UOS)", "A space returns to letters (UOS)"), st.rttyUos);
        uos.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.rttyUos = on; st.save(); a.applyRttySettings(); }
        });
        box.addView(uos);
        seek(a, box, Ui.label(a, ""), 5, 40, st.rttySq, new Fmt() { public String text(int v) { return Lang.F("Squelch (signal / bruit) : ×{0}", "Squelch (signal / noise): ×{0}", v); } },
            new Change() { public void set(int v) { st.rttySq = v; } });
        box.addView(Ui.button(a, Lang.T("Valeurs par défaut", "Default values"), new View.OnClickListener() {
            public void onClick(View v) {
                st.rttyDefaults();
                st.save();
                a.applyRttySettings();
                box.removeAllViews();
                rttyPanel(a, box);
            }
        }));
    }

    static void pskPanel(final MainActivity a, final LinearLayout box) {
        final Store st = a.st;
        box.addView(Ui.small(a, Lang.T("Réglages PSK31 (appliqués tout de suite) :", "PSK31 settings (applied immediately):")));
        final CheckBox auto = Ui.check(a, Lang.T("Fréquence automatique (signal le plus fort)", "Automatic frequency (strongest signal)"), st.pskAuto);
        auto.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.pskAuto = on; st.save(); a.applyPskSettings(); }
        });
        box.addView(auto);
        seek(a, box, Ui.label(a, ""), 300, 2900, st.pskFreq, new Fmt() { public String text(int v) { return Lang.F("Fréquence audio : {0} Hz", "Audio frequency: {0} Hz", v); } },
            new Change() { public void set(int v) { st.pskFreq = v; st.pskAuto = false; auto.setChecked(false); } });
        choice(a, box, Lang.T("Vitesse", "Speed"), new String[] { "PSK31 (31,25 bauds)", "PSK63 (62,5 bauds)" }, st.pskBaud >= 47 ? 1 : 0,
            new Pick() { public void chose(int i) { st.pskBaud = i == 1 ? 63 : 31; st.save(); a.applyPskSettings(); } });
        final CheckBox afc = Ui.check(a, Lang.T("Suivi de dérive (AFC)", "Drift tracking (AFC)"), st.pskAfc);
        afc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.pskAfc = on; st.save(); a.applyPskSettings(); }
        });
        box.addView(afc);
        seek(a, box, Ui.label(a, ""), 60, 98, st.pskSq, new Fmt() { public String text(int v) { return Lang.F("Squelch (cohérence de phase) : {0} %", "Squelch (phase coherence): {0} %", v); } },
            new Change() { public void set(int v) { st.pskSq = v; } });
        box.addView(Ui.button(a, Lang.T("Valeurs par défaut", "Default values"), new View.OnClickListener() {
            public void onClick(View v) {
                st.pskDefaults();
                st.save();
                a.applyPskSettings();
                box.removeAllViews();
                pskPanel(a, box);
            }
        }));
    }

    static void sstvPanel(final MainActivity a, final LinearLayout box) {
        final Store st = a.st;
        final CheckBox save = Ui.check(a, Lang.T("Enregistrer automatiquement les images (Images › PhoneSDRLink)", "Save the pictures automatically (Pictures › PhoneSDRLink)"), st.sstvSave);
        save.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.sstvSave = on; st.save(); }
        });
        box.addView(save);
        box.addView(Ui.button(a, Lang.T("Ouvrir le dossier des images enregistrées", "Open the folder of saved pictures"), new View.OnClickListener() {
            public void onClick(View v) { a.openSstvFolder(); }
        }));
    }
}
