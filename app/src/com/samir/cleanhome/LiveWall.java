package com.samir.cleanhome;

import android.content.SharedPreferences;
import android.graphics.BitmapFactory;

import java.io.File;

/**
 * Live wallpapers straight from Google, never a stored set: a new picture every 15 minutes, chosen from
 * Google's Chromecast ambient feed (a huge, constantly changing pool of photos and art) and Google Arts &
 * Culture's masterpieces. Only two pictures are ever on the TV: the one showing and the next one, which is
 * downloaded in advance so the change is instant. Everything already shown is remembered, so nothing repeats.
 */
final class LiveWall {
    static final long ROTATE_MS = 15 * 60_000L;
    static final String AMBIENT_FEED = "https://clients3.google.com/cast/chromecast/home";
    static final String ART_FEED = "https://www.gstatic.com/culturalinstitute/tabext/imax_2_2.json";
    static final int SEEN_MAX = 2000; // about 20 days of pictures at one every 15 minutes

    final File dir;
    final SharedPreferences p;
    volatile boolean busy;

    LiveWall(File dir, SharedPreferences p) { this.dir = dir; this.p = p; }

    /** A picture on disk: its file and a one-line credit. */
    static final class Pic {
        final String file, credit;
        Pic(String file, String credit) { this.file = file; this.credit = credit; }
        String json() {
            try { return new org.json.JSONObject().put("file", file).put("credit", credit).toString(); } catch (Exception e) { return ""; }
        }
        static Pic from(String s) {
            if (s == null || s.isEmpty()) return null;
            try { org.json.JSONObject o = new org.json.JSONObject(s); return new Pic(o.getString("file"), o.optString("credit")); }
            catch (Exception e) { return null; }
        }
    }

    File file(String name) { return new File(dir, name); }

    /** The picture showing now, if it is on disk. */
    Pic current() {
        Pic c = Pic.from(p.getString("live_cur", null));
        return c != null && file(c.file).exists() ? c : null;
    }

    /**
     * Call often (it is cheap). Off the UI thread it promotes the prepared picture when 15 minutes are up
     * (or right away when force is set), then prepares the next one. onChange runs after a new picture is current.
     */
    void tick(boolean artOnly, boolean force, Runnable onChange) {
        if (busy) return;
        Pic cur = current();
        Pic next = Pic.from(p.getString("live_next", null));
        boolean nextReady = next != null && file(next.file).exists();
        boolean wrongKind = cur != null && p.getBoolean("live_cur_art", false) != artOnly; // e.g. a painting after choosing photos only
        boolean due = cur == null || force || wrongKind || System.currentTimeMillis() - p.getLong("live_at", 0) >= ROTATE_MS;
        boolean nextFits = artOnly == p.getBoolean("live_next_art", false);
        if (nextReady && nextFits && !due) return; // the next picture is waiting and it isn't time yet
        busy = true;
        new Thread(() -> {
            try {
                Pic n = Pic.from(p.getString("live_next", null));
                boolean ready = n != null && file(n.file).exists();
                if (ready && artOnly != p.getBoolean("live_next_art", false)) {
                    // Switched style: the prepared picture is the wrong kind; prepare the right one.
                    file(n.file).delete(); ready = false; n = null;
                }
                if (!ready) { n = prepare(artOnly); }
                if (n != null && (current() == null || force || wrongKind || System.currentTimeMillis() - p.getLong("live_at", 0) >= ROTATE_MS)) {
                    Pic old = current();
                    p.edit().putString("live_cur", n.json()).putBoolean("live_cur_art", p.getBoolean("live_next_art", false))
                            .putLong("live_at", System.currentTimeMillis()).remove("live_next").apply();
                    if (old != null && !old.file.equals(n.file)) file(old.file).delete();
                    cleanup();
                    if (onChange != null) onChange.run();
                    n = prepare(artOnly); // and have the next one waiting
                }
            } catch (Exception ignored) {
            } finally { busy = false; }
        }).start();
    }

