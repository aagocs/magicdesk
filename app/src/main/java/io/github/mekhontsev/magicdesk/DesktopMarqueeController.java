package io.github.mekhontsev.magicdesk;

import android.graphics.drawable.GradientDrawable;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Android adapter for desktop rubber-band selection. It only observes the touch sequence that
 * the wallpaper parent already receives for empty desktop space; it never consumes events, so
 * item drag and drop, the grid drop target, taps and the context menu keep their own paths.
 *
 * <p>The gesture starts only with the primary mouse button alone, on a point no grid child
 * covers. The activation slop is the system touch slop, so a drag the parent's tap detector
 * rejects is exactly one that starts the marquee.
 */
final class DesktopMarqueeController {
    interface Host {
        /** The desktop grid, or {@code null} while it is not attached. */
        DesktopGridLayout grid();

        boolean isSelectableItem(String itemId);

        List<String> selectedItemIds();

        /** Applies the selection and refreshes highlights without rebuilding grid children. */
        void setSelection(List<String> itemIds);

        int touchSlop();
    }

    private final Host mHost;
    private final DesktopMarqueeSelection mMarquee = new DesktopMarqueeSelection();
    private final int[] mOrigin = new int[2];
    private List<String> mOriginalSelection = List.of();
    private List<String> mLastApplied;
    private GradientDrawable mOverlay;
    private DesktopGridLayout mOverlayGrid;
    private boolean mTracking;

    DesktopMarqueeController(final Host host) {
        mHost = host;
    }

    void onTouch(final MotionEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                begin(event);
                break;
            case MotionEvent.ACTION_MOVE:
                move(event);
                break;
            case MotionEvent.ACTION_UP:
                finish(false);
                break;
            case MotionEvent.ACTION_CANCEL:
                finish(true);
                break;
            default:
                break;
        }
    }

    /** Drops any gesture state, for example when the grid is destroyed. */
    void reset() {
        mTracking = false;
        mMarquee.cancel();
        removeOverlay();
    }

    private void begin(final MotionEvent event) {
        reset();
        final DesktopGridLayout grid = mHost.grid();
        if (grid == null || !grid.isShown() || !isPrimaryMouseDrag(event)) {
            return;
        }
        final float x = localX(grid, event);
        final float y = localY(grid, event);
        if (x < 0 || y < 0 || x >= grid.getWidth() || y >= grid.getHeight()
                || coversAnyChild(grid, x, y)) {
            return;
        }
        final int modifiers = KeyEvent.normalizeMetaState(event.getMetaState());
        final boolean additive =
                (modifiers & (KeyEvent.META_CTRL_ON | KeyEvent.META_SHIFT_ON)) != 0;
        mOriginalSelection = new ArrayList<>(mHost.selectedItemIds());
        mLastApplied = null;
        mMarquee.begin(x, y, additive, mOriginalSelection, mHost.touchSlop());
        mTracking = true;
    }

    private void move(final MotionEvent event) {
        final DesktopGridLayout grid = mHost.grid();
        if (!mTracking || grid == null) {
            return;
        }
        final DesktopMarqueeSelection.Bounds viewport =
                new DesktopMarqueeSelection.Bounds(0, 0, grid.getWidth(), grid.getHeight());
        final List<String> selection = mMarquee.update(
                localX(grid, event), localY(grid, event), viewport, selectableItems(grid));
        if (selection == null) {
            return;
        }
        if (!selection.equals(mLastApplied)) {
            mLastApplied = selection;
            mHost.setSelection(selection);
        }
        showOverlay(grid, mMarquee.currentRect());
    }

    private void finish(final boolean cancelled) {
        if (!mTracking) {
            return;
        }
        final boolean dragged = mMarquee.isActive();
        mTracking = false;
        if (cancelled) {
            mMarquee.cancel();
            if (dragged) {
                mHost.setSelection(mOriginalSelection);
            }
        } else {
            mMarquee.end();
        }
        removeOverlay();
    }

    private static boolean isPrimaryMouseDrag(final MotionEvent event) {
        final int buttons = event.getButtonState();
        return event.isFromSource(InputDevice.SOURCE_MOUSE)
                && (buttons & MotionEvent.BUTTON_PRIMARY) != 0
                && (buttons & ~MotionEvent.BUTTON_PRIMARY) == 0;
    }

    private float localX(final View grid, final MotionEvent event) {
        grid.getLocationOnScreen(mOrigin);
        return event.getRawX() - mOrigin[0];
    }

    private float localY(final View grid, final MotionEvent event) {
        grid.getLocationOnScreen(mOrigin);
        return event.getRawY() - mOrigin[1];
    }

    private static boolean coversAnyChild(
            final ViewGroup grid, final float x, final float y) {
        for (int index = 0; index < grid.getChildCount(); index++) {
            final View child = grid.getChildAt(index);
            if (child.getVisibility() == View.VISIBLE
                    && x >= child.getLeft() && x < child.getRight()
                    && y >= child.getTop() && y < child.getBottom()) {
                return true;
            }
        }
        return false;
    }

    private List<DesktopMarqueeSelection.Item> selectableItems(final ViewGroup grid) {
        final List<DesktopMarqueeSelection.Item> items = new ArrayList<>();
        for (int index = 0; index < grid.getChildCount(); index++) {
            final View child = grid.getChildAt(index);
            final ViewGroup.LayoutParams raw = child.getLayoutParams();
            if (child.getVisibility() != View.VISIBLE
                    || !(raw instanceof DesktopGridLayout.LayoutParams)) {
                continue;
            }
            final String itemId = ((DesktopGridLayout.LayoutParams) raw).itemId;
            if (mHost.isSelectableItem(itemId)) {
                items.add(new DesktopMarqueeSelection.Item(
                        itemId,
                        new DesktopMarqueeSelection.Bounds(
                                child.getLeft(), child.getTop(),
                                child.getRight(), child.getBottom())));
            }
        }
        return items;
    }

    private void showOverlay(
            final DesktopGridLayout grid, final DesktopMarqueeSelection.Bounds rect) {
        if (rect == null) {
            removeOverlay();
            return;
        }
        if (mOverlay == null || mOverlayGrid != grid) {
            removeOverlay();
            final int accent = UiAppearance.color(grid.getContext(), UiColor.ACCENT);
            mOverlay = new GradientDrawable();
            mOverlay.setColor((accent & 0x00FFFFFF) | 0x33000000);
            mOverlay.setStroke(
                    Math.max(1, Math.round(grid.getResources().getDisplayMetrics().density)),
                    accent);
            mOverlayGrid = grid;
            grid.getOverlay().add(mOverlay);
        }
        mOverlay.setBounds(rect.left(), rect.top(), rect.right(), rect.bottom());
    }

    private void removeOverlay() {
        if (mOverlay != null && mOverlayGrid != null) {
            mOverlayGrid.getOverlay().remove(mOverlay);
        }
        mOverlay = null;
        mOverlayGrid = null;
    }
}
