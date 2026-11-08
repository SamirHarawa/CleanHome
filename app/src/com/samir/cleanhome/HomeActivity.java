package com.samir.cleanhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuff;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CleanHome: an Android TV port of CleanFrame's Home (github.com/nthnclrk/CleanFrame, MIT).
 * Clean photo wallpaper (online daily photo or time of day), large clock with the date,
 * quiet outline utilities, and a frosted dock of white app marks with a gliding focus plate.
 * All geometry is in CleanFrame's 1920x1080 stage pixels.
 */
public class HomeActivity extends Activity {

    static final int MAX_DOCK = 6;
    static final int DOCK_VERSION = 2;
    /** Until the dock is edited, it shows the first six of these that are installed. */
    static final String[] DEFAULT_DOCK = {
            "com.netflix.ninja", "com.google.android.youtube.tv", "com.amazon.amazonvideo.livingroom",
            "com.maxliv.live", "com.rmedia.rplayer", "com.amhkor.voltratv",
            "com.disney.disneyplus", "com.wbd.stream", "com.apple.atve.androidtv.appletv", "com.spotify.tv.android",
            "com.plexapp.android", "com.hulu.livingroomplus", "com.tubitv", "com.google.android.youtube.tvmusic",
            "com.google.android.apps.youtube.kids", "com.android.vending"};
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    // CleanFrame palette and motion
    static final int INK = 0xFFF6F1E7, MUTE = 0xC2F6F1E7, FAINT = 0x94F6F1E7;
    static final int SOLID = 0xFFF3F3EF, SOLID_INK = 0xFF141618;
    static final PathInterpolator EASE = Glide.EASE;
    static final int T = 200;

    // Geometry (stage px)
    static final int DOCK_X = 105, DOCK_Y = 768, DOCK_W = 1712, DOCK_H = 216, DOCK_R = 46;
    static final int TILE_W = 264, TILE_H = 184, TILE_GAP = 8, TILE_R = 30;
    /** All Apps: five columns justified across the sheet (1920 - 2 x 124 wide). */
    static final int GRID_COLS = 5, GRID_W = 1920 - 2 * 124, GRID_GAP = (GRID_W - GRID_COLS * TILE_W) / (GRID_COLS - 1);
    static final float MARK_SCALE = .92f;
    static final float MAT_BLUR = 56, SHEET_BLUR = 80;

    // Wallpapers (Unsplash License, via CleanFrame) and the time-of-day schedule
    static final String[] WALL_IDS = {"misty", "cove", "forest", "valley", "night", "peaks", "bridge",
            "ridge", "golden", "summit", "goldcoast", "sydney", "amber", "alpine"};
    static final String[] WALL_NAMES = {"Misty coast", "Quiet cove", "Forest mist", "Mountain valley",
            "Desert stars", "Blue peaks", "Bridge at dusk", "Sunlit ridges", "Golden hour", "Sunlit summits",
            "Gold Coast dusk", "Sydney Harbour", "Amber hills", "Alpine glow"};
    static final float[] WALL_VEIL = {.20f, .12f, .12f, .12f, .20f, .36f, .44f, .12f, .30f, .20f, .20f, .44f, .20f, .20f};
    static final int[] SLOT_START = {5, 7, 10, 14, 17, 19, 21};
    static final String[] SLOT_NAMES = {"Dawn", "Morning", "Midday", "Afternoon", "Golden hour", "Dusk", "Night"};
    static final String[][] SLOT_WALLS = {
            {"forest", "misty"}, {"misty", "peaks", "alpine"}, {"cove", "sydney", "valley"},
            {"ridge", "valley", "summit"}, {"golden", "amber"}, {"goldcoast", "bridge"}, {"night"}};

    final Handler h = new Handler(Looper.getMainLooper());
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    SharedPreferences prefs;
    float s;
    int SW, SH;
    Typeface f500, f600;
    FrameLayout root, dock, overlay, shelfBox;
    ImageView bgA, bgB, matA, matB;
    View matVeil;
    LinearLayout shelfRow, utils;
    HorizontalScrollView shelf;
    TextView clock, date, utilLabel, toast, wxTemp, wxText, wxCity;
    LinearLayout wx;
    ImageView wxIcon;
    boolean wxFetching;

    /** Puts a reading on screen (fading it in the first time); hides the widget if there is none. */
    void showWeather(Weather.Now n, boolean animate) {
        if (wx == null) return;
        if (n == null) { if (wx.getVisibility() == View.VISIBLE && Weather.cached(prefs) == null) wx.setVisibility(View.INVISIBLE); return; }
        wxIcon.setImageDrawable(new Weather.Icon(Weather.kind(n.code), n.day, 0xFFF6F1E7));
        wxTemp.setText(Math.round(n.temp) + "°");
        wxCity.setText(n.city == null ? "" : n.city);
        wxText.setText(Weather.label(n.code) + "  ·  H " + Math.round(n.hi) + "°  L " + Math.round(n.lo) + "°");
        fitWeather();
        if (wx.getVisibility() != View.VISIBLE) {
            wx.setVisibility(View.VISIBLE);
            if (animate) Motion.rise(wx, px(8), 0, 600);
        }
    }

    static final float WX_TEXT = 23, WX_CITY = 26;

