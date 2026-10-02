package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public final class DesktopMarqueeSelectionTest {
    private static final int SLOP = 4;
    private static final DesktopMarqueeSelection.Bounds VIEWPORT =
            new DesktopMarqueeSelection.Bounds(0, 0, 400, 300);
    // Three icons in a row: a [10,10,110,110], b [120,10,220,110], c [230,10,330,110].
    private static final List<DesktopMarqueeSelection.Item> ITEMS = List.of(
            item("a", 10, 10, 110, 110),
            item("b", 120, 10, 220, 110),
            item("c", 230, 10, 330, 110));

    private static DesktopMarqueeSelection.Item item(
            final String id, final int l, final int t, final int r, final int b) {
        return new DesktopMarqueeSelection.Item(id, new DesktopMarqueeSelection.Bounds(l, t, r, b));
    }

    private static DesktopMarqueeSelection begin(
            final float x, final float y, final boolean additive, final Set<String> base) {
        return begin(x, y, additive ? DesktopMarqueeSelection.Mode.ADD
                : DesktopMarqueeSelection.Mode.REPLACE, base);
    }

    private static DesktopMarqueeSelection begin(final float x, final float y,
            final DesktopMarqueeSelection.Mode mode, final Set<String> base) {
        final DesktopMarqueeSelection marquee = new DesktopMarqueeSelection();
        marquee.begin(x, y, mode, base, SLOP);
        return marquee;
    }

    @Test
    public void staysInactiveUntilThePointerLeavesTheSlop() {
        final DesktopMarqueeSelection marquee = begin(50, 50, false, Set.of());
        assertNull(marquee.update(52, 52, VIEWPORT, ITEMS));
        assertFalse(marquee.isActive());
        assertNull(marquee.currentRect());
        assertEquals(List.of("a"), marquee.update(54, 60, VIEWPORT, ITEMS));
        assertTrue(marquee.isActive());
        // Once active, moving back inside the slop keeps the marquee active.
        assertEquals(List.of("a"), marquee.update(51, 60, VIEWPORT, ITEMS));
    }

    @Test
    public void selectsEveryIntersectingItemAndOnlyThose() {
        final DesktopMarqueeSelection marquee = begin(100, 50, false, Set.of());
        // Partial overlap with a (to x=110) and b (from x=120), none with c.
        assertEquals(List.of("a", "b"), marquee.update(125, 60, VIEWPORT, ITEMS));
        assertEquals(List.of("a", "b", "c"), marquee.update(300, 60, VIEWPORT, ITEMS));
    }

    @Test
    public void edgeTouchingAndZeroAreaItemsAreNotSelected() {
        final DesktopMarqueeSelection marquee = begin(110, 50, false, Set.of());
        // The rectangle starts exactly at a's right edge and ends at b's left edge.
        assertEquals(List.of(), marquee.update(120, 60, VIEWPORT, ITEMS));
        final List<DesktopMarqueeSelection.Item> withEmpty = List.of(
                item("a", 10, 10, 110, 110), item("empty", 60, 60, 60, 90));
        final DesktopMarqueeSelection second = begin(0, 0, false, Set.of());
        assertEquals(List.of("a"), second.update(200, 200, VIEWPORT, withEmpty));
    }

    @Test
    public void reversedDragSelectsTheSameItemsAsForward() {
        final DesktopMarqueeSelection forward = begin(15, 15, false, Set.of());
        final DesktopMarqueeSelection reverse = begin(240, 90, false, Set.of());
        assertEquals(forward.update(240, 90, VIEWPORT, ITEMS),
                reverse.update(15, 15, VIEWPORT, ITEMS));
        assertEquals(List.of("a", "b", "c"), reverse.update(15, 15, VIEWPORT, ITEMS));
    }

    @Test
    public void shrinkingTheRectangleDeselectsItems() {
        final DesktopMarqueeSelection marquee = begin(15, 15, false, Set.of());
        assertEquals(List.of("a", "b", "c"), marquee.update(240, 90, VIEWPORT, ITEMS));
        assertEquals(List.of("a"), marquee.update(60, 90, VIEWPORT, ITEMS));
    }

    @Test
    public void replaceModeDropsThePreviousSelection() {
        final DesktopMarqueeSelection marquee = begin(15, 15, false, Set.of("c", "hidden"));
        assertEquals(List.of("a"), marquee.update(60, 60, VIEWPORT, ITEMS));
    }

    @Test
    public void additiveModeKeepsTheBaseAndAddsHitsInItemOrder() {
        // Start below the icons so the rectangle can shrink back to nothing.
        final DesktopMarqueeSelection marquee = begin(15, 150, true,
                new java.util.LinkedHashSet<>(List.of("c", "hidden")));
        // Hits a; c stays selected from the base; "hidden" is not rendered but is retained last.
        assertEquals(List.of("a", "c", "hidden"), marquee.update(60, 60, VIEWPORT, ITEMS));
        // Shrinking back to nothing keeps only the base.
        assertEquals(List.of("c", "hidden"), marquee.update(60, 200, VIEWPORT, ITEMS));
    }

    @Test
    public void viewportClipsTheRectangleAndPartiallyClippedItems() {
        final DesktopMarqueeSelection.Bounds narrow = new DesktopMarqueeSelection.Bounds(0, 0, 100, 300);
        final DesktopMarqueeSelection marquee = begin(5, 5, false, Set.of());
        // b starts at x=120, beyond the visible viewport, so it cannot be hit.
        assertEquals(List.of("a"), marquee.update(300, 200, narrow, ITEMS));
        // Starting outside the viewport still clips cleanly.
        final DesktopMarqueeSelection outside = begin(-50, -50, false, Set.of());
        assertEquals(List.of("a"), outside.update(60, 60, narrow, ITEMS));
        // Entirely outside: nothing selected.
        final DesktopMarqueeSelection none = begin(500, 500, false, Set.of());
        assertEquals(List.of(), none.update(600, 600, VIEWPORT, ITEMS));
    }

    @Test
    public void resizeRecomputesAgainstTheNewViewportAndItems() {
        final DesktopMarqueeSelection marquee = begin(15, 15, false, Set.of());
        assertEquals(List.of("a", "b", "c"), marquee.update(300, 90, VIEWPORT, ITEMS));
        final DesktopMarqueeSelection.Bounds shrunk = new DesktopMarqueeSelection.Bounds(0, 0, 200, 300);
        assertEquals(List.of("a", "b"), marquee.update(300, 90, shrunk, ITEMS));
        // An item that disappeared from the live list is no longer selected.
        assertEquals(List.of("a"), marquee.update(300, 90, VIEWPORT, ITEMS.subList(0, 1)));
    }

    @Test
    public void cancelRestoresTheBaseSelectionAndEndsTheGesture() {
        final DesktopMarqueeSelection marquee = begin(15, 15, false,
                new java.util.LinkedHashSet<>(List.of("c")));
        assertEquals(List.of("a"), marquee.update(60, 60, VIEWPORT, ITEMS));
        assertEquals(List.of("c"), marquee.cancel());
        assertFalse(marquee.isActive());
        assertNull(marquee.currentRect());
        assertNull(marquee.update(300, 90, VIEWPORT, ITEMS));
    }

    @Test
    public void endKeepsTheLastSelectionAndReportsWhetherItDragged() {
        final DesktopMarqueeSelection moved = begin(15, 15, false, Set.of());
        moved.update(60, 60, VIEWPORT, ITEMS);
        assertTrue(moved.end());
        assertFalse(moved.isActive());
        final DesktopMarqueeSelection click = begin(15, 15, false, Set.of());
        click.update(16, 16, VIEWPORT, ITEMS);
        assertFalse(click.end());
    }

    @Test
    public void currentRectIsNormalizedAndClipped() {
        final DesktopMarqueeSelection marquee = begin(300, 250, false, Set.of());
        marquee.update(-20, 100, VIEWPORT, ITEMS);
        final DesktopMarqueeSelection.Bounds rect = marquee.currentRect();
        assertEquals(new DesktopMarqueeSelection.Bounds(0, 100, 300, 250), rect);
    }
    @Test
    public void toggleModeInvertsCoveredItemsAndKeepsTheRestOfTheBase() {
        final DesktopMarqueeSelection marquee = begin(15, 15,
                DesktopMarqueeSelection.Mode.TOGGLE, Set.of("a", "c", "hidden"));
        // Covering a and b: a was selected and is deselected, b is added, c is untouched.
        assertEquals(List.of("b", "c", "hidden"), marquee.update(215, 105, VIEWPORT, ITEMS));
    }

    @Test
    public void shrinkingAToggleRectangleRestoresUncoveredItems() {
        final DesktopMarqueeSelection marquee = begin(15, 15,
                DesktopMarqueeSelection.Mode.TOGGLE, Set.of("a"));
        assertEquals(List.of("b", "c"), marquee.update(325, 105, VIEWPORT, ITEMS));
        assertEquals(List.of("b"), marquee.update(215, 105, VIEWPORT, ITEMS));
        assertEquals(List.of(), marquee.update(100, 105, VIEWPORT, ITEMS));
    }

    @Test
    public void cancellingAToggleRestoresTheBase() {
        final DesktopMarqueeSelection marquee = begin(15, 15,
                DesktopMarqueeSelection.Mode.TOGGLE, Set.of("a"));
        marquee.update(215, 105, VIEWPORT, ITEMS);
        assertEquals(List.of("a"), marquee.cancel());
    }

    @Test
    public void ctrlTogglesShiftAddsAndCtrlWinsWhenBothAreHeld() {
        assertEquals(DesktopMarqueeSelection.Mode.REPLACE,
                DesktopMarqueeSelection.Mode.forModifiers(false, false));
        assertEquals(DesktopMarqueeSelection.Mode.ADD,
                DesktopMarqueeSelection.Mode.forModifiers(false, true));
        assertEquals(DesktopMarqueeSelection.Mode.TOGGLE,
                DesktopMarqueeSelection.Mode.forModifiers(true, false));
        assertEquals(DesktopMarqueeSelection.Mode.TOGGLE,
                DesktopMarqueeSelection.Mode.forModifiers(true, true));
    }
}
