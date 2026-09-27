package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class StartMenuLayoutTest {
    @Test
    public void narrowPhoneAndDesktopHaveIndependentGridCapacity() {
        assertEquals(3, StartMenuLayout.columns(332, 100, 44));
        assertEquals(5, StartMenuLayout.columns(532, 100, 44));
        assertEquals(3, StartMenuLayout.columns(332, 100, 44));
    }

    @Test
    public void keyboardResizeKeepsOneScrollableRow() {
        assertEquals(3, StartMenuLayout.rows(430));
        assertEquals(1, StartMenuLayout.rows(180));
        assertEquals(1, StartMenuLayout.rows(40));
    }

    @Test
    public void largeViewportUsesAvailableWidthWhileRowsRemainBounded() {
        assertEquals(20, StartMenuLayout.columns(2000, 100, 44));
        assertEquals(6, StartMenuLayout.rows(2000));
    }

    @Test
    public void unmeasuredViewportRemainsValid() {
        assertEquals(1, StartMenuLayout.columns(0, 100, 44));
        assertEquals(1, StartMenuLayout.rows(0));
    }

    @Test
    public void largeIconsFitGridPaddingAndSearchRows() {
        assertEquals(3, StartMenuLayout.columns(332, 80, 64));
        assertEquals(4, StartMenuLayout.columns(332, 80, 24));
        assertEquals(1, StartMenuLayout.columns(30, 80, 64));
        assertEquals(76, StartMenuLayout.rowHeight(64));
        assertEquals(58, StartMenuLayout.rowHeight(24));
    }

}
