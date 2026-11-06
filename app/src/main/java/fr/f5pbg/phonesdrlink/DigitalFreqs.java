package fr.f5pbg.phonesdrlink;

import java.util.ArrayList;

// Liste automatique « Modes numériques » des mémoires : les fréquences habituelles (porteuse / affichage du récepteur, en kHz) du mode
// du décodeur, bandes de 1,8 à 440 MHz, selon la région IARU (1 Europe-Afrique, 2 Amériques, 3 Asie-Pacifique).
// Sources : table des fréquences par défaut de WSJT-X (FT8, FT4, JT9, WSPR), plans de bande IARU et usages courants (PSK31, RTTY, SSTV, CW QRP,
// FreeDV). Ce sont des fréquences d'usage : elles peuvent changer, et ne remplacent pas le plan de bande de chaque pays.
final class DigitalFreqs {
    private DigitalFreqs() { }

    // Une ligne : { mode du décodeur, kHz, mode du récepteur, régions ("a" = toutes, sinon chiffres), remarque }
    private static final Object[][] T = {
        // FT8 (WSJT-X)
        { MainActivity.DEC_FT8, 1840.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 3573.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 5357.0, "USB", "a", "" },
        { MainActivity.DEC_FT8, 7074.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 10136.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 14074.0, "USB", "a", "" },
        { MainActivity.DEC_FT8, 18100.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 21074.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 24915.0, "USB", "a", "" },
        { MainActivity.DEC_FT8, 28074.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 50313.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 50323.0, "USB", "a", "" },
        { MainActivity.DEC_FT8, 70154.0, "USB", "1", "" }, { MainActivity.DEC_FT8, 144174.0, "USB", "a", "" }, { MainActivity.DEC_FT8, 432174.0, "USB", "a", "" },
        // FT4
        { MainActivity.DEC_FT4, 3575.0, "USB", "a", "" }, { MainActivity.DEC_FT4, 3568.0, "USB", "3", "" }, { MainActivity.DEC_FT4, 7047.5, "USB", "a", "" },
        { MainActivity.DEC_FT4, 10140.0, "USB", "a", "" }, { MainActivity.DEC_FT4, 14080.0, "USB", "a", "" }, { MainActivity.DEC_FT4, 18104.0, "USB", "a", "" },
        { MainActivity.DEC_FT4, 21140.0, "USB", "a", "" }, { MainActivity.DEC_FT4, 24919.0, "USB", "a", "" }, { MainActivity.DEC_FT4, 28180.0, "USB", "a", "" },
        { MainActivity.DEC_FT4, 50318.0, "USB", "a", "" }, { MainActivity.DEC_FT4, 144170.0, "USB", "a", "" },
        // WSPR
        { MainActivity.DEC_WSPR, 1836.6, "USB", "a", "" }, { MainActivity.DEC_WSPR, 3568.6, "USB", "a", "" }, { MainActivity.DEC_WSPR, 7038.6, "USB", "a", "" },
        { MainActivity.DEC_WSPR, 10138.7, "USB", "a", "" }, { MainActivity.DEC_WSPR, 14095.6, "USB", "a", "" }, { MainActivity.DEC_WSPR, 18104.6, "USB", "a", "" },
        { MainActivity.DEC_WSPR, 21094.6, "USB", "a", "" }, { MainActivity.DEC_WSPR, 24924.6, "USB", "a", "" }, { MainActivity.DEC_WSPR, 28124.6, "USB", "a", "" },
        { MainActivity.DEC_WSPR, 50293.0, "USB", "a", "" }, { MainActivity.DEC_WSPR, 70091.0, "USB", "1", "" }, { MainActivity.DEC_WSPR, 144489.0, "USB", "a", "" },
        { MainActivity.DEC_WSPR, 432300.0, "USB", "a", "" },
        // JT9
        { MainActivity.DEC_JT9, 1839.0, "USB", "a", "" }, { MainActivity.DEC_JT9, 3572.0, "USB", "a", "" }, { MainActivity.DEC_JT9, 7078.0, "USB", "a", "" },
        { MainActivity.DEC_JT9, 10140.0, "USB", "a", "" }, { MainActivity.DEC_JT9, 14078.0, "USB", "a", "" }, { MainActivity.DEC_JT9, 18104.0, "USB", "a", "" },
        { MainActivity.DEC_JT9, 21078.0, "USB", "a", "" }, { MainActivity.DEC_JT9, 24919.0, "USB", "a", "" }, { MainActivity.DEC_JT9, 28078.0, "USB", "a", "" },
        { MainActivity.DEC_JT9, 50312.0, "USB", "a", "" }, { MainActivity.DEC_JT9, 70104.0, "USB", "1", "" },
        // PSK31 (fréquence « .15 » d'usage)
        { MainActivity.DEC_PSK, 1838.15, "USB", "a", "" }, { MainActivity.DEC_PSK, 3580.15, "USB", "a", "" }, { MainActivity.DEC_PSK, 7035.15, "USB", "13", "" },
        { MainActivity.DEC_PSK, 7080.15, "USB", "2", "" }, { MainActivity.DEC_PSK, 10142.15, "USB", "a", "" }, { MainActivity.DEC_PSK, 14070.15, "USB", "a", "" },
        { MainActivity.DEC_PSK, 18100.15, "USB", "a", "" }, { MainActivity.DEC_PSK, 21080.15, "USB", "a", "" }, { MainActivity.DEC_PSK, 24920.15, "USB", "a", "" },
        { MainActivity.DEC_PSK, 28120.15, "USB", "a", "" }, { MainActivity.DEC_PSK, 50290.15, "USB", "a", "" },
        // RTTY (début des segments numériques usuels)
        { MainActivity.DEC_RTTY, 3590.0, "USB", "a", "" }, { MainActivity.DEC_RTTY, 7040.0, "USB", "13", "" }, { MainActivity.DEC_RTTY, 7080.0, "USB", "2", "" },
        { MainActivity.DEC_RTTY, 10140.0, "USB", "a", "" }, { MainActivity.DEC_RTTY, 14080.0, "USB", "a", "" }, { MainActivity.DEC_RTTY, 18105.0, "USB", "a", "" },
        { MainActivity.DEC_RTTY, 21080.0, "USB", "a", "" }, { MainActivity.DEC_RTTY, 24920.0, "USB", "a", "" }, { MainActivity.DEC_RTTY, 28080.0, "USB", "a", "" },
        // SSTV
        { MainActivity.DEC_SSTV, 3730.0, "LSB", "13", "" }, { MainActivity.DEC_SSTV, 3845.0, "LSB", "2", "" }, { MainActivity.DEC_SSTV, 7033.0, "LSB", "13", "" },
        { MainActivity.DEC_SSTV, 7171.0, "LSB", "2", "" }, { MainActivity.DEC_SSTV, 14230.0, "USB", "a", "" }, { MainActivity.DEC_SSTV, 21340.0, "USB", "a", "" },
        { MainActivity.DEC_SSTV, 28680.0, "USB", "a", "" }, { MainActivity.DEC_SSTV, 144500.0, "FM", "1", "" },
        // CW : fréquences d'appel / d'activité QRP
        { MainActivity.DEC_CW, 1836.0, "CW", "a", "QRP" }, { MainActivity.DEC_CW, 3560.0, "CW", "a", "QRP" }, { MainActivity.DEC_CW, 7030.0, "CW", "a", "QRP" },
        { MainActivity.DEC_CW, 10106.0, "CW", "a", "QRP" }, { MainActivity.DEC_CW, 14060.0, "CW", "a", "QRP" }, { MainActivity.DEC_CW, 18086.0, "CW", "a", "QRP" },
        { MainActivity.DEC_CW, 21060.0, "CW", "a", "QRP" }, { MainActivity.DEC_CW, 24906.0, "CW", "a", "QRP" }, { MainActivity.DEC_CW, 28060.0, "CW", "a", "QRP" },
        { MainActivity.DEC_CW, 50090.0, "CW", "a", "" }, { MainActivity.DEC_CW, 70050.0, "CW", "1", "" }, { MainActivity.DEC_CW, 144050.0, "CW", "a", "" },
        { MainActivity.DEC_CW, 222050.0, "CW", "2", "" }, { MainActivity.DEC_CW, 432050.0, "CW", "a", "" },
        // FreeDV (RADE : mêmes fréquences)
        { MainActivity.DEC_RADE, 1997.0, "LSB", "2", "" }, { MainActivity.DEC_RADE, 3625.0, "LSB", "13", "" }, { MainActivity.DEC_RADE, 3850.0, "LSB", "2", "" },
        { MainActivity.DEC_RADE, 7177.0, "LSB", "a", "" }, { MainActivity.DEC_RADE, 7197.0, "LSB", "2", "" }, { MainActivity.DEC_RADE, 14236.0, "USB", "a", "" },
        { MainActivity.DEC_RADE, 18118.0, "USB", "a", "" }, { MainActivity.DEC_RADE, 21313.0, "USB", "a", "" }, { MainActivity.DEC_RADE, 24933.0, "USB", "a", "" },
        { MainActivity.DEC_RADE, 28330.0, "USB", "a", "" },
    };

