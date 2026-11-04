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
    Button btnWeb, btnSet, btnMute, btnMode, btnScan, btnScanConf, btnMem, btnStep;
    TextView tvFreq, tvStatus, tvPage;
    FreqBar bar;
    final Handler h = new Handler(Looper.getMainLooper());

    // État de réception
    long freq;
    String mode;
    String sdrKind = "";
    long sdrF;
    boolean muted, callMute, focusMute;
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
        setTheme(st.dark ? android.R.style.Theme_DeviceDefault_NoActionBar : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        freq = st.freqHz;
        mode = st.mode;
        try { InputStream in = getAssets().open("bridge.js"); bridgeScript = Store.readAll(in); in.close(); } catch (Exception e) { }
        build();
        setupWeb();
        refreshAll();
        web.loadUrl(st.lastUrl);
        h.postDelayed(new Runnable() { public void run() { firstRunSearch(); if (st.checkUpdates) checkUpdate(false); } }, 1200);
        h.postDelayed(callWatch, 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        requestFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        if (focusReq != null) audio.abandonAudioFocusRequest(focusReq);
        st.freqHz = freq;
        st.mode = mode;
        st.save();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (web != null) { web.destroy(); }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        layoutForOrientation();
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
        btnWeb = Ui.button(this, "WEBSDR", new View.OnClickListener() { public void onClick(View v) { DlgWebSdr.show(MainActivity.this); } });
        btnWeb.setTypeface(Typeface.DEFAULT_BOLD);
        btnSet = Ui.button(this, "SET", new View.OnClickListener() { public void onClick(View v) { DlgSettings.show(MainActivity.this); } });
        btnSet.setTypeface(Typeface.DEFAULT_BOLD);
        btnMute = Ui.button(this, Lang.T("Muet", "Mute"), new View.OnClickListener() {
            public void onClick(View v) { muted = !muted; applyVolume(); updateButtons(); }
        });
        r1.addView(btnWeb, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, this, 2));
        r1.addView(btnSet, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, this, 2));
        r1.addView(btnMute, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f, this, 2));
        controls.addView(r1);

        // Ligne 2 : fréquence (appui = clavier) et mode
        LinearLayout r2 = Ui.row(this);
        tvFreq = new TextView(this);
        tvFreq.setTextColor(Ui.accent());
        tvFreq.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvFreq.setGravity(Gravity.CENTER);
        tvFreq.setTextSize(36);
        tvFreq.setSingleLine(true);
        tvFreq.setBackground(Ui.round(Ui.panel(), 10, this));
        tvFreq.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { DlgKeypad.show(MainActivity.this); } });
        btnMode = Ui.button(this, "", new View.OnClickListener() { public void onClick(View v) { chooseMode(); } });
        btnMode.setTextSize(18);
        btnMode.setTypeface(Typeface.DEFAULT_BOLD);
        r2.addView(tvFreq, Ui.lp(0, Ui.dp(this, 64), 1, this, 2));
        r2.addView(btnMode, Ui.lp(Ui.dp(this, 92), Ui.dp(this, 64), 0, this, 2));
        controls.addView(r2);

        // Ligne 3 : scan, conf scan, mémoires
        LinearLayout r3 = Ui.row(this);
        btnScan = Ui.button(this, "▶ Scan", new View.OnClickListener() { public void onClick(View v) { onScanButton(); } });
        btnScanConf = Ui.button(this, Lang.T("Conf scan", "Scan setup"), new View.OnClickListener() { public void onClick(View v) { DlgScan.show(MainActivity.this); } });
        btnMem = Ui.button(this, Lang.T("Mémoires", "Memories"), new View.OnClickListener() { public void onClick(View v) { DlgMemory.show(MainActivity.this); } });
        r3.addView(btnScan, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, this, 2));
        r3.addView(btnScanConf, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, this, 2));
        r3.addView(btnMem, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1, this, 2));
        controls.addView(r3);

        // Ligne 4 : barre à glisser et pas
        LinearLayout r4 = Ui.row(this);
        bar = new FreqBar(this);
        bar.hint = Lang.T("◀ glisser : − fréquence | + fréquence ▶", "◀ swipe: − frequency | + frequency ▶");
        bar.setListener(new FreqBar.Listener() { public void onSteps(int n, double ms) { tuneBySteps(n, ms); } });
        btnStep = Ui.button(this, "", new View.OnClickListener() { public void onClick(View v) { chooseStep(); } });
        r4.addView(bar, Ui.lp(0, Ui.dp(this, 52), 1, this, 2));
        r4.addView(btnStep, Ui.lp(Ui.dp(this, 92), Ui.dp(this, 52), 0, this, 2));
        controls.addView(r4);

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
        zoom.addView(plus);
        zoom.addView(minus);
        FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        zp.setMargins(0, 0, Ui.dp(this, 10), Ui.dp(this, 10));
        webFrame.addView(zoom, zp);

        root.addView(controls);
        root.addView(webFrame);
        setContentView(root);
        layoutForOrientation();
    }

    Button zoomButton(String text, final boolean in) {
        Button b = Ui.button(this, text, new View.OnClickListener() {
            public void onClick(View v) { if (in) web.zoomIn(); else web.zoomOut(); }
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
        View old = root.getChildAt(0) != webFrame ? root.getChildAt(0) : null;
        if (old != null) root.removeView(old);
        View top;
        if (land) {
            ScrollView sv = new ScrollView(this);
            sv.addView(controls);
            top = sv;
            root.addView(top, 0, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f));
            webFrame.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.58f));
        } else {
            root.addView(controls, 0, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            webFrame.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
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
        btnMode.setText(modeLabel(mode));
        btnStep.setText(stepText(Store.STEPS[st.stepIdx]));
    }

    void updateButtons() {
        boolean m = muted || callMute || focusMute;
        btnMute.setText(m ? Lang.T("🔇 Muet", "🔇 Muted") : Lang.T("Muet", "Mute"));
        Ui.paint(btnMute, muted, Ui.danger());
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
        web.setBackgroundColor(Color.BLACK);
        installBridgeScripts();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap icon) {
                sdrKind = "";
                sdrF = 0;
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) injectFallback();
                tvPage.setText(url);
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) injectFallback();
                applyAudio();
                st.lastUrl = url;
                tvPage.setText(url);
            }
            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                status(Lang.T("La page a planté : rechargement", "The page crashed: reloading"));
                h.post(new Runnable() { public void run() { web.reload(); } });
                return false;
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

    void js(String code) {
        web.evaluateJavascript("(function(){try{" + code + "}catch(e){}})()", null);
    }

    void applyVolume() {
        boolean m = muted || callMute || focusMute;
        js("if(window.__sdrlink)__sdrlink.setVolume(" + (m ? 0 : 1) + ");");
    }

    void applyAudio() {
        applyVolume();
        js("if(window.__sdrlink){__sdrlink.setNotch(" + st.autoNotch + ");__sdrlink.setDnr(" + st.autoDnr + ");}");
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

    void setSdr(long hz, String sdrMode) {
        js("if(window.__sdrlink)__sdrlink.set(" + hz + ",'" + sdrMode + "');");
    }

    void openUrl(String url) {
        url = url == null ? "" : url.trim();
        if (url.isEmpty()) return;
        if (!url.contains("://")) url = "http://" + url;
        sdrKind = "";
        web.loadUrl(url);
    }

    String currentUrl() { return web.getUrl() == null ? st.lastUrl : web.getUrl(); }
    String currentTitle() { return web.getTitle() == null ? "" : web.getTitle(); }

    // Messages du pont : « type|Hz|mode[|u] » (état du WebSDR) ou « click » (appui dans la page).
    void onWebMessage(String msg) {
        if ("click".equals(msg)) { onPageClick(); return; }
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
        if (first) applyAudio();
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
        if (!fam.isEmpty() && !toSdr(mode).equals(fam)) mode = fromFamily(fam);
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

    void setMode(String m) {
        mode = m;
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
        long step = accelerate(Store.STEPS[st.stepIdx], ms);
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
        String[] names = new String[Store.STEPS.length];
        for (int i = 0; i < names.length; i++) names[i] = stepText(Store.STEPS[i]);
        Ui.choose(this, Lang.T("Pas :", "Step:"), names, new Ui.Index() { public void chose(int i) { st.stepIdx = i; updateDisplays(); st.save(); } });
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
        if (!m.mode.isEmpty()) mode = m.mode;
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

    // Vérifie la dernière release GitHub. manual : affiche toujours un résultat.
    void checkUpdate(final boolean manual) {
        new Thread(new Runnable() {
            public void run() {
                String tag = null, url = null;
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
                } catch (Exception e) { }
                final String ftag = tag, furl = url;
                h.post(new Runnable() {
                    public void run() {
                        if (ftag == null) { if (manual) Ui.message(MainActivity.this, Lang.T("PhoneSDRLink : mises à jour", "PhoneSDRLink: updates"), Lang.T("Impossible de joindre GitHub pour vérifier les mises à jour.", "Could not reach GitHub to check for updates.")); return; }
                        if (!isNewer(ftag, versionName())) {
                            if (manual) Ui.message(MainActivity.this, Lang.T("PhoneSDRLink : mises à jour", "PhoneSDRLink: updates"),
                                Lang.T("Vous utilisez la dernière version.", "You are using the latest version.") + "\n" + Lang.F("Version utilisée : V{0}", "Version in use: V{0}", versionName()));
                            return;
                        }
                        updateUrl = furl;
                        status(Lang.F("⚠ Mise à jour V{0} disponible : cliquez pour la télécharger", "⚠ Update V{0} available: click to download it", ftag));
                        if (manual) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(furl)));
                    }
                });
            }
        }).start();
    }
}
