package com.samir.cleanhome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Map;
import java.util.Set;

/**
 * A copy of CleanHome's settings (dock, wallpaper, weather location...) and the profile picture, kept in the TV's
 * own system settings, which outlive the app. After a reinstall CleanHome starts exactly as it was left instead of
 * from scratch. Needs WRITE_SECURE_SETTINGS (granted by the installer); without it nothing is saved.
 */
final class Backup {
    private Backup() { }

    static final String KEY = "cleanhome_backup", AVATAR = "cleanhome_avatar";
    /** Changes every few minutes and mean nothing on another start: not worth a write. */
    static final String[] SKIP = {"wx_tried"};

    /** On a fresh install (no settings at all), brings everything back from the copy, if there is one. */
    static boolean restoreIfFresh(Context c, SharedPreferences p) {
        if (!p.getAll().isEmpty()) return false;
        String s = read(c, KEY);
        if (s == null || s.isEmpty()) return false;
        try {
            JSONObject o = new JSONObject(s);
            SharedPreferences.Editor e = p.edit();
            JSONArray names = o.names();
            for (int i = 0; names != null && i < names.length(); i++) {
                String k = names.getString(i);
                JSONObject v = o.getJSONObject(k);
                switch (v.getString("t")) {
                    case "s": e.putString(k, v.getString("v")); break;
                    case "i": e.putInt(k, v.getInt("v")); break;
                    case "l": e.putLong(k, v.getLong("v")); break;
                    case "f": e.putFloat(k, (float) v.getDouble("v")); break;
                    case "b": e.putBoolean(k, v.getBoolean("v")); break;
                    case "S": {
                        JSONArray a = v.getJSONArray("v");
                        java.util.HashSet<String> set = new java.util.HashSet<>();
                        for (int j = 0; j < a.length(); j++) set.add(a.getString(j));
                        e.putStringSet(k, set);
                        break;
                    }
                }
            }
            e.commit();
            String av = read(c, AVATAR);
            if (av != null && !av.isEmpty()) {
                byte[] png = android.util.Base64.decode(av, android.util.Base64.DEFAULT);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(c.getFilesDir(), "avatar.png"))) { out.write(png); }
            }
            return true;
        } catch (Exception e) { return false; }
    }

    static String last;

    /** Saves the settings as they are now (only when something changed). Call off the main thread. */
    static void save(Context c, SharedPreferences p) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
                String k = en.getKey();
                boolean skip = false;
                for (String x : SKIP) if (x.equals(k)) skip = true;
                if (skip) continue;
                Object v = en.getValue();
                JSONObject e = new JSONObject();
                if (v instanceof String) e.put("t", "s").put("v", v);
                else if (v instanceof Integer) e.put("t", "i").put("v", v);
                else if (v instanceof Long) e.put("t", "l").put("v", v);
                else if (v instanceof Float) e.put("t", "f").put("v", (double) (Float) v);
                else if (v instanceof Boolean) e.put("t", "b").put("v", v);
                else if (v instanceof Set) {
                    JSONArray a = new JSONArray();
                    for (Object x : (Set<?>) v) a.put(String.valueOf(x));
                    e.put("t", "S").put("v", a);
                } else continue;
                o.put(k, e);
            }
            String s = o.toString();
            if (s.equals(last)) return;
            if (write(c, KEY, s)) last = s;
        } catch (Exception e) { android.util.Log.w("CleanHomeBackup", "save: " + e); }
    }

    /** Keeps a copy of the profile picture too (a small PNG). */
    static void saveAvatar(Context c, File f) {
        try {
            if (f.length() > 96 * 1024) return;
            byte[] b = new byte[(int) f.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
            write(c, AVATAR, android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP));
        } catch (Exception ignored) { }
    }

    static String read(Context c, String k) {
        try { return Settings.Secure.getString(c.getContentResolver(), k); } catch (Exception e) { return null; }
    }

    static boolean write(Context c, String k, String v) {
        try {
            ContentResolver r = c.getContentResolver();
            return Settings.Secure.putString(r, k, v);
        } catch (Exception e) { android.util.Log.w("CleanHomeBackup", "write " + k + ": " + e); return false; }
    }
}
