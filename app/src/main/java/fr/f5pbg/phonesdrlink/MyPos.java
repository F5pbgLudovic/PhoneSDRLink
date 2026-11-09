package fr.f5pbg.phonesdrlink;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

// Position approximative de l'appareil (permission « position approximative », demandée une fois par lancement), pour la recherche de WebSDR.
// Sans permission, sans position récente ou position désactivée : has() reste faux et la recherche garde le centre de la France.
// La position n'est ni enregistrée ni envoyée : elle ne sert qu'à calculer les distances sur l'appareil.
final class MyPos {
    static final String PERM = "android.permission.ACCESS_COARSE_LOCATION";
    static double lat = Double.NaN, lon = Double.NaN;
    static Runnable onChange;
    static boolean asked;

    static boolean has() { return !Double.isNaN(lat) && !Double.isNaN(lon); }

    static boolean granted(MainActivity a) { return a.checkSelfPermission(PERM) == android.content.pm.PackageManager.PERMISSION_GRANTED; }

    // Appelé à l'ouverture de la recherche : onChange est exécuté (fil de l'interface) quand une position est connue.
    static void ensure(MainActivity a, Runnable change) {
        onChange = change;
        if (granted(a)) { locate(a); return; }
        if (!asked) { asked = true; a.requestPermissions(new String[] { PERM }, 8); }
    }

    static void afterPermission(MainActivity a) { if (granted(a)) locate(a); }

    private static void locate(MainActivity a) {
        LocationManager lm = (LocationManager) a.getSystemService(android.content.Context.LOCATION_SERVICE);
        if (lm == null) return;
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException e) { }   // le fournisseur GPS exige la position précise : ignoré
            }
        } catch (Exception e) { }
        if (best != null) { set(best); return; }
        try {   // aucune position récente : une mesure unique
            String p = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
            if (p != null) lm.requestSingleUpdate(p, new LocationListener() {
                public void onLocationChanged(Location l) { set(l); }
                public void onStatusChanged(String s, int i, Bundle b) { }
                public void onProviderEnabled(String s) { }
                public void onProviderDisabled(String s) { }
            }, Looper.getMainLooper());
        } catch (Exception e) { }
    }

    private static void set(Location l) {
        lat = l.getLatitude(); lon = l.getLongitude();
        if (onChange != null) onChange.run();
    }
}
