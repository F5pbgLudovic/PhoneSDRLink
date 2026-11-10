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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import java.io.File;
import java.io.OutputStream;

// Bouton SET : mode sombre, mise à jour, langue, notch automatique (DNF), réducteur de bruit (DNR), nom donné aux WebSDR,
// import / export des réglages, réinitialisation.
final class DlgSettings {
    static Dialog setDlg;   // fenêtre SET ouverte : elle reste dessous pendant import, export et réinitialisation, pour y revenir
    static void closeSet() { try { if (setDlg != null) setDlg.dismiss(); } catch (Exception e) { } setDlg = null; }

    static void show(final MainActivity a) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        final Dialog d = Ui.window(a, "SET", Ui.scroll(a, col), false);
        setDlg = d;

        TextView ver = Ui.label(a, "PhoneSDRLink V" + a.versionName() + " @F5PBG 2026");
        ver.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        ver.setTextColor(0xFFFFEF00);   // jaune canari
        ver.setBackground(Ui.round(0xFF1B2128, 10, a));
        ver.setPadding(Ui.dp(a, 12), Ui.dp(a, 8), Ui.dp(a, 12), Ui.dp(a, 8));
        col.addView(ver, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 0));
        Button decB = add(col, a, Lang.T("Décodeur FT8 / FT4 / WSPR / JT9 / CW / RTTY / PSK31 / SSTV / FreeDV…", "FT8 / FT4 / WSPR / JT9 / CW / RTTY / PSK31 / SSTV / FreeDV decoder…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); DlgDecoder.show(a); }
        });
        decB.setBackground(Ui.roundOutline(Ui.decBtn(), 10, a));
        decB.setTextColor(Ui.onColor(Ui.decBtn()));
        decB.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        final Button fdvB = add(col, a, "FreeDV auto", new View.OnClickListener() {
            public void onClick(View v) { showFreedv(a, (Button) v); }
        });
        Ui.paint(fdvB, st.freedvAuto, Ui.on());
        final Button ecoB = add(col, a, Lang.T("Mode économie", "Power saving mode"), new View.OnClickListener() {
            public void onClick(View v) { showEco(a, (Button) v); }
        });
        Ui.paint(ecoB, st.eco, Ui.on());
        // Boutons à un clic : vert = fonction active.
        final Button darkB = add(col, a, Lang.T("Mode sombre", "Dark mode"), null);
        Ui.paint(darkB, st.dark, Ui.on());
        darkB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { st.dark = !st.dark; st.save(); d.dismiss(); Ui.reopenSet = true; a.recreate(); }
        });
        final Button keepB = add(col, a, Lang.T("Interdire l'extinction de l'écran", "Keep the screen on"), null);
        Ui.paint(keepB, st.keepScreen, Ui.on());
        keepB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { st.keepScreen = !st.keepScreen; st.save(); a.applyKeepScreen(); Ui.paint(keepB, st.keepScreen, Ui.on()); }
        });
        final Button zoomB = add(col, a, Lang.T("Zoom du spectre (1 kHz par mm)", "Spectrum zoom (1 kHz per mm)"), null);
        Ui.paint(zoomB, st.spectrumZoom, Ui.on());
        zoomB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { st.spectrumZoom = !st.spectrumZoom; st.save(); if (st.spectrumZoom) a.applySpectrumZoom(); Ui.paint(zoomB, st.spectrumZoom, Ui.on()); }
        });
        // Notch et réducteur de bruit : le bouton ouvre une fenêtre (coche, niveau, explication).
        final Button dnfB = add(col, a, filterLabel(st, true), new View.OnClickListener() {
            public void onClick(View v) { showFilter(a, (Button) v, true); }
        });
        Ui.paint(dnfB, st.autoNotch, Ui.on());
        final Button dnrB = add(col, a, filterLabel(st, false), new View.OnClickListener() {
            public void onClick(View v) { showFilter(a, (Button) v, false); }
        });
        Ui.paint(dnrB, st.autoDnr, Ui.on());
        final Button relB = add(col, a, reloadLabel(st), new View.OnClickListener() {
            public void onClick(View v) { showReload(a, (Button) v); }
        });
        Ui.paint(relB, st.autoReload, Ui.on());
        final Button volB = add(col, a, Lang.T("Volume par WebSDR", "Volume per WebSDR"), new View.OnClickListener() {
            public void onClick(View v) { showVolSdr(a, (Button) v); }
        });
        Ui.paint(volB, st.volPerSdr, Ui.on());
        final Button vocB = add(col, a, Lang.T("Commande vocale du scan", "Voice command of the scan"), new View.OnClickListener() {
            public void onClick(View v) { showVoice(a, (Button) v); }
        });
        Ui.paint(vocB, st.voiceCmd && VoiceCmd.ready(a, st.language), Ui.on());
        final Button bufB = add(col, a, bufLabel(st), new View.OnClickListener() {
            public void onClick(View v) { showBuffer(a, (Button) v); }   // SET reste ouvert dessous : le retour arrière y ramène
        });
        Ui.paint(bufB, st.bufMs > 0, Ui.on());
        final Button headB = add(col, a, Lang.T("Casque / Molette", "Headset / Wheel"), new View.OnClickListener() {
            public void onClick(View v) { showHeadset(a, (Button) v); }
        });
        Ui.paint(headB, st.headsetStop, Ui.on());
        add(col, a, Lang.T("Couleurs…", "Colours…"), new View.OnClickListener() {
            public void onClick(View v) { showColors(a); }
        });

        add(col, a, Lang.T("Langue :", "Language:") + " " + name(st.language), new View.OnClickListener() {
            public void onClick(View v) {
                Ui.choose(a, Lang.T("Langue :", "Language:"), Lang.NAMES, new Ui.Index() {
                    public void chose(int i) { st.language = Lang.CODES[i]; st.save(); d.dismiss(); Ui.reopenSet = true; a.recreate(); }   // on revient dans SET
                });
            }
        });
        // Mise à jour : un seul bouton pleine largeur. Un appui sur « Auto » (dans le bouton, à droite) change la vérification au démarrage ;
        // un appui ailleurs lance la vérification, avec un bref éclair de couleur.
        FrameLayout upd = new FrameLayout(a);
        final Button updB = Ui.button(a, Lang.T("Vérifier les mises à jour", "Check for updates"), null);
        updB.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        updB.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                updB.setBackground(Ui.round(Ui.accent(), 10, a));
                updB.setTextColor(Ui.onColor(Ui.accent()));
                updB.postDelayed(new Runnable() { public void run() { Ui.paint(updB, false, 0); } }, 350);
                a.checkUpdate(true);
            }
        });
        upd.addView(updB, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        CheckBox autoBox = toggle(a, "Auto", st.checkUpdates, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.checkUpdates = on; st.save(); }
        });
        autoBox.setTextColor(Ui.btnTx());
        autoBox.setPadding(autoBox.getPaddingLeft(), 0, Ui.dp(a, 12), 0);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL);
        autoBox.setElevation(Ui.dp(a, 12));   // au-dessus du bouton (un Button a une élévation)
        upd.addView(autoBox, alp);
        col.addView(upd, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 3));
        add(col, a, Lang.T("Nom ou indicatif donné aux WebSDR", "Name or callsign for WebSDRs") + " : " + st.callsign, new View.OnClickListener() {
            public void onClick(View v) {
                Ui.input(a, Lang.T("Nom ou indicatif donné aux WebSDR qui en demandent un (KiwiSDR, websdr.org) :", "Name or callsign given to WebSDRs that ask for one (KiwiSDR, websdr.org):"),
                    "", st.callsign, new Ui.Text() {
                        public void got(String text) {
                            String c = Store.clean(text);
                            if (c.isEmpty()) { Ui.toast(a, Lang.T("L'indicatif ne peut pas être vide.", "The callsign cannot be empty.")); return; }
                            st.callsign = c;
                            st.save();
                            a.updateIdentity();
                            a.web.reload();
                            d.dismiss();   // SET est réaffiché pour montrer le nouveau nom
                            show(a);
                        }
                    });
            }
        });
        add(col, a, Lang.T("Ressources Processeur / RAM", "CPU / RAM resources"), new View.OnClickListener() {
            public void onClick(View v) { DlgPerf.show(a); }   // les mesures ne tournent que fenêtre ouverte
        });
        add(col, a, Lang.T("Exporter tous les réglages…", "Export all settings…"), new View.OnClickListener() {
            public void onClick(View v) { export(a); }
        });
        add(col, a, Lang.T("Importer des réglages…", "Import settings…"), new View.OnClickListener() {
            public void onClick(View v) { importSettings(a); }
        });
        Button reset = add(col, a, Lang.T("Réinitialiser le programme…", "Reset the program…"), new View.OnClickListener() {
            public void onClick(View v) { reset(a); }
        });
        Ui.paint(reset, false, 0);
        d.show();
    }


    // Bouton « FreeDV auto » : fenêtre avec les coches et l'explication ; le bouton de SET devient vert quand FreeDV auto est actif.
    static void showFreedv(final MainActivity a, final Button owner) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        Dialog fd = Ui.window(a, "FreeDV auto", Ui.scroll(a, col), false);
        col.addView(toggle(a, "FreeDV auto", st.freedvAuto, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.freedvAuto = on; st.save(); a.applyFreedvAuto(); Ui.paint(owner, on, Ui.on()); }
        }));
        col.addView(Ui.small(a, Lang.T("Écoute classique en permanence ; dès que des trames FreeDV RADE sont décodées, le son bascule sur la parole décodée. Coupé pendant qu'un autre décodeur est actif, remis en route dès qu'il s'arrête.", "Normal listening all the time; as soon as FreeDV RADE frames are decoded, the sound switches to the decoded speech. Suspended while another decoder is active, resumed as soon as it stops.")));
        TextView warn = Ui.small(a, Lang.T("⚠ Attention : le décodage FreeDV permanent augmente nettement la charge du processeur (de l'ordre de 10 à 25 % d'un cœur sur un smartphone OnePlus Nord) et peut faire chauffer le téléphone et vider la batterie.", "⚠ Warning: permanent FreeDV decoding noticeably raises the processor load (about 10 to 25 % of one core on a OnePlus Nord smartphone) and can make the phone hot and drain the battery."));
        warn.setTextColor(Ui.danger());
        col.addView(warn);
        TextView warn2 = Ui.small(a, Lang.T("Pour vérifier la charge : SET › Ressources Processeur / RAM.", "To check the load: SET › CPU / RAM resources."));
        warn2.setTextColor(Ui.danger());
        col.addView(warn2);
        // Garder le notch automatique (DNF) et le réducteur de bruit (DNR) pendant FreeDV auto (coupés par défaut en mode numérique).
        col.addView(toggle(a, "DNF + DNR", st.freedvFilters, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.freedvFilters = on; st.save(); a.applyAudio(); }
        }));
        Ui.closeOnTap(fd, col);
        Ui.flashButtons(col);
        fd.show();
    }

    // Texte des boutons DNF / DNR : le réglage (niveau 1 à 10, ou OFF) est visible dans le bouton.
    static String filterLabel(Store st, boolean dnf) {
        boolean on = dnf ? st.autoNotch : st.autoDnr;
        return (dnf ? Lang.T("Notch automatique (DNF)", "Auto notch (DNF)") : Lang.T("Réducteur de bruit (DNR)", "Noise reducer (DNR)")) + " : " + (on ? String.valueOf(dnf ? st.notchLevel : st.dnrLevel) : "OFF");
    }

    static String reloadLabel(Store st) {
        return Lang.T("Réinitialisation automatique WEBSDR", "Automatic WebSDR reset") + " : " + (st.autoReload ? st.reloadSec + " s" : "OFF");
    }

    // Réinitialisation automatique du WebSDR : coche, délai sans son (10 à 60 s) et explication.
    static void showReload(final MainActivity a, final Button owner) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        Dialog rd = Ui.window(a, Lang.T("Réinitialisation automatique WEBSDR", "Automatic WebSDR reset"), Ui.scroll(a, col), false);
        col.addView(toggle(a, Lang.T("Réinitialisation automatique WEBSDR", "Automatic WebSDR reset"), st.autoReload, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.autoReload = on; st.save(); a.applyAudio(); Ui.paint(owner, on, Ui.on()); owner.setText(reloadLabel(st)); }
        }));
        final int[] secs = new int[11];
        String[] names = new String[11];
        int si = 4;
        for (int i = 0; i < 11; i++) { secs[i] = 10 + 5 * i; names[i] = secs[i] + " s"; if (secs[i] == st.reloadSec) si = i; }
        DlgDecoder.choice(a, col, Lang.T("Délai sans son avant le rechargement", "Time without sound before the reload"), names, si,
            new DlgDecoder.Pick() { public void chose(int i) { st.reloadSec = secs[i]; st.save(); owner.setText(reloadLabel(st)); } });
        col.addView(Ui.small(a, Lang.T("Recharge le WebSDR quand le son est coupé depuis le délai choisi (de 10 à 60 s), par exemple après une coupure du serveur ou un time-out du WebSDR. Sans effet si le volume est à zéro ou en mode muet. Trois essais de suite au plus.", "Reloads the WebSDR when the sound has been lost for the chosen time (10 to 60 s), for example after a server drop or a WebSDR time-out. No effect if the volume is zero or muted. Three attempts in a row at most.")));
        Ui.closeOnTap(rd, col);
        rd.show();
    }

    // Fenêtre du notch automatique (dnf = true) ou du réducteur de bruit : coche, niveau de 1 à 10 et explication.
    static void showFilter(final MainActivity a, final Button owner, final boolean dnf) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        Dialog fd = Ui.window(a, dnf ? Lang.T("Notch automatique (DNF)", "Auto notch (DNF)") : Lang.T("Réducteur de bruit (DNR)", "Noise reducer (DNR)"), Ui.scroll(a, col), false);
        col.addView(levelRow(a, toggle(a, dnf ? Lang.T("Notch automatique (DNF)", "Auto notch (DNF)") : Lang.T("Réducteur de bruit (DNR)", "Noise reducer (DNR)"), dnf ? st.autoNotch : st.autoDnr, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { if (dnf) st.autoNotch = on; else st.autoDnr = on; st.save(); a.applyAudio(); Ui.paint(owner, on, Ui.on()); owner.setText(filterLabel(st, dnf)); }
        }), dnf ? st.notchLevel : st.dnrLevel, new Ui.Index() { public void chose(int i) { if (dnf) st.notchLevel = i + 1; else st.dnrLevel = i + 1; st.save(); a.applyAudio(); owner.setText(filterLabel(st, dnf)); } }));
        col.addView(Ui.small(a, dnf
            ? Lang.T("Repère les sifflements fixes (porteuses) dans le son et les atténue automatiquement. Le niveau, de 1 (réglage d'origine) à 10, augmente le nombre de sifflements traités et la force de l'action. Non appliqué en CW ni en mode numérique (DATA, décodeurs).", "Detects steady whistles (carriers) in the sound and attenuates them automatically. The level, from 1 (original setting) to 10, increases the number of whistles handled and the strength of the action. Not applied in CW or in digital modes (DATA, decoders).")
            : Lang.T("Atténue le souffle de fond quand il n'y a pas de signal utile. Le niveau, de 1 (léger) à 10 (fort), règle la force de la réduction ; un niveau élevé peut rendre la voix un peu métallique. Non appliqué pendant un décodeur numérique.", "Attenuates the background hiss when there is no useful signal. The level, from 1 (light) to 10 (strong), sets the strength of the reduction; a high level can make voices sound slightly metallic. Not applied while a digital decoder is running.")));
        Ui.closeOnTap(fd, col);
        fd.show();
    }

    // Volume par WebSDR : coche et explication ; le bouton de SET est vert quand la fonction est active.
    static void showVolSdr(final MainActivity a, final Button owner) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        Dialog vd = Ui.window(a, Lang.T("Volume par WebSDR", "Volume per WebSDR"), Ui.scroll(a, col), false);
        col.addView(toggle(a, Lang.T("Volume par WebSDR", "Volume per WebSDR"), st.volPerSdr, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.volPerSdr = on; st.save(); Ui.paint(owner, on, Ui.on()); }
        }));
        col.addView(Ui.small(a, Lang.T("Le volume réglé avec les touches du téléphone est mémorisé pour le WebSDR affiché, s'il est dans vos favoris. Quand on revient sur ce WebSDR, le volume du téléphone est remis tout seul à ce niveau ; un WebSDR sans niveau mémorisé garde le volume du moment. Le niveau est enregistré avec les favoris : il est exporté et importé avec eux.", "The volume set with the phone keys is remembered for the displayed WebSDR, if it is in your favorites. When you come back to this WebSDR, the phone volume is set back to that level automatically; a WebSDR without a remembered level keeps the current volume. The level is saved with the favorites: it is exported and imported with them.")));
        Ui.closeOnTap(vd, col);
        vd.show();
    }

    // Commande vocale du scan : coche, explication, et gestion du modèle de langue (téléchargé une seule fois, à la demande).
    static void showVoice(final MainActivity a, final Button owner) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        final Dialog vd = Ui.window(a, Lang.T("Commande vocale du scan", "Voice command of the scan"), Ui.scroll(a, col), false);
        final boolean[] quiet = { false };   // évite que la remise de la coche déclenche à nouveau son action
        final CheckBox box = toggle(a, Lang.T("Commande vocale du scan", "Voice command of the scan"), st.voiceCmd && VoiceCmd.ready(a, st.language), null);
        final TextView info = Ui.small(a, "");
        final Button get = Ui.button(a, "", null);
        final Button[] resetRef = { null };
        final Runnable refresh = new Runnable() {
            public void run() {
                boolean ok = VoiceCmd.ready(a, st.language);
                if (resetRef[0] != null) resetRef[0].setVisibility(ok ? View.VISIBLE : View.GONE);
                info.setText(ok ? Lang.T("Modèle vocal : installé.", "Voice model: installed.") : Lang.T("Modèle vocal : non téléchargé.", "Voice model: not downloaded."));
                get.setText(ok ? Lang.T("Supprimer le modèle vocal", "Delete the voice model") : Lang.T("Télécharger le modèle vocal (40 Mo)", "Download the voice model (40 MB)"));
                get.setEnabled(true);
            }
        };
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(final CompoundButton b, boolean on) {
                if (quiet[0]) return;
                if (!on) { st.voiceCmd = false; st.save(); a.voice.stop(); a.updateButtons(); Ui.paint(owner, false, Ui.on()); return; }
                if (!VoiceCmd.ready(a, st.language)) {   // le modèle d'abord
                    quiet[0] = true; b.setChecked(false); quiet[0] = false;
                    Ui.toast(a, Lang.T("Téléchargez d'abord le modèle vocal.", "Download the voice model first."));
                    return;
                }
                a.voicePermission(new MainActivity.BoolCb() {
                    public void done(boolean ok) {
                        if (ok) { st.voiceCmd = true; st.save(); Ui.paint(owner, true, Ui.on()); if (a.scanning) a.voiceStart(); }
                        else { quiet[0] = true; b.setChecked(false); quiet[0] = false; Ui.toast(a, Lang.T("Accès au micro refusé.", "Microphone access refused.")); }
                    }
                });
            }
        });
        col.addView(box);
        final String[] spc = VoiceCmd.spec(st.language);
        col.addView(Ui.small(a, Lang.T("Pendant un scan, le micro écoute quatre mots : « stop » met le scan en pause, « start » le reprend, « plus un » avance d'un pas de la réglette et « moins un » recule d'un pas. La reconnaissance se fait dans le téléphone, sans Internet. Le micro n'est ouvert que pendant un scan (et 5 minutes après sa pause), application à l'écran. Il entend aussi le haut-parleur : avec un casque, la fiabilité est bien meilleure. L'écoute augmente la charge du processeur et de la mémoire.", "During a scan, the microphone listens for four words: \"stop\" pauses the scan, \"start\" resumes it, \"plus un\" moves one step of the tuning bar forward and \"moins un\" one step back. Recognition runs inside the phone, without Internet. The microphone is open only during a scan (and for 5 minutes after it is paused), with the application on screen. It also hears the loudspeaker: with a headset, reliability is much better. Listening raises the processor and memory load.").replace("plus un", spc[4]).replace("moins un", spc[5])));
        col.addView(Ui.small(a, Lang.T("Le modèle vocal suit la langue de l'application, avec les ordres dans cette langue (en français : stop, start, plus un, moins un). Les langues sans modèle vocal utilisent l'anglais (stop, start, plus one, minus one). Si vous changez de langue, utilisez « Réinitialiser le modèle vocal ».", "The voice model follows the application language, with the orders in that language (in French: stop, start, plus un, moins un). Languages without a voice model use English (stop, start, plus one, minus one). If you change the language, use \"Reset the voice model\".")));
        col.addView(info);
        get.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (VoiceCmd.ready(a, st.language)) {
                    a.voice.stop();
                    VoiceCmd.deleteAll(a);
                    st.voiceCmd = false; st.save(); Ui.paint(owner, false, Ui.on());
                    quiet[0] = true; box.setChecked(false); quiet[0] = false;
                    refresh.run();
                    return;
                }
                get.setEnabled(false);
                VoiceCmd.download(a, st.language, new VoiceCmd.Progress() {
                    public void on(int pc, String err) {
                        if (err != null) { info.setText(Lang.T("Échec du téléchargement. Vérifiez la connexion puis réessayez.", "Download failed. Check the connection and try again.")); get.setEnabled(true); return; }
                        if (pc >= 100) { Ui.toast(a, Lang.T("Modèle vocal téléchargé.", "Voice model downloaded.")); refresh.run(); return; }
                        info.setText(Lang.F("Téléchargement… {0} %", "Downloading… {0}%", pc));
                    }
                });
            }
        });
        // Réinitialiser : supprime les modèles de toutes les langues puis télécharge celui de la langue actuelle (après un changement de langue).
        final Button reset = Ui.button(a, Lang.T("Réinitialiser le modèle vocal", "Reset the voice model"), new View.OnClickListener() {
            public void onClick(View v) {
                a.voice.stop();
                VoiceCmd.deleteAll(a);
                st.voiceCmd = false; st.save(); Ui.paint(owner, false, Ui.on());
                quiet[0] = true; box.setChecked(false); quiet[0] = false;
                refresh.run();
                get.performClick();   // télécharge le modèle de la langue actuelle
            }
        });
        reset.setVisibility(VoiceCmd.ready(a, st.language) ? View.VISIBLE : View.GONE);
        resetRef[0] = reset;
        col.addView(reset, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 3));
        col.addView(get, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 3));
        refresh.run();
        Ui.closeOnTap(vd, col);
        vd.show();
    }

    // Bouton « Mode économie » : fenêtre avec la coche et l'explication ; le bouton de SET devient vert quand le mode est actif.
    static void showEco(final MainActivity a, final Button owner) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        Dialog ed = Ui.window(a, Lang.T("Mode économie", "Power saving mode"), Ui.scroll(a, col), false);
        col.addView(toggle(a, Lang.T("Mode économie", "Power saving mode"), st.eco, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.eco = on; st.save(); a.applyAudio(); Ui.paint(owner, on, Ui.on()); }
        }));
        col.addView(Ui.small(a, Lang.T("Limite l'animation du spectre et de la cascade du WebSDR à environ 20 images par seconde au lieu de 60 : le processeur et la puce graphique travaillent moins, le téléphone chauffe moins et la batterie dure plus longtemps. Le son et les décodeurs ne sont pas touchés. Utile en 4G, sur batterie ou quand le téléphone chauffe ; le spectre est un peu moins fluide.", "Limits the WebSDR spectrum and waterfall animation to about 20 frames per second instead of 60: the processor and graphics chip work less, the phone runs cooler and the battery lasts longer. Sound and decoders are not affected. Useful on 4G, on battery or when the phone gets hot; the spectrum is slightly less smooth.")));
        Ui.closeOnTap(ed, col);
        Ui.flashButtons(col);
        ed.show();
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
            case 9: return c == Ui.freqBg();
            case 10: return false;
            case 11: return c == 0xFFFFFFFF || c == Ui.btn() || c == Ui.danger();   // le texte des boutons actifs est blanc
            case 12: return c == 0xFFFFFFFF || c == Ui.btn() || c == Ui.on();
            default: return c == Ui.btn();
        }
    }
    static void setColor(Store st, int id, int c) {
        switch (id) { case 0: st.colBtnBg = c; break; case 1: st.colBtnTx = c; break; case 2: st.colFreqTx = c; break; case 3: st.colFreqBg = c; break; case 4: st.colBarFg = c; break; case 5: st.colBarBg = c; break; case 7: st.colFt8A = c; break; case 8: st.colFt8B = c; break; case 9: st.colDec = c; break; case 10: st.colDecBtn = c; break; case 11: st.colOn = c; break; case 12: st.colDanger = c; break; default: st.colBg = c; }
    }

    // Bouton « Mémoire tampon réception » : mémoire tampon audio supplémentaire demandée au WebSDR (essai).
    static String bufLabel(Store st) {
        return Lang.T("Mémoire tampon réception", "Reception buffer") + " : " + (st.bufMs > 0 ? st.bufMs + " ms" : "OFF");
    }

    static void showBuffer(final MainActivity a, final Button owner) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        Dialog bd = Ui.window(a, Lang.T("Mémoire tampon réception", "Reception buffer"), Ui.scroll(a, col), false);
        final int[] bufMs = { 0, 100, 200, 300, 400, 500, 600, 700, 800, 900 };   // 0 = réglage d'origine, puis de 100 à 900 ms
        int bi = 0;
        for (int i = 0; i < bufMs.length; i++) if (Math.abs(bufMs[i] - st.bufMs) < Math.abs(bufMs[bi] - st.bufMs)) bi = i;   // valeur la plus proche (d'anciennes versions proposaient 250, 1000 ms…)
        DlgDecoder.choice(a, col, Lang.T("Mémoire tampon audio supplémentaire (essai)", "Extra audio buffer (test)"),
            new String[] { Lang.T("Aucune (réglage d'origine)", "None (original setting)"), "100 ms", "200 ms", "300 ms", "400 ms", "500 ms", "600 ms", "700 ms", "800 ms", "900 ms" }, bi,
            new DlgDecoder.Pick() { public void chose(int i) { st.bufMs = bufMs[i]; st.save(); owner.setText(bufLabel(st)); Ui.paint(owner, st.bufMs > 0, Ui.on()); a.applyBuffer(); } });
        col.addView(Ui.small(a, Lang.T("Demande au WebSDR une plus grande mémoire tampon de sortie audio : plus de latence, dans l'espoir de supprimer de petites coupures. Le WebSDR est rechargé quand on change la valeur.", "Asks the WebSDR for a larger audio output buffer: more latency, in the hope of removing small dropouts. The WebSDR is reloaded when the value is changed.")));
        Ui.closeOnTap(bd, col);
        bd.show();
    }

    // Bouton « Casque / Molette » : boutons de l'oreillette ou du casque, et bague / molette de doigt (délai et vitesse du défilement rapide).
    static void showHeadset(final MainActivity a, final Button owner) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        Dialog hd = Ui.window(a, Lang.T("Casque / Molette", "Headset / Wheel"), Ui.scroll(a, col), false);
        col.addView(toggle(a, Lang.T("Boutons du casque (arrêt / départ du scan)", "Headset buttons (stop / start the scan)"), st.headsetStop, new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) { st.headsetStop = on; st.save(); a.applyHeadset(); Ui.paint(owner, on, Ui.on()); }
        }));
        col.addView(Ui.small(a, Lang.T("Bouton de l'oreillette ou du casque : arrête le scan, puis le relance ; un double appui quand il est arrêté recule d'un pas. Décochée : les boutons du casque sont ignorés.", "Earphone or headset button: stops the scan, then starts it again; a double press while it is stopped goes back one step. Unticked: the headset buttons are ignored.")));
        final int[] ringMs = new int[17];
        String[] ringNames = new String[17];
        int ri = 7;
        for (int i = 0; i < 17; i++) { ringMs[i] = 100 + 50 * i; ringNames[i] = ringMs[i] + " ms"; if (ringMs[i] == st.ringMs) ri = i; }
        DlgDecoder.choice(a, col, Lang.T("Bague : délai entre deux coups de molette pour accélérer", "Finger ring: delay between two wheel strokes to accelerate"), ringNames, ri,
            new DlgDecoder.Pick() { public void chose(int i) { st.ringMs = ringMs[i]; st.save(); } });
        col.addView(Ui.small(a, Lang.T("Deux coups de molette dans le même sens séparés de moins que ce délai lancent le défilement rapide ; sinon, chaque coup fait un seul pas.", "Two wheel strokes in the same direction closer together than this delay start the fast scroll; otherwise each stroke is a single step.")));
        final int[] ringPct = new int[15];
        String[] ringPctNames = new String[15];
        int pi = 9;
        for (int i = 0; i < 15; i++) { ringPct[i] = 10 * (i + 1); ringPctNames[i] = ringPct[i] + " %"; if (ringPct[i] == st.ringSpeed) pi = i; }
        DlgDecoder.choice(a, col, Lang.T("Bague : vitesse du défilement rapide (100 % = vitesse d'origine)", "Finger ring: speed of the fast scroll (100 % = original speed)"), ringPctNames, pi,
            new DlgDecoder.Pick() { public void chose(int i) { st.ringSpeed = ringPct[i]; st.save(); } });
        col.addView(Ui.small(a, Lang.T("Vitesse atteinte quand plusieurs coups de molette se suivent : de presque zéro (10 %) à 150 % ; 100 % est la vitesse d'origine.", "Speed reached when several wheel strokes follow each other: from almost zero (10 %) to 150 %; 100 % is the original speed.")));
        Ui.closeOnTap(hd, col);
        hd.show();
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
        Button bd = Ui.button(a, Lang.T("Mode numérique actif (case de la fréquence)", "Active digital mode (frequency box)"), new View.OnClickListener() {
            public void onClick(View v) { pick(a, d, 9); }
        });
        bd.setTextColor(Ui.decCol());
        bd.setBackground(Ui.round(Ui.freqBg(), 10, a));
        col.addView(bd, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        Button bb = Ui.button(a, Lang.T("Bouton Décodeur (SET)", "Decoder button (SET)"), new View.OnClickListener() {
            public void onClick(View v) { pick(a, d, 10); }
        });
        bb.setTextColor(Ui.onColor(Ui.decBtn()));
        bb.setBackground(Ui.roundOutline(Ui.decBtn(), 10, a));
        col.addView(bb, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        col.addView(Ui.small(a, Lang.T("Couleurs des boutons de réglage :", "Setting button colours:")));
        Button bon = Ui.button(a, Lang.T("Bouton actif (fonction en marche)", "Active button (function on)"), new View.OnClickListener() {
            public void onClick(View v) { pick(a, d, 11); }
        });
        Ui.paint(bon, true, Ui.on());
        col.addView(bon, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        Button bdg = Ui.button(a, Lang.T("Bouton en cours (scan, REC, recherche…)", "Busy button (scan, REC, search…)"), new View.OnClickListener() {
            public void onClick(View v) { pick(a, d, 12); }
        });
        Ui.paint(bdg, true, Ui.danger());
        col.addView(bdg, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        col.addView(Ui.button(a, Lang.T("Couleurs par défaut", "Default colours"), new View.OnClickListener() {
            public void onClick(View v) {
                st.colBtnBg = st.colBtnTx = st.colFreqTx = st.colFreqBg = st.colBarFg = st.colBarBg = st.colBg = st.colFt8A = st.colFt8B = st.colDec = st.colDecBtn = st.colOn = st.colDanger = 0;
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
        switch (id) { case 0: cur = Ui.btn(); break; case 1: cur = Ui.btnTx(); break; case 2: cur = Ui.freqTx(); break; case 3: cur = Ui.freqBg(); break; case 4: cur = Ui.barFgText(); break; case 5: cur = Ui.barBg(); break; case 7: cur = Ui.ft8A(); break; case 8: cur = Ui.ft8B(); break; case 9: cur = Ui.decCol(); break; case 10: cur = Ui.decBtn(); break; default: cur = Ui.bg(); }
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
        final CheckBox mem = Ui.check(a, Lang.T("Mémoires de fréquences", "Frequency memories"), (has(text, "Mem=") || has(text, "MemRecent=") || has(text, "MemHist=") || has(text, "PhoneHist=")));
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
                closeSet();
                Ui.reopenSet = true;
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
        closeSet();
        Ui.reopenSet = !full;   // réinitialisation totale : écran principal ; partielle : retour dans SET
        a.recreate();
    }
}
