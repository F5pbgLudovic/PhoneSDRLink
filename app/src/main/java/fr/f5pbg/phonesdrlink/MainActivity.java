package fr.f5pbg.phonesdrlink;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Écran unique de PHONESDRLINK : commandes en haut (ou à gauche en paysage), WebSDR en dessous (ou à droite).
public class MainActivity extends Activity {
    static final String[] MODES = { "USB", "LSB", "AM", "FM", "DATAU", "CW" };
    static final long TOLERANCE = 10;
    static final int SETTLE_MS = 3500, SCAN_HOLD_MS = 2000;
    static final String RELEASES_API = "https://api.github.com/repos/F5pbgLudovic/PhoneSDRLink/releases/latest";

    Store st;
    WebView web;
    LinearLayout root, controls;
    FrameLayout webFrame;
    LinearLayout right, barRow, zoomBox;
    Button btnWeb, btnSet, btnRec, btnMute, btnLock, btnMode, btnScan, btnScanConf, btnMem, btnStep;
    TextView tvFreq, tvStatus, tvPage;
    FreqBar bar;
    final Handler h = new Handler(Looper.getMainLooper());

    // État de réception
    long freq;
    String mode;
    String sdrKind = "";
    long sdrF;
    boolean muted, callMute, focusMute, locked;
    int crashes;
    // Enregistrement MP3
    boolean recording;
    long recStart;
    java.io.OutputStream recOut;
    android.net.Uri recUri;
    java.io.File recFile;
    String recName = "";
    boolean recPending;
    long crashSince;
    ScriptHandler bridgeHandle, identityHandle;
    long settleUntil, targetUntil, target, scanHoldUntil, scanHoldHz;
    String updateUrl;

    // Scan
    boolean scanning;
    ScanProfile scanRun;
    long scanHz;
    int scanIndex;
    java.util.ArrayList<Memory> scanMems = new java.util.ArrayList<Memory>();
    final Runnable scanTick = new Runnable() { public void run() { scanNext(); } };

    AudioManager audio;
    AudioFocusRequest focusReq;
    String bridgeScript = "";
    FileCb fileCb;

    interface FileCb { void done(Uri uri); }

    // ------------------------------------------------------------------ démarrage

