package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class DialogContentInsetsTest {
    @Test public void captionPaddingTracksActualDialogOverlap() {
        assertEquals(60, DialogContentInsets.leading(153, 60, 153, 564));
        assertEquals(0, DialogContentInsets.leading(153, 60, 300, 200));
        assertEquals(13, DialogContentInsets.leading(153, 60, 200, 400));
        assertEquals(20, DialogContentInsets.leading(153, 60, 153, 20));
    }
    @Test public void absentSourcesDoNotCreateMarginsAndTrailingEdgesAreSymmetric() {
        assertEquals(0, DialogContentInsets.leading(153, 0, 100, 500));
        assertEquals(0, DialogContentInsets.trailing(717, 0, 300, 500));
        assertEquals(40, DialogContentInsets.trailing(717, 40, 153, 564));
        assertEquals(0, DialogContentInsets.trailing(717, 40, 300, 200));
    }
}
