# Composants de tiers / Third-party components

PhoneSDRLink 1.9 embarque du code natif (dossier `app/src/main/cpp`) :

| Composant | Rôle | Licence | Dossier |
|---|---|---|---|
| **ft8_lib** (Kārlis Goba) | décodage FT8 | MIT | `ft8lib/` (LICENSE) |
| **radae_nopy** (David Rowe, Peter B Marks, FreeDV) | réception FreeDV RADE V1 (modem et réseau de neurones, poids intégrés) | BSD 2 clauses | `rade/` (LICENSE) |
| **Opus** (Xiph.Org, dnn et celt) | réseaux de neurones (nnet) et vocodeur FARGAN, modèles du même projet | BSD 3 clauses | `opus/` (COPYING) |

Sources : https://github.com/kgoba/ft8_lib · https://github.com/drowe67/radae_nopy · https://github.com/xiph/opus (commit 940d4e5, modèles `opus_data-4ed9445b…`).
Les fichiers `rade/rade_dec_data.c` et `opus/dnn/fargan_data.c` sont les poids des réseaux de neurones (environ 24 Mo de texte chacun).