    /** Keeps the conditions line within the width of the date above it, shrinking the text a little if needed. */
    void fitWeather() {
        if (wxText == null || date == null) return;
        float dateW = date.getPaint().measureText(String.valueOf(date.getText()));
        if (dateW <= 0) return;
        android.graphics.Paint p = new android.graphics.Paint(wxText.getPaint());
        p.setTextSize(px(WX_TEXT));
        float w = p.measureText(String.valueOf(wxText.getText())) + px(2);
        float size = w <= dateW ? WX_TEXT : Math.max(15, WX_TEXT * dateW / w);
        wxText.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));

        // The city sits beside the temperature in whatever room is left within the date's width.
        if (wxCity != null && wxCity.length() > 0) {
            float used = px(52 + 12 + 16) + wxTemp.getPaint().measureText(String.valueOf(wxTemp.getText()));
            float room = dateW - used;
            android.graphics.Paint cp = new android.graphics.Paint(wxCity.getPaint());
            cp.setTextSize(px(WX_CITY));
            float cw = cp.measureText(String.valueOf(wxCity.getText()));
            float cs = cw <= room ? WX_CITY : Math.max(18, WX_CITY * room / cw);
            wxCity.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(cs));
            wxCity.setMaxWidth(Math.max(0, Math.round(room))); // anything longer still ends with "…"
        }
    }

    /** Refreshes the weather in the background when it is due (every 30 minutes). */
    void refreshWeather() {
        if (wxFetching || !Weather.due(prefs)) return;
        wxFetching = true;
        new Thread(() -> {
            Weather.Now n = Weather.fetch(prefs);
            h.post(() -> { wxFetching = false; if (n != null) showWeather(n, true); });
        }).start();
    }
    Glide dockGlide;
    final Map<String, ResolveInfo> apps = new LinkedHashMap<>();
    int lastDockFocus = 0;
    /** Test switch: adb shell am start -n com.samir.cleanhome/.HomeActivity --ez forceAuto true */
    static boolean forceAuto = false;

    Runnable tick;
    /** Fires right on each minute boundary, so the clock changes exactly when the minute does. */
    Runnable minute;
    { minute = () -> { updateClock(); h.postDelayed(minute, 60_000 - System.currentTimeMillis() % 60_000 + 30); }; }
    { tick = () -> { updateClock(); refreshWeather(); fetchDaily(false); fetchGallery(false); if (overlay.getVisibility() != View.VISIBLE) applyWallpaper(false); h.postDelayed(tick, 15000); }; }
    final Runnable hideToast = () -> toast.animate().alpha(0f).translationY(-px(8)).setDuration(220).start();

    int px(float v) { return Math.round(v * s); }

    // ================================================================ lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SW = getResources().getDisplayMetrics().widthPixels;
        SH = getResources().getDisplayMetrics().heightPixels;
        s = SW / 1920f;
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        if (prefs.getInt("dockv", 0) < DOCK_VERSION) {
            prefs.edit().remove("dock").putInt("dockv", DOCK_VERSION).putString("wall", "daily").apply();
        }
        if (prefs.getInt("wallv", 0) < 3) prefs.edit().putInt("wallv", 3).putString("wall", "google").apply();
        if (prefs.getInt("dailyv", 0) < 2) { // re-fetch daily photos in 4K
            prefs.edit().putInt("dailyv", 2).putLong("daily_checked", 0).apply();
        }
        f500 = font(500);
        f600 = font(600);

        root = new FrameLayout(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF0D1114));
        root.setClipChildren(false);

        bgA = photoView();
        bgB = photoView();
        bgB.setAlpha(0f);
        bgB.setVisibility(View.INVISIBLE);
        root.addView(bgA, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(bgB, new FrameLayout.LayoutParams(MATCH, MATCH));

        // Large clock, the date under it.
        clock = text(84, INK, f500, -2.6f);
        clock.setGravity(Gravity.CENTER_VERTICAL);
        clock.setFontFeatureSettings("tnum"); // equal-width digits: the clock keeps its width as minutes change
        clock.setShadowLayer(px(6), 0, px(1), 0x40000000);
        root.addView(clock, at(136, 52, WRAP, 104));
        date = text(30, 0xE6F6F1E7, f500, -.2f);
        date.setShadowLayer(px(4), 0, px(1), 0x4D000000);
        root.addView(date, at(142, 158, WRAP, 40));

        // Weather under the date, never wider than it: a coloured icon with the temperature, then the
        // conditions and today's high and low on a second line.
        wx = new LinearLayout(this);
        wx.setOrientation(LinearLayout.VERTICAL);
        LinearLayout wxTop = new LinearLayout(this);
        wxTop.setOrientation(LinearLayout.HORIZONTAL);
        wxTop.setGravity(Gravity.CENTER_VERTICAL);
        wxIcon = new ImageView(this);
        wxTop.addView(wxIcon, new LinearLayout.LayoutParams(px(52), px(52)));
        wxTemp = text(42, INK, f500, -1f);
        wxTemp.setShadowLayer(px(5), 0, px(1), 0x4D000000);
        LinearLayout.LayoutParams tlp2 = new LinearLayout.LayoutParams(WRAP, WRAP);
        tlp2.leftMargin = px(12);
        wxTop.addView(wxTemp, tlp2);
        wxCity = text(WX_CITY, 0xE6F6F1E7, f500, -.2f);
        wxCity.setShadowLayer(px(9), 0, px(1), 0xB3000000);
        wxCity.setSingleLine(true);
        wxCity.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(WRAP, WRAP);
        clp.leftMargin = px(16);
        clp.topMargin = px(6); // sits on the temperature's baseline area
        wxTop.addView(wxCity, clp);
        wx.addView(wxTop, new LinearLayout.LayoutParams(WRAP, WRAP));
        wxText = text(WX_TEXT, 0xDEF6F1E7, f500, -.1f);
        wxText.setShadowLayer(px(9), 0, px(1), 0xB3000000); // a soft dark halo so it reads on busy photos
        wxText.setSingleLine(true);
        LinearLayout.LayoutParams xlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        xlp.topMargin = px(6);
        xlp.leftMargin = px(2);
        wx.addView(wxText, xlp);
        wx.setVisibility(View.INVISIBLE);
        root.addView(wx, at(140, 252, WRAP, WRAP));
        showWeather(Weather.cached(prefs), false);

        utils = new LinearLayout(this);
        utils.setOrientation(LinearLayout.HORIZONTAL);
        utils.addView(utilButton(R.drawable.g_search, () -> "Search", v -> showSearch()));
        View net = utilButton(R.drawable.g_ethernet, () -> netLabel, v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        netIcon = (ImageView) ((ViewGroup) net).getChildAt(0);
        utils.addView(net);
        utils.addView(utilButton(R.drawable.g_art, () -> "Wallpaper", v -> showWallpapers()));
        utils.addView(utilButton(R.drawable.g_apps, () -> "Apps", v -> showApps()));
        utils.addView(utilButton(R.drawable.g_settings, () -> "Quick settings", v -> showQuickSettings()));
        View acct = utilButton(R.drawable.g_person, () -> "Account", v -> openAccount());
        accountIcon = (ImageView) ((ViewGroup) acct).getChildAt(0);
        utils.addView(acct);
        refreshAvatar();
        pinEnds(utils);
        FrameLayout.LayoutParams ulp = new FrameLayout.LayoutParams(WRAP, px(64), Gravity.TOP | Gravity.END);
        ulp.topMargin = px(76);
        ulp.rightMargin = px(122);
        root.addView(utils, ulp);

        utilLabel = text(28, INK, f500, -.2f);
        utilLabel.setShadowLayer(px(4), 0, px(1), 0x660E0A06);
        utilLabel.setAlpha(0f);
        root.addView(utilLabel, at(0, 146, WRAP, 36));

        buildDockFrame();

        toast = text(28, SOLID_INK, f500, -.2f);
        toast.setPadding(px(28), px(14), px(28), px(14));
        toast.setBackground(round(SOLID, px(32)));
        toast.setAlpha(0f);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        tlp.topMargin = px(84);
        root.addView(toast, tlp);

        overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(MATCH, MATCH));

        setContentView(root);
        hideBars();

        android.content.IntentFilter pf = new android.content.IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        try {
            cm = (android.net.ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (Exception ignored) { }
        updateNet();
        animateIn();
        if (getIntent() != null && getIntent().getBooleanExtra("quickSettings", false)) h.postDelayed(this::showQuickSettings, 400);
    }

    final android.content.BroadcastReceiver packages = new PackageReceiver(this);

    /** New or updated apps get their white mark straight away. */
    static class PackageReceiver extends android.content.BroadcastReceiver {
        final HomeActivity a;
        PackageReceiver(HomeActivity a) { this.a = a; }
        @Override public void onReceive(android.content.Context c, Intent i) {
            a.loadApps();
            a.buildDock();
            if (a.overlay.getVisibility() != View.VISIBLE) a.focusDock(a.lastDockFocus);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        loadApps();
        buildDock();
        fetchDaily(false);
        fetchGallery(false);
        refreshAvatar();
        applyWallpaper(true);
        h.removeCallbacks(tick);
        h.post(tick);
        h.removeCallbacks(minute);
        h.postDelayed(minute, 60_000 - System.currentTimeMillis() % 60_000 + 30);
        if (overlay.getVisibility() != View.VISIBLE) focusDock(lastDockFocus);
        live = this;
        // Woken from the screensaver (not just passing through while it starts under the cover).
        h.postDelayed(() -> {
            if (live == this && HomeKeyService.instance == null && !setupHintShown) {
                setupHintShown = true;
                showToast("Finish setup from a computer: github.com/SamirHarawa/CleanHome");
            }
        }, 4000);
        if (dreamPaused && !HomeKeyService.coverShowing()) { dreamPaused = false; wakeFromDream(); }
        DnsPause.resume(this); // back on Home: ad blocking is always on
        // TCL blocks third-party services at startup; while CleanHome is on screen it may start them.
        h.postDelayed(() -> {
            if (HomeKeyService.instance == null
                    && checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    String me = getPackageName() + "/" + HomeKeyService.class.getName();
                    android.content.ContentResolver cr = getContentResolver();
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "");
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, me);
                    Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                } catch (Exception ignored) { }
            }
            if (NotifService.instance == null) {
                try { android.service.notification.NotificationListenerService.requestRebind(
                        new android.content.ComponentName(this, NotifService.class)); } catch (Exception ignored) { }
            }
        }, 1500);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (curtain == null || curtain.getVisibility() != View.VISIBLE) animateIn(); // back from an app
    }

    /** The Home screen while it is in front; the remote's settings key opens our panel then. */
    static HomeActivity live;

    @Override
    protected void onPause() {
        super.onPause();
        if (live == this) live = null;
        if (dreaming) dreamPaused = true;
        h.removeCallbacks(minute);
        h.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(packages); } catch (Exception ignored) { }
        try { cm.unregisterNetworkCallback(netCallback); } catch (Exception ignored) { }
        worker.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("quickSettings", false)) {
            if (overlay.getVisibility() == View.VISIBLE) dismissOverlay(false); else showQuickSettings();
            return;
        }
        if (intent.hasExtra("setDock")) { applyDockExtra(intent); return; }
        if (intent.hasExtra("forceAuto")) { forceAuto = intent.getBooleanExtra("forceAuto", false); buildDock(); showApps(); return; }
        if (overlay.getVisibility() == View.VISIBLE) dismissOverlay(true);
        else focusDock(lastDockFocus);
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) {
            clickSound();
            dismissOverlay(true);
            return;
        }
        startScreensaver(); // Back on Home: the Google TV screensaver
    }

    /** A gentle arrival: the clock and icons fade in, the dock eases up. */
    void animateIn() { animateIn(0, 1f); }

    /** delay shifts the whole arrival; stretch slows it (1 = normal). */
    void animateIn(long delay, float stretch) {
        // Clock, then date, then the icons one after another; the dock rises and its apps follow in a wave.
        Motion.rise(clock, px(18), delay + (long) (40 * stretch), (long) (620 * stretch));
        Motion.rise(date, px(14), delay + (long) (110 * stretch), (long) (620 * stretch));
        if (wx.getVisibility() == View.VISIBLE) Motion.rise(wx, px(12), delay + (long) (170 * stretch), (long) (620 * stretch));
        utils.setAlpha(1f);
        for (int i = 0; i < utils.getChildCount(); i++)
            Motion.rise(utils.getChildAt(i), px(10), delay + (long) ((120 + 40 * i) * stretch), (long) (520 * stretch));
        Motion.rise(dock, px(36), delay + (long) (60 * stretch), (long) (680 * stretch));
        h.post(() -> {
            for (int i = 0; i < shelfRow.getChildCount(); i++)
                Motion.rise(shelfRow.getChildAt(i), px(16), delay + (long) ((160 + 45 * i) * stretch), (long) (560 * stretch));
        });
    }

    void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    // ================================================================ network status

    android.net.ConnectivityManager cm;
    ImageView netIcon;
    String netLabel = "Network";
    final NetCallback netCallback = new NetCallback(this);

    static class NetCallback extends android.net.ConnectivityManager.NetworkCallback {
        final HomeActivity a;
        NetCallback(HomeActivity a) { this.a = a; }
        @Override public void onCapabilitiesChanged(android.net.Network n, android.net.NetworkCapabilities c) { a.h.post(a::updateNet); }
        @Override public void onLost(android.net.Network n) { a.h.postDelayed(a::updateNet, 300); }
        @Override public void onAvailable(android.net.Network n) { a.h.post(a::updateNet); }
    }

    /** Ethernet, Wi-Fi strength or offline, live in the top-right icon. */
    void updateNet() {
        int icon;
        String label;
        android.net.NetworkCapabilities c = null;
        try { c = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork()); } catch (Exception ignored) { }
        if (c == null) {
            icon = R.drawable.g_wifioff;
            label = "Offline";
        } else {
            boolean online = c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            if (c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                int sig = c.getSignalStrength();
                int lv = sig == Integer.MIN_VALUE ? 3 : sig > -60 ? 3 : sig > -72 ? 2 : 1;
                icon = lv == 3 ? R.drawable.g_wifi3 : lv == 2 ? R.drawable.g_wifi2 : R.drawable.g_wifi1;
                label = "Wi-Fi · " + (lv == 3 ? "Strong signal" : lv == 2 ? "Good signal" : "Weak signal");
            } else {
                icon = R.drawable.g_ethernet;
                label = "Ethernet";
            }
            label += online ? "" : " · No internet";
            if (online && !label.startsWith("Wi-Fi")) label += " · Connected";
        }
        netLabel = label;
        if (netIcon != null) netIcon.setImageResource(icon);
        if (focusedUtilName != null && utils.findFocus() != null) utilLabel.setText(focusedUtilName.get());
    }

    // ================================================================ sound

    /** Every focus move plays the system navigation beep, including inside scrolling rows and grids. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0)
            android.util.Log.i("CleanHomeKey", "key " + e.getKeyCode() + " " + KeyEvent.keyCodeToString(e.getKeyCode()) + " scan=" + e.getScanCode());
        if (Remote.handle(this, e)) return true;
        View before = getCurrentFocus();
        int kc = e.getKeyCode();
        if (before != null && (kc == KeyEvent.KEYCODE_DPAD_CENTER || kc == KeyEvent.KEYCODE_ENTER || kc == KeyEvent.KEYCODE_NUMPAD_ENTER)
                && (before.findViewWithTag("mark") != null || before.getParent() == utils)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) Motion.press(before, true);
            else if (e.getAction() == KeyEvent.ACTION_UP) Motion.press(before, false);
        }
        boolean handled = super.dispatchKeyEvent(e);
        if (handled && e.getAction() == KeyEvent.ACTION_DOWN && getCurrentFocus() != before) {
            int fx;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT: fx = AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
                case KeyEvent.KEYCODE_DPAD_RIGHT: fx = AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
                case KeyEvent.KEYCODE_DPAD_UP: fx = AudioManager.FX_FOCUS_NAVIGATION_UP; break;
                case KeyEvent.KEYCODE_DPAD_DOWN: fx = AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
                default: fx = -1;
            }
            if (fx >= 0) audio().playSoundEffect(fx);
        }
        return handled;
    }

    AudioManager am;
    AudioManager audio() {
        if (am == null) am = (AudioManager) getSystemService(AUDIO_SERVICE);
        return am;
    }

    void clickSound() { audio().playSoundEffect(AudioManager.FX_KEY_CLICK); }

    // ================================================================ wallpaper: choice

    static int slotFor(int hour) {
        int slot = SLOT_START.length - 1;
        for (int i = 0; i < SLOT_START.length; i++) if (hour >= SLOT_START[i]) slot = i;
        return slot;
    }

    String autoWall() {
        Calendar c = Calendar.getInstance();
        String[] set = SLOT_WALLS[slotFor(c.get(Calendar.HOUR_OF_DAY))];
        return set[c.get(Calendar.DAY_OF_YEAR) % set.length];
    }

    static final long ROTATE_MS = 15 * 60_000L;

    String wantedWall() {
        String w = prefs.getString("wall", "auto");
        if ("google".equals(w)) {
            List<String[]> g = galleryList();
            if (!g.isEmpty()) return "gal:" + g.get((int) ((System.currentTimeMillis() / ROTATE_MS) % g.size()))[0];
            List<String[]> d = dailyList();
            return d.isEmpty() ? autoWall() : "bing:" + d.get(0)[0];
        }
        if ("auto".equals(w)) return autoWall();
        if ("daily".equals(w)) {
            List<String[]> d = dailyList();
            return d.isEmpty() ? autoWall() : "bing:" + d.get(0)[0];
        }
        if (w.startsWith("bing:") && !dailyFile(w.substring(5)).exists()) return autoWall();
        return w;
    }

    static int wallIndex(String id) {
        for (int i = 0; i < WALL_IDS.length; i++) if (WALL_IDS[i].equals(id)) return i;
        return 0;
    }

    // ================================================================ wallpaper: online daily photos (Bing, 4K)

    List<String[]> dailyList() {
        List<String[]> out = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.getString("daily", "[]"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                String d = o.getString("date");
                if (dailyFile(d).exists()) out.add(new String[]{d, o.optString("title"), o.optString("copyright")});
            }
        } catch (Exception ignored) { }
        return out;
    }

    File dailyFile(String d) { return new File(getFilesDir(), "d4k-" + d + ".jpg"); }

    File galFile(String name) { return new File(getFilesDir(), name); }

    /** Google's Chromecast ambient gallery: {file, photographer}, in download order. */
    List<String[]> galleryList() {
        List<String[]> out = new ArrayList<>();
        try {
            org.json.JSONArray a = new org.json.JSONArray(prefs.getString("gallery", "[]"));
            for (int i = 0; i < a.length(); i++) {
                org.json.JSONObject o = a.getJSONObject(i);
                if (galFile(o.getString("file")).exists()) out.add(new String[]{o.getString("file"), o.optString("author")});
            }
        } catch (Exception ignored) { }
        return out;
    }

    volatile boolean galFetching = false;

    /** Downloads Google's ambient photos in 4K (about 20), refreshed every 6 hours. */
    void fetchGallery(boolean force) {
        long now = System.currentTimeMillis();
        if (galFetching || (!force && now - prefs.getLong("gallery_checked", 0) < 6 * 3600_000L)) return;
        galFetching = true;
        new Thread(() -> {
            boolean first = galleryList().isEmpty();
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL("https://clients3.google.com/cast/chromecast/home").openConnection();
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko) CrKey/1.56.500000");
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                String html;
                try (InputStream in = c.getInputStream()) {
                    java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[16384];
                    for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
                    html = b.toString("UTF-8");
                } finally { c.disconnect(); }
                int a = html.indexOf("JSON.parse('"), e = html.indexOf("'))", a);
                org.json.JSONArray items = new org.json.JSONArray(unescapeJs(html.substring(a + 12, e))).getJSONArray(0);
                org.json.JSONArray keep = new org.json.JSONArray();
                Set<String> names = new HashSet<>();
                int got = 0;
                for (int i = 0; i < items.length() && got < 20; i++) {
                    org.json.JSONArray it = items.getJSONArray(i);
                    String url = it.optString(0), author = it.optString(1);
                    if (!url.contains("googleusercontent.com") || author.isEmpty() || author.equals("null")) continue;
                    String big = url.replaceAll("=w\\d+-h\\d+", "=w3840-h2160");
                    String name = "g-" + Integer.toHexString(url.hashCode()) + ".jpg";
                    File f = galFile(name);
                    if (!f.exists()) {
                        File tmp = new File(getFilesDir(), "gdl.tmp");
                        try { httpFile(big, tmp); } catch (Exception ex) { httpFile(url, tmp); }
                        if (!tmp.renameTo(f)) continue;
                    }
                    keep.put(new org.json.JSONObject().put("file", name).put("author", author));
                    names.add(name);
                    got++;
                    if (first && got == 3) {
                        prefs.edit().putString("gallery", keep.toString()).apply();
                        h.post(() -> { if (overlay.getVisibility() != View.VISIBLE) applyWallpaper(false); });
                    }
                }
                if (got > 0) {
                    File[] old = getFilesDir().listFiles((dir, n) -> n.startsWith("g-"));
                    if (old != null) for (File f : old) if (!names.contains(f.getName())) f.delete();
                    prefs.edit().putString("gallery", keep.toString()).putLong("gallery_checked", System.currentTimeMillis()).apply();
                }
            } catch (Exception ignored) {
                // Offline or the page changed: Bing's daily photo and the bundled photos cover the gap.
            } finally {
                galFetching = false;
            }
            if (first) h.post(() -> { if (overlay.getVisibility() != View.VISIBLE) applyWallpaper(false); });
        }).start();
    }

    /** Decodes a JavaScript single-quoted string literal (hex, unicode and slash escapes). */
    static String unescapeJs(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch != '\\' || i + 1 >= s.length()) { b.append(ch); continue; }
            char n = s.charAt(++i);
            if (n == 'x' && i + 2 < s.length()) { b.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16)); i += 2; }
            else if (n == 'u' && i + 4 < s.length()) { b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
            else if (n == 'n') b.append('\n');
            else b.append(n);
        }
        return b.toString();
    }

    volatile boolean fetching = false;

    /** Bing's photo of the day and the previous week, in 4K, checked at most every 3 hours. */
    void fetchDaily(boolean force) {
        long now = System.currentTimeMillis();
        if (fetching || (!force && now - prefs.getLong("daily_checked", 0) < 3 * 3600_000L)) return;
        fetching = true;
        new Thread(() -> {
            boolean changed = false;
            try {
                Locale l = Locale.getDefault();
                String mkt = l.getCountry().isEmpty() ? "en-GB" : l.getLanguage() + "-" + l.getCountry();
                org.json.JSONArray imgs = new org.json.JSONObject(httpText(
                        "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=" + mkt)).getJSONArray("images");
                org.json.JSONArray keep = new org.json.JSONArray();
                Set<String> names = new HashSet<>();
                for (int i = 0; i < imgs.length(); i++) {
                    org.json.JSONObject im = imgs.getJSONObject(i);
                    String d = im.getString("startdate");
                    File f = dailyFile(d);
                    if (!f.exists()) {
                        File tmp = new File(getFilesDir(), "dl.tmp");
                        try { httpFile("https://www.bing.com" + im.getString("urlbase") + "_UHD.jpg", tmp); }
                        catch (Exception e) { httpFile("https://www.bing.com" + im.getString("urlbase") + "_1920x1080.jpg", tmp); }
                        if (!tmp.renameTo(f)) continue;
                        changed = true;
                    }
                    String cr = im.optString("copyright"), title = im.optString("title");
                    if (title.isEmpty() || title.equals("Info")) title = cr.contains(" (") ? cr.substring(0, cr.indexOf(" (")) : cr;
                    keep.put(new org.json.JSONObject().put("date", d).put("title", title).put("copyright", cr));
                    names.add(f.getName());
                }
                File[] old = getFilesDir().listFiles((dir, n) -> n.startsWith("daily-") || n.startsWith("d4k-"));
                if (old != null) for (File f : old) if (!names.contains(f.getName())) f.delete();
                prefs.edit().putString("daily", keep.toString()).putLong("daily_checked", System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
                // Offline: keep what we have; the time-of-day photos cover the gap.
            } finally {
                fetching = false;
            }
            if (changed) h.post(() -> { if (overlay.getVisibility() != View.VISIBLE) applyWallpaper(false); });
        }).start();
    }

    static String httpText(String url) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    static void httpFile(String url, File out) throws java.io.IOException {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        if (c.getResponseCode() != 200) { c.disconnect(); throw new java.io.IOException("HTTP " + c.getResponseCode()); }
        try (InputStream in = c.getInputStream(); java.io.FileOutputStream o = new java.io.FileOutputStream(out)) {
            byte[] buf = new byte[32768];
            for (int n; (n = in.read(buf)) > 0; ) o.write(buf, 0, n);
        } finally { c.disconnect(); }
    }

    // ================================================================ wallpaper: clean rendering

    /** A wallpaper prepared for display: the photo with overlays, the dock glass and the sheet glass. */
    static final class Look {
        String id;
        Bitmap bg, mat, sheet;
        float veil, sheetExtra;
    }

    final Map<String, Look> looks = new LinkedHashMap<>();
    Look current;
    String currentWall;
    volatile int gen = 0;

    void applyWallpaper(boolean instant) { showWall(wantedWall(), instant); }

    void showWall(String id, boolean instant) {
        if (id.equals(currentWall)) return;
        currentWall = id;
        final int my = ++gen;
        Look cached = looks.get(id);
        if (cached != null) { present(cached, instant); return; }
        worker.execute(() -> {
            if (my != gen) return;
            Bitmap w = decodeWall(id);
            if (w == null || my != gen) return;
            Look L = new Look();
            L.id = id;
            Frost.Small sm = Frost.small(w, SW, SH);
            // Adaptive contrast: where the photo is bright behind the clock or the icons, a soft dim
            // is baked in under them, scaled to how bright it is. Dark photos are left untouched.
            float lumTL = Frost.lum(sm, px(100), px(40), px(760), px(340));
            float lumTR = Frost.lum(sm, px(1380), px(50), px(1860), px(200));
            Frost.Dim[] dims = {
                    new Frost.Dim(330, 170, 680, 360, .42f * Frost.smooth(.40f, .85f, lumTL)),
                    new Frost.Dim(1650, 112, 470, 230, .40f * Frost.smooth(.40f, .85f, lumTR))};
            L.bg = Frost.compose(w, SW, SH, s, dims);
            if (my != gen) return;
            Frost.Small matBlur = Frost.blurred(sm, MAT_BLUR * s);
            float lumMat = Frost.lum(matBlur, px(DOCK_X), px(DOCK_Y), px(DOCK_X + DOCK_W), px(DOCK_Y + DOCK_H));
            float base = (id.startsWith("bing:") || id.startsWith("gal:")) ? .20f : WALL_VEIL[wallIndex(id)];
            L.veil = Math.min(.62f, base + .30f * Frost.smooth(.42f, .85f, lumMat));
            L.mat = Frost.region(matBlur, px(DOCK_X), px(DOCK_Y), px(DOCK_W), px(DOCK_H), s, true);
            h.post(() -> { remember(L); if (id.equals(currentWall)) present(L, instant); });
            // The glass behind panels is only needed later; make it after the photo is up.
            Frost.Small sheetBlur = Frost.blurred(sm, SHEET_BLUR * s);
            L.sheetExtra = .22f * Frost.smooth(.45f, .85f, Frost.lum(sheetBlur, 0, 0, SW, SH));
            Bitmap sheet = Frost.region(sheetBlur, 0, 0, SW, SH, s, false);
            h.post(() -> L.sheet = sheet);
        });
    }

    /** Keeps the two most recent looks (the current one always stays). */
    void remember(Look L) {
        looks.remove(L.id);
        looks.put(L.id, L);
        List<String> ids = new ArrayList<>(looks.keySet());
        for (int i = 0; i < ids.size() && looks.size() > 2; i++) {
            if (current != null && ids.get(i).equals(current.id)) continue;
            if (ids.get(i).equals(L.id)) continue;
            looks.remove(ids.get(i));
        }
    }

    Bitmap decodeWall(String id) {
        try {
            if (id.startsWith("bing:") || id.startsWith("gal:")) {
                File f = id.startsWith("gal:") ? galFile(id.substring(4)) : dailyFile(id.substring(5));
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= SW && o.outHeight / (sample * 2) >= SH) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                return BitmapFactory.decodeFile(f.getPath(), o);
            }
            try (InputStream in = getAssets().open("wallpapers/" + id + ".jpg")) { return BitmapFactory.decodeStream(in); }
        } catch (Exception e) { return null; }
    }

    /** Crossfades the photo and the dock glass together. */
    void present(Look L, boolean instant) {
        current = L;
        int veilTo = Color.argb(Math.round(L.veil * 255), 12, 10, 8);
        if (instant || bgA.getDrawable() == null) matVeil.setBackgroundColor(veilTo);
        else {
            int from = matVeil.getBackground() instanceof android.graphics.drawable.ColorDrawable
                    ? ((android.graphics.drawable.ColorDrawable) matVeil.getBackground()).getColor() : veilTo;
            ValueAnimator va = ValueAnimator.ofArgb(from, veilTo);
            va.setDuration(1200);
            va.addUpdateListener(an -> matVeil.setBackgroundColor((Integer) an.getAnimatedValue()));
            va.start();
        }
        if (instant || bgA.getDrawable() == null) {
            bgA.setImageBitmap(L.bg);
            matA.setImageBitmap(L.mat);
            bgB.setAlpha(0f);
            matB.setAlpha(0f);
            return;
        }
        bgB.animate().cancel();
        matB.animate().cancel();
        bgB.setImageBitmap(L.bg);
        matB.setImageBitmap(L.mat);
        bgB.setAlpha(0f);
        matB.setAlpha(0f);
        bgB.setVisibility(View.VISIBLE);
        matB.setVisibility(View.VISIBLE);
        bgB.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        matB.animate().alpha(1f).setDuration(1200).setInterpolator(Motion.INOUT).start();
        bgB.animate().alpha(1f).setDuration(1200).setInterpolator(Motion.INOUT).withEndAction(() -> {
            bgA.setImageBitmap(L.bg);
            matA.setImageBitmap(L.mat);
            bgB.setAlpha(0f);
            matB.setAlpha(0f);
            bgB.setLayerType(View.LAYER_TYPE_NONE, null);
            bgB.setVisibility(View.INVISIBLE);
            matB.setVisibility(View.INVISIBLE);
        }).start();
    }

    ImageView photoView() {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_XY);
        return iv;
    }

    // ================================================================ dock

    void buildDockFrame() {
        dock = new FrameLayout(this);
        dock.setOutlineProvider(new RoundOutline(px(DOCK_R)));
        dock.setClipToOutline(true);

        matA = photoView();
        matB = photoView();
        matB.setAlpha(0f);
        matB.setVisibility(View.INVISIBLE);
        dock.addView(matA, new FrameLayout.LayoutParams(MATCH, MATCH));
        dock.addView(matB, new FrameLayout.LayoutParams(MATCH, MATCH));
        matVeil = new View(this);
        matVeil.setBackgroundColor(0x330C0A08);
        dock.addView(matVeil, new FrameLayout.LayoutParams(MATCH, MATCH));
        View edge = new View(this);
        edge.setBackground(new PlateDrawable(0, 0, 0, .24f, px(DOCK_R)));
        dock.addView(edge, new FrameLayout.LayoutParams(MATCH, MATCH));

        shelf = new HorizontalScrollView(this);
        shelf.setHorizontalScrollBarEnabled(false);
        shelf.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shelf.setHorizontalFadingEdgeEnabled(false);
        shelf.setFadingEdgeLength(px(132));
        shelf.setSmoothScrollingEnabled(true);
        shelf.setClipChildren(false);
        shelfBox = new FrameLayout(this);
        shelfBox.setClipChildren(false);
        View plate = new View(this);
        plate.setBackground(new PlateDrawable(.30f, .16f, .22f, .34f, px(TILE_R)));
        dockGlide = new Glide(shelfBox, plate);
        shelfRow = new LinearLayout(this);
        shelfRow.setOrientation(LinearLayout.HORIZONTAL);
        shelfRow.setClipChildren(false);
        shelfBox.addView(shelfRow, new FrameLayout.LayoutParams(WRAP, px(DOCK_H)));
        shelf.addView(shelfBox, new FrameLayout.LayoutParams(WRAP, px(DOCK_H), Gravity.CENTER_HORIZONTAL));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(px(DOCK_W - 32), px(DOCK_H));
        slp.leftMargin = px(16);
        dock.addView(shelf, slp);

        root.addView(dock, at(DOCK_X, DOCK_Y, DOCK_W, DOCK_H));
    }

    void buildDock() {
        shelfRow.removeAllViews();
        dockGlide.reset();
        List<String> d = dockList();
        if (d.isEmpty()) {
            TextView hint = text(34, INK, f500, -.6f);
            hint.setText("Open Apps to add up to " + MAX_DOCK + " apps");
            hint.setGravity(Gravity.CENTER);
            hint.setFocusable(true);
            hint.setOnClickListener(v -> showApps());
            shelfRow.addView(hint, new LinearLayout.LayoutParams(px(DOCK_W - 32), px(DOCK_H)));
            return;
        }
        for (int i = 0; i < d.size(); i++) {
            final int idx = i;
            final String pkg = d.get(i);
            View t = tile(pkg);
            t.setOnFocusChangeListener((v, f) -> {
                markFocus(v, f);
                dockGlide.focus(v, f);
                if (f) lastDockFocus = idx;
            });
            t.setOnClickListener(v -> launch(pkg, v));
            t.setOnLongClickListener(v -> { clickSound(); dockMenu(pkg); return true; });
            t.setOnKeyListener((v, k, e) -> {
                if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { clickSound(); dockMenu(pkg); return true; }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(TILE_W), px(TILE_H));
            lp.topMargin = px(16);
            lp.rightMargin = i == d.size() - 1 ? 0 : px(TILE_GAP);
            shelfRow.addView(t, lp);
        }
        pinEnds(shelfRow);
    }

    /** Left on the first item and Right on the last stay put instead of jumping rows. */
    void pinEnds(ViewGroup row) {
        int n = row.getChildCount();
        if (n == 0) return;
        for (int i = 0; i < n; i++) if (row.getChildAt(i).getId() == View.NO_ID) row.getChildAt(i).setId(View.generateViewId());
        View first = row.getChildAt(0), last = row.getChildAt(n - 1);
        first.setNextFocusLeftId(first.getId());
        last.setNextFocusRightId(last.getId());
    }

    void focusDock(int i) {
        h.post(() -> {
            if (shelfRow.getChildCount() == 0) return;
            shelfRow.getChildAt(Math.max(0, Math.min(i, shelfRow.getChildCount() - 1))).requestFocus();
        });
    }

    // ================================================================ tiles and marks

    /** A CleanFrame tile: white mark and label; the focus plate is shared and glides between tiles. */
    FrameLayout tile(String pkg) {
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setClipChildren(false);

        View mark = mark(pkg);
        mark.setAlpha(.84f);
        mark.setTag("mark");
        FrameLayout.LayoutParams mlp = (FrameLayout.LayoutParams) mark.getLayoutParams();
        mlp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        mlp.topMargin = px(64) - mlp.height / 2;
        t.addView(mark, mlp);

        TextView label = text(34, INK, f500, -.8f);
        label.setText(appLabel(pkg));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(.84f);
        label.setTag("label");
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(MATCH, px(42), Gravity.TOP);
        llp.topMargin = px(116);
        llp.leftMargin = px(4);
        llp.rightMargin = px(4);
        t.addView(label, llp);
        return t;
    }

    void markFocus(View t, boolean f) {
        View mark = t.findViewWithTag("mark"), label = t.findViewWithTag("label");
        if (f) mark.animate().alpha(1f).translationY(-px(4)).scaleX(1.08f).scaleY(1.08f).setDuration(420).setInterpolator(Motion.SPRING).start();
        else mark.animate().alpha(.84f).translationY(0).scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(Motion.OUT).start();
        label.animate().alpha(f ? 1f : .84f).setDuration(f ? 220 : 180).setInterpolator(Motion.OUT).start();
    }

    View pinDot() {
        View dot = new View(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(INK);
        dot.setBackground(dg);
        dot.setTag("pin");
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(px(12), px(12), Gravity.TOP | Gravity.END);
        dlp.setMargins(0, px(18), px(20), 0);
        dot.setLayoutParams(dlp);
        return dot;
    }

    final Map<String, Integer> brandColors = new HashMap<>();

    /** The colour an app's mark is drawn in: its brand colour (known, or read from its own art). */
    int brandColor(String pkg) {
        int known = knownBrand(pkg);
        if (known != 0) return known;
        Integer c = brandColors.get(pkg);
        if (c != null) return c;
        ResolveInfo ri = apps.get(pkg);
        int col = Color.WHITE;
        if (ri != null) {
            PackageManager pm = getPackageManager();
            Drawable banner = null, icon = null;
            try { banner = ri.activityInfo.loadBanner(pm); } catch (Exception ignored) { }
            try { icon = ri.loadIcon(pm); } catch (Exception ignored) { }
            col = AutoMark.brandColor(banner, icon);
        }
        brandColors.put(pkg, col);
        android.util.Log.i("CleanHomeBrand", pkg + " -> #" + Integer.toHexString(col));
        return col;
    }

    /** Brand colours for apps whose art is not a fair guide (multicolour, or too dark). 0 = read from the art. */
    static int knownBrand(String p) {
        if (p.equals("com.netflix.ninja")) return 0xFFE50914;
        if (p.equals("com.amazon.amazonvideo.livingroom")) return 0xFF1A98FF;
        if (p.equals("com.disney.disneyplus")) return 0xFF3D8BFF;
        if (p.startsWith("com.spotify")) return 0xFF1ED760;
        if (p.equals("com.tubitv")) return 0xFFFFF200;
        if (p.equals("com.apple.android.music")) return 0xFFFA2D48;
        if (p.equals("ch.protonvpn.android")) return 0xFF8F75FF;
        if (p.equals("com.apple.atve.androidtv.appletv") || p.equals("com.android.tv.settings") || p.equals("com.tcl.suspension")
                || p.equals("com.tcl.tv") || p.equals("com.esaba.downloader")) return Color.WHITE;
        return 0;
    }

    static void tint(ImageView iv, int color) {
        if (color != Color.WHITE) iv.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }

    /** App mark in its brand colour: drawn glyphs, then the app's themed icon, then one made from its own art, then a monogram. */
    View mark(String pkg) {
        int[] g = forceAuto ? null : glyphFor(pkg);
        if (g != null) {
            ImageView iv = new ImageView(this);
            // Multicolour logos have their own coloured drawings; the rest are tinted with one brand colour.
            if (g[0] == R.drawable.g_youtube) iv.setImageResource(R.drawable.g_youtube_color);
            else if (g[0] == R.drawable.g_maxliv) iv.setImageResource(R.drawable.g_maxliv_color);
            else if (g[0] == R.drawable.g_voltra) iv.setImageResource(R.drawable.g_voltra_color);
            else { iv.setImageResource(g[0]); tint(iv, brandColor(pkg)); }
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setLayoutParams(new FrameLayout.LayoutParams(px(g[1] * MARK_SCALE), px(g[2] * MARK_SCALE)));
            return iv;
        }
        ResolveInfo ri = apps.get(pkg);
        if (ri != null && Build.VERSION.SDK_INT >= 33) {
            try {
                Drawable d = ri.loadIcon(getPackageManager());
                if (d instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) d).getMonochrome() != null) {
                    ImageView iv = new ImageView(this);
                    Drawable mono = ((AdaptiveIconDrawable) d).getMonochrome().mutate();
                    mono.setTint(brandColor(pkg));
                    iv.setImageDrawable(mono);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setLayoutParams(new FrameLayout.LayoutParams(px(96), px(96)));
                    return iv;
                }
            } catch (Exception ignored) { }
        }
        Bitmap auto = autoMarkFor(pkg);
        if (auto != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(auto);
            tint(iv, brandColor(pkg));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            float k = Math.min(124f / auto.getWidth(), 64f / auto.getHeight());
            iv.setLayoutParams(new FrameLayout.LayoutParams(px(auto.getWidth() * k * MARK_SCALE), px(auto.getHeight() * k * MARK_SCALE)));
            return iv;
        }
        FrameLayout m = new FrameLayout(this);
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(px(27));
        edge.setStroke(px(2), 0x61FFFFFF);
        m.setBackground(edge);
        TextView l = text(40, brandColor(pkg), f600, 0);
        String name = appLabel(pkg);
        l.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT));
        l.setGravity(Gravity.CENTER);
        m.addView(l, new FrameLayout.LayoutParams(MATCH, MATCH));
        m.setLayoutParams(new FrameLayout.LayoutParams(px(66), px(66)));
        return m;
    }

    final Map<String, Bitmap> autoMarks = new HashMap<>();
    final Set<String> noAutoMark = new HashSet<>();

    Bitmap autoMarkFor(String pkg) {
        ResolveInfo ri = apps.get(pkg);
        if (ri == null) return null;
        String key = pkg;
        try { key += "@" + getPackageManager().getPackageInfo(pkg, 0).lastUpdateTime; } catch (Exception ignored) { }
        if (autoMarks.containsKey(key)) return autoMarks.get(key);
        if (noAutoMark.contains(key)) return null;
        PackageManager pm = getPackageManager();
        Bitmap b = null;
        try { b = AutoMark.fromBanner(ri.activityInfo.loadBanner(pm)); } catch (Exception ignored) { }
        if (b == null) try { b = AutoMark.fromIcon(ri.loadIcon(pm)); } catch (Exception ignored) { }
        if (b == null) noAutoMark.add(key); else autoMarks.put(key, b);
        return b;
    }

    /** {drawable, width, height} in stage px, sized to CleanFrame's measured ink. */
    static int[] glyphFor(String p) {
        if (p.equals("com.netflix.ninja")) return new int[]{R.drawable.g_netflix, 72, 72};
        if (p.startsWith("com.google.android.youtube.tv") || p.startsWith("com.liskovsoft.smarttube")) return new int[]{R.drawable.g_youtube, 64, 64};
        if (p.equals("com.amazon.amazonvideo.livingroom")) return new int[]{R.drawable.g_primevideo, 120, 32};
        if (p.equals("com.tubitv")) return new int[]{R.drawable.g_tubi, 118, 118};
        if (p.startsWith("com.stremio")) return new int[]{R.drawable.g_stremio, 62, 62};
        if (p.equals("com.disney.disneyplus")) return new int[]{R.drawable.g_disneyplus, 74, 74};
        if (p.equals("com.apple.atve.androidtv.appletv")) return new int[]{R.drawable.g_appletv, 96, 96};
        if (p.startsWith("com.spotify")) return new int[]{R.drawable.g_spotify, 62, 62};
        if (p.equals("com.wbd.stream") || p.startsWith("com.hbo")) return new int[]{R.drawable.g_hbomax, 120, 120};
        if (p.equals("com.apple.android.music")) return new int[]{R.drawable.g_applemusic, 64, 64};
        if (p.equals("com.maxliv.live")) return new int[]{R.drawable.g_maxliv, 80, 80};
        if (p.equals("com.rmedia.rplayer")) return new int[]{R.drawable.g_reezn, 124, 33};
        if (p.equals("com.amhkor.voltratv")) return new int[]{R.drawable.g_voltra, 80, 69};
        if (p.equals("com.linkdrop.tvapp")) return new int[]{R.drawable.g_linkdrop, 66, 66};
        if (p.equals("com.leed.sportsfire")) return new int[]{R.drawable.g_sportsfire, 124, 43};
        if (p.equals("com.sportzx.live")) return new int[]{R.drawable.g_sportzx, 66, 66};
        if (p.equals("link.unlinked")) return new int[]{R.drawable.g_unlinked, 72, 65};
        if (p.equals("com.tcl.tv")) return new int[]{R.drawable.g_tv, 68, 68};
        if (p.equals("com.android.vending")) return new int[]{R.drawable.g_play, 64, 64};
        if (p.equals("ch.protonvpn.android")) return new int[]{R.drawable.g_protonvpn, 66, 66};
        if (p.equals("com.esaba.downloader")) return new int[]{R.drawable.g_download, 66, 66};
        if (p.equals("com.android.tv.settings")) return new int[]{R.drawable.g_settings, 66, 66};
        if (p.equals("com.tcl.suspension")) return new int[]{R.drawable.g_sliders, 66, 66};
        return null;
    }

    static class RoundOutline extends ViewOutlineProvider {
        final float r;
        RoundOutline(float r) { this.r = r; }
        @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
    }

    /** CleanFrame's --plate: a 170deg white gradient with a 1px top edge that follows the radius. */
    static class PlateDrawable extends Drawable {
        final float a0, a1, a2, edge, r;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF b = new RectF(), in = new RectF();
        @Override protected void onBoundsChange(android.graphics.Rect r) {
            b.set(r);
            in.set(b);
            in.inset(.75f, .75f);
            if (a0 + a1 + a2 > 0) fill.setShader(new LinearGradient(b.left, b.top, b.left + b.height() * .17f, b.bottom,
                    new int[]{Color.argb((int) (a0 * 255), 255, 255, 255), Color.argb((int) (a1 * 255), 255, 255, 255), Color.argb((int) (a2 * 255), 255, 255, 255)},
                    new float[]{0, .58f, 1}, Shader.TileMode.CLAMP));
            if (edge > 0) {
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(1.5f);
                line.setShader(new LinearGradient(0, b.top, 0, b.top + r(), Color.argb((int) (edge * 255), 255, 255, 255), 0x00FFFFFF, Shader.TileMode.CLAMP));
            }
        }
        float r() { return Math.min(r, b.height() / 2f); }
        PlateDrawable(float a0, float a1, float a2, float edge, float r) { this.a0 = a0; this.a1 = a1; this.a2 = a2; this.edge = edge; this.r = r; }
        @Override public void draw(Canvas c) {
            float rr = r();
            if (a0 + a1 + a2 > 0) c.drawRoundRect(b, rr, rr, fill);
            if (edge > 0) c.drawRoundRect(in, rr, rr, line);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ================================================================ utilities (top right)

    interface NameFn { String get(); }

    NameFn focusedUtilName;

    View utilButton(int icon, NameFn name, View.OnClickListener click) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setAlpha(.66f);
        b.addView(iv, new FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER));
        b.setOnFocusChangeListener((v, f) -> {
            iv.animate().alpha(f ? 1f : (iv.getTag() == Boolean.TRUE ? .92f : .66f)).scaleX(f ? 1.14f : 1f).scaleY(f ? 1.14f : 1f)
                    .setDuration(f ? 400 : 240).setInterpolator(f ? Motion.SPRING : Motion.OUT).start();
            if (f) {
                boolean wasShown = utilLabel.getAlpha() > .5f;
                utilLabel.setText(name.get());
                focusedUtilName = name;
                utilLabel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                int[] loc = new int[2];
                v.getLocationInWindow(loc);
                float cx = loc[0] + v.getWidth() / 2f;
                float left = Math.min(cx - utilLabel.getMeasuredWidth() / 2f, px(1920 - 40) - utilLabel.getMeasuredWidth());
                utilLabel.animate().cancel();
                if (wasShown) {
                    utilLabel.animate().translationX(left).alpha(1f).setDuration(T).setInterpolator(EASE).start();
                } else {
                    utilLabel.setTranslationX(left);
                    utilLabel.setTranslationY(-px(6));
                    utilLabel.animate().alpha(1f).translationY(0).setDuration(T).setInterpolator(EASE).start();
                }
            } else {
                v.post(() -> {
                    if (utils.findFocus() == null) utilLabel.animate().alpha(0f).setDuration(140).start();
                });
            }
        });
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(64), px(64));
        lp.leftMargin = px(16);
        b.setLayoutParams(lp);
        return b;
    }

    // ================================================================ sheets

    static final int SHEET_FULL = 0, SHEET_PANEL = 1, SHEET_MENU = 2;

    void closeOverlay() {
        overlay.animate().cancel();
        for (View v : new View[]{dock, clock, date, wx, utils}) { if (v == null) continue; v.animate().cancel(); v.setAlpha(1f); v.setScaleX(1f); v.setScaleY(1f); }
        overlay.removeAllViews();
        overlay.setVisibility(View.GONE);
        overlay.setAlpha(1f);
        blockHome(false);
    }

    /** Fades the open sheet away, then returns to the dock. */
    void dismissOverlay(boolean revertPreview) {
        if (overlay.getVisibility() != View.VISIBLE) return;
        blockHome(false);
        View content = overlay.getChildCount() > 0 ? overlay.getChildAt(0) : null;
        int kind = overlay.getTag() instanceof Integer ? (Integer) overlay.getTag() : SHEET_FULL;
        View card = content == null ? null : content.findViewWithTag("card");
        if (card != null) card.animate().translationX(px(72)).setDuration(220).setInterpolator(Motion.IN).start();
        else if (content != null) {
            if (kind == SHEET_PANEL) content.animate().translationX(px(64)).setDuration(220).setInterpolator(Motion.IN).start();
            else if (kind == SHEET_MENU) content.animate().scaleX(.94f).scaleY(.94f).setDuration(200).setInterpolator(Motion.IN).start();
            else content.animate().scaleX(.97f).scaleY(.97f).setDuration(220).setInterpolator(Motion.IN).start();
        }
        overlay.animate().alpha(0f).setDuration(220).setInterpolator(Motion.IN).withEndAction(this::closeOverlay).start();
        // Home comes back forward as the sheet leaves.
        for (View v : new View[]{dock, clock, date, wx, utils}) v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(420).setInterpolator(Motion.OUT).start();
        if (revertPreview) applyWallpaper(false);
        focusDock(lastDockFocus);
    }

    void showOverlay(View content, FrameLayout.LayoutParams lp, int kind, View focus) {
        closeOverlay();
        overlay.setTag(kind);
        overlay.addView(content, lp);
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(280).setInterpolator(Motion.OUT).start();
        // Home steps back a little behind full sheets and menus, giving them depth.
        if (kind != SHEET_PANEL) {
            float a = kind == SHEET_FULL ? 0f : .55f, sc = kind == SHEET_FULL ? .96f : .985f;
            for (View v : new View[]{dock, clock, date, wx, utils}) v.animate().alpha(a).scaleX(sc).scaleY(sc).setStartDelay(0).setDuration(360).setInterpolator(Motion.OUT).start();
        }
        View card = content.findViewWithTag("card");
        if (card != null) {
            card.setTranslationX(px(96));
            card.animate().translationX(0).setDuration(480).setInterpolator(Motion.OUT).start();
        } else if (kind == SHEET_PANEL) {
            content.setTranslationX(px(88));
            content.animate().translationX(0).setDuration(460).setInterpolator(Motion.OUT).start();
        } else if (kind == SHEET_MENU) {
            content.setScaleX(.9f);
            content.setScaleY(.9f);
            content.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(Motion.SPRING).start();
        } else {
            content.setScaleX(.95f);
            content.setScaleY(.95f);
            content.animate().scaleX(1f).scaleY(1f).setDuration(480).setInterpolator(Motion.OUT).start();
        }
        blockHome(true);
        if (focus != null) h.post(focus::requestFocus);
    }

    /** While a sheet is open, nothing on Home underneath can take focus. */
    void blockHome(boolean block) {
        int mode = block ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        utils.setDescendantFocusability(mode);
        dock.setDescendantFocusability(mode);
    }

    /** CleanFrame's sheet: the wallpaper, heavily frosted (cleanly, no grain), under a dark veil. */
    FrameLayout sheet(float veil, int widthPx, boolean right) {
        FrameLayout sh = new FrameLayout(this);
        ImageView bg = photoView();
        if (current != null && current.sheet != null) {
            bg.setImageBitmap(current.sheet);
        } else if (current != null) {
            bg.setImageBitmap(current.bg);
            if (Build.VERSION.SDK_INT >= 31) bg.setRenderEffect(RenderEffect.createBlurEffect(px(SHEET_BLUR), px(SHEET_BLUR), Shader.TileMode.CLAMP));
        }
        if (right) bg.setTranslationX(-(SW - widthPx));
        sh.addView(bg, new FrameLayout.LayoutParams(SW, SH));
        View v = new View(this);
        float extra = current == null ? 0 : current.sheetExtra;
        v.setBackgroundColor(Color.argb(Math.round(Math.min(.75f, veil + extra) * 255), 10, 9, 8));
        sh.addView(v, new FrameLayout.LayoutParams(MATCH, MATCH));
        return sh;
    }

    /** A list whose solid highlight pill glides between rows. */
    FrameLayout pillBox(LinearLayout list, Glide[] out) {
        FrameLayout box = new FrameLayout(this);
        View pill = new View(this);
        pill.setBackground(round(SOLID, px(32)));
        out[0] = new Glide(box, pill);
        box.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));
        return box;
    }

    TextView sheetRow(String name, Glide[] glide, Runnable action) {
        TextView t = text(30, MUTE, f500, -.3f);
        t.setText(name);
        t.setFocusable(true);
        t.setClickable(true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(px(28), 0, px(28), 0);
        t.setOnFocusChangeListener((v, f) -> {
            if (glide[0] != null) glide[0].focus(v, f);
            Object old = t.getTag();
            if (old instanceof ValueAnimator) ((ValueAnimator) old).cancel();
            ValueAnimator a = ValueAnimator.ofArgb(t.getCurrentTextColor(), f ? SOLID_INK : MUTE);
            a.setDuration(f ? 200 : 140);
            a.addUpdateListener(an -> t.setTextColor((Integer) an.getAnimatedValue()));
            a.start();
            t.setTag(a);
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    /** Hold OK or Menu on a dock app: move it or remove it. */
    void dockMenu(String pkg) {
        List<String> d = dockList();
        int i = d.indexOf(pkg);
        FrameLayout sh = sheet(.42f, SW, false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        View m = mark(pkg);
        col.addView(m);
        TextView title = text(48, INK, f500, -1.5f);
        title.setText(appLabel(pkg));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, px(64));
        tl.topMargin = px(20);
        tl.bottomMargin = px(32);
        col.addView(title, tl);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        Glide[] g = new Glide[1];
        FrameLayout box = pillBox(list, g);
        List<TextView> rows = new ArrayList<>();
        if (i > 0) rows.add(sheetRow("Move left", g, () -> { Collections.swap(d, i, i - 1); saveDock(d); lastDockFocus = i - 1; afterDockEdit(); }));
        if (i >= 0 && i < d.size() - 1) rows.add(sheetRow("Move right", g, () -> { Collections.swap(d, i, i + 1); saveDock(d); lastDockFocus = i + 1; afterDockEdit(); }));
        rows.add(sheetRow("Remove from Home", g, () -> { d.remove(pkg); saveDock(d); lastDockFocus = Math.max(0, i - 1); afterDockEdit(); }));
        rows.add(sheetRow("Cancel", g, () -> dismissOverlay(false)));
        for (TextView r : rows) {
            r.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(480), px(64));
            lp.topMargin = px(6);
            list.addView(r, lp);
        }
        col.addView(box, new LinearLayout.LayoutParams(px(480), WRAP));
        sh.addView(col, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        showOverlay(sh, new FrameLayout.LayoutParams(MATCH, MATCH), SHEET_MENU, rows.get(0));
    }

    void afterDockEdit() {
        buildDock();
        dismissOverlay(false);
    }

    TextView appsHint;
    final Map<String, FrameLayout> appTiles = new HashMap<>();

    /** Apps: every installed app. OK opens; hold OK or Menu adds it to Home or removes it. */
    void showApps() {
        FrameLayout sh = sheet(.38f, SW, false);
        TextView title = text(48, INK, f500, -1.5f);
        title.setText("Apps");
        sh.addView(title, at(140, 72, WRAP, 64));
        appsHint = text(30, MUTE, f500, -.2f);
        sh.addView(appsHint, at(140, 140, WRAP, 40));
        updateAppsHint();

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(false);
        sv.setFadingEdgeLength(px(80));
        sv.setSmoothScrollingEnabled(true);
        sv.setClipToPadding(false);
        sv.setPadding(0, px(12), 0, px(60));
        FrameLayout gridBox = new FrameLayout(this);
        View plate = new View(this);
        plate.setBackground(new PlateDrawable(.30f, .16f, .22f, .34f, px(TILE_R)));
        Glide glide = new Glide(gridBox, plate);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        gridBox.addView(grid, new FrameLayout.LayoutParams(WRAP, WRAP));
        sv.addView(gridBox);
        sh.addView(sv, at(140 - 16, 212, 1920 - 2 * 124, 1080 - 212));

        List<String> d = dockList();
        appTiles.clear();
        View first = null;
        LinearLayout row = null;
        int i = 0;
        for (String pkg : apps.keySet()) {
            if (i++ % GRID_COLS == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row, new LinearLayout.LayoutParams(WRAP, px(TILE_H + 16)));
            }
            FrameLayout t = tile(pkg);
            if (d.contains(pkg)) t.addView(pinDot());
            t.setOnFocusChangeListener((v, f) -> { markFocus(v, f); glide.focus(v, f); });
            t.setOnClickListener(v -> { launch(pkg, v); closeOverlay(); });
            t.setOnLongClickListener(v -> { clickSound(); togglePin(pkg); return true; });
            t.setOnKeyListener((v, k, e) -> {
                if (k == KeyEvent.KEYCODE_MENU && e.getAction() == KeyEvent.ACTION_UP) { clickSound(); togglePin(pkg); return true; }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(TILE_W), px(TILE_H));
            // Justified: five columns spread edge to edge, the same spacing on every row.
            lp.rightMargin = (i - 1) % GRID_COLS == GRID_COLS - 1 ? 0 : px(GRID_GAP);
            row.addView(t, lp);
            appTiles.put(pkg, t);
            if (first == null) first = t;
            int idx = i - 1;
            if (idx < 25) Motion.rise(t, px(28), 120 + (idx / 5) * 55L + (idx % 5) * 22L, 520);
        }
        showOverlay(sh, new FrameLayout.LayoutParams(MATCH, MATCH), SHEET_FULL, first);
    }

    void updateAppsHint() {
        if (appsHint != null) appsHint.setText("Hold OK to add to Home or remove  ·  " + dockList().size() + " of " + MAX_DOCK + " on Home");
    }

    /** Adds or removes an app from Home in place: the dot fades, nothing else moves. */
    void togglePin(String pkg) {
        List<String> d = dockList();
        FrameLayout t = appTiles.get(pkg);
        if (d.contains(pkg)) {
            d.remove(pkg);
            showToast(appLabel(pkg) + " removed from Home");
            if (t != null) {
                View dot = t.findViewWithTag("pin");
                if (dot != null) dot.animate().alpha(0f).scaleX(.4f).scaleY(.4f).setDuration(180).withEndAction(() -> t.removeView(dot)).start();
            }
        } else if (d.size() >= MAX_DOCK) {
            showToast("Home holds " + MAX_DOCK + " apps. Remove one first.");
            return;
        } else {
            d.add(pkg);
            showToast(appLabel(pkg) + " added to Home");
            if (t != null && t.findViewWithTag("pin") == null) {
                View dot = pinDot();
                dot.setAlpha(0f);
                dot.setScaleX(.4f);
                dot.setScaleY(.4f);
                t.addView(dot);
                dot.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(EASE).start();
            }
        }
        saveDock(d);
        buildDock();
        dock.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        updateAppsHint();
    }

    /** Wallpaper: daily online photos, automatic time of day, or one photo. Focus previews it. */
    void showWallpapers() {
        int w = px(720);
        FrameLayout sh = sheet(.46f, w, true);
        sh.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        sh.setClipToOutline(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(64), px(72), px(64), px(48));
        TextView title = text(48, INK, f500, -1.5f);
        title.setText("Wallpaper");
        col.addView(title, new LinearLayout.LayoutParams(WRAP, px(64)));
        int slot = slotFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        TextView sub = text(26, FAINT, f500, -.2f);
        final String defSub = "Google ambient photos change every 15 minutes. Daily photo comes from Bing. Automatic follows the time of day (now: " + SLOT_NAMES[slot] + ").";
        sub.setText(defSub);
        sub.setPadding(0, px(8), 0, px(24));
        col.addView(sub);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(false);
        sv.setFadingEdgeLength(px(60));
        sv.setSmoothScrollingEnabled(true);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        Glide[] g = new Glide[1];
        sv.addView(pillBox(list, g));
        col.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        String pref = prefs.getString("wall", "auto");
        List<TextView> rows = new ArrayList<>();
        List<String[]> gal = galleryList();
        String galNow = gal.isEmpty() ? null : gal.get((int) ((System.currentTimeMillis() / ROTATE_MS) % gal.size()))[0];
        String galBy = "";
        for (String[] x : gal) if (x[0].equals(galNow)) galBy = x[1];
        final String galCredit = gal.isEmpty() ? "Google's ambient photos are downloading…" : "Google ambient · changes every 15 minutes · photo by " + galBy;
        TextView googleRow = sheetRow(("google".equals(pref) ? "✓  " : "     ") + "Google ambient (changing)", g, () -> pick("google"));
        googleRow.setOnFocusChangeListener(chain(googleRow.getOnFocusChangeListener(), f -> {
            if (f) { if (galNow != null) showWall("gal:" + galNow, false); sub.setText(galCredit); }
        }));
        rows.add(googleRow);
        TextView auto = sheetRow(("auto".equals(pref) ? "✓  " : "     ") + "Automatic (time of day)", g, () -> pick("auto"));
        auto.setOnFocusChangeListener(chain(auto.getOnFocusChangeListener(), f -> { if (f) { showWall(autoWall(), false); sub.setText(defSub); } }));
        rows.add(auto);
        List<String[]> daily = dailyList();
        if (daily.isEmpty()) {
            rows.add(sheetRow("     Daily photo (online, loading…)", g, () -> { fetchDaily(true); showToast("Fetching today's photo…"); }));
        }
        for (int i = 0; i < daily.size(); i++) {
            final String[] dd = daily.get(i);
            final String key = i == 0 ? "daily" : "bing:" + dd[0];
            TextView r = sheetRow((key.equals(pref) ? "✓  " : "     ") + (i == 0 ? "Daily photo · " + dd[1] : dd[1]), g, () -> pick(key));
            r.setOnFocusChangeListener(chain(r.getOnFocusChangeListener(), f -> { if (f) { showWall("bing:" + dd[0], false); sub.setText(dd[2]); } }));
            rows.add(r);
        }
        final int bundledStart = rows.size();
        for (int i = 0; i < WALL_IDS.length; i++) {
            final String id = WALL_IDS[i];
            TextView r = sheetRow((id.equals(pref) ? "✓  " : "     ") + WALL_NAMES[i], g, () -> pick(id));
            r.setOnFocusChangeListener(chain(r.getOnFocusChangeListener(), f -> { if (f) { showWall(id, false); sub.setText(defSub); } }));
            rows.add(r);
        }
        for (TextView r : rows) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, px(64));
            lp.bottomMargin = px(4);
            list.addView(r, lp);
        }
        sh.addView(col, new FrameLayout.LayoutParams(MATCH, MATCH));
        int sel = 1;
        if ("google".equals(pref)) sel = 0;
        else if ("daily".equals(pref) && !daily.isEmpty()) sel = 2;
        else if (pref.startsWith("bing:")) { for (int i = 1; i < daily.size(); i++) if (pref.equals("bing:" + daily.get(i)[0])) sel = 2 + i; }
        else if (!"auto".equals(pref) && !"daily".equals(pref)) sel = bundledStart + wallIndex(pref);
        showOverlay(sh, new FrameLayout.LayoutParams(w, MATCH, Gravity.END), SHEET_PANEL, rows.get(Math.min(sel, rows.size() - 1)));
    }

    // ================================================================ search

    /** Search: type (OK opens the keyboard) or press the mic. Matching apps appear as you type. */
    void showSearch() {
        FrameLayout sh = sheet(.40f, SW, false);
        TextView title = text(48, INK, f500, -1.5f);
        title.setText("Search");
        sh.addView(title, at(140, 72, WRAP, 64));

        android.widget.EditText field = new android.widget.EditText(this);
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(34));
        field.setTypeface(f500);
        field.setTextColor(INK);
        field.setHintTextColor(FAINT);
        field.setHint("Search apps, films and shows");
        field.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        field.setPadding(px(84), 0, px(28), 0);
        field.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable fbg = round(0x1FFFFFFF, px(40));
        fbg.setStroke(Math.max(1, px(1.5f)), 0x33FFFFFF);
        field.setBackground(fbg);
        FrameLayout fieldBox = new FrameLayout(this);
        fieldBox.addView(field, new FrameLayout.LayoutParams(MATCH, MATCH));
        ImageView mag = new ImageView(this);
        mag.setImageResource(R.drawable.g_search);
        mag.setAlpha(.7f);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(px(34), px(34), Gravity.CENTER_VERTICAL | Gravity.START);
        mlp.leftMargin = px(32);
        fieldBox.addView(mag, mlp);
        sh.addView(fieldBox, at(140, 176, 1460, 80));
        field.setOnFocusChangeListener((v, f) -> {
            fbg.setColor(f ? 0x38FFFFFF : 0x1FFFFFFF);
            fbg.setStroke(Math.max(1, px(1.5f)), f ? 0x99FFFFFF : 0x33FFFFFF);
            mag.animate().alpha(f ? 1f : .7f).setDuration(T).start();
        });

        // Mic: a round glass button that turns solid on focus.
        FrameLayout mic = new FrameLayout(this);
        mic.setFocusable(true);
        mic.setClickable(true);
        GradientDrawable mbg = new GradientDrawable();
        mbg.setShape(GradientDrawable.OVAL);
        mbg.setColor(0x1FFFFFFF);
        mbg.setStroke(Math.max(1, px(1.5f)), 0x33FFFFFF);
        mic.setBackground(mbg);
        ImageView micIcon = new ImageView(this);
        micIcon.setImageResource(R.drawable.g_mic);
        mic.addView(micIcon, new FrameLayout.LayoutParams(px(36), px(36), Gravity.CENTER));
        mic.setOnFocusChangeListener((v, f) -> {
            mbg.setColor(f ? SOLID : 0x1FFFFFFF);
            micIcon.setColorFilter(f ? SOLID_INK : Color.WHITE);
            v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(T).setInterpolator(EASE).start();
        });
        mic.setOnClickListener(v -> { dismissOverlay(false); voice(); });
        sh.addView(mic, at(140 + 1460 + 24, 176, 80, 80));
        TextView micHint = text(24, FAINT, f500, -.2f);
        micHint.setText("Speak");
        micHint.setGravity(Gravity.CENTER);
        sh.addView(micHint, at(140 + 1460 + 24 - 20, 266, 120, 30));

        TextView hint = text(30, MUTE, f500, -.2f);
        hint.setText("Press OK to type with the keyboard, or choose the mic to speak.");
        sh.addView(hint, at(140, 300, WRAP, 40));

        // Matching apps
        TextView appsTitle = text(30, FAINT, f500, -.2f);
        appsTitle.setText("Apps");
        appsTitle.setAlpha(0f);
        sh.addView(appsTitle, at(140, 300, WRAP, 40));
        FrameLayout appBox = new FrameLayout(this);
        View plate = new View(this);
        plate.setBackground(new PlateDrawable(.30f, .16f, .22f, .34f, px(TILE_R)));
        Glide ag = new Glide(appBox, plate);
        LinearLayout appRow = new LinearLayout(this);
        appRow.setOrientation(LinearLayout.HORIZONTAL);
        appBox.addView(appRow, new FrameLayout.LayoutParams(WRAP, px(TILE_H)));
        sh.addView(appBox, at(140 - 16, 350, 1680, TILE_H));

        // Search actions
        LinearLayout acts = new LinearLayout(this);
        acts.setOrientation(LinearLayout.VERTICAL);
        Glide[] g = new Glide[1];
        FrameLayout actBox = pillBox(acts, g);
        actBox.setAlpha(0f);
        sh.addView(actBox, at(140 - 28, 300, 1000, WRAP));

        Runnable refresh = () -> {
            String q = field.getText().toString().trim();
            appRow.removeAllViews();
            acts.removeAllViews();
            ag.reset();
            if (q.isEmpty()) {
                hint.animate().alpha(1f).setDuration(T).start();
                appsTitle.animate().alpha(0f).setDuration(T).start();
                actBox.animate().alpha(0f).setDuration(T).start();
                return;
            }
            hint.animate().alpha(0f).setDuration(140).start();
            int n = 0;
            for (String pkg : apps.keySet()) {
                if (!appLabel(pkg).toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))) continue;
                FrameLayout t = tile(pkg);
                t.setOnFocusChangeListener((v, f) -> { markFocus(v, f); ag.focus(v, f); });
                t.setOnClickListener(v -> { launch(pkg, v); closeOverlay(); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(TILE_W), px(TILE_H));
                lp.rightMargin = px(TILE_GAP);
                appRow.addView(t, lp);
                if (++n == 6) break;
            }
            appsTitle.animate().alpha(n > 0 ? 1f : 0f).setDuration(T).start();
            FrameLayout.LayoutParams alp = (FrameLayout.LayoutParams) actBox.getLayoutParams();
            alp.topMargin = px(n > 0 ? 560 : 300);
            actBox.setLayoutParams(alp);
            TextView everywhere = sheetRow("Search Google TV for “" + q + "”", g, () -> searchEverywhere(q));
            acts.addView(everywhere, new LinearLayout.LayoutParams(MATCH, px(64)));
            if (apps.containsKey("com.google.android.youtube.tv")) {
                TextView yt = sheetRow("Search YouTube for “" + q + "”", g, () -> searchYouTube(q));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, px(64));
                lp.topMargin = px(6);
                acts.addView(yt, lp);
            }
            actBox.animate().alpha(1f).setDuration(T).start();
        };
        field.addTextChangedListener(new SearchWatcher(refresh));
        field.setOnEditorActionListener((v, action, e) -> {
            String q = field.getText().toString().trim();
            if (!q.isEmpty()) searchEverywhere(q);
            return true;
        });
        // Down from the box goes to results (apps first, then actions); Right goes to the mic.
        field.setId(View.generateViewId());
        mic.setId(View.generateViewId());
        field.setNextFocusRightId(mic.getId());
        mic.setNextFocusLeftId(field.getId());
        showOverlay(sh, new FrameLayout.LayoutParams(MATCH, MATCH), SHEET_FULL, field);
    }

    static class SearchWatcher implements android.text.TextWatcher {
        final Runnable r;
        SearchWatcher(Runnable r) { this.r = r; }
        @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
        @Override public void onTextChanged(CharSequence c, int a, int b, int d) { r.run(); }
        @Override public void afterTextChanged(android.text.Editable e) { }
    }

    void searchEverywhere(String q) {
        closeOverlay();
        Intent i = new Intent("android.search.action.GLOBAL_SEARCH").putExtra(android.app.SearchManager.QUERY, q)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tryStart(i, "Search isn't available");
    }

    void searchYouTube(String q) {
        closeOverlay();
        Intent i = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv")
                .putExtra(android.app.SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!tryStart(i, null)) launch("com.google.android.youtube.tv");
    }

    // ================================================================ quick settings

    /**
     * Quick settings: Google TV's panel, same arrangement and same destinations, drawn in CleanHome's glass.
     * Header (date, time, All settings, profile), eight tiles, notifications, Google TV footer.
     */
    void showQuickSettings() {
        closeOverlay();
        if (!HomeKeyService.openQuickSettings()) openSettings(Settings.ACTION_SETTINGS);
    }

    ImageView accountIcon;

    /** Shows the Google profile picture (captured from Google's own panel by HomeKeyService) on the account button. */
    void refreshAvatar() {
        if (accountIcon == null) return;
        java.io.File f = new java.io.File(getFilesDir(), "avatar.png");
        Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
        if (b == null) return;
        accountIcon.setImageBitmap(b);
        accountIcon.setTag(Boolean.TRUE);
        if (!accountIcon.hasFocus() && !((View) accountIcon.getParent()).hasFocus()) accountIcon.setAlpha(.92f);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) accountIcon.getLayoutParams();
        lp.width = px(40); lp.height = px(40);
        accountIcon.setLayoutParams(lp);
    }

    /** Google TV's profile chooser, or the accounts page in Settings. */
    void openAccount() {
        closeOverlay();
        if (!tryStart(new Intent().setClassName("com.google.android.apps.tv.launcherx",
                "com.google.android.apps.tv.launcherx.profile.chooser.ProfileChooserActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null))
            openComponent("com.android.tv.settings", "com.google.android.tv.settings.AccountActivity");
    }

    /** The earlier glass copy of the panel (no longer used: the settings icon opens Google's own). */
    void showGlassQuickSettings() {
        final int CX = 1328, CY = 32, CW = 544, CH = 1016;
        FrameLayout layer = new FrameLayout(this);
        View dim = new View(this);
        dim.setBackgroundColor(0x66000000);
        layer.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));

        FrameLayout card = new FrameLayout(this);
        card.setTag("card");
        card.setOutlineProvider(new RoundOutline(px(40)));
        card.setClipToOutline(true);
        ImageView glass = photoView();
        if (current != null && current.sheet != null) glass.setImageBitmap(current.sheet);
        else if (current != null) {
            glass.setImageBitmap(current.bg);
            if (Build.VERSION.SDK_INT >= 31) glass.setRenderEffect(RenderEffect.createBlurEffect(px(SHEET_BLUR), px(SHEET_BLUR), Shader.TileMode.CLAMP));
        }
        glass.setTranslationX(-px(CX));
        glass.setTranslationY(-px(CY));
        card.addView(glass, new FrameLayout.LayoutParams(SW, SH));
        View veil = new View(this);
        float extra = current == null ? 0 : current.sheetExtra;
        veil.setBackgroundColor(Color.argb(Math.round(Math.min(.75f, .40f + extra) * 255), 10, 9, 8));
        card.addView(veil, new FrameLayout.LayoutParams(MATCH, MATCH));
        View edge = new View(this);
        edge.setBackground(new PlateDrawable(0, 0, 0, .26f, px(40)));
        card.addView(edge, new FrameLayout.LayoutParams(MATCH, MATCH));
        layer.addView(card, at(CX, CY, CW, CH));

        // Header
        Date now = new Date();
        TextView date = text(26, FAINT, f500, -.2f);
        date.setText(new SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(now));
        card.addView(date, at(30, 48, WRAP, 37));
        TextView time = text(48, INK, f500, -1.2f);
        time.setText(android.text.format.DateFormat.getTimeFormat(this).format(now));
        card.addView(time, at(30, 89, WRAP, 60));
        View gear = circleButton(R.drawable.g_settings, 62, () -> openSettings(Settings.ACTION_SETTINGS));
        card.addView(gear, at(385, 49, 62, 62));
        TextView gearLabel = text(22, MUTE, f500, -.2f);
        gearLabel.setText("All settings");
        gearLabel.setGravity(Gravity.CENTER);
        card.addView(gearLabel, at(336, 116, 160, 30));
        View profile = circleButton(R.drawable.g_person, 56, () -> {
            closeOverlay();
            if (!tryStart(new Intent().setClassName("com.google.android.apps.tv.launcherx",
                    "com.google.android.apps.tv.launcherx.profile.chooser.ProfileChooserActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null))
                openComponent("com.android.tv.settings", "com.google.android.tv.settings.AccountActivity");
        });
        card.addView(profile, at(464, 52, 56, 56));

        // Tiles and notifications scroll between the header and the footer.
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setVerticalFadingEdgeEnabled(false);
        sv.setFadingEdgeLength(px(40));
        sv.setSmoothScrollingEnabled(true);
        sv.setClipToPadding(false);
        FrameLayout box = new FrameLayout(this);
        View plate = new View(this);
        plate.setBackground(new PlateDrawable(.30f, .16f, .22f, .34f, px(24)));
        Glide g = new Glide(box, plate);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, px(10), 0, px(16));
        box.addView(col, new FrameLayout.LayoutParams(MATCH, WRAP));
        sv.addView(box);
        card.addView(sv, at(0, 160, CW, CH - 160 - 84));

        String netName = netLabel.startsWith("Wi-Fi") ? "Wi-Fi" : netLabel.startsWith("Offline") ? "Offline" : "Ethernet";
        Object[][] items = {
                {0, "Screensaver", (Runnable) this::startScreensaver},
                {R.drawable.g_input, "Inputs", (Runnable) () -> openSettings("com.android.tv.action.VIEW_INPUTS")},
                {R.drawable.g_display, "Display", (Runnable) () -> openSettings(Settings.ACTION_DISPLAY_SETTINGS)},
                {R.drawable.g_sound, "Sound", (Runnable) () -> {
                    if (!tryStart(new Intent("com.android.tv.action.VIEW_SOUND_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null))
                        openSettings(Settings.ACTION_SOUND_SETTINGS);
                    closeOverlay();
                }},
                {netIconRes(), netName, (Runnable) () -> openSettings(Settings.ACTION_WIFI_SETTINGS)},
                {R.drawable.g_home2, "Google Home", (Runnable) () -> {
                    closeOverlay();
                    tryStart(new Intent("com.google.android.libraries.tv.smarthome.intent.action.OPEN_SMART_HOME")
                            .setPackage("com.google.android.apps.tv.dreamx").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Google Home isn't available");
                }},
                {R.drawable.g_bluetooth, "Bluetooth", (Runnable) () -> openComponent("com.android.tv.settings", "com.tcl.settings.accessories.AccessoriesActivity")},
                {R.drawable.g_access, "Accessibility", (Runnable) () -> openSettings(Settings.ACTION_ACCESSIBILITY_SETTINGS)},
        };
        View firstTile = null;
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(WRAP, px(108));
                rl.leftMargin = px(24);
                rl.bottomMargin = px(12);
                col.addView(row, rl);
            }
            View t = gTile((Integer) items[i][0], (String) items[i][1], (Runnable) items[i][2], g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(242), px(108));
            lp.leftMargin = i % 2 == 0 ? 0 : px(12);
            row.addView(t, lp);
            if (firstTile == null) firstTile = t;
        }
        // Notifications, newest first, as in Google's panel.
        int shown = 0;
        android.service.notification.StatusBarNotification[] ns = NotifService.current();
        java.util.Arrays.sort(ns, (x, y) -> Long.compare(y.getPostTime(), x.getPostTime()));
        for (android.service.notification.StatusBarNotification sbn : ns) {
            View c = notifCard(sbn, g);
            if (c == null) continue;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.topMargin = px(shown == 0 ? 18 : 0);
            col.addView(c, lp);
            if (++shown == 6) break;
        }

        // Footer
        TextView brand = text(26, MUTE, f500, -.2f);
        brand.setText("Google TV");
        card.addView(brand, at(35, CH - 62, WRAP, 37));
        View help = circleButton(R.drawable.g_help, 44, () -> openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS));
        card.addView(help, at(CW - 66, CH - 66, 44, 44));

        // Focus order: header row <-> tiles
        gear.setId(View.generateViewId());
        profile.setId(View.generateViewId());
        gear.setNextFocusRightId(profile.getId());
        profile.setNextFocusLeftId(gear.getId());
        profile.setNextFocusRightId(profile.getId());
        showOverlay(layer, new FrameLayout.LayoutParams(MATCH, MATCH), SHEET_FULL, firstTile);
    }

    /** A round glass button that turns solid on focus (the header and footer buttons). */
    View circleButton(int icon, int size, Runnable action) {
        FrameLayout b = new FrameLayout(this);
        b.setFocusable(true);
        b.setClickable(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0x1AFFFFFF);
        bg.setStroke(Math.max(1, px(1.2f)), 0x33FFFFFF);
        b.setBackground(bg);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        b.addView(iv, new FrameLayout.LayoutParams(px(size * .5f), px(size * .5f), Gravity.CENTER));
        b.setOnFocusChangeListener((v, f) -> {
            bg.setColor(f ? SOLID : 0x1AFFFFFF);
            iv.setColorFilter(f ? SOLID_INK : Color.WHITE);
            v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(T).setInterpolator(EASE).start();
        });
        b.setOnClickListener(v -> action.run());
        return b;
    }

    /** One of the eight tiles: glass at rest, the bright frosted plate glides onto it. */
    View gTile(int icon, String label, Runnable action, Glide g) {
        FrameLayout t = new FrameLayout(this);
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(new PlateDrawable(.09f, .05f, .07f, .16f, px(24)));
        View art;
        TextView l = text(label.length() > 10 ? 24 : 27, INK, f500, -.5f);
        l.setText(label);
        l.setSingleLine(true);
        l.setEllipsize(TextUtils.TruncateAt.END);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setAlpha(.9f);
        if (icon == 0) { // Screensaver: Google's illustrated tile
            ImageView scene = new ImageView(this);
            scene.setImageResource(R.drawable.g_scene);
            scene.setScaleType(ImageView.ScaleType.FIT_XY);
            t.addView(scene, new FrameLayout.LayoutParams(MATCH, MATCH));
            art = scene;
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(MATCH, px(36), Gravity.CENTER_VERTICAL);
            llp.leftMargin = px(22);
            llp.bottomMargin = px(10);
            t.addView(l, llp);
        } else {
            ImageView iv = new ImageView(this);
            iv.setImageResource(icon);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(px(32), px(32), Gravity.CENTER_VERTICAL);
            ilp.leftMargin = px(22);
            t.addView(iv, ilp);
            art = iv;
            FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(MATCH, px(36), Gravity.CENTER_VERTICAL);
            llp.leftMargin = px(64);
            llp.rightMargin = px(6);
            t.addView(l, llp);
        }
        t.setOnFocusChangeListener((v, f) -> {
            g.focus(v, f);
            art.animate().alpha(f ? 1f : .9f).setDuration(T).start();
            l.animate().alpha(f ? 1f : .9f).setDuration(160).start();
        });
        t.setOnClickListener(v -> action.run());
        return t;
    }

    /** A notification row: app icon, title, two lines of text. OK opens it. */
    View notifCard(android.service.notification.StatusBarNotification sbn, Glide g) {
        android.app.Notification n = sbn.getNotification();
        if (sbn.getPackageName().equals(getPackageName())) return null;
        if ((n.flags & android.app.Notification.FLAG_ONGOING_EVENT) != 0) return null;
        if ((n.flags & android.app.Notification.FLAG_GROUP_SUMMARY) != 0) return null;
        if ((n.flags & (android.app.Notification.FLAG_FOREGROUND_SERVICE | android.app.Notification.FLAG_NO_CLEAR)) != 0) return null;
        CharSequence title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE);
        CharSequence body = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT);
        if (title == null || title.length() == 0) return null;
        FrameLayout c = new FrameLayout(this);
        c.setFocusable(true);
        c.setClickable(true);
        c.setMinimumHeight(px(150));
        // The app's own icon in a round badge, as Google's panel shows it.
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try { iv.setImageDrawable(getPackageManager().getApplicationIcon(sbn.getPackageName())); } catch (Exception ignored) { }
        iv.setOutlineProvider(new RoundOutline(px(32)));
        iv.setClipToOutline(true);
        c.addView(iv, at(24, 30, 64, 64));
        TextView t = text(28, INK, f500, -.3f);
        t.setText(title);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams tl = at(110, 30, WRAP, 38);
        tl.width = px(544 - 110 - 30);
        c.addView(t, tl);
        if (body != null) {
            TextView b = text(24, FAINT, f500, -.2f);
            b.setText(body);
            b.setMaxLines(2);
            b.setEllipsize(TextUtils.TruncateAt.END);
            b.setLineSpacing(px(4), 1f);
            FrameLayout.LayoutParams bl = at(110, 72, WRAP, WRAP);
            bl.width = px(544 - 110 - 30);
            bl.bottomMargin = px(26);
            c.addView(b, bl);
        }
        c.setOnFocusChangeListener((v, f) -> g.focus(v, f));
        c.setOnClickListener(v -> {
            closeOverlay();
            try { if (n.contentIntent != null) n.contentIntent.send(); } catch (Exception ignored) { }
            if ((n.flags & android.app.Notification.FLAG_AUTO_CANCEL) != 0) NotifService.dismiss(sbn.getKey());
        });
        return c;
    }

    /** Starts Google's screensaver (through its own panel button); falls back to the screensaver settings. */
    boolean dreamPaused, setupHintShown;

    View curtain;
    boolean dreaming;

    /**
     * Back on Home: Home slowly pushes into the photo while the clock, icons and dock drift away and
     * the screen fades to black, then Google's own ambient screensaver fades up. No panels on the way.
     */
    void startScreensaver() {
        if (dreaming) return;
        closeOverlay();
        dreaming = true;
        if (curtain == null) {
            curtain = new View(this);
            curtain.setBackgroundColor(Color.BLACK);
            curtain.setClickable(false);
            curtain.setFocusable(false);
            root.addView(curtain, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        curtain.bringToFront();
        curtain.animate().cancel();
        curtain.setAlpha(0f);
        curtain.setVisibility(View.VISIBLE);
        for (View v : new View[]{bgA, bgB}) {
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(420).setInterpolator(Motion.OUT).start();
        }
        clock.animate().alpha(0f).translationY(-px(18)).setStartDelay(0).setDuration(220).setInterpolator(Motion.IN).start();
        date.animate().alpha(0f).translationY(-px(12)).setStartDelay(0).setDuration(220).setInterpolator(Motion.IN).start();
        wx.animate().alpha(0f).translationY(-px(10)).setStartDelay(0).setDuration(220).setInterpolator(Motion.IN).start();
        utils.animate().alpha(0f).translationY(-px(10)).setStartDelay(0).setDuration(200).setInterpolator(Motion.IN).start();
        dock.animate().alpha(0f).translationY(px(36)).setStartDelay(0).setDuration(240).setInterpolator(Motion.IN).start();
        utilLabel.animate().alpha(0f).setDuration(150).start();
        // The service's cover fades the whole screen to black (about a third of a second) and starts the
        // screensaver the instant it is black; our own curtain goes black underneath for the way back.
        if (HomeKeyService.startScreensaverCovered(200, () -> curtain.setAlpha(1f))) return;
        curtain.animate().alpha(1f).setStartDelay(0).setDuration(300).setInterpolator(Motion.INOUT).withEndAction(this::enterDream).start();
    }

    void enterDream() {
        wakeFromDream();
        openComponent("com.android.tv.settings", "com.google.android.tv.settings.AmbientActivity");
    }

    /** Back from the screensaver: the black lifts off the photo as it settles back, and Home builds in. */
    void wakeFromDream() {
        dreaming = false;
        if (curtain == null || curtain.getVisibility() != View.VISIBLE) return;
        for (View v : new View[]{bgA, bgB}) {
            v.animate().scaleX(1f).scaleY(1f).setDuration(1600).setInterpolator(Motion.OUT)
                    .withEndAction(() -> v.setLayerType(View.LAYER_TYPE_NONE, null)).start();
        }
        curtain.animate().cancel();
        curtain.animate().alpha(0f).setStartDelay(0).setDuration(1200).setInterpolator(Motion.INOUT)
                .withEndAction(() -> curtain.setVisibility(View.GONE)).start();
        for (View v : new View[]{clock, date, wx, utils, dock}) v.setTranslationY(0);
        animateIn(350, 1.25f); // about half a second slower than a normal arrival, unhurried after the screensaver
    }

    int netIconRes() {
        if (netLabel.startsWith("Wi-Fi")) return R.drawable.g_wifi3;
        if (netLabel.startsWith("Offline")) return R.drawable.g_wifioff;
        return R.drawable.g_ethernet;
    }

    void openSettings(String action) {
        closeOverlay();
        tryStart(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Settings isn't available");
    }

    void openComponent(String pkg, String cls) {
        closeOverlay();
        tryStart(new Intent().setClassName(pkg, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "Settings isn't available");
    }

    void pick(String key) {
        prefs.edit().putString("wall", key).apply();
        dismissOverlay(false);
        applyWallpaper(false);
    }

    interface FocusFn { void on(boolean f); }

    View.OnFocusChangeListener chain(View.OnFocusChangeListener a, FocusFn b) {
        return (v, f) -> { a.onFocusChange(v, f); b.on(f); };
    }

    void showToast(String msg) {
        toast.setText(msg);
        toast.bringToFront();
        toast.animate().cancel();
        toast.setAlpha(0f);
        toast.setTranslationY(-px(8));
        toast.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(EASE).start();
        h.removeCallbacks(hideToast);
        h.postDelayed(hideToast, 2200);
    }

    // ================================================================ data

    void loadApps() {
        apps.clear();
        PackageManager pm = getPackageManager();
        List<ResolveInfo> all = new ArrayList<>();
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        all.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Map<String, ResolveInfo> seen = new LinkedHashMap<>();
        for (ResolveInfo ri : all) {
            String p = ri.activityInfo.packageName;
            if (!p.equals(getPackageName()) && !seen.containsKey(p)) seen.put(p, ri);
        }
        List<ResolveInfo> sorted = new ArrayList<>(seen.values());
        Map<ResolveInfo, String> labels = new HashMap<>();
        for (ResolveInfo ri : sorted) labels.put(ri, String.valueOf(ri.loadLabel(pm)));
        Collections.sort(sorted, (a, c) -> labels.get(a).compareToIgnoreCase(labels.get(c)));
        for (ResolveInfo ri : sorted) apps.put(ri.activityInfo.packageName, ri);
    }

    List<String> dockList() {
        List<String> out = new ArrayList<>();
        String saved = prefs.getString("dock", null);
        String[] src = saved == null ? DEFAULT_DOCK : (saved.isEmpty() ? new String[0] : saved.split(","));
        for (String p : src) if (apps.containsKey(p) && !out.contains(p) && out.size() < MAX_DOCK) out.add(p);
        return out;
    }

    /**
     * Sets the dock order from outside, e.g. from a computer:
     *   adb shell am start -n com.samir.cleanhome/.HomeActivity --es setDock "com.netflix.ninja,com.google.android.youtube.tv"
     * Apps that aren't installed yet keep their place and appear once installed.
     */
    void applyDockExtra(Intent intent) {
        String v = intent.getStringExtra("setDock");
        if (v == null) return;
        List<String> d = new ArrayList<>();
        for (String p : v.split(",")) { p = p.trim(); if (!p.isEmpty() && !d.contains(p) && d.size() < MAX_DOCK) d.add(p); }
        saveDock(d);
        lastDockFocus = 0;
        buildDock();
        focusDock(0);
    }

    void saveDock(List<String> d) { prefs.edit().putString("dock", TextUtils.join(",", d)).apply(); }

    String appLabel(String pkg) {
        ResolveInfo ri = apps.get(pkg);
        if (ri == null) return pkg;
        if (pkg.equals("com.tcl.tv")) return "Live TV";
        if (pkg.equals("com.android.vending")) return "Play Store";
        return String.valueOf(ri.loadLabel(getPackageManager()));
    }

    // ================================================================ actions

    void updateClock() {
        Date now = new Date();
        String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(now);
        if (!hm.contentEquals(clock.getText())) clock.setText(hm); // the minute simply changes
        date.setText(new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now));
        fitClock();
        fitWeather();
    }

    boolean live() { return live == this && overlay.getVisibility() != View.VISIBLE; }

    float clockSize = 0;

    /** Sizes the clock so it spans the same width as the date under it (measured on "00:00" so it never jumps per minute). */
    void fitClock() {
        float dateW = date.getPaint().measureText(String.valueOf(date.getText())) / s;
        android.graphics.Paint p = new android.graphics.Paint(clock.getPaint());
        p.setTextSize(px(100));
        float w100 = p.measureText("00:00") / s;
        if (w100 <= 0 || dateW <= 0) return;
        float size = Math.max(100, Math.min(136, 100 * dateW / w100));
        if (Math.abs(size - clockSize) < .5f) return;
        clockSize = size;
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        int boxH = Math.round(size * 1.24f);
        FrameLayout.LayoutParams c = (FrameLayout.LayoutParams) clock.getLayoutParams();
        c.topMargin = px(40);
        c.height = px(boxH);
        clock.setLayoutParams(c);
        FrameLayout.LayoutParams d = (FrameLayout.LayoutParams) date.getLayoutParams();
        d.topMargin = px(40 + boxH + 2);
        date.setLayoutParams(d);
        FrameLayout.LayoutParams wl = (FrameLayout.LayoutParams) wx.getLayoutParams();
        wl.topMargin = px(40 + boxH + 2 + 54);
        wx.setLayoutParams(wl);
    }

    void launch(String pkg) { launch(pkg, null); }

    /** Opens an app; from a tile, the app zooms up out of that tile. */
    void launch(String pkg, View from) {
        android.os.Bundle opts = null;
        if (from != null && from.isAttachedToWindow()) {
            try { opts = android.app.ActivityOptions.makeScaleUpAnimation(from, 0, 0, from.getWidth(), from.getHeight()).toBundle(); }
            catch (Exception ignored) { }
        }
        final android.os.Bundle o = opts;
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { showToast("Can't open " + appLabel(pkg)); return; }
        final Intent start = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (DnsPause.APPS.contains(pkg) && DnsPause.canWrite(this)) {
            // Switch ad-blocking DNS off first so the app sees a normal connection when it checks.
            DnsPause.pause(this);
            h.postDelayed(() -> tryStart(start, null), 900);
            return;
        }
        try { startActivity(start, o); } catch (Exception e) { tryStart(start, null); }
    }

    boolean tryStart(Intent i, String failMsg) {
        try { startActivity(i); return true; }
        catch (Exception e) { if (failMsg != null) showToast(failMsg); return false; }
    }

    void voice() {
        if (tryStart(new Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null)) return;
        tryStart(new Intent("android.search.action.GLOBAL_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                "Use the mic button on the remote");
    }



    // ================================================================ view helpers

    Typeface font(int weight) {
        try {
            return new Typeface.Builder(getAssets(), "fonts/Manrope.ttf").setFontVariationSettings("'wght' " + weight).build();
        } catch (Exception e) {
            return Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }
    }

    TextView text(float sizePx, int color, Typeface tf, float trackingPx) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizePx));
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        if (trackingPx != 0) t.setLetterSpacing(trackingPx / sizePx);
        t.setIncludeFontPadding(false);
        return t;
    }

    FrameLayout.LayoutParams at(int x, int y, int w, int hh) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w == WRAP ? WRAP : px(w), hh == WRAP ? WRAP : px(hh), Gravity.TOP | Gravity.START);
        lp.leftMargin = px(x);
        lp.topMargin = px(y);
        return lp;
    }

    GradientDrawable round(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }
}
