# Partage de fréquence (deep link) — plan d'implémentation

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** bouton « partage » au-dessus de INIT qui partage un lien `phonesdrlink://tune?…` ; ce lien ouvre PhoneSDRLink sur le même WebSDR, la même fréquence, le même mode, la même largeur et le même décodeur.

**Architecture:** `ShareLink` (Java pur, testé en JUnit) construit et lit le lien. `MainActivity` ajoute le bouton, le partage natif (`ACTION_SEND`) et la réception du lien (`onCreate` / `onNewIntent`, confirmation, puis `openUrl` : le mécanisme existant `onSdrState` impose fréquence/mode/largeur à la page).

**Tech Stack:** Java 17, Android SDK 34, AGP 8.5.2, Gradle 8.13 (déjà dans `~/.gradle/wrapper/dists`), JUnit 4.13.2.

Spec : `docs/superpowers/specs/2026-11-09-share-link-design.md`.

Raccourcis utilisés dans les commandes :

```bash
G=$(ls ~/.gradle/wrapper/dists/gradle-8.13-bin/*/gradle-8.13/bin/gradle)
J=app/src/main/java/fr/f5pbg/phonesdrlink
```

---

### Task 1 : `ShareLink` + tests JUnit

**Files:**
- Modify: `app/build.gradle` (bloc `dependencies`)
- Create: `app/src/main/java/fr/f5pbg/phonesdrlink/ShareLink.java`
- Test: `app/src/test/java/fr/f5pbg/phonesdrlink/ShareLinkTest.java`

- [ ] **Step 1 : dépendance JUnit** — dans `app/build.gradle`, bloc `dependencies` :

```groovy
dependencies {
    implementation 'androidx.webkit:webkit:1.11.0'
    testImplementation 'junit:junit:4.13.2'
}
```

- [ ] **Step 2 : test qui échoue** — `app/src/test/java/fr/f5pbg/phonesdrlink/ShareLinkTest.java` :

```java
package fr.f5pbg.phonesdrlink;

import static org.junit.Assert.*;
import org.junit.Test;

public class ShareLinkTest {
    static final String[] MODES = { "USB", "LSB", "AM", "FM", "DATAU", "CW" };
    static final String TW = "http://websdr.ewi.utwente.nl:8901/";

    @Test public void buildMinimalIsExact() {
        assertEquals("phonesdrlink://tune?u=http%3A%2F%2Fwebsdr.ewi.utwente.nl%3A8901%2F&f=7100000&m=LSB",
            ShareLink.build(TW, 7100000L, "LSB", 0, ""));
    }

    @Test public void roundTripAllFields() {
        ShareLink s = ShareLink.parse(ShareLink.build(TW, 7074000L, "USB", 2900, "FT8"), MODES);
        assertNotNull(s);
        assertEquals(TW, s.url); assertEquals(7074000L, s.hz); assertEquals("USB", s.mode);
        assertEquals(2900, s.bwHz); assertEquals("FT8", s.decoder);
    }

    @Test public void roundTripMinimalHasDefaults() {
        ShareLink s = ShareLink.parse(ShareLink.build(TW, 3650000L, "LSB", 0, null), MODES);
        assertNotNull(s);
        assertEquals(0, s.bwHz); assertEquals("", s.decoder);
    }

    @Test public void urlWithSpecialCharacters() {
        String u = "http://kiwi.example.org:8073/?f=7074usb&z=10#top";
        assertEquals(u, ShareLink.parse(ShareLink.build(u, 7074000L, "USB", 0, ""), MODES).url);
        String v = "https://sdr.exemple.fr/réception a+b/";
        assertEquals(v, ShareLink.parse(ShareLink.build(v, 7074000L, "USB", 0, ""), MODES).url);
    }

    @Test public void decoderWithSpace() {
        assertEquals("RADE V1", ShareLink.parse(ShareLink.build(TW, 14236000L, "USB", 0, "RADE V1"), MODES).decoder);
    }

    @Test public void lowercaseModeAndUppercaseSchemeAccepted() {
        ShareLink s = ShareLink.parse("PhoneSDRLink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=usb", MODES);
        assertNotNull(s);
        assertEquals("USB", s.mode);
    }

    @Test public void unknownParametersIgnored() {
        assertNotNull(ShareLink.parse("phonesdrlink://tune?v=2&u=http%3A%2F%2Fa.b%2F&zz=1&f=7074000&m=USB", MODES));
    }

    @Test public void rejectsWrongSchemeOrHost() {
        assertNull(ShareLink.parse(null, MODES));
        assertNull(ShareLink.parse("https://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://other?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB", MODES));
    }

    @Test public void rejectsMissingRequired() {
        assertNull(ShareLink.parse("phonesdrlink://tune?f=7074000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000", MODES));
    }

    @Test public void rejectsFrequencyOutOfRange() {
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=1000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=500000000&m=USB", MODES));
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=abc&m=USB", MODES));
    }

    @Test public void rejectsUnknownMode() {
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=XYZ", MODES));
    }

    @Test public void rejectsNonWebUrl() {
        assertNull(ShareLink.parse(ShareLink.build("javascript:alert(1)", 7074000L, "USB", 0, ""), MODES));
        assertNull(ShareLink.parse(ShareLink.build("file:///sdcard/x.html", 7074000L, "USB", 0, ""), MODES));
        assertNull(ShareLink.parse(ShareLink.build("http://", 7074000L, "USB", 0, ""), MODES));
    }

    @Test public void rejectsMalformedEscape() {
        assertNull(ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2&f=7074000&m=USB", MODES));
    }

    @Test public void ignoresBandwidthOutOfRange() {
        assertEquals(0, ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB&bw=50", MODES).bwHz);
        assertEquals(0, ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB&bw=99999", MODES).bwHz);
        assertEquals(0, ShareLink.parse("phonesdrlink://tune?u=http%3A%2F%2Fa.b%2F&f=7074000&m=USB&bw=x", MODES).bwHz);
    }
}
```

