package fr.f5pbg.phonesdrlink;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

// Liste à cases à cocher : la case coche, le reste de la rangée sélectionne (comme « coché » et « sélectionné » dans SDRLink).
// Les états sont gardés par élément (jamais par position) et chaque rangée est entièrement réécrite à chaque affichage :
// aucun reste d'une rangée recyclée ne peut apparaître en faisant défiler la liste.
final class CheckList<T> extends BaseAdapter {
    interface Fmt<T> { String title(T t); String sub(T t); }

    final Context ctx;
    final Fmt<T> fmt;
    final ArrayList<T> items = new ArrayList<T>();
    final HashSet<T> checked = new HashSet<T>();
    T selected;
    Runnable onChange;
    interface Tint<T> { int color(T t); }
    Tint<T> tint;

    CheckList(Context c, Fmt<T> f) { ctx = c; fmt = f; }

    void set(List<T> list) {
        items.clear();
        items.addAll(list);
        checked.retainAll(items);
        if (selected != null && !items.contains(selected)) selected = null;
        notifyDataSetChanged();
    }

    void checkAll(boolean on) {
        if (on) checked.addAll(items); else checked.clear();
        notifyDataSetChanged();
        if (onChange != null) onChange.run();
    }

    void select(int pos) {
        selected = pos >= 0 && pos < items.size() ? items.get(pos) : null;
        notifyDataSetChanged();
    }

    // Les éléments cochés ; à défaut, l'élément sélectionné.
    ArrayList<T> checkedOrSelected() {
        ArrayList<T> r = new ArrayList<T>();
        for (T t : items) if (checked.contains(t)) r.add(t);
        if (r.isEmpty() && selected != null) r.add(selected);
        return r;
    }

    public int getCount() { return items.size(); }
    public Object getItem(int i) { return items.get(i); }
    public long getItemId(int i) { return i; }

    @Override
    public View getView(final int pos, View v, ViewGroup parent) {
        LinearLayout row;
        CheckBox cb;
        TextView title, sub;
        if (v == null) {
            row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            cb = new CheckBox(ctx);
            cb.setFocusable(false);
            LinearLayout col = new LinearLayout(ctx);
            col.setOrientation(LinearLayout.VERTICAL);
            title = new TextView(ctx);
            title.setTextSize(15);
            title.setMaxLines(2);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub = new TextView(ctx);
            sub.setTextSize(12);
            sub.setMaxLines(2);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(title);
            col.addView(sub);
            int p = Ui.dp(ctx, 6);
            row.setPadding(p, p, p, p);
            row.addView(cb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setTag(new Object[] { cb, title, sub });
        } else {
            row = (LinearLayout) v;
        }
        Object[] tag = (Object[]) row.getTag();
        cb = (CheckBox) tag[0];
        title = (TextView) tag[1];
        sub = (TextView) tag[2];
        final T item = items.get(pos);
        title.setText(fmt.title(item));
        title.setTextColor(Ui.fg());
        String s = fmt.sub(item);
        sub.setText(s);
        sub.setTextColor(Ui.sub());
        int tc = tint == null ? 0 : tint.color(item);   // couleur d'état (vert, orange, rouge) ; 0 = couleurs normales
        if (tc != 0) { title.setTextColor(tc); sub.setTextColor(tc); }
        sub.setVisibility(s == null || s.isEmpty() ? View.GONE : View.VISIBLE);
        cb.setOnCheckedChangeListener(null);
        cb.setChecked(checked.contains(item));
        cb.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) {
                if (checked.contains(item)) checked.remove(item); else checked.add(item);
                if (onChange != null) onChange.run();
            }
        });
        row.setBackgroundColor(item == selected ? (Ui.dark ? 0xFF2B4A63 : 0xFFBFD9F2) : Color.TRANSPARENT);
        return row;
    }
}
