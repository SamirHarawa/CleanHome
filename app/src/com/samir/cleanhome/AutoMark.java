package com.samir.cleanhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * Turns any app's banner or icon into a white CleanFrame-style mark: learn the background from the
 * edge colours (gradients included), keep only what stands out from it as soft white, crop to the logo.
 * Returns null when the art looks like a photo or has no clear logo, so the caller can use a monogram.
 */
final class AutoMark {
    private AutoMark() { }

    static String lastReason = "";

    static Bitmap fromBanner(Drawable d) { return d == null ? null : make(render(d, 320, 180, false)); }

    static Bitmap fromIcon(Drawable d) {
        if (d == null) return null;
        if (d instanceof AdaptiveIconDrawable && ((AdaptiveIconDrawable) d).getForeground() != null) {
            // Adaptive layers are 108 units with a 72-unit visible centre.
            Bitmap full = render(((AdaptiveIconDrawable) d).getForeground(), 288, 288, false);
            return make(Bitmap.createBitmap(full, 48, 48, 192, 192));
        }
        return make(render(d, 192, 192, false));
    }

    private static Bitmap render(Drawable d, int w, int h, boolean unused) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        if (iw > 0 && ih > 0 && Math.abs((float) iw / ih - (float) w / h) > .05f) {
            float s = Math.min((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
        } else {
            d.setBounds(0, 0, w, h);
        }
        d.draw(c);
        return b;
    }

    private static Bitmap make(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int ring = Math.max(2, Math.min(w, h) / 25);
        int[] r = extract(px, w, h, ring, ring, w - 1 - ring, h - 1 - ring, true);
        if (r != null && r[0] == 1) {
            // The art is a solid tile (e.g. a rounded-square icon on transparency):
            // sample the tile's own colours just inside its edge and look for the logo on it.
            int bx0 = r[1], by0 = r[2], bx1 = r[3], by1 = r[4];
            int inset = Math.max(3, Math.min(bx1 - bx0, by1 - by0) / 12);
            r = extract(px, w, h, bx0 + inset, by0 + inset, bx1 - inset, by1 - inset, false);
        }
        if (r == null || r[0] != 0) return null;
        int minX = r[1], minY = r[2], maxX = r[3], maxY = r[4];
        Bitmap all = Bitmap.createBitmap(OUT, w, h, Bitmap.Config.ARGB_8888);
        return Bitmap.createBitmap(all, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    private static int[] OUT;

    /**
     * Samples background colours along the rectangle (x0,y0)-(x1,y1), writes white ink into OUT and
     * returns {status, minX, minY, maxX, maxY}: status 0 = good mark, 1 = solid tile (try again inside it).
     * Returns null when there is no usable mark.
     */
    private static int[] extract(int[] px, int w, int h, int x0, int y0, int x1, int y1, boolean allowFloating) {
        int rw = Math.max(1, x1 - x0), rh = Math.max(1, y1 - y0);
        int perimeter = 2 * (rw + rh);
        int[] bg = new int[96];
        int nbg = 0, edges = 0, clear = 0;
        for (int k = 0; k < 192; k++) {
            int t = k * perimeter / 192, x, y;
            if (t < rw) { x = x0 + t; y = y0; }
            else if (t < rw + rh) { x = x1; y = y0 + t - rw; }
            else if (t < 2 * rw + rh) { x = x1 - (t - rw - rh); y = y1; }
            else { x = x0; y = y1 - (t - 2 * rw - rh); }
            x = Math.max(0, Math.min(w - 1, x)); y = Math.max(0, Math.min(h - 1, y));
            int p = px[y * w + x];
            edges++;
            if ((p >>> 24) < 60) { clear++; continue; }
            if (nbg < bg.length && (k % 2 == 0 || nbg < 48)) bg[nbg++] = p;
        }
        boolean floating = allowFloating && (clear > edges * 0.6f || nbg == 0);
        if (!floating && nbg == 0) { lastReason = "no background"; return null; }

        int[] out = new int[w * h];
        int minX = w, minY = h, maxX = -1, maxY = -1, inkCount = 0;
        for (int i = 0; i < px.length; i++) {
            int x = i % w, y = i / w;
            if (!allowFloating && (x < x0 || x > x1 || y < y0 || y > y1)) continue; // stay inside the tile
            int p = px[i], a = p >>> 24;
            float ink;
            if (a < 30) ink = 0;
            else if (floating) ink = a / 255f;
            else {
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                float best = Float.MAX_VALUE;
                for (int j = 0; j < nbg; j++) {
                    int q = bg[j];
                    float dr = r - ((q >> 16) & 255), dg = g - ((q >> 8) & 255), db = b - (q & 255);
                    float dist = dr * dr * .3f + dg * dg * .59f + db * db * .11f;
                    if (dist < best) best = dist;
                }
                ink = Math.max(0, Math.min(1, ((float) Math.sqrt(best) - 22) / 50f)) * (a / 255f);
            }
            if (ink > 0.02f) {
                out[i] = (Math.round(ink * 255) << 24) | 0xFFFFFF;
                if (ink > .25f) {
                    if (x < minX) minX = x; if (x > maxX) maxX = x;
                    if (y < minY) minY = y; if (y > maxY) maxY = y;
                    inkCount++;
                }
            }
        }
        if (maxX < 0 || inkCount < 40) { lastReason = "empty floating=" + floating; return null; }
        int bw = maxX - minX + 1, bh = maxY - minY + 1;
        float fill = inkCount / (float) (bw * bh);
        float strength = 0; int strong = 0;
        for (int v : out) { int oa = v >>> 24; if (oa > 64) { strength += oa; strong++; } }
        strength = strong == 0 ? 0 : strength / strong / 255f;
        lastReason = "w=" + w + " h=" + h + " bw=" + bw + " bh=" + bh + " fill=" + fill + " strength=" + strength + " floating=" + floating;
        if (floating && fill > .8f) return new int[]{1, minX, minY, maxX, maxY};     // solid tile: look inside
        if (bw > w * .9f && bh > h * .9f && fill > .6f) return null;                  // a photo, not a logo
        if (fill > .92f && bw * bh > w * h * .4f) return null;
        if (strength < .6f) return null;                                               // faint placeholder art
        int pad = 2;
        OUT = out;
        return new int[]{0, Math.max(0, minX - pad), Math.max(0, minY - pad), Math.min(w - 1, maxX + pad), Math.min(h - 1, maxY + pad)};
    }

    /**
     * The app's own brand colour, read from its banner and icon: the hue that carries the most vivid
     * pixels, averaged. White when the art has no clear colour (a monochrome brand). Kept bright
     * enough to read on the frosted dock.
     */
    static int brandColor(Drawable banner, Drawable icon) {
        float[] wsum = new float[24], rs = new float[24], gs = new float[24], bs = new float[24];
        int opaque = 0;
        float[] hsv = new float[3];
        for (Drawable d : new Drawable[]{banner, icon}) {
            if (d == null) continue;
            Bitmap b;
            try { b = render(d, 96, d == banner ? 54 : 96, false); } catch (Exception e) { continue; }
            int[] px = new int[b.getWidth() * b.getHeight()];
            b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
            for (int p : px) {
                if ((p >>> 24) < 128) continue;
                opaque++;
                android.graphics.Color.colorToHSV(p, hsv);
                if (hsv[1] < .35f || hsv[2] < .25f) continue;
                int k = Math.min(23, (int) (hsv[0] / 15f));
                float w = hsv[1] * hsv[2];
                wsum[k] += w; rs[k] += w * ((p >> 16) & 255); gs[k] += w * ((p >> 8) & 255); bs[k] += w * (p & 255);
            }
        }
        int best = -1; float bw = 0, total = 0;
        for (int k = 0; k < 24; k++) {
            float w = wsum[k] + .5f * (wsum[(k + 23) % 24] + wsum[(k + 1) % 24]); // neighbouring hues count too
            total += wsum[k];
            if (w > bw) { bw = w; best = k; }
        }
        if (best < 0 || opaque == 0 || total < opaque * .04f) return 0xFFFFFFFF;
        int c = android.graphics.Color.rgb(Math.round(rs[best] / wsum[best]), Math.round(gs[best] / wsum[best]), Math.round(bs[best] / wsum[best]));
        return readable(c);
    }

    static float lum(int o) { return (.2126f * ((o >> 16) & 255) + .7152f * ((o >> 8) & 255) + .0722f * (o & 255)) / 255f; }

    /** Same hue, lifted just enough to stand out on the dark frosted dock. */
    static int readable(int c) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(c, hsv);
        hsv[2] = Math.max(hsv[2], .85f);
        hsv[1] = Math.min(hsv[1], .95f);
        int o = android.graphics.Color.HSVToColor(hsv);
        // Deep blues, purples and reds look dark even at full value: mix in white until it reads.
        int base = o;
        for (float t = 0; t <= .6f && lum(o) < .42f; t += .04f) {
            o = android.graphics.Color.rgb(Math.round(((base >> 16) & 255) * (1 - t) + 255 * t),
                    Math.round(((base >> 8) & 255) * (1 - t) + 255 * t), Math.round((base & 255) * (1 - t) + 255 * t));
        }
        return o;
    }
}