- [ ] **Step 3 : lancer le test, il doit échouer**

Run: `$G -q testDebugUnitTest --tests fr.f5pbg.phonesdrlink.ShareLinkTest`
Expected: échec de compilation, `cannot find symbol ... ShareLink`.

- [ ] **Step 4 : implémentation** — `app/src/main/java/fr/f5pbg/phonesdrlink/ShareLink.java` :

```java
package fr.f5pbg.phonesdrlink;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

// Lien de partage d'une écoute : phonesdrlink://tune?u=<WebSDR>&f=<Hz>&m=<mode>[&bw=<Hz>][&d=<décodeur>].
// Java pur (testé en JUnit, sans téléphone). Le décodeur est désigné par son nom (MainActivity.decName), pas par son numéro,
// pour que les liens restent valables si les numéros changent. Paramètres inconnus ignorés (versions futures).
final class ShareLink {
    static final String SCHEME = "phonesdrlink";
    static final String PREFIX = SCHEME + "://tune?";
    static final int MIN_BW = 100, MAX_BW = 15000;

    final String url, mode, decoder;   // decoder : "" = aucun
    final long hz;
    final int bwHz;                    // 0 = filtre d'origine du WebSDR

    private ShareLink(String url, long hz, String mode, int bwHz, String decoder) {
        this.url = url; this.hz = hz; this.mode = mode; this.bwHz = bwHz; this.decoder = decoder;
    }

    static String build(String url, long hz, String mode, int bwHz, String decoder) {
        StringBuilder sb = new StringBuilder(PREFIX);
        sb.append("u=").append(enc(url)).append("&f=").append(hz).append("&m=").append(enc(mode));
        if (bwHz > 0) sb.append("&bw=").append(bwHz);
        if (decoder != null && !decoder.isEmpty()) sb.append("&d=").append(enc(decoder));
        return sb.toString();
    }

    // null si le lien n'est pas valable (u, f ou m manquant ou invalide, échappement % abîmé) ; bw et d invalides sont ignorés.
    static ShareLink parse(String link, String[] modes) {
        if (link == null || !link.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) return null;
        String q = link.substring(PREFIX.length());
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

    static boolean isWeb(String u) {
        String l = u.toLowerCase(Locale.ROOT);
        int start = l.startsWith("http://") ? 7 : l.startsWith("https://") ? 8 : -1;
        return start > 0 && l.length() > start;
    }

    static boolean contains(String[] a, String s) { for (String x : a) if (x.equals(s)) return true; return false; }

    static long toLong(String s) { try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return -1; } }

    static String enc(String s) { try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; } }
}
```

