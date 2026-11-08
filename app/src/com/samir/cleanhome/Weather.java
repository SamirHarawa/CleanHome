package com.samir.cleanhome;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Current weather for the Home screen. Location comes from the internet connection (the TV has no GPS
 * and no Wi-Fi fix), refreshed daily; weather comes from Open-Meteo (free, no account), refreshed every
 * 30 minutes. Everything is cached so the widget shows instantly after a restart, even offline.
 */
final class Weather {
    private Weather() { }

    static final long LOC_EVERY = 24 * 3600_000L, WX_EVERY = 30 * 60_000L;

    /** Cached reading, or null. */
    static final class Now {
        float temp, hi, lo;
        int code;
        boolean day;
        String city;
        long at;
    }

    static Now cached(SharedPreferences p) {
        if (!p.contains("wx_at")) return null;
        Now n = new Now();
        n.temp = p.getFloat("wx_temp", 0); n.hi = p.getFloat("wx_hi", 0); n.lo = p.getFloat("wx_lo", 0);
        n.code = p.getInt("wx_code", 0); n.day = p.getBoolean("wx_day", true);
        n.city = p.getString("wx_city", ""); n.at = p.getLong("wx_at", 0);
        // A reading older than 6 hours is not "now" any more.
        return System.currentTimeMillis() - n.at > 6 * 3600_000L ? null : n;
    }

    static boolean due(SharedPreferences p) { return System.currentTimeMillis() - p.getLong("wx_tried", 0) > WX_EVERY; }

    /** Blocking: refresh location if stale, then the weather. Returns the new reading or null. Call off the UI thread. */
    static Now fetch(SharedPreferences p) {
        p.edit().putLong("wx_tried", System.currentTimeMillis()).apply();
        try {
            long now = System.currentTimeMillis();
            if (now - p.getLong("loc_at", 0) > LOC_EVERY || !p.contains("loc_lat")) {
                double[] ll = null; String city = null;
                try {
                    org.json.JSONObject j = new org.json.JSONObject(get("https://ipwho.is/"));
                    if (j.optBoolean("success")) { ll = new double[]{j.getDouble("latitude"), j.getDouble("longitude")}; city = j.optString("city"); }
                } catch (Exception ignored) { }
                if (ll == null) {
                    try {
                        org.json.JSONObject j = new org.json.JSONObject(get("https://ipapi.co/json/"));
                        if (j.has("latitude") && !j.isNull("latitude")) { ll = new double[]{j.getDouble("latitude"), j.getDouble("longitude")}; city = j.optString("city"); }
                    } catch (Exception ignored) { }
                }
                if (ll != null) p.edit().putFloat("loc_lat", (float) ll[0]).putFloat("loc_lon", (float) ll[1])
                        .putString("wx_city", city == null ? "" : city).putLong("loc_at", now).apply();
            }
            if (!p.contains("loc_lat")) return null;
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + p.getFloat("loc_lat", 0) + "&longitude=" + p.getFloat("loc_lon", 0)
                    + "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1";
            org.json.JSONObject j = new org.json.JSONObject(get(url));
            org.json.JSONObject c = j.getJSONObject("current"), d = j.getJSONObject("daily");
            Now n = new Now();
            n.temp = (float) c.getDouble("temperature_2m");
            n.code = c.getInt("weather_code");
            n.day = c.optInt("is_day", 1) == 1;
            n.hi = (float) d.getJSONArray("temperature_2m_max").getDouble(0);
            n.lo = (float) d.getJSONArray("temperature_2m_min").getDouble(0);
            n.city = p.getString("wx_city", "");
            n.at = System.currentTimeMillis();
            p.edit().putFloat("wx_temp", n.temp).putFloat("wx_hi", n.hi).putFloat("wx_lo", n.lo).putInt("wx_code", n.code)
                    .putBoolean("wx_day", n.day).putLong("wx_at", n.at).apply();
            return n;
        } catch (Exception e) { return null; }
    }