    @Override
    protected void onCreate(Bundle b) {
        Store.init(this);
        st = Store.I;
        Lang.set(this, st.language);
        Ui.dark = st.dark;
        Ui.loadColors(st);
        setTheme(st.dark ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        try { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO); } catch (Exception e) { }   // le fil principal relaie le son : priorité audio
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        freq = st.freqHz;
        mode = st.mode;
        bwHz = st.bwFor(mode);
        try { InputStream in = getAssets().open("bridge.js"); bridgeScript = Store.readAll(in); in.close(); } catch (Exception e) { }
        build();
        applyKeepScreen();
        setupWeb();
        refreshAll();
        if (Ui.reopenColors) { Ui.reopenColors = false; h.post(new Runnable() { public void run() { DlgSettings.showColors(MainActivity.this); } }); }
        String first = getIntent() != null ? getIntent().getStringExtra("url") : null;
        web.loadUrl(first != null ? first : st.lastUrl);
        h.postDelayed(new Runnable() { public void run() { firstRunSearch(); if (st.checkUpdates) checkUpdate(false); } }, 1200);
        h.postDelayed(callWatch, 1000);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] { "android.permission.POST_NOTIFICATIONS" }, 7);
        try {
            Intent svc = new Intent(this, PlayService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        } catch (Exception e) { }
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        requestFocus();
        registerReceiver(volReceiver, new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
        applyPhoneVolume();
        h.removeCallbacks(watchRun);
        h.postDelayed(watchRun, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Pas de web.onPause() ni d'abandon du focus audio : le son continue, fluide, écran éteint (PlayService garde le processus actif).
        try { unregisterReceiver(volReceiver); } catch (Exception e) { }
        st.freqHz = freq;
        st.mode = mode;
        st.save();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        stopService(new Intent(this, PlayService.class));
        if (focusReq != null) audio.abandonAudioFocusRequest(focusReq);
        if (web != null) { web.destroy(); }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        layoutForOrientation();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        debugIntent(i);
        String u = i.getStringExtra("url");
        if (u != null) openUrl(u);
    }

    // Outils de mise au point par adb : --ez dump true (copie settings.ini dans le dossier public de l'application),
    // --es js "code" (évalue du JavaScript dans la page et écrit le résultat dans le journal, étiquette PSDR).
    void debugIntent(Intent i) {
        if (i == null) return;
        if (i.getBooleanExtra("dump", false)) {
            try {
                st.freqHz = freq; st.mode = mode; st.save();
                java.io.File out = new java.io.File(getExternalFilesDir(null), "settings-dump.ini");
                java.io.FileOutputStream o = new java.io.FileOutputStream(out);
                o.write(st.exportText().getBytes("UTF-8"));
                o.close();
            } catch (Exception e) { android.util.Log.i("PSDR", "dump: " + e); }
        }
        if (i.getBooleanExtra("rec", false)) onRecButton();
        if (i.getBooleanExtra("openrec", false)) openRecordings();
        String code = i.getStringExtra("js");
        if (code != null) web.evaluateJavascript("(function(){try{Promise.resolve((0, eval)(" + org.json.JSONObject.quote(code) + ")).then(function(v){PhoneSdr.post(\"dbg|\"+(typeof v===\"string\"?v:JSON.stringify(v)))},function(e){PhoneSdr.post(\"dbg|ERR \"+e)})}catch(e){PhoneSdr.post(\"dbg|ERR \"+e)}})()", null);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    // ------------------------------------------------------------------ interface

    void build() {
        Window w = getWindow();
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root = new LinearLayout(this);
        root.setBackgroundColor(Ui.bg());
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 6);
        controls.setPadding(p, p, p, p);

        // Ligne 1 : WEBSDR, SET, Mute
        LinearLayout r1 = Ui.row(this);
        btnWeb = Ui.button(this, "WEBSDR", new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; DlgWebSdr.show(MainActivity.this); } });
        btnWeb.setTypeface(Typeface.DEFAULT_BOLD);
        btnSet = Ui.button(this, "SET", new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; DlgSettings.show(MainActivity.this); } });
        btnSet.setTypeface(Typeface.DEFAULT_BOLD);
        btnMute = Ui.button(this, Lang.T("Muet", "Mute"), new View.OnClickListener() {
            public void onClick(View v) { muted = !muted; applyVolume(); updateButtons(); }
        });
        btnMute.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { onMuteLongPress(); return true; } });
        btnRec = Ui.button(this, "● REC", new View.OnClickListener() { public void onClick(View v) { onRecButton(); } });
        btnRec.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { openRecordings(); return true; } });
        r1.addView(btnWeb, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f, this, 2));
        r1.addView(btnSet, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f, this, 2));
        r1.addView(btnRec, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.95f, this, 2));
        btnLock = Ui.button(this, "", new View.OnClickListener() {
            public void onClick(View v) { locked = !locked; bar.locked = locked; updateButtons(); }
        });
        for (Button x : new Button[] { btnWeb, btnSet, btnRec, btnMute, btnLock }) { x.setTextSize(13); x.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0); }
        r1.addView(btnMute, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f, this, 2));
        r1.addView(btnLock, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f, this, 2));
        controls.addView(r1);

        // Ligne 2 : fréquence (appui = clavier) et mode
        LinearLayout r2 = Ui.row(this);
        tvFreq = new TextView(this) {
            // Nom de l'application en tout petit sous la fréquence, centré, sans changer la taille de la case ; s'il ne rentre pas, rien n'est écrit.
            final android.graphics.Paint tiny = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(android.graphics.Canvas c) {
                super.onDraw(c);
                android.text.Layout l = getLayout();
                if (l == null || getText().length() == 0) return;
                String s = "PhoneSDRLink V" + versionName() + " @F5PBG 2026";
                float dm = getResources().getDisplayMetrics().scaledDensity, dp = getResources().getDisplayMetrics().density;
                float top = (getHeight() - l.getHeight()) / 2f + l.getLineBaseline(0);   // bas des chiffres
                tiny.setTypeface(Typeface.DEFAULT);
                tiny.setColor(Ui.cFreqTx != 0 ? Ui.cFreqTx : Ui.sub());
                for (float sz = 7; sz >= 5.5f; sz -= 0.5f) {
                    tiny.setTextSize(sz * dm);
                    float base = getHeight() - 2 * dp - tiny.descent();
                    if (base + tiny.ascent() >= top + dp && tiny.measureText(s) <= getWidth() - 8 * dp) {
                        // Le texte de la fréquence est centré par défilement interne de la vue : on suit ce décalage.
                        c.drawText(s, getScrollX() + (getWidth() - tiny.measureText(s)) / 2f, getScrollY() + base, tiny);
                        return;
                    }
                }
            }
        };
        tvFreq.setTextColor(Ui.freqTx());
        tvFreq.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFreq.setGravity(Gravity.CENTER);
        tvFreq.setTextSize(36);
        tvFreq.setSingleLine(true);
        tvFreq.setBackground(Ui.round(Ui.freqBg(), 10, this));
        tvFreq.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; DlgKeypad.show(MainActivity.this); } });
        btnMode = Ui.button(this, "", new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; chooseMode(); } });
        btnMode.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { chooseBandwidth(); return true; } });
        btnMode.setTextSize(18);
        btnMode.setTypeface(Typeface.DEFAULT_BOLD);
        r2.addView(tvFreq, Ui.lp(0, Ui.dp(this, 64), 1, this, 2));
        r2.addView(btnMode, Ui.lp(Ui.dp(this, 92), Ui.dp(this, 64), 0, this, 2));
        controls.addView(r2);

        // Ligne 3 : scan, conf scan, mémoires
        LinearLayout r3 = Ui.row(this);
        btnScan = Ui.button(this, "▶ Scan", new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; onScanButton(); } });
        btnScanConf = Ui.button(this, Lang.T("Conf scan", "Scan setup"), new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; DlgScan.show(MainActivity.this); } });
        btnMem = Ui.button(this, Lang.T("Mémoires", "Memories"), new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; DlgMemory.show(MainActivity.this); } });
        btnStep = Ui.button(this, "", new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; chooseStep(); } });
        for (Button x : new Button[] { btnScan, btnScanConf, btnMem, btnStep }) { x.setTextSize(13); x.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0); }
        r3.addView(btnScan, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, this, 2));
        r3.addView(btnScanConf, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f, this, 2));
        r3.addView(btnMem, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.05f, this, 2));
        r3.addView(btnStep, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f, this, 2));
        controls.addView(r3);

        // Ligne 4 : barre à glisser, sur toute la largeur
        barRow = Ui.row(this);
        final LinearLayout r4 = barRow;
        bar = new FreqBar(this);
        bar.hint = Lang.T("◀ glisser : − fréquence | + fréquence ▶", "◀ swipe: − frequency | + frequency ▶");
        bar.setListener(new FreqBar.Listener() { public void onSteps(int n, double ms) { tuneBySteps(n, ms); } });
        r4.addView(bar, Ui.lp(0, Ui.dp(this, 52), 1, this, 2));
        controls.addView(r4);
        bar.onLong = new Runnable() { public void run() { chooseBarPlace(); } };
        tvStatus = Ui.small(this, "");
        tvStatus.setPadding(Ui.dp(this, 6), Ui.dp(this, 2), Ui.dp(this, 6), 0);
        tvStatus.setMinLines(1);
        tvStatus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { if (updateUrl != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))); }
        });
        controls.addView(tvStatus);
        tvPage = Ui.small(this, "");
        tvPage.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), Ui.dp(this, 4));
        tvPage.setSingleLine(true);
        controls.addView(tvPage);

        web = new WebView(this);
        webFrame = new FrameLayout(this);
        webFrame.setBackgroundColor(Color.BLACK);
        webFrame.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout zoom = new LinearLayout(this);
        zoom.setOrientation(LinearLayout.VERTICAL);
        Button plus = zoomButton("+", true), minus = zoomButton("−", false);
        zoom.addView(initButton());
        zoom.addView(plus);
        zoom.addView(minus);
        zoomBox = zoom;
        holdThen(plus, new Runnable() { public void run() { chooseZoomSide(); } });
        holdThen(minus, new Runnable() { public void run() { chooseZoomSide(); } });
        right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(webFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        placeZoom();

        root.addView(controls);
        root.addView(right);
        setContentView(root);
        placeBar();
        layoutForOrientation();
    }

    // Appui de 3 secondes sans lâcher sur un bouton : exécute r (le clic normal est alors ignoré).
    void holdThen(final View v, final Runnable r) {
        final boolean[] fired = { false };
        final Runnable fire = new Runnable() { public void run() { fired[0] = true; v.setPressed(false); r.run(); } };
        v.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            public boolean onTouch(View b, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: fired[0] = false; x0 = e.getX(); y0 = e.getY(); h.postDelayed(fire, 3000); break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getX() - x0) > Ui.dp(MainActivity.this, 12) || Math.abs(e.getY() - y0) > Ui.dp(MainActivity.this, 12)) h.removeCallbacks(fire);
                        break;
                    case MotionEvent.ACTION_UP: h.removeCallbacks(fire); if (fired[0]) { fired[0] = false; return true; } break;
                    case MotionEvent.ACTION_CANCEL: h.removeCallbacks(fire); break;
                }
                return false;
            }
        });
    }

    // Les trois boutons INIT, + et − : à droite (position d'origine) ou à gauche ; plus hauts quand la barre est en bas.
    void placeZoom() {
        if (zoomBox == null) return;
        if (zoomBox.getParent() != null) ((ViewGroup) zoomBox.getParent()).removeView(zoomBox);
        FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | (st.zoomLeft ? Gravity.START : Gravity.END));
        int m = Ui.dp(this, 10);
        zp.setMargins(st.zoomLeft ? m : 0, 0, st.zoomLeft ? 0 : m, st.barBottom ? Ui.dp(this, 28) : m);
        webFrame.addView(zoomBox, zp);
    }

    void chooseZoomSide() {
        Ui.choose(this, Lang.T("Boutons INIT + −", "INIT + − buttons"), new String[] { Lang.T("À gauche de l'écran", "Left of the screen"), Lang.T("À droite de l'écran", "Right of the screen") }, new Ui.Index() {
            public void chose(int i) { st.zoomLeft = i == 0; st.save(); placeZoom(); }
        });
    }

    // La barre de réglage : au-dessus du WebSDR (haut) ou tout en bas de l'écran.
    void placeBar() {
        if (barRow == null) return;
        if (barRow.getParent() != null) ((ViewGroup) barRow.getParent()).removeView(barRow);
        if (st.barBottom) right.addView(barRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        else controls.addView(barRow, 3);
        placeZoom();
    }

    void chooseBarPlace() {
        Ui.choose(this, Lang.T("Barre de réglage", "Tuning bar"), new String[] { Lang.T("Haut (où elle est)", "Top (where it is)"), Lang.T("Bas de l'écran", "Bottom of the screen") }, new Ui.Index() {
            public void chose(int i) { st.barBottom = i == 1; st.save(); placeBar(); }
        });
    }
    // Rond « INIT » : relance le WebSDR (rechargement de la page) quand le son s'est arrêté.
    Button initButton() {
        Button b = Ui.button(this, "INIT", new View.OnClickListener() {
            public void onClick(View v) {
                if (blocked()) return;
                sdrKind = "";
                status(Lang.T("INIT : le WebSDR est relancé", "INIT: restarting the WebSDR"));
                cancelScaleGuard();
                initReset = true;
                String cu = web.getUrl();
                if (cu != null && !cu.isEmpty()) web.loadUrl(cu); else web.reload();   // nouveau chargement : zoom et affichage d'origine
            }
        });
        b.setTextSize(11);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(Ui.round(0xAA000000, 24, this));
        b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        p.setMargins(0, Ui.dp(this, 4), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    // Verrou : tant qu'il est rouge, aucune commande ne change rien (seuls Muet et Verrou restent actifs).
    boolean blocked() {
        if (locked) Ui.toast(this, Lang.T("🔒 Verrouillé : appuyez sur Verrou pour modifier", "🔒 Locked: press Lock to change"));
        return locked;
    }

    Button zoomButton(String text, final boolean in) {
        Button b = Ui.button(this, text, new View.OnClickListener() {
            public void onClick(View v) { if (blocked()) return; cancelScaleGuard(); if (in) web.zoomIn(); else web.zoomOut(); h.postDelayed(new Runnable() { public void run() { applySpectrumZoom(); } }, 700); }
        });
        b.setTextSize(22);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(Ui.round(0xAA000000, 24, this));
        b.setTextColor(Color.WHITE);
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        ((LinearLayout.LayoutParams) b.getLayoutParams()).setMargins(0, Ui.dp(this, 4), 0, 0);
        return b;
    }

    // Portrait (9/16) : commandes au-dessus, WebSDR en dessous. Paysage : commandes à gauche (défilables), WebSDR à droite.
    void layoutForOrientation() {
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        root.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        if (controls.getParent() == root) root.removeView(controls);
        if (controls.getParent() instanceof ScrollView) ((ScrollView) controls.getParent()).removeView(controls);
        View old = root.getChildAt(0) != right ? root.getChildAt(0) : null;
        if (old != null) root.removeView(old);
        View top;
        if (land) {
            ScrollView sv = new ScrollView(this);
            sv.addView(controls);
            top = sv;
            root.addView(top, 0, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f));
            right.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.58f));
        } else {
            root.addView(controls, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            right.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        }
        tvFreq.setTextSize(land ? 30 : 36);
    }

    // Relit les réglages pour réafficher tous les textes (après un changement de langue ou de thème : l'écran est recréé).
    void refreshAll() {
        updateDisplays();
        updateButtons();
    }

    void updateDisplays() {
        tvFreq.setText(formatFreq(freq));
        btnMode.setText(bwHz > 0 ? modeLabel(mode) + "\n" + bwText(bwHz).replace(" kHz", "k").replace(" Hz", "Hz") : modeLabel(mode));
        btnStep.setText(stepText(st.stepFor(mode)));
    }

    void updateButtons() {
        boolean m = muted || callMute || focusMute;
        btnMute.setText(m ? Lang.T("🔇 Muet", "🔇 Muted") : sleepAt != 0 ? "⏱ " + sleepText() : Lang.T("Muet", "Mute"));
        Ui.paint(btnMute, muted, Ui.danger());
        btnLock.setText(locked ? "🔒 " + Lang.T("Verrou", "Lock") : "🔓 " + Lang.T("Verrou", "Lock"));
        Ui.paint(btnLock, locked, Ui.danger());
        btnRec.setText(recording ? "■ " + recTime() : "● REC");
        Ui.paint(btnRec, recording, Ui.danger());
        btnScan.setText(scanning ? Lang.T("■ Arrêter le scan", "■ Stop scan") : "▶ Scan");
        Ui.paint(btnScan, scanning, Ui.danger());
    }

    void status(String s) {
        tvStatus.setText(s);
    }

    static String formatFreq(long hz) {
        return String.format(java.util.Locale.US, "%d.%03d.%03d", hz / 1000000, (hz / 1000) % 1000, hz % 1000);
    }

    static String stepText(long hz) { return hz < 1000 ? hz + " Hz" : Memory.kHz(hz) + " kHz"; }

    static String modeLabel(String m) { return "DATAU".equals(m) ? "DATA" : m; }

    // ------------------------------------------------------------------ WebSDR (WebView et pont JavaScript)

    class JsBridge {
        @JavascriptInterface
        public void post(final String msg) {
            h.post(new Runnable() { public void run() { onWebMessage(msg); } });
        }
    }

    void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        // Page « ordinateur » : les WebSDR y montrent leur affichage complet, que « + » et « − » agrandissent ou réduisent.
        String ua = s.getUserAgentString().replaceAll("\\(Linux; Android[^)]*\\)", "(X11; Linux x86_64)").replace(" Mobile", "");
        s.setUserAgentString(ua);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new JsBridge(), "PhoneSdr");
        if (Build.VERSION.SDK_INT >= 26) web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);   // le processus qui joue le son garde sa priorité même écran éteint
        web.setBackgroundColor(Color.BLACK);
        web.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getPointerCount() > 1) cancelScaleGuard();   // zoom à la main
                if (locked && e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) blocked();
                return locked;
            }
        });
        installBridgeScripts();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                sdrKind = "";
                sdrF = 0;
                if (recording && !recPending) { recResume = true; recTries = 0; }   // le REC continue sur la nouvelle page
                else if (recording || recPending) recFinish();
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) injectFallback();
                tvPage.setText(url);
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) injectFallback();
                applyAudio();
                if (captureScale || initReset) h.postDelayed(scaleInit, 1200);
                st.lastUrl = url;
                tvPage.setText(url);
            }
            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                long now = System.currentTimeMillis();
                if (now - crashSince > 30000) { crashSince = now; crashes = 0; }
                if (++crashes <= 2) h.post(new Runnable() { public void run() { recreate(); } });
                else status(Lang.T("La page ne peut pas s'afficher dans cette application", "This page cannot be displayed in this app"));
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView v, String title) { if (title != null) tvPage.setText(title); }
        });
    }

    void installBridgeScripts() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        if (bridgeHandle != null) bridgeHandle.remove();
        bridgeHandle = WebViewCompat.addDocumentStartJavaScript(web, bridgeScript, Collections.singleton("*"));
        updateIdentity();
    }

    // Pseudo donné aux WebSDR qui en demandent un avant le son : websdr.org le lit dans le cookie « username », KiwiSDR dans
    // localStorage « ident » (deux caractères au moins). Un nom tapé sur le site est gardé ; celui qui a été mis ici est remplacé.
    String identityScript() {
        String name = Store.clean(st.callsign);
        if (name.isEmpty()) return null;
        StringBuilder js = new StringBuilder("'");
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c < 0x20 || c > 0x7E) js.append(String.format("\\u%04x", (int) c)); else js.append(c);
        }
        js.append("'");
        return "(function (name) {\n"
            + "  if (window.top !== window) return;\n"
            + "  try {\n"
            + "    var kiwi = name.length >= 2 ? name : name + name;\n"
            + "    var cur = localStorage.getItem('ident'), mine = localStorage.getItem('__sdrlink_ident');\n"
            + "    if (!cur || cur === mine) { localStorage.setItem('ident', kiwi); localStorage.setItem('__sdrlink_ident', kiwi); }\n"
            + "  } catch (e) { }\n"
            + "  try {\n"
            + "    var get = function (k) { var m = document.cookie.match(new RegExp('(?:^|;\\\\s*)' + k + '=([^;]*)')); return m ? m[1] : null; };\n"
            + "    var curC = get('username'), mineC = get('__sdrlink_user'), tail = '; max-age=157680000; path=/; SameSite=Lax';\n"
            + "    if (!curC || curC === mineC) { document.cookie = 'username=' + name + tail; document.cookie = '__sdrlink_user=' + name + tail; }\n"
            + "  } catch (e) { }\n"
            + "})(" + js + ");";
    }

    void updateIdentity() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        if (identityHandle != null) { identityHandle.remove(); identityHandle = null; }
        String s = identityScript();
        if (s != null) identityHandle = WebViewCompat.addDocumentStartJavaScript(web, s, Collections.singleton("*"));
    }

    // WebView trop ancien pour les scripts « au début du document » : injection au début et à la fin du chargement.
    void injectFallback() {
        web.evaluateJavascript(bridgeScript, null);
        String s = identityScript();
        if (s != null) web.evaluateJavascript(s, null);
    }

    // « Interdire l'extinction de l'écran » (SET).
    void applyKeepScreen() {
        if (st.keepScreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Volume de la page au maximum (une fois par page) : le volume du téléphone donne alors le son le plus fort possible.
    void maxPageVolume() { applyPhoneVolume(); }

    // Zoom du spectre : 3 kHz de spectre = 3 mm sur l'écran (SET), recalculé quand la taille de la page change.
    void applySpectrumZoom() {
        if (!st.spectrumZoom || sdrKind.isEmpty()) return;
        float xdpi = getResources().getDisplayMetrics().xdpi;
        js("if(window.__sdrlink)__sdrlink.spectrumZoom(" + web.getWidth() + "," + xdpi + ",1);");
    }

    // Le volume du téléphone (touches ou curseur système) est recopié sur le curseur de volume de la page du WebSDR.
    void applyPhoneVolume() {
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        float f = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
        js("if(window.__sdrlink)__sdrlink.setPageVolume(" + f + ");");
    }

    final android.content.BroadcastReceiver volReceiver = new android.content.BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if (i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1) == AudioManager.STREAM_MUSIC) applyPhoneVolume();
        }
    };

    @Override
    public boolean onKeyDown(int code, android.view.KeyEvent e) {
        boolean r = super.onKeyDown(code, e);
        if (code == android.view.KeyEvent.KEYCODE_VOLUME_UP || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) h.postDelayed(new Runnable() { public void run() { applyPhoneVolume(); } }, 150);
        return r;
    }

    void js(String code) {
        web.evaluateJavascript("(function(){try{" + code + "}catch(e){}})()", null);
    }

    void applyVolume() {
        boolean m = muted || callMute || focusMute;
        js("if(window.__sdrlink)__sdrlink.setVolume(" + (m ? 0 : 1) + ");");
    }

    // « Réinitialisation automatique WEBSDR » : si la page reste 30 s sans produire de son alors que le volume n'est pas à zéro
    // (téléphone, mute de l'application, curseur de la page), la page est rechargée ; 3 essais de suite au plus par WebSDR.
    int autoTries;
    final Runnable watchRun = new Runnable() {
        public void run() {
            h.postDelayed(this, 5000);
            if (!st.autoReload || sdrKind.isEmpty() || muted || callMute || focusMute || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || autoTries >= 3) return;
            web.evaluateJavascript("(function(){try{return window.__sdrlink?__sdrlink.silentMs():-1}catch(e){return -1}})()", new android.webkit.ValueCallback<String>() {
                public void onReceiveValue(String v) {
                    long ms;
                    try { ms = Long.parseLong(v.trim()); } catch (Exception e) { return; }
                    if (ms >= 30000 && st.autoReload && !muted && !callMute && !focusMute) {
                        autoTries++;
                        status(Lang.T("Plus de son depuis 30 s : le WebSDR est rechargé.", "No sound for 30 s: the WebSDR is reloaded."));
                        web.reload();
                    }
                }
            });
        }
    };

    // Le notch automatique n'est pas appliqué en CW ni en DATA (la coche reste telle que l'opérateur l'a mise).
    boolean notchNow() { return st.autoNotch && !"CW".equals(mode) && !(mode != null && mode.startsWith("DATA")); }

    boolean notchSent;

    void applyAudio() {
        applyVolume();
        notchSent = notchNow();
        js("if(window.__sdrlink)__sdrlink.setWatch(" + st.autoReload + ");");
        js("if(window.__sdrlink){__sdrlink.setNotch(" + notchSent + "," + st.notchLevel + ");__sdrlink.setDnr(" + st.autoDnr + "," + st.dnrLevel + ");}");
    }

    static String toSdr(String m) {
        if ("LSB".equals(m)) return "lsb";
        if ("USB".equals(m) || "DATAU".equals(m)) return "usb";
        if ("CW".equals(m)) return "cw";
        if ("AM".equals(m)) return "am";
        if ("FM".equals(m)) return "fm";
        return "";
    }

    // Famille lsb/usb/cw/am/fm du mode affiché par le WebSDR.
    static String family(String m) {
        m = m == null ? "" : m.toLowerCase(java.util.Locale.ROOT);
        if (m.startsWith("lsb") || m.equals("lsn") || m.equals("digl") || m.equals("sal")) return "lsb";
        if (m.startsWith("usb") || m.equals("usn") || m.equals("digu") || m.equals("sau")) return "usb";
        if (m.startsWith("cw")) return "cw";
        if (m.contains("fm")) return "fm";
        if (m.contains("am") || m.equals("sas") || m.equals("dsb")) return "am";
        return "";
    }

    static String fromFamily(String f) {
        if (f.equals("lsb")) return "LSB";
        if (f.equals("usb")) return "USB";
        if (f.equals("cw")) return "CW";
        if (f.equals("am")) return "AM";
        if (f.equals("fm")) return "FM";
        return "";
    }

    // Un changement de fréquence ne change pas le zoom de l'écran : l'échelle de la page est notée avant une série de commandes
    // et remise 1,5 s après la dernière si elle a bougé ; un zoom voulu (+, − ou geste) annule cette garde.
    float scaleRef, initScale;
    int initTries;
    boolean captureScale = true, initReset;   // initScale : échelle d'une page fraîchement ouverte ; INIT y remet l'écran

    final Runnable scaleInit = new Runnable() {
        @SuppressWarnings("deprecation")
        public void run() {
            float s = web.getScale();
            if (initReset) {
                boolean off = initScale > 0 && s > 0 && Math.abs(s / initScale - 1) > 0.02f;
                if (off && initTries++ < 5) { web.zoomBy(initScale / s); h.postDelayed(this, 1500); }   // la page peut refuser le premier essai : on revérifie
                else { initReset = false; initTries = 0; }
            } else if (captureScale) { initScale = s; captureScale = false; }
        }
    };
    final Runnable scaleCheck = new Runnable() {
        @SuppressWarnings("deprecation")
        public void run() {
            float s = web.getScale();
            if (scaleRef > 0 && s > 0 && Math.abs(s / scaleRef - 1) > 0.03f) web.zoomBy(scaleRef / s);
            scaleRef = 0;
        }
    };

    @SuppressWarnings("deprecation")
    void guardScale() {
        if (scaleRef == 0) scaleRef = web.getScale();
        h.removeCallbacks(scaleCheck);
        h.postDelayed(scaleCheck, 1500);
    }

    void cancelScaleGuard() { h.removeCallbacks(scaleCheck); scaleRef = 0; }

    void setSdr(long hz, String sdrMode) {
        guardScale();
        js("if(window.__sdrlink)__sdrlink.set(" + hz + ",'" + sdrMode + "');");
    }

    void openUrl(String url) {
        url = url == null ? "" : url.trim();
        if (url.isEmpty()) return;
        if (!url.contains("://")) url = "http://" + url;
        sdrKind = "";
        autoTries = 0;
        bwHz = st.bwFor(mode);   // nouveau WebSDR : la largeur retenue pour le mode (appliquée au premier état de la page)
        captureScale = true;
        updateDisplays();
        web.loadUrl(url);
    }

    String currentUrl() { return web.getUrl() == null ? st.lastUrl : web.getUrl(); }
    String currentTitle() { return web.getTitle() == null ? "" : web.getTitle(); }

    // Messages du pont : « type|Hz|mode[|u] » (état du WebSDR) ou « click » (appui dans la page).
    void onWebMessage(String msg) {
        if ("click".equals(msg)) { onPageClick(); return; }
        if (msg.startsWith("mp3|")) { recWrite(msg.substring(4)); return; }
        if (msg.equals("mp3end")) { recFinish(); return; }
        if (msg.startsWith("rec|")) { onRecMessage(msg.split("\\|", -1)); return; }
        if (msg.startsWith("dbg|")) { android.util.Log.i("PSDR", "js=" + msg.substring(4)); return; }
        String[] p = msg.split("\\|", -1);
        if (p.length < 3) return;
        long f;
        try { f = Long.parseLong(p[1]); } catch (NumberFormatException e) { return; }
        onSdrState(p[0], f, p[2], p.length > 3 && p[3].equals("u"));
    }

    void onSdrState(String kind, long f, String m, boolean user) {
        boolean first = sdrKind.isEmpty();
        sdrKind = kind;
        sdrF = f;
        if (first && recResume) h.postDelayed(recResumeRun, 1500);
        if (first) { applyAudio(); h.postDelayed(new Runnable() { public void run() { maxPageVolume(); } }, 2500); h.postDelayed(new Runnable() { public void run() { applySpectrumZoom(); } }, 4500); }
        String fam = family(m);

        // Juste après un clic qui a arrêté le scan : la page est remise sur la fréquence scannée.
        if (!first && System.currentTimeMillis() < scanHoldUntil) {
            if (Math.abs(f - scanHoldHz) > TOLERANCE) setSdr(scanHoldHz, "");
            return;
        }
        if (first) {
            // Page qui vient de se charger : l'application impose sa fréquence et son mode.
            settleUntil = System.currentTimeMillis() + SETTLE_MS;
            setSdr(freq, toSdr(mode));
            if (bwHz > 0) h.postDelayed(new Runnable() { public void run() { if (bwHz > 0) js("if(window.__sdrlink)__sdrlink.setBw(" + bwHz + ");"); } }, 3500);   // page rechargée : largeur remise
            updateDisplays();
            return;
        }
        // Page en cours de mise en place : elle annonce parfois encore son ancienne fréquence après notre réglage.
        if (System.currentTimeMillis() < settleUntil && !user) {
            if (Math.abs(f - freq) > TOLERANCE) setSdr(freq, "");
            return;
        }
        if (user) settleUntil = 0;
        // Réglage fait dans la page (clic dans la cascade, touches) : l'affichage le suit.
        if (System.currentTimeMillis() < targetUntil && Math.abs(f - target) > 400) return;   // retard de la page pendant qu'on glisse
        if (f > 0) freq = f;
        if (!fam.isEmpty() && !toSdr(mode).equals(fam)) {
            mode = fromFamily(fam);
            syncBw(true);
            if (bwHz > 0) h.postDelayed(new Runnable() { public void run() { if (bwHz > 0) js("if(window.__sdrlink)__sdrlink.setBw(" + bwHz + ");"); } }, 700);
        }
        updateDisplays();
        saveSoon();
    }

    final Runnable saver = new Runnable() { public void run() { st.freqHz = freq; st.mode = mode; st.save(); } };

    void saveSoon() {
        h.removeCallbacks(saver);
        h.postDelayed(saver, 1000);
    }

    // ------------------------------------------------------------------ réglage de la fréquence et du mode

    void tune(long hz) {
        hz = Math.max(Store.MIN_HZ, Math.min(Store.MAX_HZ, hz));
        freq = hz;
        target = hz;
        targetUntil = System.currentTimeMillis() + 1000;
        setSdr(hz, "");
        updateDisplays();
        saveSoon();
    }

    // Le mode change : le pas et la largeur de bande retenus pour ce mode sont repris (le pas s'affiche d'après le mode).
    // push : la largeur est aussi annoncée à la page (réappliquée par L.set après le changement de mode).
    void syncBw(boolean push) {
        bwHz = st.bwFor(mode);
        if (notchNow() != notchSent) applyAudio();
        if (push) js("if(window.__sdrlink)__sdrlink.bw=" + bwHz + ";");
    }

    void setMode(String m) {
        mode = m;
        syncBw(true);
        setSdr(0, toSdr(m));
        updateDisplays();
        saveSoon();
    }

    static long accelerate(long step, double msPerStep) {
        if (step >= 1000) return step;
        if (msPerStep < 30) return step * 10;
        if (msPerStep < 60) return step * 5;
        if (msPerStep < 100) return step * 2;
        return step;
    }

    // Glissement sur la barre : n pas (positif = vers la droite = fréquence plus haute).
    void tuneBySteps(int n, double ms) {
        long step = accelerate(st.stepFor(mode), ms);
        long base = System.currentTimeMillis() < targetUntil && target > 0 ? target : freq;
        long t = Math.round((double) base / step) * step + n * step;
        if (scanning) stopScan(null);
        tune(t);
    }

    void chooseMode() {
        String[] names = new String[MODES.length];
        for (int i = 0; i < MODES.length; i++) names[i] = modeLabel(MODES[i]);
        Ui.choose(this, Lang.T("Mode :", "Mode:"), names, new Ui.Index() { public void chose(int i) { setMode(MODES[i]); } });
    }

    void chooseStep() {
        boolean ssb = "USB".equals(mode) || "LSB".equals(mode);
        final int[] steps = new int[ssb ? Store.STEPS.length : Store.STEPS.length - 1];
        String[] names = new String[steps.length];
        for (int i = 0, k = 0; i < Store.STEPS.length; i++) if (ssb || Store.STEPS[i] != 1) { steps[k] = Store.STEPS[i]; names[k++] = stepText(Store.STEPS[i]); }
        Ui.choose(this, Lang.T("Pas :", "Step:"), names, new Ui.Index() { public void chose(int i) { st.setStepFor(mode, steps[i]); updateDisplays(); st.save(); } });
    }

    void tuneMemory(Memory m) {
        if (m == null || m.hz <= 0) return;
        applyMemory(m);
        st.addRecent(m);
        status(Lang.F("Mémoire « {0} » : {1} kHz", "Memory \"{0}\": {1} kHz", m.label(), Memory.kHz(m.hz)));
        st.save();
    }

    void applyMemory(Memory m) {
        freq = m.hz;
        target = m.hz;
        targetUntil = System.currentTimeMillis() + 1000;
        if (!m.mode.isEmpty()) { mode = m.mode; syncBw(true); }
        setSdr(m.hz, m.mode.isEmpty() ? "" : toSdr(m.mode));
        updateDisplays();
        saveSoon();
    }

    // ------------------------------------------------------------------ scan

    void onScanButton() {
        if (scanning) { stopScan(null); return; }
        final ScanProfile cur = st.currentScan();
        java.util.ArrayList<String> items = new java.util.ArrayList<String>();
        items.add(Lang.T("Reprendre le scan", "Resume the scan") + "  —  " + resumeText());
        items.add(Lang.T("Réglage actuel de « Conf scan »", "Current \"Scan setup\" setting") + "  —  " + summary(cur));
        for (ScanProfile p : st.scanProfiles) items.add(p.name + "  —  " + summary(p));
        Ui.choose(this, "Scan", items.toArray(new String[0]), new Ui.Index() {
            public void chose(int i) {
                if (i == 0) startScan(true);
                else if (i == 1) startScan(false);
                else { st.useScan(st.scanProfiles.get(i - 2)); st.save(); startScan(false); }
            }
        });
    }

    static String dwellText(int sec) {
        return sec < 60 ? Lang.F("{0} s", "{0} s", sec) : Lang.F("{0} min", "{0} min", sec / 60);
    }

    static String scanStepText(long hz) { return hz < 1000 ? hz + " Hz" : Memory.kHz(hz) + " kHz"; }

    String summary(ScanProfile p) {
        if (p.type == 1) return Lang.F("mémoires : {0}, {1}", "memories: {0}, {1}", p.mems.size(), dwellText(p.memDwell));
        if (p.lo() <= 0 || p.lo() == p.hi()) return Lang.T("fréquences non réglées", "frequencies not set");
        String s = Memory.kHz(p.lo()) + " – " + Memory.kHz(p.hi()) + " kHz, " + scanStepText(p.stepHz) + ", " + dwellText(p.freqDwell);
        if (!p.skips.isEmpty()) s += ", " + Lang.F("zones non scannées : {0}", "skipped ranges: {0}", p.skips.size());
        return s;
    }

    static long resumeFreq(ScanProfile p, long cur) {
        if (cur < p.lo() || cur > p.hi()) return p.next(-1);
        return p.skipped(cur) ? p.next(cur) : cur;
    }

    java.util.ArrayList<Memory> scanMemories() {
        java.util.ArrayList<Memory> l = new java.util.ArrayList<Memory>();
        for (Memory m : st.memories) if (m.scan) l.add(m);
        return l;
    }

    String resumeText() {
        long cur = freq;
        if (st.scanType == 0) {
            ScanProfile p = st.currentScan();
            if (p.lo() <= 0 || p.lo() == p.hi()) return Lang.T("fréquences non réglées", "frequencies not set");
            long f = resumeFreq(p, cur);
            if (f < 0) return Lang.T("toutes les fréquences sont dans des zones non scannées", "all frequencies are in skipped ranges");
            if (cur >= p.lo() && cur <= p.hi()) return Lang.F("à {0} kHz", "at {0} kHz", Memory.kHz(f));
            return Lang.F("au début ({0} kHz) : fréquence actuelle hors de la plage", "from the start ({0} kHz): current frequency out of range", Memory.kHz(f));
        }
        java.util.ArrayList<Memory> mems = scanMemories();
        if (mems.isEmpty()) return Lang.T("aucune mémoire cochée", "no memory checked");
        for (Memory m : mems) if (m.hz == cur) return Lang.F("à la mémoire {0}", "at memory {0}", m.label());
        return Lang.F("au début ({0}) : fréquence actuelle hors des mémoires", "from the start ({0}): current frequency not in the memories", mems.get(0).label());
    }

    void startScan(boolean resume) {
        if (scanning) stopScan(null);
        long cur = resume ? freq : -1;
        int dwell;
        if (st.scanType == 0) {
            scanRun = st.currentScan();
            if (scanRun.lo() <= 0 || scanRun.lo() == scanRun.hi()) { status(Lang.T("Scan : indiquez les fréquences de début et de fin dans « Conf scan »", "Scan: set the start and end frequencies in \"Scan setup\"")); return; }
            scanHz = resumeFreq(scanRun, cur);
            if (scanHz < 0) { status(Lang.T("Scan : toutes les fréquences sont dans des zones non scannées (« Conf scan »)", "Scan: all frequencies are in skipped ranges (\"Scan setup\")")); return; }
            dwell = Math.max(1, st.scanFreqDwell);
        } else {
            scanMems = scanMemories();
            if (scanMems.isEmpty()) { status(Lang.T("Scan : cochez des mémoires dans « Conf scan »", "Scan: check memories in \"Scan setup\"")); return; }
            scanIndex = 0;
            if (resume) for (int i = 0; i < scanMems.size(); i++) if (scanMems.get(i).hz == cur) scanIndex = i;
            dwell = Math.max(1, st.scanMemDwell);
        }
        scanning = true;
        scanDwellMs = dwell * 1000L;
        scanTune();
        h.postDelayed(scanTick, scanDwellMs);
        updateButtons();
    }

    long scanDwellMs = 5000;

    void stopScan(String reason) {
        if (!scanning) return;
        scanning = false;
        h.removeCallbacks(scanTick);
        status(reason != null ? Lang.F("Scan arrêté : {0}", "Scan stopped: {0}", reason) : Lang.T("Scan arrêté", "Scan stopped"));
        updateButtons();
    }

    void scanNext() {
        if (!scanning) return;
        if (st.scanType == 0) scanHz = scanRun.next(scanHz);
        else scanIndex = (scanIndex + 1) % scanMems.size();
        scanTune();
        h.postDelayed(scanTick, scanDwellMs);
    }

    void scanTune() {
        String what;
        if (st.scanType == 0) { tune(scanHz); what = Memory.kHz(scanHz) + " kHz"; }
        else {
            Memory m = scanMems.get(scanIndex);
            applyMemory(m);
            what = m.label() + (m.name.length() > 0 ? " (" + Memory.kHz(m.hz) + " kHz)" : "");
        }
        status(Lang.F("Scan : {0}", "Scan: {0}", what));
    }

    // Appui dans la page du WebSDR pendant un scan : le scan s'arrête sur la fréquence scannée à ce moment-là ; pendant
    // 2 s, le changement de fréquence que l'appui fait dans la cascade est annulé. L'appui suivant règle la fréquence normalement.
    void onPageClick() {
        if (!scanning) { scanHoldUntil = 0; return; }
        long hz = st.scanType == 0 ? scanHz : scanMems.get(scanIndex).hz;
        stopScan(Lang.F("clic dans la page, arrêt sur {0} kHz", "click in the page, stopped on {0} kHz", Memory.kHz(hz)));
        scanHoldHz = hz;
        scanHoldUntil = System.currentTimeMillis() + SCAN_HOLD_MS;
        if (Math.abs(sdrF - hz) > TOLERANCE) { freq = hz; setSdr(hz, ""); updateDisplays(); }
    }

    // ------------------------------------------------------------------ son : appel téléphonique, focus audio

    final Runnable callWatch = new Runnable() {
        public void run() {
            int m = audio.getMode();
            boolean inCall = m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_RINGTONE || m == AudioManager.MODE_IN_COMMUNICATION;
            if (inCall != callMute) {
                callMute = inCall;
                applyVolume();
                updateButtons();
                if (inCall) status(Lang.T("Appel téléphonique : son du WebSDR coupé", "Phone call: WebSDR sound muted"));
            }
            h.postDelayed(this, 1000);
        }
    };

    void requestFocus() {
        if (focusReq != null) {
            // Même demande qu'avant (pas de nouvelle : l'ancienne recevrait une perte de focus et couperait le son).
            if (audio.requestAudioFocus(focusReq) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && focusMute) {
                focusMute = false;
                applyVolume();
                updateButtons();
            }
            return;
        }
        AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focusReq = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs).setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                public void onAudioFocusChange(int change) {
                    boolean lost = change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                    if (change == AudioManager.AUDIOFOCUS_GAIN || lost) {
                        focusMute = lost;
                        applyVolume();
                        updateButtons();
                    }
                }
            }).build();
        audio.requestAudioFocus(focusReq);
    }

    // ------------------------------------------------------------------ largeur de bande (appui long sur le mode)

    int bwHz;   // 0 = filtre d'origine du WebSDR

    static String bwText(int hz) {
        return hz < 1000 ? hz + " Hz" : hz % 1000 == 0 ? (hz / 1000) + " kHz" : String.format(java.util.Locale.US, "%.2f", hz / 1000.0).replaceAll("0+$", "").replace(".", Lang.T(",", ".")) + " kHz";
    }

    void chooseBandwidth() {
        if (blocked()) return;
        if (sdrKind.isEmpty()) { status(Lang.T("Largeur de bande impossible : aucun WebSDR actif", "Cannot set the bandwidth: no active WebSDR")); return; }
        final int[] v = bwChoices(mode);
        String[] items = new String[v.length + 2];
        items[0] = Lang.T("Filtre d'origine du WebSDR", "WebSDR default filter") + (bwHz == 0 ? "  ✓" : "");
        for (int i = 0; i < v.length; i++) items[i + 1] = bwText(v[i]) + (bwHz == v[i] ? "  ✓" : "");
        items[v.length + 1] = Lang.T("Autre valeur (en kHz)…", "Other value (kHz)…") + (bwHz != 0 && !contains(v, bwHz) ? "  ✓ " + bwText(bwHz) : "");
        Ui.choose(this, Lang.T("Largeur de bande du signal :", "Signal bandwidth:"), items, new Ui.Index() {
            public void chose(int i) {
                if (i == 0) setBandwidth(0);
                else if (i <= v.length) setBandwidth(v[i - 1]);
                else Ui.input(MainActivity.this, Lang.T("Largeur de bande en kHz (0,1 à 15)", "Bandwidth in kHz (0.1 to 15)"), "kHz", bwHz >= 1000 ? bwText(bwHz).replace(" kHz", "") : bwHz > 0 ? String.valueOf(bwHz / 1000.0).replace(".", Lang.T(",", ".")) : "", new Ui.Text() {
                    public void got(String t) {
                        double k = -1;
                        try { k = Double.parseDouble(t.trim().replace(',', '.')); } catch (NumberFormatException e) { }
                        if (!(k >= 0.1 && k <= 15)) { Ui.toast(MainActivity.this, Lang.T("Valeur invalide (0,1 à 15 kHz).", "Invalid value (0.1 to 15 kHz).")); return; }
                        setBandwidth((int) Math.round(k * 1000));
                    }
                });
            }
        });
    }

    // Largeurs proposées selon le mode (Hz) ; « filtre d'origine » et « autre valeur » sont toujours ajoutés.
    static int[] bwChoices(String m) {
        if ("CW".equals(m)) return new int[] { 100, 200, 300, 400, 500, 800 };
        if ("USB".equals(m) || "LSB".equals(m)) return new int[] { 2100, 2400, 2600, 2800, 2900, 3000, 4000, 5000 };
        if ("AM".equals(m)) return new int[] { 6000, 8000, 10000 };
        if ("FM".equals(m)) return new int[] { 8000, 10000, 12000, 15000 };
        return new int[] { 200, 300, 500, 2100, 2400, 2800, 3000, 4000 };   // DATA
    }

    static boolean contains(int[] a, int x) { for (int y : a) if (y == x) return true; return false; }

    void setBandwidth(int hz) {
        bwHz = hz;
        st.setBwFor(mode, hz);   // devient la largeur par défaut de ce mode, sur tous les WebSDR et après un redémarrage
        st.save();
        if (hz == 0) {
            // Retour au filtre d'origine : le mode est simplement réappliqué.
            js("if(window.__sdrlink)__sdrlink.bw=0;");
            setSdr(0, toSdr(mode));
            status(Lang.T("Largeur de bande : filtre d'origine du WebSDR", "Bandwidth: WebSDR default filter"));
        } else {
            js("if(window.__sdrlink)__sdrlink.setBw(" + hz + ");");
            status(Lang.F("Largeur de bande : {0}", "Bandwidth: {0}", bwText(hz)));
        }
        updateDisplays();
    }

    // ------------------------------------------------------------------ minuterie d'arrêt (appui long sur Muet)

    long sleepAt, recStopAt;      // heures d'arrêt (System.currentTimeMillis), 0 = pas de minuterie
    boolean sleepFired;           // le son est coupé ; l'application se ferme dès la fin du REC éventuel

    final Runnable sleepRun = new Runnable() { public void run() { sleepFire(); } };
    final Runnable recStopRun = new Runnable() { public void run() { recStopTimer(); } };
    final Runnable sleepTick = new Runnable() {
        public void run() {
            if (sleepAt == 0 || sleepFired) return;
            updateButtons();
            h.postDelayed(this, 1000);
        }
    };

    String sleepText() {
        long s = Math.max(0, (sleepAt - System.currentTimeMillis() + 999) / 1000);
        return s >= 60 ? Lang.F("{0} min", "{0} min", (s + 59) / 60) : s + " s";
    }

    void onMuteLongPress() {
        if (blocked()) return;
        java.util.ArrayList<String> items = new java.util.ArrayList<String>();
        final boolean armed = sleepAt != 0 || recStopAt != 0;
        if (armed) items.add(Lang.T("Annuler la minuterie", "Cancel the timer"));
        items.add(Lang.F("{0} minutes", "{0} minutes", 15));
        items.add(Lang.F("{0} minutes", "{0} minutes", 30));
        items.add(Lang.F("{0} minutes", "{0} minutes", 45));
        items.add(Lang.F("{0} minutes", "{0} minutes", 60));
        items.add(Lang.T("Autre durée (en minutes)…", "Other duration (minutes)…"));
        final int off = armed ? 1 : 0;
        Ui.choose(this, Lang.T("Couper le son et fermer l'application dans :", "Cut the sound and close the application in:"), items.toArray(new String[0]), new Ui.Index() {
            public void chose(int i) {
                if (armed && i == 0) { sleepCancel(); return; }
                int k = i - off;
                if (k < 4) sleepAsk(new int[] { 15, 30, 45, 60 }[k]);
                else Ui.input(MainActivity.this, Lang.T("Durée en minutes", "Duration in minutes"), "min", "", new Ui.Text() {
                    public void got(String t) {
                        int m = 0;
                        try { m = Integer.parseInt(t.trim()); } catch (NumberFormatException e) { }
                        if (m < 1 || m > 1440) { Ui.toast(MainActivity.this, Lang.T("Durée invalide (1 à 1440 minutes).", "Invalid duration (1 to 1440 minutes).")); return; }
                        sleepAsk(m);
                    }
                });
            }
        });
    }

    // Minuterie de m minutes. Si un REC est en cours, demande quand l'arrêter (il peut durer après la coupure du son).
    void sleepAsk(final int m) {
        if (!recording) { sleepSet(m, 0); return; }
        final int[] rec = { m, m + 15, m + 30, m + 60 };
        String[] items = new String[5];
        items[0] = Lang.F("En même temps que le son ({0} min)", "At the same time as the sound ({0} min)", rec[0]);
        for (int i = 1; i < 4; i++) items[i] = Lang.F("{0} min (après la coupure du son)", "{0} min (after the sound is cut)", rec[i]);
        items[4] = Lang.T("Autre durée (en minutes)…", "Other duration (minutes)…");
        Ui.choose(this, Lang.T("Le REC en cours s'arrête dans :", "The recording in progress stops in:"), items, new Ui.Index() {
            public void chose(int i) {
                if (i < 4) { sleepSet(m, rec[i]); return; }
                Ui.input(MainActivity.this, Lang.T("Arrêt du REC dans (minutes)", "Stop the recording in (minutes)"), "min", "", new Ui.Text() {
                    public void got(String t) {
                        int r = 0;
                        try { r = Integer.parseInt(t.trim()); } catch (NumberFormatException e) { }
                        if (r < 1 || r > 1440) { Ui.toast(MainActivity.this, Lang.T("Durée invalide (1 à 1440 minutes).", "Invalid duration (1 to 1440 minutes).")); return; }
                        sleepSet(m, r);
                    }
                });
            }
        });
    }

    void sleepSet(int m, int recMin) {
        sleepCancel();
        long now = System.currentTimeMillis();
        sleepAt = now + m * 60000L;
        h.postDelayed(sleepRun, m * 60000L);
        h.postDelayed(sleepTick, 1000);
        String s = Lang.F("Le son sera coupé et l'application fermée dans {0} min", "The sound will be cut and the application closed in {0} min", m);
        if (recMin > 0) {
            recStopAt = now + recMin * 60000L;
            h.postDelayed(recStopRun, recMin * 60000L);
            s += " " + Lang.F("(REC arrêté dans {0} min)", "(recording stopped in {0} min)", recMin);
        }
        status(s);
        updateButtons();
    }

    void sleepCancel() {
        boolean was = sleepAt != 0 || recStopAt != 0;
        h.removeCallbacks(sleepRun);
        h.removeCallbacks(recStopRun);
        h.removeCallbacks(sleepTick);
        sleepAt = 0;
        recStopAt = 0;
        sleepFired = false;
        if (was) status(Lang.T("Minuterie annulée", "Timer cancelled"));
        updateButtons();
    }

    // Fin de la minuterie : le son du haut-parleur est coupé (le REC enregistre le son brut, il continue). L'application se ferme
    // tout de suite, ou à la fin du REC (heure choisie, ou arrêt immédiat si aucune heure n'a été donnée).
    void sleepFire() {
        sleepFired = true;
        sleepAt = 0;
        muted = true;
        applyVolume();
        updateButtons();
        if (!recording && !recPending) { quitApp(); return; }
        if (recStopAt > System.currentTimeMillis()) {
            status(Lang.T("Son coupé ; l'application se fermera à la fin du REC", "Sound cut; the application will close when the recording ends"));
            return;
        }
        recStopTimer();
    }

    void recStopTimer() {
        recStopAt = 0;
        if (recording && !recPending) onRecButton();
        else if (!recording && !recPending && sleepFired) quitApp();
    }

    void quitApp() {
        st.freqHz = freq;
        st.mode = mode;
        st.save();
        finishAndRemoveTask();
    }

    // ------------------------------------------------------------------ enregistrement MP3

    String recTime() {
        long s = (System.currentTimeMillis() - recStart) / 1000;
        return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
    }

    final Runnable recTick = new Runnable() {
        public void run() {
            if (!recording) return;
            updateButtons();
            h.postDelayed(this, 1000);
        }
    };

    // Appui long sur REC : ouvre le dossier Musique/PhoneSDRLink dans l'application Fichiers du téléphone ; à défaut, le dernier
    // enregistrement dans un lecteur audio.
    void openRecordings() {
        try {
            android.net.Uri dir = android.provider.DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Music/PhoneSDRLink");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(dir, android.provider.DocumentsContract.Document.MIME_TYPE_DIR);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return;
        } catch (Exception e) { }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setType("audio/*");
            i.setDataAndType(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "vnd.android.cursor.dir/audio");
            startActivity(i);
        } catch (Exception e) {
            status(Lang.T("Les enregistrements sont dans Musique/PhoneSDRLink", "The recordings are in Music/PhoneSDRLink"));
        }
    }

    // Démarre (ou reprend, sur une nouvelle page) la capture du son dans la page ; recRate : cadence du premier morceau, pour
    // que tout le fichier garde la même fréquence d'échantillonnage.
    boolean recResume;
    int recTries, recRate;

    void recStartJs() throws Exception {
        java.io.InputStream in = getAssets().open("lame.min.js");
        String src = Store.readAll(in);
        in.close();
        web.evaluateJavascript("(function(){try{__sdrlink.recStart(" + org.json.JSONObject.quote(src) + ", 128, " + recRate + ")}catch(e){PhoneSdr.post(\"rec|err|\"+e)}})()", null);
    }

    final Runnable recResumeRun = new Runnable() {
        public void run() {
            if (!recResume || !recording || recPending) return;
            try { recStartJs(); } catch (Exception e) { recResume = false; recFinish(); }
        }
    };

    void onRecButton() {
        if (recPending) return;
        if (recording) {
            recPending = true;   // la fin arrive par le message « mp3end » une fois le dernier morceau encodé
            js("if(window.__sdrlink)__sdrlink.recStop();");
            h.postDelayed(new Runnable() { public void run() { if (recPending) recFinish(); } }, 4000);
            return;
        }
        if (sdrKind.isEmpty()) { status(Lang.T("Enregistrement impossible : aucun son", "Cannot record: no audio")); return; }
        try {
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date());
            recName = "PhoneSDRLink_" + stamp + "_" + Memory.kHz(freq) + "kHz_" + modeLabel(mode) + ".mp3";
            recRate = 0;
            openRecFile();
            recStartJs();
        } catch (Exception e) {
            status(Lang.T("Enregistrement impossible : ", "Cannot record: ") + e.getMessage());
            recClose(true);
        }
    }

    // Fichier de sortie : dossier Musique/PhoneSDRLink (MediaStore), ou dossier de l'application avant Android 10.
    void openRecFile() throws Exception {
        recUri = null;
        recFile = null;
        if (Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, recName);
            v.put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
            v.put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/PhoneSDRLink");
            v.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1);
            recUri = getContentResolver().insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v);
            if (recUri == null) throw new Exception("MediaStore");
            recOut = getContentResolver().openOutputStream(recUri);
        } else {
            java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
            dir.mkdirs();
            recFile = new java.io.File(dir, recName);
            recOut = new java.io.FileOutputStream(recFile);
        }
    }

    void onRecMessage(String[] p) {
        if (recResume && recording) {
            if (p.length >= 2 && p[1].equals("ok")) { recResume = false; status(Lang.T("Le REC continue sur le nouveau WebSDR", "The recording continues on the new WebSDR")); }
            else if (++recTries < 20) h.postDelayed(recResumeRun, 2000);   // le son n'est pas encore démarré : on réessaie
            else { recResume = false; recFinish(); }
            return;
        }
        if (p.length >= 2 && p[1].equals("ok")) {
            try { recRate = Integer.parseInt(p[2]); } catch (Exception e) { recRate = 0; }
            recording = true;
            recStart = System.currentTimeMillis();
            status(Lang.F("Enregistrement du son de « {0} » (appuyez sur REC pour arrêter)", "Recording the sound of \"{0}\" (click REC to stop)", currentTitle()));
            updateButtons();
            h.postDelayed(recTick, 1000);
        } else {
            status(Lang.T("Enregistrement impossible : aucun son", "Cannot record: no audio"));
            recClose(true);
        }
    }

    long recBytes;

    void recWrite(String b64) {
        if (recOut == null) return;
        try {
            byte[] d = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            recOut.write(d);
            recBytes += d.length;
        } catch (Exception e) { recFinish(); }
    }

    // Fin : le dernier morceau est arrivé (ou la page a changé : ce qui a été reçu est gardé).
    void recFinish() {
        if (!recording && !recPending && recOut == null) return;
        boolean empty = recBytes == 0;
        String name = recName;
        recClose(empty);
        recording = false;
        recPending = false;
        recResume = false;
        updateButtons();
        status(empty ? Lang.T("Enregistrement vide : rien à sauvegarder", "Empty recording: nothing to save")
            : Lang.F("Enregistrement sauvegardé : « {0} » (Musique/PhoneSDRLink)", "Recording saved: \"{0}\"", name));
        if (sleepFired) quitApp();
    }

    void recClose(boolean discard) {
        try { if (recOut != null) recOut.close(); } catch (Exception e) { }
        recOut = null;
        if (recUri != null) {
            if (discard) { try { getContentResolver().delete(recUri, null, null); } catch (Exception e) { } }
            else if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues v = new android.content.ContentValues();
                v.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0);
                try { getContentResolver().update(recUri, v, null, null); } catch (Exception e) { }
            }
        } else if (recFile != null && discard) recFile.delete();
        recUri = null;
        recFile = null;
        recBytes = 0;
        if (discard) { recording = false; recPending = false; updateButtons(); }
    }
    // ------------------------------------------------------------------ fichiers (Import / Export)

    void pickFile(boolean create, String name, FileCb cb) {
        fileCb = cb;
        Intent i = new Intent(create ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(create ? "text/plain" : "*/*");
        if (create) i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, 7);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7 && fileCb != null) {
            FileCb cb = fileCb;
            fileCb = null;
            if (res == RESULT_OK && data != null && data.getData() != null) cb.done(data.getData());
        }
    }

    // ------------------------------------------------------------------ premier démarrage, mises à jour

    boolean online() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) { return false; }
    }

    // Premier démarrage : la recherche des WebSDR s'ouvre d'elle-même (une seule fois ; sans Internet, elle est retentée au démarrage suivant).
    void firstRunSearch() {
        if (st.firstSearchDone || !online()) return;
        st.firstSearchDone = true;
        st.save();
        DlgWebSdr.search(this);
    }

    static int[] versionParts(String v) {
        String[] p = v.split("\\.");
        int[] r = new int[4];
        for (int i = 0; i < 4 && i < p.length; i++) r[i] = Store.toInt(p[i].replaceAll("[^0-9]", ""), 0);
        return r;
    }

    static boolean isNewer(String tag, String cur) {
        int[] a = versionParts(tag), b = versionParts(cur);
        for (int i = 0; i < 4; i++) if (a[i] != b[i]) return a[i] > b[i];
        return false;
    }

    String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1.0"; }
    }

    // Vérifie la dernière release GitHub. manual : affiche toujours un résultat (sans quitter la fenêtre en cours).
    // Au démarrage, une mise à jour trouvée ouvre une alerte qui propose de l'installer (téléchargement de l'APK de la release,
    // puis installation par Android, qui demande confirmation) ; sans APK dans la release, la page GitHub s'ouvre.
    String apkUrl;

    void checkUpdate(final boolean manual) {
        new Thread(new Runnable() {
            public void run() {
                String tag = null, url = null, apk = null;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                    c.setRequestProperty("User-Agent", "PhoneSDRLink/" + versionName());
                    c.setRequestProperty("Accept", "application/vnd.github+json");
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    String json = Store.readAll(c.getInputStream());
                    Matcher m = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([0-9][0-9.]*)\"").matcher(json);
                    if (m.find()) tag = m.group(1);
                    Matcher u = Pattern.compile("\"html_url\"\\s*:\\s*\"(https://github\\.com/[^\"]+/releases/tag/[^\"]+)\"").matcher(json);
                    url = u.find() ? u.group(1) : "https://github.com/F5pbgLudovic/PhoneSDRLink/releases/latest";
                    Matcher a = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"(https://[^\"]+\\.apk)\"").matcher(json);
                    if (a.find()) apk = a.group(1);
                } catch (Exception e) { }
                final String ftag = tag, furl = url, fapk = apk;
                h.post(new Runnable() {
                    public void run() {
                        if (ftag == null) { if (manual) Ui.message(MainActivity.this, Lang.T("PhoneSDRLink : mises à jour", "PhoneSDRLink: updates"), Lang.T("Impossible de joindre GitHub pour vérifier les mises à jour.", "Could not reach GitHub to check for updates.")); return; }
                        if (!isNewer(ftag, versionName())) {
                            if (manual) Ui.message(MainActivity.this, Lang.T("PhoneSDRLink : mises à jour", "PhoneSDRLink: updates"),
                                Lang.T("Vous utilisez la dernière version.", "You are using the latest version.") + "\n" + Lang.F("Version utilisée : V{0}", "Version in use: V{0}", versionName()));
                            return;
                        }
                        updateUrl = furl;
                        apkUrl = fapk;
                        status(Lang.F("⚠ Mise à jour V{0} disponible : cliquez pour la télécharger", "⚠ Update V{0} available: click to download it", ftag));
                        offerUpdate(ftag);
                    }
                });
            }
        }).start();
    }

    void offerUpdate(String tag) {
        String msg = Lang.F("La version V{0} de PhoneSDRLink est disponible (vous utilisez la V{1}).", "PhoneSDRLink V{0} is available (you are using V{1}).", tag, versionName());
        if (apkUrl != null) {
            Ui.confirm(this, "⚠ " + Lang.T("Mise à jour disponible", "Update available"),
                msg + "\n\n" + Lang.T("Télécharger et installer maintenant ? Android demandera une confirmation.", "Download and install now? Android will ask for confirmation."),
                Lang.T("Installer", "Install"), Lang.T("Plus tard", "Later"), new Ui.Yes() { public void yes() { downloadAndInstall(); } });
        } else {
            Ui.confirm(this, "⚠ " + Lang.T("Mise à jour disponible", "Update available"),
                msg + "\n\n" + Lang.T("Ouvrir la page de téléchargement ?", "Open the download page?"),
                Lang.T("Ouvrir", "Open"), Lang.T("Plus tard", "Later"), new Ui.Yes() { public void yes() { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))); } catch (Exception e) { } } });
        }
    }

    void downloadAndInstall() {
        final String src = apkUrl;
        status(Lang.T("Téléchargement de la mise à jour…", "Downloading the update…"));
        new Thread(new Runnable() {
            public void run() {
                String err = null;
                try {
                    java.io.File f = new java.io.File(getCacheDir(), "update.apk");
                    HttpURLConnection c = (HttpURLConnection) new URL(src).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(20000);
                    c.setInstanceFollowRedirects(true);
                    InputStream in = c.getInputStream();
                    java.io.FileOutputStream o = new java.io.FileOutputStream(f);
                    byte[] b = new byte[65536];
                    int n;
                    while ((n = in.read(b)) > 0) o.write(b, 0, n);
                    o.close();
                    in.close();
                    if (f.length() < 100000) throw new Exception("APK");
                    installApk(f);
                } catch (Exception e) { err = String.valueOf(e.getMessage()); }
                final String ferr = err;
                if (ferr != null) h.post(new Runnable() { public void run() {
                    status(Lang.T("Mise à jour impossible : ouverture de la page de téléchargement", "Update failed: opening the download page"));
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))); } catch (Exception e) { }
                } });
            }
        }).start();
    }

    void installApk(java.io.File f) throws Exception {
        final android.content.pm.PackageInstaller pi = getPackageManager().getPackageInstaller();
        int id = pi.createSession(new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL));
        android.content.pm.PackageInstaller.Session s = pi.openSession(id);
        java.io.OutputStream o = s.openWrite("update", 0, f.length());
        InputStream in = new java.io.FileInputStream(f);
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) > 0) o.write(b, 0, n);
        s.fsync(o);
        in.close();
        o.close();
        android.content.BroadcastReceiver r = new android.content.BroadcastReceiver() {
            public void onReceive(Context c, Intent i) {
                int st2 = i.getIntExtra(android.content.pm.PackageInstaller.EXTRA_STATUS, -1);
                if (st2 == android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(confirm); }
                } else if (st2 != android.content.pm.PackageInstaller.STATUS_SUCCESS) {
                    status(Lang.T("Installation de la mise à jour annulée ou impossible", "Update installation cancelled or failed"));
                    try { unregisterReceiver(this); } catch (Exception e) { }
                }
            }
        };
        String action = getPackageName() + ".INSTALL_DONE";
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, new android.content.IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, new android.content.IntentFilter(action));
        Intent ii = new Intent(action).setPackage(getPackageName());
        int fl = android.app.PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? android.app.PendingIntent.FLAG_MUTABLE : 0);
        s.commit(android.app.PendingIntent.getBroadcast(this, id, ii, fl).getIntentSender());
    }
}
