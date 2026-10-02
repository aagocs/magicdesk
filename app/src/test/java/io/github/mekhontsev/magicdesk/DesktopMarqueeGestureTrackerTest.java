package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DesktopMarqueeGestureTrackerTest {
    @Test
    public void tapBelowSlopDoesNotStartMarquee() {
        final DesktopMarqueeGestureTracker tracker =
                new DesktopMarqueeGestureTracker();
        tracker.down(5, 7, true);
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.NONE,
                tracker.move(8, 9, 5));
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.NONE,
                tracker.up());
        assertFalse(tracker.isTracking());
    }

    @Test
    public void primaryGestureStartsOnlyAfterCrossingSlopAndFinishes() {
        final DesktopMarqueeGestureTracker tracker =
                new DesktopMarqueeGestureTracker();
        tracker.down(5, 7, true);
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.NONE,
                tracker.move(5, 11, 5));
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.STARTED,
                tracker.move(5, 13, 5));
        assertTrue(tracker.isActive());
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.UPDATED,
                tracker.move(15, 13, 5));
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.FINISHED,
                tracker.up());
        assertFalse(tracker.isActive());
    }

    @Test
    public void ineligibleOrCancelledGestureNeverLeavesTrackingState() {
        final DesktopMarqueeGestureTracker tracker =
                new DesktopMarqueeGestureTracker();
        tracker.down(0, 0, false);
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.NONE,
                tracker.move(20, 20, 5));
        tracker.down(0, 0, true);
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.STARTED,
                tracker.move(10, 10, 5));
        assertEquals(
                DesktopMarqueeGestureTracker.Transition.CANCELLED,
                tracker.cancel());
        assertFalse(tracker.isTracking());
        assertFalse(tracker.isActive());
    }

    @Test
    public void activeGestureExposesItsOriginalGridPosition() {
        final DesktopMarqueeGestureTracker tracker =
                new DesktopMarqueeGestureTracker();
        tracker.down(12, 34, true);
        assertEquals(12, tracker.startX(), 0);
        assertEquals(34, tracker.startY(), 0);
    }
}
