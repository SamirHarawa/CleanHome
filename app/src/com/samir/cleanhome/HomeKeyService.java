package com.samir.cleanhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Google TV's launcher always claims the Home button. This service sends Home to CleanHome,
 * and if the Google TV home screen ever appears (boot, a crash), it switches straight back.
 * Every other key, including the mic, YouTube and gear buttons, passes through untouched.
 */
public class HomeKeyService extends AccessibilityService {

    static final String GTV = "com.google.android.apps.tv.launcherx";
    static HomeKeyService instance;

    String lastWindow = "";

    /**
     * At power-on this service's process starts while Google TV's home is still loading, about a second
     * before the system finishes connecting the service. Opening CleanHome right here gets it in front
     * before Google TV's home has drawn at all. Only in the first two minutes after boot, when nothing else
     * can be on screen yet; later (the service switched back on, an update) it waits to connect as usual.
     */
    @Override public void onCreate() {
        super.onCreate();
        if (android.os.SystemClock.elapsedRealtime() < 120_000 && HomeActivity.live == null) {
            try { goHome(); } catch (Exception ignored) { }
        }
    }

    @Override protected void onServiceConnected() {
        instance = this;
        // Connected while Google TV home is already showing (after an update or at boot): leave it.
        // At boot this service can connect before Google TV has drawn its home, and the window event for it
        // may come before we are listening, so keep looking for a while rather than checking once.
        bootChecks = 0;
        // Just after power-on, Google TV's home is still starting up (it takes ~1.5 s to draw). Switching
        // now puts CleanHome in front before it ever shows, instead of waiting to see it first.
        if (android.os.SystemClock.elapsedRealtime() < 180_000 && bootScreen()) {
            lastWindow = "home.HomeActivity";
            goHome();
        }
        h.post(bootCheck);
    }