    static String band(double khz) {
        if (khz < 2000) return "160 m";
        if (khz < 4000) return "80 m";
        if (khz < 5500) return "60 m";
        if (khz < 7500) return "40 m";
        if (khz < 10200) return "30 m";
        if (khz < 14500) return "20 m";
        if (khz < 18200) return "17 m";
        if (khz < 21500) return "15 m";
        if (khz < 25000) return "12 m";
        if (khz < 30000) return "10 m";
        if (khz < 55000) return "6 m";
        if (khz < 80000) return "4 m";
        if (khz < 150000) return "2 m";
        if (khz < 250000) return "1.25 m";
        return "70 cm";
    }

    /** Fréquences du mode de décodeur (DEC_*) pour la région IARU 1, 2 ou 3, dans l'ordre croissant. Mémoires fictives (hors liste enregistrée). */
    static ArrayList<Memory> list(int decMode, int region) {
        ArrayList<Memory> out = new ArrayList<Memory>();
        String r = String.valueOf(region);
        String name = MainActivity.decName(decMode);
        if (decMode == MainActivity.DEC_RADE2) decMode = MainActivity.DEC_RADE;   // mêmes fréquences d'appel pour la V1 et la V2
        for (Object[] row : T) {
            if ((Integer) row[0] != decMode) continue;
            String reg = (String) row[3];
            if (!reg.equals("a") && !reg.contains(r)) continue;
            double khz = (Double) row[1];
            String note = (String) row[4];
            String label = name + " " + band(khz) + (note.isEmpty() ? "" : " " + note) + (reg.equals("a") ? "" : " (" + regs(reg) + ")");
            Memory m = new Memory(label, Math.round(khz * 1000), (String) row[2]);
            out.add(m);
        }
        return out;
    }

    private static String regs(String reg) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < reg.length(); i++) sb.append(i > 0 ? "/" : "").append("R").append(reg.charAt(i));
        return sb.toString();
    }

    static String regionName(int region) {
        return region == 2 ? Lang.T("Région IARU 2 (Amériques)", "IARU Region 2 (Americas)")
            : region == 3 ? Lang.T("Région IARU 3 (Asie-Pacifique)", "IARU Region 3 (Asia-Pacific)")
            : Lang.T("Région IARU 1 (Europe, Afrique)", "IARU Region 1 (Europe, Africa)");
    }
}
