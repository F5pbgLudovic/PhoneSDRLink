package fr.f5pbg.phonesdrlink;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Locale;

// Un favori : un WebSDR (nom, adresse), rangé dans l'une des listes.
final class Favorite {
    static final int MAX_LISTS = 10;
    String name, url;
    int volume = -1;
    int list;
    // Dernières vérifications (ms) : dernière réponse du serveur (0 = jamais vue), première absence de réponse depuis (0 = aucune).
    long lastOk, firstFail;
    static final long WEEK = 7L * 24 * 3600 * 1000, MONTH = 30L * 24 * 3600 * 1000;

    // 0 = pas d'information, 1 = répond (vert), 2 = ne répond pas depuis moins d'une semaine ou durée inconnue,
    // 3 = sans réponse depuis une semaine (orange), 4 = depuis un mois (rouge).
    int health(long now) {
        if (firstFail > 0) {
            long ref = lastOk > 0 ? lastOk : firstFail;
            return now - ref >= MONTH ? 4 : now - ref >= WEEK ? 3 : 2;
        }
        return lastOk > 0 ? 1 : 0;
    }

    Favorite(String name, String url) {
        this.url = url == null ? "" : url.trim();
        this.name = name == null || name.trim().isEmpty() ? hostOf(this.url) : clean(name);
    }

    // Nom sur une seule ligne, sans reste de balisage des annuaires (un retour à la ligne casserait le fichier de réglages).
    static String clean(String n) {
        return n.replace("\\\"", "\"").replace("&#39;", "'").replace("&amp;", "&").replaceAll("[\\r\\n\\s]+", " ").trim();
    }

    boolean matches(String other) {
        String k = Finder.urlKey(other);
        return k != null && k.equals(Finder.urlKey(url));
    }

    static String hostOf(String url) {
        try { URI u = new URI(url); return u.getHost() != null ? u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") : url; }
        catch (Exception e) { return url; }
    }

    // "Nom|URL|volume|liste" (même format que SDRLink)
    String toSetting() { return name.replace("|", "/") + "|" + url + "|" + volume + "|" + list; }

    static Favorite fromSetting(String v) {
        String[] p = v.split("\\|", -1);
        try {
            if (p.length >= 4) {
                int vol = Integer.parseInt(p[p.length - 2].trim()), li = Integer.parseInt(p[p.length - 1].trim());
                Favorite f = new Favorite(join(p, 0, p.length - 3), p[p.length - 3]);
                f.volume = vol < 0 ? -1 : Math.min(100, vol);
                f.list = Math.max(0, Math.min(MAX_LISTS - 1, li));
                return f;
            }
        } catch (NumberFormatException e) { }
        if (p.length >= 2) return new Favorite(p[0], p[1]);
        return new Favorite(null, v);
    }

    static String join(String[] p, int from, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < from + count; i++) { if (i > from) sb.append('|'); sb.append(p[i]); }
        return sb.toString();
    }
}

// Une mémoire de fréquence.
final class Memory {
    static final int MAX_LISTS = 10, RECENT = 20;
    String name = "", mode = "";
    long hz;
    int list;
    boolean scan;

    Memory(String name, long hz, String mode) {
        this.name = name == null ? "" : name.trim();
        this.hz = hz;
        this.mode = mode == null ? "" : mode;
    }

    String label() { return name.length() > 0 ? name : kHz(hz) + " kHz"; }

    static String kHz(long hz) {
        String s = String.format(Locale.US, "%.3f", hz / 1000.0);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    boolean same(Memory m) { return m != null && m.hz == hz && m.name.equals(name) && m.mode.equals(mode); }

    String key() { return hz + ":" + enc(mode) + ":" + enc(name); }

    static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); } catch (Exception e) { return s; }
    }

    // "nom|Hz|mode|liste|scan"
    String toSetting() { return name.replace("|", "/") + "|" + hz + "|" + mode + "|" + list + (scan ? "|1" : ""); }

    static Memory fromSetting(String v) {
        String[] p = v.split("\\|", -1);
        if (p.length < 2) return null;
        long hz;
        try { hz = Long.parseLong(p[1].trim()); } catch (NumberFormatException e) { return null; }
        if (hz <= 0) return null;
        Memory m = new Memory(p[0], hz, p.length >= 3 ? p[2] : "");
        try { if (p.length >= 4) m.list = Math.max(0, Math.min(MAX_LISTS - 1, Integer.parseInt(p[3].trim()))); } catch (NumberFormatException e) { }
        m.scan = p.length >= 5 && p[4].equals("1");
        return m;
    }
}

// Réglage complet d'un scan (profil nommé, ou réglage du moment).
final class ScanProfile {
    static final int MAX_PROFILES = 10, MAX_SKIPS = 10;
    String name = "";
    int type;                       // 0 fréquences, 1 mémoires
    long startHz, endHz, stepHz = 1000;
    int freqDwell = 5, memDwell = 5;
    ArrayList<long[]> skips = new ArrayList<long[]>();
    ArrayList<String> mems = new ArrayList<String>();

    long lo() { return Math.min(startHz, endHz); }
    long hi() { return Math.max(startHz, endHz); }

    boolean skipped(long hz) {
        for (long[] z : skips) if (hz > z[0] && hz < z[1]) return true;
        return false;
    }

    // Fréquence suivante, en sautant les zones non scannées ; la première si hz < début ; -1 si tout est sauté.
    long next(long hz) {
        long lo = lo(), step = Math.max(1, stepHz), n = (hi() - lo) / step + 1;
        long pos = hz < lo ? -1 : (hz - lo) / step;
        for (long i = 0; i < n; i++) {
            pos = (pos + 1) % n;
            long f = lo + pos * step;
            if (!skipped(f)) return f;
        }
        return -1;
    }