    /** At start-up: nothing on screen yet, or only Google TV home / the system's placeholder home. */
    boolean bootScreen() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return true;
            String pkg = String.valueOf(root.getPackageName());
            return GTV.equals(pkg) || "com.android.tv.settings".equals(pkg);
        } catch (Exception e) { return false; }
    }

    int bootChecks;
    final Runnable bootCheck = this::bootCheck;

    void bootCheck() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                String pkg = String.valueOf(root.getPackageName());
                if (GTV.equals(pkg) && !lastWindow.contains(".dashboard.") && !lastWindow.endsWith("ProfileChooserActivity")) {
                    lastWindow = "home.HomeActivity";
                    goHome();
                    return;
                }
                if (getPackageName().equals(pkg)) return; // CleanHome is up: nothing to do
            }
        } catch (Exception ignored) { }
        if (++bootChecks < 40) h.postDelayed(bootCheck, 500); // up to 20 s
    }
    @Override public boolean onUnbind(Intent i) { instance = null; return super.onUnbind(i); }

    // ---- Starting the Google screensaver: Android only lets system apps do it directly, so open Google's
    // panel and press its own "Start Screensaver" button.
    final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    int dreamTries = -1;

    static boolean startScreensaver() {
        HomeKeyService s = instance;
        if (s == null) return false;
        s.dreamTries = 0;
        if (!s.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) { s.dreamTries = -1; return false; }
        s.h.postDelayed(s::pressScreensaver, 30);
        return true;
    }

    // ---- A black cover above every window (accessibility overlays need no extra permission), so Google's
    // panel is never seen while its Screensaver button is pressed underneath.
    android.view.View cover;
    boolean awaitDream;

    static boolean coverShowing() { return instance != null && instance.cover != null; }

    /** Each Back-to-screensaver attempt gets its own number, so a timer from an earlier one can never touch it. */
    int coverGen = 0;

    /**
     * The cover fades the screen to black over fadeMs. Only once it is on screen and fully opaque is Google's
     * panel opened beneath it, so the panel can never be seen.
     */
    static boolean startScreensaverCovered(long fadeMs, Runnable onBlack) {
        HomeKeyService s = instance;
        if (s == null) return false;
        s.removeCoverNow();
        s.showCover();
        if (s.cover == null) return false;
        final android.view.View v = s.cover;
        final int gen = ++s.coverGen;
        v.setAlpha(0f);
        // Wait for the cover window to really be on screen before fading (it can take a frame or two to attach).
        v.post(() -> v.postOnAnimation(() -> {
            if (s.coverGen != gen) return;
            v.animate().alpha(1f).setDuration(fadeMs).setInterpolator(Motion.OUT).withEndAction(() -> {
                if (s.coverGen != gen) return;
                if (onBlack != null) onBlack.run();
                // Opaque; one more frame so that black is what is actually displayed, then open the panel.
                v.postOnAnimation(() -> {
                    if (s.coverGen != gen) return;
                    if (!startScreensaver()) s.hideCover(0);
                });
            }).start();
        }));
        s.h.postDelayed(() -> { if (s.coverGen == gen) s.safeHideCover(); }, 6000); // never leave it up
        return true;
    }

    /** Lifts the cover, but first sends Google's panel away if it is still open, so it is never revealed. */
    void safeHideCover() {
        dreamTries = -1;
        awaitDream = false;
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && GTV.contentEquals(String.valueOf(root.getPackageName())) && lastWindow.contains(".dashboard.")) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            final int gen = coverGen;
            h.postDelayed(() -> { if (coverGen == gen) hideCover(0); }, 450);
        } else hideCover(0);
    }

    void removeCoverNow() {
        if (cover == null) return;
        try { ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover); } catch (Exception ignored) { }
        cover = null;
    }

    void showCover() {
        if (cover != null) return;
        try {
            android.view.WindowManager wm = (android.view.WindowManager) getSystemService(WINDOW_SERVICE);
            android.view.View v = new android.view.View(this);
            v.setBackgroundColor(0xFF000000);
            android.view.WindowManager.LayoutParams lp = new android.view.WindowManager.LayoutParams(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT);
            lp.windowAnimations = 0;
            wm.addView(v, lp);
            cover = v;
        } catch (Exception ignored) { cover = null; }
    }

    /** Fades the cover away, revealing whatever is now on screen (the screensaver). */
    void hideCover(long delay) {
        final android.view.View v = cover;
        if (v == null) return;
        cover = null;
        v.animate().alpha(0f).setStartDelay(delay).setDuration(300).setInterpolator(Motion.INOUT).withEndAction(() -> {
            try { ((android.view.WindowManager) getSystemService(WINDOW_SERVICE)).removeView(v); } catch (Exception ignored) { }
        }).start();
    }

    void pressScreensaver() {
        if (dreamTries < 0) return;
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && GTV.contentEquals(String.valueOf(root.getPackageName()))) {
            java.util.List<android.view.accessibility.AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByText("Screensaver");
            // Other languages: the Screensaver button is the first tile of the panel's grid.
            if (found.isEmpty()) { android.view.accessibility.AccessibilityNodeInfo t = firstGridTile(root); if (t != null) found = java.util.Collections.singletonList(t); }
            for (android.view.accessibility.AccessibilityNodeInfo n : found) {
                android.view.accessibility.AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) {
                    dreamTries = -1;
                    awaitDream = true; // lift the cover once the screensaver itself is on screen
                    final int gen = coverGen;
                    h.postDelayed(() -> { if (awaitDream && coverGen == gen) safeHideCover(); }, 2000);
                    return;
                }
            }
        }
        if (++dreamTries < 60) h.postDelayed(this::pressScreensaver, 30);
        else safeHideCover();
    }

    // ---- The Google profile picture: copied from Google's own panel whenever it is open, for CleanHome's account button.
    final Runnable captureAvatar = this::captureAvatar;
    long avatarAt = 0;

    void captureAvatar() {
        if (android.os.Build.VERSION.SDK_INT < 30 || dreamTries >= 0) return;
        if (avatarAt != 0 && android.os.SystemClock.uptimeMillis() - avatarAt < 600_000) return;
        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || !GTV.contentEquals(String.valueOf(root.getPackageName()))) return;
        for (android.view.accessibility.AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText("profile selector")) {
            if (n.isFocused()) return; // its focus ring would be in the picture; try another time
            android.graphics.Rect r = new android.graphics.Rect();
            n.getBoundsInScreen(r);
            if (r.width() < 24 || r.height() < 24) return;
            avatarAt = android.os.SystemClock.uptimeMillis();
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(), new Shot(this, r));
            return;
        }
    }

    static final class Shot implements TakeScreenshotCallback {
        final HomeKeyService s; final android.graphics.Rect r;
        Shot(HomeKeyService s, android.graphics.Rect r) { this.s = s; this.r = r; }
        @Override public void onSuccess(ScreenshotResult res) {
            try {
                android.graphics.Bitmap hw = android.graphics.Bitmap.wrapHardwareBuffer(res.getHardwareBuffer(), res.getColorSpace());
                res.getHardwareBuffer().close();
                if (hw == null) return;
                android.graphics.Bitmap full = hw.copy(android.graphics.Bitmap.Config.ARGB_8888, false);
                // Screenshot is in real pixels; node bounds are too.
                int inset = Math.max(2, Math.round(r.width() * .09f));
                android.graphics.Rect c = new android.graphics.Rect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset);
                if (c.right > full.getWidth() || c.bottom > full.getHeight()) return;
                int size = 128;
                android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas cv = new android.graphics.Canvas(out);
                android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
                cv.drawCircle(size / 2f, size / 2f, size / 2f - 1, p);
                p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
                cv.drawBitmap(full, c, new android.graphics.Rect(0, 0, size, size), p);
                java.io.File tmp = new java.io.File(s.getFilesDir(), "avatar.tmp");
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(tmp)) { out.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); }
                tmp.renameTo(new java.io.File(s.getFilesDir(), "avatar.png"));
                HomeActivity a = HomeActivity.live;
                if (a != null) a.refreshAvatar();
            } catch (Exception ignored) { }
        }
        @Override public void onFailure(int code) { }
    }

    /** Opens Google TV's quick-settings sidebar (SystemUI routes the panel to it). */
    static boolean openQuickSettings() {
        return instance != null && instance.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (Remote.handleGlobal(this, e)) return true;
        // Back on Google's account chooser: it is see-through over Google TV's own home, so closing it would
        // flash that home. Go straight back to CleanHome instead.
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK && lastWindow.endsWith("ProfileChooserActivity") && chooserInFront()) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) goHome();
            return true;
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (e.getAction() == KeyEvent.ACTION_UP && !e.isCanceled()) goHome();
            return true;
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && lastWindow.contains(".dashboard.") && unstickPanelUp(e)) return true;
        return false;
    }

    /**
     * Google's quick settings panel has a dead end: on the "Help & feedback" button at the very bottom, Up goes
     * nowhere (the gap above it is too big for its focus search). When focus is on that bottom button, move it
     * ourselves to the nearest item above it. Found by position, so it works in every language.
     */
    boolean unstickPanelUp(KeyEvent e) {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || !GTV.contentEquals(String.valueOf(root.getPackageName()))) return false;
            android.view.accessibility.AccessibilityNodeInfo f = root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);
            if (f == null) return false;
            android.graphics.Rect fr = new android.graphics.Rect(), rr = new android.graphics.Rect();
            f.getBoundsInScreen(fr); root.getBoundsInScreen(rr);
            if (fr.top < rr.top + rr.height() * 0.8f) return false; // not the bottom button: Google handles Up fine
            if (e.getAction() != KeyEvent.ACTION_DOWN) return true;  // swallow the matching key-up too
            android.view.accessibility.AccessibilityNodeInfo best = null;
            int bestBottom = Integer.MIN_VALUE; long bestScore = Long.MAX_VALUE;
            java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo> q = new java.util.ArrayDeque<>();
            q.add(root);
            android.graphics.Rect r = new android.graphics.Rect();
            while (!q.isEmpty()) {
                android.view.accessibility.AccessibilityNodeInfo n = q.poll();
                for (int i = 0; i < n.getChildCount(); i++) { android.view.accessibility.AccessibilityNodeInfo c = n.getChild(i); if (c != null) q.add(c); }
                if (!n.isFocusable() || !n.isVisibleToUser()) continue;
                n.getBoundsInScreen(r);
                if (r.bottom > fr.top || r.height() > rr.height() / 3) continue; // above the button, and not a whole list
                long dx = Math.abs(r.centerX() - fr.centerX());
                long score = (long) r.width() * r.height() + dx; // the smallest, closest item on the lowest row
                if (r.bottom > bestBottom || (r.bottom == bestBottom && score < bestScore)) { best = n; bestBottom = r.bottom; bestScore = score; }
            }
            if (best != null) best.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS);
            return true;
        } catch (Exception ex) { return false; }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent ev) {
        if (awaitDream && ev.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && "com.google.android.apps.tv.dreamx".contentEquals(String.valueOf(ev.getPackageName()))) {
            awaitDream = false;
            hideCover(0); // the screensaver is up: let it fade in out of the black
        }
        if (ev.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && ev.getPackageName() != null) {
            DnsPause.onForeground(this, ev.getPackageName().toString());
        }
        if (!GTV.contentEquals(String.valueOf(ev.getPackageName()))) return;
        if (ev.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence cls = ev.getClassName();
            lastWindow = cls == null ? "" : cls.toString();
            if (lastWindow.endsWith("home.HomeActivity")) goHome();
            if (lastWindow.contains(".dashboard.") && dreamTries < 0) {
                h.removeCallbacks(captureAvatar);
                h.postDelayed(captureAvatar, 900);
            }
        } else if (ev.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED && lastWindow.endsWith("home.HomeActivity")) {
            // Someone is moving around on Google TV home (it was already up): take them to CleanHome.
            goHome();
        }
    }

    static android.view.accessibility.AccessibilityNodeInfo firstGridTile(android.view.accessibility.AccessibilityNodeInfo n) {
        if (n == null) return null;
        if (String.valueOf(n.getClassName()).endsWith("GridView")) {
            for (int i = 0; i < n.getChildCount(); i++) { android.view.accessibility.AccessibilityNodeInfo c = n.getChild(i); if (c != null && c.isClickable()) return c; }
            return null;
        }
        for (int i = 0; i < n.getChildCount(); i++) { android.view.accessibility.AccessibilityNodeInfo r = firstGridTile(n.getChild(i)); if (r != null) return r; }
        return null;
    }

    boolean chooserInFront() {
        try {
            android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
            return root != null && GTV.contentEquals(String.valueOf(root.getPackageName()));
        } catch (Exception e) { return false; }
    }

    void goHome() {
        lastWindow = ""; // we are leaving Google TV home; only a new home window re-arms the focus check
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startActivity(i);
    }

    @Override
    public void onInterrupt() { }
}
