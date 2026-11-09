# Partage de fréquence par deep link — conception

Date : 2026-11-09 · Branche : `feature/share-link`

## But

Partager à des amis ce qu'on écoute : un lien qui ouvre PhoneSDRLink sur le **même WebSDR**, la **même fréquence**,
le **même mode** (USB, LSB…), la même largeur de bande et, s'il tourne, le même décodeur.

## Format du lien (schéma perso uniquement)

```
phonesdrlink://tune?u=<adresse du WebSDR, encodée>&f=<Hz>&m=<mode>[&bw=<Hz>][&d=<décodeur>]
```

| Paramètre | Obligatoire | Valeurs acceptées |
|---|---|---|
| `u` | oui | adresse `http://` ou `https://` (encodée avec `URLEncoder`, UTF-8) |
| `f` | oui | entier, `Store.MIN_HZ` (1,8 MHz) à `Store.MAX_HZ` (440 MHz) |
| `m` | oui | un des `MainActivity.MODES` : `USB`, `LSB`, `AM`, `FM`, `DATAU`, `CW` |
| `bw` | non | 100 à 15000 ; absent = filtre d'origine du WebSDR |
| `d` | non | nom de décodeur tel que donné par `MainActivity.decName()` (`FT8`, `RADE V1`…) ; absent = aucun |

Le décodeur est désigné par son **nom** et non par son numéro interne `DEC_*`, pour que les liens restent valables si les
numéros changent. Paramètres inconnus : ignorés (compatibilité avec de futures versions).

Pas d'hébergement https : choix assumé. Le message partagé contient donc aussi l'adresse du WebSDR en clair, pour que
le lien reste utile si la messagerie ne rend pas `phonesdrlink://` cliquable ou si l'ami n'a pas l'application.

## Bouton « partage »

- Rond de 48 dp, même style que INIT (`Ui.round(0xAA000000, 24, …)`), **au-dessus de INIT** dans `zoomBox` : il suit donc
  les 8 positions du bloc INIT / + / −. Icône : `android.R.drawable.ic_menu_share` (blanche).
- Appui : fenêtre de partage native d'Android (`Intent.ACTION_SEND`, `text/plain`, `Intent.createChooser`).
- Texte partagé (traduit) :
  ```
  📻 7074 kHz USB (FT8) — <titre de la page, sinon hôte>
  PhoneSDRLink : phonesdrlink://tune?u=…&f=7074000&m=USB&d=FT8
  Navigateur : http://websdr…/
  ```
- Actif même quand le **Verrou** est mis (partager ne modifie rien).
- Sans WebSDR chargé (adresse vide) : message d'état, pas de partage.

## Réception du lien

- `AndroidManifest.xml` : second `intent-filter` sur `MainActivity` — `VIEW`, catégories `DEFAULT` + `BROWSABLE`,
  `scheme="phonesdrlink"`, `host="tune"`. L'activité est déjà `singleTop`.
- Point d'entrée : `onCreate` (application fermée) et `onNewIntent` (application ouverte) appellent
  `handleShareIntent(Intent)` si `intent.getData()` a le schéma `phonesdrlink`.
- Lecture et vérification par `ShareLink.parse(String)`. `u`, `f` ou `m` manquant/invalide → rien n'est appliqué,
  message « Lien de partage invalide ». `bw` ou `d` invalide → ignoré seul.
- **Confirmation obligatoire** (`Ui.confirm`) : « Ouvrir <hôte> sur 7074 kHz USB (FT8) ? ». Le lien vient d'un tiers et
  fait charger une page quelconque dans la WebView qui expose le pont `PhoneSdr`.
  - Au démarrage par un lien, la page habituelle (`st.lastUrl`) se charge normalement derrière la question.
  - Annuler : rien ne change.
- Acceptation (`applyShare`) :
  1. arrêt du scan s'il tourne ;
  2. `freq` = f, `mode` = m ;
  3. `openUrl(u)` (remet `bwHz` à la valeur retenue du mode), puis `bwHz` = bw si présent — **pour cette écoute
     seulement** : `st.setBwFor` n'est pas appelé, les réglages par mode de l'ami ne changent pas ;
  4. décodeur : si `d` est présent, `decMode` est réglé et le décodeur démarre comme lors d'un changement de page
     (`decoderStart`) ; un décodeur déjà actif sans `d` dans le lien continue (comme la liste « Mem ») ;
  5. le mécanisme existant fait le reste : au premier état de la page (`onSdrState`, cas `first`) l'application impose
     sa fréquence, son mode et sa largeur au WebSDR.
