package io.github.mekhontsev.magicdesk;

import android.app.Activity;
import android.graphics.Insets;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;

/** Fits the complete modal content, after Android's decor has applied its own insets. */
final class DialogContentInsets implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnPreDrawListener, ViewTreeObserver.OnGlobalLayoutListener {
    private final int types = WindowInsets.Type.systemBars()
            | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime();
    private final View content;
    private final View owner;
    private final int left, top, right, bottom;
    private final int[] position = new int[2];
    private final int[] parentPosition = new int[2];
    private Insets own = Insets.NONE;
    private ViewTreeObserver contentObserver;
    private ViewTreeObserver ownerObserver;

    static void bind(View content, Activity owner) {
        new DialogContentInsets(content, owner.getWindow().getDecorView());
    }

    private DialogContentInsets(View content, View owner) {
        this.content = content;
        this.owner = owner;
        left = content.getPaddingLeft(); top = content.getPaddingTop();
        right = content.getPaddingRight(); bottom = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((view, insets) -> {
            own = insets.getInsets(types);
            content.requestLayout();
            // This host fits title, body and buttons; children must not add the same sources again.
            return new WindowInsets.Builder(insets).setInsets(types, Insets.NONE).build();
        });
        content.addOnAttachStateChangeListener(this);
        if (content.isAttachedToWindow()) onViewAttachedToWindow(content);
    }

    @Override public void onViewAttachedToWindow(View view) {
        contentObserver = content.getViewTreeObserver();
        ownerObserver = owner.getViewTreeObserver();
        contentObserver.addOnPreDrawListener(this);
        ownerObserver.addOnGlobalLayoutListener(this);
        content.requestApplyInsets();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        if (contentObserver.isAlive()) contentObserver.removeOnPreDrawListener(this);
        if (ownerObserver.isAlive()) ownerObserver.removeOnGlobalLayoutListener(this);
        contentObserver = null;
        ownerObserver = null;
    }

    @Override public void onGlobalLayout() {
        // The owner's caption can change without resizing the modal window.
        content.requestLayout();
    }

    @Override public boolean onPreDraw() {
        Insets safe = own;
        WindowInsets published = owner.getRootWindowInsets();
        if (published != null && owner.isAttachedToWindow()) {
            Insets caption = published.getInsets(WindowInsets.Type.captionBar());
            owner.getLocationOnScreen(parentPosition);
            content.getLocationOnScreen(position);
            safe = Insets.max(safe, Insets.of(
                    leading(parentPosition[0], caption.left, position[0], content.getWidth()),
                    leading(parentPosition[1], caption.top, position[1], content.getHeight()),
                    trailing(parentPosition[0] + owner.getWidth(), caption.right, position[0], content.getWidth()),
                    trailing(parentPosition[1] + owner.getHeight(), caption.bottom, position[1], content.getHeight())));
        }
        if (content.getPaddingLeft() == left + safe.left && content.getPaddingTop() == top + safe.top
                && content.getPaddingRight() == right + safe.right && content.getPaddingBottom() == bottom + safe.bottom) {
            return true;
        }
        content.setPadding(left + safe.left, top + safe.top, right + safe.right, bottom + safe.bottom);
        // Layout barrier, not a timed wait: draw only after the inset-adjusted measurement.
        return false;
    }

    static int leading(int parentStart, int inset, int childStart, int size) {
        return inset == 0 ? 0 : Math.min(size, Math.max(0, parentStart + inset - childStart));
    }

    static int trailing(int parentEnd, int inset, int childStart, int size) {
        return inset == 0 ? 0 : Math.min(size, Math.max(0, childStart + size - parentEnd + inset));
    }
}