    // Fréquence précédente, en sautant les zones non scannées ; la dernière si hz <= début ; -1 si tout est sauté.
    long prev(long hz) {
        long lo = lo(), step = Math.max(1, stepHz), n = (hi() - lo) / step + 1;
        long pos = hz <= lo ? n : (hz - lo + step - 1) / step;
        for (long i = 0; i < n; i++) {
            pos = (pos - 1 + n) % n;
            long f = lo + pos * step;
            if (!skipped(f)) return f;
        }
        return -1;
    }

    static String skipText(long[] z) { return z[0] + "-" + z[1]; }

    static long[] parseSkip(String v) {
        int d = v.length() > 2 ? v.indexOf('-', 1) : -1;
        if (d < 0) return null;
        try {
            long a = Long.parseLong(v.substring(0, d).trim()), b = Long.parseLong(v.substring(d + 1).trim());
            if (a <= 0 || b <= 0) return null;
            return new long[] { Math.min(a, b), Math.max(a, b) };
        } catch (NumberFormatException e) { return null; }
    }

    String toSetting() {
        StringBuilder z = new StringBuilder(), m = new StringBuilder();
        for (long[] s : skips) { if (z.length() > 0) z.append(';'); z.append(skipText(s)); }
        for (String s : mems) { if (m.length() > 0) m.append(';'); m.append(s); }
        return name.replace("|", "/") + "|" + type + "|" + startHz + "|" + endHz + "|" + stepHz + "|" + freqDwell + "|" + memDwell + "|" + z + "|" + m;
    }

    static ScanProfile fromSetting(String v) {
        String[] p = v.split("\\|", -1);
        if (p.length < 7 || p[0].trim().isEmpty()) return null;
        ScanProfile s = new ScanProfile();
        s.name = p[0].trim();
        s.type = Store.toInt(p[1], 0) == 1 ? 1 : 0;
        long l;
        if ((l = Store.toLong(p[2], 0)) > 0) s.startHz = l;
        if ((l = Store.toLong(p[3], 0)) > 0) s.endHz = l;
        if ((l = Store.toLong(p[4], 0)) > 0) s.stepHz = l;
        int i;
        if ((i = Store.toInt(p[5], 0)) > 0) s.freqDwell = i;
        if ((i = Store.toInt(p[6], 0)) > 0) s.memDwell = i;
        if (p.length > 7) for (String z : p[7].split(";")) { long[] r = parseSkip(z.trim()); if (r != null && s.skips.size() < MAX_SKIPS) s.skips.add(r); }
        if (p.length > 8) for (String m : p[8].split(";")) if (m.length() > 0) s.mems.add(m);
        return s;
    }

    ScanProfile copy() { return fromSetting(toSetting().replaceFirst("^\\|", "x|")); }
}

// Tous les réglages de PHONESDRLINK, enregistrés dans settings.ini (même format « clé=valeur » que SDRLink pour PC :
// un fichier d'export de SDRLink peut être importé, les clés inconnues sont ignorées).
final class Store {
    static final int[] STEPS = { 1, 10, 50, 100, 250, 500, 1000, 10000 };   // 1 Hz : proposé seulement en USB et LSB
    static final long MIN_HZ = 1800000L, MAX_HZ = 440000000L;
    static final int IMP_CONFIG = 1, IMP_WEBSDR = 2, IMP_MEMS = 4, IMP_SCAN = 8;

    // Favoris de départ : assets/default-favorites.txt (« nom|adresse »), tous dans la liste 1, nommée HF.
    static String[] DEFAULT_FAVS = { "Twente (NL) - websdr.org|http://websdr.ewi.utwente.nl:8901/", "Wessex (UK) - UberSDR|https://wessex.zapto.org/v2/",
        "French Alps - PhantomSDR|http://linkz.ddns.net:8088/" };

    // Configuration
    String language = "";
    boolean dark = true, autoNotch, autoDnr, checkUpdates = true, firstSearchDone, keepScreen, spectrumZoom = true, autoReload, barBottom, zoomLeft, headsetStop, decOverlay = true, cwAutoTone = true, cwAutoSpeed = true;
    int cwTone = 700, cwBw = 80, cwWpm = 18, cwThr = 55, cwChar = 20, cwWord = 50, cwSq = 5, cwAgc = 1;   // réglages du décodeur CW
    boolean cwAfc = true, cwAutoBw = true;
    int rttyBaud = 0, rttyShift = 0, rttyPol = 0, rttySq = 15, rttyFreq = 0;   // réglages du décodeur RTTY (0 = automatique ; débit en centièmes de baud)
    boolean rttyUos = true, rttyAfc = true, freedvAuto;   // freedvAuto : décodeur FreeDV RADE permanent (SET › FreeDV auto)
    boolean pskAuto = true, pskAfc = true, sstvSave = true;   // PSK31 : fréquence automatique, suivi de dérive ; SSTV : enregistrement automatique des images
    int pskFreq = 1000, pskBaud = 31, pskSq = 75;             // PSK31 : fréquence audio manuelle (Hz), débit (31 ou 63), squelch (% de cohérence de phase)
    int zoomPos;                      // boutons INIT + − : coin (bits 0-1 : 0 bas droit, 1 bas gauche, 2 haut droit, 3 haut gauche) et sens (bit 2 : horizontal)
    int bufMs;                        // mémoire tampon audio supplémentaire (ms), 0 = réglage d origine
    boolean zoomPosSet;
    int sstvSens = 5, sstvLoss = 4;   // SSTV : sensibilité aux signaux faibles et « perte data » (patience avant d'abandonner une image), 1 à 10
    String viewPos = "";              // positions des listes (liste choisie, défilement) : "cle=a,b,c|cle=a,b"
    int iaruRegion = 1;               // liste de mémoires « Modes numériques » : région IARU 1, 2 ou 3
    int colBtnBg, colBtnTx, colFreqTx, colFreqBg, colBarFg, colBarBg, colBg, colFt8A, colFt8B, colDec;   // couleurs choisies (0 = couleur d'origine)
    int notchLevel = 1, dnrLevel = 1;   // 1 = réglage d'origine, 10 = le plus fort
    String callsign = "PhoneSDRLink";
    String lastUrl = "http://websdr.ewi.utwente.nl:8901/";
    long freqHz = 7100000L;
    String mode = "LSB";
    // Pas de la barre (Hz) retenu pour chaque famille de modes : CW, USB+LSB, autres (AM, FM, DATA). Celui que l'opérateur choisit
    // devient le pas par défaut du mode, y compris après un redémarrage. Largeur de bande (Hz, 0 = filtre d'origine du WebSDR)
    // retenue de la même façon : CW, USB+LSB, AM, FM, DATA.
    int stepCw = 10, stepSsb = 1000, stepOther = 1000;
    int bwCw = 300, bwSsb = 2900, bwAm = 6000, bwFm = 8000, bwData = 0;

