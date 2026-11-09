package fr.f5pbg.phonesdrlink;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
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
import android.widget.ImageView;
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
        cleanTemp(false);
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
        applyHeadset();
        histSchedule();
        if (getIntent() != null && "clear".equals(getIntent().getStringExtra("histfill"))) { st.hist.clear(); st.save(); }
        else if (getIntent() != null && getIntent().getStringExtra("histfill") != null) { for (int i = 0; i < 20; i++) st.addHist(3500000L + i * 100000L, i % 2 == 0 ? "USB" : "LSB", i % 3 == 0 ? DEC_FT8 : 0); st.save(); }   // essai : remplit la liste Mem
        if (getIntent() != null && getIntent().getStringExtra("radewav") != null) radeSelfTest(getIntent().getStringExtra("radewav"));
        if (getIntent() != null && getIntent().getStringExtra("radeeng") != null) radeEngineTest(getIntent().getStringExtra("radeeng"));
        if (getIntent() != null && getIntent().getStringExtra("radev2test") != null) radeV2Loop(getIntent().getStringExtra("radev2test"));
        if (getIntent() != null && getIntent().getStringExtra("dectest") != null) decSelfTest(getIntent().getStringExtra("dectest"));
        if (getIntent() != null && getIntent().getStringExtra("decwav") != null) decWavTest(getIntent().getStringExtra("decwav"));
        if (getIntent() != null && getIntent().getStringExtra("olitest") != null) oliviaLoop(getIntent().getStringExtra("olitest"));
        setupWeb();
        refreshAll();
        if (Ui.reopenColors) { Ui.reopenColors = false; h.post(new Runnable() { public void run() { DlgSettings.showColors(MainActivity.this); } }); }
        String first = getIntent() != null ? getIntent().getStringExtra("url") : null;
        web.loadUrl(first != null ? first : st.lastUrl);
        final Intent shareIntent = getIntent();
        h.post(new Runnable() { public void run() { handleShareIntent(shareIntent); } });   // lien de partage : question posée devant la page habituelle
        h.postDelayed(new Runnable() { public void run() { firstRunSearch(); if (st.checkUpdates) checkUpdate(false); } }, 1200);
        h.postDelayed(callWatch, 1000);
        if (st.freedvAuto) h.postDelayed(new Runnable() { public void run() { maybeStartAuto(); } }, 4000);   // FreeDV auto : démarre avec l'application
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
        if (mediaSession != null) { try { mediaSession.release(); } catch (Exception e) { } }
        decoderStop(false);
        if (isFinishing()) cleanTemp(true);
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
        handleShareIntent(i);
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
        btnSet = Ui.button(this, "SET", new View.OnClickListener() { public void onClick(View v) { if (blocked()) return; if (decMode != 0 && !radeAuto) DlgDecoder.show(MainActivity.this); else DlgSettings.show(MainActivity.this); } });
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
        btnLock.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { askQuit(); return true; } });
        for (Button x : new Button[] { btnWeb, btnSet, btnRec, btnMute, btnLock }) { x.setTextSize(13); x.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0); }
        r1.addView(btnMute, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f, this, 2));
        r1.addView(btnLock, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.9f, this, 2));
        controls.addView(r1);

        // Ligne 2 : fréquence (appui = clavier) et mode
        LinearLayout r2 = Ui.row(this);
        tvFreq = new TextView(this) {
            // Nom de l'application en tout petit sous la fréquence, centré, sans changer la taille de la case ; s'il ne rentre pas, rien n'est écrit.
            // Quand un mode numérique est actif, son nom s'écrit de la même taille dans le coin droit en bas (couleur réglable dans SET › Couleurs).
            final android.graphics.Paint tiny = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            void drawDecName(android.graphics.Canvas c) {
                if (decMode == 0) return;
                String s = decName(decMode);
                float dm = getResources().getDisplayMetrics().scaledDensity, dp = getResources().getDisplayMetrics().density;
                tiny.setTypeface(Typeface.DEFAULT_BOLD);
                tiny.setColor(Ui.decCol());
                tiny.setTextSize(7 * dm);
                float base = getHeight() - 2 * dp - tiny.descent();
                c.drawText(s, getScrollX() + getWidth() - 6 * dp - tiny.measureText(s), getScrollY() + base, tiny);
            }
            @Override
            protected void onDraw(android.graphics.Canvas c) {
                super.onDraw(c);
                android.text.Layout l = getLayout();
                if (l == null || getText().length() == 0) return;
                drawDecName(c);
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
        zoom.addView(shareButton());
        zoom.addView(initButton());
        zoom.addView(plus);
        zoom.addView(minus);
        zoomBox = zoom;
        decOverlayView = new TextView(this);
        decOverlayView.setTypeface(android.graphics.Typeface.MONOSPACE);
        decOverlayView.setTextSize(10);
        decOverlayView.setBackgroundColor(0xB0000000);
        decOverlayView.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), Ui.dp(this, 4), Ui.dp(this, 2));
        decOverlayView.setVisibility(View.GONE);
        decOverlayView.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (!blocked()) DlgDecoder.show(MainActivity.this); } });
        sstvOverlay = new ImageView(this);
        sstvOverlay.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sstvOverlay.setBackgroundColor(0xB0000000);
        sstvOverlay.setVisibility(View.GONE);
        sstvOverlay.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (!blocked()) DlgDecoder.show(MainActivity.this); } });
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

    // Appui de 2 secondes sans lâcher sur un bouton : exécute r (le clic normal est alors ignoré).
    void holdThen(final View v, final Runnable r) {
        final boolean[] fired = { false };
        final Runnable fire = new Runnable() { public void run() { fired[0] = true; v.setPressed(false); r.run(); } };
        v.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            public boolean onTouch(View b, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: fired[0] = false; x0 = e.getX(); y0 = e.getY(); h.postDelayed(fire, 2000); break;
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

    // Les trois boutons INIT, + et − : dans l'un des quatre coins de la zone WebSDR, en colonne ou en ligne (st.zoomPos : voir Store).
    void placeZoom() {
        if (zoomBox == null) return;
        if (zoomBox.getParent() != null) ((ViewGroup) zoomBox.getParent()).removeView(zoomBox);
        int corner = st.zoomPos & 3;
        boolean horiz = (st.zoomPos & 4) != 0, left = corner == 1 || corner == 3, top = corner >= 2;
        zoomBox.setOrientation(horiz ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        for (int i = 0; i < zoomBox.getChildCount(); i++) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) zoomBox.getChildAt(i).getLayoutParams();
            lp.setMargins(horiz ? Ui.dp(this, 4) : 0, horiz ? 0 : Ui.dp(this, 4), 0, 0);
            zoomBox.getChildAt(i).setLayoutParams(lp);
        }
        FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, (top ? Gravity.TOP : Gravity.BOTTOM) | (left ? Gravity.START : Gravity.END));
        int m = Ui.dp(this, 10);
        zp.setMargins(left ? m : 0, top ? m : 0, left ? 0 : m, top ? 0 : (st.barBottom ? Ui.dp(this, 28) : m));
        webFrame.addView(zoomBox, zp);
        placeDecOverlay();
    }

    void chooseZoomSide() {
        String[] c = { Lang.T("Bas à droite", "Bottom right"), Lang.T("Bas à gauche", "Bottom left"), Lang.T("Haut à droite", "Top right"), Lang.T("Haut à gauche", "Top left") };
        String v = Lang.T("vertical", "vertical"), hz = Lang.T("horizontal", "horizontal");
        String[] items = new String[8];
        for (int i = 0; i < 4; i++) { items[i] = c[i] + " — " + v + (i == 0 ? " " + Lang.T("(position d'origine)", "(original position)") : ""); items[4 + i] = c[i] + " — " + hz; }
        Ui.choose(this, Lang.T("Boutons INIT + −", "INIT + − buttons"), items, new Ui.Index() {
            public void chose(int i) { st.zoomPos = i; st.zoomLeft = (i & 3) == 1 || (i & 3) == 3; st.save(); placeZoom(); }
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
        Ui.choose(this, Lang.T("Barre de réglage", "Tuning bar"), new String[] { Lang.T("Haut de l'écran", "Top of the screen"), Lang.T("Bas de l'écran", "Bottom of the screen") }, new Ui.Index() {
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
                st.putPos(zoomKey(), 1000);   // INIT remet l'écran d'origine : le zoom retenu est oublié
                st.putPos(ownKey(), Integer.MAX_VALUE);   // et le zoom propre du WebSDR retenu aussi
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

    // Rond « partage » (au-dessus de INIT) : partage de l'écoute en cours par la fenêtre de partage d'Android. Actif même verrouillé.
    View shareButton() {
        android.widget.ImageButton b = new android.widget.ImageButton(this);
        b.setImageResource(android.R.drawable.ic_menu_share);
        b.setColorFilter(Color.WHITE);
        b.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        int pad = Ui.dp(this, 10);
        b.setPadding(pad, pad, pad, pad);
        b.setBackground(Ui.round(0xAA000000, 24, this));
        b.setContentDescription(Lang.T("Partager la fréquence", "Share the frequency"));
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { shareCurrent(); } });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        p.setMargins(0, Ui.dp(this, 4), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    // Texte partagé : écoute lisible, lien PhoneSDRLink, et adresse du WebSDR pour ceux qui n'ont pas l'application.
    void shareCurrent() {
        String u = currentUrl();
        if (u == null || !ShareLink.isWeb(u)) { status(Lang.T("Aucun WebSDR à partager", "No WebSDR to share")); return; }
        String title = currentTitle().trim();
        if (title.isEmpty()) title = Uri.parse(u).getHost();
        String link = ShareLink.build(u, freq, mode, bwHz, decMode != 0 ? decName(decMode) : "");
        String text = "📻 " + Lang.F("{0} sur {1}", "{0} on {1}", listenText(freq, mode, decMode), title)
            + "\nPhoneSDRLink : " + link
            + "\n" + Lang.F("Navigateur : {0}", "Browser: {0}", u);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, Lang.T("Partager la fréquence", "Share the frequency")));
    }

    // Verrou : tant qu'il est rouge, aucune commande ne change rien (seuls Muet et Verrou restent actifs).
    boolean blocked() {
        if (locked) Ui.toast(this, Lang.T("🔒 Verrouillé : appuyez sur Verrou pour modifier", "🔒 Locked: press Lock to change"));
        return locked;
    }

    Button zoomButton(String text, final boolean in) {
        Button b = Ui.button(this, text, new View.OnClickListener() {
            public void onClick(View v) {
                if (blocked()) return;
                cancelScaleGuard();
                // D'abord le zoom du WebSDR lui-même (un palier, comme ses propres boutons + et −) ; s'il n'en a pas de connu, on garde l'ancien
                // comportement : zoom de la page puis remise à l'échelle du spectre.
                web.evaluateJavascript("(function(){try{return window.__sdrlink&&__sdrlink.zoomStep(" + (in ? 1 : -1) + ")?1:0}catch(e){return 0}})()", new android.webkit.ValueCallback<String>() {
                    public void onReceiveValue(String r) {
                        if ("1".equals(r)) {   // zoom propre du WebSDR fait : on note le nouveau palier (même si la page vient de s'ouvrir)
                            h.postDelayed(new Runnable() { public void run() { ownForce = true; js("if(window.__sdrlink)__sdrlink.zoomReport();"); } }, 1200);
                            return;
                        }
                        if (in) web.zoomIn(); else web.zoomOut();
                        h.postDelayed(new Runnable() { public void run() { applySpectrumZoom(); } }, 700);
                        h.removeCallbacks(recordZoomRun);
                        h.postDelayed(recordZoomRun, 1500);
                    }
                });
            }
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
        histNote();
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
                if (e.getPointerCount() > 1) { cancelScaleGuard(); pinched = true; }   // zoom à la main
                if (pinched && e.getActionMasked() == android.view.MotionEvent.ACTION_UP) { pinched = false; h.removeCallbacks(recordZoomRun); h.postDelayed(recordZoomRun, 1500); }
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

    // Script du pont : précédé de la mémoire tampon audio supplémentaire demandée dans SET (ms).
    String bridgeSrc() { return "window.__sdrlinkBufMs=" + st.bufMs + ";" + bridgeScript; }

    // Nouvelle mémoire tampon : le script est réinstallé et la page rechargée (le tampon se choisit à la création du contexte audio).
    void applyBuffer() {
        installBridgeScripts();
        status(Lang.F("Mémoire tampon audio : {0} ms ; le WebSDR est rechargé", "Audio buffer: {0} ms; reloading the WebSDR", st.bufMs));
        sdrKind = "";
        cancelScaleGuard();
        initReset = true;
        String cu = web.getUrl();
        if (cu != null && !cu.isEmpty()) web.loadUrl(cu); else web.reload();
    }

    void installBridgeScripts() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        if (bridgeHandle != null) bridgeHandle.remove();
        bridgeHandle = WebViewCompat.addDocumentStartJavaScript(web, bridgeSrc(), Collections.singleton("*"));
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
        web.evaluateJavascript(bridgeSrc(), null);
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

    // Bague / télécommande de doigt « JX-11 » (vue comme un petit écran tactile et un clavier de touches multimédia) : un balayage vers le
    // haut monte la fréquence, vers le bas la baisse (un pas du réglage en cours par balayage) ; un appui (touche de volume, simple toucher)
    // arrête net la roue, comme le doigt posé sur la réglette. Ces événements sont consommés ici : ils ne touchent ni la page ni le volume.
    static boolean isRing(android.view.InputEvent e) {
        android.view.InputDevice d = e.getDevice();
        return d != null && d.getName() != null && d.getName().startsWith("JX-11");
    }
    float ringY0;

    // Listes Mode (clic droit de la bague) et Largeur de bande (clic gauche) : un cadre s'affiche sur l'écran, la molette fait défiler la liste,
    // le choix est appliqué au bout de 3 secondes sans mouvement (ou à un second clic sur le même bouton).
    int ringPick;              // 0 aucune liste, 1 mode, 2 largeur de bande
    int ringIdx;
    String[] ringItems = new String[0];
    int[] ringBw = new int[0];
    TextView ringView;
    final Runnable ringApplyRun = new Runnable() { public void run() { ringApply(); } };

    void ringOpen(int kind) {
        if (ringPick == kind) { ringApply(); return; }
        if (ringPick != 0) ringApply();
        if (blocked()) return;
        if (kind == 2 && sdrKind.isEmpty()) { status(Lang.T("Largeur de bande impossible : aucun WebSDR actif", "Cannot set the bandwidth: no active WebSDR")); return; }
        if (bar != null) bar.stopFling();
        if (kind == 1) {
            ringItems = new String[MODES.length];
            ringIdx = 0;
            for (int i = 0; i < MODES.length; i++) { ringItems[i] = modeLabel(MODES[i]); if (MODES[i].equals(mode)) ringIdx = i; }
        } else {
            int[] v = bwChoices(mode);
            ringBw = new int[v.length + 1];
            ringItems = new String[v.length + 1];
            ringBw[0] = 0;
            ringItems[0] = Lang.T("Filtre d'origine du WebSDR", "WebSDR default filter");
            ringIdx = bwHz == 0 ? 0 : -1;
            int best = 0, bestD = Integer.MAX_VALUE;
            for (int i = 0; i < v.length; i++) { ringBw[i + 1] = v[i]; ringItems[i + 1] = bwText(v[i]); int d = Math.abs(v[i] - bwHz); if (d < bestD) { bestD = d; best = i + 1; } }
            if (ringIdx < 0) ringIdx = best;
        }
        ringPick = kind;
        ringShow();
    }

    void ringMove(int d) {
        if (ringPick == 0 || ringItems.length == 0) return;
        ringIdx = Math.max(0, Math.min(ringItems.length - 1, ringIdx + d));
        ringShow();
    }

    void ringShow() {
        if (webFrame == null) return;
        if (ringView == null) {
            ringView = new TextView(this);
            ringView.setTextSize(20);
            ringView.setTextColor(0xFFFFFFFF);
            ringView.setBackgroundColor(0xE6000000);
            ringView.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
            ringView.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            webFrame.addView(ringView, lp);
        }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        String title = ringPick == 1 ? Lang.T("Mode", "Mode") : Lang.T("Largeur de bande", "Bandwidth");
        sb.append(title).append('\n');
        sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, title.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        for (int i = 0; i < ringItems.length; i++) {
            int s0 = sb.length();
            sb.append("  ").append(ringItems[i]).append("  ");
            if (i == ringIdx) {
                sb.setSpan(new android.text.style.BackgroundColorSpan(Ui.accent()), s0, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF000000), s0, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s0, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i < ringItems.length - 1) sb.append('\n');
        }
        ringView.setText(sb);
        ringView.setVisibility(View.VISIBLE);
        h.removeCallbacks(ringApplyRun);
        h.postDelayed(ringApplyRun, 3000);
    }

    void ringApply() {
        h.removeCallbacks(ringApplyRun);
        int kind = ringPick;
        ringPick = 0;
        if (ringView != null) ringView.setVisibility(View.GONE);
        if (kind == 1 && ringIdx >= 0 && ringIdx < MODES.length && !MODES[ringIdx].equals(mode)) setMode(MODES[ringIdx]);
        else if (kind == 2 && ringIdx >= 0 && ringIdx < ringBw.length && ringBw[ringIdx] != bwHz) setBandwidth(ringBw[ringIdx]);
    }
    int ringDir;
    long ringT;
    static boolean ringPresent() {
        for (int id : android.view.InputDevice.getDeviceIds()) { android.view.InputDevice d = android.view.InputDevice.getDevice(id); if (d != null && d.getName() != null && d.getName().startsWith("JX-11")) return true; }
        return false;
    }
    // Le système transmet les gestes de la bague comme des touchers « virtuels » (identité -1, pas « JX-11 ») dont l'abscisse est toujours
    // la même : milieu de la plage 0..1000 de la bague, soit 500 / 1001 de la largeur de l'écran. Un vrai doigt n'a jamais l'identité -1.
    boolean isRingTouch(android.view.MotionEvent ev) {
        if (isRing(ev)) return true;
        if (ev.getDeviceId() != -1) return false;
        float cx = 500f * getResources().getDisplayMetrics().widthPixels / 1001f;
        return Math.abs(ev.getX() - cx) < 0.05f;
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (isRingTouch(ev)) {
            int a = ev.getActionMasked();
            if (a == android.view.MotionEvent.ACTION_DOWN) ringY0 = ev.getY();
            else if (a == android.view.MotionEvent.ACTION_UP) {
                float dy = ev.getY() - ringY0;
                if (bar != null && Math.abs(dy) >= Ui.dp(this, 30)) {   // (un simple toucher ne fait plus rien)
                    int dir = dy < 0 ? 1 : -1;
                    long now = android.os.SystemClock.uptimeMillis();
                    if (ringPick != 0) ringMove(-dir);                                   // liste Mode / Largeur affichée : le balayage la fait défiler
                    else if (bar.flinging() && bar.flingDir() != dir) { bar.stopFling(); ringT = 0; ringDir = 0; return true; }   // sens inverse : arrêt net
                    else {
                        // Un balayage isolé = un pas du réglage en cours. Des balayages rapprochés dans le même sens (moins de st.ringMs, 450 ms par défaut, réglable dans SET) lancent la roue
                        // comme un geste rapide sur la réglette, et chaque balayage de plus l'accélère.
                        if (bar.flinging() || (dir == ringDir && now - ringT < st.ringMs)) bar.kick(dir, st.ringSpeed);
                        else tuneBySteps(dir, 1000);
                    }
                    ringDir = dir;
                    ringT = now;
                }
            }
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent ev) {
        if ((isRing(ev) && ev.getKeyCode() != android.view.KeyEvent.KEYCODE_HOME) || (ev.getDeviceId() == -1 && ringPresent() && (ev.getKeyCode() == android.view.KeyEvent.KEYCODE_VOLUME_UP || ev.getKeyCode() == android.view.KeyEvent.KEYCODE_VOLUME_DOWN))) {
            // Touches de la bague : volume + = clic droit (liste des modes), volume - = clic gauche (liste des largeurs de bande).
            if (ev.getAction() == android.view.KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                if (ev.getKeyCode() == android.view.KeyEvent.KEYCODE_VOLUME_UP) ringOpen(1);
                else if (ev.getKeyCode() == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) ringOpen(2);
            }
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }

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
        boolean m = muted || callMute || focusMute || (isRade(decMode) && radeVoice);
        js("if(window.__sdrlink)__sdrlink.setVolume(" + (m ? 0 : 1) + ");");
        if (rade != null) rade.setMuted(muted || callMute || focusMute);   // la parole FreeDV passe par une AudioTrack : Muet doit aussi la couper
    }

    // « Réinitialisation automatique WEBSDR » : si la page reste 30 s sans produire de son alors que le volume n'est pas à zéro
    // (téléphone, mute de l'application, curseur de la page), la page est rechargée ; 3 essais de suite au plus par WebSDR.
    int autoTries;
    final Runnable watchRun = new Runnable() {
        public void run() {
            h.postDelayed(this, 5000);
            if (!st.autoReload || sdrKind.isEmpty() || muted || callMute || focusMute || (isRade(decMode) && radeVoice) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || autoTries >= 3) return;
            web.evaluateJavascript("(function(){try{return window.__sdrlink?__sdrlink.silentMs():-1}catch(e){return -1}})()", new android.webkit.ValueCallback<String>() {
                public void onReceiveValue(String v) {
                    long ms;
                    try { ms = Long.parseLong(v.trim()); } catch (Exception e) { return; }
                    if (ms >= 30000 && st.autoReload && !muted && !callMute && !focusMute && !(isRade(decMode) && radeVoice)) {
                        autoTries++;
                        status(Lang.T("Plus de son depuis 30 s : le WebSDR est rechargé.", "No sound for 30 s: the WebSDR is reloaded."));
                        web.reload();
                    }
                }
            });
        }
    };

    // Le notch automatique n'est pas appliqué en CW ni en DATA (la coche reste telle que l'opérateur l'a mise).
    // Notch et réducteur de bruit sont coupés (sans décocher les cases) tant qu'un mode numérique est actif : ils abîmeraient le son.
    boolean dnrNow() { return st.autoDnr && decMode == 0; }

    boolean notchNow() { return st.autoNotch && decMode == 0 && !"CW".equals(mode) && !(mode != null && mode.startsWith("DATA")); }

    boolean notchSent;

    void applyAudio() {
        applyVolume();
        notchSent = notchNow();
        js("if(window.__sdrlink)__sdrlink.setWatch(" + st.autoReload + ");");
        js("if(window.__sdrlink){__sdrlink.setNotch(" + notchSent + "," + st.notchLevel + ");__sdrlink.setDnr(" + dnrNow() + "," + st.dnrLevel + ");}");
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

    // Zoom de l'écran retenu par WebSDR (rapport à l'échelle d'une page fraîchement ouverte, en millièmes, dans PhoneViewPos) : retrouvé quand on
    // revient sur ce WebSDR et à la relance de l'application. INIT l'oublie.
    boolean zoomApplying, pinched;
    String zoomKey() { String u = web != null ? web.getUrl() : null; return "z" + Integer.toHexString(Finder.urlKey(u == null ? st.lastUrl : u).hashCode()); }

    float savedRatio() {
        int[] p = st.getPos(zoomKey());
        return p == null || p.length < 1 ? 1f : Math.max(0.2f, Math.min(10f, p[0] / 1000f));
    }

    @SuppressWarnings("deprecation")
    final Runnable recordZoomRun = new Runnable() {
        public void run() {
            if (zoomApplying || initReset || captureScale || scaleRef != 0 || initScale <= 0) return;   // échelle en cours de mise en place : rien à noter
            float s = web.getScale();
            if (s <= 0) return;
            int r = Math.round(s / initScale * 1000);
            int[] p = st.getPos(zoomKey());
            if (p != null && Math.abs(p[0] - r) <= 15) return;
            if (p == null && Math.abs(r - 1000) <= 15) return;   // pas de zoom voulu
            st.putPos(zoomKey(), r);
            st.save();
        }
    };

    final Runnable scaleInit = new Runnable() {
        @SuppressWarnings("deprecation")
        public void run() {
            float s = web.getScale();
            if (captureScale) { initScale = s; captureScale = false; zoomApplying = true; }   // page fraîchement ouverte : on y remet le zoom retenu
            if (initReset || zoomApplying) {
                float target = initScale * savedRatio();
                boolean off = initScale > 0 && s > 0 && Math.abs(s / target - 1) > 0.02f;
                if (off && initTries++ < 5) { web.zoomBy(target / s); h.postDelayed(this, 1500); }   // la page peut refuser le premier essai : on revérifie
                else { initReset = false; zoomApplying = false; initTries = 0; }
            }
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

    // Lien de partage reçu (phonesdrlink://tune?…, voir ShareLink) : confirmation, puis WebSDR, fréquence, mode, largeur et décodeur.
    // La confirmation est obligatoire : le lien vient d'un tiers et fait charger une page quelconque dans la WebView (pont PhoneSdr).
    void handleShareIntent(Intent i) {
        if (i == null || i.getData() == null || !ShareLink.SCHEME.equalsIgnoreCase(i.getData().getScheme())) return;
        final ShareLink s = ShareLink.parse(i.getData().toString(), MODES);
        if (s == null) { status(Lang.T("Lien de partage invalide", "Invalid share link")); return; }
        if (blocked()) return;
        final int dec = decFromName(s.decoder);
        Ui.confirm(this, Lang.T("Fréquence partagée", "Shared frequency"),
            Lang.F("Ouvrir {0} sur {1} ?", "Open {0} on {1}?", Uri.parse(s.url).getHost(), listenText(s.hz, s.mode, dec)),
            new Ui.Yes() { public void yes() { applyShare(s, dec); } });
    }

    void applyShare(ShareLink s, int dec) {
        if (blocked()) return;
        if (scanning) stopScan(null);
        freq = s.hz;
        mode = s.mode;
        openUrl(s.url);                      // bwHz = largeur retenue pour le mode ; au premier état de la page, fréquence et mode sont imposés
        if (s.bwHz > 0) bwHz = s.bwHz;       // pour cette écoute seulement : st.setBwFor n'est pas appelé
        if (dec != 0) decoderStart(dec);     // sans décodeur dans le lien, celui qui tourne continue (comme la liste « Mem »)
        updateDisplays();
        saveSoon();
    }

    // « 7074 kHz USB (FT8) » : texte du partage et de la confirmation.
    static String listenText(long hz, String m, int dec) { return Memory.kHz(hz) + " kHz " + modeLabel(m) + (dec != 0 ? " (" + decName(dec) + ")" : ""); }

    // Inverse de decName : 0 si le nom est inconnu.
    static int decFromName(String n) {
        if (n == null || n.isEmpty()) return 0;
        for (int d = 1; d <= DEC_WEFAX; d++) if (decName(d).equalsIgnoreCase(n)) return d;
        return 0;
    }

    // Zoom propre du WebSDR (palier de zoom du spectre/cascade) retenu pour chaque favori, dans PhoneViewPos (clé s<hash>, comme la clé z<hash>
    // du zoom de l'écran) : retrouvé à chaque retour sur ce WebSDR et à la relance de l'application. Le zoom est lu toutes les 3 s ; une valeur
    // différente de celle de la page fraîchement réglée est enregistrée (zoom voulu avec + / −, les boutons de la page ou un geste).
    // INIT l'oublie. Sans valeur retenue, le « zoom du spectre » automatique de SET s'applique comme avant.
    int ownPhase;               // 0 page en cours de réglage, 1 prochaine lecture = référence, 2 suivi des changements
    int ownBase;
    boolean ownForce;
    int ownSeq;                 // numéro de la page en cours (annule les actions retardées d'une page précédente)
    String ownKey() { return "s" + zoomKey().substring(1); }

    void ownStart() {
        final int seq = ++ownSeq;
        ownPhase = 0;
        int[] sv = st.getPos(ownKey());
        final boolean saved = sv != null && sv.length > 0 && sv[0] != Integer.MAX_VALUE;
        final int val = saved ? sv[0] : 0;
        if (saved) {
            int[] when = { 3000, 5000, 7500 };   // la page peut refuser un premier essai : on recommence
            for (int i = 0; i < when.length; i++) h.postDelayed(new Runnable() { public void run() { if (seq == ownSeq) js("if(window.__sdrlink)__sdrlink.zoomWrite(" + val + ");"); } }, when[i]);
        } else {
            h.postDelayed(new Runnable() { public void run() { if (seq == ownSeq) applySpectrumZoom(); } }, 4500);
        }
        h.postDelayed(new Runnable() { public void run() { if (seq == ownSeq) ownPhase = 1; } }, 10000);
        h.removeCallbacks(ownPollRun);
        h.postDelayed(ownPollRun, 3000);
    }

    final Runnable ownPollRun = new Runnable() {
        public void run() {
            if (sdrKind.isEmpty() || web == null) return;
            if (ownPhase > 0) js("if(window.__sdrlink)__sdrlink.zoomReport();");
            h.postDelayed(this, 3000);
        }
    };

    void onOwnZoom(int v) {
        if (ownForce) { ownForce = false; ownBase = v; if (ownPhase < 2) ownPhase = 2; st.putPos(ownKey(), v); st.save(); return; }
        if (ownPhase == 1) { ownBase = v; ownPhase = 2; return; }
        if (ownPhase != 2 || v == ownBase) return;
        ownBase = v;
        st.putPos(ownKey(), v);
        st.save();
    }

    String currentUrl() { return web.getUrl() == null ? st.lastUrl : web.getUrl(); }
    String currentTitle() { return web.getTitle() == null ? "" : web.getTitle(); }

    // Messages du pont : « type|Hz|mode[|u] » (état du WebSDR) ou « click » (appui dans la page).
    void onWebMessage(String msg) {
        if ("click".equals(msg)) { onPageClick(); return; }
        if (msg.startsWith("mp3|")) { recWrite(msg.substring(4)); return; }
        if (msg.equals("mp3end")) { recFinish(); return; }
        if (msg.startsWith("rec|")) { onRecMessage(msg.split("\\|", -1)); return; }
        if (msg.startsWith("dec|")) { onDecMessage(msg); return; }
        if (msg.startsWith("zv|")) { try { onOwnZoom(Integer.parseInt(msg.substring(3).trim())); } catch (NumberFormatException e) { } return; }
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
        if (first && decMode != 0) h.postDelayed(decStartRun, 1500);   // le décodeur reprend sur la nouvelle page
        if (first) { applyAudio(); h.postDelayed(new Runnable() { public void run() { maxPageVolume(); } }, 2500); ownStart(); }
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

    // Historique « Mem » du clavier de fréquence : enregistrement automatique toutes les histMin minutes (0 = aucun)
    final Runnable histTick = new Runnable() {
        public void run() {
            st.addHist(freq, mode, histDec());
            st.save();
            if (st.histMin > 0) h.postDelayed(this, st.histMin * 60000L);
        }
    };

    void histSchedule() {
        h.removeCallbacks(histTick);
        histLastHz = freq; histLastMode = mode; histLastDec = histDec();
        if (st.histMin > 0) h.postDelayed(histTick, st.histMin * 60000L);
    }

    // Un changement de fréquence ou de mode avant l'échéance remet le compteur à zéro : on n'enregistre que si l'on reste le temps choisi sur la même fréquence.
    long histLastHz = -1;
    String histLastMode = "";
    int histLastDec;
    // décodeur choisi par l'utilisateur (RADE lancé tout seul par « FreeDV auto » exclu)
    int histDec() { return decMode != 0 && !radeAuto ? decMode : 0; }
    void histNote() {
        if (freq == histLastHz && mode != null && mode.equals(histLastMode) && histDec() == histLastDec) return;
        histSchedule();
        if (getIntent() != null && "clear".equals(getIntent().getStringExtra("histfill"))) { st.hist.clear(); st.save(); }
        else if (getIntent() != null && getIntent().getStringExtra("histfill") != null) { for (int i = 0; i < 20; i++) st.addHist(3500000L + i * 100000L, i % 2 == 0 ? "USB" : "LSB", i % 3 == 0 ? DEC_FT8 : 0); st.save(); }   // essai : remplit la liste Mem
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

    // Bouton de l'oreillette / du casque : une session média reçoit les appuis (lecture/pause, décrocher…) ; aucun calcul tant qu'on n'appuie pas.
    android.media.session.MediaSession mediaSession;
    boolean stoppedByHeadset;
    long lastHeadsetMs;

    void applyHeadset() {
        try {
            if (st.headsetStop) {
                if (mediaSession == null) {
                    mediaSession = new android.media.session.MediaSession(this, "PhoneSDRLink");
                    mediaSession.setFlags(android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
                    mediaSession.setCallback(new android.media.session.MediaSession.Callback() {
                        @Override public boolean onMediaButtonEvent(Intent i) {
                            android.view.KeyEvent e = i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                            if (e != null) {
                                android.util.Log.i("PhoneSDRLink", "MEDIAKEY code=" + e.getKeyCode() + " (" + android.view.KeyEvent.keyCodeToString(e.getKeyCode()) + ") action=" + e.getAction() + " repeat=" + e.getRepeatCount());
                                if (e.getAction() == android.view.KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) headsetKey(e.getKeyCode());
                            }
                            return true;
                        }
                    });
                    long acts = android.media.session.PlaybackState.ACTION_PLAY | android.media.session.PlaybackState.ACTION_PAUSE | android.media.session.PlaybackState.ACTION_PLAY_PAUSE | android.media.session.PlaybackState.ACTION_STOP;
                    mediaSession.setPlaybackState(new android.media.session.PlaybackState.Builder().setActions(acts).setState(android.media.session.PlaybackState.STATE_PLAYING, 0, 1f).build());
                }
                mediaSession.setActive(true);
            } else if (mediaSession != null) {
                mediaSession.setActive(false);
                mediaSession.release();
                mediaSession = null;
            }
        } catch (Exception e) { }
    }

    // Double appui (« suivant ») ou triple appui (« précédent ») : seulement quand le casque a arrêté le scan, revient d'un pas en arrière
    // (le signal est déjà passé) ; un appui simple reprend ensuite le scan depuis cette fréquence.
    void headsetBack() {
        if (scanning || !stoppedByHeadset) return;
        if (st.scanType == 0) {
            if (scanRun == null) return;
            long f = scanRun.prev(scanHz);
            if (f < 0) return;
            scanHz = f;
        } else {
            if (scanMems.isEmpty()) return;
            scanIndex = (scanIndex - 1 + scanMems.size()) % scanMems.size();
        }
        scanTune();
        status(Lang.F("Retour d'un pas : {0}", "Back one step: {0}", st.scanType == 0 ? Memory.kHz(scanHz) + " kHz" : scanMems.get(scanIndex).label()));
    }

    void headsetKey(int code) {
        boolean back = code == android.view.KeyEvent.KEYCODE_MEDIA_NEXT || code == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        boolean ok = code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == android.view.KeyEvent.KEYCODE_HEADSETHOOK
                || code == android.view.KeyEvent.KEYCODE_MEDIA_PLAY || code == android.view.KeyEvent.KEYCODE_MEDIA_PAUSE || code == android.view.KeyEvent.KEYCODE_MEDIA_STOP;
        long now = System.currentTimeMillis();
        if (!(ok || back) || !st.headsetStop || now - lastHeadsetMs < (back ? 300 : 700)) return;   // anti-rebond (double événement de certains casques) ; plus court pour le retour, qu'on enchaîne
        lastHeadsetMs = now;
        if (back) { headsetBack(); return; }
        if (scanning) { stopScan(Lang.T("bouton du casque", "headset button")); stoppedByHeadset = true; }
        else { stoppedByHeadset = false; startScan(true); }   // arrêté (ou jamais lancé) : le bouton lance / reprend le scan
    }

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
        String ttl = recording ? Lang.T("Couper le son dans :", "Cut the sound in:") : Lang.T("Couper le son et fermer l'application dans :", "Cut the sound and close the application in:");
        Ui.choose(this, ttl, items.toArray(new String[0]), new Ui.Index() {
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
        Ui.choose(this, Lang.T("Le REC en cours s'arrête et l'application se ferme dans :", "The recording in progress stops and the application closes in:"), items, new Ui.Index() {
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
            s = Lang.F("Le son sera coupé dans {0} min ; le REC s'arrêtera et l'application se fermera dans {1} min", "The sound will be cut in {0} min; the recording will stop and the application close in {1} min", m, recMin);
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

    // ------------------------------------------------------------------ décodeurs numériques (FT8, FT4, CW, RTTY, FreeDV RADE)

    static final int DEC_FT8 = 1, DEC_RADE = 2, DEC_FT4 = 3, DEC_CW = 4, DEC_RTTY = 5, DEC_PSK = 6, DEC_SSTV = 7, DEC_WSPR = 8, DEC_JT9 = 9, DEC_RADE2 = 10, DEC_JS8 = 11, DEC_FT2 = 12, DEC_OLIVIA = 13, DEC_CONTESTIA = 14, DEC_HELL = 15, DEC_WEFAX = 16;
    static boolean isRade(int m) { return m == DEC_RADE || m == DEC_RADE2; }   // FreeDV RADE V1 (FreeDV 2.x) ou V2 (FreeDV 3.0)
    int decMode;                       // 0 arrêté, sinon DEC_*
    boolean radeVoice;                 // RADE : des trames FreeDV sont décodées, la parole remplace le son du WebSDR
    Ft8Engine ft8;
    WsprEngine wspr;
    Jt9Engine jt9;
    Js8Engine js8;
    RadeEngine rade;
    TextEngine txt;
    static final class DecLine { String text, slot; int col; DecLine(String t, int c) { text = t; col = c; int k = t.indexOf(" "); slot = c != 0 && k > 0 ? t.substring(0, k) : null; } }   // col : 0 normal, 1 période 1, 2 période 2
    final java.util.ArrayList<DecLine> decLines = new java.util.ArrayList<DecLine>();
    boolean decNewLine = true;
    String decStatus = "";
    TextView decView, decState, decOverlayView;
    ScrollView decScroll;
    int decTries;

    static String decName(int m) { return m == DEC_FT8 ? "FT8" : m == DEC_FT4 ? "FT4" : m == DEC_FT2 ? "FT2" : m == DEC_CW ? "CW" : m == DEC_RTTY ? "RTTY" : m == DEC_PSK ? "PSK31" : m == DEC_SSTV ? "SSTV" : m == DEC_WSPR ? "WSPR" : m == DEC_JT9 ? "JT9" : m == DEC_JS8 ? "JS8" : m == DEC_RADE ? "RADE V1" : m == DEC_RADE2 ? "RADE V2" : m == DEC_OLIVIA ? "Olivia" : m == DEC_CONTESTIA ? "Contestia" : m == DEC_HELL ? "Hell" : m == DEC_WEFAX ? "WEFAX" : ""; }
    static boolean isImageDec(int m) { return m == DEC_SSTV || m == DEC_HELL || m == DEC_WEFAX; }   // décodeurs dont le résultat est une image (SSTV, Hell, WEFAX)
    static int decRate(int m) { return (m == DEC_FT8 || m == DEC_FT4 || m == DEC_FT2 || m == DEC_WSPR || m == DEC_JT9 || m == DEC_JS8) ? Ft8Engine.RATE : 8000; }

    // Surbrillance (réglage « Surbrillance » de la fenêtre du décodeur) : chaque suite de caractères saisie (séparées par des virgules,
    // tiret, barre oblique et espaces permis à l'intérieur) est repérée sans tenir compte des majuscules, dans la fenêtre du décodeur et
    // sur l'écran principal.
    String hiSrc = null;
    String[] hiPats = new String[0];
    void hiParse() {
        String src = st.decHi == null ? "" : st.decHi;
        if (src.equals(hiSrc)) return;
        hiSrc = src;
        java.util.ArrayList<String> l = new java.util.ArrayList<String>();
        for (String p : src.split("[,;\\n]")) { p = p.trim().toLowerCase(java.util.Locale.ROOT); if (p.length() > 0) l.add(p); }
        hiPats = l.toArray(new String[0]);
    }
    void hilite(android.text.SpannableStringBuilder sb, int start, String text) {
        hiParse();
        if (hiPats.length == 0) return;
        String low = text.toLowerCase(java.util.Locale.ROOT);
        if (low.length() != text.length()) return;   // (rare) la mise en minuscules a changé la longueur : positions fausses, on s'abstient
        for (String p : hiPats) {
            for (int k = low.indexOf(p); k >= 0; k = low.indexOf(p, k + p.length())) {
                sb.setSpan(new android.text.style.BackgroundColorSpan(0xFF00E676), start + k, start + k + p.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.ForegroundColorSpan(0xFF000000), start + k, start + k + p.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start + k, start + k + p.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    android.text.SpannableStringBuilder decSpan(int last) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        for (int i = Math.max(0, decLines.size() - last); i < decLines.size(); i++) {
            DecLine l = decLines.get(i);
            int s = sb.length();
            sb.append(l.text);
            sb.setSpan(new android.text.style.ForegroundColorSpan(l.col == 1 ? Ui.ft8A() : l.col == 2 ? Ui.ft8B() : Ui.fg()), s, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            hilite(sb, s, l.text);
            if (i < decLines.size() - 1) sb.append('\n');
        }
        return sb;
    }

    // Écran principal : en FT8 / FT4, toute la dernière période (les lignes de même heure) tant qu'elle tient sur la partie WebSDR ;
    // en CW / RTTY / RADE, les six dernières lignes.
    android.text.SpannableStringBuilder decOverlaySpan() {
        int end = decLines.size(), start = Math.max(0, end - 6);
        String slot = end > 0 ? decLines.get(end - 1).slot : null;
        if (slot != null) {
            start = end;
            while (start > 0 && slot.equals(decLines.get(start - 1).slot)) start--;
            int lh = Math.max(1, decOverlayView.getLineHeight());
            int room = webFrame != null && webFrame.getHeight() > 0 ? (webFrame.getHeight() - Ui.dp(this, st.barBottom ? 40 : 18)) / lh : 6;
            if (end - start > room) start = end - Math.max(1, room);
        }
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        for (int i = start; i < end; i++) {
            DecLine l = decLines.get(i);
            int s = sb.length();
            sb.append(l.text);
            sb.setSpan(new android.text.style.ForegroundColorSpan(l.col == 1 ? Ui.ft8A() : l.col == 2 ? Ui.ft8B() : Ui.fg()), s, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            hilite(sb, s, l.text);
            if (i < end - 1) sb.append('\n');
        }
        return sb;
    }

    // Le texte décodé s'affiche dans la fenêtre du décodeur et, en bas de l'écran principal, sur quelques lignes (on peut ainsi
    // déplacer la fréquence tout en suivant le décodage). Un appui sur ces lignes ouvre la fenêtre.
    // Les mises à jour de l'affichage sont regroupées : au plus une reconstruction du texte coloré toutes les 150 ms.
    boolean decRefreshPending;
    final Runnable decRefreshRun = new Runnable() { public void run() { decRefreshPending = false; decRefreshNow(); } };

    void decRefresh() {
        if (decRefreshPending) return;
        decRefreshPending = true;
        h.postDelayed(decRefreshRun, 150);
    }

    void decRefreshNow() {
        if (decView != null) {
            decView.setText(decSpan(200));
            if (decScroll != null) decScroll.post(new Runnable() { public void run() { if (decScroll != null) decScroll.fullScroll(View.FOCUS_DOWN); } });
        }
        if (decOverlayView != null) {
            boolean show = decMode != 0 && !isRade(decMode) && !isImageDec(decMode) && st.decOverlay && !decLines.isEmpty();   // FreeDV : pas de lignes devant le spectre (la parole suffit)
            decOverlayView.setVisibility(show ? View.VISIBLE : View.GONE);
            if (show) decOverlayView.setText(decOverlaySpan());
        }
        if (isImageDec(decMode) || (sstvOverlay != null && sstvOverlay.getVisibility() == View.VISIBLE)) {
            sstvShow();
        }
    }

    void decAddLine(String text, int col) {
        decLines.add(new DecLine(text, col));
        while (decLines.size() > 200) decLines.remove(0);
        decNewLine = true;
    }

    void decAppendText(String s) {   // CW, RTTY : flux de caractères, une ligne datée environ tous les 62 caractères
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\n') { decNewLine = true; continue; }
            if (decNewLine || decLines.isEmpty() || decLines.get(decLines.size() - 1).text.length() >= 70) {
                if (ch == ' ') { decNewLine = true; continue; }
                decAddLine(RadeEngine.clock(System.currentTimeMillis()) + "  ", 0);
                decNewLine = false;
            }
            decLines.get(decLines.size() - 1).text += ch;
        }
        decRefresh();
    }

    // Réglages CW : copiés du stockage vers le moteur (appelé au démarrage du décodeur CW et à chaque changement dans la fenêtre).
    void applyCwSettings() {
        if (!(txt instanceof CwEngine)) return;
        final CwEngine c = (CwEngine) txt;
        c.autoTone = st.cwAutoTone;
        c.manualTone = st.cwTone;
        c.bandwidth = st.cwBw;
        c.autoSpeed = st.cwAutoSpeed;
        c.manualWpm = st.cwWpm;
        c.threshold = st.cwThr;
        c.charGap = st.cwChar / 10.0;
        c.wordGap = st.cwWord / 10.0;
        c.squelch = st.cwSq;
        c.autoBw = st.cwAutoBw;
        c.afc = st.cwAfc;
        c.agc = st.cwAgc;
        if (!c.autoSpeed) c.post(new Runnable() { public void run() { c.setManualSpeed(); } });
    }

    // Réglages RTTY : copiés du stockage vers le moteur ; les décodeurs sont refaits seulement si débit, décalage, polarité ou fréquence changent.
    void applyRttySettings() {
        if (!(txt instanceof RttyEngine)) return;
        final RttyEngine r = (RttyEngine) txt;
        double baud = st.rttyBaud / 100.0;
        boolean redo = r.baudSetting != baud || r.shiftSetting != st.rttyShift || r.polSetting != st.rttyPol || r.freqSetting != st.rttyFreq || r.afc != st.rttyAfc;
        r.baudSetting = baud;
        r.shiftSetting = st.rttyShift;
        r.polSetting = st.rttyPol;
        r.freqSetting = st.rttyFreq;
        r.afc = st.rttyAfc;
        r.uos = st.rttyUos;
        r.squelch = st.rttySq;
        if (redo) r.post(new Runnable() { public void run() { r.settingsChanged(); } });
    }

    void applyDecSettings() { applyCwSettings(); applyRttySettings(); applyPskSettings(); applySstvSettings(); applyOliviaSettings(); applyHellSettings(); applyFaxSettings(); }

    // WEFAX : réglages copiés du stockage vers le moteur ; les images passent par les mêmes vues que les images SSTV.
    void applyFaxSettings() {
        if (!(txt instanceof WefaxEngine)) return;
        WefaxEngine e = (WefaxEngine) txt;
        e.lpmIdx = st.faxLpm; e.invert = st.faxInv; e.tol = st.faxTol; e.tone = st.faxTone; e.align = st.faxAlign;
    }

    void faxCmd(int c) { if (txt instanceof WefaxEngine) ((WefaxEngine) txt).cmd = c; }

    boolean sstvQuiet;   // WEFAX : le tableau de l'image grandit, ce n'est pas une nouvelle image
    final WefaxEngine.Out faxOut = new WefaxEngine.Out() {
        public void begin(final int w, final int hh, final int[] px, final boolean fresh) {
            h.post(new Runnable() { public void run() { if (txt instanceof WefaxEngine) { sstvQuiet = !fresh; sstvBegin("WEFAX", w, hh, px); sstvQuiet = false; } } });
        }
        public void rows(int done) {
            sstvRows = done;
            if (sstvPending) return;
            sstvPending = true;
            h.postDelayed(new Runnable() { public void run() { sstvPending = false; sstvUpdate(); } }, 300);
        }
        public void end(final boolean complete, final int rows) {
            h.post(new Runnable() { public void run() { sstvEnd(complete, rows); } });
        }
    };

    void applyJs8Settings() { if (decMode == DEC_JS8) decoderStart(DEC_JS8); }   // vitesse changée : le décodeur JS8 repart
    void applySstvSettings() { if (txt instanceof SstvEngine) ((SstvEngine) txt).setLevels(st.sstvSens, st.sstvLoss); }

    // Réglages Olivia / Contestia : copiés du stockage vers le moteur ; le décodeur natif est refait seulement si un paramètre qui compte change.
    void applyOliviaSettings() {
        if (!(txt instanceof OliviaEngine)) return;
        final OliviaEngine o = (OliviaEngine) txt;
        int t = o.contestia ? st.conTones : st.oliTones, b = o.contestia ? st.conBw : st.oliBw, f = st.oliAuto ? 0 : st.oliFreq;
        boolean redo = o.tones != t || o.bw != b || o.margin != st.oliMargin || o.integ != st.oliInteg || o.thr10 != st.oliThr || o.reverse != st.oliRev || (o.centerSetting != f && f == 0 && o.centerSetting > 0);
        o.tones = t; o.bw = b; o.margin = st.oliMargin; o.integ = st.oliInteg; o.thr10 = st.oliThr; o.reverse = st.oliRev; o.centerSetting = f;
        if (redo) o.post(new Runnable() { public void run() { o.settingsChanged(); } });
    }

    // Réglages Hell : copiés du stockage vers le moteur.
    void applyHellSettings() {
        if (!(txt instanceof HellEngine)) return;
        final HellEngine e = (HellEngine) txt;
        boolean redo = e.variant != st.hellVar;
        e.variant = st.hellVar; e.centerSetting = st.hellAuto ? 0 : st.hellFreq; e.sq = st.hellSq; e.invert = st.hellInv;
        if (redo) e.post(new Runnable() { public void run() { e.settingsChanged(); } });
    }

    void hellClear() { if (txt instanceof HellEngine) ((HellEngine) txt).clearReq++; }

    void hellSave() {
        if (sstvBmp == null || !(txt instanceof HellEngine)) { Ui.toast(this, Lang.T("Rien à enregistrer", "Nothing to save")); return; }
        final Bitmap b = sstvBmp.copy(Bitmap.Config.ARGB_8888, false);
        new Thread(new Runnable() {
            public void run() {
                final String res = saveSstv(b, "Hell", true);
                h.post(new Runnable() { public void run() { Ui.toast(MainActivity.this, res == null ? Lang.T("Enregistrement impossible", "Could not save") : Lang.T("Image enregistrée : ", "Picture saved: ") + res); } });
            }
        }).start();
    }

    // Hell : l'image (tableau rempli par le moteur) passe par les mêmes vues que les images SSTV.
    final HellEngine.Display hellDisplay = new HellEngine.Display() {
        public void begin(final int w, final int hh, final int[] px) {
            h.post(new Runnable() { public void run() { if (txt instanceof HellEngine) sstvBegin("Hell", w, hh, px); } });
        }
        public void changed() {
            if (sstvPending) return;
            sstvPending = true;
            h.postDelayed(new Runnable() { public void run() { sstvPending = false; sstvUpdate(); } }, 120);
        }
    };

    // Réglages PSK31 : copiés du stockage vers le moteur (fréquence automatique ou manuelle, AFC, débit, squelch).
    void applyPskSettings() {
        if (!(txt instanceof PskEngine)) return;
        final PskEngine p = (PskEngine) txt;
        p.autoFreq = st.pskAuto;
        p.manualFreq = st.pskFreq;
        p.afc = st.pskAfc;
        p.baud = st.pskBaud;
        p.squelch = st.pskSq;
        p.post(new Runnable() { public void run() { p.settingsChanged(); } });
    }

    // ---- SSTV : image en cours (bitmap rempli ligne par ligne), incrustation en bas de l'écran principal, enregistrement automatique
    ImageView sstvOverlay, sstvDlgImg;
    Bitmap sstvBmp;
    int[] sstvPx;
    int sstvW, sstvH, sstvShown;
    volatile int sstvRows;
    String sstvName = "", sstvSaved = "";
    boolean sstvPending;

    final SstvEngine.Images sstvImages = new SstvEngine.Images() {
        public void begin(final String name, final int w, final int hh, final int[] px) {
            h.post(new Runnable() { public void run() { if (txt instanceof SstvEngine) sstvBegin(name, w, hh, px); } });
        }
        public void rows(int done) {
            sstvRows = done;
            if (sstvPending) return;
            sstvPending = true;
            h.postDelayed(new Runnable() { public void run() { sstvPending = false; sstvUpdate(); } }, 200);
        }
        public void end(final boolean complete, final int rows) {
            h.post(new Runnable() { public void run() { sstvEnd(complete, rows); } });
        }
    };

    void sstvBegin(String name, int w, int hh, int[] px) {
        sstvName = name; sstvPx = px; sstvW = w; sstvH = hh; sstvShown = 0;
        if (!sstvQuiet) sstvRows = 0;
        sstvBmp = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888);
        sstvBmp.eraseColor(0xFF202020);
        if (!(txt instanceof HellEngine) && !sstvQuiet) { decAddLine(RadeEngine.clock(System.currentTimeMillis()) + "  " + name + " (" + w + "×" + hh + ")", 0); decRefresh(); }
        sstvShow();
    }

    void sstvUpdate() {
        if (sstvBmp == null || sstvPx == null) return;
        if (txt instanceof HellEngine) { sstvBmp.setPixels(sstvPx, 0, sstvW, 0, 0, sstvW, sstvH); sstvShow(); return; }   // Hell : toute l'image est redessinée (elle défile)
        int done = Math.min(sstvRows, sstvH);
        if (done > sstvShown) { sstvBmp.setPixels(sstvPx, sstvShown * sstvW, sstvW, 0, sstvShown, sstvW, done - sstvShown); sstvShown = done; }
        sstvShow();
    }

    // Met l'image dans la fenêtre du décodeur et dans l'incrustation de l'écran principal (petit format, à la place du texte).
    void sstvShow() {
        if (sstvDlgImg != null && sstvBmp != null) sstvDlgImg.setImageBitmap(sstvBmp);
        if (sstvOverlay == null) return;
        boolean show = isImageDec(decMode) && st.decOverlay && sstvBmp != null;
        sstvOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        int fw = webFrame != null ? webFrame.getWidth() : 0, fh = webFrame != null ? webFrame.getHeight() : 0;
        if (fw <= 0 || fh <= 0) { fw = getResources().getDisplayMetrics().widthPixels; fh = getResources().getDisplayMetrics().heightPixels / 2; }
        float k = decMode == DEC_HELL ? Math.min((fw * 0.92f) / sstvW, (fh * 0.40f) / sstvH) : Math.min((fw * 0.42f) / sstvW, (fh * 0.42f) / sstvH);
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) sstvOverlay.getLayoutParams();
        if (p != null) {
            int nw = Math.max(1, (int) (sstvW * k)), nh = Math.max(1, (int) (sstvH * k));
            if (p.width != nw || p.height != nh) { p.width = nw; p.height = nh; sstvOverlay.setLayoutParams(p); }
        }
        sstvOverlay.setImageBitmap(sstvBmp);
    }

    void sstvEnd(boolean complete, int rows) {
        sstvRows = rows;
        sstvUpdate();
        if (sstvBmp == null) return;
        if (sstvName.equals("WEFAX") && rows >= 16 && rows < sstvH) { sstvBmp = Bitmap.createBitmap(sstvBmp, 0, 0, sstvW, rows); sstvH = rows; sstvShow(); }   // WEFAX : l'image finie est montrée sans la partie vide
        if (!st.sstvSave || rows < 16) {
            decAddLine(RadeEngine.clock(System.currentTimeMillis()) + "  " + (complete ? Lang.T("image terminée", "image complete") : Lang.T("signal perdu", "signal lost")) + (rows < 16 ? " — " + Lang.T("trop courte, non enregistrée", "too short, not saved") : ""), 0);
            decRefresh();
            return;
        }
        final Bitmap b = rows < sstvH ? Bitmap.createBitmap(sstvBmp, 0, 0, sstvW, rows) : sstvBmp.copy(Bitmap.Config.ARGB_8888, false);
        final String nm = sstvName;
        final boolean comp = complete;
        new Thread(new Runnable() {
            public void run() {
                final String res = saveSstv(b, nm, comp);
                h.post(new Runnable() {
                    public void run() {
                        sstvSaved = res == null ? "" : res;
                        decAddLine(RadeEngine.clock(System.currentTimeMillis()) + "  " + (res == null ? Lang.T("enregistrement impossible", "could not save") : Lang.T("image enregistrée : ", "image saved: ") + res), 0);
                        decRefresh();
                        if (res != null) Ui.toast(MainActivity.this, Lang.T("Image SSTV enregistrée : ", "SSTV image saved: ") + res);
                    }
                });
            }
        }).start();
    }

    // Enregistre l'image en PNG dans Images/PhoneSDRLink (Android 10 et plus : galerie, sans permission) ou, plus ancien, dans le dossier
    // de l'application (Android/data/…/files/Pictures/SSTV). Renvoie le nom du fichier, ou null.
    String saveSstv(Bitmap b, String mode, boolean complete) {
        String fn = (mode.startsWith("Hell") || mode.startsWith("WEFAX") ? "" : "SSTV_") + mode.replace(" ", "") + "_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + (complete ? "" : "_partiel") + ".png";
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fn);
                cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhoneSDRLink");
                android.net.Uri u = getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (u == null) return null;
                java.io.OutputStream os = getContentResolver().openOutputStream(u);
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
            } else {
                java.io.File d = new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "SSTV");
                d.mkdirs();
                java.io.FileOutputStream os = new java.io.FileOutputStream(new java.io.File(d, fn));
                b.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
            }
            return fn;
        } catch (Exception e) { return null; }
    }

    // Lien vers le dossier des images enregistrées : le gestionnaire de fichiers s'il sait ouvrir ce dossier, sinon la galerie.
    void openSstvFolder() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3APictures%2FPhoneSDRLink"), "vnd.android.document/directory");
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    return;
                } catch (Exception e) { }
                Intent g = new Intent(Intent.ACTION_VIEW, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                g.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(g);
            } else {
                Ui.toast(this, Lang.T("Dossier : Android/data/fr.f5pbg.phonesdrlink/files/Pictures/SSTV", "Folder: Android/data/fr.f5pbg.phonesdrlink/files/Pictures/SSTV"));
            }
        } catch (Exception e) {
            Ui.toast(this, Lang.T("Aucune application pour ouvrir le dossier : cherchez Images › PhoneSDRLink", "No application can open the folder: look in Pictures › PhoneSDRLink"));
        }
    }

    // Essai PSK31 / SSTV sur un fichier WAV (8 kHz, 16 bits, mono) injecté à la place du son de la page :
    // am start … --es decwav psk:/chemin/fichier.wav  ou  sstv:/chemin/fichier.wav  (résultat dans la fenêtre du décodeur et dans logcat, étiquette PSDR).
    // Essai WSPR / JT9 avec un enregistrement 12 kHz réel : injecté en temps réel à partir de la prochaine limite de tranche UTC.
    void decSlotWav(final int mode, final String path) {
        decTest = true;
        js("if(window.__sdrlink&&__sdrlink.decStop)__sdrlink.decStop();");
        new Thread(new Runnable() {
            public void run() {
                try {
                    long per = mode == DEC_WSPR ? 120000L : mode == DEC_JS8 ? Js8Engine.PERIOD[st.js8Speed] : 60000L;
                    long now = System.currentTimeMillis(), t0 = (now / per + 1) * per;
                    android.util.Log.i("PSDR", "DECWAV attente " + (t0 - now) + " ms");
                    long tS = t0 - 3200;   // 3,2 s de silence avant : la fenêtre d analyse commence avant la tranche
                    Thread.sleep(Math.max(0, tS - now));
                    java.io.RandomAccessFile f = new java.io.RandomAccessFile(path, "r");
                    f.seek(44);
                    byte[] buf = new byte[3200];
                    int r, k = 0;
                    for (; k < 24; k++) {
                        byte[] z = new byte[3200];
                        if (wspr != null) wspr.feed(z, 3200); else if (jt9 != null) jt9.feed(z, 3200); else if (js8 != null) js8.feed(z, 3200);
                        long dz = tS + (k + 1) * 1600L * 1000L / 12000L - System.currentTimeMillis();
                        if (dz > 0) Thread.sleep(dz);
                    }
                    while ((r = f.read(buf)) > 0) {
                        if (wspr == null && jt9 == null && js8 == null) break;
                        byte[] c = buf.clone();
                        if (wspr != null) wspr.feed(c, r); else if (jt9 != null) jt9.feed(c, r); else js8.feed(c, r);
                        k++;
                        long due = tS + k * 1600L * 1000L / 12000L, w = due - System.currentTimeMillis();
                        if (w > 0) Thread.sleep(w);
                    }
                    f.close();
                    android.util.Log.i("PSDR", "DECWAV fin");
                } catch (Throwable t) { android.util.Log.i("PSDR", "DECWAV erreur " + t); }
            }
        }).start();
    }

    void decWavTest(final String spec) {
        final int mode = spec.startsWith("sstv:") ? DEC_SSTV : spec.startsWith("wspr:") ? DEC_WSPR : spec.startsWith("jt9:") ? DEC_JT9 : spec.startsWith("js8:") ? DEC_JS8 : spec.startsWith("oli:") ? DEC_OLIVIA : spec.startsWith("con:") ? DEC_CONTESTIA : spec.startsWith("hell:") ? DEC_HELL : spec.startsWith("fax:") ? DEC_WEFAX : DEC_PSK;
        String body = spec.substring(spec.indexOf(':') + 1);
        if ((mode == DEC_OLIVIA || mode == DEC_CONTESTIA || mode == DEC_HELL) && !body.startsWith("/") && body.indexOf(':') > 0) {   // oli:tons/bande:chemin  hell:variante:chemin (essais seulement, non enregistré)
            String opt = body.substring(0, body.indexOf(':'));
            body = body.substring(body.indexOf(':') + 1);
            try {
                if (mode == DEC_HELL) st.hellVar = Integer.parseInt(opt);
                else { String[] tb = opt.split("/"); if (mode == DEC_OLIVIA) { st.oliTones = Integer.parseInt(tb[0]); st.oliBw = Integer.parseInt(tb[1]); } else { st.conTones = Integer.parseInt(tb[0]); st.conBw = Integer.parseInt(tb[1]); } }
            } catch (Exception e) { }
        }
        final String path = body;
        HellEngine.debug = true;
        if (mode == DEC_WEFAX) { st.faxTol = path.contains("_tol1") ? 1 : path.contains("_tol2") ? 2 : 0; st.faxTone = path.contains("_tone"); st.faxAlign = path.contains("_now") ? 1 : 0; }   // essais seulement (non enregistré)
        h.postDelayed(new Runnable() {
            public void run() {
                decoderStart(mode);
                if (mode == DEC_WSPR || mode == DEC_JT9 || mode == DEC_JS8) { decSlotWav(mode, path); return; }
                if (txt == null) return;
                if (mode == DEC_WEFAX && path.contains("_now")) faxCmd(1);
                js("if(window.__sdrlink&&__sdrlink.decStop)__sdrlink.decStop();");
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            java.io.RandomAccessFile f = new java.io.RandomAccessFile(path, "r");
                            f.seek(44);
                            byte[] buf = new byte[3200];
                            int r;
                            while ((r = f.read(buf)) > 0) {
                                if (txt == null) break;
                                txt.feedBytes(buf.clone(), r);
                                Thread.sleep(4);
                            }
                            f.close();
                            android.util.Log.i("PSDR", "DECWAV fin");
                        } catch (Throwable t) { android.util.Log.i("PSDR", "DECWAV erreur " + t); }
                    }
                }).start();
            }
        }, 3000);
    }

    void decSetStatus(String s) { decStatus = s; if (decState != null) decState.setText(s); if (DlgDecoder.onState != null) DlgDecoder.onState.run(); }

    void placeDecOverlay() {
        if (decOverlayView == null || webFrame == null) return;
        if (decOverlayView.getParent() != null) ((ViewGroup) decOverlayView.getParent()).removeView(decOverlayView);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        int side = Ui.dp(this, 72), m = Ui.dp(this, 4);   // on laisse la place aux boutons INIT, + et −
        int zc = st.zoomPos & 3; boolean zb = zc < 2 && (st.zoomPos & 4) == 0, zh = zc < 2 && (st.zoomPos & 4) != 0;   // boutons en bas : colonne = place sur le côté, ligne = place au-dessus
        p.setMargins(zb && (zc == 1) ? side : m, 0, zb && (zc == 0) ? side : m, (st.barBottom ? Ui.dp(this, 28) : m) + (zh ? Ui.dp(this, 60) : 0));
        webFrame.addView(decOverlayView, p);
        if (sstvOverlay != null) {
            if (sstvOverlay.getParent() != null) ((ViewGroup) sstvOverlay.getParent()).removeView(sstvOverlay);
            FrameLayout.LayoutParams q = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
            q.setMargins(p.leftMargin, 0, p.rightMargin, p.bottomMargin);
            webFrame.addView(sstvOverlay, q);
            sstvShow();
        }
    }

    // FreeDV auto (SET) : le décodeur FreeDV RADE tourne en permanence pendant l'écoute classique (radeAuto) ; il est suspendu tant qu'un
    // autre décodeur est actif et relancé quand celui-ci s'arrête (decoderStopUser). Sans la coche, rien de tout cela.
    boolean radeAuto;

    void maybeStartAuto() {
        if (!st.freedvAuto || decMode != 0 || !Decoders.loaded) return;
        decoderStart(DEC_RADE);
        if (isRade(decMode)) radeAuto = true;
    }

    void applyFreedvAuto() {
        if (st.freedvAuto) maybeStartAuto();
        else if (radeAuto) { radeAuto = false; decoderStop(false); }
    }

    // Arrêt demandé par l'utilisateur (bouton Arrêter) : si FreeDV auto est coché, FreeDV reprend.
    void decoderStopUser() {
        decoderStop(true);
        maybeStartAuto();
    }

    // « Changement auto fréquence Modes numériques » (SET) : au lancement d'un décodeur par l'utilisateur, on se place sur la mémoire de la
    // liste « Modes numériques » de ce décodeur (région IARU de SET) la plus proche de la fréquence actuelle, avec son mode.
    void digAutoTune(int dec) {
        if (!st.digAuto) return;
        Memory best = null;
        for (Memory m : DigitalFreqs.list(dec, st.iaruRegion)) if (best == null || Math.abs(m.hz - freq) < Math.abs(best.hz - freq)) best = m;
        if (best == null) return;
        if (scanning) stopScan(null);
        applyMemory(best);
    }

    void decoderStart(int mode) {
        if (!Decoders.loaded) { Ui.toast(this, Lang.T("Décodeur indisponible sur ce téléphone", "Decoder not available on this phone")); return; }
        decoderStop(false);
        radeAuto = false;
        decTries = 0;
        decLines.clear();
        decNewLine = true;
        decRefresh();
        decSetStatus(Lang.T("Démarrage…", "Starting…"));
        if (isRade(mode)) {
            rade = new RadeEngine(mode == DEC_RADE2, new RadeEngine.Listener() {
                public void onStatus(String s) { decSetStatus(s); }
                public void onEvent(String line) { decAddLine(line, 0); decRefresh(); }
                public void onVoice(boolean on) { radeVoice = on; applyVolume(); js("if(window.__sdrlink)__sdrlink.recVoice(" + on + ");"); }
                // Parole décodée (16 kHz, 16 bits) : copiée dans le REC à la place du son du WebSDR (qui n'est alors que la modulation numérique)
                public void onPcm(short[] pcm, int n) {
                    if (!recording) return;
                    byte[] b = new byte[n * 2];
                    for (int i = 0; i < n; i++) { b[2 * i] = (byte) pcm[i]; b[2 * i + 1] = (byte) (pcm[i] >> 8); }
                    final String s = android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
                    h.post(new Runnable() { public void run() { js("if(window.__sdrlink)__sdrlink.recPcm('" + s + "');"); } });
                }
            });
            if (!rade.start()) { rade = null; decSetStatus(Lang.T("FreeDV RADE demande un téléphone 64 bits", "FreeDV RADE needs a 64-bit phone")); return; }
            rade.setMuted(muted || callMute || focusMute);
        } else if (mode == DEC_WSPR || mode == DEC_JT9 || mode == DEC_JS8) {
            Ft8Engine.Listener sl = new Ft8Engine.Listener() {
                public void onLines(java.util.ArrayList<String> lines, int parity) { for (String s : lines) decAddLine(s, parity == 0 ? 1 : 2); decRefresh(); }
                public void onStatus(String s) { decSetStatus(s); }
            };
            SlotDecoder.dumpDir = getExternalFilesDir(null);
            if (mode == DEC_WSPR) { wspr = new WsprEngine(sl); wspr.start(); }
            else if (mode == DEC_JS8) {
                if (!Js8Text.jscLoaded()) { try { java.io.InputStream in = getAssets().open("js8jsc.bin"); try { Js8Text.loadJsc(in); } finally { in.close(); } } catch (Exception e) { android.util.Log.i("PSDR", "JS8 dictionnaire : " + e); } }
                js8 = new Js8Engine(st.js8Speed, sl); js8.start();
            } else { jt9 = new Jt9Engine(sl); jt9.start(); }
        } else if (mode == DEC_FT8 || mode == DEC_FT4 || mode == DEC_FT2) {
            ft8 = new Ft8Engine(mode == DEC_FT2 ? 2 : mode == DEC_FT4 ? 1 : 0, new Ft8Engine.Listener() {
                public void onLines(java.util.ArrayList<String> lines, int parity) { for (String s : lines) decAddLine(s, parity == 0 ? 1 : 2); decRefresh(); }
                public void onStatus(String s) { decSetStatus(s); }
            });
            ft8.start();
        } else {
            TextEngine.Listener l = new TextEngine.Listener() {
                public void onText(final String s) { h.post(new Runnable() { public void run() { if (txt != null) decAppendText(s); } }); }   // le calcul se fait dans un fil à part
                public void onStatus(final String s) { h.post(new Runnable() { public void run() { if (txt != null) decSetStatus(s); } }); }
            };
            txt = mode == DEC_CW ? new CwEngine(l) : mode == DEC_PSK ? new PskEngine(l) : mode == DEC_SSTV ? new SstvEngine(l, sstvImages) : mode == DEC_OLIVIA || mode == DEC_CONTESTIA ? new OliviaEngine(l, mode == DEC_CONTESTIA) : mode == DEC_HELL ? new HellEngine(l, hellDisplay) : mode == DEC_WEFAX ? new WefaxEngine(l, faxOut) : new RttyEngine(l);
            applyFaxSettings();
            applyCwSettings();
            applyOliviaSettings();
            applyHellSettings();
            applyRttySettings();
            applyPskSettings();
            applySstvSettings();
        }
        decMode = mode;
        histNote();
        if (DlgDecoder.onState != null) DlgDecoder.onState.run();
        if (tvFreq != null) tvFreq.postInvalidate();   // nom du mode actif dans la case de la fréquence
        sstvShow();
        applyAudio();   // volume (en RADE, le son brut du WebSDR est coupé : on entend la parole décodée) ; notch et réducteur de bruit coupés en mode numérique
        decStartJs();
    }

    void decoderStop(boolean message) {
        boolean was = decMode != 0;
        decMode = 0;
        histNote();
        h.removeCallbacks(decStartRun);
        if (ft8 != null) { ft8.stop(); ft8 = null; }
        if (wspr != null) { wspr.stop(); wspr = null; }
        if (jt9 != null) { jt9.stop(); jt9 = null; }
        if (js8 != null) { js8.stop(); js8 = null; }
        if (rade != null) { rade.stop(); rade = null; }
        radeVoice = false;
        js("if(window.__sdrlink)__sdrlink.recVoice(false);");
        radeAuto = false;
        if (txt instanceof SstvEngine && ((SstvEngine) txt).img != null) sstvEnd(false, sstvRows);   // image interrompue : on garde ce qui est reçu
        if (txt != null) { txt.shutdown(); txt = null; }
        if (web != null) { js("if(window.__sdrlink&&__sdrlink.decStop)__sdrlink.decStop();"); applyAudio(); }
        if (was && message) decSetStatus(Lang.T("Décodeur arrêté", "Decoder stopped"));
        if (decOverlayView != null) decOverlayView.setVisibility(View.GONE);
        sstvShow();
        if (DlgDecoder.onState != null) DlgDecoder.onState.run();
        if (tvFreq != null) tvFreq.postInvalidate();   // nom du mode actif dans la case de la fréquence
    }

    void decStartJs() {
        if (decMode == 0) return;
        web.evaluateJavascript("(function(){try{__sdrlink.decStart(" + decRate(decMode) + ")}catch(e){PhoneSdr.post(\"dec|err|\"+e)}})()", null);
    }

    final Runnable decStartRun = new Runnable() { public void run() { decStartJs(); } };

    boolean decTest;   // essai avec fichier son : on ignore le son de la page
    void onDecMessage(String msg) {
        if (decTest) return;
        if (msg.startsWith("dec|d|")) {
            if (ft8 == null && wspr == null && jt9 == null && js8 == null && rade == null && txt == null) return;
            try {
                byte[] b = android.util.Base64.decode(msg.substring(6), android.util.Base64.NO_WRAP);
                if (ft8 != null) ft8.feed(b, b.length); else if (wspr != null) wspr.feed(b, b.length); else if (jt9 != null) jt9.feed(b, b.length); else if (js8 != null) js8.feed(b, b.length); else if (rade != null) rade.feed(b, b.length); else if (txt != null) txt.feedBytes(b, b.length);
            } catch (Exception e) { }
            return;
        }
        if (msg.startsWith("dec|ok")) {
            decTries = 0;
            decSetStatus(isRade(decMode) ? "RADE : " + Lang.T("recherche du signal…", "searching for the signal…") : decName(decMode) + " : " + Lang.T("en écoute", "listening"));
            return;
        }
        if (msg.startsWith("dec|err")) {   // le son de la page n'est pas encore démarré : on réessaie
            if (decMode != 0 && (radeAuto || ++decTries < 20)) h.postDelayed(decStartRun, radeAuto ? 5000 : 2000);
            else if (decMode != 0) { decoderStop(false); decSetStatus(Lang.T("Pas de son à décoder dans cette page", "No sound to decode in this page")); }
        }
    }

    // Fichiers temporaires : mise à jour téléchargée, sorties d'essai, copie de réglages, cache du navigateur de la page. Ils ne servent
    // plus au prochain lancement : on les supprime au démarrage (restes d'un arrêt brutal) et à la fermeture.
    void cleanTemp(boolean clearWebCache) {
        try {
            java.io.File c = getCacheDir();
            java.io.File[] fs = c.listFiles();
            if (fs != null) for (java.io.File f : fs) if (f.isFile()) f.delete();
            java.io.File e = getExternalCacheDir();
            if (e != null) { fs = e.listFiles(); if (fs != null) for (java.io.File f : fs) if (f.isFile()) f.delete(); }
            java.io.File x = getExternalFilesDir(null);
            if (x != null) for (String n : new String[] { "settings-dump.ini", "rade-test-out.pcm" }) { java.io.File f = new java.io.File(x, n); if (f.exists()) f.delete(); }
            if (clearWebCache && web != null) web.clearCache(true);
        } catch (Exception e) { }
    }

    // Essai des décodeurs CW et RTTY sur un signal synthétique : am start … --es dectest cw|rtty ; résultat dans logcat (étiquette PSDR).
    void decSelfTest(final String kind) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    final StringBuilder out = new StringBuilder();
                    TextEngine.Listener l = new TextEngine.Listener() {
                        public void onText(String s) { out.append(s); }
                        public void onStatus(String s) { android.util.Log.i("PSDR", "DECTEST statut : " + s); }
                    };
                    java.util.Random rnd = new java.util.Random(7);
                    java.util.ArrayList<Double> sig = new java.util.ArrayList<Double>();
                    String msg;
                    if (kind.equals("cw")) {
                        msg = "CQ CQ DE F5PBG F5PBG PSE K";
                        double dot = 1.2 / 18 * 8000, ph = 0;   // 18 mots par minute
                        for (int i = 0; i < 16000; i++) sig.add(0.0);
                        for (char ch : msg.toCharArray()) {
                            if (ch == ' ') { for (int i = 0; i < (int) (4 * dot); i++) sig.add(0.0); continue; }
                            String m = CwEngine.MORSE[CwEngine.CHARS.indexOf(ch)];
                            for (char e : m.toCharArray()) {
                                int len = (int) ((e == '.' ? 1 : 3) * dot);
                                for (int i = 0; i < len; i++) { ph += 2 * Math.PI * 700 / 8000; sig.add(0.5 * Math.sin(ph)); }
                                for (int i = 0; i < (int) dot; i++) sig.add(0.0);
                            }
                            for (int i = 0; i < (int) (2 * dot); i++) sig.add(0.0);
                        }
                        for (int i = 0; i < 16000; i++) sig.add(0.0);
                    } else {
                        msg = "RYRYRYRY CQ CQ DE F5PBG F5PBG K";
                        double bit = 8000 / 45.45, ph = 0, acc = 0;
                        java.util.ArrayList<Integer> bits = new java.util.ArrayList<Integer>();
                        for (int i = 0; i < 40; i++) bits.add(1);
                        boolean figs = false;
                        for (char ch : msg.toCharArray()) {
                            int c = RttyEngine.LTRS.indexOf(ch);
                            if (ch == ' ') c = 4;
                            if (c < 0) continue;
                            bits.add(0);
                            for (int k = 0; k < 5; k++) bits.add((c >> k) & 1);
                            bits.add(1); bits.add(1);
                        }
                        for (int i = 0; i < 40; i++) bits.add(1);
                        for (int b : bits) {
                            double f = b == 1 ? 2125 : 2295;   // mark = tonalité basse
                            acc += bit;
                            while (acc >= 1) { acc -= 1; ph += 2 * Math.PI * f / 8000; sig.add(0.5 * Math.sin(ph)); }
                        }
                    }
                    TextEngine eng = kind.equals("cw") ? new CwEngine(l) : new RttyEngine(l);
                    short[] chunk = new short[1024];
                    int k = 0;
                    for (int i = 0; i < sig.size(); i++) {
                        double v = sig.get(i) + 0.04 * rnd.nextGaussian();
                        chunk[k++] = (short) (Math.max(-1, Math.min(1, v)) * 30000);
                        if (k == chunk.length) { eng.feed(chunk, k); k = 0; }
                    }
                    if (k > 0) eng.feed(chunk, k);
                    android.util.Log.i("PSDR", "DECTEST " + kind + " attendu [" + msg + "] décodé [" + out + "]");
                } catch (Throwable t) { android.util.Log.i("PSDR", "DECTEST erreur " + t); }
            }
        }).start();
    }

    // Essai du moteur RADE complet (bascule du son) : am start … --es radeeng /chemin/fichier.wav (48 kHz, 16 bits, mono) ; le fichier est
    // injecté en temps réel pendant 30 s à la place du son de la page ; les changements d'état de la voix sont dans logcat (PSDR).
    void radeEngineTest(final String path) {
        h.postDelayed(new Runnable() {
            public void run() {
                decoderStart(DEC_RADE);
                if (rade == null) return;
                js("if(window.__sdrlink&&__sdrlink.decStop)__sdrlink.decStop();");   // pas de son de la page : seul le fichier alimente le décodeur
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            java.io.RandomAccessFile f = new java.io.RandomAccessFile(path, "r");
                            f.seek(44);
                            byte[] buf = new byte[96000];
                            for (int s = 0; s < 40; s++) {
                                int r = f.read(buf);
                                if (r < 12) break;
                                int n = r / 12;
                                byte[] o = new byte[n * 2];
                                for (int i = 0; i < n; i++) {
                                    int sum = 0;
                                    for (int k = 0; k < 6; k++) sum += (short) ((buf[(i * 6 + k) * 2] & 255) | (buf[(i * 6 + k) * 2 + 1] << 8));
                                    short v = (short) (sum / 6);
                                    o[2 * i] = (byte) v; o[2 * i + 1] = (byte) (v >> 8);
                                }
                                if (rade != null) rade.feed(o, o.length);
                                Thread.sleep(1000);
                            }
                            f.close();
                            android.util.Log.i("PSDR", "RADEENG fin");
                        } catch (Throwable t) { android.util.Log.i("PSDR", "RADEENG erreur " + t); }
                    }
                }).start();
            }
        }, 4000);
    }

    // Essai du décodeur RADE sur un fichier WAV (48 kHz, 16 bits, mono) : am start … --es radewav /chemin/fichier.wav ;
    // résultats dans logcat (étiquette PSDR) et parole décodée dans files/rade-test-out.pcm (16 kHz, 16 bits).
    void radeSelfTest(final String path) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    java.io.RandomAccessFile f = new java.io.RandomAccessFile(path, "r");
                    long len = f.length();
                    f.seek(44);
                    long h0 = Decoders.radeNew();
                    if (h0 == 0) { android.util.Log.i("PSDR", "RADETEST init impossible"); f.close(); return; }
                    java.io.FileOutputStream fo = new java.io.FileOutputStream(new java.io.File(getExternalFilesDir(null), "rade-test-out.pcm"));
                    byte[] buf = new byte[96000];
                    short[] in8 = new short[8000], out = new short[8000 * 2 + 8000];
                    int[] st = new int[3];
                    long outTotal = 0, chunks = 0, syncChunks = 0, t0 = android.os.SystemClock.elapsedRealtime();
                    int r;
                    while ((r = f.read(buf)) >= 12) {
                        int n = r / 12;
                        for (int i = 0; i < n; i++) {
                            int s = 0;
                            for (int k = 0; k < 6; k++) s += (short) ((buf[(i * 6 + k) * 2] & 255) | (buf[(i * 6 + k) * 2 + 1] << 8));
                            in8[i] = (short) (s / 6);
                        }
                        int w = Decoders.radeProcess(h0, in8, n, out, st);
                        outTotal += w;
                        chunks++;
                        if (st[0] != 0) syncChunks++;
                        byte[] ob = new byte[w * 2];
                        for (int i = 0; i < w; i++) { ob[2 * i] = (byte) out[i]; ob[2 * i + 1] = (byte) (out[i] >> 8); }
                        fo.write(ob);
                        if (chunks % 10 == 0) android.util.Log.i("PSDR", "RADETEST t=" + chunks + "s sync=" + st[0] + " snr=" + st[1] + " foff=" + st[2] / 10.0 + " out=" + outTotal);
                    }
                    fo.close();
                    f.close();
                    Decoders.radeFree(h0);
                    android.util.Log.i("PSDR", "RADETEST fin : " + chunks + " s de son, " + syncChunks + " s synchronisées, " + outTotal + " échantillons de parole, " + (android.os.SystemClock.elapsedRealtime() - t0) + " ms de calcul (fichier " + len + " octets)");
                } catch (Throwable t) { android.util.Log.i("PSDR", "RADETEST erreur " + t); }
            }
        }).start();
    }

    // Essai de bouclage Olivia / Contestia : am start … --es olitest "tons,bande,contestia,snr10,décalage;…" (ex. 32,1000,0,0,0;8,250,0,-100,20) ; résultats dans logcat (PSDR).
    void oliviaLoop(final String spec) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    for (String one : spec.split(";")) {
                        if (one.startsWith("wav:")) {   // wav:tons,bande,contestia,snr10,centre,texte,chemin
                            String[] q = one.substring(4).split(",", 7);
                            int n = Decoders.oliviaMakeWav(Integer.parseInt(q[0]), Integer.parseInt(q[1]), Integer.parseInt(q[2]), q[5], Integer.parseInt(q[3]), Integer.parseInt(q[4]), q[6]);
                            android.util.Log.i("PSDR", "OLIVIA wav " + q[6] + " -> " + n);
                            continue;
                        }
                        String[] p = one.split(",");
                        long t0 = System.currentTimeMillis();
                        String r = Decoders.oliviaLoopTest(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()), "CQ CQ DE F5PBG F5PBG PSE K", Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim()));
                        android.util.Log.i("PSDR", "OLIVIA " + one + " -> " + r + " (" + (System.currentTimeMillis() - t0) + " ms)");
                    }
                    android.util.Log.i("PSDR", "OLIVIA fin");
                } catch (Throwable t) { android.util.Log.i("PSDR", "OLIVIA erreur " + t); }
            }
        }).start();
    }

    // Essai de bouclage RADE V2 (bibliothèque d'essai seulement) : am start … --es radev2test "200,60,100,60" = paires (SNR en dixièmes de dB, durée en s) ;
    // résultats dans logcat (étiquette PSDR).
    void radeV2Loop(final String spec) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    String[] p = spec.split(",");
                    for (int k = 0; k < p.length; k += 2) {
                        int snr = Integer.parseInt(p[k].trim()), sec = p.length > k + 1 ? Integer.parseInt(p[k + 1].trim()) : 60;
                        android.util.Log.i("PSDR", "RADEV2 " + Decoders.radeLoopTest(snr, sec));
                    }
                    android.util.Log.i("PSDR", "RADEV2 fin");
                } catch (Throwable t) { android.util.Log.i("PSDR", "RADEV2 erreur " + t); }
            }
        }).start();
    }

    // Appui long sur Verrou : quitter l'application (utile à ceux qui ne savent pas la fermer). Un REC en cours est d'abord sauvegardé.
    void askQuit() {
        Ui.confirm(this, Lang.T("Quitter l'application", "Quit the app"), Lang.T("Quitter PhoneSDRLink ? Le son et le scan s'arrêtent.", "Quit PhoneSDRLink? The sound and the scan stop."),
            Lang.T("Quitter", "Quit"), Lang.T("Annuler", "Cancel"), new Ui.Yes() {
                public void yes() { if (scanning) stopScan(null); if (recording || recPending) recFinish(); quitApp(); }
            });
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
            if (p.length >= 2 && p[1].equals("ok")) { recResume = false; if (radeVoice) js("if(window.__sdrlink)__sdrlink.recVoice(true);"); status(Lang.T("Le REC continue sur le nouveau WebSDR", "The recording continues on the new WebSDR")); }
            else if (++recTries < 20) h.postDelayed(recResumeRun, 2000);   // le son n'est pas encore démarré : on réessaie
            else { recResume = false; recFinish(); }
            return;
        }
        if (p.length >= 2 && p[1].equals("ok")) {
            try { recRate = Integer.parseInt(p[2]); } catch (Exception e) { recRate = 0; }
            recording = true;
            recStart = System.currentTimeMillis();
            if (radeVoice) js("if(window.__sdrlink)__sdrlink.recVoice(true);");
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
