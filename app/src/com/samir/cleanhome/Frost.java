package com.samir.cleanhome;

import android.graphics.Bitmap;

/**
 * Clean, band-free rendering of the wallpaper and its frosted glass.
 * Everything is computed in floating point and written out with a ±1-step triangular dither,
 * which is below what the eye can see but stops 8-bit gradients from banding. No visible grain.
 */
final class Frost {
    private Frost() { }

    /** CleanFrame's overlays in stage px: .shade (top 300px) and .scrim (bottom 520px), colour rgb(4,10,12). */
    static float overlayAlpha(float y) {
        if (y < 150) return .32f - (.32f - .14f) * (y / 150f);
        if (y < 300) return .14f * (1 - (y - 150) / 150f);
        if (y < 560) return 0;
        float t = Math.min(1, (y - 560) / 520f);
        return t < .7f ? .42f * (t / .7f) : .42f + .10f * ((t - .7f) / .3f);
    }

    static final float OR = 4, OG = 10, OB = 12;

    private static int seed = 0x2545F491;
    private static float rnd() { // xorshift, [0,1)
        seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5;
        return (seed >>> 8) / 16777216f;
    }
    private static int q(float v) {
        v += rnd() - rnd(); // triangular dither, ±1 step
        int i = Math.round(v);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    /** Mean luminance (0..1) of a screen-space rectangle of the low-res photo. */
    static float lum(Small s, float x0, float y0, float x1, float y1) {
        double sum = 0; int n = 0;
        for (int y = 0; y < s.h; y++) {
            float Y = s.dy + (y + .5f) * s.f;
            if (Y < y0 || Y > y1) continue;
            for (int x = 0; x < s.w; x++) {
                float X = s.dx + (x + .5f) * s.f;
                if (X < x0 || X > x1) continue;
                int i = y * s.w + x;
                sum += (.2126f * s.r[i] + .7152f * s.g[i] + .0722f * s.b[i]) / 255f;
                n++;
            }
        }
        return n == 0 ? 0 : (float) (sum / n);
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Soft elliptical dims (stage px) placed behind text/icons when the photo there is bright. */
    static final class Dim {
        final float cx, cy, rx, ry, a;
        Dim(float cx, float cy, float rx, float ry, float a) { this.cx = cx; this.cy = cy; this.rx = rx; this.ry = ry; this.a = a; }
        float at(float x, float y) {
            if (a <= 0) return 0;
            float dx = (x - cx) / rx, dy = (y - cy) / ry;
            float t = (float) Math.sqrt(dx * dx + dy * dy);
            return t >= 1 ? 0 : a * (1 - smooth(0, 1, t));
        }
    }

    static Bitmap compose(Bitmap wall, int sw, int sh, float stage) { return compose(wall, sw, sh, stage, new Dim[0]); }

    /** The photo centre-cropped to the screen with the shade, scrim and any adaptive dims baked in. */
    static Bitmap compose(Bitmap wall, int sw, int sh, float stage, Dim[] dims) {
        int bw = wall.getWidth(), bh = wall.getHeight();
        float scale = Math.max(sw / (float) bw, sh / (float) bh);
        int cw = Math.min(bw, Math.round(sw / scale)), ch = Math.min(bh, Math.round(sh / scale));
        Bitmap crop = Bitmap.createBitmap(wall, (bw - cw) / 2, (bh - ch) / 2, cw, ch);
        Bitmap scaled = (cw == sw && ch == sh) ? crop : Bitmap.createScaledBitmap(crop, sw, sh, true);
        int[] px = new int[sw * sh];
        scaled.getPixels(px, 0, sw, 0, 0, sw, sh);
        for (int y = 0; y < sh; y++) {
            float sy = y / stage;
            float base = overlayAlpha(sy);
            boolean anyDim = false;
            for (Dim d : dims) if (d.a > 0 && Math.abs(sy - d.cy) < d.ry) anyDim = true;
            if (base <= 0 && !anyDim) continue;
            for (int x = 0, i = y * sw; x < sw; x++, i++) {
                float a = base;
                if (anyDim) {
                    float sx = x / stage;
                    for (Dim d : dims) { float da = d.at(sx, sy); if (da > 0) a = 1 - (1 - a) * (1 - da); }
                }
                if (a <= 0) continue;
                float k = 1 - a;
                int p = px[i];
                px[i] = 0xFF000000 | (q(((p >> 16) & 255) * k + OR * a) << 16) | (q(((p >> 8) & 255) * k + OG * a) << 8) | q((p & 255) * k + OB * a);
            }
        }
        return Bitmap.createBitmap(px, sw, sh, Bitmap.Config.ARGB_8888);
    }

    /** The on-screen photo at low resolution, as float RGB, ready to blur. */
    static final class Small {
        int w, h;
        float f, dx, dy; // screen px per small px, and the centre-crop offset
        float[] r, g, b;
    }

    static Small small(Bitmap wall, int sw, int sh) {
        int bw = wall.getWidth(), bh = wall.getHeight();
        float scale = Math.max(sw / (float) bw, sh / (float) bh);
        Bitmap s = wall;
        while (s.getWidth() > 260) s = Bitmap.createScaledBitmap(s, Math.max(1, s.getWidth() / 2), Math.max(1, s.getHeight() / 2), true);
        Small o = new Small();
        o.w = s.getWidth();
        o.h = s.getHeight();
        o.f = (bw / (float) o.w) * scale;
        o.dx = (sw - bw * scale) / 2f;
        o.dy = (sh - bh * scale) / 2f;
        int[] px = new int[o.w * o.h];
        s.getPixels(px, 0, o.w, 0, 0, o.w, o.h);
        o.r = new float[px.length]; o.g = new float[px.length]; o.b = new float[px.length];
        for (int i = 0; i < px.length; i++) {
            o.r[i] = (px[i] >> 16) & 255; o.g[i] = (px[i] >> 8) & 255; o.b[i] = px[i] & 255;
        }
        return o;
    }

    /** Gaussian-like blur (three box passes) of a copy, sigma in screen px. */
    static Small blurred(Small s, float sigmaScreen) {
        Small o = new Small();
        o.w = s.w; o.h = s.h; o.f = s.f; o.dx = s.dx; o.dy = s.dy;
        o.r = s.r.clone(); o.g = s.g.clone(); o.b = s.b.clone();
        float sigma = sigmaScreen / s.f;
        int r = Math.max(1, Math.round(((float) Math.sqrt(12 * sigma * sigma / 3 + 1) - 1) / 2));
        float[] tmp = new float[Math.max(s.w, s.h)];
        for (float[] c : new float[][]{o.r, o.g, o.b})
            for (int pass = 0; pass < 3; pass++) { boxH(c, o.w, o.h, r, tmp); boxV(c, o.w, o.h, r, tmp); }
        return o;
    }

    private static void boxH(float[] c, int w, int h, int r, float[] tmp) {
        float n = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += c[row + clamp(k, w)];
            for (int x = 0; x < w; x++) {
                tmp[x] = sum / n;
                sum += c[row + clamp(x + r + 1, w)] - c[row + clamp(x - r, w)];
            }
            System.arraycopy(tmp, 0, c, row, w);
        }
    }

    private static void boxV(float[] c, int w, int h, int r, float[] tmp) {
        float n = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += c[clamp(k, h) * w + x];
            for (int y = 0; y < h; y++) {
                tmp[y] = sum / n;
                sum += c[clamp(y + r + 1, h) * w + x] - c[clamp(y - r, h) * w + x];
            }
            for (int y = 0; y < h; y++) c[y * w + x] = tmp[y];
        }
    }

