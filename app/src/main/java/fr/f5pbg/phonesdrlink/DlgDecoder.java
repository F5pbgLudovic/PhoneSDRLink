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
    static ScrollView cwBox, rttyBox, pskBox, sstvBox, js8Box;
    static LinearLayout hiBox;      // champ « Surbrillance » (tous les modes qui font défiler du texte)
    static View sstvImgBox;

    // Panneaux de réglages et image selon le décodeur choisi.
    static void showPanels(int mode) {
        if (cwBox != null) cwBox.setVisibility(mode == MainActivity.DEC_CW ? View.VISIBLE : View.GONE);
        if (rttyBox != null) rttyBox.setVisibility(mode == MainActivity.DEC_RTTY ? View.VISIBLE : View.GONE);
        if (pskBox != null) pskBox.setVisibility(mode == MainActivity.DEC_PSK ? View.VISIBLE : View.GONE);
        if (sstvBox != null) sstvBox.setVisibility(mode == MainActivity.DEC_SSTV ? View.VISIBLE : View.GONE);
        if (hiBox != null) hiBox.setVisibility(mode == MainActivity.DEC_SSTV || mode == MainActivity.DEC_RADE || mode == MainActivity.DEC_RADE2 ? View.GONE : View.VISIBLE);
        if (js8Box != null) js8Box.setVisibility(mode == MainActivity.DEC_JS8 ? View.VISIBLE : View.GONE);
        if (sstvImgBox != null) sstvImgBox.setVisibility(mode == MainActivity.DEC_SSTV ? View.VISIBLE : View.GONE);
    }

    // Ordre des modes dans la liste déroulante.
    static final int[] MODES = { MainActivity.DEC_FT8, MainActivity.DEC_FT4, MainActivity.DEC_FT2, MainActivity.DEC_WSPR, MainActivity.DEC_JT9, MainActivity.DEC_JS8, MainActivity.DEC_CW,
        MainActivity.DEC_RTTY, MainActivity.DEC_PSK, MainActivity.DEC_SSTV, MainActivity.DEC_RADE, MainActivity.DEC_RADE2 };
    static int lastSel = MainActivity.DEC_FT8;     // dernier mode choisi dans la liste (gardé tant que l'application tourne)
    static Runnable onState;                       // appelé quand le décodeur démarre ou s'arrête : remet à jour les boutons

    // Texte du bouton « i » : change avec le mode choisi.
    static String info(int mode) {
        String head, cols;
        switch (mode) {
            case MainActivity.DEC_FT8: case MainActivity.DEC_FT4:
                head = Lang.T("Mode USB ou DATA, sur une fréquence FT8 (7.074, 14.074 MHz…) ou FT4 (7.0475, 14.080 MHz…). L'heure du téléphone doit être exacte.", "USB or DATA mode, on an FT8 (7.074, 14.074 MHz…) or FT4 (7.0475, 14.080 MHz…) frequency. The phone clock must be accurate.");
                cols = Lang.T("Heure UTC · DT (s) · fréquence audio (Hz) · message. Les deux périodes (paire et impaire) sont affichées en deux couleurs.", "UTC time · DT (s) · audio frequency (Hz) · message. The two periods (even and odd) are shown in two colours.");
                break;
            case MainActivity.DEC_FT2:
                head = Lang.T("Mode USB ou DATA, sur une fréquence FT2 (3.578, 7.052, 14.084 MHz…). FT2 est un FT4 deux fois plus rapide : tranches de 3,75 s, messages de 2,5 s, bande de 170 Hz. L'heure du téléphone doit être exacte. Réception seule ; les signaux faibles se décodent moins bien qu'en FT4 (symboles plus courts).", "USB or DATA mode, on an FT2 frequency (3.578, 7.052, 14.084 MHz…). FT2 is FT4 at double speed: 3.75 s slots, 2.5 s messages, 170 Hz wide. The phone clock must be accurate. Reception only; weak signals decode less well than in FT4 (shorter symbols).");
                cols = Lang.T("Heure UTC · DT (s) · fréquence audio (Hz) · message. Les deux périodes (paire et impaire) sont affichées en deux couleurs.", "UTC time · DT (s) · audio frequency (Hz) · message. The two periods (even and odd) are shown in two colours.");
                break;
            case MainActivity.DEC_WSPR:
                head = Lang.T("Mode USB, sur une fréquence WSPR (14.0956, 7.0386, 10.1387 MHz… : fréquence de la porteuse + 1500 Hz). Tranches de 2 minutes ; l'heure du téléphone doit être exacte ; premiers résultats à la fin de la première tranche.", "USB mode, on a WSPR frequency (14.0956, 7.0386, 10.1387 MHz…: carrier frequency + 1500 Hz). 2-minute slots; the phone clock must be accurate; first results at the end of the first slot.");
                cols = Lang.T("Heure UTC · SNR (dB) · DT (s) · fréquence audio (Hz) · indicatif, locator, puissance (dBm).", "UTC time · SNR (dB) · DT (s) · audio frequency (Hz) · callsign, locator, power (dBm).");
                break;
            case MainActivity.DEC_JT9:
                head = Lang.T("Mode USB, sur une fréquence JT9 (14.078, 7.078, 3.578 MHz…). Tranches d'une minute ; l'heure du téléphone doit être exacte.", "USB mode, on a JT9 frequency (14.078, 7.078, 3.578 MHz…). 1-minute slots; the phone clock must be accurate.");
                cols = Lang.T("Heure UTC · SNR (dB) · DT (s) · fréquence audio (Hz) · message.", "UTC time · SNR (dB) · DT (s) · audio frequency (Hz) · message.");
                break;
            case MainActivity.DEC_JS8:
                head = Lang.T("Mode USB, sur une fréquence JS8Call (14.078, 7.078, 3.578 MHz…). Réception seule : trames balise (HEARTBEAT), messages dirigés, texte libre. Choisissez la vitesse de l'émetteur (liste « Vitesse » sous le texte) : Lent (tranches de 30 s), Normal (15 s), Rapide (10 s), Turbo (6 s), Ultra (4 s) ; elle doit être la même que celle de la station écoutée. L'heure du téléphone doit être exacte.", "USB mode, on a JS8Call frequency (14.078, 7.078, 3.578 MHz…). Receive only: heartbeat frames, directed messages, free text. Choose the transmitter's speed (the Speed list under the text): Slow (30 s slots), Normal (15 s), Fast (10 s), Turbo (6 s), Ultra (4 s); it must be the same as the station you listen to. The phone clock must be accurate.");
                cols = Lang.T("Heure UTC · SNR (dB) · DT (s) · fréquence audio (Hz) · trame (indicatif émetteur : destinataire ou commande, texte). Les deux périodes (paire et impaire) sont affichées en deux couleurs.", "UTC time · SNR (dB) · DT (s) · audio frequency (Hz) · frame (sender callsign: addressee or command, text). The two periods (even and odd) are shown in two colours.");
                break;
            case MainActivity.DEC_CW:
                head = Lang.T("Mode CW ou USB, sur un signal CW. Réglages (tonalité, filtre, vitesse, seuils…) sous le texte.", "CW or USB mode, on a CW signal. Settings (tone, filter, speed, thresholds…) under the text.");
                cols = Lang.T("Heure UTC · texte.", "UTC time · text.");
                break;
            case MainActivity.DEC_RTTY:
                head = Lang.T("Mode USB ou LSB (au moins 500 Hz de bande), sur un signal RTTY (14.080 à 14.099 MHz…). Réglages sous le texte.", "USB or LSB mode (at least 500 Hz bandwidth), on an RTTY signal (14.080 to 14.099 MHz…). Settings under the text.");
                cols = Lang.T("Heure UTC · texte.", "UTC time · text.");
                break;
            case MainActivity.DEC_PSK:
                head = Lang.T("Mode USB, sur un signal PSK31 (14.070, 7.035, 3.580 MHz…) ; la fréquence audio est trouvée automatiquement (ou réglée à la main). PSK63 possible.", "USB mode, on a PSK31 signal (14.070, 7.035, 3.580 MHz…); the audio frequency is found automatically (or set by hand). PSK63 possible.");
                cols = Lang.T("Heure UTC · texte.", "UTC time · text.");
                break;
            case MainActivity.DEC_SSTV:
                head = Lang.T("Mode USB (au moins 2,4 kHz de bande), sur une fréquence SSTV (14.230, 7.033, 3.730 MHz…) ; le mode de l'image est reconnu tout seul, l'image se construit ligne par ligne, puis elle est enregistrée à la fin.", "USB mode (at least 2.4 kHz bandwidth), on an SSTV frequency (14.230, 7.033, 3.730 MHz…); the picture mode is recognised automatically, the picture builds up line by line and is saved when it ends.");
                cols = Lang.T("Heure UTC · mode de l'image.", "UTC time · picture mode.");
                cols += "\n\n" + Lang.T("Sensibilité (1 à 10) : plus le chiffre est haut, plus le décodeur accepte les signaux faibles (code de début, synchros, lignes), au risque de quelques fausses détections.", "Sensitivity (1 to 10): the higher the number, the weaker the signals the decoder accepts (start code, sync pulses, lines), at the risk of a few false detections.") + "\n\n" + Lang.T("Perte data (1 à 10) : combien de temps le décodeur continue une image quand le signal disparaît (3 s au chiffre 1, 12 s au 4, 120 s au 10). Si le signal revient dans ce temps, l'image reprend à la bonne ligne et les lignes perdues restent grises. Passé ce temps, l'image est enregistrée telle quelle et le décodeur se remet à l'écoute (il retrouve alors le mode tout seul).", "Data loss (1 to 10): how long the decoder keeps going with a picture when the signal disappears (3 s at 1, 12 s at 4, 120 s at 10). If the signal comes back in that time, the picture resumes at the right line and the lost lines stay grey. After that time the picture is saved as it is and the decoder listens again (it then finds the mode by itself).");
                break;
            case MainActivity.DEC_RADE2:
                head = Lang.T("Mode USB, sur un signal FreeDV RADE V2 (FreeDV 3.0). Attention : la V2 est encore en développement (forme d'onde et réseaux de neurones susceptibles de changer, non compatible avec la V1 de FreeDV 2.x) : cette version reprend un état des sources du 6 novembre 2026 et ne décodera pas forcément les signaux V2 à venir. La parole décodée remplace le son du WebSDR.", "USB mode, on a FreeDV RADE V2 signal (FreeDV 3.0). Warning: V2 is still in development (waveform and neural networks may change, not compatible with FreeDV 2.x's V1): this version uses a snapshot of the sources of 6 November 2026 and may not decode future V2 signals. The decoded speech replaces the WebSDR sound.");
                cols = null;
                break;
            default:
                head = Lang.T("Mode USB, sur un signal FreeDV RADE (14.236 MHz…) ; la parole décodée remplace le son du WebSDR.", "USB mode, on a FreeDV RADE signal (14.236 MHz…); the decoded speech replaces the WebSDR sound.");
                cols = null;
        }
        return Lang.T("Le son du WebSDR est analysé en continu, même coupé.", "The WebSDR sound is analysed continuously, even when muted.") + "\n\n" + head + (cols != null ? "\n\n" + cols : "");
    }

    // Zone de réglages : prend la hauteur de son contenu, plafonnée pour laisser de la place au texte reçu (ascenseur au-delà).
    static final class Capped extends ScrollView {
        final int cap;
        Capped(MainActivity a, int capPx) { super(a); cap = capPx; }
        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(cap, View.MeasureSpec.AT_MOST));
        }
    }

    static Capped capped(MainActivity a, LinearLayout content) {
        Capped s = new Capped(a, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.50f));
        s.addView(content);
        return s;
    }

    static void show(final MainActivity a) {
        LinearLayout col = Ui.column(a);
        final TextView state = Ui.label(a, "");
        final Dialog[] dd = new Dialog[1];
        final int[] sel = { a.decMode != 0 ? a.decMode : lastSel };
        // Ligne 1 : liste des modes, bouton du mode (vert en marche), STOP (rouge en marche, grisé à l'arrêt)
        LinearLayout r1 = Ui.row(a), r2 = Ui.row(a);
        final String[] names = new String[MODES.length];
        int selIdx = 0;
        for (int i = 0; i < MODES.length; i++) { names[i] = MainActivity.decName(MODES[i]); if (MODES[i] == sel[0]) selIdx = i; }
        final android.widget.Spinner sp = Ui.spinner(a, new java.util.ArrayList<String>(java.util.Arrays.asList(names)));
        sp.setSelection(selIdx);
        final Button go = Ui.button(a, "", null);
        final Button stop = Ui.button(a, "STOP", null);
        r1.addView(sp, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.7f, a, 2));
        r1.addView(go, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f, a, 2));
        r1.addView(stop, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f, a, 2));
        final Runnable refresh = new Runnable() {
            public void run() {
                go.setText(MainActivity.decName(sel[0]));
                Ui.paint(go, a.decMode == sel[0], Ui.on());
                boolean running = a.decMode != 0;
                stop.setEnabled(running);
                stop.setAlpha(running ? 1f : 0.45f);
                Ui.paint(stop, running, Ui.danger());
            }
        };
        onState = refresh;
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (a.decMode == sel[0]) return;   // déjà en marche
                showPanels(sel[0]);
                a.digAutoTune(sel[0]);
                a.decoderStart(sel[0]);
                refresh.run();
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (a.decMode == 0) return;
                a.decoderStopUser();
                refresh.run();
                Ui.toast(a, Lang.T("Décodeur arrêté", "Decoder stopped"));
            }
        });
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> par, View v, int pos, long id) {
                boolean changed = MODES[pos] != sel[0];   // l'appel de départ de la liste (rien de changé) ne lance rien
                sel[0] = MODES[pos];
                lastSel = sel[0];
                showPanels(sel[0]);
                if (changed) { a.digAutoTune(sel[0]); a.decoderStart(sel[0]); }      // choisir un mode dans la liste le lance (le bouton passe au vert)
                refresh.run();
            }
            public void onNothingSelected(android.widget.AdapterView<?> par) { }
        });
        // Ligne 2 : info (petit) et réglages
        r2.addView(Ui.button(a, "ⓘ Info", new View.OnClickListener() {
            public void onClick(View v) {
                TextView t = Ui.label(a, info(sel[0]));
                t.setTextSize(14);
                LinearLayout c = Ui.column(a);
                c.addView(Ui.scroll(a, t), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                final Dialog di = Ui.window(a, MainActivity.decName(sel[0]), c, false);
                c.addView(Ui.button(a, "OK", new View.OnClickListener() { public void onClick(View x) { di.dismiss(); } }));
                di.show();
            }
        }), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f, a, 2));
        r2.addView(Ui.button(a, Lang.T("Réglages (SET)…", "Settings (SET)…"), new View.OnClickListener() {
            public void onClick(View v) { if (dd[0] != null) dd[0].dismiss(); DlgSettings.show(a); }
        }), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f, a, 2));
        col.addView(r1);
        col.addView(r2);
        final CheckBox ov = Ui.check(a, Lang.T("Afficher les derniers messages sur l'écran principal", "Show the latest messages on the main screen"), a.st.decOverlay);
        ov.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { a.st.decOverlay = on; a.st.save(); a.decRefresh(); }
        });
        col.addView(ov);
        hiBox = Ui.row(a);
        hiBox.addView(Ui.small(a, Lang.T("Surbrillance", "Highlight")), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        // Zone d'affichage (pas de saisie sur place, donc pas de curseur qui clignote) : un appui ouvre une fenêtre de saisie avec Valider / Retour.
        final TextView hi = new TextView(a);
        hi.setTextSize(14);
        hi.setTextColor(Ui.fg());
        hi.setHintTextColor(Ui.sub());
        hi.setHint("F5PBG, DL1ABC, CQ DX…");
        hi.setSingleLine(true);
        hi.setEllipsize(android.text.TextUtils.TruncateAt.END);
        hi.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        android.graphics.drawable.GradientDrawable hiBg = new android.graphics.drawable.GradientDrawable();
        hiBg.setColor(Ui.bg());
        hiBg.setStroke(Ui.dp(a, 1), Ui.sub());
        hiBg.setCornerRadius(Ui.dp(a, 6));
        hi.setBackground(hiBg);
        hi.setText(a.st.decHi);
        hi.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Ui.input(a, Lang.T("Surbrillance : indicatifs ou textes à repérer, séparés par des virgules", "Highlight: callsigns or texts to spot, separated by commas"), "F5PBG, DL1ABC, CQ DX…", a.st.decHi,
                    "✓", "✕", new Ui.Text() {
                        public void got(String t) { a.st.decHi = t.trim(); a.st.save(); hi.setText(a.st.decHi); a.decRefresh(); }
                    });
            }
        });
        hiBox.addView(hi, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(hiBox);
        col.addView(state);
        // SSTV : l'image reçue (se remplit ligne par ligne)
        final ImageView pic = new ImageView(a);
        pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pic.setBackgroundColor(0xFF101010);
        a.sstvDlgImg = pic;
        if (a.sstvBmp != null) pic.setImageBitmap(a.sstvBmp);
        sstvImgBox = pic;
        col.addView(pic, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (a.getResources().getDisplayMetrics().heightPixels * 0.34f)));
        pic.setVisibility(sel[0] == MainActivity.DEC_SSTV ? View.VISIBLE : View.GONE);
        // Fenêtre de réception (encadrée) : prend toute la place libre au-dessus des réglages
        final TextView log = new TextView(a);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(11);
        log.setTextColor(Ui.fg());
        log.setHintTextColor(Ui.sub());
        log.setHint(Lang.T("Texte reçu (cadre de réception) : il s'affichera ici.", "Received text (reception frame): it will appear here."));
        log.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
        final ScrollView sv = Ui.scroll(a, log);
        LinearLayout frame = new LinearLayout(a);
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setColor(Ui.bg());
        border.setStroke(Ui.dp(a, 2), Ui.accent());
        border.setCornerRadius(Ui.dp(a, 8));
        frame.setBackground(border);
        frame.setPadding(Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3), Ui.dp(a, 3));
        frame.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.setMinimumHeight(Ui.dp(a, 110));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        flp.setMargins(0, Ui.dp(a, 4), 0, Ui.dp(a, 4));
        col.addView(frame, flp);
        // Réglages sous le texte reçu : hauteur adaptée au contenu
        LinearLayout cw = Ui.column(a);
        cwPanel(a, cw);
        cwBox = capped(a, cw);
        col.addView(cwBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout rt = Ui.column(a);
        rttyPanel(a, rt);
        rttyBox = capped(a, rt);
        col.addView(rttyBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout ps = Ui.column(a);
        pskPanel(a, ps);
        pskBox = capped(a, ps);
        col.addView(pskBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout ss = Ui.column(a);
        sstvPanel(a, ss);
        sstvBox = capped(a, ss);
        col.addView(sstvBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout js = Ui.column(a);
        js8Panel(a, js);
        js8Box = capped(a, js);
        col.addView(js8Box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showPanels(sel[0]);
        refresh.run();
        final Dialog d = Ui.window(a, Lang.T("Décodeur", "Decoder"), col, true);
        dd[0] = d;
        a.decView = log;
        a.decScroll = sv;
        a.decState = state;
        state.setText(a.decStatus);
        a.decRefresh();
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface x) { a.decView = null; a.decScroll = null; a.decState = null; a.sstvDlgImg = null; cwBox = null; rttyBox = null; pskBox = null; sstvBox = null; js8Box = null; hiBox = null; sstvImgBox = null; onState = null; }
        });
        d.show();
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
        TextView lbl = Ui.small(a, label);
        lbl.setPadding(0, Ui.dp(a, 8), 0, 0);
        box.addView(lbl);
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

    // Liste déroulante de chiffres ; p est appelé quand le choix change (pas à l'affichage).
    static android.widget.Spinner numSpinner(MainActivity a, String[] items, int selected, final Pick p) {
        final android.widget.Spinner sp = Ui.spinner(a, new java.util.ArrayList<String>(java.util.Arrays.asList(items)));
        sp.setSelection(selected);
        final int[] cur = { selected };
        sp.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> par, View v, int pos, long id) { if (pos != cur[0]) { cur[0] = pos; p.chose(pos); } }
            public void onNothingSelected(android.widget.AdapterView<?> par) { }
        });
        return sp;
    }

    static void js8Panel(final MainActivity a, final LinearLayout box) {
        final Store st = a.st;
        String[] names = { Lang.T("Lent (30 s)", "Slow (30 s)"), Lang.T("Normal (15 s)", "Normal (15 s)"), Lang.T("Rapide (10 s)", "Fast (10 s)"), "Turbo (6 s)", "Ultra (4 s)" };
        LinearLayout line = Ui.row(a);
        line.addView(Ui.small(a, Lang.T("Vitesse", "Speed")), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        line.addView(numSpinner(a, names, st.js8Speed, new Pick() { public void chose(int i) { st.js8Speed = i; st.save(); a.applyJs8Settings(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        box.addView(line);
    }

    static void sstvPanel(final MainActivity a, final LinearLayout box) {
        final Store st = a.st;
        // Une seule ligne de réglages : sensibilité (1 à 10) et perte data (1 à 10) ; leur effet est expliqué dans « Info ».
        LinearLayout line = Ui.row(a);
        String[] nums = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10" };
        line.addView(Ui.small(a, Lang.T("Sensibilité", "Sensitivity")), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        line.addView(numSpinner(a, nums, st.sstvSens - 1, new Pick() { public void chose(int i) { st.sstvSens = i + 1; st.save(); a.applySstvSettings(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        line.addView(Ui.small(a, Lang.T("Perte data", "Data loss")), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        line.addView(numSpinner(a, nums, st.sstvLoss - 1, new Pick() { public void chose(int i) { st.sstvLoss = i + 1; st.save(); a.applySstvSettings(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        box.addView(line);
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
