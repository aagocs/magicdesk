package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class StartSearchSelectionTest {
    @Test public void insertionAndReorderingPreserveTheSelectedIdentity() {
        final StartSearchSelection selection = new StartSearchSelection();
        selection.update(List.of("A", "B", "C"));
        selection.move(1);
        selection.update(List.of("X", "C", "A", "B"));
        assertEquals(3, selection.index());
        selection.update(List.of("B", "A", "C"));
        assertEquals(0, selection.index());
    }

    @Test public void removedSelectionUsesTheSamePositionOrTheLastRemainingResult() {
        final StartSearchSelection selection = new StartSearchSelection();
        selection.update(List.of("A", "B", "C"));
        selection.move(1);
        selection.update(List.of("A", "C"));
        assertEquals(1, selection.index());
        selection.update(List.of("A"));
        assertEquals(0, selection.index());
    }

    @Test public void emptyResultsHaveNoSelectionAndNewResultsSelectTheFirst() {
        final StartSearchSelection selection = new StartSearchSelection();
        assertEquals(-1, selection.index());
        selection.move(1);
        assertEquals(-1, selection.index());
        selection.update(List.of("A", "B"));
        selection.move(1);
        selection.update(List.of());
        assertEquals(-1, selection.index());
        selection.update(List.of("X", "B"));
        assertEquals(0, selection.index());
    }

    @Test public void arrowsStopAtTheFirstAndLastResults() {
        final StartSearchSelection selection = new StartSearchSelection();
        selection.update(List.of("A", "B"));
        selection.move(-1);
        assertEquals(0, selection.index());
        selection.move(1);
        selection.move(1);
        assertEquals(1, selection.index());
    }

    @Test public void queryOrDestinationResetDoesNotRetainAnOldIdentity() {
        final StartSearchSelection selection = new StartSearchSelection();
        selection.update(List.of("A", "B"));
        selection.move(1);
        selection.reset();
        selection.update(List.of("X", "B"));
        assertEquals(0, selection.index());
    }

    @Test public void callerMutationCannotChangeTheRetainedIdentity() {
        final StartSearchSelection selection = new StartSearchSelection();
        final List<String> keys = new ArrayList<>(List.of("A", "B"));
        selection.update(keys);
        selection.move(1);
        keys.set(1, "X");
        selection.update(List.of("B", "X", "A"));
        assertEquals(0, selection.index());
    }
}
