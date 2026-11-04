# PhoneSDRLink (F5PBG) — notes de projet

Application Android (APK) qui reprend les fonctions de réception de SDRLink, sans la télécommande du poste. Notice d'utilisation et d'installation : [README.md](README.md).

## Où est quoi

* Sources : `app\src\main\java\fr\f5pbg\phonesdrlink` (Java pur, un seul écran).
  * `MainActivity` : écran, WebView, état (fréquence, mode), scan, son (focus audio, appel, volume du téléphone), REC, verrou, zoom du spectre, mises à jour.
  * `Store` : tous les réglages (`settings.ini`, même format que SDRLink PC : un export de SDRLink PC s'importe : SET › Importer des réglages…, détail dans le README, essayé avec un vrai fichier le 4 nov. 2026 : 166 favoris, 5 listes, 3 mémoires, 2 profils), favoris, mémoires, profils de scan.
  * `PlayService` : service de premier plan (notification, verrou processeur + Wi-Fi) lancé au démarrage, arrêté à la fermeture : son fluide écran éteint. `MainActivity` n'appelle plus `web.onPause()` ni n'abandonne le focus audio à la mise en pause.
  * `Finder` : annuaires websdr.org / KiwiSDR / ReceiverBook. `Dlg*` : fenêtres (WEBSDR, favoris, recherche, mémoires, scan, SET, clavier). `CheckList` : liste à cases. `Ui`, `FreqBar`, `Lang`.
* `app\src\main\assets\bridge.js` : pont JavaScript injecté dans chaque page (dérivé de `WebSdrBridge.cs` de SDRLink) : détection du type de WebSDR, lecture/réglage fréquence et mode, démarrage automatique du son, chaîne audio (notch automatique, réducteur de bruit, gain), volume de la page, REC MP3, zoom du spectre, viewport 1000 px, désactivation de la reconnaissance vocale (qui faisait tomber UberSDR).
* `assets\lang\xx.txt` : 23 langues (anglais → traduction, comme SDRLink). `assets\default-favorites.txt` : favoris de départ (liste « HF »). `assets\lame.min.js` : encodeur MP3 lamejs (LGPL-3.0).
* Chaîne de compilation : `toolchain\` (non versionnée) ← `tools\install-toolchain.ps1`. Compiler : `.\build.ps1`.
* Signature : `phonesdrlink.jks` (mot de passe `phonesdrlink`), **non publiée** : à garder dans la sauvegarde ; sans elle une mise à jour ne s'installe plus par-dessus l'ancienne version.

## Règles de travail

* Demander à F5PBG avant d'ajouter une fonction de SDRLink qu'il n'a pas citée.
* Publier (GitHub, release) seulement sur demande.
* Tout nouveau texte : `Lang.T("français", "English")`, puis ajouter la ligne `English<TAB>traduction` aux 21 fichiers de `assets\lang` (attention aux espaces de fin de clé).
* Essais sur le vrai téléphone par adb (voir README, « Outils de mise au point ») ; contrôler le premier plan (`dumpsys window | grep mCurrentFocus`) avant tout `input tap`, F5PBG utilise parfois le téléphone en même temps.

## Historique

* **1.0** (4 nov. 2026) : première version.
* **1.1** (4 nov. 2026) : bouton Muet + Verrou, INIT, REC MP3 (+ appui long vers le dossier), liste de recherche et gestion des favoris refaites comme SDRLink, favoris HF par défaut, grandes cases dans la liste WEBSDR (position conservée), pas sur la ligne Scan, écran toujours allumé, zoom du spectre 1 kHz/mm, volume de la page recopié depuis le volume du téléphone, page 1000 px à zoom libre (panneaux OpenWebRX/Kiwi), plantage UberSDR corrigé (reconnaissance vocale), notch automatique qui coupait le son (NaN) corrigé, noms de favoris sur une ligne.

* **1.2** (4 nov. 2026) : son fluide écran éteint / téléphone verrouillé (PlayService, plus de `web.onPause()`, priorité du moteur de rendu). Bug corrigé : le son disparaissait au réveil, car `requestFocus()` créait une nouvelle demande à chaque `onResume` et l'ancienne recevait une perte de focus qui mettait `focusMute` à vrai ; la même demande est maintenant réutilisée. Mesures adb : service au premier plan, verrou `PhoneSDRLink:play`, pas de veille de la chaîne audio, volume interne de la page à 1 après chaque réveil. Testée à l'oreille par F5PBG : « ça a l'air ok », le DNF fonctionne parfaitement, effet du DNR discret.

* **1.3** (4 nov. 2026, publiée ; essayée à l'oreille par F5PBG : le son ne craque plus) : saccades écran éteint, surtout au lancement d'un REC. Cause : les WebSDR demandent la latence minimale (piste audio « rapide » de 8 ms) ; mesuré : 7 à 8 sous-alimentations de la sortie audio en 40 s écran éteint. Correction dans `bridge.js` : `AudioContext` enveloppé pour imposer `latencyHint:'playback'` (piste normale d'environ 80 ms) et tampon du ScriptProcessor du REC porté de 4096 à 16384. Mesuré après : 0 sous-alimentation en 40 s avec REC écran éteint.

* **1.4** (5 nov. 2026, publiée) : (1) appui long sur le mode : largeur de bande (2,1 / 2,4 / 2,8 / 2,9 / 3 / 4 kHz ou valeur libre 0,3-12 kHz), `bridge.js` `L.setBw` / `L.applyBw` : websdr.org `lo`/`hi` + `updbw()`, KiwiSDR `ext_set_passband`, OpenWebRX/OpenWebRX+ `demodulateur.setBandpass`, UberSDR `command('passband')`, PhantomSDR `spectrumAudio.setAudioRange` ; SSB bord à 300 Hz, AM/FM/CW centrée ; réappliquée après un changement de mode et au rechargement ; remise à zéro à l'ouverture d'un autre WebSDR. (2) appui long sur Muet : minuterie 15/30/45/60 min ou libre, le son est coupé (le REC enregistre le son brut, donc continue) puis `finishAndRemoveTask` ; avec un REC en cours : choix de l'heure d'arrêt du REC, fermeture à la fin du REC. (3) barre à glisser : effet de roue (VelocityTracker, friction exponentielle, un appui arrête). (4) Gestion des favoris : « Écouter la sélection » ne ferme plus la fenêtre. Toutes essayées par adb sur le OnePlus ; 23 textes traduits dans les 21 langues. Le build écrase dist\PhoneSDRLink-<version>.apk : toujours changer versionName avant de compiler.

## Points ouverts

* 1.4 : OpenWebRX classique (non « + ») non essayé pour la largeur de bande ; effet de roue réglé par adb seulement (friction 1,6 dans FreqBar), à ajuster si F5PBG le demande.
* Pas essayés sur le vrai téléphone : scan, mémoires, favoris (import/export, vérification), coupure pendant un appel, PhantomSDR (pas de zoom du spectre : aucune commande trouvée), mise à jour (aucune version plus récente à détecter).
* Le volume de la page et le volume du téléphone se multiplient (téléphone bas = son très bas) ; à ajuster si F5PBG le demande.
* Pas de licence sur le dépôt.
