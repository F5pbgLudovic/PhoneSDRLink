package fr.f5pbg.phonesdrlink;

import android.app.Dialog;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Bouton WEBSDR : choisir un favori (par liste), saisir une adresse, ajouter ou retirer la page des favoris, gérer les favoris,
// rechercher des WebSDR dans les annuaires publics (mêmes fenêtres et mêmes réglages que SDRLink).
final class DlgWebSdr {
    static final Handler UI = new Handler(Looper.getMainLooper());
    static List<FoundSdr> cache;
    // Position dans la liste des favoris (par liste) : on retrouve la même place en rouvrant la fenêtre.
    static final HashMap<Integer, int[]> LIST_POS = new HashMap<Integer, int[]>();

    static void savePos(ListView lv, int list) {
        View c = lv.getChildAt(0);
        LIST_POS.put(list, new int[] { lv.getFirstVisiblePosition(), c == null ? 0 : c.getTop() });
    }

    static void restorePos(final ListView lv, int list) {
        final int[] p = LIST_POS.get(list);
        if (p != null) lv.post(new Runnable() { public void run() { lv.setSelectionFromTop(p[0], p[1]); } });
    }

    static LinearLayout.LayoutParams w(MainActivity a, float weight) { return Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight, a, 2); }

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
        final String[] query = { "" };
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
                for (Favorite f : st.favorites) {
                    if (!(st.curFavList >= st.favListCount || f.list == st.curFavList)) continue;
                    if (!query[0].isEmpty() && !(f.name.toLowerCase().contains(query[0]) || f.url.toLowerCase().contains(query[0]))) continue;
                    shown.add(f);
                }
                ArrayList<String> rows = new ArrayList<String>();
                for (Favorite f : shown) rows.add(f.name);
                // Grandes cases : un WebSDR par case, bien visible et facile à toucher.
                lv.setAdapter(new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, rows) {
                    public View getView(int p, View v, ViewGroup g) {
                        LinearLayout box = new LinearLayout(a);
                        box.setPadding(Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4), Ui.dp(a, 4));
                        TextView t = new TextView(a);
                        t.setText(getItem(p));
                        t.setTextColor(Ui.fg());
                        t.setTextSize(16);
                        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        t.setMaxLines(3);
                        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        t.setGravity(Gravity.CENTER_VERTICAL);
                        t.setMinHeight(Ui.dp(a, 68));
                        t.setPadding(Ui.dp(a, 16), Ui.dp(a, 10), Ui.dp(a, 16), Ui.dp(a, 10));
                        t.setBackground(Ui.round(Ui.btn(), 14, a));
                        box.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                        return box;
                    }
                });
            }
        };
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (!filling[0]) { savePos(lv, st.curFavList); st.curFavList = pos; fill[0].run(); restorePos(lv, pos); } }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        lv.setDivider(null);
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                d.dismiss();
                a.openUrl(shown.get(pos).url);
            }
        });
        // Loupe à droite du nom de la liste : un champ de recherche filtre la liste en cours (nom ou adresse).
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
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

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
        }), w(a, 1));
        r1.addView(Ui.button(a, Lang.T("Retirer des favoris", "Remove from favorites"), new View.OnClickListener() {
            public void onClick(View v) { removeCurrent(a, fill[0]); }
        }), w(a, 1));
        col.addView(r1);
        LinearLayout r2 = Ui.row(a);
        r2.addView(Ui.button(a, Lang.T("Gérer les favoris…", "Manage favorites…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); manage(a); }
        }), w(a, 1));
        r2.addView(Ui.button(a, Lang.T("Chercher des WebSDR…", "Find WebSDRs…"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); search(a); }
        }), w(a, 1));
        col.addView(r2);
        LinearLayout r3 = Ui.row(a);
        r3.addView(Ui.button(a, Lang.T("Recharger", "Reload"), new View.OnClickListener() {
            public void onClick(View v) { d.dismiss(); a.web.reload(); }
        }), w(a, 1));
        r3.addView(Ui.button(a, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }), w(a, 1));
        col.addView(r3);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() { public void onDismiss(android.content.DialogInterface x) { savePos(lv, st.curFavList); st.save(); } });
        fill[0].run();
        restorePos(lv, st.curFavList);
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

    // Retire la page affichée de la liste choisie.
    static void removeCurrent(final MainActivity a, final Runnable refresh) {
        final Store st = a.st;
        final String url = a.currentUrl();
        int list = st.curFavList >= st.favListCount ? -1 : st.curFavList;
        Favorite found = null;
        for (Favorite f : st.favorites) if (f.matches(url) && (list < 0 || f.list == list)) found = f;
        if (found == null) {
            Ui.message(a, Lang.T("Retirer des favoris", "Remove from favorites"), Lang.F("Cette page n'est pas dans « {0} ».", "This page is not in \"{0}\".", list < 0 ? Lang.T("Toutes les listes", "All lists") : st.favListLabel(list)));
            return;
        }
        final Favorite fav = found;
        Ui.confirm(a, Lang.T("Retirer des favoris", "Remove from favorites"), Lang.F("Retirer « {0} » de « {1} » ?", "Remove \"{0}\" from \"{1}\"?", fav.name, st.favListLabel(fav.list)), new Ui.Yes() {
            public void yes() { st.favorites.remove(fav); st.save(); refresh.run(); }
        });
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
        final TextView info = Ui.small(a, "");
        final HashMap<Favorite, String> status = new HashMap<Favorite, String>();
        final CheckList<Favorite> cl = new CheckList<Favorite>(a, new CheckList.Fmt<Favorite>() {
            public String title(Favorite f) { return f.name; }
            public String sub(Favorite f) { return f.url + (status.containsKey(f) ? "   [" + status.get(f) + "]" : ""); }
        });
        // Couleur d'état d'après les vérifications : vert = répond ; orange = sans réponse depuis une semaine ; rouge = depuis un mois.
        cl.tint = new CheckList.Tint<Favorite>() {
            public int color(Favorite f) {
                int s = f.health(System.currentTimeMillis());
                return s == 1 ? 0xFF43A047 : s == 3 ? 0xFFFF9800 : s == 4 ? 0xFFE53935 : 0;
            }
        };
        lv.setAdapter(cl);
        final boolean[] filling = { false };
        final String[] query = { "" };
        final Runnable[] fill = new Runnable[1];
        final Runnable infoRun = new Runnable() {
            public void run() { info.setText(Lang.F("{0} favori(s) affiché(s), {1} coché(s)", "{0} favorite(s) shown, {1} checked", cl.items.size(), cl.checked.size())); }
        };
        cl.onChange = infoRun;
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
                ArrayList<Favorite> shown = new ArrayList<Favorite>();
                for (Favorite f : st.favorites) {
                    if (!(st.curFavList >= st.favListCount || f.list == st.curFavList)) continue;
                    if (!query[0].isEmpty() && !(f.name.toLowerCase().contains(query[0]) || f.url.toLowerCase().contains(query[0]))) continue;
                    shown.add(f);
                }
                cl.set(shown);
                infoRun.run();
            }
        };
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { if (!filling[0]) { st.curFavList = pos; fill[0].run(); } }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { cl.select(pos); }
        });
        // Loupe à droite du nom de la liste (comme dans la fenêtre WEBSDR) : filtre la liste en cours (nom ou adresse).
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
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        col.addView(info);

        LinearLayout r1 = Ui.row(a);
        r1.addView(Ui.button(a, Lang.T("Ajouter un favori…", "Add favorite…"), new View.OnClickListener() {
            public void onClick(View v) { editFavorite(a, null, "", "", Math.min(st.curFavList, st.favListCount - 1), fill[0]); }
        }), w(a, 1));
        r1.addView(Ui.button(a, Lang.T("Renommer…", "Rename…"), new View.OnClickListener() {
            public void onClick(View v) {
                ArrayList<Favorite> c = cl.checkedOrSelected();
                if (c.size() != 1) { Ui.toast(a, Lang.T("Sélectionnez d'abord un favori dans la liste.", "Please select a favorite in the list first.")); return; }
                editFavorite(a, c.get(0), c.get(0).name, c.get(0).url, c.get(0).list, fill[0]);
            }
        }), w(a, 1));
        r1.addView(Ui.button(a, Lang.T("Supprimer…", "Remove…"), new View.OnClickListener() {
            public void onClick(View v) {
                final ArrayList<Favorite> c = cl.checkedOrSelected();
                if (c.isEmpty()) { Ui.toast(a, Lang.T("Sélectionnez d'abord un favori dans la liste.", "Please select a favorite in the list first.")); return; }
                String msg = c.size() == 1 ? Lang.F("Supprimer le favori « {0} » ?", "Remove favorite \"{0}\"?", c.get(0).name)
                    : Lang.F("Supprimer les {0} favoris cochés ?", "Remove the {0} checked favorites?", c.size());
                Ui.confirm(a, Lang.T("Supprimer…", "Remove…"), msg, new Ui.Yes() { public void yes() { st.favorites.removeAll(c); st.save(); fill[0].run(); } });
            }
        }), w(a, 1));
        col.addView(r1);

        LinearLayout r2 = Ui.row(a);
        r2.addView(Ui.button(a, Lang.T("Écouter la sélection", "Listen to selection"), new View.OnClickListener() {
            public void onClick(View v) {
                ArrayList<Favorite> c = cl.checkedOrSelected();
                if (c.isEmpty()) { Ui.toast(a, Lang.T("Sélectionnez d'abord un favori dans la liste.", "Please select a favorite in the list first.")); return; }
                // La fenêtre reste ouverte : le WebSDR s'ouvre derrière et on l'entend pendant la gestion des favoris.
                a.openUrl(c.get(0).url);
                info.setText("♪ " + Lang.F("Écoute de « {0} »", "Listening to \"{0}\"", c.get(0).name));
            }
        }), w(a, 1.0f));
        r2.addView(Ui.button(a, Lang.T("Déplacer vers ▸", "Move to ▸"), new View.OnClickListener() {
            public void onClick(View v) {
                final ArrayList<Favorite> c = cl.checkedOrSelected();
                if (c.isEmpty()) { Ui.toast(a, Lang.T("Sélectionnez d'abord un favori dans la liste.", "Please select a favorite in the list first.")); return; }
                String[] names = new String[st.favListCount];
                for (int i = 0; i < names.length; i++) names[i] = st.favListLabel(i);
                Ui.choose(a, Lang.T("Déplacer vers ▸", "Move to ▸"), names, new Ui.Index() {
                    public void chose(int i) {
                        for (Favorite f : c) f.list = i;
                        st.save();
                        a.status(Lang.F("{0} favori(s) déplacé(s) vers « {1} ».", "{0} favorite(s) moved to \"{1}\".", c.size(), st.favListLabel(i)));
                        fill[0].run();
                    }
                });
            }
        }), w(a, 1));
        r2.addView(Ui.button(a, "▲", new View.OnClickListener() { public void onClick(View v) { move(st, cl.checkedOrSelected(), -1); fill[0].run(); } }), w(a, 0.5f));
        r2.addView(Ui.button(a, "▼", new View.OnClickListener() { public void onClick(View v) { move(st, cl.checkedOrSelected(), 1); fill[0].run(); } }), w(a, 0.5f));
        android.widget.Button top = Ui.button(a, "▲▲", new View.OnClickListener() { public void onClick(View v) { moveTop(st, cl.checkedOrSelected()); fill[0].run(); } });
        top.setSingleLine(true);
        top.setPadding(0, 0, 0, 0);
        top.setTextSize(13);   // les deux flèches tiennent sur une ligne, même dimension de touche que ▲ et ▼
        r2.addView(top, w(a, 0.5f));
        col.addView(r2);

        LinearLayout r3 = Ui.row(a);
        r3.addView(Ui.button(a, Lang.T("Tout cocher", "Check all"), new View.OnClickListener() { public void onClick(View v) { cl.checkAll(true); } }), w(a, 1));
        r3.addView(Ui.button(a, Lang.T("Tout décocher", "Uncheck all"), new View.OnClickListener() { public void onClick(View v) { cl.checkAll(false); } }), w(a, 1));
        r3.addView(Ui.button(a, Lang.T("Vérifier les favoris", "Check favorites"), new View.OnClickListener() {
            public void onClick(View v) { checkFavorites(a, cl, status, info, fill[0]); }
        }), w(a, 1.3f));
        col.addView(r3);

        LinearLayout r4 = Ui.row(a);
        r4.addView(Ui.button(a, Lang.T("Importer…", "Import…"), new View.OnClickListener() { public void onClick(View v) { importFavorites(a, fill[0]); } }), w(a, 1));
        r4.addView(Ui.button(a, Lang.T("Exporter…", "Export…"), new View.OnClickListener() { public void onClick(View v) { exportFavorites(a, cl.items); } }), w(a, 1));
        r4.addView(Ui.button(a, Lang.T("Nouvelle liste", "New list"), new View.OnClickListener() {
            public void onClick(View v) {
                if (st.favListCount >= Favorite.MAX_LISTS) return;
                st.curFavList = st.favListCount++;
                a.status(Lang.F("Liste « {0} » ajoutée ({1}/{2}).", "List \"{0}\" added ({1}/{2}).", st.favListLabel(st.curFavList), st.favListCount, Favorite.MAX_LISTS));
                fill[0].run();
            }
        }), w(a, 1));
        col.addView(r4);

        LinearLayout r5 = Ui.row(a);
        r5.addView(Ui.button(a, Lang.T("Renommer la liste", "Rename list"), new View.OnClickListener() {
            public void onClick(View v) {
                final int i = st.curFavList;
                if (i >= st.favListCount) return;
                Ui.input(a, Lang.F("Renommer « {0} »…", "Rename \"{0}\"…", st.favListLabel(i)), "", st.favListLabel(i), new Ui.Text() {
                    public void got(String t) { if (t.trim().length() > 0) { st.favListNames[i] = t.trim(); fill[0].run(); } }
                });
            }
        }), w(a, 1));
        r5.addView(Ui.button(a, Lang.T("Supprimer la liste…", "Delete list…"), new View.OnClickListener() {
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
                        st.save();
                        a.status(Lang.F("Liste « {0} » supprimée.", "List \"{0}\" deleted.", st.favListLabel(st.curFavList)));
                        fill[0].run();
                    }
                });
            }
        }), w(a, 1));
        r5.addView(Ui.button(a, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }), w(a, 0.7f));
        col.addView(r5);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() { public void onDismiss(android.content.DialogInterface x) { st.save(); } });
        fill[0].run();
        d.show();
    }

    // Monte ou descend les favoris choisis d'un rang, parmi ceux de la même liste.
    static void move(final Store st, final ArrayList<Favorite> sel, int dir) {
        ArrayList<Favorite> order = new ArrayList<Favorite>(sel);
        Collections.sort(order, new Comparator<Favorite>() {
            public int compare(Favorite x, Favorite y) { return st.favorites.indexOf(x) - st.favorites.indexOf(y); }
        });
        if (dir > 0) Collections.reverse(order);
        for (Favorite f : order) {
            int i = st.favorites.indexOf(f), j = i + dir;
            while (j >= 0 && j < st.favorites.size() && st.favorites.get(j).list != f.list) j += dir;
            if (j < 0 || j >= st.favorites.size() || sel.contains(st.favorites.get(j))) continue;
            st.favorites.remove(i);
            st.favorites.add(j, f);
        }
        st.save();
    }

    // Met les favoris choisis tout en haut de leur liste (leur ordre entre eux est conservé).
    static void moveTop(final Store st, ArrayList<Favorite> sel) {
        ArrayList<Favorite> order = new ArrayList<Favorite>(sel);
        Collections.sort(order, new Comparator<Favorite>() {
            public int compare(Favorite x, Favorite y) { return st.favorites.indexOf(y) - st.favorites.indexOf(x); }
        });
        for (Favorite f : order) {   // du plus bas au plus haut : le plus haut finit en premier
            int first = -1;
            for (int i = 0; i < st.favorites.size(); i++) if (st.favorites.get(i).list == f.list) { first = i; break; }
            int cur = st.favorites.indexOf(f);
            if (first < 0 || cur <= first) continue;
            st.favorites.remove(cur);
            st.favorites.add(first, f);
        }
        st.save();
    }

    // Vérifie les adresses (réponse du serveur) ; retire les doublons ; coche les liens morts pour que « Supprimer… » les retire.
    static void checkFavorites(final MainActivity a, final CheckList<Favorite> cl, final HashMap<Favorite, String> status,
                               final TextView info, final Runnable refresh) {
        final Store st = a.st;
        int dup = 0;
        for (Favorite f : new ArrayList<Favorite>(cl.items)) {
            for (Favorite g : st.favorites) {
                if (g != f && g.list == f.list && g.matches(f.url) && st.favorites.indexOf(g) < st.favorites.indexOf(f)) { st.favorites.remove(f); dup++; break; }
            }
        }
        refresh.run();
        final int dups = dup;
        final ArrayList<Favorite> todo = new ArrayList<Favorite>(cl.items);
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
                            long now = System.currentTimeMillis();
                            if (!s.equals(Lang.T("répond", "answers")) && !s.equals(Lang.T("répond (certificat)", "answers (certificate)"))) {
                                dead.add(f);
                                if (f.firstFail == 0) f.firstFail = now;   // l'ancienneté est mesurée depuis la dernière réponse connue (sinon depuis ce constat)
                            } else {
                                f.lastOk = now;   // il répond : vert, compteur remis à zéro
                                f.firstFail = 0;
                            }
                            left[0]--;
                            info.setText(Lang.F("Vérification… {0} restant(s)", "Checking… {0} left", left[0]));
                            if (left[0] == 0) {
                                cl.checked.clear();
                                cl.checked.addAll(dead);
                                cl.notifyDataSetChanged();   // couleurs d'état mises à jour
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

    static void exportFavorites(final MainActivity a, final List<Favorite> shown) {
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

    static int userCount(FoundSdr s) {
        try { return Integer.parseInt(s.users.split("/")[0].trim()); } catch (Exception e) { return -1; }
    }

    static void search(final MainActivity a) {
        final Store st = a.st;
        LinearLayout col = Ui.column(a);
        col.setPadding(Ui.dp(a, 10), Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 10));
        final Dialog d = Ui.window(a, Lang.T("Rechercher des WebSDR (France et pays voisins)", "Find WebSDRs (France and neighbouring countries)"), col, true);

        LinearLayout dr = Ui.row(a);
        dr.addView(Ui.small(a, Lang.T("Distance max du centre de la France (km) :", "Max distance from the centre of France (km):")), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        final EditText dist = Ui.edit(a, "1000", "", InputType.TYPE_CLASS_NUMBER);
        dr.addView(dist, Ui.lp(Ui.dp(a, 90), ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        col.addView(dr);
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
        LinearLayout so = Ui.row(a);
        so.addView(Ui.small(a, Lang.T("Trier par :", "Sort by:")), Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 4));
        ArrayList<String> cols = new ArrayList<String>();
        cols.add(Lang.T("Nom", "Name"));
        cols.add(Lang.T("Lieu", "Location"));
        cols.add(Lang.T("Pays", "Country"));
        cols.add("Type");
        cols.add("km");
        cols.add(Lang.T("Util.", "Users"));
        final Spinner sSort = Ui.spinner(a, cols);
        sSort.setSelection(4);
        final boolean[] desc = { false };
        final android.widget.Button bOrder = Ui.button(a, "↑", null);
        so.addView(sSort, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, a, 2));
        so.addView(bOrder, Ui.lp(Ui.dp(a, 56), ViewGroup.LayoutParams.WRAP_CONTENT, 0, a, 2));
        col.addView(so);

        final ListView lv = new ListView(a);
        final TextView info = Ui.small(a, "");
        final String[] report = { "" };
        final int[] hiddenFavs = { 0 };
        final HashSet<String> checkedKeys = new HashSet<String>();
        final CheckList<FoundSdr> cl = new CheckList<FoundSdr>(a, new CheckList.Fmt<FoundSdr>() {
            public String title(FoundSdr s) { return s.name.isEmpty() ? s.url : s.name; }
            public String sub(FoundSdr s) {
                return s.location + (s.country.isEmpty() ? "" : " — " + s.country) + " — " + s.type + " — " + Math.round(s.distanceKm) + " km"
                    + (s.users.isEmpty() ? "" : " — " + s.users + " " + Lang.T("util.", "users"));
            }
        });
        lv.setAdapter(cl);
        col.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        col.addView(info);

        final Runnable statusRun = new Runnable() {
            public void run() {
                if (cache == null && report[0].isEmpty()) return;
                info.setText(Lang.F("{0} récepteur(s) affiché(s), {1} coché(s), {2} déjà en favoris masqué(s)   —   sources : ", "{0} receiver(s) shown, {1} checked, {2} already in favorites hidden   —   sources: ",
                    cl.items.size(), cl.checked.size(), hiddenFavs[0]) + report[0]);
            }
        };
        final Runnable fill = new Runnable() {
            public void run() {
                hiddenFavs[0] = 0;
                ArrayList<FoundSdr> shown = new ArrayList<FoundSdr>();
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
                    }
                    final int sortCol = sSort.getSelectedItemPosition();
                    final boolean dsc = desc[0];
                    Collections.sort(shown, new Comparator<FoundSdr>() {
                        public int compare(FoundSdr x, FoundSdr y) {
                            int c;
                            switch (sortCol) {
                                case 0: c = x.name.compareToIgnoreCase(y.name); break;
                                case 1: c = x.location.compareToIgnoreCase(y.location); break;
                                case 2: c = x.country.compareToIgnoreCase(y.country); break;
                                case 3: c = x.type.compareToIgnoreCase(y.type); break;
                                case 5: c = userCount(x) - userCount(y); break;
                                default: c = Double.compare(x.distanceKm, y.distanceKm);
                            }
                            return dsc ? -c : c;
                        }
                    });
                }
                for (FoundSdr s : shown) if (checkedKeys.contains(Finder.urlKey(s.url))) cl.checked.add(s);
                cl.set(shown);
                statusRun.run();
            }
        };
        // Les cases cochées sont retenues par adresse : elles survivent aux filtres, au tri et à l'actualisation.
        cl.onChange = new Runnable() {
            public void run() {
                for (FoundSdr s : cl.items) {
                    String k = Finder.urlKey(s.url);
                    if (cl.checked.contains(s)) checkedKeys.add(k); else checkedKeys.remove(k);
                }
                statusRun.run();
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
                                cl.checked.clear();
                                StringBuilder sb = new StringBuilder();
                                for (String s : rep) { if (sb.length() > 0) sb.append(", "); sb.append(s); }
                                report[0] = sb.toString();
                                fill.run();
                            }
                        });
                    }
                }).start();
            }
        };
        android.text.TextWatcher tw = new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a1, int b, int c) { }
            public void onTextChanged(CharSequence s, int a1, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) { fill.run(); }
        };
        dist.addTextChangedListener(tw);
        filter.addTextChangedListener(tw);
        AdapterView.OnItemSelectedListener again = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { fill.run(); }
            public void onNothingSelected(AdapterView<?> p) { }
        };
        sType.setOnItemSelectedListener(again);
        sSort.setOnItemSelectedListener(again);
        bOrder.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { desc[0] = !desc[0]; bOrder.setText(desc[0] ? "↓" : "↑"); fill.run(); }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) { cl.select(pos); }
        });
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) { d.dismiss(); a.openUrl(cl.items.get(pos).url); return true; }
        });

        LinearLayout r1 = Ui.row(a);
        r1.addView(Ui.button(a, Lang.T("Écouter la sélection", "Listen to selection"), new View.OnClickListener() {
            public void onClick(View v) {
                if (cl.selected == null) { Ui.message(a, "WebSDR", Lang.T("Sélectionnez d'abord un récepteur dans la liste.", "Please select a receiver in the list first.")); return; }
                d.dismiss();
                a.openUrl(cl.selected.url);
            }
        }), w(a, 1));
        r1.addView(Ui.button(a, Lang.T("Tout cocher", "Check all"), new View.OnClickListener() { public void onClick(View v) { cl.checkAll(true); } }), w(a, 0.8f));
        r1.addView(Ui.button(a, Lang.T("Tout décocher", "Uncheck all"), new View.OnClickListener() { public void onClick(View v) { cl.checkAll(false); } }), w(a, 0.8f));
        col.addView(r1);
        LinearLayout r2 = Ui.row(a);
        r2.addView(Ui.button(a, Lang.T("Ajouter les cochés aux favoris", "Add checked to favorites"), new View.OnClickListener() {
            public void onClick(View v) {
                if (checkedKeys.isEmpty()) { Ui.message(a, Lang.T("Ajouter aux favoris", "Add to favorites"), Lang.T("Cochez d'abord les récepteurs à ajouter.", "Please check the receivers to add first.")); return; }
                String[] names = new String[st.favListCount];
                for (int i = 0; i < names.length; i++) names[i] = st.favListLabel(i);
                Ui.choose(a, Lang.T("Enregistrer dans la liste :", "Save to list:"), names, new Ui.Index() {
                    public void chose(int list) {
                        int n = 0;
                        for (FoundSdr s : cache) {
                            if (!checkedKeys.contains(Finder.urlKey(s.url))) continue;
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
                        cl.checked.clear();
                        st.save();
                        fill.run();
                        Ui.message(a, Lang.T("Ajouter aux favoris", "Add to favorites"), Lang.F("{0} favori(s) ajouté(s) à « {1} ». Vous pouvez les renommer dans « Gérer les favoris… ».",
                            "{0} favorite(s) added to \"{1}\". You can rename them in \"Manage favorites…\".", n, st.favListLabel(list)));
                    }
                });
            }
        }), w(a, 1.6f));
        r2.addView(Ui.button(a, Lang.T("Actualiser", "Refresh"), new View.OnClickListener() { public void onClick(View v) { load.run(); } }), w(a, 1));
        r2.addView(Ui.button(a, Lang.T("Fermer", "Close"), new View.OnClickListener() { public void onClick(View v) { d.dismiss(); } }), w(a, 0.8f));
        col.addView(r2);
        d.show();
        if (cache == null) load.run(); else fill.run();
    }
}
