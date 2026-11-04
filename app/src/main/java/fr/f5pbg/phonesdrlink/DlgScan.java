package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;

// Bouton « Conf scan » : scan de fréquences (début, fin, pas, durée, jusqu'à 10 zones non scannées) ou de mémoires (cochées, durée),
// et jusqu'à 10 profils de scan nommés (comme SDRLink).
final class DlgScan {
    static final int[] DWELLS = { 1, 2, 5, 10, 30, 60, 120, 300, 600 };
    static final long[] STEPS = { 100, 250, 500, 1000, 2500, 5000, 6250, 9000, 10000, 12500, 25000, 100000 };

    final MainActivity a;
    final Store st;
    Dialog d;
    Spinner spProfile, spStep, spDwellF, spDwellM;
    EditText profName, txtStart, txtEnd;
    RadioButton rbFreq, rbMem;
    LinearLayout pnlFreq, pnlMem, zonesBox;
    final ArrayList<CheckBox> memChecks = new ArrayList<CheckBox>();
    final ArrayList<EditText[]> zoneRows = new ArrayList<EditText[]>();
    boolean filling;
    int lastLoaded = -1;

    DlgScan(MainActivity a) { this.a = a; this.st = a.st; }

    static void show(MainActivity a) { new DlgScan(a).open(); }

    static ArrayList<String> dwellNames() {
        ArrayList<String> l = new ArrayList<String>();
        for (int s : DWELLS) l.add(MainActivity.dwellText(s));
        return l;
    }

    static int dwellIndex(int sec) {
        for (int i = 0; i < DWELLS.length; i++) if (DWELLS[i] == sec) return i;
        return 2;
    }

