package fr.f5pbg.phonesdrlink;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

// Un récepteur trouvé dans les annuaires publics.
final class FoundSdr {
    String name = "", location = "", type = "", url = "", users = "", country = "";
    double lat, lon, distanceKm;
    private String k;
    String key() { if (k == null) { k = Finder.urlKey(url); if (k == null) k = ""; } return k; }
}

// Lecture des annuaires publics : websdr.org, KiwiSDR (kiwisdr.com via rx.linkfanel.net) et ReceiverBook (comme SDRLink).
final class Finder {
    static final double CENTER_LAT = 46.6, CENTER_LON = 2.4;
    static final String WEBSDR_ORG = "http://websdr.ewi.utwente.nl/~~websdrlistk?v=1&fmt=2&chseq=0";
    static final String KIWI = "http://rx.linkfanel.net/kiwisdr_com.js";
    static final String RECEIVERBOOK = "https://www.receiverbook.de/map";

    // Télécharge et fusionne les trois annuaires ; une source en panne n'empêche pas les autres.
    static List<FoundSdr> loadAll(List<String> report) {
        ArrayList<FoundSdr> all = new ArrayList<FoundSdr>();
        HashSet<String> seen = new HashSet<String>();
        load("websdr.org", WEBSDR_ORG, 0, all, seen, report);
        load("KiwiSDR", KIWI, 1, all, seen, report);
        load("ReceiverBook", RECEIVERBOOK, 2, all, seen, report);
        for (FoundSdr s : all) {
            s.distanceKm = distance(CENTER_LAT, CENTER_LON, s.lat, s.lon);
            s.country = guessCountry(s.name + " " + s.location + " " + s.url, s.lat, s.lon);
        }
        Collections.sort(all, new Comparator<FoundSdr>() {
            public int compare(FoundSdr a, FoundSdr b) { return Double.compare(a.distanceKm, b.distanceKm); }
        });
        return all;
    }

    private static void load(String source, String url, int kind, List<FoundSdr> all, HashSet<String> seen, List<String> report) {
        try {
            String text = download(url);
            List<FoundSdr> found = kind == 0 ? parseWebSdrOrg(text) : kind == 1 ? parseKiwi(text) : parseReceiverBook(text);
            int added = 0;
            for (FoundSdr s : found) {
                String key = urlKey(s.url);
                if (key == null || seen.contains(key)) continue;
                seen.add(key);
                all.add(s);
                added++;
            }
            report.add(source + " : " + added);
        } catch (Exception ex) {
            String msg = String.valueOf(ex.getMessage());
            if (msg.length() > 80) msg = msg.substring(0, 80) + "…";
            report.add(source + " : " + Lang.T("indisponible", "unavailable") + " (" + msg + ")");
        }
    }

