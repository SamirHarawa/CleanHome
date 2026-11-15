package com.samir.cleanhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Deep glass, in the spirit of Apple's newer design: the photo behind bends at the rounded rim like the edge of
 * a lens (with a whisper of colour fringing), colours read richer through it, and the rim catches the light.
 */
final class Glass {
    private Glass() { }

    /** Colours seen through glass are a touch richer than the photo itself. */
    static ColorFilter vibrancy(float sat) {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(sat);
        return new ColorMatrixColorFilter(m);
    }

    static final String LENS =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float radius;\n" +
            "uniform float band;\n" +
            "uniform float sat;\n" +
            "float sd(float2 p, float2 b, float r) {\n" +
            "  float2 q = abs(p) - b + r;\n" +
            "  return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;\n" +
            "}\n" +
            "half4 main(float2 xy) {\n" +
            "  float2 c = size * 0.5;\n" +
            "  float2 p = xy - c;\n" +
            "  float d = sd(p, c, radius);\n" +
            // 0 in the flat middle of the glass, 1 at the very rim.
            "  float t = clamp(1.0 + d / band, 0.0, 1.0);\n" +
            "  t = t * t * t;\n" +
            "  float e = 1.5;\n" +
            "  float2 n = float2(sd(p + float2(e, 0.0), c, radius) - sd(p - float2(e, 0.0), c, radius),\n" +
            "                    sd(p + float2(0.0, e), c, radius) - sd(p - float2(0.0, e), c, radius));\n" +
            "  n = n / max(length(n), 0.0001);\n" +
            // Near the rim the glass looks inward: what is behind the edge appears stretched round it.
            "  float2 off = -n * band * 0.85 * t;\n" +
            "  half4 col;\n" +
            "  col.r = content.eval(xy + off * 1.08).r;\n" +
            "  col.g = content.eval(xy + off).g;\n" +
            "  col.b = content.eval(xy + off * 0.92).b;\n" +
            "  col.a = content.eval(xy).a;\n" +
            "  half l = dot(col.rgb, half3(0.2126, 0.7152, 0.0722));\n" +
            "  col.rgb = mix(half3(l), col.rgb, half(sat));\n" +
            // The thick rim gathers a little light.
            "  col.rgb = col.rgb + half3(0.07) * half(t);\n" +
            "  return col;\n" +
            "}\n";

    /**
     * The lens: rim refraction, colour fringing and vibrancy, on a view showing the photo behind a glass shape
     * of the view's own size. Android 13+; on older systems it falls back to vibrancy alone.
     */
    static void lens(View v, float radius, float band, float sat) {
        if (android.os.Build.VERSION.SDK_INT < 33) {
            if (v instanceof android.widget.ImageView) ((android.widget.ImageView) v).setColorFilter(vibrancy(sat));
            return;
        }
        android.graphics.RuntimeShader s = new android.graphics.RuntimeShader(LENS);
        s.setFloatUniform("radius", radius);
        s.setFloatUniform("band", band);
        s.setFloatUniform("sat", sat);
        Runnable apply = () -> {
            if (v.getWidth() == 0 || v.getHeight() == 0) return;
            s.setFloatUniform("size", v.getWidth(), v.getHeight());
            v.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content"));
        };
        v.addOnLayoutChangeListener((x, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) apply.run();
        });
        apply.run();
    }

    /**
     * The rim of a piece of glass: a hairline that catches the light at the top left and, more softly, at the
     * bottom right; a sheen just inside the top edge; and a faint shade just inside the bottom, for thickness.
     */
    static final class Rim extends Drawable {
        final float r, strength;
        final boolean depth;
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG),
                shade = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        final RectF b = new RectF(), in = new RectF();
        final Path clip = new Path();

        /** r: corner radius; strength: 0..1 of the hairline; depth: also draw the inner sheen and shade. */
        Rim(float r, float strength, boolean depth) { this.r = r; this.strength = strength; this.depth = depth; }

        float r() { return Math.min(r, b.height() / 2f); }

        static int white(float a) { return Color.argb(Math.round(Math.max(0, Math.min(1, a)) * 255), 255, 255, 255); }

        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            b.set(bounds);
            in.set(b);
            in.inset(.75f, .75f);
            float s = strength;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(1.5f);
            // Angles run clockwise from 3 o'clock: brightest round the top left, an echo at the bottom right.
            SweepGradient g = new SweepGradient(b.centerX(), b.centerY(),
                    new int[]{white(.22f * s), white(.50f * s), white(.16f * s), white(.30f * s), white(.95f * s), white(.62f * s), white(.22f * s)},
                    new float[]{0f, .13f, .30f, .48f, .62f, .78f, 1f});
            line.setShader(g);
            if (depth) {
                float hh = b.height();
                sheen.setShader(new LinearGradient(0, b.top, 0, b.top + hh * .34f, white(.10f * s), white(0), Shader.TileMode.CLAMP));
                shade.setShader(new LinearGradient(0, b.bottom - hh * .30f, 0, b.bottom, 0x00000000, Color.argb(Math.round(40 * s), 0, 0, 0), Shader.TileMode.CLAMP));
                clip.reset();
                clip.addRoundRect(b, r(), r(), Path.Direction.CW);
            }
        }

        @Override public void draw(Canvas c) {
            float rr = r();
            if (depth) {
                c.save();
                c.clipPath(clip);
                c.drawRect(b.left, b.top, b.right, b.top + b.height() * .34f, sheen);
                c.drawRect(b.left, b.bottom - b.height() * .30f, b.right, b.bottom, shade);
                c.restore();
            }
            c.drawRoundRect(in, rr, rr, line);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
