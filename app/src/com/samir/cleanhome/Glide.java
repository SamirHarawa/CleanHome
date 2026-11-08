package com.samir.cleanhome;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

/**
 * One focus plate that glides to whichever item is focused inside a container (CleanFrame's .plate),
 * instead of each item lighting up on its own. Fades in on arrival and out when focus leaves.
 */
final class Glide {
    static final PathInterpolator EASE = new PathInterpolator(.2f, .7f, .2f, 1f);
    static final int MOVE = 380, FADE = 170;

    final ViewGroup box;
    final View plate;
    private boolean shown;
    private final Rect r = new Rect();

    /** box is a FrameLayout whose first child is the plate; the items live in later children. */
    Glide(FrameLayout box, View plate) {
        this.box = box;
        this.plate = plate;
        plate.setAlpha(0f);
        plate.setTag("plate");
        box.addView(plate, 0, new FrameLayout.LayoutParams(0, 0));
    }

    void focus(View v, boolean hasFocus) {
        if (!hasFocus) {
            box.post(() -> {
                if (box.findFocus() == null && shown) {
                    shown = false;
                    plate.animate().alpha(0f).scaleX(.97f).scaleY(.97f).setDuration(FADE).setInterpolator(Motion.IN).start();
                }
            });
            return;
        }
        try {
            v.getDrawingRect(r);
            box.offsetDescendantRectToMyCoords(v, r);
        } catch (IllegalArgumentException e) { return; }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) plate.getLayoutParams();
        if (lp.width != r.width() || lp.height != r.height()) {
            lp.width = r.width();
            lp.height = r.height();
            plate.setLayoutParams(lp);
        }
        plate.animate().cancel();
        if (!shown) {
            shown = true;
            plate.setTranslationX(r.left);
            plate.setTranslationY(r.top);
            plate.setScaleX(.9f);
            plate.setScaleY(.9f);
            plate.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(360).setInterpolator(Motion.SPRING).start();
        } else {
            // Glides on a soft spring, so it eases into the new tile instead of stopping dead.
            plate.animate().translationX(r.left).translationY(r.top).alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(MOVE).setInterpolator(Motion.SPRING).start();
        }
    }

    void reset() {
        shown = false;
        plate.animate().cancel();
        plate.setAlpha(0f);
    }
}
