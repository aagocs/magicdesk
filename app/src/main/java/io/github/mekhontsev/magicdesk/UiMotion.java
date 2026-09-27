package io.github.mekhontsev.magicdesk;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import java.util.WeakHashMap;

/** Native presentation effects. Dismissal and focus never await animation completion. */
final class UiMotion {
    private static final WeakHashMap<View, ShellAppearance> ACTIVE = new WeakHashMap<>();
    static void reveal(View view, boolean panel) {
        cancel(view);
        var theme = AppearanceStore.current(view.getContext());
        var motion = theme.motion();
        int duration = motion.duration(panel ? motion.panels() : motion.taskbar(), ValueAnimator.areAnimatorsEnabled());
        if (duration == 0) return;
        ACTIVE.put(view, theme);
        View.OnAttachStateChangeListener lifetime = new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View v) { }
            public void onViewDetachedFromWindow(View v) { cancel(v); }
        };
        view.addOnAttachStateChangeListener(lifetime);
        view.setAlpha(.35f);
        view.animate().alpha(1f).setDuration(duration).setInterpolator(switch (motion.curve()) {
            case LINEAR -> new android.view.animation.LinearInterpolator();
            case EASE_OUT -> new android.view.animation.DecelerateInterpolator();
            case SMOOTH -> new android.view.animation.AccelerateDecelerateInterpolator();
        }).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                ACTIVE.remove(view); view.removeOnAttachStateChangeListener(lifetime);
                view.setAlpha(1f); view.animate().setListener(null);
            }
        }).start();
    }
    static void cancel(View view) {
        if (ACTIVE.remove(view) != null) { view.animate().cancel(); view.setAlpha(1f); }
    }
    static void refresh() {
        for (View view : new java.util.ArrayList<>(ACTIVE.keySet())) {
            if (!AppearanceStore.current(view.getContext()).equals(ACTIVE.get(view))) cancel(view);
        }
    }
}
