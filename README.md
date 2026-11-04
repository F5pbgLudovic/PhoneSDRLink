# PhoneSDRLink

**Français** — Application Android (APK) du radioamateur F5PBG qui reprend les fonctions de réception de [SDRLink](https://github.com/F5pbgLudovic/SDRLink), sans la télécommande du poste : on écoute un WebSDR (websdr.org, KiwiSDR, OpenWebRX, UberSDR, PhantomSDR) depuis son smartphone.

* **WEBSDR** : favoris en listes, recherche automatique dans les annuaires publics (au premier démarrage, puis à la demande), adresse libre.
* **Fréquence** (1,8 à 440 MHz) : un appui ouvre un clavier ; **mode** USB / LSB / AM / FM / DATA / CW ; barre à glisser avec pas de 100 Hz à 1 kHz.
* **Scan** de fréquences ou de mémoires, **Conf scan** (zones non scannées, profils nommés), **Mémoires** en listes.
* **SET** : mode sombre, mise à jour, 23 langues, notch automatique (DNF), réducteur de bruit (DNR), pseudo donné aux WebSDR, import/export des réglages (compatible SDRLink PC), réinitialisation.
* Volume = touches du téléphone, bouton Muet, son coupé pendant un appel, boutons + / − sur la page, portrait / paysage automatiques.

Installation : télécharger l'APK dans les [releases](https://github.com/F5pbgLudovic/PhoneSDRLink/releases/latest) et l'ouvrir sur le téléphone (autoriser les « sources inconnues » pour le navigateur ou le gestionnaire de fichiers). Android 8 ou plus. Un antivirus peut signaler une application inconnue : c'est une APK hors Play Store, les sources sont ici.

**English** — Android app (APK) by radio amateur F5PBG, built on the receiving side of [SDRLink](https://github.com/F5pbgLudovic/SDRLink) without the rig control: listen to a WebSDR (websdr.org, KiwiSDR, OpenWebRX, UberSDR, PhantomSDR) from a smartphone. Favorite lists, automatic search of the public directories, frequency keypad (1.8–440 MHz), modes, swipe tuning bar (100 Hz–1 kHz steps), frequency/memory scan with profiles, auto notch, noise reducer, 23 languages, settings import/export (compatible with SDRLink for PC), dark mode, update check. Android 8+; install the APK from the releases page (allow unknown sources; an antivirus may warn about an APK from outside the Play Store).

## Compiler / Build

`tools\install-toolchain.ps1` (once: portable JDK 17 + Gradle + Android SDK in `toolchain\`, accepts the Android SDK licences), then `.\build.ps1` → `dist\PhoneSDRLink-<version>.apk`. The signing key `phonesdrlink.jks` is not published: without it the APK is built unsigned.

73 de F5PBG
