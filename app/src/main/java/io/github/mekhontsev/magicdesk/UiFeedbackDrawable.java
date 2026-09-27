package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** One retained drawable per native control; state resolution allocates no theme objects. */
final class UiFeedbackDrawable extends StateListDrawable {
    private final GradientDrawable[] mPaints = new GradientDrawable[6];
    private final float mDensity, mRadius;
    private final AppearanceScopeSource mSource;
    private ShellMotion mMotion;
    UiFeedbackDrawable(Context context, float density, float radius) {
        mSource = new AppearanceScopeSource(context);
        mDensity = density; mRadius = radius;
        int[][] states = {{-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
                {android.R.attr.state_focused}, {android.R.attr.state_selected}, {android.R.attr.state_hovered}, {}};
        for (int i = 0; i < states.length; i++) { mPaints[i] = new GradientDrawable(); addState(states[i], mPaints[i]); }
        refresh();
    }
    void refresh() {
        var theme = mSource.current(); var f = theme.feedback();
        mMotion = theme.motion();
        UiColor[] fills = {f.disabled(), f.pressed(), f.focused(), f.selected(), f.hover(), f.normal()};
        for (int i = 0; i < fills.length; i++) {
            mPaints[i].setColor(theme.palette().color(fills[i]));
            mPaints[i].setCornerRadius(mRadius * theme.shape().radiusScale());
            mPaints[i].setStroke(i == 2 || i == 3 ? Math.round(mDensity * theme.shape().borderDp()) : 0,
                    theme.palette().color(f.outline()));
        }
        updateMotion(); invalidateSelf();
    }
    private void updateMotion() {
        int duration = mMotion == null || mMotion.reduced() || !android.animation.ValueAnimator.areAnimatorsEnabled()
                ? 0 : mMotion.feedbackMs();
        setEnterFadeDuration(duration); setExitFadeDuration(duration);
    }
    @Override protected boolean onStateChange(int[] states) {
        updateMotion(); return super.onStateChange(states);
    }
}
