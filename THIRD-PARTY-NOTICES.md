# Composants de tiers / Third-party components

PhoneSDRLink 1.9 embarque du code natif (dossier `app/src/main/cpp`) :

| Composant | Rôle | Licence | Dossier |
|---|---|---|---|
| **ft8_lib** (Kārlis Goba) | décodage FT8 | MIT | `ft8lib/` (LICENSE) |
| **radae_nopy** (David Rowe, Peter B Marks, FreeDV) | réception FreeDV RADE V1 (modem et réseau de neurones, poids intégrés) | BSD 2 clauses | `rade/` (LICENSE) |
| **Opus** (Xiph.Org, dnn et celt) | réseaux de neurones (nnet) et vocodeur FARGAN, modèles du même projet | BSD 3 clauses | `opus/` (COPYING) |

Sources : https://github.com/kgoba/ft8_lib · https://github.com/drowe67/radae_nopy (puis les trois fichiers rade_rx.c, rade_bpf.c et rade_ofdm.c de https://github.com/freedv/rade_c, branche main au 6 nov. 2026, version utilisée par FreeDV 2.4.1) · https://github.com/xiph/opus (commit 940d4e5, modèles `opus_data-4ed9445b…`).
Les fichiers `rade/rade_dec_data.c` et `opus/dnn/fargan_data.c` sont les poids des réseaux de neurones (environ 24 Mo de texte chacun).

RADE V2 (FreeDV 3.0, en développement) : dossier rade2/ = fichiers de https://github.com/freedv/rade_c (David Rowe, FreeDV), branche main au 6 novembre 2026, licence BSD 2 clauses (rade2/LICENSE) : rade_v2_ofdm, rade_rx_v2, rade_dec_v2 (+ poids rade_dec_v2_data.c), rade_sync (+ poids rade_sync_data.c), constantes. Réseaux de neurones : Opus (même commit 940d4e5), avec MAX_CONV_INPUTS_ALL porté de 1536 à 2048 dans opus/dnn/nnet.c.

JS8Call (décodeur JS8, 1.12) : le décodeur est écrit pour PhoneSDRLink (Js8Engine.java, Js8Text.java), mais il utilise des données et des définitions de **JS8Call** (Jordan Sherer KN4CRD et contributeurs ; https://github.com/JS8Call-improved/JS8Call-improved ; licence **GNU GPL v3**) : matrices du code LDPC (174,87), polynôme et valeur finale du CRC de 12 bits, suites de synchro de Costas, tables de Varicode et de codage Huffman, et le dictionnaire de compression JSC (262 144 mots, `app/src/main/assets/js8jsc.bin`, 1,9 Mo, extrait de JSC_map.cpp avec `tools/js8/GenJsc.java`). Ces éléments sont sous GPL v3 : voir https://www.gnu.org/licenses/gpl-3.0.html. Enregistrements d'essai : fichiers `media/tests` du même dépôt.

FT2 (décodeur FT2, 1.12) : aucun code de Decodium ni de WSJT-X n'est repris. Le décodeur réutilise le moteur FT4 de ft8_lib (MIT) avec des symboles de 24 ms, d'après les paramètres publiés (https://www.ft2.it, manuel de Decodium) et les travaux de rétro-ingénierie de SP5TLS (https://github.com/kosciej/wsjtx-ft2libre, GPL v3 ; ses fichiers d'essai research/ft2_signal.wav et ft2_capture*.wav n'ont servi que de test et ne sont pas inclus).

Licence de PhoneSDRLink (1.12) : GNU GPL v3 (fichier LICENSE à la racine), pour rester conforme à la licence de JS8Call.

WSPR et JT9 : décodeurs écrits pour PhoneSDRLink d'après les descriptions publiées des protocoles (K1JT et al., WSJT-X, GPL v3), validés avec des enregistrements de WSJT-X ; leur code est compatible avec la GPL v3.

ft8_lib (MIT) a été légèrement modifié pour FT2 (durée de symbole et de tranche réglables, plage de recherche en temps) : la mention de licence MIT d'origine (app/src/main/cpp/ft8lib/LICENSE) est conservée. lamejs (LGPL-3.0), radae_nopy et rade_c (BSD) et Opus (BSD) sont compatibles avec la GPL v3.

Olivia et Contestia : le décodeur utilise la bibliothèque MFSK de Pawel Jalocha (app/src/main/cpp/jalocha/, reprise telle quelle de fldigi, W1HKJ et contributeurs, GNU GPL), compatible avec la GPL v3 ; Hell : paramètres de modulation (cadence des colonnes, largeurs de filtre, écarts de tonalité) repris des sources de fldigi (feld.cxx, GPL v3).

Icône « partage » (app/src/main/res/drawable/ic_share.xml) : icône « share » des Material Icons de Google, licence Apache 2.0 (https://github.com/google/material-design-icons).

Commande vocale du scan (1.26) : reconnaissance vocale hors ligne avec **Vosk** (Alpha Cephei, https://github.com/alphacep/vosk-api, bibliothèque `com.alphacephei:vosk-android` 0.3.47, licence Apache 2.0) et **JNA** (`net.java.dev.jna:jna` 5.13.0, Apache 2.0 / LGPL 2.1), embarqués dans l'APK. Les modèles de langue (« vosk-model-small-xx », Apache 2.0 d'après le site de Vosk) ne sont **pas** dans l'APK : ils sont téléchargés une fois, à la demande de l'utilisateur, depuis https://alphacephei.com/vosk/models dans le dossier privé de l'application. / Voice command (1.26): offline speech recognition with Vosk (Apache 2.0) and JNA (Apache 2.0 / LGPL 2.1) bundled in the APK; the small language models are downloaded once, on request, from alphacephei.com into the app's private folder.

Le texte complet de la GPL v3 est dans le fichier LICENSE à la racine du dépôt.
