package fr.f5pbg.phonesdrlink;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;

// Langue de l'interface. Les textes sont écrits en français et en anglais dans le code ; les autres langues
// traduisent le texte anglais (assets/lang/xx.txt, mêmes fichiers que SDRLink), qui sert aussi de secours.
final class Lang {
    static final String[] CODES = { "fr", "en", "de", "es", "it", "nl", "pt", "pl", "cs", "sk", "sl", "hr", "hu", "ro", "bg", "el", "sv", "da", "nb", "fi", "et", "lv", "lt" };
    static final String[] NAMES = { "Français", "English", "Deutsch", "Español", "Italiano", "Nederlands", "Português", "Polski", "Čeština", "Slovenčina",
        "Slovenščina", "Hrvatski", "Magyar", "Română", "Български", "Ελληνικά", "Svenska", "Dansk", "Norsk", "Suomi", "Eesti", "Latviešu", "Lietuvių" };

    private static String code = "fr";
    private static HashMap<String, String> table;

    static String code() { return code; }

    // Langue du téléphone si elle fait partie des langues connues, sinon l'anglais.
    static String systemDefault() {
        String l = Locale.getDefault().getLanguage();
        if (l.equals("no")) l = "nb";
        for (String c : CODES) if (c.equals(l)) return c;
        return "en";
    }

    static void set(Context ctx, String c) {
        boolean known = false;
        for (String k : CODES) if (k.equals(c)) known = true;
        code = known ? c : "fr";
        table = null;
        if (code.equals("fr") || code.equals("en")) return;
        table = new HashMap<String, String>();
        try {
            InputStream in = ctx.getAssets().open("lang/" + code + ".txt");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t > 0) table.put(line.substring(0, t), line.substring(t + 1));
            }
            r.close();
        } catch (Exception e) { table = null; }
    }

    static String T(String fr, String en) {
        if (code.equals("fr")) return fr;
        if (table == null) return en;
        String s = table.get(en);
        return s != null ? s : en;
    }

    // Texte à trous : {0}, {1}... remplacés par les valeurs.
    static String F(String fr, String en, Object... args) {
        String s = T(fr, en);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }
}