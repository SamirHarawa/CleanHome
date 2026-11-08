package com.samir.cleanhome;

import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

/** Remote buttons that Google TV's home used to answer, now answered by CleanHome. */
final class Remote {
    private Remote() { }

    /** When CleanHome last answered the settings button (uptime ms). */
    static volatile long lastSettingsAt = 0;

    static boolean isSettings(int k) { return k == KeyEvent.KEYCODE_SETTINGS || k == KeyEvent.KEYCODE_NOTIFICATION; }
    static boolean isYouTube(int k) { return k == KeyEvent.KEYCODE_BUTTON_3; }

    /** Inside CleanHome: settings toggles the glass panel, YouTube opens YouTube. */
    static boolean handle(HomeActivity a, KeyEvent e) {
        int k = e.getKeyCode();
        if (!isSettings(k) && !isYouTube(k)) return false;
        if (isSettings(k)) {
            // Google's own quick-settings panel answers this button; just tidy away any CleanHome sheet.
            if (e.getAction() == KeyEvent.ACTION_DOWN && a.overlay.getVisibility() == android.view.View.VISIBLE) a.closeOverlay();
            return false;
        }
        if (e.getAction() == KeyEvent.ACTION_UP) a.launch("com.google.android.youtube.tv");
        return true;
    }

    /** In any other app (via the accessibility service): bring up CleanHome's panel, or open YouTube. */
    static boolean handleGlobal(Context c, KeyEvent e) {
        int k = e.getKeyCode();
        if (!isYouTube(k)) return false; // settings: Google's own panel opens as normal
        if (HomeActivity.live != null) return false; // CleanHome is in front: it handles the key itself
        if (e.getAction() == KeyEvent.ACTION_UP) {
            Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv");
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(i); } catch (Exception ignored) { }
            }
        }
        return true;
    }
}
