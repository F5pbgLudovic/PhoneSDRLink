package fr.f5pbg.phonesdrlink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

// Service de premier plan : garde le processus, le processeur et le Wi-Fi actifs quand l'écran est éteint ou le téléphone verrouillé,
// pour que le son du WebSDR ne devienne pas saccadé.
public class PlayService extends Service {
    static final String CHANNEL = "phonesdrlink_play";
    PowerManager.WakeLock cpu;
    WifiManager.WifiLock wifi;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "PhoneSDRLink", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder nb = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        nb.setContentTitle("PhoneSDRLink").setContentText(Lang.T("Écoute du WebSDR en cours", "Listening to the WebSDR"))
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentIntent(pi).setOngoing(true);
        Notification n = nb.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(1, n);
        if (cpu == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PhoneSDRLink:play");
            cpu.setReferenceCounted(false);
            cpu.acquire();
        }
        if (wifi == null) {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "PhoneSDRLink:wifi");
                wifi.setReferenceCounted(false);
                wifi.acquire();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try { if (cpu != null && cpu.isHeld()) cpu.release(); } catch (Exception e) { }
        try { if (wifi != null && wifi.isHeld()) wifi.release(); } catch (Exception e) { }
        super.onDestroy();
    }
}
