package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import org.junit.Test;

public final class DesktopTaskbarHostTest {
    private final ShellBounds output = new ShellBounds(0, 0, 1920, 1080);
    private final ShellBounds floating = new ShellBounds(500, 1000, 1420, 1064);
    @Test public void revealEdgeIsAtOutputEdgeNotFloatingPanelEdge() {
        assertEquals(new ShellBounds(500, 1076, 1420, 1080), TaskbarGeometry.presented(output, floating, true, true, 4));
        assertEquals(0, TaskbarGeometry.paintAlpha(true, true));
    }
    @Test public void unavailableHasNeitherInputNorPaint() {
        assertTrue(TaskbarGeometry.presented(output, floating, false, true, 4).isEmpty());
        assertTrue(TaskbarGeometry.presented(output, floating, false, false, 4).isEmpty());
        assertEquals(0, TaskbarGeometry.paintAlpha(false, false));
    }
    @Test public void visiblePreservesExactResolvedSurface() {
        assertSame(floating, TaskbarGeometry.presented(output, floating, true, false, 1));
        assertEquals(255, TaskbarGeometry.paintAlpha(true, false));
    }
}