    int stepFor(String m) { return "CW".equals(m) ? stepCw : ("USB".equals(m) || "LSB".equals(m)) ? stepSsb : stepOther; }

    void setStepFor(String m, int hz) { if ("CW".equals(m)) stepCw = hz; else if ("USB".equals(m) || "LSB".equals(m)) stepSsb = hz; else stepOther = hz; }

    int bwFor(String m) { return "CW".equals(m) ? bwCw : ("USB".equals(m) || "LSB".equals(m)) ? bwSsb : "AM".equals(m) ? bwAm : "FM".equals(m) ? bwFm : bwData; }

    static int defaultBw(String m) { return "CW".equals(m) ? 300 : ("USB".equals(m) || "LSB".equals(m)) ? 2900 : "AM".equals(m) ? 6000 : "FM".equals(m) ? 8000 : 0; }

    void setBwFor(String m, int hz) {
        if ("CW".equals(m)) bwCw = hz; else if ("USB".equals(m) || "LSB".equals(m)) bwSsb = hz; else if ("AM".equals(m)) bwAm = hz; else if ("FM".equals(m)) bwFm = hz; else bwData = hz;
    }

    void defaultsModes() { stepCw = 10; stepSsb = 1000; stepOther = 1000; bwCw = 300; bwSsb = 2900; bwAm = 6000; bwFm = 8000; bwData = 0; }
    // WebSDR
    final ArrayList<Favorite> favorites = new ArrayList<Favorite>();
    final String[] favListNames = new String[Favorite.MAX_LISTS];
    int favListCount = 4, curFavList;
    // Mémoires
    final ArrayList<Memory> memories = new ArrayList<Memory>(), recent = new ArrayList<Memory>();
    final String[] memListNames = new String[Memory.MAX_LISTS];
    int memListCount = 1, curMemList;
    // Scan
    int scanType;
    long scanStart, scanEnd, scanStep = 1000;
    int scanFreqDwell = 5, scanMemDwell = 5;
    final ArrayList<long[]> scanSkips = new ArrayList<long[]>();
    final ArrayList<ScanProfile> scanProfiles = new ArrayList<ScanProfile>();

    static Store I;
    static File file;

    Store() { defaultsWebSdr(); defaultsMems(); }

    static void init(Context ctx) {
        file = new File(ctx.getFilesDir(), "settings.ini");
        try {
            InputStream in = ctx.getAssets().open("default-favorites.txt");
            String[] l = readAll(in).split("\r?\n");
            in.close();
            ArrayList<String> keep = new ArrayList<String>();
            for (String x : l) if (x.contains("|")) keep.add(x);
            if (!keep.isEmpty()) DEFAULT_FAVS = keep.toArray(new String[0]);
        } catch (Exception e) { }
        Store s = new Store();
        boolean existed = file.exists();
        if (existed) { try { s.parse(readFile(file), 0xFFFF); } catch (Exception e) { } }
        else s.language = Lang.systemDefault();
        if (existed && s.language.isEmpty()) s.language = Lang.systemDefault();
        I = s;
    }

    void defaultsWebSdr() {
        favorites.clear();
        favListCount = 4;
        curFavList = 0;
        for (int i = 0; i < Favorite.MAX_LISTS; i++) favListNames[i] = "Liste " + (i + 1);
        favListNames[0] = "HF";
        for (String f : DEFAULT_FAVS) { int bar = f.lastIndexOf('|'); favorites.add(new Favorite(f.substring(0, bar), f.substring(bar + 1))); }
        firstSearchDone = false;
    }

    void defaultsMems() {
        memories.clear();
        recent.clear();
        memListCount = 1;
        curMemList = 0;
        for (int i = 0; i < Memory.MAX_LISTS; i++) memListNames[i] = "Liste " + (i + 1);
    }

    void defaultsScan() {
        scanType = 0; scanStart = scanEnd = 0; scanStep = 1000; scanFreqDwell = scanMemDwell = 5;
        scanSkips.clear();
        scanProfiles.clear();
        for (Memory m : memories) m.scan = false;
    }

    void defaultsConfig() {
        notchLevel = 1; dnrLevel = 1; autoReload = false; barBottom = false; zoomLeft = false; zoomPos = 0; bufMs = 0; iaruRegion = 1; headsetStop = false; colBtnBg = colBtnTx = colFreqTx = colFreqBg = colBarFg = colBarBg = colBg = colFt8A = colFt8B = colDec = 0; decOverlay = true; freedvAuto = false; cwDefaults(); rttyDefaults(); pskDefaults(); sstvSave = true; sstvSens = 5; sstvLoss = 4;
        dark = true; autoNotch = false; autoDnr = false; keepScreen = false; spectrumZoom = true; checkUpdates = true; callsign = "PhoneSDRLink";
        lastUrl = "http://websdr.ewi.utwente.nl:8901/"; freqHz = 7100000L; mode = "LSB"; defaultsModes();
    }