(`Store.MIN_HZ` / `Store.MAX_HZ` sont des constantes `static final long` : elles sont recopiées à la compilation, la classe `Store` n'est pas chargée pendant le test.)

- [ ] **Step 5 : relancer, tout doit passer**

Run: `$G -q testDebugUnitTest --tests fr.f5pbg.phonesdrlink.ShareLinkTest && echo OK`
Expected: `OK`, 14 tests passés (rapport : `app/build/reports/tests/testDebugUnitTest/index.html`).

- [ ] **Step 6 : commit**

```bash
git add app/build.gradle app/src/main/java/fr/f5pbg/phonesdrlink/ShareLink.java app/src/test
git commit -m "Partage : ShareLink (construction et lecture du lien phonesdrlink://) et tests JUnit"
```

---

### Task 2 : réception du lien (manifeste, confirmation, application)

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (activité `.MainActivity`)
- Modify: `app/src/main/java/fr/f5pbg/phonesdrlink/MainActivity.java` (fin de `onCreate`, `onNewIntent`, nouvelles méthodes après `openUrl`)

- [ ] **Step 1 : manifeste** — ajouter dans `<activity android:name=".MainActivity" …>`, après l'`intent-filter` MAIN/LAUNCHER :

```xml
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="phonesdrlink" android:host="tune" />
            </intent-filter>
```

- [ ] **Step 2 : `onCreate`** — juste après `web.loadUrl(first != null ? first : st.lastUrl);` :

```java
        final Intent shareIntent = getIntent();
        h.post(new Runnable() { public void run() { handleShareIntent(shareIntent); } });   // lien de partage : question posée devant la page habituelle
```

- [ ] **Step 3 : `onNewIntent`** — remplacer le corps par :

```java
        super.onNewIntent(i);
        debugIntent(i);
        String u = i.getStringExtra("url");
        if (u != null) openUrl(u);
        handleShareIntent(i);
```

- [ ] **Step 4 : méthodes** — ajouter juste après la méthode `openUrl(String url)` :

```java
    // Lien de partage reçu (phonesdrlink://tune?…, voir ShareLink) : confirmation, puis WebSDR, fréquence, mode, largeur et décodeur.
    // La confirmation est obligatoire : le lien vient d'un tiers et fait charger une page quelconque dans la WebView (pont PhoneSdr).
    void handleShareIntent(Intent i) {
        if (i == null || i.getData() == null || !ShareLink.SCHEME.equalsIgnoreCase(i.getData().getScheme())) return;
        final ShareLink s = ShareLink.parse(i.getData().toString(), MODES);
        if (s == null) { status(Lang.T("Lien de partage invalide", "Invalid share link")); return; }
        if (blocked()) return;
        final int dec = decFromName(s.decoder);
        Ui.confirm(this, Lang.T("Fréquence partagée", "Shared frequency"),
            Lang.F("Ouvrir {0} sur {1} ?", "Open {0} on {1}?", Uri.parse(s.url).getHost(), listenText(s.hz, s.mode, dec)),
            new Ui.Yes() { public void yes() { applyShare(s, dec); } });
    }

    void applyShare(ShareLink s, int dec) {
        if (blocked()) return;
        if (scanning) stopScan(null);
        freq = s.hz;
        mode = s.mode;
        openUrl(s.url);                      // bwHz = largeur retenue pour le mode ; au premier état de la page, fréquence et mode sont imposés
        if (s.bwHz > 0) bwHz = s.bwHz;       // pour cette écoute seulement : st.setBwFor n'est pas appelé
        if (dec != 0) decoderStart(dec);     // sans décodeur dans le lien, celui qui tourne continue (comme la liste « Mem »)
        updateDisplays();
        saveSoon();
    }

    // « 7074 kHz USB (FT8) » : texte du partage et de la confirmation.
    static String listenText(long hz, String m, int dec) { return Memory.kHz(hz) + " kHz " + modeLabel(m) + (dec != 0 ? " (" + decName(dec) + ")" : ""); }

    // Inverse de decName : 0 si le nom est inconnu.
    static int decFromName(String n) {
        if (n == null || n.isEmpty()) return 0;
        for (int d = 1; d <= DEC_WEFAX; d++) if (decName(d).equalsIgnoreCase(n)) return d;
        return 0;
    }
```

(`DEC_WEFAX = 16` est le plus grand numéro de décodeur, ligne `static final int DEC_FT8 = 1, …`.)

- [ ] **Step 5 : compilation**

Run: `$G -q assembleDebug && echo OK`
Expected: `OK`.

- [ ] **Step 6 : essai sur téléphone** (adb ; vérifier d'abord le premier plan : `adb shell dumpsys window | grep mCurrentFocus`)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop fr.f5pbg.phonesdrlink
adb shell am start -a android.intent.action.VIEW -d "'phonesdrlink://tune?u=http%3A%2F%2Fwebsdr.ewi.utwente.nl%3A8901%2F&f=7074000&m=USB&bw=2900&d=FT8'"
```

Expected : question « Ouvrir websdr.ewi.utwente.nl sur 7074 kHz USB (FT8) ? » ; OK → WebSDR de Twente sur 7074 USB, largeur 2,9k, décodeur FT8 lancé. Recommencer appli ouverte (même commande sans `force-stop`), avec Annuler (rien ne change), avec Verrou rouge (message 🔒), avec un lien invalide (`f=1`) → « Lien de partage invalide ».

- [ ] **Step 7 : commit**

```bash
git add app/src/main/AndroidManifest.xml $J/MainActivity.java
git commit -m "Partage : ouverture des liens phonesdrlink://tune (confirmation, WebSDR, frequence, mode, largeur, decodeur)"
```

---

### Task 3 : bouton « partage » au-dessus de INIT

**Files:**
- Modify: `app/src/main/java/fr/f5pbg/phonesdrlink/MainActivity.java` (construction de `zoomBox`, nouvelles méthodes après `initButton()`)

- [ ] **Step 1 : bouton dans le bloc** — dans la construction de `zoomBox` (`zoom.addView(initButton());`) :

```java
        zoom.addView(shareButton());
        zoom.addView(initButton());
```

(`placeZoom()` règle les marges de tous les enfants du bloc : le bouton suit les 8 positions.)

- [ ] **Step 2 : méthodes** — ajouter juste après la méthode `initButton()` :

```java
    // Rond « partage » (au-dessus de INIT) : partage de l'écoute en cours par la fenêtre de partage d'Android. Actif même verrouillé.
    View shareButton() {
        android.widget.ImageButton b = new android.widget.ImageButton(this);
        b.setImageResource(android.R.drawable.ic_menu_share);
        b.setColorFilter(Color.WHITE);
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int pad = Ui.dp(this, 10);
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(Ui.round(0xAA000000, 24, this));
        b.setContentDescription(Lang.T("Partager la fréquence", "Share the frequency"));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { shareCurrent(); } });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        p.setMargins(0, Ui.dp(this, 4), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    // Texte partagé : écoute lisible, lien PhoneSDRLink, et adresse du WebSDR pour ceux qui n'ont pas l'application.
    void shareCurrent() {
        String u = currentUrl();
        if (u == null || !ShareLink.isWeb(u)) { status(Lang.T("Aucun WebSDR à partager", "No WebSDR to share")); return; }
        String title = currentTitle().trim();
        if (title.isEmpty()) title = Uri.parse(u).getHost();
        String link = ShareLink.build(u, freq, mode, bwHz, decMode != 0 ? decName(decMode) : "");
        String text = "📻 " + Lang.F("{0} sur {1}", "{0} on {1}", listenText(freq, mode, decMode), title)
            + "\nPhoneSDRLink : " + link
            + "\n" + Lang.F("Navigateur : {0}", "Browser: {0}", u);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, Lang.T("Partager la fréquence", "Share the frequency")));
    }
