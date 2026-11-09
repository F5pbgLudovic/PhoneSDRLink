package fr.f5pbg.phonesdrlink;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

// Lien de partage d'une écoute : WEB_BASE?u=<WebSDR>&f=<Hz>&m=<mode>[&bw=<Hz>][&d=<décodeur>] (lien https, cliquable dans
// les messageries ; la page GitHub Pages docs/s/index.html propose d'ouvrir l'application), aussi accepté sous la forme
// phonesdrlink://tune?… (premiers liens, et bouton de la page).
// Java pur (testé en JUnit, sans téléphone). Le décodeur est désigné par son nom (MainActivity.decName), pas par son numéro,
// pour que les liens restent valables si les numéros changent. Paramètres inconnus ignorés (versions futures).
final class ShareLink {
    static final String SCHEME = "phonesdrlink";
    // Page de partage : GitHub Pages du dépôt F5pbgLudovic/PhoneSDRLink (dossier docs, voir docs/GITHUB-PAGES.md).
    // À changer avec l'hôte et le chemin de l'intent-filter https d'AndroidManifest.xml.
    static final String WEB_BASE = "https://f5pbgludovic.github.io/PhoneSDRLink/s/";
    static final String[] PREFIXES = { SCHEME + "://tune?", WEB_BASE + "?", WEB_BASE.substring(0, WEB_BASE.length() - 1) + "?" };
    static final int MIN_BW = 100, MAX_BW = 15000;

    final String url, mode, decoder;   // decoder : "" = aucun
    final long hz;
    final int bwHz;                    // 0 = filtre d'origine du WebSDR

    private ShareLink(String url, long hz, String mode, int bwHz, String decoder) {
        this.url = url; this.hz = hz; this.mode = mode; this.bwHz = bwHz; this.decoder = decoder;
    }

    static String build(String url, long hz, String mode, int bwHz, String decoder) {
        StringBuilder sb = new StringBuilder(WEB_BASE).append('?');
        sb.append("u=").append(enc(url)).append("&f=").append(hz).append("&m=").append(enc(mode));
        if (bwHz > 0) sb.append("&bw=").append(bwHz);
        if (decoder != null && !decoder.isEmpty()) sb.append("&d=").append(enc(decoder));
        return sb.toString();
    }

    // null si le lien n'est pas valable (u, f ou m manquant ou invalide, échappement % abîmé) ; bw et d invalides sont ignorés.
    static ShareLink parse(String link, String[] modes) {
        String p = prefixOf(link);
        if (p == null) return null;
        String q = link.substring(p.length());
        int hash = q.indexOf('#');
        if (hash >= 0) q = q.substring(0, hash);
        String url = null, mode = null, dec = "";
        long hz = -1;
        int bw = 0;
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq), v;
            try { v = URLDecoder.decode(part.substring(eq + 1), "UTF-8"); } catch (Exception e) { return null; }
            if (k.equals("u")) url = v.trim();
            else if (k.equals("f")) hz = toLong(v);
            else if (k.equals("m")) mode = v.trim().toUpperCase(Locale.ROOT);
            else if (k.equals("bw")) { long b = toLong(v); bw = b >= MIN_BW && b <= MAX_BW ? (int) b : 0; }
            else if (k.equals("d")) dec = v.trim();
        }
        if (url == null || !isWeb(url)) return null;
        if (hz < Store.MIN_HZ || hz > Store.MAX_HZ) return null;
        if (mode == null || !contains(modes, mode)) return null;
        return new ShareLink(url, hz, mode, bw, dec);
    }

    // Vrai si le lien est un lien de partage de PhoneSDRLink (sans vérifier ses paramètres).
    static boolean isShareLink(String link) { return prefixOf(link) != null; }

    private static String prefixOf(String link) {
        if (link != null) for (String p : PREFIXES) if (link.regionMatches(true, 0, p, 0, p.length())) return p;
        return null;
    }

    static boolean isWeb(String u) {
        String l = u.toLowerCase(Locale.ROOT);
        int start = l.startsWith("http://") ? 7 : l.startsWith("https://") ? 8 : -1;
        return start > 0 && l.length() > start;
    }

    static boolean contains(String[] a, String s) { for (String x : a) if (x.equals(s)) return true; return false; }

    static long toLong(String s) { try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1; } }

    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; } }
}