    // ------------------------------------------------------------------ lecture / écriture

    static int okStep(int v, int def) { for (int x : STEPS) if (x == v) return v; return def; }
    static int okBw(int v, int def) { return v == 0 || (v >= 100 && v <= 15000) ? v : def; }

    static int toInt(String v, int def) { try { return Integer.parseInt(v.trim()); } catch (Exception e) { return def; } }
    static long toLong(String v, long def) { try { return Long.parseLong(v.trim()); } catch (Exception e) { return def; } }

    static String readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try { return readAll(in); } finally { in.close(); }
    }

    static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        String s = new String(bo.toByteArray(), "UTF-8");
        return s.length() > 0 && s.charAt(0) == '\uFEFF' ? s.substring(1) : s;
    }

    // Les lignes du texte sont rangées selon les catégories demandées (masque IMP_*) ; remplace ce qui s'y trouve.
    void parse(String text, int cats) {
        boolean favSeen = false, memSeen = false;
        ArrayList<Favorite> favs = new ArrayList<Favorite>();
        ArrayList<String[]> states = new ArrayList<String[]>();
        ArrayList<Memory> mems = new ArrayList<Memory>(), rec = new ArrayList<Memory>();
        ArrayList<long[]> skips = new ArrayList<long[]>();
        ArrayList<ScanProfile> profs = new ArrayList<ScanProfile>();
        int favCount = favListCount, memCount = memListCount;
        // Une ligne qui ne commence pas par « Clé= » prolonge la précédente (anciens fichiers où un nom contenait un retour à la ligne).
        ArrayList<String> rows = new ArrayList<String>();
        for (String l : text.split("\r?\n")) {
            if (l.isEmpty()) continue;
            if (!rows.isEmpty() && !l.matches("^[A-Za-z0-9]+=.*")) rows.set(rows.size() - 1, rows.get(rows.size() - 1) + " " + l);
            else rows.add(l);
        }
        for (String raw : rows) {
            int eq = raw.indexOf('=');
            if (eq <= 0) continue;
            String k = raw.substring(0, eq).trim(), v = raw.substring(eq + 1);
            boolean cfg = (cats & IMP_CONFIG) != 0, web = (cats & IMP_WEBSDR) != 0, mem = (cats & IMP_MEMS) != 0, scn = (cats & IMP_SCAN) != 0;
            if (cfg) {
                if (k.equals("Language")) { for (String c : Lang.CODES) if (c.equals(v.trim())) language = c; }
                else if (k.equals("DarkMode")) dark = v.trim().equals("1");
                else if (k.equals("AutoNotch")) autoNotch = v.trim().equals("1");
                else if (k.equals("AutoDnr")) autoDnr = v.trim().equals("1");
                else if (k.equals("PhoneNotchLevel")) notchLevel = Math.max(1, Math.min(10, toInt(v, 1)));
                else if (k.equals("PhoneDnrLevel")) dnrLevel = Math.max(1, Math.min(10, toInt(v, 1)));
                else if (k.equals("PhoneAutoReload")) autoReload = v.trim().equals("1");
                else if (k.equals("PhoneBarBottom")) barBottom = v.trim().equals("1");
                else if (k.equals("PhoneZoomLeft")) { zoomLeft = v.trim().equals("1"); if (!zoomPosSet) zoomPos = zoomLeft ? 1 : 0; }
                else if (k.equals("PhoneZoomPos")) { zoomPos = Math.max(0, Math.min(7, toInt(v, 0))); zoomPosSet = true; }
                else if (k.equals("PhoneViewPos")) viewPos = v.trim();
                else if (k.equals("PhoneBufMs")) bufMs = Math.max(0, Math.min(5000, toInt(v, 0)));
                else if (k.equals("PhoneIaruRegion")) iaruRegion = Math.max(1, Math.min(3, toInt(v, 1)));
                else if (k.equals("PhoneColFt8A")) colFt8A = hexColor(v);
                else if (k.equals("PhoneColFt8B")) colFt8B = hexColor(v);
                else if (k.equals("PhoneColDec")) colDec = hexColor(v);
                else if (k.equals("PhoneDecOverlay")) decOverlay = !v.trim().equals("0");
                else if (k.equals("PhoneCwAutoTone")) cwAutoTone = !v.trim().equals("0");
                else if (k.equals("PhoneCwAutoSpeed")) cwAutoSpeed = !v.trim().equals("0");
                else if (k.equals("PhoneCwTone")) cwTone = Math.max(200, Math.min(1200, toInt(v, 700)));
                else if (k.equals("PhoneCwBw")) cwBw = Math.max(30, Math.min(400, toInt(v, 75)));
                else if (k.equals("PhoneCwWpm")) cwWpm = Math.max(5, Math.min(60, toInt(v, 18)));
                else if (k.equals("PhoneCwThr")) cwThr = Math.max(30, Math.min(80, toInt(v, 55)));
                else if (k.equals("PhoneCwChar")) cwChar = Math.max(15, Math.min(40, toInt(v, 20)));
                else if (k.equals("PhoneCwWord")) cwWord = Math.max(40, Math.min(90, toInt(v, 55)));
                else if (k.equals("PhoneCwSq")) cwSq = Math.max(3, Math.min(30, toInt(v, 5)));
                else if (k.equals("PhoneCwAgc")) cwAgc = Math.max(0, Math.min(2, toInt(v, 1)));
                else if (k.equals("PhoneCwAfc")) cwAfc = !v.trim().equals("0");
                else if (k.equals("PhoneCwAutoBw")) cwAutoBw = !v.trim().equals("0");
                else if (k.equals("PhoneRttyBaud")) rttyBaud = Math.max(0, Math.min(30000, toInt(v, 0)));
                else if (k.equals("PhoneRttyShift")) rttyShift = Math.max(0, Math.min(2000, toInt(v, 0)));
                else if (k.equals("PhoneRttyPol")) rttyPol = Math.max(0, Math.min(2, toInt(v, 0)));
                else if (k.equals("PhoneRttySq")) rttySq = Math.max(5, Math.min(40, toInt(v, 15)));
                else if (k.equals("PhoneRttyFreq")) rttyFreq = Math.max(0, Math.min(3000, toInt(v, 0)));
                else if (k.equals("PhoneRttyUos")) rttyUos = !v.trim().equals("0");
                else if (k.equals("PhoneRttyAfc")) rttyAfc = !v.trim().equals("0");
                else if (k.equals("PhoneFreeDvAuto")) freedvAuto = v.trim().equals("1");
                else if (k.equals("PhonePskAuto")) pskAuto = !v.trim().equals("0");
                else if (k.equals("PhonePskAfc")) pskAfc = !v.trim().equals("0");
                else if (k.equals("PhonePskFreq")) pskFreq = Math.max(300, Math.min(2900, toInt(v, 1000)));
                else if (k.equals("PhonePskBaud")) pskBaud = toInt(v, 31) >= 47 ? 63 : 31;
                else if (k.equals("PhonePskSq")) pskSq = Math.max(60, Math.min(98, toInt(v, 75)));
                else if (k.equals("PhoneSstvSave")) sstvSave = !v.trim().equals("0");
                else if (k.equals("PhoneSstvSens10")) sstvSens = Math.max(1, Math.min(10, toInt(v, 5)));
                else if (k.equals("PhoneSstvLoss")) sstvLoss = Math.max(1, Math.min(10, toInt(v, 4)));
                else if (k.equals("PhoneHeadsetStop")) headsetStop = v.trim().equals("1");
                else if (k.equals("PhoneColBtnBg")) colBtnBg = hexColor(v);
                else if (k.equals("PhoneColBtnTx")) colBtnTx = hexColor(v);
                else if (k.equals("PhoneColFreqTx")) colFreqTx = hexColor(v);
                else if (k.equals("PhoneColFreqBg")) colFreqBg = hexColor(v);
                else if (k.equals("PhoneColBarFg")) colBarFg = hexColor(v);
                else if (k.equals("PhoneColBarBg")) colBarBg = hexColor(v);
                else if (k.equals("PhoneColBg")) colBg = hexColor(v);
                else if (k.equals("KeepScreen")) keepScreen = v.trim().equals("1");
                else if (k.equals("SpecZoom")) spectrumZoom = !v.trim().equals("0");
                else if (k.equals("CheckUpdates")) checkUpdates = !v.trim().equals("0");
                else if (k.equals("Callsign")) { try { String c = clean(java.net.URLDecoder.decode(v, "UTF-8")); if (!c.isEmpty()) callsign = c; } catch (Exception e) { } }
                else if (k.equals("LastUrl")) { if (v.trim().length() > 0) lastUrl = v.trim(); }
                else if (k.equals("Freq0")) { long f = toLong(v, 0); if (f >= MIN_HZ && f <= MAX_HZ) freqHz = f; }
                else if (k.equals("Mode0")) { if (v.trim().length() > 0) mode = v.trim(); }
                else if (k.equals("PhoneStep")) { int[] old = { 100, 250, 500, 1000 }; stepSsb = stepOther = old[Math.max(0, Math.min(3, toInt(v, 3)))]; }   // anciennes versions : un seul pas
                else if (k.equals("PhoneStepCw")) stepCw = okStep(toInt(v, 10), 10);
                else if (k.equals("PhoneStepSsb")) stepSsb = okStep(toInt(v, 1000), 1000);
                else if (k.equals("PhoneStepOther")) stepOther = okStep(toInt(v, 1000), 1000);
                else if (k.equals("PhoneBwCw")) bwCw = okBw(toInt(v, 300), 300);
                else if (k.equals("PhoneBwSsb")) bwSsb = okBw(toInt(v, 2900), 2900);
                else if (k.equals("PhoneBwAm")) bwAm = okBw(toInt(v, 6000), 6000);
                else if (k.equals("PhoneBwFm")) bwFm = okBw(toInt(v, 8000), 8000);
                else if (k.equals("PhoneBwData")) bwData = okBw(toInt(v, 0), 0);
            }
            if (web) {
                if (k.equals("Fav")) { if (v.length() > 0) { favs.add(Favorite.fromSetting(v)); favSeen = true; } }
                else if (k.equals("PhoneFavState")) { String[] q = v.split("\\|", -1); if (q.length >= 3) states.add(q); }   // « adresse|dernière réponse|première absence »
                else if (k.equals("ListCount")) favCount = Math.max(1, Math.min(Favorite.MAX_LISTS, toInt(v, 4)));
                else if (k.equals("CurrentList")) curFavList = Math.max(0, Math.min(Favorite.MAX_LISTS - 1, toInt(v, 0)));
                else if (k.equals("FirstSearch")) firstSearchDone = !v.trim().equals("0");
                else if (k.startsWith("List") && !k.equals("ListCount")) {
                    int li = toInt(k.substring(4), 0);
                    if (li >= 1 && li <= Favorite.MAX_LISTS && v.length() > 0) favListNames[li - 1] = v;
                }
            }
            if (mem) {
                if (k.equals("Mem")) { Memory m = Memory.fromSetting(v); if (m != null) { mems.add(m); memSeen = true; } }
                else if (k.equals("MemRecent")) { Memory m = Memory.fromSetting(v); if (m != null && rec.size() < Memory.RECENT) rec.add(m); }
                else if (k.equals("MemListCount")) memCount = Math.max(1, Math.min(Memory.MAX_LISTS, toInt(v, 1)));
                else if (k.equals("MemCurrentList")) curMemList = Math.max(0, Math.min(Memory.MAX_LISTS - 1, toInt(v, 0)));
                else if (k.startsWith("MemList") && !k.equals("MemListCount")) {
                    int li = toInt(k.substring(7), 0);
                    if (li >= 1 && li <= Memory.MAX_LISTS && v.length() > 0) memListNames[li - 1] = v;
                }
            }
            if (scn) {
                if (k.equals("ScanType")) scanType = toInt(v, 0) == 1 ? 1 : 0;
                else if (k.equals("ScanStart")) scanStart = Math.max(0, toLong(v, 0));
                else if (k.equals("ScanEnd")) scanEnd = Math.max(0, toLong(v, 0));
                else if (k.equals("ScanStep")) { long s = toLong(v, 1000); if (s > 0) scanStep = s; }
                else if (k.equals("ScanFreqDwell")) { int d = toInt(v, 5); if (d > 0) scanFreqDwell = d; }
                else if (k.equals("ScanMemDwell")) { int d = toInt(v, 5); if (d > 0) scanMemDwell = d; }
                else if (k.equals("ScanSkip")) { long[] z = ScanProfile.parseSkip(v.trim()); if (z != null && skips.size() < ScanProfile.MAX_SKIPS) skips.add(z); }
                else if (k.equals("ScanProfile")) { ScanProfile p = ScanProfile.fromSetting(v); if (p != null && profs.size() < ScanProfile.MAX_PROFILES) profs.add(p); }
            }
        }
        if ((cats & IMP_WEBSDR) != 0 && favSeen) {
            favorites.clear(); favorites.addAll(favs);
            favListCount = favCount;
            for (Favorite f : favorites) favListCount = Math.max(favListCount, f.list + 1);
            curFavList = Math.min(curFavList, favListCount - 1);
            for (String[] q : states) {
                int cut = q.length - 2;
                String url = Favorite.join(q, 0, cut);
                for (Favorite f : favorites) if (f.url.equals(url)) { f.lastOk = toLong(q[cut], 0); f.firstFail = toLong(q[cut + 1], 0); }
            }
        }
        if ((cats & IMP_MEMS) != 0 && (memSeen || !rec.isEmpty())) {
            memories.clear(); memories.addAll(mems);
            recent.clear(); recent.addAll(rec);
            memListCount = memCount;
            for (Memory m : memories) memListCount = Math.max(memListCount, m.list + 1);
            curMemList = Math.min(curMemList, memListCount - 1);
        }
        if ((cats & IMP_SCAN) != 0) {
            if (!skips.isEmpty()) { scanSkips.clear(); scanSkips.addAll(skips); }
            if (!profs.isEmpty()) { scanProfiles.clear(); scanProfiles.addAll(profs); }
        }
    }

    // Fusion : les favoris, mémoires et profils du texte sont ajoutés à ceux déjà là (sans doublon).
    void merge(String text, int cats) {
        Store t = new Store();
        t.favorites.clear();
        t.parse(text, cats & ~IMP_CONFIG | (cats & IMP_CONFIG));
        if ((cats & IMP_CONFIG) != 0) { language = t.language.isEmpty() ? language : t.language; dark = t.dark; autoNotch = t.autoNotch; autoDnr = t.autoDnr; notchLevel = t.notchLevel; dnrLevel = t.dnrLevel; autoReload = t.autoReload; barBottom = t.barBottom; zoomLeft = t.zoomLeft; zoomPos = t.zoomPos; bufMs = t.bufMs; iaruRegion = t.iaruRegion; headsetStop = t.headsetStop; colBtnBg = t.colBtnBg; colBtnTx = t.colBtnTx; colFreqTx = t.colFreqTx; colFreqBg = t.colFreqBg; colBarFg = t.colBarFg; colBarBg = t.colBarBg; colBg = t.colBg; colFt8A = t.colFt8A; colFt8B = t.colFt8B; colDec = t.colDec; decOverlay = t.decOverlay; cwAutoTone = t.cwAutoTone; cwAutoSpeed = t.cwAutoSpeed; cwTone = t.cwTone; cwBw = t.cwBw; cwWpm = t.cwWpm; cwThr = t.cwThr; cwChar = t.cwChar; cwWord = t.cwWord; cwSq = t.cwSq; cwAgc = t.cwAgc; cwAfc = t.cwAfc; cwAutoBw = t.cwAutoBw; rttyBaud = t.rttyBaud; rttyShift = t.rttyShift; rttyPol = t.rttyPol; rttySq = t.rttySq; rttyFreq = t.rttyFreq; rttyUos = t.rttyUos; rttyAfc = t.rttyAfc; freedvAuto = t.freedvAuto; pskAuto = t.pskAuto; pskAfc = t.pskAfc; pskFreq = t.pskFreq; pskBaud = t.pskBaud; pskSq = t.pskSq; sstvSave = t.sstvSave; sstvSens = t.sstvSens; sstvLoss = t.sstvLoss; callsign = t.callsign; checkUpdates = t.checkUpdates; }
        if ((cats & IMP_WEBSDR) != 0) {
            for (int i = 0; i < Favorite.MAX_LISTS; i++) if (!t.favListNames[i].equals("Liste " + (i + 1)) && favListNames[i].equals("Liste " + (i + 1))) favListNames[i] = t.favListNames[i];
            for (Favorite f : t.favorites) {
                boolean dup = false;
                for (Favorite g : favorites) if (g.list == f.list && g.matches(f.url)) dup = true;
                if (!dup) favorites.add(f);
            }
            favListCount = Math.max(favListCount, t.favListCount);
        }
        if ((cats & IMP_MEMS) != 0) {
            for (Memory m : t.memories) {
                boolean dup = false;
                for (Memory g : memories) if (g.same(m) && g.list == m.list) dup = true;
                if (!dup) memories.add(m);
            }
            for (int i = 0; i < Memory.MAX_LISTS; i++) if (!t.memListNames[i].equals("Liste " + (i + 1)) && memListNames[i].equals("Liste " + (i + 1))) memListNames[i] = t.memListNames[i];
            memListCount = Math.max(memListCount, t.memListCount);
        }
        if ((cats & IMP_SCAN) != 0) {
            for (ScanProfile p : t.scanProfiles) {
                boolean dup = false;
                for (ScanProfile g : scanProfiles) if (g.name.equalsIgnoreCase(p.name)) dup = true;
                if (!dup && scanProfiles.size() < ScanProfile.MAX_PROFILES) scanProfiles.add(p);
            }
        }
    }

    // Les 4 listes proposées d'abord, dans l'ordre : texte du fichier exporté.
    String exportText() {
        StringBuilder sb = new StringBuilder();
        line(sb, "Language", language);
        line(sb, "DarkMode", dark ? 1 : 0);
        line(sb, "AutoNotch", autoNotch ? 1 : 0);
        line(sb, "AutoDnr", autoDnr ? 1 : 0);
        line(sb, "PhoneNotchLevel", notchLevel);
        line(sb, "PhoneDnrLevel", dnrLevel);
        line(sb, "PhoneAutoReload", autoReload ? 1 : 0);
        line(sb, "PhoneBarBottom", barBottom ? 1 : 0);
        line(sb, "PhoneZoomLeft", zoomLeft ? 1 : 0);
        line(sb, "PhoneZoomPos", zoomPos);
        line(sb, "PhoneBufMs", bufMs);
        line(sb, "PhoneViewPos", viewPos);
        line(sb, "PhoneIaruRegion", iaruRegion);
        line(sb, "PhoneDecOverlay", decOverlay ? 1 : 0);
        line(sb, "PhoneCwAutoTone", cwAutoTone ? 1 : 0);
        line(sb, "PhoneCwAutoSpeed", cwAutoSpeed ? 1 : 0);
        line(sb, "PhoneCwTone", cwTone);
        line(sb, "PhoneCwBw", cwBw);
        line(sb, "PhoneCwWpm", cwWpm);
        line(sb, "PhoneCwThr", cwThr);
        line(sb, "PhoneCwChar", cwChar);
        line(sb, "PhoneCwWord", cwWord);
        line(sb, "PhoneCwSq", cwSq);
        line(sb, "PhoneCwAgc", cwAgc);
        line(sb, "PhoneCwAfc", cwAfc ? 1 : 0);
        line(sb, "PhoneCwAutoBw", cwAutoBw ? 1 : 0);
        line(sb, "PhoneRttyBaud", rttyBaud);
        line(sb, "PhoneRttyShift", rttyShift);
        line(sb, "PhoneRttyPol", rttyPol);
        line(sb, "PhoneRttySq", rttySq);
        line(sb, "PhoneRttyFreq", rttyFreq);
        line(sb, "PhoneRttyUos", rttyUos ? 1 : 0);
        line(sb, "PhoneRttyAfc", rttyAfc ? 1 : 0);
        line(sb, "PhoneFreeDvAuto", freedvAuto ? 1 : 0);
        line(sb, "PhonePskAuto", pskAuto ? 1 : 0);
        line(sb, "PhonePskAfc", pskAfc ? 1 : 0);
        line(sb, "PhonePskFreq", pskFreq);
        line(sb, "PhonePskBaud", pskBaud);
        line(sb, "PhonePskSq", pskSq);
        line(sb, "PhoneSstvSave", sstvSave ? 1 : 0);
        line(sb, "PhoneSstvSens10", sstvSens);
        line(sb, "PhoneSstvLoss", sstvLoss);
        line(sb, "PhoneHeadsetStop", headsetStop ? 1 : 0);
        line(sb, "PhoneColBtnBg", String.format("%08X", colBtnBg));
        line(sb, "PhoneColBtnTx", String.format("%08X", colBtnTx));
        line(sb, "PhoneColFreqTx", String.format("%08X", colFreqTx));
        line(sb, "PhoneColFreqBg", String.format("%08X", colFreqBg));
        line(sb, "PhoneColBarFg", String.format("%08X", colBarFg));
        line(sb, "PhoneColBarBg", String.format("%08X", colBarBg));
        line(sb, "PhoneColBg", String.format("%08X", colBg));
        line(sb, "PhoneColFt8A", String.format("%08X", colFt8A));
        line(sb, "PhoneColFt8B", String.format("%08X", colFt8B));
        line(sb, "PhoneColDec", String.format("%08X", colDec));
        line(sb, "KeepScreen", keepScreen ? 1 : 0);
        line(sb, "SpecZoom", spectrumZoom ? 1 : 0);
        line(sb, "CheckUpdates", checkUpdates ? 1 : 0);
        try { line(sb, "Callsign", java.net.URLEncoder.encode(callsign, "UTF-8").replace("+", "%20")); } catch (Exception e) { }
        line(sb, "LastUrl", lastUrl);
        line(sb, "Freq0", freqHz);
        line(sb, "Mode0", mode);
        line(sb, "PhoneStepCw", stepCw);
        line(sb, "PhoneStepSsb", stepSsb);
        line(sb, "PhoneStepOther", stepOther);
        line(sb, "PhoneBwCw", bwCw);
        line(sb, "PhoneBwSsb", bwSsb);
        line(sb, "PhoneBwAm", bwAm);
        line(sb, "PhoneBwFm", bwFm);
        line(sb, "PhoneBwData", bwData);
        line(sb, "FirstSearch", firstSearchDone ? 1 : 0);
        line(sb, "ListCount", favListCount);
        for (int i = 0; i < favListCount; i++) line(sb, "List" + (i + 1), favListNames[i]);
        line(sb, "CurrentList", curFavList);
        for (Favorite f : favorites) line(sb, "Fav", f.toSetting());
        for (Favorite f : favorites) if (f.lastOk > 0 || f.firstFail > 0) line(sb, "PhoneFavState", f.url + "|" + f.lastOk + "|" + f.firstFail);
        line(sb, "MemListCount", memListCount);
        line(sb, "MemCurrentList", curMemList);
        for (int i = 0; i < memListCount; i++) line(sb, "MemList" + (i + 1), memListNames[i]);
        for (Memory m : memories) line(sb, "Mem", m.toSetting());
        for (Memory m : recent) line(sb, "MemRecent", m.toSetting());
        line(sb, "ScanType", scanType);
        line(sb, "ScanStart", scanStart);
        line(sb, "ScanEnd", scanEnd);
        line(sb, "ScanStep", scanStep);
        line(sb, "ScanFreqDwell", scanFreqDwell);
        line(sb, "ScanMemDwell", scanMemDwell);
        for (long[] z : scanSkips) line(sb, "ScanSkip", ScanProfile.skipText(z));
        for (ScanProfile p : scanProfiles) line(sb, "ScanProfile", p.toSetting());
        return sb.toString();
    }

    void cwDefaults() { cwAutoTone = true; cwAutoSpeed = true; cwTone = 700; cwBw = 80; cwWpm = 18; cwThr = 55; cwChar = 20; cwWord = 50; cwSq = 5; cwAgc = 1; cwAfc = true; cwAutoBw = true; }
    void rttyDefaults() { rttyBaud = 0; rttyShift = 0; rttyPol = 0; rttySq = 15; rttyFreq = 0; rttyUos = true; rttyAfc = true; }
    void pskDefaults() { pskAuto = true; pskAfc = true; pskFreq = 1000; pskBaud = 31; pskSq = 75; }

    static int hexColor(String v) { try { return (int) Long.parseLong(v.trim(), 16); } catch (Exception e) { return 0; } }
    // Position mémorisée d'une liste (entiers) ; null si inconnue.
    int[] getPos(String key) {
        for (String e : viewPos.split("[|]")) {
            int i = e.indexOf('=');
            if (i <= 0 || !e.substring(0, i).equals(key)) continue;
            String[] p = e.substring(i + 1).split(",");
            int[] r = new int[p.length];
            try { for (int k = 0; k < p.length; k++) r[k] = Integer.parseInt(p[k].trim()); } catch (NumberFormatException x) { return null; }
            return r;
        }
        return null;
    }

    void putPos(String key, int... v) {
        StringBuilder sb = new StringBuilder();
        for (String e : viewPos.split("[|]")) {
            if (e.isEmpty() || e.startsWith(key + "=")) continue;
            if (sb.length() > 0) sb.append('|');
            sb.append(e);
        }
        if (sb.length() > 0) sb.append('|');
        sb.append(key).append('=');
        for (int k = 0; k < v.length; k++) sb.append(k > 0 ? "," : "").append(v[k]);
        while (sb.length() > 6000) { int i = sb.indexOf("|"); if (i < 0) break; sb.delete(0, i + 1); }   // les plus anciennes positions sont oubliées
        viewPos = sb.toString();
    }

    static void line(StringBuilder sb, String k, Object v) { sb.append(k).append('=').append(v).append("\r\n"); }

    void save() {
        try {
            FileOutputStream o = new FileOutputStream(file);
            o.write(exportText().getBytes("UTF-8"));
            o.close();
        } catch (Exception e) { }
    }

    // ------------------------------------------------------------------ listes

    static String listLabel(String[] names, int i) {
        String def = "Liste " + (i + 1);
        return names[i].equals(def) ? Lang.F("Liste {0}", "List {0}", i + 1) : names[i];
    }

    String favListLabel(int i) { return listLabel(favListNames, i); }
    String memListLabel(int i) { return listLabel(memListNames, i); }

    // Nom ou indicatif donné aux WebSDR : sans ; , = \ ' " ni caractères de contrôle, 30 caractères au plus.
    // Les espaces deviennent le caractère invisible Alt+255 (espace insécable, U+00A0) : les WebSDR le prennent en compte,
    // alors qu'ils ignorent un espace de clavier ; un nom réduit à un espace donne donc un nom invisible.
    static String clean(String name) {
        StringBuilder sb = new StringBuilder();
        String t = name == null ? "" : name;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == ' ') c = '\u00A0';
            if (!Character.isISOControl(c) && ";,=\\'\"".indexOf(c) < 0 && sb.length() < 30) sb.append(c);
        }
        return sb.toString();
    }

    void addRecent(Memory m) {
        for (int i = recent.size() - 1; i >= 0; i--) if (recent.get(i).same(m)) recent.remove(i);
        recent.add(0, m);
        while (recent.size() > Memory.RECENT) recent.remove(recent.size() - 1);
    }

    // Réglage de scan du moment, sous forme de profil.
    ScanProfile currentScan() {
        ScanProfile p = new ScanProfile();
        p.type = scanType; p.startHz = scanStart; p.endHz = scanEnd; p.stepHz = scanStep; p.freqDwell = scanFreqDwell; p.memDwell = scanMemDwell;
        for (long[] z : scanSkips) p.skips.add(new long[] { z[0], z[1] });
        for (Memory m : memories) if (m.scan) p.mems.add(m.key());
        return p;
    }

    void useScan(ScanProfile p) {
        scanType = p.type; scanStart = p.startHz; scanEnd = p.endHz; scanStep = p.stepHz; scanFreqDwell = p.freqDwell; scanMemDwell = p.memDwell;
        scanSkips.clear();
        for (long[] z : p.skips) scanSkips.add(new long[] { z[0], z[1] });
        for (Memory m : memories) m.scan = p.mems.contains(m.key());
    }
}
