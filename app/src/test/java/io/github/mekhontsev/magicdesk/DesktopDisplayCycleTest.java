package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public final class DesktopDisplayCycleTest {
    @Test
    public void stepsThroughWorkspacesInStableIdOrderAndWraps() {
        final Set<Integer> displays = Set.of(9, 2, 5);

        assertEquals(5, DesktopDisplayCycle.adjacent(2, displays, false));
        assertEquals(9, DesktopDisplayCycle.adjacent(5, displays, false));
        assertEquals(2, DesktopDisplayCycle.adjacent(9, displays, false));
        assertEquals(9, DesktopDisplayCycle.adjacent(2, displays, true));
        assertEquals(2, DesktopDisplayCycle.adjacent(5, displays, true));
    }

    @Test
    public void repeatedMovesInOneDirectionVisitEveryDisplay() {
        final List<Integer> displays = List.of(3, 7, 4);
        int current = 3;
        for (final int expected : new int[] {4, 7, 3}) {
            current = DesktopDisplayCycle.adjacent(current, displays, false);
            assertEquals(expected, current);
        }
    }

    @Test
    public void withoutAnotherWorkspaceThereIsNoTarget() {
        assertEquals(-1, DesktopDisplayCycle.adjacent(4, Set.of(4), false));
        assertEquals(-1, DesktopDisplayCycle.adjacent(4, Set.of(), true));
        assertEquals(-1, DesktopDisplayCycle.adjacent(4, null, true));
    }

    @Test
    public void aTaskOutsideEveryWorkspaceEntersAtTheNearEnd() {
        // The phone without a phone Desktop is not itself a workspace.
        assertEquals(3, DesktopDisplayCycle.adjacent(0, List.of(3, 6), false));
        assertEquals(6, DesktopDisplayCycle.adjacent(0, List.of(3, 6), true));
    }

    @Test
    public void duplicateAndInvalidIdsAreIgnored() {
        assertEquals(6, DesktopDisplayCycle.adjacent(3, java.util.Arrays.asList(3, 3, -1, null, 6), false));
    }
}
