package com.samir.cleanhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

/**
 * Card art for the Apps grid. Uses an app's own Android TV banner when it is a real one; otherwise
 * (no banner, a square icon passed off as a banner, or the blank template banner) it builds a
 * 16:9 card from the app's icon, sized so every logo sits comfortably inside the card.
 */
final class BannerArt {
    private BannerArt() { }

    /** True if the banner is worth showing: wide like a banner, and not a blank or colourless template. */
    static boolean usable(Drawable banner) {
        if (banner == null) return false;
        int w = banner.getIntrinsicWidth(), h = banner.getIntrinsicHeight();
        if (w <= 0 || h <= 0 || w < h * 1.4f) return false; // a square icon standing in for a banner
        Bitmap b = render(banner, 64, 36);
        int n = 64 * 36;
        float[] lum = new float[n];
        float maxSat = 0, opaque = 0;
        float[] hsv = new float[3];
        for (int y = 0, i = 0; y < 36; y++) for (int x = 0; x < 64; x++, i++) {
            int c = b.getPixel(x, y);
            if (Color.alpha(c) > 128) opaque++;
            Color.colorToHSV(c, hsv);
            if (hsv[2] > .15f) maxSat = Math.max(maxSat, hsv[1]);
            lum[i] = (.2126f * Color.red(c) + .7152f * Color.green(c) + .0722f * Color.blue(c)) / 255f;
        }
        b.recycle();
        if (opaque < n * .6f) return false; // mostly see-through: a logo, not a banner
        java.util.Arrays.sort(lum);
        float contrast = lum[(int) (n * .97f)] - lum[(int) (n * .03f)];
        // Grey and flat (e.g. the keyline-grid template): nothing an eye can latch onto.
        return !(maxSat < .12f && contrast < .3f);
    }

    /**
     * A w x h banner built from the app's icon: the logo above the app's name, centred, like the banners
     * TV apps ship. brand tints the card when the icon has no backdrop of its own.
     */
    static Bitmap fromIcon(Drawable icon, int brand, String name, Typeface face, int w, int h) {
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        if (icon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) icon;
            Drawable bg = a.getBackground(), fg = a.getForeground();
            if (bg instanceof ColorDrawable) c.drawColor(((ColorDrawable) bg).getColor());
            else if (bg != null) {
                // Cover the card with the backdrop layer, cropped top and bottom.
                bg.setBounds(0, (h - w) / 2, w, (h + w) / 2);
                bg.draw(c);
            } else c.drawColor(backdrop(brand));
            if (fg != null) {
                // The layer is 108 units with the logo inside the middle ~66: make that ~56% of the card height.
                int s = Math.round(h * .56f * 108f / 66f), cy = Math.round(h * ICON_CY);
                fg.setBounds((w - s) / 2, cy - s / 2, (w + s) / 2, cy + s / 2);
                fg.draw(c);
            }
            label(c, out, name, face, w, h);
            return out;
        }
        // A plain icon: put it on its own edge colour if it has a solid backdrop, else on a tinted card.
        int s = Math.round(h * .48f);
        Bitmap ib = render(icon, s, s);
        int edge = edgeColor(ib);
        boolean solid = Color.alpha(edge) > 230;
        c.drawColor(solid ? edge : backdrop(brand));
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        float l = (w - s) / 2f, t = h * ICON_CY - s / 2f;
        int save = c.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(l, t, l + s, t + s), s * .22f, s * .22f, Path.Direction.CW);
        c.clipPath(clip);
        c.drawBitmap(ib, l, t, p);
        c.restoreToCount(save);
        ib.recycle();
        label(c, out, name, face, w, h);
        return out;
    }

    /** Vertical centre of the logo, as a fraction of the card height; the name sits below it. */
    static final float ICON_CY = .39f;

    /** The app's name, centred under the logo, in white or near-black, whichever reads on the card.
     *  Drawn in the system font (Roboto), like the banners TV apps ship. */
    static void label(Canvas c, Bitmap card, String name, Typeface face, int w, int h) {
        if (name == null || name.isEmpty()) return;
        float base = h * .86f, size = h * .158f;
        // Pick the ink from the card behind the text.
        long lum = 0, n = 0;
        for (int y = Math.round(base - size); y < Math.min(h, Math.round(base + size * .3f)); y += 2)
            for (int x = w / 8; x < w * 7 / 8; x += 3) {
                int p = card.getPixel(x, y);
                lum += Math.round(.2126f * Color.red(p) + .7152f * Color.green(p) + .0722f * Color.blue(p));
                n++;
            }
        boolean light = n > 0 && lum / n > 150;
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        tp.setTypeface(face);
        tp.setColor(light ? 0xFF1C1C1E : Color.WHITE);
        tp.setTextSize(size);
        tp.setLetterSpacing(-.01f);
        float max = w * .86f;
        // Long names shrink a little before they are cut short.
        while (tp.measureText(name) > max && tp.getTextSize() > h * .105f) tp.setTextSize(tp.getTextSize() - 1);
        String text = String.valueOf(TextUtils.ellipsize(name, tp, max, TextUtils.TruncateAt.END));
        c.drawText(text, (w - tp.measureText(text)) / 2f, base, tp);
    }

    /** A dark card faintly tinted with the brand colour, like glass over it. */
    static int backdrop(int brand) {
        int base = 0xFF26272B;
        if (brand == Color.WHITE || brand == 0) return base;
        float k = .28f;
        return Color.rgb(Math.round(Color.red(base) * (1 - k) + Color.red(brand) * k),
                Math.round(Color.green(base) * (1 - k) + Color.green(brand) * k),
                Math.round(Color.blue(base) * (1 - k) + Color.blue(brand) * k));
    }

    /** Average colour around the border of an icon (transparent if the border is see-through). */
    static int edgeColor(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        long r = 0, g = 0, bl = 0, a = 0, n = 0;
        int in = Math.max(1, w / 16);
        for (int x = 0; x < w; x += 2) for (int y : new int[]{in, h - 1 - in}) {
            int c = b.getPixel(x, y); r += Color.red(c); g += Color.green(c); bl += Color.blue(c); a += Color.alpha(c); n++;
        }
        for (int y = 0; y < h; y += 2) for (int x : new int[]{in, w - 1 - in}) {
            int c = b.getPixel(x, y); r += Color.red(c); g += Color.green(c); bl += Color.blue(c); a += Color.alpha(c); n++;
        }
        return Color.argb((int) (a / n), (int) (r / n), (int) (g / n), (int) (bl / n));
    }

    static Bitmap render(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Rect old = d.copyBounds();
        d.setBounds(0, 0, w, h);
        d.draw(c);
        d.setBounds(old);
        return b;
    }
}
