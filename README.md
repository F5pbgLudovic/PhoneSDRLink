# PhoneSDRLink

**Français** — Application Android (APK) du radioamateur F5PBG qui reprend les fonctions de réception de [SDRLink](https://github.com/F5pbgLudovic/SDRLink), sans la télécommande du poste : on écoute un WebSDR (websdr.org, KiwiSDR, OpenWebRX, UberSDR, PhantomSDR) depuis son smartphone.

## Fonctions

* **WEBSDR** : favoris en listes (grandes cases), recherche dans les annuaires publics (websdr.org, KiwiSDR, ReceiverBook) au premier démarrage puis à la demande, gestion des favoris comme SDRLink (renommer, déplacer, vérifier, importer, exporter, **écouter la sélection sans quitter la fenêtre** : le WebSDR joue derrière), adresse libre. Le son démarre sans action, comme dans SDRLink.
* **Fréquence** (1,8 à 440 MHz) : un appui ouvre un clavier (saisie en kHz). **Mode** USB / LSB / AM / FM / DATA / CW ; **appui long sur le mode : largeur de bande** du signal (2,1 / 2,4 / 2,6 / 2,8 / 2,9 / 3 / 4 kHz, ou une valeur de 0,3 à 12 kHz ; réglée dans le WebSDR lui-même : websdr.org, KiwiSDR, OpenWebRX, UberSDR, PhantomSDR). **Barre à glisser** (droite = plus haut) avec pas de 1 Hz (USB/LSB seulement) à 10 kHz (1 Hz, 10 Hz, 50 Hz, 100 Hz, 250 Hz, 500 Hz, 1 kHz, 10 kHz) ; un geste rapide la lance comme une roue qui ralentit, un doigt posé l'arrête.
* **Scan** de fréquences ou de mémoires, **Conf scan** (zones non scannées, profils nommés), **Mémoires** en listes. Un appui dans la page arrête le scan.
* **REC** : enregistre le son du WebSDR en MP3 dans `Musique/PhoneSDRLink` (appui long : ouvre ce dossier dans l'application Fichiers).
* **Muet** (appui long : **minuterie** de 15, 30, 45, 60 minutes ou une durée au choix ; le son est coupé puis l'application se ferme ; si un REC est en cours, l'application demande quand l'arrêter, éventuellement après la coupure du son, et ne se ferme qu'à la fin du REC), **Verrou** (rouge = plus rien ne peut être modifié, **INIT**, **+** et **−** compris ; seuls Muet et Verrou restent actifs), **INIT** (relance le WebSDR si le son s'arrête et remet l'affichage d'origine, zoom compris), **+ / −** (taille de la page ; le zoom, à la main ou avec + et −, n'est pas modifié par un changement de fréquence).
* **Zoom du spectre** automatique : 3 kHz de spectre pour environ 3 mm d'écran (websdr.org, KiwiSDR, OpenWebRX, UberSDR).
* **SET** : mode sombre, notch automatique (DNF) et réducteur de bruit (DNR) avec 10 niveaux chacun, réinitialisation automatique du WebSDR après 30 s sans son, écran toujours allumé, couleurs de l'écran (fond, boutons, fréquence, réglette), langue (23), mise à jour, nom donné aux WebSDR, import/export des réglages (compatible SDRLink pour PC), réinitialisation.
* Le volume du WebSDR suit les touches de volume du téléphone ; le son est coupé pendant un appel ; portrait et paysage automatiques.
* **Écran éteint** : l'écoute continue, sans saccades (service de lecture avec notification « Écoute du WebSDR en cours »). Si le son saccade quand même (téléphone à batterie très stricte : OnePlus, Xiaomi…), mettre la batterie de PhoneSDRLink sur « Sans restriction » (Paramètres › Applications › PhoneSDRLink › Batterie). Android 13 ou plus : accepter les notifications. Le tampon audio est large (environ 80 ms) pour que le son ne saccade pas, même pendant un REC.

## Appuis longs et gestes

* **Mode (appui long) : largeur de bande.** Les largeurs proposées dépendent du mode (USB/LSB 2,1 à 5 kHz, 2,9 kHz compris ; CW 100 à 800 Hz ; AM 6, 8, 10 kHz ; FM 8 à 15 kHz ; DATA 200 Hz à 4 kHz), ou « Autre valeur » (0,1 à 15 kHz), ou revenir au « filtre d'origine du WebSDR ». Le réglage est fait dans le WebSDR (websdr.org, KiwiSDR, OpenWebRX, UberSDR, PhantomSDR) ; la largeur choisie s'affiche sous le mode (par exemple « USB 2,4k »). En SSB le bord du filtre est à 300 Hz de la porteuse, en AM, FM et CW le filtre reste centré. Elle est remise après un changement de mode et au rechargement de la page, et revient au filtre d'origine quand on ouvre un autre WebSDR.
* **Muet (appui long) : minuterie.** 15, 30, 45, 60 minutes ou une durée au choix : à l'échéance le son est coupé, puis l'application se ferme (le bouton affiche le temps restant ; un nouvel appui long permet d'annuler). Si un REC est en cours, l'application demande dans combien de temps l'arrêter : en même temps que le son, ou plus tard (le REC enregistre le son avant la coupure, il continue donc après) ; l'application se ferme seulement quand le fichier MP3 est sauvegardé. Sans heure d'arrêt donnée au REC, il s'arrête avec le son.
* **REC (appui long)** : ouvre le dossier des enregistrements.
* **Barre à glisser** : un geste rapide lance la barre comme une roue qui ralentit peu à peu ; poser le doigt dessus l'arrête.
* **Gérer les favoris › Écouter la sélection** : le WebSDR sélectionné joue derrière la fenêtre, qui reste ouverte (on peut enchaîner les essais avant de ranger ses favoris).
* **REC qui continue** : changer de WebSDR ou appuyer sur INIT n'arrête plus l'enregistrement ; il reprend dans le même fichier MP3 dès que le son du nouveau WebSDR démarre (trou de 2 à 3 secondes).
* **WEBSDR › loupe** (à droite du nom de la liste) : un champ de recherche filtre la liste en cours, par nom ou par adresse.
* **SET › Nom ou indicatif donné aux WebSDR** : les espaces sont remplacés par le caractère invisible Alt+255 (espace insécable), que les WebSDR prennent en compte ; un nom réduit à un espace est donc invisible.
* **Mises à jour** : SET › « Vérifier les mises à jour » reste dans SET ; avec « Vérifier les mises à jour au démarrage » cochée, une alerte « Mise à jour disponible » propose d'installer : l'APK de la release GitHub est téléchargé puis installé par Android (qui demande d'abord d'autoriser l'installation depuis PhoneSDRLink, puis de confirmer). Sans APK ou en cas d'échec, la page GitHub s'ouvre.
* **Pas et largeur de bande par mode** : en CW le pas est de 10 Hz au départ, en USB/LSB de 1 kHz ; la largeur de bande par défaut est de 300 Hz en CW, 2,9 kHz en USB/LSB, 6 kHz en AM et 8 kHz en FM. Le pas ou la largeur que vous choisissez pour un mode devient celui de ce mode (USB et LSB vont ensemble), sur tous les WebSDR et après un redémarrage. « Réinitialiser le programme » (réinitialisation totale) remet les valeurs d'origine.
* **Notch et réducteur de bruit à 10 niveaux** : dans SET, un petit menu déroulant à côté de « Notch automatique » et de « Réducteur de bruit » choisit le niveau de 1 (réglage d'origine) à 10 (le plus agressif). Le notch automatique est coupé en CW et en DATA (la coche n'est pas modifiée).
* **Réinitialisation automatique WEBSDR** (SET) : si le son est perdu pendant 30 secondes, la page du WebSDR est rechargée, sauf si le volume est à zéro ou coupé (volume du téléphone, Muet, curseur de volume ou bouton mute de la page). Trois essais de suite au plus par WebSDR.
* **Gestion des favoris** : loupe de recherche sur la première ligne (liste HF), touche ▲▲ qui monte le favori sélectionné tout en haut de sa liste, et couleur des lignes après « Vérifier les favoris » : vert = répond, orange = sans réponse depuis une semaine, rouge = depuis un mois (la couleur est conservée à la réouverture ; un favori qui répond de nouveau repasse au vert).
* **USB/LSB** : largeur de bande 2,6 kHz ajoutée, pas de 1 Hz ajouté. Le nom de l'application, la version et « @F5PBG 2026 » s'affichent en tout petit sous la fréquence quand la place le permet.
* **Barre de réglage en bas** : un appui de 3 secondes sur la barre ouvre un menu Haut / Bas ; « Bas » la place tout en bas de l'écran (INIT, + et − remontent pour ne pas la toucher).
* **Boutons INIT, + et − à gauche ou à droite** : un appui de 3 secondes sur + ou − ouvre un menu Gauche / Droite (droite = position d'origine), utile quand ils masquent des commandes du WebSDR.
* **Mémoires** : loupe de recherche à droite du nom de la liste, et une case (bouton arrondi) par mémoire.
* **Couleurs de l'écran** (SET › Couleurs…, fenêtre à part ; exportées et importées avec les réglages) : choix par palette (ou couleur personnalisée en RVB, 0 à 255) du fond général, de la couleur de fond et du texte des boutons, du texte et du fond de la fréquence, de l'avant-plan et du fond de la réglette. Un texte ne peut pas prendre la couleur de son fond (la couleur est refusée) ; « Couleurs par défaut » remet tout comme à l'installation.
* **Boutons de l'oreillette ou du casque** (SET › « Boutons du casque (arrêt / départ du scan) », décochée par défaut) : un appui simple arrête le scan, ou le lance s'il est arrêté ; après un arrêt par le casque, un **double appui recule d'un pas de scan** (répétable, utile si l'on a arrêté trop tard), puis un appui simple reprend. Oreillettes et casques Bluetooth ou filaires (essayé avec un Sony WH-1000XM4) ; pas de micro, rien ne tourne tant qu'on n'appuie pas ; décochée, les boutons du casque sont ignorés.
* **Appui long sur Verrou** : quitter l'application (avec confirmation ; un REC en cours est sauvegardé).
## Reprendre ses réglages de SDRLink (PC) dans PhoneSDRLink

Les réglages de SDRLink pour PC s'importent dans PhoneSDRLink (même format de fichier `settings.ini`) : favoris WebSDR et listes de favoris, mémoires de fréquences, profils de scan, zones non scannées, langue, mode sombre, nom donné aux WebSDR.

1. Dans **SDRLink sur PC** : exporter tous les réglages vers un fichier `.ini` (le fichier de travail est aussi `%APPDATA%\SDRLink\settings.ini`).
2. Copier ce fichier sur le téléphone (câble USB, Drive, e-mail… ; par exemple dans « Téléchargements »).
3. Dans **PhoneSDRLink** : **SET › Importer des réglages…**, choisir le fichier (dans le sélecteur de fichiers : menu en haut à gauche › Télécharger).
4. Cocher ce qu'il faut importer :
   * **Configuration** (affichage, langue, nom…) ;
   * **WebSDR** (favoris et listes de favoris, avec leurs noms) ;
   * **Mémoires de fréquences** ;
   * **Scan** (réglage, zones non scannées, profils de scan nommés).
5. Choisir : **garder les éléments déjà dans l'application et ajouter ceux du fichier** (sans doublons : même adresse dans la même liste, même nom de profil) ou **les supprimer et les remplacer** par ceux du fichier.

Les catégories absentes du fichier sont grisées. Dans l'autre sens, **SET › Exporter tous les réglages…** crée un fichier `PhoneSDRLink-settings.ini` qui sert de sauvegarde (à réimporter dans PhoneSDRLink après une réinstallation ou sur un autre téléphone). Essayé avec un vrai fichier de SDRLink pour PC : 166 favoris, 5 listes, 3 mémoires et 2 profils de scan repris correctement.
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

**Features**: favorite lists, automatic search of the public directories, frequency keypad (1.8–440 MHz), modes, swipe tuning bar (1 Hz–10 kHz steps, flick it like a wheel; 3-second press: move it to the bottom of the screen), long-press on the mode for the signal bandwidth (2.1 to 5 kHz or any value), frequency/memory scan with named profiles, **REC to MP3** (`Music/PhoneSDRLink`, long-press opens the folder), mute (long-press: sleep timer that cuts the sound and closes the app, after the recording if one is running), lock, INIT (restart the WebSDR), page zoom, automatic spectrum zoom (3 kHz ≈ 3 mm), auto notch, noise reducer, keep-screen-on, 23 languages, settings import/export (compatible with SDRLink for PC), dark mode, update check. The page volume follows the phone volume keys; sound is muted during a phone call; portrait/landscape follow the phone. Listening continues smoothly with the screen off (foreground service + notification; on aggressive-battery phones set the app's battery to "Unrestricted"), with a large audio buffer so that recording does not make it stutter.

**Install** (Android 8+): download `PhoneSDRLink-x.y.apk` from the [latest release](https://github.com/F5pbgLudovic/PhoneSDRLink/releases/latest), open it, allow "install unknown apps" for the app you opened it from, tap Install. Update by installing the new APK over the old one.

**Antivirus / Play Protect warnings**: an APK from outside the Play Store signed by an unknown author is often flagged "suspicious" (heuristic, based on rarity). The app only needs Internet, network state and audio settings. In Play Protect tap **More details › Install anyway**; in AVG/Avast/Norton choose **Cancel/Ignore** (not Uninstall) and add PhoneSDRLink to the antivirus exceptions. The SHA-256 of each APK is in the release notes, and the full source is in this repository, so you can also build it yourself (see *Compiler / Build*).

**Long presses and gestures**: long-press on the mode = signal bandwidth (2.1, 2.4, 2.8, 2.9, 3, 4 kHz or any value from 0.3 to 12 kHz; set inside the WebSDR itself on websdr.org, KiwiSDR, OpenWebRX, UberSDR and PhantomSDR; SSB edge 300 Hz from the carrier, AM/FM/CW centred). Long-press on Mute = sleep timer (15, 30, 45, 60 minutes or any duration): the sound is cut, then the app closes; if a recording is running the app asks when to stop it (possibly after the sound is cut, since REC records the sound before muting) and closes only once the MP3 is saved. The tuning bar keeps spinning like a wheel after a quick flick and stops when you touch it. In the favorite manager, *Listen to selection* keeps the window open while the WebSDR plays behind it.

**Also in 1.5**: the lock now also disables INIT, + and −; recording continues when you change WebSDR or press INIT (same MP3 file, a 2-3 s gap); INIT reloads the WebSDR and restores the original display including the zoom, while a frequency change never alters your zoom; a magnifier in the WEBSDR window filters the current list; spaces in the name given to WebSDRs become the invisible Alt+255 character; *Check for updates* stays in SET and, with *Check for updates at startup* ticked, an alert offers to download and install the new version from GitHub (Android asks for confirmation); new Phone/SDR/Link icon.

**Step and bandwidth per mode**: tuning-bar steps are now 10 Hz, 50 Hz, 100 Hz, 250 Hz, 500 Hz, 1 kHz and 10 kHz. CW starts at 10 Hz, USB/LSB at 1 kHz; default bandwidths are 300 Hz (CW), 2.9 kHz (USB/LSB), 6 kHz (AM) and 8 kHz (FM). The step or bandwidth you pick for a mode becomes that mode's default on every WebSDR and across restarts (USB and LSB share them). A full reset in *Reset the program* restores the original values.

**Version 1.7**: **10 levels for the automatic notch and the noise reducer** (small drop-down in SET, level 1 = original behaviour, 10 = most aggressive; the automatic notch is switched off in CW and DATA); **automatic WebSDR reset** (SET): the page is reloaded after 30 s without sound unless the volume is zero or muted; favorites management gets a search loupe on the first (HF) line, a **▲▲** button (move to the top of the list) and **colours after *Check favorites***: green = answers, orange = no answer for a week, red = for a month (kept across restarts, back to green as soon as it answers again); USB/LSB gain a **2.6 kHz** bandwidth and a **1 Hz** step; a tiny "PhoneSDRLink V1.7 @F5PBG 2026" is drawn under the frequency when it fits; a 3-second press on the tuning bar offers **Top / Bottom** placement (bottom of the screen, INIT/+/- move up); a 3-second press on + or - offers **Left / Right** for the INIT, + and - buttons; the Memories window gets a search loupe and one rounded box per memory; **colours** (SET): palette for the button background/text, frequency text/background and tuning-bar foreground/background (a text can never share its background colour), plus a *Default colours* button.

**Version 1.8**: **headset / earphone buttons** (SET, tick *Headset buttons (stop / start the scan)*, off by default): one press stops the scan, or starts it if it is stopped; after a stop from the headset, a **double press goes back one scan step** (repeatable, handy if you stopped too late), then one press resumes the scan from there; works with Bluetooth or wired earphones and headsets (tested with a Sony WH-1000XM4), no microphone, nothing runs until you press, unticked = the headset buttons are ignored. **Long press on Lock = quit the app** (with confirmation, a recording in progress is saved).

**Importing your SDRLink (PC) settings**: SDRLink for PC settings import into PhoneSDRLink (same `settings.ini` format): favorite WebSDRs and favorite lists, frequency memories, scan profiles and skipped ranges, language, dark mode, WebSDR nickname. In SDRLink for PC export all settings to an `.ini` file (the working file is also `%APPDATA%\SDRLink\settings.ini`), copy it to the phone, then in PhoneSDRLink use **SET › Import settings…**, tick what to import (Configuration, WebSDR, Memories, Scan) and choose to keep the existing items and add the file's (no duplicates) or to replace them. **SET › Export all settings…** writes a `PhoneSDRLink-settings.ini` backup that can be re-imported the same way.
73 de F5PBG
