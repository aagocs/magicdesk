package io.github.mekhontsev.magicdesk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Rubber-band selection geometry for the desktop surface, independent of Android views.
 *
 * <p>Every update recomputes the selection from the current rectangle, so shrinking the
 * rectangle deselects items. Only the visible part of the viewport can select, and items
 * that merely touch the rectangle's edge are not selected.
 */
final class DesktopMarqueeSelection {
    /** How the rectangle combines with the selection that existed when the drag began. */
    enum Mode {
        /** The rectangle alone is the selection. */
        REPLACE,
        /** Shift: covered items are added to the base. */
        ADD,
        /** Ctrl: covered items are inverted against the base, as in Windows Explorer. */
        TOGGLE;

        static Mode forModifiers(final boolean ctrl, final boolean shift) {
            return ctrl ? TOGGLE : shift ? ADD : REPLACE;
        }
    }

    record Bounds(int left, int top, int right, int bottom) {
        boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        boolean overlaps(final Bounds other) {
            return !isEmpty() && !other.isEmpty()
                    && left < other.right && right > other.left
                    && top < other.bottom && bottom > other.top;
        }
    }

    record Item(String id, Bounds bounds) {
        Item {
            Objects.requireNonNull(id);
            Objects.requireNonNull(bounds);
        }
    }

    private boolean mArmed;
    private boolean mActive;
    private Mode mMode = Mode.REPLACE;
    private int mSlop;
    private float mStartX;
    private float mStartY;
    private float mX;
    private float mY;
    private Bounds mViewport;
    private final Set<String> mBase = new LinkedHashSet<>();

    /** Starts a gesture; nothing is selected until the pointer leaves {@code slop} pixels. */
    void begin(
            final float x,
            final float y,
            final Mode mode,
            final Collection<String> baseSelection,
            final int slop) {
        reset();
        mArmed = true;
        mStartX = x;
        mStartY = y;
        mX = x;
        mY = y;
        mMode = Objects.requireNonNull(mode);
        mSlop = Math.max(0, slop);
        if (baseSelection != null) {
            mBase.addAll(baseSelection);
        }
    }

    /**
     * Returns the full selection for the current pointer position, ordered as {@code items}
     * (a kept base item that is not rendered follows), or {@code null} while the gesture is
     * not armed or the pointer has not yet left the slop.
     */
    List<String> update(
            final float x,
            final float y,
            final Bounds viewport,
            final List<Item> items) {
        if (!mArmed) {
            return null;
        }
        mX = x;
        mY = y;
        mViewport = viewport;
        if (!mActive) {
            if (Math.abs(x - mStartX) < mSlop && Math.abs(y - mStartY) < mSlop) {
                return null;
            }
            mActive = true;
        }
        final Bounds rect = currentRect();
        final Set<String> selected = new LinkedHashSet<>();
        final Set<String> rendered = new LinkedHashSet<>();
        for (final Item item : items) {
            rendered.add(item.id());
            final boolean hit = rect != null && rect.overlaps(item.bounds());
            final boolean inBase = mBase.contains(item.id());
            if (mMode == Mode.TOGGLE ? hit != inBase : hit || mMode == Mode.ADD && inBase) {
                selected.add(item.id());
            }
        }
        final List<String> result = new ArrayList<>(selected);
        if (mMode != Mode.REPLACE) {
            for (final String id : mBase) {
                if (!rendered.contains(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    /** The normalized rectangle clipped to the viewport, or {@code null} when inactive or empty. */
    Bounds currentRect() {
        if (!mActive || mViewport == null) {
            return null;
        }
        final Bounds clipped = new Bounds(
                Math.max(mViewport.left(), (int) Math.floor(Math.min(mStartX, mX))),
                Math.max(mViewport.top(), (int) Math.floor(Math.min(mStartY, mY))),
                Math.min(mViewport.right(), (int) Math.ceil(Math.max(mStartX, mX))),
                Math.min(mViewport.bottom(), (int) Math.ceil(Math.max(mStartY, mY))));
        return clipped.isEmpty() ? null : clipped;
    }

    boolean isActive() {
        return mActive;
    }

    /** Ends the gesture, keeping the last selection; returns whether the pointer dragged. */
    boolean end() {
        final boolean dragged = mActive;
        reset();
        return dragged;
    }

    /** Ends the gesture and returns the selection to restore. */
    List<String> cancel() {
        final List<String> base = new ArrayList<>(mBase);
        reset();
        return base;
    }

    private void reset() {
        mArmed = false;
        mActive = false;
        mMode = Mode.REPLACE;
        mViewport = null;
        mBase.clear();
    }
}