    static String get(String url) throws Exception { return get(url, null); }

    /** Saves a URL to a file (used for live wallpapers). */
    static void download(String url, java.io.File to) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        try (java.io.InputStream in = c.getInputStream(); java.io.FileOutputStream out = new java.io.FileOutputStream(to)) {
            if (c.getResponseCode() != 200) throw new java.io.IOException("HTTP " + c.getResponseCode());
            byte[] buf = new byte[32768];
            for (int k; (k = in.read(buf)) > 0; ) out.write(buf, 0, k);
        } finally { c.disconnect(); }
    }

    static String get(String url, String userAgent) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setRequestProperty("User-Agent", userAgent != null ? userAgent : "CleanHome/1.0 (Android TV launcher)");
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        try (java.io.InputStream in = c.getInputStream()) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int k; (k = in.read(buf)) > 0; ) b.write(buf, 0, k);
            return b.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    // ---- WMO weather codes

    static final int CLEAR = 0, PARTLY = 1, CLOUD = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, SNOW = 6, STORM = 7;

    static int kind(int code) {
        if (code == 0) return CLEAR;
        if (code == 1 || code == 2) return PARTLY;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        return CLOUD;
    }

    static String label(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mainly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: case 77: return "Snow";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorms";
            case 96: case 99: return "Storms with hail";
            default: return "Cloudy";
        }
    }

    /** Colour weather icons: a warm gradient sun, soft shaded clouds, blue rain, icy snow, a pale moon. */
    static final class Icon extends Drawable {
        final int kind; final boolean day;
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
        int alpha = 255;

        Icon(int kind, boolean day, int unusedColor) {
            this.kind = kind; this.day = day;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public void draw(Canvas c) {
            float u = getBounds().width() / 24f;
            c.save();
            c.translate(getBounds().left, getBounds().top);
            c.scale(u, u);
            fill.setAlpha(alpha); line.setAlpha(alpha);
            boolean dark = kind == RAIN || kind == STORM;
            switch (kind) {
                case CLEAR: if (day) sun(c, 12, 12, 5f); else moon(c, 12, 12, 7f); break;
                case PARTLY: {
                    c.save();
                    c.clipOutPath(cloudPath(13.6f, 15.2f, 1.18f));
                    if (day) sun(c, 8.6f, 8.6f, 3.9f); else moon(c, 9.2f, 8.6f, 5.2f);
                    c.restore();
                    cloud(c, 13.6f, 15.2f, 1f, false); break;
                }
                case CLOUD:
                    cloud(c, 9.5f, 10.5f, .75f, true);   // a second cloud behind, a shade darker, for depth
                    cloud(c, 13f, 14f, 1.05f, false); break;
                case FOG:
                    cloud(c, 12, 10, 1f, false);
                    line.setStrokeWidth(1.6f); line.setColor(0xFFC9D1DB); line.setAlpha(alpha);
                    c.drawLine(4.5f, 18.5f, 19.5f, 18.5f, line); c.drawLine(6.5f, 21.5f, 17.5f, 21.5f, line); break;
                case DRIZZLE: cloud(c, 12, 10, 1f, false); drops(c, 0xFF7CC6FF, 2.2f, 2); break;
                case RAIN: cloud(c, 12, 10, 1f, true); drops(c, 0xFF4FA8FF, 3.6f, 3); break;
                case SNOW: {
                    cloud(c, 12, 10, 1f, false);
                    fill.setShader(null); fill.setColor(0xFFE3F2FF); fill.setAlpha(alpha);
                    c.drawCircle(7.5f, 19f, 1.3f, fill); c.drawCircle(12f, 21.2f, 1.3f, fill); c.drawCircle(16.5f, 19f, 1.3f, fill);
                    break;
                }
                case STORM: {
                    cloud(c, 12, 9.5f, 1f, true);
                    Path b = new Path();
                    b.moveTo(13.6f, 13.6f); b.lineTo(9.4f, 19.2f); b.lineTo(12.2f, 19.2f); b.lineTo(10.6f, 23.4f);
                    b.lineTo(15.4f, 17.2f); b.lineTo(12.6f, 17.2f); b.close();
                    fill.setShader(null); fill.setColor(0xFFFFD23F); fill.setAlpha(alpha);
                    c.drawPath(b, fill);
                    break;
                }
            }
            c.restore();
        }

        void sun(Canvas c, float x, float y, float r) {
            // Rays first, then a glowing gradient disc.
            line.setShader(null);
            line.setColor(0xFFFFC23D); line.setAlpha(alpha);
            line.setStrokeWidth(1.7f);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i + Math.PI / 8;
                float cx = (float) Math.cos(a), sy = (float) Math.sin(a);
                c.drawLine(x + cx * (r + 1.8f), y + sy * (r + 1.8f), x + cx * (r + 3.6f), y + sy * (r + 3.6f), line);
            }
            fill.setShader(new android.graphics.RadialGradient(x - r * .35f, y - r * .35f, r * 1.4f,
                    0xFFFFEC8A, 0xFFFFA81F, android.graphics.Shader.TileMode.CLAMP));
            fill.setAlpha(alpha);
            c.drawCircle(x, y, r, fill);
            fill.setShader(null);
        }

        void moon(Canvas c, float x, float y, float r) {
            Path m = new Path(), cut = new Path();
            m.addCircle(x, y, r, Path.Direction.CW);
            cut.addCircle(x + r * .55f, y - r * .45f, r * .85f, Path.Direction.CW);
            m.op(cut, Path.Op.DIFFERENCE);
            fill.setShader(new android.graphics.LinearGradient(x - r, y - r, x + r, y + r, 0xFFFFF4C9, 0xFFE2CE86,
                    android.graphics.Shader.TileMode.CLAMP));
            fill.setAlpha(alpha);
            c.drawPath(m, fill);
            fill.setShader(null);
        }

        /** A soft, shaded cloud; grey ones carry rain and storms. */
        void cloud(Canvas c, float x, float y, float s, boolean grey) {
            Path p = cloudPath(x, y, s);
            int top = grey ? 0xFFDCE2EA : 0xFFFFFFFF, bottom = grey ? 0xFF8E9AAB : 0xFFC9D3DF;
            fill.setShader(new android.graphics.LinearGradient(0, y - 6.5f * s, 0, y + 4.4f * s, top, bottom,
                    android.graphics.Shader.TileMode.CLAMP));
            fill.setAlpha(alpha);
            c.drawPath(p, fill);
            fill.setShader(null);
        }

        /** A cloud outline centred on (x, y). */
        static Path cloudPath(float x, float y, float s) {
            Path p = new Path();
            p.addCircle(x - 3.6f * s, y + .8f * s, 3.4f * s, Path.Direction.CW);
            p.addCircle(x + .6f * s, y - 1.6f * s, 4.8f * s, Path.Direction.CW);
            p.addCircle(x + 4.6f * s, y + 1.2f * s, 3.2f * s, Path.Direction.CW);
            Path base = new Path();
            base.addRoundRect(new RectF(x - 7f * s, y + .2f * s, x + 7.8f * s, y + 4.4f * s), 2.2f * s, 2.2f * s, Path.Direction.CW);
            p.op(base, Path.Op.UNION);
            return p;
        }

        void drops(Canvas c, int color, float len, int n) {
            line.setShader(null);
            line.setColor(color); line.setAlpha(alpha);
            line.setStrokeWidth(1.8f);
            float[] xs = n == 2 ? new float[]{9.5f, 14.5f} : new float[]{7.5f, 12, 16.5f};
            for (float x : xs) c.drawLine(x, 17.4f, x - 1.3f, 17.4f + len, line);
        }

        @Override public void setAlpha(int a) { alpha = a; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); line.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
