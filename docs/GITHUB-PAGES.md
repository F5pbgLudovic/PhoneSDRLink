# Publier la page de partage avec GitHub Pages

Le bouton de partage de PhoneSDRLink (rond au-dessus de **INIT**) envoie un lien comme celui-ci :

```
https://f5pbgludovic.github.io/PhoneSDRLink/s/?u=http%3A%2F%2Fwebsdr.ewi.utwente.nl%3A8901%2F&f=7074000&m=USB&bw=2900&d=FT8
```

Ce lien mène à une petite page web (fichier `docs/s/index.html` du dépôt). Elle affiche l'écoute partagée et trois boutons :
**Ouvrir dans PhoneSDRLink**, **Écouter dans le navigateur**, **Installer PhoneSDRLink**. Pour que le lien marche, GitHub
doit publier le dossier `docs` du dépôt : c'est **GitHub Pages**, gratuit pour un dépôt public. Rien à installer, tout se
règle sur le site de GitHub, une fois pour toutes.

Il y a deux étapes : la première est **obligatoire** (sans elle, le lien donne une erreur 404), la seconde est
**facultative** (elle fait ouvrir l'application directement, sans passer par la page).

---

## Étape 1 (obligatoire) : publier la page

À faire **après la fusion** de la branche du partage dans `main` (le dossier `docs` doit être dans `main`).

1. Ouvrir https://github.com/F5pbgLudovic/PhoneSDRLink, connecté avec le compte **F5pbgLudovic**.
2. Onglet **Settings** (Paramètres, en haut à droite de la page du dépôt).
3. Menu de gauche : **Pages** (rubrique *Code and automation*).
4. Rubrique **Build and deployment** :
   - **Source** : *Deploy from a branch* ;
   - **Branch** : choisir `main`, puis le dossier **`/docs`** (et non `/ (root)`) ;
   - bouton **Save**.
5. Attendre une à deux minutes. L'onglet **Actions** du dépôt montre une tâche *pages build and deployment* : elle doit
   finir en vert. En haut de la page **Settings › Pages** s'affiche alors *Your site is live at
   https://f5pbgludovic.github.io/PhoneSDRLink/*.

**Vérifier** : ouvrir ce lien dans un navigateur (téléphone ou PC) :

https://f5pbgludovic.github.io/PhoneSDRLink/s/?u=http%3A%2F%2Fwebsdr.ewi.utwente.nl%3A8901%2F&f=7074000&m=USB

La page doit afficher **7074 kHz**, **USB**, *sur websdr.ewi.utwente.nl:8901* et les boutons. Sur le téléphone,
**Ouvrir dans PhoneSDRLink** lance l'application, qui demande « Ouvrir websdr.ewi.utwente.nl sur 7074 kHz USB ? ».

À partir de là, les liens partagés fonctionnent : cliquables dans toutes les messageries, ils ouvrent la page, et un appui
sur **Ouvrir dans PhoneSDRLink** ouvre l'application.

> Chaque utilisateur peut aussi, sur son téléphone, faire ouvrir ces liens directement par l'application, sans l'étape 2 :
> Paramètres Android › Applis › PhoneSDRLink › **Ouvrir par défaut** › **Ajouter des liens** › cocher
> `f5pbgludovic.github.io`.

---

## Étape 2 (facultative) : ouvrir l'application directement pour tout le monde

Android ouvre un lien https directement dans une application seulement si le site **prouve** que l'application lui
appartient : c'est le fichier `assetlinks.json` (« Android App Links »). Ce fichier doit être à la **racine** du domaine :

```
https://f5pbgludovic.github.io/.well-known/assetlinks.json
```

La page de l'étape 1 est publiée sous `/PhoneSDRLink/`, pas à la racine : il faut donc un **second dépôt**, le « site
personnel » GitHub, dont le nom est imposé.

1. Créer un dépôt **public** nommé exactement **`F5pbgLudovic.github.io`** (https://github.com/new ; cocher *Add a README
   file* pour qu'il ne soit pas vide).
2. Dans ce dépôt, ajouter deux fichiers (bouton **Add file › Create new file**) :
   - **`.nojekyll`** : fichier vide (sans lui, GitHub ne publie pas les dossiers dont le nom commence par un point) ;
   - **`.well-known/assetlinks.json`** (taper le nom avec le `/`, GitHub crée le dossier) avec ce contenu :

     ```json
     [
       {
         "relation": ["delegate_permission/common.handle_all_urls"],
         "target": {
           "namespace": "android_app",
           "package_name": "fr.f5pbg.phonesdrlink",
           "sha256_cert_fingerprints": [
             "71:81:B3:7A:99:06:FF:5A:78:99:DC:A0:A1:1B:E2:1B:A0:82:60:C9:4B:A9:4F:5F:F5:34:46:98:4D:27:82:4E"
           ]
         }
       }
     ]
     ```

     L'empreinte est celle de la clé `phonesdrlink.jks` qui signe les APK des releases (certificat
     `CN=PhoneSDRLink, O=F5PBG, C=FR`). Pour la recalculer :
     `keytool -list -v -keystore phonesdrlink.jks -storepass phonesdrlink` (ligne `SHA256:`).
3. Dans ce dépôt : **Settings › Pages › Source** *Deploy from a branch*, **Branch** `main` et **`/ (root)`**, **Save**.
4. Vérifier, après une à deux minutes, que
   https://f5pbgludovic.github.io/.well-known/assetlinks.json affiche bien le texte ci-dessus.
   Outil de Google pour contrôler :
   https://developers.google.com/digital-asset-links/tools/generator (domaine `f5pbgludovic.github.io`, package
   `fr.f5pbg.phonesdrlink`, empreinte ci-dessus, bouton *Test statement*).