- Un REC en cours continue (même comportement qu'un changement de WebSDR). Le Verrou est respecté :
  verrouillé → message « 🔒 Verrouillé » et le lien n'est pas appliqué.

## Découpage du code

- **`ShareLink.java`** (nouveau, sans dépendance Android → testable en JUnit pur) :
  - `static String build(String url, long hz, String mode, int bwHz, String decoder)`
  - `static ShareLink parse(String link)` → `null` si invalide ; champs `url`, `hz`, `mode`, `bwHz` (0 = absent),
    `decoder` (`""` = absent)
  - analyse de la requête à la main (`URLDecoder`), pas `android.net.Uri`.
- **`MainActivity.java`** : `shareButton()` (sur le modèle de `initButton()`, ajouté avant INIT dans `zoomBox`),
  `shareCurrent()`, `handleShareIntent(Intent)`, `applyShare(ShareLink)`, `decFromName(String)` (inverse de `decName`).
- **`AndroidManifest.xml`** : `intent-filter` ci-dessus.
- **Langues** : textes via `Lang.T` / `Lang.F` ; traductions anglais → 21 langues ajoutées aux `assets/lang/*.txt`.
- **`app/build.gradle`** : `testImplementation 'junit:junit:4.13.2'`.
- **README.md / LISEZMOI.md** : paragraphe « Partager une fréquence ».

## Tests

- JUnit (`app/src/test/.../ShareLinkTest.java`) : aller-retour `build` → `parse` ; adresse contenant `?`, `&`, `#`,
  espaces, accents ; décodeur avec espace (`RADE V1`) ; `f` hors plage, mode inconnu, schéma `javascript:` / `file:`,
  `bw` hors plage, paramètres manquants, paramètres inconnus ignorés.
- Sur téléphone (adb) : `adb shell am start -a android.intent.action.VIEW -d "phonesdrlink://tune?…"` application
  fermée puis ouverte ; confirmation / annulation ; Verrou ; bouton de partage dans les 8 positions ; partage réel vers
  une messagerie.

## Hors périmètre

Lien https / App Links, QR code, zoom de la cascade, partage depuis SDRLink (PC).

## Révision du 2026-11-09 : lien https

Les messageries ne rendent pas `phonesdrlink://` cliquable. Le lien partagé devient
`https://bt0r.github.io/PhoneSDRLink/s/?u=…&f=…&m=…[&bw=…][&d=…]` (`ShareLink.WEB_BASE`, mêmes paramètres) ;
`phonesdrlink://tune?…` reste accepté.

- Page GitHub Pages `docs/s/index.html` (Pages du fork `bt0r`, source : dossier `docs`) : affiche l'écoute, bouton
  « Ouvrir dans PhoneSDRLink » (`intent://` vers `phonesdrlink://`, repli vers la page d'installation), « Écouter dans le
  navigateur », « Installer PhoneSDRLink ». Elle refait le lien à partir des seuls paramètres connus et refuse toute adresse
  autre que http/https.
- Manifeste : `intent-filter` https (`autoVerify`) sur `bt0r.github.io/PhoneSDRLink/s`. Sans `assetlinks.json` à la racine
  du domaine (dépôt `bt0r.github.io`), Android ne vérifie pas le lien : il s'ouvre dans le navigateur, sauf si l'utilisateur
  l'ajoute dans Ouvrir par défaut.
- Message partagé : ligne d'écoute + lien https (la page remplace la ligne « Navigateur »).
- Passage au dépôt de F5PBG : changer `ShareLink.WEB_BASE`, l'hôte et le chemin de l'intent-filter, `APK_URL` de la page.

## Révision du 2026-11-09 : dépôt de F5PBG

Après l'essai sur le fork `bt0r`, le lien pointe sur les Pages du dépôt de F5PBG :
`https://f5pbgludovic.github.io/PhoneSDRLink/s/` (`ShareLink.WEB_BASE`, intent-filter https du manifeste, tests).
Les liens de l'essai (`bt0r.github.io`) ne sont plus reconnus. Réglage GitHub Pages (dossier `docs` de `main`) et
ouverture directe facultative (`assetlinks.json` dans le dépôt `F5pbgLudovic.github.io`, empreinte de la clé des
releases) : `docs/GITHUB-PAGES.md`.
