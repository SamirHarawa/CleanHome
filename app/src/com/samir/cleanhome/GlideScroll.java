package com.samir.cleanhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.ScrollView;

/**
 * A vertical scroller that glides like tvOS: the focused row settles at a fixed height on screen and the
 * content eases underneath it, instead of ScrollView's quick jump when focus reaches the edge.
 * Focus itself moves by the normal focus search; this view only follows it.
 */
final class GlideScroll extends ScrollView {
    /** Long, soft ease-out: quick to respond, slow to settle. */
    static final PathInterpolator GLIDE = new PathInterpolator(.22f, .9f, .26f, 1f);
    static final int DURATION = 560;

    /** Where the top of the focused item settles, in px from the top of this view. */
    int anchor;
    private ValueAnimator anim;
    private int target = -1;
    private final Rect r = new Rect();

    GlideScroll(Context c) { super(c); }

    /** ScrollView would jump to keep focus on screen; this view scrolls only through follow(). */
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) { return 0; }

    /** Leave arrow keys to the normal focus search, so ScrollView never page-jumps on its own. */
    @Override public boolean executeKeyEvent(KeyEvent e) { return false; }

    /** Glide so that v's top sits at the anchor (clamped to the content). */
    void follow(View v) {
        if (getChildCount() == 0) return;
        try {
            v.getDrawingRect(r);
            offsetDescendantRectToMyCoords(v, r);
        } catch (IllegalArgumentException e) { return; }
        int max = Math.max(0, getChildAt(0).getHeight() + getPaddingTop() + getPaddingBottom() - getHeight());
        // r is in content coordinates (offsetDescendantRectToMyCoords ignores this view's own scroll).
        int to = Math.max(0, Math.min(max, r.top - anchor));
        if (to == (anim != null && anim.isRunning() ? target : getScrollY())) return;
        target = to;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofInt(getScrollY(), to);
        anim.setDuration(DURATION);
        anim.setInterpolator(GLIDE);
        anim.addUpdateListener(a -> scrollTo(0, (Integer) a.getAnimatedValue()));
        anim.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
