package io.github.mekhontsev.magicdesk;

import android.app.Activity;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

/** A child dialog may not receive its task's caption source. Fit only the actual overlap. */
final class DialogContentInsets {
    static void bind(View root, Activity owner) {
        final int left = root.getPaddingLeft(), top = root.getPaddingTop();
        final int right = root.getPaddingRight(), bottom = root.getPaddingBottom();
        final int[] position = new int[2];
        final Runnable update = () -> {
            if (root.getWidth() == 0 || root.getHeight() == 0) return;
            WindowInsets own = root.getRootWindowInsets();
            Insets safe = own == null ? Insets.NONE : own.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            WindowInsets parent = owner.getWindow().getDecorView().getRootWindowInsets();
            if (parent != null) {
                var bounds = owner.getWindowManager().getCurrentWindowMetrics().getBounds();
                Insets caption = parent.getInsets(WindowInsets.Type.captionBar());
                root.getLocationOnScreen(position);
                safe = Insets.max(safe, Insets.of(
                        leading(bounds.left, caption.left, position[0], root.getWidth()),
                        leading(bounds.top, caption.top, position[1], root.getHeight()),
                        trailing(bounds.right, caption.right, position[0], root.getWidth()),
                        trailing(bounds.bottom, caption.bottom, position[1], root.getHeight())));
            }
            if (root.getPaddingLeft() != left + safe.left || root.getPaddingTop() != top + safe.top
                    || root.getPaddingRight() != right + safe.right || root.getPaddingBottom() != bottom + safe.bottom) {
                root.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
            }
        };
        root.setOnApplyWindowInsetsListener((view, insets) -> { update.run(); return insets; });
        root.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> update.run());
        root.requestApplyInsets();
        update.run();
    }

    static int leading(int parentStart, int inset, int childStart, int size) {
        return inset == 0 ? 0 : Math.min(size, Math.max(0, parentStart + inset - childStart));
    }

    static int trailing(int parentEnd, int inset, int childStart, int size) {
        return inset == 0 ? 0 : Math.min(size, Math.max(0, childStart + size - parentEnd + inset));
    }
}
