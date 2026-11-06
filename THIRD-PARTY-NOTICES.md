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
