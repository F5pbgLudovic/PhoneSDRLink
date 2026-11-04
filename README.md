# PhoneSDRLink

**Français** — Application Android (APK) du radioamateur F5PBG qui reprend les fonctions de réception de [SDRLink](https://github.com/F5pbgLudovic/SDRLink), sans la télécommande du poste : on écoute un WebSDR (websdr.org, KiwiSDR, OpenWebRX, UberSDR, PhantomSDR) depuis son smartphone.

## Fonctions

* **WEBSDR** : favoris en listes (grandes cases), recherche dans les annuaires publics (websdr.org, KiwiSDR, ReceiverBook) au premier démarrage puis à la demande, gestion des favoris comme SDRLink (renommer, déplacer, vérifier, importer, exporter), adresse libre. Le son démarre sans action, comme dans SDRLink.
* **Fréquence** (1,8 à 440 MHz) : un appui ouvre un clavier (saisie en kHz). **Mode** USB / LSB / AM / FM / DATA / CW. **Barre à glisser** (droite = plus haut) avec pas de 100 Hz à 1 kHz.
* **Scan** de fréquences ou de mémoires, **Conf scan** (zones non scannées, profils nommés), **Mémoires** en listes. Un appui dans la page arrête le scan.
* **REC** : enregistre le son du WebSDR en MP3 dans `Musique/PhoneSDRLink` (appui long : ouvre ce dossier dans l'application Fichiers).
* **Muet**, **Verrou** (rouge = plus rien ne peut être modifié), **INIT** (relance le WebSDR si le son s'arrête), **+ / −** (taille de la page).
* **Zoom du spectre** automatique : 3 kHz de spectre pour environ 3 mm d'écran (websdr.org, KiwiSDR, OpenWebRX, UberSDR).
* **SET** : mode sombre, notch automatique (DNF), réducteur de bruit (DNR), écran toujours allumé, langue (23), mise à jour, nom donné aux WebSDR, import/export des réglages (compatible SDRLink pour PC), réinitialisation.
* Le volume du WebSDR suit les touches de volume du téléphone ; le son est coupé pendant un appel ; portrait et paysage automatiques.

## Installer sur un smartphone Android

Android 8 ou plus. L'application n'est pas dans le Play Store : on installe le fichier APK à la main.

1. Sur le téléphone, ouvrir la [dernière version](https://github.com/F5pbgLudovic/PhoneSDRLink/releases/latest) et télécharger `PhoneSDRLink-x.y.apk` (ou le télécharger sur PC et le copier sur le téléphone).
2. Ouvrir le fichier (notification de téléchargement, ou application Fichiers › Téléchargements).
3. Android demande l'autorisation d'installer des applications « de sources inconnues » pour l'application qui a ouvert le fichier (Chrome, Fichiers…) : **Paramètres › Autoriser depuis cette source**, puis revenir et **Installer**.
4. Ouvrir **PhoneSDRLink**. Au premier lancement, la recherche des WebSDR se lance toute seule : cocher ceux qu'on veut garder et « Ajouter les cochés aux favoris ».

Pour mettre à jour : installer le nouvel APK par-dessus (même signature, les réglages sont conservés), ou SET › « Vérifier les mises à jour ».

### Si un antivirus ou Play Protect proteste

Un APK qui ne vient pas du Play Store, signé par un auteur inconnu, est souvent signalé « suspect » par les antivirus (c'est une détection prudente fondée sur la rareté du fichier, pas sur un défaut trouvé). PhoneSDRLink ne demande que l'accès à Internet, au réseau et aux réglages audio ; il n'accède ni aux contacts, ni aux SMS, ni à la localisation, ni au micro. Ses sources sont toutes publiques dans ce dépôt.

* **Google Play Protect** (« Application non reconnue » / « Bloquer ») : toucher **Plus de détails** puis **Installer quand même**. Si le message ne propose que « OK », ouvrir le Play Store › profil › **Play Protect** › roue dentée › désactiver temporairement « Analyser les applis avec Play Protect », installer, puis le réactiver.
* **AVG, Avast, Norton, Kaspersky, etc.** (« Application suspecte trouvée ») : choisir **Annuler** / **Ignorer** (surtout pas « Désinstaller »), puis dans l'antivirus **ajouter PhoneSDRLink aux exceptions** (AVG : Menu › Paramètres › Exceptions › Ajouter une application ; l'intitulé varie selon la marque). L'alerte peut revenir après chaque mise à jour.
* **Vérifier le fichier** (facultatif) : l'empreinte SHA-256 de chaque version est indiquée dans les notes de la release ; on peut la comparer à celle du fichier téléchargé (sous Windows : `Get-FileHash fichier.apk`).
* **Compiler soi-même** : les sources sont ici ; voir « Compiler » plus bas.
* Si l'installation échoue en disant que l'application est déjà installée avec une autre signature (APK compilé par quelqu'un d'autre), désinstaller d'abord l'ancienne version.

## Compiler / Build

`tools\install-toolchain.ps1` (une fois : JDK 17 + Gradle + SDK Android portables dans `toolchain\`, accepte les licences du SDK Android), puis `.\build.ps1` → `dist\PhoneSDRLink-<version>.apk`. La clé de signature `phonesdrlink.jks` n'est pas publiée : sans elle l'APK est compilé non signé (à signer avec sa propre clé pour l'installer).

Outils de mise au point (adb) : `adb shell am start -n fr.f5pbg.phonesdrlink/.MainActivity --es url <adresse>` ouvre un WebSDR ; `--ez dump true` copie les réglages dans `Android/data/fr.f5pbg.phonesdrlink/files/` ; `--es js "<code>"` évalue du JavaScript dans la page (résultat dans `logcat -s PSDR`).

Bibliothèque embarquée : [lamejs](https://github.com/zhuker/lamejs) (encodeur MP3, LGPL-3.0). Le pont JavaScript vers les WebSDR (`app/src/main/assets/bridge.js`) est dérivé de celui de SDRLink.

---

**English** — Android app (APK) by radio amateur F5PBG, built on the receiving side of [SDRLink](https://github.com/F5pbgLudovic/SDRLink) without the rig control: listen to a WebSDR (websdr.org, KiwiSDR, OpenWebRX, UberSDR, PhantomSDR) from a smartphone.

**Features**: favorite lists, automatic search of the public directories, frequency keypad (1.8–440 MHz), modes, swipe tuning bar (100 Hz–1 kHz steps), frequency/memory scan with named profiles, **REC to MP3** (`Music/PhoneSDRLink`, long-press opens the folder), mute, lock, INIT (restart the WebSDR), page zoom, automatic spectrum zoom (3 kHz ≈ 3 mm), auto notch, noise reducer, keep-screen-on, 23 languages, settings import/export (compatible with SDRLink for PC), dark mode, update check. The page volume follows the phone volume keys; sound is muted during a phone call; portrait/landscape follow the phone.

**Install** (Android 8+): download `PhoneSDRLink-x.y.apk` from the [latest release](https://github.com/F5pbgLudovic/PhoneSDRLink/releases/latest), open it, allow "install unknown apps" for the app you opened it from, tap Install. Update by installing the new APK over the old one.

**Antivirus / Play Protect warnings**: an APK from outside the Play Store signed by an unknown author is often flagged "suspicious" (heuristic, based on rarity). The app only needs Internet, network state and audio settings. In Play Protect tap **More details › Install anyway**; in AVG/Avast/Norton choose **Cancel/Ignore** (not Uninstall) and add PhoneSDRLink to the antivirus exceptions. The SHA-256 of each APK is in the release notes, and the full source is in this repository, so you can also build it yourself (see *Compiler / Build*).

73 de F5PBG
