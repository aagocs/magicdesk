package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import org.junit.Test;

public final class DesktopTaskbarHostTest {
    private final ShellBounds output = new ShellBounds(0, 0, 1920, 1080);
    private final ShellBounds floating = new ShellBounds(500, 1000, 1420, 1064);
    @Test public void revealEdgeIsAtOutputEdgeNotFloatingPanelEdge() {
        assertEquals(new ShellBounds(500, 1076, 1420, 1080), PanelGeometry.presented(output, floating,
                ShellPanel.Edge.BOTTOM, true, true, 4));
        assertEquals(0, PanelGeometry.paintAlpha(true, true));
    }
    @Test public void unavailableHasNeitherInputNorPaint() {
        assertTrue(PanelGeometry.presented(output, floating, ShellPanel.Edge.BOTTOM, false, true, 4).isEmpty());
        assertTrue(PanelGeometry.presented(output, floating, ShellPanel.Edge.BOTTOM, false, false, 4).isEmpty());
        assertEquals(0, PanelGeometry.paintAlpha(false, false));
    }
    @Test public void visiblePreservesExactResolvedSurface() {
        assertSame(floating, PanelGeometry.presented(output, floating, ShellPanel.Edge.BOTTOM, true, false, 1));
        assertEquals(255, PanelGeometry.paintAlpha(true, false));
    }
}