    private static int clamp(int v, int n) { return v < 0 ? 0 : (v >= n ? n - 1 : v); }

    /**
     * A screen-space rectangle of the blurred photo, upsampled smoothly, optionally with the
     * bottom scrim baked in (CleanFrame's .mat-scrim), dithered to 8-bit.
     */
    static Bitmap region(Small s, int x0, int y0, int w, int h, float stage, boolean scrim) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int Y = y0 + y;
            float v = (Y + .5f - s.dy) / s.f - .5f;
            v = Math.max(0, Math.min(s.h - 1.001f, v));
            int vi = (int) v;
            float fv = v - vi;
            int r0 = vi * s.w, r1 = Math.min(s.h - 1, vi + 1) * s.w;
            float a = scrim ? overlayAlpha(Y / stage) : 0, k = 1 - a;
            for (int x = 0; x < w; x++) {
                float u = (x0 + x + .5f - s.dx) / s.f - .5f;
                u = Math.max(0, Math.min(s.w - 1.001f, u));
                int ui = (int) u, u1 = Math.min(s.w - 1, ui + 1);
                float fu = u - ui;
                float w00 = (1 - fu) * (1 - fv), w10 = fu * (1 - fv), w01 = (1 - fu) * fv, w11 = fu * fv;
                float R = s.r[r0 + ui] * w00 + s.r[r0 + u1] * w10 + s.r[r1 + ui] * w01 + s.r[r1 + u1] * w11;
                float G = s.g[r0 + ui] * w00 + s.g[r0 + u1] * w10 + s.g[r1 + ui] * w01 + s.g[r1 + u1] * w11;
                float B = s.b[r0 + ui] * w00 + s.b[r0 + u1] * w10 + s.b[r1 + ui] * w01 + s.b[r1 + u1] * w11;
                out[y * w + x] = 0xFF000000 | (q(R * k + OR * a) << 16) | (q(G * k + OG * a) << 8) | q(B * k + OB * a);
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
    }
}