    /** Downloads one picture nobody has seen yet and stores it as "next". */
    Pic prepare(boolean artOnly) {
        int turn = p.getInt("live_turn", 0);
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean art = artOnly;
            String[] cand = art ? nextArt() : nextAmbient();
            if (cand == null) { if (!artOnly) continue; return null; }
            String url = cand[0], credit = cand[1], key = key(url);
            markSeen(key);
            String name = "live-" + Integer.toHexString(key.hashCode()) + ".jpg";
            File f = file(name), tmp = file("live.tmp");
            try {
                String big = art ? url + "=w3840-h2160" : url.replaceAll("=w\\d+-h\\d+.*$", "") + "=w3840-h2160";
                Weather.download(big, tmp);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(tmp.getPath(), o);
                // Must fill a TV well: landscape and at least Full HD wide.
                if (o.outWidth < 1600 || o.outWidth < o.outHeight * 1.25f) { tmp.delete(); continue; }
                if (!tmp.renameTo(f)) continue;
                Pic n = new Pic(name, credit);
                p.edit().putString("live_next", n.json()).putBoolean("live_next_art", art).putInt("live_turn", turn + attempt + 1).apply();
                return n;
            } catch (Exception e) { tmp.delete(); }
        }
        return null;
    }

    // ---- sources

    /** A fresh photo from Google's ambient feed (each request returns a new mix of about 50). */
    String[] nextAmbient() {
        try {
            org.json.JSONArray q = new org.json.JSONArray(p.getString("live_q", "[]"));
            for (int tries = 0; q.length() == 0 && tries < 4; tries++) {
                String html = Weather.get(AMBIENT_FEED, "Mozilla/5.0 (Linux; Android 9) AppleWebKit/537.36 (KHTML, like Gecko) CrKey/1.56.500000");
                int a = html.indexOf("JSON.parse('"), e = html.indexOf("'))", a);
                org.json.JSONArray items = new org.json.JSONArray(HomeActivity.unescapeJs(html.substring(a + 12, e))).getJSONArray(0);
                for (int i = 0; i < items.length(); i++) {
                    org.json.JSONArray it = items.getJSONArray(i);
                    String url = it.optString(0), by = it.optString(1);
                    if (!url.contains("googleusercontent.com")) continue;
                    if (seen(key(url))) { oldest(url, by); continue; }
                    q.put(new org.json.JSONArray().put(url).put(by == null || by.equals("null") || by.isEmpty() ? "Google ambient" : "Photo by " + by));
                }
            }
            if (q.length() == 0 && fallbackUrl != null) {
                // Everything Google offered right now was shown in the last ~20 days: reuse the one shown longest ago.
                q.put(new org.json.JSONArray().put(fallbackUrl).put(fallbackCredit));
            }
            fallbackUrl = null; fallbackRank = Integer.MAX_VALUE;
            if (q.length() == 0) return null;
            org.json.JSONArray it = q.getJSONArray(0);
            org.json.JSONArray rest = new org.json.JSONArray();
            for (int i = 1; i < q.length(); i++) rest.put(q.get(i));
            p.edit().putString("live_q", rest.toString()).apply();
            return new String[]{it.getString(0), it.getString(1)};
        } catch (Exception e) { return null; }
    }

    String fallbackUrl, fallbackCredit;
    int fallbackRank = Integer.MAX_VALUE;

    /** Remembers the already-shown picture that was shown longest ago, as a last resort. */
    void oldest(String url, String by) {
        int i = 0, rank = -1; String h = Integer.toHexString(key(url).hashCode());
        for (String x : seenSet()) { if (x.equals(h)) { rank = i; break; } i++; }
        if (rank >= 0 && rank < fallbackRank) {
            fallbackRank = rank; fallbackUrl = url;
            fallbackCredit = by == null || by.equals("null") || by.isEmpty() ? "Google ambient" : "Photo by " + by;
        }
    }

    /** A masterpiece from Google Arts & Culture that hasn't been shown yet. */
    String[] nextArt() {
        try {
            File cache = file("live-art.json");
            if (!cache.exists() || System.currentTimeMillis() - cache.lastModified() > 24 * 3600_000L) {
                String s = Weather.get(ART_FEED, null);
                new org.json.JSONArray(s); // valid?
                try (java.io.FileOutputStream o = new java.io.FileOutputStream(cache)) { o.write(s.getBytes("UTF-8")); }
            }
            byte[] b = new byte[(int) cache.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(cache)) { int off = 0; for (int k; off < b.length && (k = in.read(b, off, b.length - off)) > 0; ) off += k; }
            org.json.JSONArray a = new org.json.JSONArray(new String(b, "UTF-8"));
            int n = a.length(), start = new java.util.Random().nextInt(Math.max(1, n));
            for (int i = 0; i < n; i++) {
                org.json.JSONObject o = a.getJSONObject((start + i) % n);
                String url = o.optString("image");
                if (url.isEmpty() || seen(key(url))) continue;
                String credit = o.optString("title") + "  ·  " + o.optString("creator") + "  ·  " + o.optString("attribution");
                return new String[]{url, credit.replaceAll("(  ·  )+$", "")};
            }
            // Every artwork has been shown: start the round again.
            forgetArt(a);
            return null;
        } catch (Exception e) { return null; }
    }

    // ---- memory of what has been shown

    static String key(String url) { int i = url.indexOf('='); return i > 0 ? url.substring(0, i) : url; }

    java.util.LinkedHashSet<String> seenSet() {
        java.util.LinkedHashSet<String> s = new java.util.LinkedHashSet<>();
        try { org.json.JSONArray a = new org.json.JSONArray(p.getString("live_seen", "[]")); for (int i = 0; i < a.length(); i++) s.add(a.getString(i)); }
        catch (Exception ignored) { }
        return s;
    }

    boolean seen(String k) { return seenSet().contains(Integer.toHexString(k.hashCode())); }

    void markSeen(String k) {
        java.util.LinkedHashSet<String> s = seenSet();
        String h = Integer.toHexString(k.hashCode());
        s.remove(h); // a reused picture counts as just shown
        s.add(h);
        org.json.JSONArray a = new org.json.JSONArray();
        int skip = Math.max(0, s.size() - SEEN_MAX), i = 0;
        for (String x : s) if (i++ >= skip) a.put(x);
        p.edit().putString("live_seen", a.toString()).apply();
    }

    void forgetArt(org.json.JSONArray art) {
        java.util.LinkedHashSet<String> s = seenSet();
        try { for (int i = 0; i < art.length(); i++) s.remove(Integer.toHexString(key(art.getJSONObject(i).optString("image")).hashCode())); }
        catch (Exception ignored) { }
        org.json.JSONArray a = new org.json.JSONArray();
        for (String x : s) a.put(x);
        p.edit().putString("live_seen", a.toString()).apply();
    }

    /** Only the current and next pictures stay on the TV; the old 20-photo gallery is removed. */
    void cleanup() {
        Pic c = current(), n = Pic.from(p.getString("live_next", null));
        File[] fs = dir.listFiles((d, name) -> (name.startsWith("live-") && name.endsWith(".jpg")) || name.startsWith("g-"));
        if (fs != null) for (File f : fs) {
            String nm = f.getName();
            if ((c != null && nm.equals(c.file)) || (n != null && nm.equals(n.file))) continue;
            f.delete();
        }
        p.edit().remove("gallery").apply();
    }
}
