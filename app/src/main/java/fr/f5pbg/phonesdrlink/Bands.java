package fr.f5pbg.phonesdrlink;

// Bandes radioamateurs (limites selon la région IARU de l'opérateur) et mode usuel de chaque bande : LSB sous 10 MHz (160, 80, 40 m),
// USB sur 60 m et à partir de 30 m. Le mode choisi par l'opérateur dans une bande est retenu (Store.bandModes) pour les fois suivantes.
final class Bands {
    // nom, limite basse et haute en Hz pour les régions 1, 2 et 3 ; mode par défaut
    static final Object[][] TABLE = {
        { "160", new long[] { 1810000, 2000000 }, new long[] { 1800000, 2000000 }, new long[] { 1800000, 2000000 }, "LSB" },
        { "80", new long[] { 3500000, 3800000 }, new long[] { 3500000, 4000000 }, new long[] { 3500000, 3900000 }, "LSB" },
        { "60", new long[] { 5250000, 5450000 }, new long[] { 5250000, 5450000 }, new long[] { 5250000, 5450000 }, "USB" },
        { "40", new long[] { 7000000, 7200000 }, new long[] { 7000000, 7300000 }, new long[] { 7000000, 7200000 }, "LSB" },
        { "30", new long[] { 10100000, 10150000 }, new long[] { 10100000, 10150000 }, new long[] { 10100000, 10150000 }, "USB" },
        { "20", new long[] { 14000000, 14350000 }, new long[] { 14000000, 14350000 }, new long[] { 14000000, 14350000 }, "USB" },
        { "17", new long[] { 18068000, 18168000 }, new long[] { 18068000, 18168000 }, new long[] { 18068000, 18168000 }, "USB" },
        { "15", new long[] { 21000000, 21450000 }, new long[] { 21000000, 21450000 }, new long[] { 21000000, 21450000 }, "USB" },
        { "12", new long[] { 24890000, 24990000 }, new long[] { 24890000, 24990000 }, new long[] { 24890000, 24990000 }, "USB" },
        { "10", new long[] { 28000000, 29700000 }, new long[] { 28000000, 29700000 }, new long[] { 28000000, 29700000 }, "USB" },
        { "6", new long[] { 50000000, 52000000 }, new long[] { 50000000, 54000000 }, new long[] { 50000000, 54000000 }, "USB" },
        // Bande étendue du 27 MHz (26,000 à 28,000 MHz) : placée après le 10 m, donc 28,000 MHz reste au 10 m ; at() la renvoie de 26,000 à 27,999 MHz.
        { "27", new long[] { 26000000, 28000000 }, new long[] { 26000000, 28000000 }, new long[] { 26000000, 28000000 }, "AM" },
    };

    static int region = 0;   // 1, 2 ou 3 ; 0 = pas encore déterminée

    // Région IARU : d'après la position de l'appareil si elle est connue (non enregistrée), sinon d'après le pays de la langue du téléphone.
    static int region() {
        if (MyPos.has()) {
            double lat = MyPos.lat, lon = MyPos.lon;
            if (lon < -30 && lon > -180) return 2;
            if (lon >= 63 && lat < 52) return 3;
            if (lon >= 100 && lat < 60) return 3;
            return 1;
        }
        if (region != 0) return region;
        String c = java.util.Locale.getDefault().getCountry();
        String r2 = " US CA MX BR AR CL CO PE VE EC UY PY BO CU DO GT HN SV NI CR PA PR JM TT BS BB ";
        String r3 = " JP CN IN AU NZ KR KP TW HK SG MY TH VN ID PH PK BD LK NP MM KH LA MN FJ PG ";
        region = r2.contains(" " + c + " ") ? 2 : r3.contains(" " + c + " ") ? 3 : 1;
        return region;
    }

    // Indice de la bande qui contient hz, -1 si aucune (hors bandes radioamateurs : le mode n'est pas touché).
    static int at(long hz) {
        int r = region();
        for (int i = 0; i < TABLE.length; i++) {
            long[] e = (long[]) TABLE[i][r];
            if (hz >= e[0] && hz <= e[1]) return i;
        }
        return -1;
    }

    static String name(int i) { return (String) TABLE[i][0]; }
    static String defMode(int i) { return (String) TABLE[i][4]; }
    static long lo(int i) { return ((long[]) TABLE[i][region()])[0]; }
    static long hi(int i) { return ((long[]) TABLE[i][region()])[1]; }
}
