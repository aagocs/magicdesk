package io.github.mekhontsev.magicdesk;

/** Tracks the slop threshold for a gesture that began on empty desktop space. */
final class DesktopMarqueeGestureTracker {
    enum Transition {
        NONE,
        STARTED,
        UPDATED,
        FINISHED,
        CANCELLED
    }

    private boolean mTracking;
    private boolean mActive;
    private float mStartX;
    private float mStartY;

    Transition down(final float x, final float y, final boolean eligible) {
        reset();
        if (!eligible) {
            return Transition.NONE;
        }
        mTracking = true;
        mStartX = x;
        mStartY = y;
        return Transition.NONE;
    }

    Transition move(final float x, final float y, final float touchSlop) {
        if (!mTracking) {
            return Transition.NONE;
        }
        if (mActive) {
            return Transition.UPDATED;
        }
        final float dx = x - mStartX;
        final float dy = y - mStartY;
        final float slop = Math.max(0, touchSlop);
        if (dx * dx + dy * dy < slop * slop) {
            return Transition.NONE;
        }
        mActive = true;
        return Transition.STARTED;
    }

    Transition up() {
        if (!mActive) {
            reset();
            return Transition.NONE;
        }
        reset();
        return Transition.FINISHED;
    }

    Transition cancel() {
        if (!mActive) {
            reset();
            return Transition.NONE;
        }
        reset();
        return Transition.CANCELLED;
    }

    boolean isTracking() {
        return mTracking;
    }

    boolean isActive() {
        return mActive;
    }

    float startX() {
        return mStartX;
    }

    float startY() {
        return mStartY;
    }

    private void reset() {
        mTracking = false;
        mActive = false;
        mStartX = 0;
        mStartY = 0;
    }
}
