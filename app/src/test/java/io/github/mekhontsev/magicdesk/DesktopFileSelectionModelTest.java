package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;

public final class DesktopFileSelectionModelTest {
    @Test
    public void controlClickTogglesItemAndMovesRangeAnchor() {
        final DesktopFileSelectionModel selection = new DesktopFileSelectionModel();
        final List<String> visible = List.of("a", "b", "c");
        selection.selectOnly("a");

        selection.selectModified("c", visible, true, false);
        assertEquals(List.of("a", "c"), selection.selectedItemIds());
        assertEquals("c", selection.focusedItemId());

        selection.selectModified("c", visible, true, false);
        assertEquals(List.of("a"), selection.selectedItemIds());
    }

    @Test
    public void shiftSelectsInclusiveRangeAndKeepsAnchorWhileExtending() {
        final DesktopFileSelectionModel selection = new DesktopFileSelectionModel();
        final List<String> visible = List.of("a", "b", "c", "d");
        selection.selectOnly("b");

        selection.selectModified("d", visible, false, true);
        assertEquals(List.of("b", "c", "d"), selection.selectedItemIds());
        selection.selectModified("c", visible, false, true);
        assertEquals(List.of("b", "c"), selection.selectedItemIds());
    }

    @Test
    public void controlClickUpdatesAnchorForLaterRangeSelection() {
        final DesktopFileSelectionModel selection = new DesktopFileSelectionModel();
        final List<String> visible = List.of("a", "b", "c", "d");
        selection.selectOnly("a");
        selection.selectModified("c", visible, true, false);

        selection.selectModified("d", visible, false, true);
        assertEquals(List.of("c", "d"), selection.selectedItemIds());
    }

    @Test
    public void controlShiftAddsRangeWithoutDroppingExistingItems() {
        final DesktopFileSelectionModel selection = new DesktopFileSelectionModel();
        final List<String> visible = List.of("a", "b", "c", "d");
        selection.selectOnly("a");
        selection.selectModified("d", visible, true, false);

        selection.selectModified("b", visible, true, true);
        assertEquals(List.of("a", "d", "b", "c"), selection.selectedItemIds());
    }

    @Test
    public void selectAllAndRetainRemoveDeletedItemsAndClearAnchor() {
        final DesktopFileSelectionModel selection = new DesktopFileSelectionModel();
        selection.selectAll(List.of("a", "b", "c"));
        assertTrue(selection.contains("a"));
        assertTrue(selection.contains("c"));
        selection.retain(Set.of("b", "c"));
        assertEquals(List.of("b", "c"), selection.selectedItemIds());
        selection.remove("b");
        assertEquals(List.of("c"), selection.selectedItemIds());
        selection.clear();
        assertTrue(selection.isEmpty());
        assertFalse(selection.isSingleSelection());
    }

    @Test
    public void orderedSelectionContainsExactlyClipboardEligibleItems() {
        final DesktopFileSelectionModel selection = new DesktopFileSelectionModel();
        selection.selectOnly("c");
        selection.selectModified("a", List.of("a", "b", "c", "d"), true, false);

        assertEquals(List.of("a", "c"), selection.selectedItemIdsInOrder(
                List.of("a", "b", "c", "d")));
    }

    @Test
    public void rangeSelectionIgnoresTargetsOutsideVisibleSurface() {
        final DesktopFileSelectionModel selection = new DesktopFileSelectionModel();
        selection.selectOnly("a");

        assertFalse(selection.selectModified("hidden", List.of("a", "b"), false, true));
        assertEquals(List.of("a"), selection.selectedItemIds());
    }
}
