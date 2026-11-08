package com.samir.cleanhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Some apps refuse to run with an ad-blocking private DNS (Reezn's free tier). While such an app is in
 * front, private DNS is switched off; as soon as you leave it, the saved setting comes back.
 * Needs WRITE_SECURE_SETTINGS (granted once over adb).
 */
final class DnsPause {
    private DnsPause() { }

    /** Apps that need private DNS off while open. */
    static final Set<String> APPS = new HashSet<>(Arrays.asList("com.rmedia.rplayer"));

    /** Windows that are not "leaving the app": system pop-ups, keyboards, voice overlay. */
    static final Set<String> IGNORE = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.google.android.katniss",
            "com.tcl.inputmethod.international", "com.google.android.inputmethod.latin",
            "com.tcl.globalkeyoverlay", "com.tcl.systemui.plugin"));

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("dnspause", Context.MODE_PRIVATE);
    }

    static boolean canWrite(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Called with the package that just came to the front. */
    static void onForeground(Context c, String pkg) {
        if (pkg == null || IGNORE.contains(pkg)) return;
        if (APPS.contains(pkg)) pause(c); else resume(c);
    }

    static synchronized void pause(Context c) {
        if (!canWrite(c) || prefs(c).getBoolean("paused", false)) return;
        try {
            String mode = Settings.Global.getString(c.getContentResolver(), "private_dns_mode");
            String spec = Settings.Global.getString(c.getContentResolver(), "private_dns_specifier");
            if (!"hostname".equals(mode)) return; // nothing to pause
            prefs(c).edit().putBoolean("paused", true).putString("mode", mode).putString("spec", spec).commit();
            Settings.Global.putString(c.getContentResolver(), "private_dns_mode", "off");
        } catch (Exception ignored) { }
    }

    static synchronized void resume(Context c) {
        SharedPreferences p = prefs(c);
        if (!canWrite(c) || !p.getBoolean("paused", false)) return;
        try {
            String spec = p.getString("spec", null);
            if (spec != null) Settings.Global.putString(c.getContentResolver(), "private_dns_specifier", spec);
            Settings.Global.putString(c.getContentResolver(), "private_dns_mode", p.getString("mode", "hostname"));
            p.edit().putBoolean("paused", false).commit();
        } catch (Exception ignored) { }
    }
}