Android vérifie le fichier à l'installation ou à la mise à jour de l'application. Pour forcer la vérification sur un
téléphone relié au PC (adb) :

```
adb shell pm verify-app-links --re-verify fr.f5pbg.phonesdrlink
adb shell pm get-app-links fr.f5pbg.phonesdrlink
```

La dernière commande doit afficher `f5pbgludovic.github.io: verified`. Les liens partagés ouvrent alors l'application
directement, sans passer par la page (la page reste utile pour ceux qui n'ont pas l'application).

Seuls les APK signés avec la clé des releases sont reconnus : un APK compilé sans `phonesdrlink.jks` (clé « debug »)
garde le passage par la page.

---

## Si l'adresse change un jour

Nom de compte, nom de dépôt ou domaine personnalisé : l'adresse de la page est écrite à trois endroits, à changer ensemble
puis publier une nouvelle version de l'application :

| Fichier | Quoi |
|---|---|
| `app/src/main/java/fr/f5pbg/phonesdrlink/ShareLink.java` | `WEB_BASE` (adresse de la page, avec `/s/` à la fin) |
| `app/src/main/AndroidManifest.xml` | `android:host` et `android:pathPrefix` de l'`intent-filter` https |
| `app/src/test/java/fr/f5pbg/phonesdrlink/ShareLinkTest.java` | adresses attendues par les tests |

Le bouton **Installer PhoneSDRLink** de la page mène à `APK_URL`, en haut du script de `docs/s/index.html`.

Les liens déjà envoyés avec l'ancienne adresse ne s'ouvriront plus dans l'application.

---

## En cas de problème

| Symptôme | Cause probable |
|---|---|
| Le lien donne **404** | Pages pas encore activé, ou branche / dossier mal choisis (doit être `main` et `/docs`), ou publication pas finie (onglet Actions). |
| La page affiche **« Lien de partage invalide »** | Lien coupé ou modifié (copie incomplète dans une messagerie) : repartager depuis l'application. |
| **Ouvrir dans PhoneSDRLink** mène à la page des releases | L'application n'est pas installée, ou c'est une version sans le partage. |
| Le lien s'ouvre toujours dans le navigateur | Normal sans l'étape 2 ; sinon vérifier `assetlinks.json` (étape 2, point 4) et que l'APK vient des releases. |
