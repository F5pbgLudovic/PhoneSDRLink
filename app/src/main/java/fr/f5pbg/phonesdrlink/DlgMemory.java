package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.ArrayList;

// Bouton Mémoires : listes de mémoires (1 à 10, nommées) et les 20 dernières utilisées. Appui = on y va ; appui long = modifier,
// déplacer, supprimer. Les mémoires à scanner se cochent dans « Conf scan ».
final class DlgMemory {
    static int lastPos = -1, lastFirst, lastTop;   // liste habituelle : position du menu des listes et du défilement à la dernière fermeture
    static int digFirst, digTop;                   // liste « Modes numériques » : défilement à la dernière fermeture
    static boolean rec, dig;                       // « Dernières mémoires » ou « Modes numériques » choisie

    static void show(final MainActivity a) {
        final Store st = a.st;
        final LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 10));
        final Dialog d = Ui.window(a, Lang.T("Mémoires…", "Memories…"), col, true);
        final Spinner sp = Ui.spinner(a, new ArrayList<String>());
        final ListView lv = new ListView(a);
        lv.setDivider(null);
        final TextView info = Ui.small(a, "");
        final LinearLayout regionRow = Ui.row(a);
        regionRow.setVisibility(View.GONE);
        final ArrayList<Memory> shown = new ArrayList<Memory>();
        final Runnable[] fill = new Runnable[1];
        final boolean[] filling = { false };
        if (lastPos < 0) { int[] mp = st.getPos("mem"); if (mp != null && mp.length >= 3) { lastPos = mp[0]; lastFirst = mp[1]; lastTop = mp[2]; } }   // après un redémarrage : positions du fichier de réglages
        if (digFirst == 0 && digTop == 0) { int[] dp = st.getPos("memd"); if (dp != null && dp.length >= 2) { digFirst = dp[0]; digTop = dp[1]; } }
        // Un mode numérique est actif : on ouvre la liste « Modes numériques » ; sinon on revient à la liste habituelle (celle de la dernière fermeture).
        dig = a.decMode != 0;
        rec = !dig && lastPos == 0;
        final int restoreFirst = dig ? digFirst : lastFirst, restoreTop = dig ? digTop : lastTop;
        final String[] query = { "" };

        fill[0] = new Runnable() {
            public void run() {
                filling[0] = true;
                ArrayList<String> names = new ArrayList<String>();
                names.add(Lang.F("Dernières {0} mémoires utilisées", "Last {0} memories used", Memory.RECENT));
                for (int i = 0; i < st.memListCount; i++) names.add(st.memListLabel(i));
                names.add(Lang.T("Modes numériques", "Digital modes"));
                @SuppressWarnings("unchecked") ArrayAdapter<String> ad = (ArrayAdapter<String>) sp.getAdapter();
                ad.clear();
                ad.addAll(names);
                int sel = dig ? st.memListCount + 1 : rec ? 0 : Math.min(st.curMemList, st.memListCount - 1) + 1;
                sp.setSelection(sel);
                filling[0] = false;
                list(sel);
            }
            void list(int sel) {
                shown.clear();
                regionRow.setVisibility(sel == st.memListCount + 1 ? View.VISIBLE : View.GONE);
                if (sel == st.memListCount + 1) shown.addAll(DigitalFreqs.list(digMode(a), st.iaruRegion));
                else if (sel == 0) shown.addAll(st.recent);
                else for (Memory m : st.memories) if (m.list == sel - 1) shown.add(m);
                if (!query[0].isEmpty()) {
                    for (int k = shown.size() - 1; k >= 0; k--) {
                        Memory m = shown.get(k);
                        String hay = (m.label() + " " + Memory.kHz(m.hz) + " " + m.mode).toLowerCase();
                        if (!hay.contains(query[0])) shown.remove(k);
                    }
                }
                ArrayList<String> rows = new ArrayList<String>();
                for (Memory m : shown) rows.add(m.label() + "   —   " + Memory.kHz(m.hz) + " kHz" + (m.mode.isEmpty() ? "" : "  " + MainActivity.modeLabel(m.mode)) + (m.scan ? "   ⟳" : ""));
                lv.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, rows) {
                    public View getView(int p, View v, ViewGroup g) {
                        // Une case (bouton arrondi) par mémoire.
                        LinearLayout box = new LinearLayout(a);
                        box.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
                        TextView t = new TextView(a);
                        t.setText(getItem(p));
                        t.setTextColor(Ui.fg());
                        t.setTextSize(16);
                        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        t.setGravity(Gravity.CENTER_VERTICAL);
                        t.setMinHeight(Ui.dp(a, 52));
                        t.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
                        t.setBackground(Ui.round(Ui.btn(), 14, a));
                        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        return box;
                    }
                });
                if (sel == st.memListCount + 1) info.setText(Lang.F("Fréquences habituelles de {0}, {1} : elles suivent le mode du décodeur. Appuyez pour y aller (appui long : copier dans une liste).", "Usual {0} frequencies, {1}: they follow the decoder mode. Click to go there (long press: copy to a list).", MainActivity.decName(digMode(a)), DigitalFreqs.regionName(st.iaruRegion)));
                else                 info.setText(Lang.F("{0} mémoire(s). Appuyez sur une mémoire pour y aller (appui long : modifier).", "{0} memory(ies). Click a memory to go there.", shown.size()));
            }
        };
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (filling[0]) return;
                int exp = dig ? st.memListCount + 1 : rec ? 0 : Math.min(st.curMemList, st.memListCount - 1) + 1;
                if (pos == exp) return;   // appel de départ du menu : la liste est déjà affichée (on garde la position de défilement)
                rec = pos == 0;
                dig = pos == st.memListCount + 1;
                if (pos > 0 && pos <= st.memListCount) st.curMemList = pos - 1;
                fill[0].run();
            }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                d.dismiss();
                if (a.scanning) a.stopScan(null);
                a.tuneMemory(shown.get(pos));
            }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                if (sp.getSelectedItemPosition() == st.memListCount + 1) { copyMenu(a, shown.get(pos)); return true; }
                itemMenu(a, shown.get(pos), sp.getSelectedItemPosition() == 0, fill[0]);
                return true;
            }
        });

        // Loupe à droite du nom de la liste : filtre les mémoires affichées (nom, fréquence ou mode).
        LinearLayout lr = Ui.row(a);
        lr.addView(sp, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        final EditText find = Ui.edit(a, "", Lang.T("Rechercher dans la liste…", "Search the list…"), InputType.TYPE_CLASS_TEXT);
        find.setVisibility(View.GONE);
        find.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int x, int y, int z) { }
            public void onTextChanged(CharSequence s, int x, int y, int z) { }
            public void afterTextChanged(android.text.Editable s) { query[0] = s.toString().trim().toLowerCase(); fill[0].run(); }
        });
        lr.addView(Ui.button(a, "🔍", new View.OnClickListener() {
            public void onClick(View v) {
                if (find.getVisibility() == View.VISIBLE) { find.setText(""); find.setVisibility(View.GONE); return; }
                find.setVisibility(View.VISIBLE);
                find.requestFocus();
                android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (im != null) im.showSoftInput(find, 0);
            }
        }), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        col.addView(lr);
        col.addView(find);
        final Spinner sr = Ui.spinner(a, new ArrayList<String>(java.util.Arrays.asList(DigitalFreqs.regionName(1), DigitalFreqs.regionName(2), DigitalFreqs.regionName(3))));
        sr.setSelection(st.iaruRegion - 1);
        sr.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (st.iaruRegion != pos + 1) { st.iaruRegion = pos + 1; st.save(); fill[0].run(); } }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        regionRow.addView(sr, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(regionRow);
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        col.addView(info);
        LinearLayout r1 = Ui.row(a);
        r1.addView(Ui.button(a, Lang.T("Enregistrer la fréquence actuelle…", "Store the current frequency…"), new View.OnClickListener() {
            public void onClick(View v) {
                Memory m = new Memory("", a.freq, a.mode);
                m.list = Math.min(st.curMemList, st.memListCount - 1);
                edit(a, Lang.T("Ajouter une mémoire", "Add memory"), m, true, fill[0]);
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r1);
        LinearLayout r2 = Ui.row(a);
        r2.addView(Ui.button(a, Lang.T("Nouvelle liste", "New list"), new View.OnClickListener() {
            public void onClick(View v) {
                if (st.memListCount >= Memory.MAX_LISTS) return;
                st.curMemList = st.memListCount++;
                dig = false;
                rec = false;
                fill[0].run();
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(Ui.button(a, Lang.T("Renommer la liste", "Rename list"), new View.OnClickListener() {
            public void onClick(View v) {
                final int i = sp.getSelectedItemPosition() - 1;
                if (i < 0 || i >= st.memListCount) return;
                Ui.input(a, Lang.F("Renommer « {0} »…", "Rename \"{0}\"…", st.memListLabel(i)), "", st.memListLabel(i), new Ui.Text() {
                    public void got(String t) { if (t.trim().length() > 0) { st.memListNames[i] = t.trim(); fill[0].run(); } }
                });
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(Ui.button(a, Lang.T("Supprimer la liste…", "Delete list…"), new View.OnClickListener() {
            public void onClick(View v) {
                final int i = sp.getSelectedItemPosition() - 1;
                if (i < 0 || i >= st.memListCount || st.memListCount <= 1) return;
                int n = 0;
                for (Memory m : st.memories) if (m.list == i) n++;
                String msg = n == 0 ? Lang.F("Supprimer la liste « {0} » (vide) ?", "Delete the list \"{0}\" (empty)?", st.memListLabel(i))
                    : Lang.F("Supprimer la liste « {0} » et ses {1} mémoire(s) ?", "Delete the list \"{0}\" and its {1} memory(ies)?", st.memListLabel(i), n);
                Ui.confirm(a, Lang.T("Supprimer la liste…", "Delete list…"), msg, new Ui.Yes() {
                    public void yes() {
                        for (int k = st.memories.size() - 1; k >= 0; k--) if (st.memories.get(k).list == i) st.memories.remove(k);
                        for (Memory m : st.memories) if (m.list > i) m.list--;
                        for (int k = i; k < Memory.MAX_LISTS - 1; k++) st.memListNames[k] = st.memListNames[k + 1];
                        st.memListNames[Memory.MAX_LISTS - 1] = "Liste " + Memory.MAX_LISTS;
                        st.memListCount--;
                        st.curMemList = Math.max(0, Math.min(i, st.memListCount - 1));
                        fill[0].run();
                    }
                });
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r2);
        LinearLayout r3 = Ui.row(a);
        r3.setGravity(Gravity.END);
        r3.addView(Ui.button(a, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        col.addView(r3);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() { public void onDismiss(android.content.DialogInterface x) {
                int pos = sp.getSelectedItemPosition(), first = lv.getFirstVisiblePosition();
                View c0 = lv.getChildAt(0);
                int top = c0 == null ? 0 : c0.getTop() - lv.getPaddingTop();
                if (pos == st.memListCount + 1) { digFirst = first; digTop = top; st.putPos("memd", digFirst, digTop); }
                else { lastPos = pos; lastFirst = first; lastTop = top; st.putPos("mem", lastPos, lastFirst, lastTop); }
                st.save();
            } });
        fill[0].run();
        lv.post(new Runnable() { public void run() { lv.setSelectionFromTop(restoreFirst, restoreTop); } });
        d.show();
    }

    // Mode du décodeur dont on affiche les fréquences : celui qui tourne, sinon le dernier choisi dans la fenêtre du décodeur.
    static int digMode(MainActivity a) { return a.decMode != 0 ? a.decMode : DlgDecoder.lastSel; }

    // Liste automatique : on ne la modifie pas, mais on peut copier une fréquence dans une liste de mémoires.
    static void copyMenu(final MainActivity a, final Memory m) {
        final Store st = a.st;
        final String[] names = new String[st.memListCount];
        for (int k = 0; k < names.length; k++) names[k] = st.memListLabel(k);
        Ui.choose(a, Lang.F("Copier « {0} » dans ▸", "Copy \"{0}\" to ▸", m.label()), names, new Ui.Index() {
            public void chose(int k) {
                Memory c = new Memory(m.name, m.hz, m.mode);
                c.list = k;
                st.memories.add(c);
                st.save();
                a.status(Lang.F("Mémoire « {0} » ajoutée à « {1} ».", "Memory \"{0}\" added to \"{1}\".", c.label(), st.memListLabel(k)));
            }
        });
    }

    static void itemMenu(final MainActivity a, final Memory m, final boolean inRecent, final Runnable refresh) {
        final Store st = a.st;
        Ui.choose(a, m.label(), new String[] { Lang.T("Modifier…", "Edit…"), Lang.T("Supprimer…", "Remove…"), Lang.T("Déplacer vers ▸", "Move to ▸") }, new Ui.Index() {
            public void chose(int i) {
                if (i == 0) {
                    if (inRecent) { Memory c = new Memory(m.name, m.hz, m.mode); edit(a, Lang.T("Ajouter une mémoire", "Add memory"), c, true, refresh); }
                    else edit(a, Lang.T("Modifier la mémoire", "Edit memory"), m, false, refresh);
                } else if (i == 1) {
                    Ui.confirm(a, Lang.T("Supprimer…", "Remove…"), Lang.F("Supprimer la mémoire « {0} » ?", "Remove the memory \"{0}\"?", m.label()), new Ui.Yes() {
                        public void yes() { if (inRecent) st.recent.remove(m); else st.memories.remove(m); refresh.run(); }
                    });
                } else {
                    final String[] names = new String[st.memListCount];
                    for (int k = 0; k < names.length; k++) names[k] = st.memListLabel(k);
                    Ui.choose(a, Lang.T("Déplacer vers ▸", "Move to ▸"), names, new Ui.Index() {
                        public void chose(int k) {
                            if (inRecent) { Memory c = new Memory(m.name, m.hz, m.mode); c.list = k; st.memories.add(c); }
                            else m.list = k;
                            refresh.run();
                        }
                    });
                }
            }
        });
    }

    // Ajout (isNew) ou modification d'une mémoire.
    static void edit(final MainActivity a, String title, final Memory m, final boolean isNew, final Runnable refresh) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        final Dialog d = Ui.window(a, title, Ui.scroll(a, col), false);
        col.addView(Ui.small(a, Lang.T("Nom :", "Name:")));
        final EditText name = Ui.edit(a, m.name, "", InputType.TYPE_CLASS_TEXT);
        col.addView(name);
        col.addView(Ui.small(a, Lang.T("Fréquence (kHz) :", "Frequency (kHz):")));
        final EditText khz = Ui.edit(a, Memory.kHz(m.hz), "", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        col.addView(khz);
        col.addView(Ui.small(a, Lang.T("Mode :", "Mode:")));
        ArrayList<String> modes = new ArrayList<String>();
        modes.add(Lang.T("(mode inchangé)", "(mode unchanged)"));
        for (String s : MainActivity.MODES) modes.add(MainActivity.modeLabel(s));
        final Spinner sm = Ui.spinner(a, modes);
        for (int i = 0; i < MainActivity.MODES.length; i++) if (MainActivity.MODES[i].equals(m.mode)) sm.setSelection(i + 1);
        col.addView(sm);
        col.addView(Ui.small(a, Lang.T("Liste :", "List:")));
        ArrayList<String> lists = new ArrayList<String>();
        for (int i = 0; i < st.memListCount; i++) lists.add(st.memListLabel(i));
        final Spinner sl = Ui.spinner(a, lists);
        sl.setSelection(Math.min(m.list, st.memListCount - 1));
        col.addView(sl);
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.END);
        r.addView(Ui.button(a, Lang.T("Annuler", "Cancel"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        r.addView(Ui.button(a, "OK", new View.OnClickListener() {
            public void onClick(View v) {
                long hz = DlgKeypad.parse(khz.getText().toString());
                if (hz < Store.MIN_HZ || hz > Store.MAX_HZ) {
                    Ui.toast(a, Lang.T("Fréquence invalide : tapez une valeur en kHz, par ex. 7074 ou 14074.5", "Invalid frequency: type a value in kHz, e.g. 7074 or 14074.5"));
                    return;
                }
                m.name = name.getText().toString().trim();
                m.hz = hz;
                int mi = sm.getSelectedItemPosition();
                m.mode = mi == 0 ? "" : MainActivity.MODES[mi - 1];
                m.list = sl.getSelectedItemPosition();
                if (isNew) {
                    st.memories.add(m);
                    st.curMemList = m.list;
                    rec = false;
                    dig = false;
                    a.status(Lang.F("Mémoire « {0} » ajoutée à « {1} ».", "Memory \"{0}\" added to \"{1}\".", m.label(), st.memListLabel(m.list)));
                }
                st.save();
                d.dismiss();
                refresh.run();
            }
        }));
        col.addView(r);
        d.show();
    }
}
