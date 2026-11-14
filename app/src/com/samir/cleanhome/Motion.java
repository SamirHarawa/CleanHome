package com.samir.cleanhome;

import android.animation.TimeInterpolator;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/** One motion language for the whole Home: soft springs for focus, eased curves for everything else. */
final class Motion {
    private Motion() { }

    /** Arrivals: quick start, long gentle settle (emphasised decelerate). */
    static final PathInterpolator OUT = new PathInterpolator(.05f, .7f, .1f, 1f);
    /** Departures: eases away and leaves quickly. */
    static final PathInterpolator IN = new PathInterpolator(.3f, 0f, .8f, .15f);
    /** Long crossfades. */
    static final PathInterpolator INOUT = new PathInterpolator(.45f, 0f, .25f, 1f);

    /** A critically-soft spring: settles with a barely-there 2-3% overshoot, so focus feels alive, not bouncy. */
    static final TimeInterpolator SPRING = new Spring(.74f, 8.4f);

    static final class Spring implements TimeInterpolator {
        final float z, w, wd, end;
        Spring(float zeta, float omega) {
            z = zeta; w = omega; wd = (float) (omega * Math.sqrt(1 - zeta * zeta));
            end = raw(1f);
        }
        float raw(float t) {
            return (float) (1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + (z * w / wd) * Math.sin(wd * t)));
        }
        @Override public float getInterpolation(float t) { return t >= 1 ? 1 : raw(t) / end; }
    }

    /** The shared focus plate behind a tile, if any (Glide puts it first in the tile's container). */
    static View plateFor(View tile) {
        android.view.ViewParent p = tile.getParent();
        while (p instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) p;
            if (g.getChildCount() > 0 && "plate".equals(g.getChildAt(0).getTag())) return g.getChildAt(0);
            p = g.getParent();
        }
        return null;
    }

    /** OK pressed on a tile: it sinks in a touch; released, it springs back. */
    static void press(View tile, boolean down) {
        View plate = plateFor(tile);
        for (View v : new View[]{tile, plate}) {
            if (v == null) continue;
            if (down) v.animate().scaleX(.94f).scaleY(.94f).setDuration(110).setInterpolator(OUT).start();
            else v.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(SPRING).start();
        }
    }

    /** Views that are rising right now, so a new rise (or anything else) can finish the old one cleanly. */
    static final java.util.WeakHashMap<View, android.animation.ValueAnimator> RISING = new java.util.WeakHashMap<>();

    /**
     * Fade and rise into place after a delay. Its own animator, not the view's animate(): other effects
     * (a nudge, a press) cancel animate() freely, and must never leave a view stuck half-faded. However it
     * stops, the view ends fully visible and in place.
     */
    static void rise(View v, float dy, long delay, long dur) {
        android.animation.ValueAnimator old = RISING.remove(v);
        if (old != null) old.cancel();
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dy);
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setStartDelay(delay);
        a.setDuration(dur);
        a.setInterpolator(OUT);
        a.addUpdateListener(new RiseStep(v, dy));
        a.addListener(new RiseEnd(v));
        RISING.put(v, a);
        a.start();
    }

    /** Ends any rise on v now, leaving it fully shown. */
    static void settle(View v) {
        android.animation.ValueAnimator a = RISING.remove(v);
        if (a != null) a.end();
    }

    static final class RiseStep implements android.animation.ValueAnimator.AnimatorUpdateListener {
        final View v; final float dy;
        RiseStep(View v, float dy) { this.v = v; this.dy = dy; }
        @Override public void onAnimationUpdate(android.animation.ValueAnimator a) {
            float p = (Float) a.getAnimatedValue();
            v.setAlpha(p);
            v.setTranslationY(dy * (1 - p));
        }
    }

    static final class RiseEnd extends android.animation.AnimatorListenerAdapter {
        final View v;
        RiseEnd(View v) { this.v = v; }
        @Override public void onAnimationStart(android.animation.Animator a) {
            // A cached layer while it fades: drawn once, not re-blended every frame. Set, not built here:
            // the render thread builds it with the next frame instead of the main thread stalling for it.
            // Not for a tile whose card can grow past its bounds (a focused card): a layer is cut to the
            // tile's rectangle, which squares off the card's rounded corners and hides its shadow.
            if (v instanceof ViewGroup && !((ViewGroup) v).getClipChildren()) return;
            v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        @Override public void onAnimationEnd(android.animation.Animator a) { done(a); }
        @Override public void onAnimationCancel(android.animation.Animator a) { done(a); }
        void done(android.animation.Animator a) {
            if (RISING.get(v) == a) RISING.remove(v);
            v.setAlpha(1f);
            v.setTranslationY(0f);
            v.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }
}
