package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AppearanceTransactionTest {
    @Test public void previewDoesNotPersistAndRestartRetainsTheCommittedTheme() {
        var dark = ShellAppearance.defaults(); var light = ShellAppearance.preset("light");
        var store = new AppearanceTransaction(dark);
        String id = store.preview(light);
        assertEquals(light, store.snapshot().current());
        assertEquals(dark, store.snapshot().committed());
        assertEquals(dark, new AppearanceTransaction(store.snapshot().committed()).snapshot().current());
        store.cancel(id);
        assertEquals(dark, store.snapshot().current()); assertNull(store.snapshot().previewId());
    }
    @Test public void confirmedPreviewCannotBeCancelledAndStaleOwnerCannotUndoLaterChanges() {
        var store = new AppearanceTransaction(ShellAppearance.defaults());
        String id = store.preview(ShellAppearance.preset("light"));
        store.apply(store.requirePreview(id));
        assertEquals(ShellAppearance.preset("light"), store.snapshot().committed());
        assertThrows(IllegalArgumentException.class, () -> store.cancel(id));
        String second = store.preview(ShellAppearance.defaults());
        store.apply(ShellAppearance.preset("contrast"));
        assertThrows(IllegalArgumentException.class, () -> store.cancel(second));
        assertEquals(ShellAppearance.preset("contrast"), store.snapshot().current());
    }
    @Test public void overlappingPreviewsAreRejectedWithoutReplacingTheFirst() {
        var store = new AppearanceTransaction(ShellAppearance.defaults());
        String id = store.preview(ShellAppearance.preset("light"));
        assertThrows(IllegalStateException.class, () -> store.preview(ShellAppearance.preset("contrast")));
        assertEquals(id, store.snapshot().previewId());
        assertEquals(1, store.snapshot().revision());
    }
}