    static String download(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "PhoneSDRLink/1.0 (F5PBG)");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        InputStream in = c.getInputStream();
        try { return Store.readAll(in); } finally { in.close(); c.disconnect(); }
    }

    private static String str(JSONObject d, String key) {
        Object v = d.opt(key);
        return v == null || v == JSONObject.NULL ? "" : String.valueOf(v).trim();
    }

    // websdr.org : tableau JSON [{url, desc, qth, lat, lon, users}, ...] précédé d'un préfixe.
    static List<FoundSdr> parseWebSdrOrg(String text) throws Exception {
        ArrayList<FoundSdr> list = new ArrayList<FoundSdr>();
        JSONArray a = new JSONArray(text.substring(text.indexOf('[')));
        for (int i = 0; i < a.length(); i++) {
            JSONObject d = a.getJSONObject(i);
            FoundSdr s = new FoundSdr();
            s.type = "websdr.org"; s.url = str(d, "url"); s.name = str(d, "desc"); s.location = str(d, "qth"); s.users = str(d, "users");
            s.lat = d.optDouble("lat", Double.NaN); s.lon = d.optDouble("lon", Double.NaN);
            if (!Double.isNaN(s.lat) && !Double.isNaN(s.lon)) list.add(s);
        }
        return list;
    }

    // KiwiSDR : "var kiwisdr_com = [ {...}, ... ];" avec gps "(lat, lon)".
    static List<FoundSdr> parseKiwi(String text) throws Exception {
        ArrayList<FoundSdr> list = new ArrayList<FoundSdr>();
        int a = text.indexOf('['), b = text.lastIndexOf(']');
        String json = Pattern.compile(",\\s*([\\]}])").matcher(text.substring(a, b + 1)).replaceAll("$1");
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject d = arr.getJSONObject(i);
            if (str(d, "offline").equals("yes")) continue;
            String gps = str(d, "gps").replace("(", "").replace(")", "");
            String[] g = gps.split(",");
            double lat, lon;
            try { lat = Double.parseDouble(g[0].trim()); lon = Double.parseDouble(g[1].trim()); } catch (Exception e) { continue; }
            String users = str(d, "users"), max = str(d, "users_max");
            FoundSdr s = new FoundSdr();
            s.type = "KiwiSDR"; s.url = str(d, "url"); s.name = str(d, "name"); s.location = str(d, "loc");
            s.users = users + (max.length() > 0 ? "/" + max : ""); s.lat = lat; s.lon = lon;
            list.add(s);
        }
        return list;
    }

    // ReceiverBook : "var receivers = [ {label, location:{coordinates:[lon,lat]}, receivers:[{label,url,type}]} ];"
    static List<FoundSdr> parseReceiverBook(String html) throws Exception {
        ArrayList<FoundSdr> list = new ArrayList<FoundSdr>();
        int i = html.indexOf("var receivers = ");
        if (i < 0) throw new Exception(Lang.T("format inattendu", "unexpected format"));
        int start = html.indexOf('[', i), end = html.indexOf('\n', start);
        String json = html.substring(start, end < 0 ? html.length() : end).trim();
        if (json.endsWith(";")) json = json.substring(0, json.length() - 1);
        JSONArray arr = new JSONArray(json);
        for (int k = 0; k < arr.length(); k++) {
            JSONObject site = arr.getJSONObject(k);
            JSONObject loc = site.optJSONObject("location");
            JSONArray co = loc != null ? loc.optJSONArray("coordinates") : null;
            JSONArray rx = site.optJSONArray("receivers");
            if (co == null || co.length() < 2 || rx == null) continue;
            for (int j = 0; j < rx.length(); j++) {
                JSONObject d = rx.getJSONObject(j);
                FoundSdr s = new FoundSdr();
                s.type = str(d, "type"); s.url = str(d, "url"); s.name = str(d, "label"); s.location = str(site, "label");
                s.lat = co.optDouble(1, Double.NaN); s.lon = co.optDouble(0, Double.NaN);
                if (!Double.isNaN(s.lat) && !Double.isNaN(s.lon)) list.add(s);
            }
        }
        return list;
    }

    // Clé de comparaison d'adresses (doublons, favoris) : hôte + port + chemin, sans tenir compte de la casse.
    // Les relais xxx.proxy.kiwisdr.com répondent sur le port 80 comme sur 8073 : le port n'y compte pas.
    static String urlKey(String url) {
        try {
            String t = (url == null ? "" : url).trim();
            if (!t.contains("://")) return null;
            URI u = new URI(t);
            if (u.getHost() == null) return null;
            String host = u.getHost().toLowerCase(Locale.ROOT);
            int port = u.getPort() > 0 ? u.getPort() : (t.startsWith("https") ? 443 : 80);
            String p = host.endsWith(".proxy.kiwisdr.com") ? "" : ":" + port;
            String path = u.getPath() == null ? "" : u.getPath();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return (host + p + path).toLowerCase(Locale.ROOT);
        } catch (Exception e) { return null; }
    }

    static double distance(double lat1, double lon1, double lat2, double lon2) {
        double r = Math.PI / 180, dLat = (lat2 - lat1) * r, dLon = (lon2 - lon1) * r;
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    // Pays estimé d'après le texte (nom, lieu, adresse), sinon d'après la position pour la France.
    static final String[][] COUNTRIES = {
        { "France", "france", "french", "francia", ".fr/", ".fr:", " fr ", "(fr)", ", fr" },
        { "Belgique", "belgi", ".be/", ".be:", "(be)", ", be" },
        { "Luxembourg", "luxemb" },
        { "Suisse", "switzerland", "suisse", "schweiz", "svizzera", ".ch/", ".ch:", "(ch)", ", ch" },
        { "Allemagne", "germany", "deutschland", "allemagne", ".de/", ".de:", "(de)", ", de" },
        { "Italie", "italy", "italia", "italie", ".it/", ".it:", "(it)", ", it" },
        { "Espagne", "spain", "españa", "espana", "espagne", ".es/", ".es:", "(es)", ", es" },
        { "Andorre", "andorra" },
        { "Monaco", "monaco" },
        { "Royaume-Uni", "united kingdom", "england", "scotland", "wales", " uk", "(uk)", ".uk/", ".uk:", "britain" },
        { "Pays-Bas", "netherlands", "nederland", "holland", ", nl", "(nl)", ".nl/", ".nl:" },
        { "Autriche", "austria", "österreich", ".at/", ".at:" },
        { "Portugal", "portugal", ".pt/", ".pt:" },
        { "Irlande", "ireland", ".ie/", ".ie:" },
    };

    static final Pattern FRENCH_CALL = Pattern.compile("\\b(F[1-9]|TK[0-9])[A-Z]{1,3}\\b");

    static String guessCountry(String text, double lat, double lon) {
        String t = " " + (text == null ? "" : text).toLowerCase(Locale.ROOT) + " ";
        for (String[] c : COUNTRIES)
            for (int i = 1; i < c.length; i++)
                if (t.contains(c[i])) return english(c[0]);
        if (FRENCH_CALL.matcher(text == null ? "" : text).find() || inFrance(lat, lon)) return "France";
        return "";
    }

    static final double[][] FRANCE = {
        { 2.55, 51.09 }, { 4.15, 49.98 }, { 4.85, 50.15 }, { 5.82, 49.50 }, { 6.36, 49.46 }, { 8.23, 48.96 }, { 7.58, 47.58 }, { 6.95, 47.30 },
        { 6.10, 46.40 }, { 6.80, 46.40 }, { 7.03, 45.92 }, { 6.63, 45.10 }, { 7.00, 44.25 }, { 7.53, 43.78 }, { 6.20, 43.05 }, { 5.00, 43.30 },
        { 4.00, 43.45 }, { 3.10, 42.95 }, { 3.17, 42.43 }, { 1.70, 42.50 }, { 0.00, 42.70 }, { -1.78, 43.37 }, { -1.25, 44.50 }, { -1.20, 46.20 },
        { -2.30, 47.10 }, { -4.80, 48.40 }, { -3.00, 48.85 }, { -1.60, 48.65 }, { -1.95, 49.70 }, { -1.00, 49.40 }, { 0.20, 49.50 }, { 1.40, 50.10 },
        { 1.60, 50.95 } };

    static boolean inFrance(double lat, double lon) {
        if (lat >= 41.3 && lat <= 43.05 && lon >= 8.5 && lon <= 9.6) return true;
        boolean inside = false;
        int n = FRANCE.length;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = FRANCE[i][0], yi = FRANCE[i][1], xj = FRANCE[j][0], yj = FRANCE[j][1];
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }

    static String english(String fr) {
        if (Lang.code().equals("fr")) return fr;
        String[][] m = { { "Belgique", "Belgium" }, { "Suisse", "Switzerland" }, { "Allemagne", "Germany" }, { "Italie", "Italy" }, { "Espagne", "Spain" },
            { "Andorre", "Andorra" }, { "Royaume-Uni", "United Kingdom" }, { "Pays-Bas", "Netherlands" }, { "Autriche", "Austria" }, { "Irlande", "Ireland" } };
        for (String[] p : m) if (p[0].equals(fr)) return p[1];
        return fr;
    }
}
