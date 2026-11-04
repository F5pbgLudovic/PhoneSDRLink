package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.SparseBooleanArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

// Bouton WEBSDR : choisir un favori (par liste), saisir une adresse, ajouter la page aux favoris, gérer les favoris,
// rechercher des WebSDR dans les annuaires publics (comme SDRLink).
final class DlgWebSdr {
    static final Handler UI = new Handler(Looper.getMainLooper());
    static List<FoundSdr> cache;

    // ------------------------------------------------------------------ fenêtre WEBSDR

    static void show(final MainActivity a) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 10));
        final Dialog d = Ui.window(a, "WEBSDR", col, true);
        final Spinner sp = Ui.spinner(a, new ArrayList<String>());
        final ListView lv = new ListView(a);
        final ArrayList<Favorite> shown = new ArrayList<Favorite>();
        final boolean[] filling = { false };
        final Runnable[] fill = new Runnable[1];
        fill[0] = new Runnable() {
            @SuppressWarnings("unchecked")
            public void run() {
                filling[0] = true;
                ArrayAdapter<String> ad = (ArrayAdapter<String>) sp.getAdapter();
                ad.clear();
                for (int i = 0; i < st.favListCount; i++) ad.add(st.favListLabel(i));
                ad.add(Lang.T("Toutes les listes", "All lists"));
                sp.setSelection(Math.min(st.curFavList, st.favListCount));
                filling[0] = false;
                shown.clear();
                for (Favorite f : st.favorites) if (st.curFavList >= st.favListCount || f.list == st.curFavList) shown.add(f);
                ArrayList<String> rows = new ArrayList<String>();
                for (Favorite f : shown) rows.add(f.name);
                lv.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, rows) {
                    public View getView(int p, View v, ViewGroup g) { TextView t = (TextView) super.getView(p, v, g); t.setTextColor(Ui.fg()); t.setTextSize(15); return t; }
                });
            }
        };
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (!filling[0]) { st.curFavList = pos; fill[0].run(); } }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                d.dismiss();
                a.openUrl(shown.get(pos).url);
            }
        });
        col.addView(sp);
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // Adresse saisie à la main
        LinearLayout ar = Ui.row(a);
        final EditText url = Ui.edit(a, "", Lang.T("Adresse du WebSDR", "WebSDR address"), InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_CLASS_TEXT);
        ar.addView(url, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        ar.addView(Ui.button(a, Lang.T("Aller", "Go"), new View.OnClickListener() {
            public void onClick(View v) { String u = url.getText().toString().trim(); if (!u.isEmpty()) { d.dismiss(); a.openUrl(u); } }
        }), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        col.addView(ar);

        LinearLayout r1 = Ui.row(a);
        r1.addView(Ui.button(a, Lang.T("★ Ajouter aux favoris", "★ Add to favorites"), new View.OnClickListener() {
            public void onClick(View v) { addCurrent(a, fill[0]); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r1.addView(Ui.button(a, Lang.T("Gérer les favoris…", "Manage favorites…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); manage(a); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r1);
        LinearLayout r2 = Ui.row(a);
        r2.addView(Ui.button(a, Lang.T("Chercher des WebSDR…", "Find WebSDRs…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); search(a); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f, a, 2));
        r2.addView(Ui.button(a, Lang.T("Recharger", "Reload"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); a.web.reload(); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(Ui.button(a, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r2);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() { public void onDismiss(android.content.DialogInterface x) { st.save(); } });
        fill[0].run();
        d.show();
    }

    // Ajoute la page affichée aux favoris (nom et liste au choix).
    static void addCurrent(final MainActivity a, final Runnable refresh) {
        final Store st = a.st;
        final String url = a.currentUrl();
        for (Favorite f : st.favorites) if (f.matches(url)) {
            Ui.message(a, Lang.T("Ajouter un favori", "Add favorite"), Lang.F("Ce WebSDR est déjà dans « {0} ».", "This WebSDR is already in \"{0}\".", st.favListLabel(f.list)));
            return;
        }
        editFavorite(a, null, a.currentTitle(), url, Math.min(st.curFavList, st.favListCount - 1), refresh);
    }

    // Ajout (f == null) ou modification d'un favori : nom, adresse, liste.
    static void editFavorite(final MainActivity a, final Favorite f, String name, String url, int list, final Runnable refresh) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        final Dialog d = Ui.window(a, f == null ? Lang.T("Ajouter un favori", "Add favorite") : Lang.T("Modifier…", "Edit…"), Ui.scroll(a, col), false);
        col.addView(Ui.small(a, Lang.T("Nom :", "Name:")));
        final EditText en = Ui.edit(a, name, "", InputType.TYPE_CLASS_TEXT);
        col.addView(en);
        col.addView(Ui.small(a, Lang.T("Adresse :", "Address:")));
        final EditText eu = Ui.edit(a, url, "", InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_CLASS_TEXT);
        col.addView(eu);
        col.addView(Ui.small(a, Lang.T("Liste :", "List:")));
        ArrayList<String> lists = new ArrayList<String>();
        for (int i = 0; i < st.favListCount; i++) lists.add(st.favListLabel(i));
        final Spinner sl = Ui.spinner(a, lists);
        sl.setSelection(Math.max(0, Math.min(list, st.favListCount - 1)));
        col.addView(sl);
        LinearLayout r = Ui.row(a);
        r.setGravity(Gravity.END);
        r.addView(Ui.button(a, Lang.T("Annuler", "Cancel"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        r.addView(Ui.button(a, "OK", new View.OnClickListener() {
            public void onClick(View v) {
                String u = eu.getText().toString().trim();
                if (u.isEmpty() || en.getText().toString().trim().isEmpty()) { Ui.toast(a, Lang.T("Indiquez un nom et une adresse.", "Please enter a name and an address.")); return; }
                if (!u.contains("://")) u = "http://" + u;
                if (f == null) {
                    Favorite n = new Favorite(en.getText().toString(), u);
                    n.list = sl.getSelectedItemPosition();
                    st.favorites.add(n);
                    st.curFavList = n.list;
                    a.status(Lang.F("« {0} » ajouté à « {1} ».", "\"{0}\" added to \"{1}\".", n.name, st.favListLabel(n.list)));
                } else {
                    f.name = en.getText().toString().trim();
                    f.url = u;
                    f.list = sl.getSelectedItemPosition();
                }
                st.save();
                d.dismiss();
                if (refresh != null) refresh.run();
            }
        }));
        col.addView(r);
        d.show();
    }

    // ------------------------------------------------------------------ gestion des favoris

    static void manage(final MainActivity a) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 10));
        final Dialog d = Ui.window(a, Lang.T("Gestion des favoris", "Favorite manager"), col, true);
        final Spinner sp = Ui.spinner(a, new ArrayList<String>());
        final ListView lv = new ListView(a);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        final TextView info = Ui.small(a, "");
        final ArrayList<Favorite> shown = new ArrayList<Favorite>();
        final HashMap<Favorite, String> status = new HashMap<Favorite, String>();
        final boolean[] filling = { false };
        final Runnable[] fill = new Runnable[1];
        final ArrayList<Favorite> keepChecked = new ArrayList<Favorite>();
        fill[0] = new Runnable() {
            @SuppressWarnings("unchecked")
            public void run() {
                filling[0] = true;
                ArrayAdapter<String> ad = (ArrayAdapter<String>) sp.getAdapter();
                ad.clear();
                for (int i = 0; i < st.favListCount; i++) ad.add(st.favListLabel(i));
                ad.add(Lang.T("Toutes les listes", "All lists"));
                sp.setSelection(Math.min(st.curFavList, st.favListCount));
                filling[0] = false;
                shown.clear();
                for (Favorite f : st.favorites) if (st.curFavList >= st.favListCount || f.list == st.curFavList) shown.add(f);
                ArrayList<String> rows = new ArrayList<String>();
                for (Favorite f : shown) rows.add(f.name + "\n" + f.url + (status.containsKey(f) ? "   [" + status.get(f) + "]" : ""));
                lv.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_list_item_multiple_choice, rows) {
                    public View getView(int p, View v, ViewGroup g) { CheckedTextView t = (CheckedTextView) super.getView(p, v, g); t.setTextColor(Ui.fg()); t.setTextSize(14); t.setMinHeight(0); t.setMinimumHeight(Ui.dp(a, 56)); t.setSingleLine(false); t.setMaxLines(4); t.setPadding(t.getPaddingLeft(), Ui.dp(a, 6), t.getPaddingRight(), Ui.dp(a, 6)); return t; }
                });
                for (int i = 0; i < shown.size(); i++) if (keepChecked.contains(shown.get(i))) lv.setItemChecked(i, true);
                keepChecked.clear();
                info.setText(Lang.F("{0} favori(s) affiché(s), {1} coché(s)", "{0} favorite(s) shown, {1} checked", shown.size(), lv.getCheckedItemCount()));
            }
        };
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (!filling[0]) { st.curFavList = pos; fill[0].run(); } }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                info.setText(Lang.F("{0} favori(s) affiché(s), {1} coché(s)", "{0} favorite(s) shown, {1} checked", shown.size(), lv.getCheckedItemCount()));
            }
        });
        col.addView(sp);
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        col.addView(info);

        LinearLayout r1 = Ui.row(a);
        r1.addView(Ui.button(a, Lang.T("Ajouter un favori…", "Add favorite…"), new View.OnClickListener() {
            public void onClick(View v) { editFavorite(a, null, "", "", Math.min(st.curFavList, st.favListCount - 1), fill[0]); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r1.addView(Ui.button(a, Lang.T("Modifier…", "Edit…"), new View.OnClickListener() {
            public void onClick(View v) {
                ArrayList<Favorite> c = checked(lv, shown);
                if (c.size() != 1) { Ui.toast(a, Lang.T("Cochez un seul favori.", "Check a single favorite.")); return; }
                editFavorite(a, c.get(0), c.get(0).name, c.get(0).url, c.get(0).list, fill[0]);
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r1.addView(Ui.button(a, Lang.T("Supprimer…", "Remove…"), new View.OnClickListener() {
            public void onClick(View v) {
                final ArrayList<Favorite> c = checked(lv, shown);
                if (c.isEmpty()) { Ui.toast(a, Lang.T("Sélectionnez d'abord un favori dans la liste.", "Please select a favorite in the list first.")); return; }
                String msg = c.size() == 1 ? Lang.F("Supprimer le favori « {0} » ?", "Remove favorite \"{0}\"?", c.get(0).name)
                    : Lang.F("Supprimer les {0} favoris cochés ?", "Remove the {0} checked favorites?", c.size());
                Ui.confirm(a, Lang.T("Supprimer…", "Remove…"), msg, new Ui.Yes() { public void yes() { st.favorites.removeAll(c); st.save(); fill[0].run(); } });
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r1);

        LinearLayout r2 = Ui.row(a);
        r2.addView(Ui.button(a, Lang.T("Déplacer vers ▸", "Move to ▸"), new View.OnClickListener() {
            public void onClick(View v) {
                final ArrayList<Favorite> c = checked(lv, shown);
                if (c.isEmpty()) { Ui.toast(a, Lang.T("Sélectionnez d'abord un favori dans la liste.", "Please select a favorite in the list first.")); return; }
                String[] names = new String[st.favListCount];
                for (int i = 0; i < names.length; i++) names[i] = st.favListLabel(i);
                Ui.choose(a, Lang.T("Déplacer vers ▸", "Move to ▸"), names, new Ui.Index() {
                    public void chose(int i) { for (Favorite f : c) f.list = i; keepChecked.addAll(c); st.save(); fill[0].run(); }
                });
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f, a, 2));
        r2.addView(Ui.button(a, "▲", new View.OnClickListener() { public void onClick(View v) { move(st, checked(lv, shown), -1, keepChecked); fill[0].run(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f, a, 2));
        r2.addView(Ui.button(a, "▼", new View.OnClickListener() { public void onClick(View v) { move(st, checked(lv, shown), 1, keepChecked); fill[0].run(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.6f, a, 2));
        r2.addView(Ui.button(a, Lang.T("Tout cocher", "Check all"), new View.OnClickListener() {
            public void onClick(View v) { for (int i = 0; i < shown.size(); i++) lv.setItemChecked(i, true); info.setText(Lang.F("{0} favori(s) affiché(s), {1} coché(s)", "{0} favorite(s) shown, {1} checked", shown.size(), lv.getCheckedItemCount())); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(Ui.button(a, Lang.T("Tout décocher", "Uncheck all"), new View.OnClickListener() {
            public void onClick(View v) { lv.clearChoices(); lv.requestLayout(); for (int i = 0; i < shown.size(); i++) lv.setItemChecked(i, false); info.setText(Lang.F("{0} favori(s) affiché(s), {1} coché(s)", "{0} favorite(s) shown, {1} checked", shown.size(), lv.getCheckedItemCount())); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r2);

        LinearLayout r3 = Ui.row(a);
        r3.addView(Ui.button(a, Lang.T("Vérifier les favoris", "Check favorites"), new View.OnClickListener() {
            public void onClick(View v) { checkFavorites(a, shown, status, lv, info, fill[0]); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f, a, 2));
        r3.addView(Ui.button(a, Lang.T("Importer…", "Import…"), new View.OnClickListener() {
            public void onClick(View v) { importFavorites(a, fill[0]); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r3.addView(Ui.button(a, Lang.T("Exporter…", "Export…"), new View.OnClickListener() {
            public void onClick(View v) { exportFavorites(a, shown); }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r3);

        LinearLayout r4 = Ui.row(a);
        r4.addView(Ui.button(a, Lang.T("Nouvelle liste", "New list"), new View.OnClickListener() {
            public void onClick(View v) {
                if (st.favListCount >= Favorite.MAX_LISTS) return;
                st.curFavList = st.favListCount++;
                a.status(Lang.F("Liste « {0} » ajoutée ({1}/{2}).", "List \"{0}\" added ({1}/{2}).", st.favListLabel(st.curFavList), st.favListCount, Favorite.MAX_LISTS));
                fill[0].run();
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r4.addView(Ui.button(a, Lang.T("Renommer la liste", "Rename list"), new View.OnClickListener() {
            public void onClick(View v) {
                final int i = st.curFavList;
                if (i >= st.favListCount) return;
                Ui.input(a, Lang.F("Renommer « {0} »…", "Rename \"{0}\"…", st.favListLabel(i)), "", st.favListLabel(i), new Ui.Text() {
                    public void got(String t) { if (t.trim().length() > 0) { st.favListNames[i] = t.trim(); fill[0].run(); } }
                });
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r4.addView(Ui.button(a, Lang.T("Supprimer la liste…", "Delete list…"), new View.OnClickListener() {
            public void onClick(View v) {
                final int i = st.curFavList;
                if (i >= st.favListCount || st.favListCount <= 1) return;
                int n = 0;
                for (Favorite f : st.favorites) if (f.list == i) n++;
                String msg = n == 0 ? Lang.F("Supprimer la liste « {0} » (vide) ?", "Delete the list \"{0}\" (empty)?", st.favListLabel(i))
                    : Lang.F("Supprimer la liste « {0} » et ses {1} favori(s) ?", "Delete the list \"{0}\" and its {1} favorite(s)?", st.favListLabel(i), n);
                Ui.confirm(a, Lang.T("Supprimer la liste…", "Delete list…"), msg, new Ui.Yes() {
                    public void yes() {
                        for (int k = st.favorites.size() - 1; k >= 0; k--) if (st.favorites.get(k).list == i) st.favorites.remove(k);
                        for (Favorite f : st.favorites) if (f.list > i) f.list--;
                        for (int k = i; k < Favorite.MAX_LISTS - 1; k++) st.favListNames[k] = st.favListNames[k + 1];
                        st.favListNames[Favorite.MAX_LISTS - 1] = "Liste " + Favorite.MAX_LISTS;
                        st.favListCount--;
                        st.curFavList = Math.max(0, Math.min(i, st.favListCount - 1));
                        fill[0].run();
                    }
                });
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r4);

        LinearLayout r5 = Ui.row(a);
        r5.setGravity(Gravity.END);
        r5.addView(Ui.button(a, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }));
        col.addView(r5);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() { public void onDismiss(android.content.DialogInterface x) { st.save(); } });
        fill[0].run();
        d.show();
    }

    static ArrayList<Favorite> checked(ListView lv, ArrayList<Favorite> shown) {
        ArrayList<Favorite> r = new ArrayList<Favorite>();
        SparseBooleanArray s = lv.getCheckedItemPositions();
        for (int i = 0; i < shown.size(); i++) if (s.get(i)) r.add(shown.get(i));
        return r;
    }

    // Monte ou descend les favoris cochés d'un rang, parmi ceux de la même liste.
    static void move(final Store st, final ArrayList<Favorite> sel, int dir, ArrayList<Favorite> keep) {
        keep.addAll(sel);
        ArrayList<Favorite> order = new ArrayList<Favorite>(sel);
        java.util.Collections.sort(order, new java.util.Comparator<Favorite>() {
            public int compare(Favorite x, Favorite y) { return st.favorites.indexOf(x) - st.favorites.indexOf(y); }
        });
        if (dir > 0) java.util.Collections.reverse(order);
        for (Favorite f : order) {
            int i = st.favorites.indexOf(f), j = i + dir;
            while (j >= 0 && j < st.favorites.size() && st.favorites.get(j).list != f.list) j += dir;
            if (j < 0 || j >= st.favorites.size() || sel.contains(st.favorites.get(j))) continue;
            st.favorites.remove(i);
            st.favorites.add(j, f);
        }
        st.save();
    }
    // Vérifie les adresses (réponse du serveur) ; supprime les doublons ; coche les liens morts pour que « Supprimer… » les retire.
    static void checkFavorites(final MainActivity a, final ArrayList<Favorite> shown, final HashMap<Favorite, String> status,
                               final ListView lv, final TextView info, final Runnable refresh) {
        final Store st = a.st;
        int dup = 0;
        for (Favorite f : new ArrayList<Favorite>(shown)) {
            for (Favorite g : st.favorites) {
                if (g != f && g.list == f.list && g.matches(f.url) && st.favorites.indexOf(g) < st.favorites.indexOf(f)) { st.favorites.remove(f); shown.remove(f); dup++; break; }
            }
        }
        final int dups = dup;
        final ArrayList<Favorite> todo = new ArrayList<Favorite>(shown);
        final int[] left = { todo.size() };
        final ArrayList<Favorite> dead = new ArrayList<Favorite>();
        info.setText(Lang.F("Vérification de {0} adresse(s)…", "Checking {0} address(es)…", todo.size()));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (final Favorite f : todo) {
            pool.execute(new Runnable() {
                public void run() {
                    final String s = probe(f.url);
                    UI.post(new Runnable() {
                        public void run() {
                            status.put(f, s);
                            if (!s.equals(Lang.T("répond", "answers")) && !s.equals(Lang.T("répond (certificat)", "answers (certificate)"))) dead.add(f);
                            left[0]--;
                            info.setText(Lang.F("Vérification… {0} restant(s)", "Checking… {0} left", left[0]));
                            if (left[0] == 0) {
                                refresh.run();
                                for (int i = 0; i < shown.size(); i++) if (dead.contains(shown.get(i))) lv.setItemChecked(i, true);
                                info.setText(Lang.F("Vérification terminée : {1} doublon(s) retiré(s), {0} lien(s) mort(s). Appuyez sur « Supprimer… » pour les retirer.",
                                    "Check done: {1} duplicate(s) removed, {0} dead link(s) checked. Click \"Remove…\" to delete them.", dead.size(), dups));
                                st.save();
                            }
                        }
                    });
                }
            });
        }
        pool.shutdown();
        if (todo.isEmpty()) refresh.run();
    }

    static String probe(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "PhoneSDRLink/1.0 (F5PBG)");
            c.setInstanceFollowRedirects(true);
            c.getResponseCode();
            c.disconnect();
            return Lang.T("répond", "answers");
        } catch (javax.net.ssl.SSLException e) {
            return Lang.T("répond (certificat)", "answers (certificate)");
        } catch (java.net.UnknownHostException e) {
            return Lang.T("hôte inconnu", "unknown host");
        } catch (java.net.ConnectException e) {
            return Lang.T("connexion refusée", "connection refused");
        } catch (java.net.MalformedURLException e) {
            return Lang.T("adresse invalide", "invalid address");
        } catch (Exception e) {
            return Lang.T("pas de réponse", "no answer");
        }
    }

    // Import : page de favoris HTML d'un navigateur, CSV/texte « nom,adresse », ou simple liste d'adresses.
    static void importFavorites(final MainActivity a, final Runnable refresh) {
        a.pickFile(false, null, new MainActivity.FileCb() {
            public void done(Uri uri) {
                Store st = a.st;
                String text;
                try { text = Store.readAll(a.getContentResolver().openInputStream(uri)); }
                catch (Exception e) { Ui.message(a, Lang.T("Importer…", "Import…"), String.valueOf(e.getMessage())); return; }
                ArrayList<Favorite> found = new ArrayList<Favorite>();
                Matcher m = Pattern.compile("<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text);
                while (m.find()) found.add(new Favorite(m.group(2).replaceAll("<[^>]*>", "").trim(), m.group(1)));
                if (found.isEmpty()) {
                    for (String line : text.split("\r?\n")) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        Matcher u = Pattern.compile("(https?://[^\\s,;\"|]+)").matcher(line);
                        if (!u.find()) continue;
                        String url = u.group(1), name = (line.substring(0, u.start()) + line.substring(u.end())).replaceAll("[,;\"|\\d\\s]+$", "").replaceAll("^[,;\"|\\s]+", "").trim();
                        found.add(new Favorite(name, url));
                    }
                }
                int added = 0, present = 0, list = Math.min(st.curFavList, st.favListCount - 1);
                for (Favorite f : found) {
                    boolean dup = false;
                    for (Favorite g : st.favorites) if (g.list == list && g.matches(f.url)) dup = true;
                    if (dup) present++; else { f.list = list; st.favorites.add(f); added++; }
                }
                st.save();
                refresh.run();
                Ui.message(a, Lang.T("Importer des favoris", "Import favorites"), Lang.F("{0} favori(s) importé(s), {1} déjà présent(s).", "{0} favorite(s) imported, {1} already present.", added, present));
            }
        });
    }

    static void exportFavorites(final MainActivity a, final ArrayList<Favorite> shown) {
        a.pickFile(true, "PhoneSDRLink-favoris.csv", new MainActivity.FileCb() {
            public void done(Uri uri) {
                try {
                    StringBuilder sb = new StringBuilder();
                    for (Favorite f : shown) sb.append('"').append(f.name.replace("\"", "'")).append("\",").append(f.url).append("\r\n");
                    OutputStream o = a.getContentResolver().openOutputStream(uri, "wt");
                    o.write(sb.toString().getBytes("UTF-8"));
                    o.close();
                    Ui.message(a, Lang.T("Exporter les favoris", "Export favorites"), Lang.F("{0} favori(s) exporté(s).", "{0} favorite(s) exported.", shown.size()));
                } catch (Exception e) { Ui.message(a, Lang.T("Exporter les favoris", "Export favorites"), String.valueOf(e.getMessage())); }
            }
        });
    }

    // ------------------------------------------------------------------ recherche de WebSDR

    static void search(final MainActivity a) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 10));
        final Dialog d = Ui.window(a, Lang.T("Rechercher des WebSDR (France et pays voisins)", "Find WebSDRs (France and neighbouring countries)"), col, true);

        col.addView(Ui.small(a, Lang.T("Distance max du centre de la France (km) :", "Max distance from the centre of France (km):")));
        final EditText dist = Ui.edit(a, "1000", "", InputType.TYPE_CLASS_NUMBER);
        col.addView(dist);
        LinearLayout fr = Ui.row(a);
        final EditText filter = Ui.edit(a, "", Lang.T("Filtre :", "Filter:"), InputType.TYPE_CLASS_TEXT);
        ArrayList<String> types = new ArrayList<String>();
        types.add(Lang.T("Tous", "All"));
        types.add("websdr.org");
        types.add("KiwiSDR");
        types.add("OpenWebRX");
        types.add("WebSDR");
        final Spinner sType = Ui.spinner(a, types);
        fr.addView(filter, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        fr.addView(sType, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        col.addView(fr);

        final ListView lv = new ListView(a);
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        final TextView info = Ui.small(a, "");
        col.addView(info);
        col.addView(Ui.small(a, Lang.T("Cochez pour ajouter aux favoris ; appui long sur un récepteur : l'écouter.", "Tick to add to favorites; long-press a receiver to listen to it.")));

        final ArrayList<FoundSdr> shown = new ArrayList<FoundSdr>();
        final java.util.HashSet<String> checkedKeys = new java.util.HashSet<String>();
        final String[] report = { "" };
        final int[] hiddenFavs = { 0 };
        final Runnable[] fill = new Runnable[1], statusRun = new Runnable[1];
        statusRun[0] = new Runnable() {
            public void run() {
                if (cache == null && report[0].isEmpty()) return;
                info.setText(Lang.F("{0} récepteur(s) affiché(s), {1} coché(s), {2} déjà en favoris masqué(s)   —   sources : ", "{0} receiver(s) shown, {1} checked, {2} already in favorites hidden   —   sources: ",
                    shown.size(), checkedKeys.size(), hiddenFavs[0]) + report[0]);
            }
        };
        fill[0] = new Runnable() {
            public void run() {
                shown.clear();
                hiddenFavs[0] = 0;
                if (cache != null) {
                    double max = 1000;
                    try { max = Double.parseDouble(dist.getText().toString().trim()); } catch (Exception e) { }
                    String q = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
                    int ti = sType.getSelectedItemPosition();
                    String type = ti > 0 ? (String) sType.getSelectedItem() : null;
                    for (FoundSdr s : cache) {
                        if (s.distanceKm > max) continue;
                        if (type != null && !s.type.equalsIgnoreCase(type)) continue;
                        if (!q.isEmpty() && !(s.name + " " + s.location + " " + s.country + " " + s.url).toLowerCase(Locale.ROOT).contains(q)) continue;
                        boolean isFav = false;
                        for (Favorite f : st.favorites) if (f.matches(s.url)) isFav = true;
                        if (isFav) { hiddenFavs[0]++; continue; }
                        shown.add(s);
                        if (shown.size() >= 600) break;
                    }
                }
                ArrayList<String> rows = new ArrayList<String>();
                for (FoundSdr s : shown)
                    rows.add(s.name + "\n" + s.location + (s.country.isEmpty() ? "" : " — " + s.country) + " — " + s.type + " — " + Math.round(s.distanceKm) + " km"
                        + (s.users.isEmpty() ? "" : " — " + s.users + " " + Lang.T("util.", "users")));
                lv.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_list_item_multiple_choice, rows) {
                    public View getView(int p, View v, ViewGroup g) { CheckedTextView t = (CheckedTextView) super.getView(p, v, g); t.setTextColor(Ui.fg()); t.setTextSize(14); t.setMinHeight(0); t.setMinimumHeight(Ui.dp(a, 56)); t.setSingleLine(false); t.setMaxLines(4); t.setPadding(t.getPaddingLeft(), Ui.dp(a, 6), t.getPaddingRight(), Ui.dp(a, 6)); return t; }
                });
                for (int i = 0; i < shown.size(); i++) if (checkedKeys.contains(Finder.urlKey(shown.get(i).url))) lv.setItemChecked(i, true);
                statusRun[0].run();
            }
        };
        final Runnable load = new Runnable() {
            public void run() {
                info.setText(Lang.T("Recherche dans les annuaires (websdr.org, KiwiSDR, ReceiverBook)…", "Searching directories (websdr.org, KiwiSDR, ReceiverBook)…"));
                new Thread(new Runnable() {
                    public void run() {
                        final ArrayList<String> rep = new ArrayList<String>();
                        final List<FoundSdr> list = Finder.loadAll(rep);
                        UI.post(new Runnable() {
                            public void run() {
                                if (!d.isShowing()) return;
                                cache = list;
                                StringBuilder sb = new StringBuilder();
                                for (String s : rep) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
                                report[0] = sb.toString();
                                fill[0].run();
                            }
                        });
                    }
                }).start();
            }
        };
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a1, int b, int c) { }
            public void onTextChanged(CharSequence s, int a1, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) { saveChecks(lv, shown, checkedKeys); fill[0].run(); }
        };
        dist.addTextChangedListener(tw);
        filter.addTextChangedListener(tw);
        sType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { saveChecks(lv, shown, checkedKeys); fill[0].run(); }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                String k = Finder.urlKey(shown.get(pos).url);
                if (lv.isItemChecked(pos)) checkedKeys.add(k); else checkedKeys.remove(k);
                statusRun[0].run();
            }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { d.dismiss(); a.openUrl(shown.get(pos).url); return true; }
        });

        LinearLayout r1 = Ui.row(a);
        r1.addView(Ui.button(a, Lang.T("Ajouter les cochés aux favoris", "Add checked to favorites"), new View.OnClickListener() {
            public void onClick(View v) {
                if (checkedKeys.isEmpty()) { Ui.message(a, Lang.T("Ajouter aux favoris", "Add to favorites"), Lang.T("Cochez d'abord les récepteurs à ajouter.", "Please check the receivers to add first.")); return; }
                String[] names = new String[st.favListCount];
                for (int i = 0; i < names.length; i++) names[i] = st.favListLabel(i);
                Ui.choose(a, Lang.T("Enregistrer dans la liste :", "Save to list:"), names, new Ui.Index() {
                    public void chose(int list) {
                        int n = 0;
                        for (FoundSdr s : cache) {
                            String key = Finder.urlKey(s.url);
                            if (!checkedKeys.contains(key)) continue;
                            boolean dup = false;
                            for (Favorite f : st.favorites) if (f.list == list && f.matches(s.url)) dup = true;
                            if (dup) continue;
                            String name = s.name.length() > 90 ? s.name.substring(0, 90).trim() + "…" : s.name;
                            if (!s.country.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(s.country.toLowerCase(Locale.ROOT))) name += " (" + s.country + ")";
                            Favorite f = new Favorite(name, s.url);
                            f.list = list;
                            st.favorites.add(f);
                            n++;
                        }
                        checkedKeys.clear();
                        st.save();
                        fill[0].run();
                        Ui.message(a, Lang.T("Ajouter aux favoris", "Add to favorites"), Lang.F("{0} favori(s) ajouté(s) à « {1} ». Vous pouvez les renommer dans « Gérer les favoris… ».",
                            "{0} favorite(s) added to \"{1}\". You can rename them in \"Manage favorites…\".", n, st.favListLabel(list)));
                    }
                });
            }
        }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r1);
        LinearLayout r2 = Ui.row(a);
        r2.addView(Ui.button(a, Lang.T("Actualiser", "Refresh"), new View.OnClickListener() { public void onClick(View v) { load.run(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        r2.addView(Ui.button(a, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        col.addView(r2);
        d.show();
        if (cache == null) load.run(); else fill[0].run();
    }

    static void saveChecks(ListView lv, ArrayList<FoundSdr> shown, java.util.HashSet<String> keys) {
        SparseBooleanArray s = lv.getCheckedItemPositions();
        for (int i = 0; i < shown.size(); i++) {
            String k = Finder.urlKey(shown.get(i).url);
            if (s.get(i)) keys.add(k); else keys.remove(k);
        }
    }
}