```

- [ ] **Step 3 : compilation**

Run: `$G -q assembleDebug && echo OK`
Expected: `OK`.

- [ ] **Step 4 : essai sur téléphone** — installer, puis : rond de partage au-dessus de INIT ; appui → fenêtre de partage Android ; partager vers une note ou une messagerie et lire le texte ; appui sur le lien reçu (si cliquable) → Task 2 ; positions du bloc (appui 2 s sur + : les 8 positions) ; Verrou rouge → le partage marche toujours.

- [ ] **Step 5 : commit**

```bash
git add $J/MainActivity.java
git commit -m "Partage : bouton de partage au-dessus de INIT (fenetre de partage d'Android)"
```

---

### Task 4 : traductions (21 langues)

**Files:**
- Create: `tools/lang-share.py` (script d'ajout, gardé pour la trace)
- Modify: `app/src/main/assets/lang/*.txt` (21 fichiers)

Clés anglaises (exactement celles du code, sans espace de fin) : `Share the frequency`, `No WebSDR to share`, `Invalid share link`, `Shared frequency`, `Open {0} on {1}?`, `{0} on {1}`, `Browser: {0}`.

- [ ] **Step 1 : script** — `tools/lang-share.py` :

```python
# Ajoute les textes du partage de fréquence aux 21 fichiers assets/lang (une clé déjà présente n'est pas ajoutée).
import os
KEYS = ["Share the frequency", "No WebSDR to share", "Invalid share link", "Shared frequency", "Open {0} on {1}?", "{0} on {1}", "Browser: {0}"]
T = {
 "de": ["Frequenz teilen", "Kein WebSDR zum Teilen", "Ungültiger Freigabelink", "Geteilte Frequenz", "{0} auf {1} öffnen?", "{0} auf {1}", "Browser: {0}"],
 "es": ["Compartir la frecuencia", "Ningún WebSDR para compartir", "Enlace compartido no válido", "Frecuencia compartida", "¿Abrir {0} en {1}?", "{0} en {1}", "Navegador: {0}"],
 "it": ["Condividi la frequenza", "Nessun WebSDR da condividere", "Link di condivisione non valido", "Frequenza condivisa", "Aprire {0} su {1}?", "{0} su {1}", "Browser: {0}"],
 "nl": ["Frequentie delen", "Geen WebSDR om te delen", "Ongeldige deellink", "Gedeelde frequentie", "{0} openen op {1}?", "{0} op {1}", "Browser: {0}"],
 "pt": ["Partilhar a frequência", "Nenhum WebSDR para partilhar", "Link de partilha inválido", "Frequência partilhada", "Abrir {0} em {1}?", "{0} em {1}", "Navegador: {0}"],
 "pl": ["Udostępnij częstotliwość", "Brak WebSDR do udostępnienia", "Nieprawidłowy link udostępniania", "Udostępniona częstotliwość", "Otworzyć {0} na {1}?", "{0} na {1}", "Przeglądarka: {0}"],
 "cs": ["Sdílet frekvenci", "Žádné WebSDR ke sdílení", "Neplatný odkaz ke sdílení", "Sdílená frekvence", "Otevřít {0} na {1}?", "{0} na {1}", "Prohlížeč: {0}"],
 "sk": ["Zdieľať frekvenciu", "Žiadne WebSDR na zdieľanie", "Neplatný odkaz na zdieľanie", "Zdieľaná frekvencia", "Otvoriť {0} na {1}?", "{0} na {1}", "Prehliadač: {0}"],
 "sl": ["Deli frekvenco", "Ni WebSDR za deljenje", "Neveljavna povezava za deljenje", "Deljena frekvenca", "Odprem {0} na {1}?", "{0} na {1}", "Brskalnik: {0}"],
 "hr": ["Podijeli frekvenciju", "Nema WebSDR-a za dijeljenje", "Neispravna poveznica za dijeljenje", "Podijeljena frekvencija", "Otvoriti {0} na {1}?", "{0} na {1}", "Preglednik: {0}"],
 "hu": ["Frekvencia megosztása", "Nincs megosztható WebSDR", "Érvénytelen megosztási hivatkozás", "Megosztott frekvencia", "Megnyitja: {0} – {1}?", "{0} – {1}", "Böngésző: {0}"],
 "ro": ["Partajează frecvența", "Niciun WebSDR de partajat", "Link de partajare nevalid", "Frecvență partajată", "Deschideți {0} pe {1}?", "{0} pe {1}", "Browser: {0}"],
 "bg": ["Сподели честотата", "Няма WebSDR за споделяне", "Невалидна връзка за споделяне", "Споделена честота", "Да се отвори ли {0} на {1}?", "{0} на {1}", "Браузър: {0}"],
 "el": ["Κοινοποίηση συχνότητας", "Κανένα WebSDR για κοινοποίηση", "Μη έγκυρος σύνδεσμος κοινοποίησης", "Κοινόχρηστη συχνότητα", "Άνοιγμα {0} στα {1};", "{0} στα {1}", "Πρόγραμμα περιήγησης: {0}"],
 "sv": ["Dela frekvensen", "Ingen WebSDR att dela", "Ogiltig delningslänk", "Delad frekvens", "Öppna {0} på {1}?", "{0} på {1}", "Webbläsare: {0}"],
 "da": ["Del frekvensen", "Ingen WebSDR at dele", "Ugyldigt delingslink", "Delt frekvens", "Åbn {0} på {1}?", "{0} på {1}", "Browser: {0}"],
 "nb": ["Del frekvensen", "Ingen WebSDR å dele", "Ugyldig delingslenke", "Delt frekvens", "Åpne {0} på {1}?", "{0} på {1}", "Nettleser: {0}"],
 "fi": ["Jaa taajuus", "Ei jaettavaa WebSDR:ää", "Virheellinen jakolinkki", "Jaettu taajuus", "Avataanko {0}: {1}?", "{0} – {1}", "Selain: {0}"],
 "et": ["Jaga sagedust", "Pole WebSDR-i, mida jagada", "Vigane jagamislink", "Jagatud sagedus", "Kas avada {0}: {1}?", "{0} – {1}", "Brauser: {0}"],
 "lv": ["Kopīgot frekvenci", "Nav WebSDR, ko kopīgot", "Nederīga kopīgošanas saite", "Kopīgotā frekvence", "Atvērt {0}: {1}?", "{0} – {1}", "Pārlūks: {0}"],
 "lt": ["Bendrinti dažnį", "Nėra WebSDR bendrinimui", "Netinkama bendrinimo nuoroda", "Bendrintas dažnis", "Atidaryti {0}: {1}?", "{0} – {1}", "Naršyklė: {0}"],
}
d = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "lang")
for code, vals in T.items():
    p = os.path.join(d, code + ".txt")
    with open(p, encoding="utf-8") as f: have = {l.split("\t", 1)[0] for l in f.read().splitlines() if "\t" in l}
    add = [k + "\t" + v for k, v in zip(KEYS, vals) if k not in have]
    if add:
        with open(p, "a", encoding="utf-8", newline="\n") as f: f.write("\n".join(add) + "\n")
    print(code, len(add))
```

- [ ] **Step 2 : exécuter**

Run: `python3 tools/lang-share.py`
Expected : 21 lignes `xx 7` (ou moins si une clé existait déjà).

- [ ] **Step 3 : contrôle** — chaque fichier a les 7 clés, une fois chacune :

Run: `for k in "Share the frequency" "No WebSDR to share" "Invalid share link" "Shared frequency" "Open {0} on {1}?" "{0} on {1}" "Browser: {0}"; do echo "$(grep -l -F "$k"$'\t' app/src/main/assets/lang/*.txt | wc -l)  $k"; done`
Expected : `21` devant chaque clé.

- [ ] **Step 4 : commit**

```bash
git add tools/lang-share.py app/src/main/assets/lang
git commit -m "Partage : traductions (21 langues)"
```

---

### Task 5 : documentation

**Files:**
- Modify: `README.md` (après la ligne `* **Appui long sur Verrou** : …`, ligne 56)
- Modify: `LISEZMOI.md` (section « Où est quoi », puce des sources)

- [ ] **Step 1 : README** — insérer après la ligne 56 :

```markdown
* **Partager une fréquence** : le rond de partage, au-dessus de **INIT**, ouvre la fenêtre de partage d'Android (messagerie, mail, SMS…). Le message contient l'écoute (« 📻 7074 kHz USB (FT8) sur … »), un lien `phonesdrlink://tune?…` et l'adresse du WebSDR pour ceux qui n'ont pas l'application. Chez un ami qui a PhoneSDRLink, le lien ouvre l'application (après confirmation) sur le **même WebSDR, la même fréquence, le même mode, la même largeur de bande** et, s'il tournait, **le même décodeur** ; la largeur reçue ne remplace pas ses propres réglages. Certaines messageries ne rendent pas ce lien cliquable : le copier dans le navigateur du téléphone. Verrouillé, le partage marche toujours, mais un lien reçu n'est pas ouvert.
```

- [ ] **Step 2 : LISEZMOI** — dans « Où est quoi », à la fin de la puce `Finder : …` (ligne des `Dlg*`), ajouter : `` `ShareLink` : lien de partage `phonesdrlink://tune?u=&f=&m=[&bw=][&d=]` (Java pur, tests JUnit dans `app\src\test`, `gradle testDebugUnitTest`) ; bouton et réception dans `MainActivity` (`shareButton`, `handleShareIntent`). ``

- [ ] **Step 3 : commit**

```bash
git add README.md LISEZMOI.md
git commit -m "Partage : documentation"
```

---

### Task 6 : vérification finale

- [ ] **Step 1** : `$G -q testDebugUnitTest assembleDebug && echo OK` → `OK`.
- [ ] **Step 2** : sur téléphone, refaire les essais des Tasks 2 et 3 avec l'APK final, dont un aller-retour réel : partager depuis l'appli → copier le lien → l'ouvrir → même WebSDR / fréquence / mode / largeur / décodeur.
- [ ] **Step 3** : `git status` propre, `git log --oneline main..` = commits de la spec, du plan et des Tasks 1 à 5.
