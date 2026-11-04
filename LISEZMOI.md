# PhoneSDRLink (F5PBG)

Application Android (APK) qui reprend les fonctions de réception de SDRLink, sans la télécommande du poste.
Compatible Android 8 et plus (testé visé : OnePlus Nord et plus récents).

* Sources : `app\src\main` (Java, sans bibliothèque sauf androidx.webkit). Pont JavaScript vers les WebSDR : `assets\bridge.js` (dérivé de SDRLink).
* Compiler : `.\build.ps1` → `dist\PhoneSDRLink-<version>.apk` (chaîne JDK 17 + Gradle + SDK Android installée dans `toolchain\`, non versionnée).
* Signature : `phonesdrlink.jks` (mot de passe `phonesdrlink`) — **à conserver** : sans cette clé, une nouvelle version ne pourra plus s'installer par-dessus l'ancienne.
* Langues : `assets\lang\xx.txt` (anglais → traduction, même format que SDRLink), 23 langues.
* Réglages : fichier `settings.ini` de l'application, même format que SDRLink PC (un export de SDRLink PC peut être importé).
