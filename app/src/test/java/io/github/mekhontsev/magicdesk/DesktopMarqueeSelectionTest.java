package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public final class DesktopMarqueeSelectionTest {
    private static final DesktopMarqueeSelection.Rectangle VIEWPORT =
            DesktopMarqueeSelection.Rectangle.bounds(0, 0, 100, 100);

    @Test
    public void zeroAreaMarqueeDoesNotSelect() {
        assertEquals(List.of(), select(
                DesktopMarqueeSelection.Rectangle.normalized(20, 20, 20, 20),
                item("a", 10, 10, 30, 30)));
    }

    @Test
    public void partialIntersectionSelectsItem() {
        assertEquals(List.of("a"), select(
                DesktopMarqueeSelection.Rectangle.normalized(20, 20, 40, 40),
                item("a", 30, 30, 50, 50)));
    }

    @Test
    public void fullAndPartialIntersectionsKeepVisibleItemOrder() {
        assertEquals(List.of("first", "second"), select(
                DesktopMarqueeSelection.Rectangle.normalized(10, 10, 60, 60),
                item("first", 20, 20, 40, 40),
                item("second", 55, 55, 75, 75)));
    }

    @Test
    public void reversedDragDirectionsNormalizeToSameRectangle() {
        final DesktopMarqueeSelection.Rectangle forward =
                DesktopMarqueeSelection.Rectangle.normalized(10, 10, 40, 40);
        final DesktopMarqueeSelection.Rectangle reverse =
                DesktopMarqueeSelection.Rectangle.normalized(40, 40, 10, 10);
        assertEquals(select(forward, item("a", 30, 30, 50, 50)),
                select(reverse, item("a", 30, 30, 50, 50)));
    }

    @Test
    public void viewportResizeAndClippingUseOnlyVisibleIntersection() {
        final DesktopMarqueeSelection.Rectangle marquee =
                DesktopMarqueeSelection.Rectangle.normalized(40, 40, 120, 120);
        final DesktopMarqueeSelection.ItemBounds partiallyVisible =
                item("visible", 90, 90, 120, 120);
        final DesktopMarqueeSelection.ItemBounds outside =
                item("outside", 105, 105, 120, 120);
        assertEquals(List.of("visible"), select(
                marquee,
                VIEWPORT,
                partiallyVisible,
                outside));

        final DesktopMarqueeSelection.Rectangle smallerViewport =
                DesktopMarqueeSelection.Rectangle.bounds(0, 0, 80, 80);
        assertEquals(List.of(), select(
                marquee,
                smallerViewport,
                partiallyVisible,
                outside));
    }

    @Test
    public void rectanglesThatOnlyTouchAnEdgeDoNotSelect() {
        assertEquals(List.of(), select(
                DesktopMarqueeSelection.Rectangle.normalized(0, 0, 10, 10),
                item("edge", 10, 2, 20, 8)));
    }

    private static List<String> select(
            final DesktopMarqueeSelection.Rectangle marquee,
            final DesktopMarqueeSelection.ItemBounds... items) {
        return select(marquee, VIEWPORT, items);
    }

    private static List<String> select(
            final DesktopMarqueeSelection.Rectangle marquee,
            final DesktopMarqueeSelection.Rectangle viewport,
            final DesktopMarqueeSelection.ItemBounds... items) {
        return DesktopMarqueeSelection.intersectingItemIds(
                marquee, viewport, List.of(items));
    }

    private static DesktopMarqueeSelection.ItemBounds item(
            final String id,
            final float left,
            final float top,
            final float right,
            final float bottom) {
        return new DesktopMarqueeSelection.ItemBounds(
                id,
                DesktopMarqueeSelection.Rectangle.bounds(
                        left, top, right, bottom));
    }
}