    void open() {
        LinearLayout col = Ui.column(a);
        d = Ui.window(a, Lang.T("Conf scan", "Scan setup"), Ui.scroll(a, col), true);

        // Profils
        col.addView(Ui.small(a, Lang.T("Profil :", "Profile:")));
        spProfile = Ui.spinner(a, new ArrayList<String>());
        col.addView(spProfile);
        profName = Ui.edit(a, "", Lang.T("Nom du profil", "Profile name"), InputType.TYPE_CLASS_TEXT);
        col.addView(profName);
        LinearLayout pr = Ui.row(a);
        pr.addView(Ui.button(a, Lang.T("Enregistrer", "Save"), new View.OnClickListener() { public void onClick(View v) { saveProfile(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        pr.addView(Ui.button(a, Lang.T("Renommer", "Rename"), new View.OnClickListener() { public void onClick(View v) { renameProfile(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        pr.addView(Ui.button(a, Lang.T("Supprimer", "Delete"), new View.OnClickListener() { public void onClick(View v) { deleteProfile(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(pr);
        fillProfiles(null);
        spProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (filling || pos == 0) return;
                lastLoaded = pos - 1;
                profName.setText(st.scanProfiles.get(lastLoaded).name);
                load(st.scanProfiles.get(lastLoaded));
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });

        // Type
        col.addView(Ui.small(a, Lang.T("Type de scan :", "Scan type:")));
        RadioGroup rg = new RadioGroup(a);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rbFreq = new RadioButton(a);
        rbMem = new RadioButton(a);
        rbFreq.setText(Lang.T("Fréquences", "Frequencies"));
        rbMem.setText(Lang.T("Mémoires", "Memories"));
        rbFreq.setTextColor(Ui.fg());
        rbMem.setTextColor(Ui.fg());
        rg.addView(rbFreq);
        rg.addView(rbMem);
        col.addView(rg);
        rbFreq.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) { showType(); }
        });

        // Fréquences
        pnlFreq = new LinearLayout(a);
        pnlFreq.setOrientation(LinearLayout.VERTICAL);
        pnlFreq.addView(Ui.small(a, Lang.T("Début (kHz) :", "Start (kHz):")));
        txtStart = Ui.edit(a, "", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        pnlFreq.addView(txtStart);
        pnlFreq.addView(Ui.small(a, Lang.T("Fin (kHz) :", "End (kHz):")));
        txtEnd = Ui.edit(a, "", "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        pnlFreq.addView(txtEnd);
        pnlFreq.addView(Ui.small(a, Lang.T("Pas :", "Step:")));
        ArrayList<String> steps = new ArrayList<String>();
        for (long s : STEPS) steps.add(MainActivity.scanStepText(s));
        spStep = Ui.spinner(a, steps);
        pnlFreq.addView(spStep);
        pnlFreq.addView(Ui.small(a, Lang.T("Durée sur chaque fréquence :", "Time on each frequency:")));
        spDwellF = Ui.spinner(a, dwellNames());
        pnlFreq.addView(spDwellF);
        pnlFreq.addView(Ui.small(a, Lang.T("Zones non scannées (de … à … kHz) :", "Skipped ranges (from … to … kHz):")));
        zonesBox = new LinearLayout(a);
        zonesBox.setOrientation(LinearLayout.VERTICAL);
        pnlFreq.addView(zonesBox);
        pnlFreq.addView(Ui.button(a, Lang.T("+ Zone non scannée", "+ Skipped range"), new View.OnClickListener() {
            public void onClick(View v) { addZone("", ""); }
        }));
        col.addView(pnlFreq);

        // Mémoires
        pnlMem = new LinearLayout(a);
        pnlMem.setOrientation(LinearLayout.VERTICAL);
        pnlMem.addView(Ui.small(a, Lang.T("Mémoires à parcourir :", "Memories to scan:")));
        for (Memory m : st.memories) {
            CheckBox c = Ui.check(a, m.label() + "  —  " + Memory.kHz(m.hz) + " kHz" + (m.mode.isEmpty() ? "" : " " + MainActivity.modeLabel(m.mode))
                + "  (" + st.memListLabel(m.list) + ")", false);
            c.setTextSize(14);
            memChecks.add(c);
            pnlMem.addView(c);
        }
        if (st.memories.isEmpty()) pnlMem.addView(Ui.small(a, Lang.T("(aucune mémoire : bouton Mémoires)", "(no memory: Memories button)")));
        LinearLayout mr = Ui.row(a);
        mr.addView(Ui.button(a, Lang.T("Tout cocher", "Check all"), new View.OnClickListener() { public void onClick(View v) { setAll(true); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        mr.addView(Ui.button(a, Lang.T("Tout décocher", "Uncheck all"), new View.OnClickListener() { public void onClick(View v) { setAll(false); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        pnlMem.addView(mr);
        pnlMem.addView(Ui.small(a, Lang.T("Durée sur chaque mémoire :", "Time on each memory:")));
        spDwellM = Ui.spinner(a, dwellNames());
        pnlMem.addView(spDwellM);
        col.addView(pnlMem);

        // Boutons
        LinearLayout b1 = Ui.row(a);
        b1.addView(Ui.button(a, Lang.T("Réinitialiser", "Reset"), new View.OnClickListener() { public void onClick(View v) { resetShown(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        Button start = Ui.button(a, Lang.T("Lancer le scan", "Start scan"), new View.OnClickListener() { public void onClick(View v) { finish(true); } });
        Ui.paint(start, true, Ui.on());
        b1.addView(start, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f, a, 2));
        col.addView(b1);
        LinearLayout b2 = Ui.row(a);
        b2.setGravity(Gravity.END);
        b2.addView(Ui.button(a, Lang.T("Annuler", "Cancel"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        b2.addView(Ui.button(a, "OK", new View.OnClickListener() { public void onClick(View v) { finish(false); } }));
        col.addView(b2);

        load(st.currentScan());
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() { public void onDismiss(android.content.DialogInterface x) { st.save(); } });
        d.show();
    }

    void fillProfiles(String select) {
        filling = true;
        ArrayList<String> names = new ArrayList<String>();
        names.add("—");
        for (ScanProfile p : st.scanProfiles) names.add(p.name);
        @SuppressWarnings("unchecked") android.widget.ArrayAdapter<String> ad = (android.widget.ArrayAdapter<String>) spProfile.getAdapter();
        ad.clear();
        ad.addAll(names);
        int idx = select == null ? -1 : profileIndex(select);
        spProfile.setSelection(idx + 1);
        filling = false;
    }

    int profileIndex(String name) {
        for (int i = 0; i < st.scanProfiles.size(); i++) if (st.scanProfiles.get(i).name.equalsIgnoreCase(name)) return i;
        return -1;
    }

    // Affiche les réglages d'un profil (ou du réglage du moment).
    void load(ScanProfile p) {
        txtStart.setText(p.startHz > 0 ? Memory.kHz(p.startHz) : "");
        txtEnd.setText(p.endHz > 0 ? Memory.kHz(p.endHz) : "");
        int si = 3;
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == p.stepHz) si = i;
        spStep.setSelection(si);
        spDwellF.setSelection(dwellIndex(p.freqDwell));
        spDwellM.setSelection(dwellIndex(p.memDwell));
        for (int i = 0; i < memChecks.size(); i++) memChecks.get(i).setChecked(p.mems.contains(st.memories.get(i).key()));
        clearZones();
        for (long[] z : p.skips) addZone(Memory.kHz(z[0]), Memory.kHz(z[1]));
        rbFreq.setChecked(p.type == 0);
        rbMem.setChecked(p.type == 1);
        showType();
    }

    void showType() {
        pnlFreq.setVisibility(rbFreq.isChecked() ? View.VISIBLE : View.GONE);
        pnlMem.setVisibility(rbFreq.isChecked() ? View.GONE : View.VISIBLE);
    }

    void setAll(boolean on) { for (CheckBox c : memChecks) c.setChecked(on); }

    void clearZones() {
        zonesBox.removeAllViews();
        zoneRows.clear();
    }

    void addZone(String from, String to) {
        if (zoneRows.size() >= ScanProfile.MAX_SKIPS) return;
        final LinearLayout row = Ui.row(a);
        EditText ea = Ui.edit(a, from, Lang.T("de", "from"), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText eb = Ui.edit(a, to, Lang.T("à", "to"), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText[] pair = { ea, eb };
        Button del = Ui.button(a, "✕", new View.OnClickListener() {
            public void onClick(View v) { zoneRows.remove(pair); zonesBox.removeView(row); }
        });
        row.addView(ea, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        row.addView(eb, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        row.addView(Ui.small(a, "kHz"));
        row.addView(del, Ui.lp(Ui.dp(a, 48), ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        zoneRows.add(pair);
        zonesBox.addView(row);
    }

    // Remet à zéro les choix du type affiché (l'autre type garde les siens).
    void resetShown() {
        if (rbFreq.isChecked()) {
            txtStart.setText("");
            txtEnd.setText("");
            spStep.setSelection(3);
            spDwellF.setSelection(dwellIndex(5));
            clearZones();
        } else {
            setAll(false);
            spDwellM.setSelection(dwellIndex(5));
        }
    }

    // null si correct, avec les réglages affichés dans out[0]. Pour lancer le scan, le type choisi doit être complet.
    String check(boolean starting, ScanProfile[] out) {
        ScanProfile p = new ScanProfile();
        out[0] = p;
        p.type = rbMem.isChecked() ? 1 : 0;
        long x = DlgKeypad.parse(txtStart.getText().toString()), y = DlgKeypad.parse(txtEnd.getText().toString());
        boolean empty = txtStart.getText().toString().trim().isEmpty() && txtEnd.getText().toString().trim().isEmpty();
        if (rbFreq.isChecked() && (starting || !empty) && (x <= 0 || y <= 0 || x == y))
            return Lang.T("Indiquez une fréquence de début et une fréquence de fin différentes, en kHz (par ex. 7000 et 7200).", "Enter different start and end frequencies, in kHz (e.g. 7000 and 7200).");
        p.startHz = Math.max(0, x);
        p.endHz = Math.max(0, y);
        p.stepHz = STEPS[Math.max(0, spStep.getSelectedItemPosition())];
        p.freqDwell = DWELLS[Math.max(0, spDwellF.getSelectedItemPosition())];
        p.memDwell = DWELLS[Math.max(0, spDwellM.getSelectedItemPosition())];
        for (int i = 0; i < zoneRows.size(); i++) {
            String ta = zoneRows.get(i)[0].getText().toString().trim(), tb = zoneRows.get(i)[1].getText().toString().trim();
            if (ta.isEmpty() && tb.isEmpty()) continue;
            long za = DlgKeypad.parse(ta), zb = DlgKeypad.parse(tb);
            if (za <= 0 || zb <= 0)
                return Lang.F("Zone non scannée n° {0} : indiquez deux fréquences en kHz (par ex. 7097 et 7103).", "Skipped range no. {0}: enter two frequencies in kHz (e.g. 7097 and 7103).", i + 1);
            p.skips.add(new long[] { Math.min(za, zb), Math.max(za, zb) });
        }
        for (int i = 0; i < memChecks.size(); i++) if (memChecks.get(i).isChecked()) p.mems.add(st.memories.get(i).key());
        if (rbFreq.isChecked() && starting && p.next(-1) < 0)
            return Lang.T("Toutes les fréquences de début à fin sont dans des zones non scannées.", "All frequencies from start to end are in skipped ranges.");
        if (rbMem.isChecked() && starting && p.mems.isEmpty())
            return Lang.T("Cochez au moins une mémoire à parcourir.", "Check at least one memory to scan.");
        return null;
    }

    void finish(boolean start) {
        ScanProfile[] out = new ScanProfile[1];
        String err = check(start, out);
        if (err != null) { Ui.message(a, Lang.T("Conf scan", "Scan setup"), err); return; }
        st.useScan(out[0]);
        st.save();
        d.dismiss();
        if (start) a.startScan(false);
    }

    void saveProfile() {
        final String name = profName.getText().toString().trim().replace("|", "/");
        if (name.isEmpty()) { Ui.message(a, Lang.T("Profil :", "Profile:"), Lang.T("Tapez d'abord un nom de profil dans la case « Profil ».", "First type a profile name in the \"Profile\" box.")); return; }
        final ScanProfile[] out = new ScanProfile[1];
        String err = check(false, out);
        if (err != null) { Ui.message(a, Lang.T("Conf scan", "Scan setup"), err); return; }
        out[0].name = name;
        final int i = profileIndex(name);
        if (i >= 0) {
            Ui.confirm(a, Lang.T("Profil :", "Profile:"), Lang.F("Remplacer le profil « {0} » par les réglages affichés ?", "Replace the profile \"{0}\" with the settings shown?", st.scanProfiles.get(i).name), new Ui.Yes() {
                public void yes() { st.scanProfiles.set(i, out[0]); fillProfiles(name); lastLoaded = profileIndex(name); st.save(); }
            });
        } else if (st.scanProfiles.size() >= ScanProfile.MAX_PROFILES) {
            Ui.message(a, Lang.T("Profil :", "Profile:"), Lang.F("{0} profils au plus : supprimez-en un d'abord.", "{0} profiles at most: delete one first.", ScanProfile.MAX_PROFILES));
        } else {
            st.scanProfiles.add(out[0]);
            fillProfiles(name);
            lastLoaded = profileIndex(name);
            st.save();
        }
    }

    void renameProfile() {
        String name = profName.getText().toString().trim().replace("|", "/");
        int i = lastLoaded;
        if (i < 0 || i >= st.scanProfiles.size()) {
            Ui.message(a, Lang.T("Profil :", "Profile:"), Lang.T("Choisissez d'abord un profil dans la liste, puis tapez son nouveau nom dans la case et cliquez sur Renommer.", "First choose a profile in the list, then type its new name in the box and click Rename."));
            return;
        }
        if (name.isEmpty()) return;
        int other = profileIndex(name);
        if (other >= 0 && other != i) { Ui.message(a, Lang.T("Profil :", "Profile:"), Lang.F("Le profil « {0} » existe déjà.", "The profile \"{0}\" already exists.", name)); return; }
        st.scanProfiles.get(i).name = name;
        fillProfiles(name);
        lastLoaded = i;
        st.save();
    }

    void deleteProfile() {
        final int i = lastLoaded >= 0 ? lastLoaded : profileIndex(profName.getText().toString().trim());
        if (i < 0 || i >= st.scanProfiles.size()) { Ui.message(a, Lang.T("Profil :", "Profile:"), Lang.T("Choisissez dans la liste le profil à supprimer.", "Choose in the list the profile to delete.")); return; }
        Ui.confirm(a, Lang.T("Profil :", "Profile:"), Lang.F("Supprimer le profil « {0} » ?", "Delete the profile \"{0}\"?", st.scanProfiles.get(i).name), new Ui.Yes() {
            public void yes() { st.scanProfiles.remove(i); fillProfiles(null); lastLoaded = -1; profName.setText(""); st.save(); }
        });
    }
}
