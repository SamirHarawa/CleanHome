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

    /** Fade and rise into place after a delay. */
    static void rise(View v, float dy, long delay, long dur) {
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationY(dy);
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(dur).setInterpolator(OUT).start();
    }
}
