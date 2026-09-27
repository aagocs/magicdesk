package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import static io.github.mekhontsev.magicdesk.ShellLayoutTestSupport.geometry;
import static io.github.mekhontsev.magicdesk.ShellLayoutTestSupport.panel;

import org.junit.Test;
import java.util.List;

public final class DesktopShellLayoutTest {
    @Test public void externalTaskbarAndWorkAreaMatchExistingGeometry() {
        final DesktopShellLayout layout = layout(1920, 1080, 0, 0, 64, false);
        assertEquals(new ShellBounds(0, 0, 1920, 1016), layout.snapshot().workArea());
        final ShellLayout.Surface taskbar = layout.taskbar();
        assertEquals(new ShellBounds(0, 1016, 1920, 1080), taskbar.content());
        assertEquals(taskbar.content(), taskbar.paint());
        assertEquals(taskbar.paint(), taskbar.input());
        assertEquals(ShellSurface.Keyboard.NONE, taskbar.request().keyboard());
    }

    @Test public void phonePaintExtendsThroughNavigationButControlsDoNot() {
        final DesktopShellLayout layout = layout(1216, 2688, 147, 126, 169, false);
        final ShellLayout.Surface taskbar = layout.taskbar();
        assertEquals(new ShellBounds(0, 2393, 1216, 2562), taskbar.content());
        assertEquals(new ShellBounds(0, 2393, 1216, 2688), taskbar.paint());
        assertEquals(new ShellBounds(0, 147, 1216, 2393), layout.snapshot().workArea());
        assertEquals(layout.snapshot().workArea(), layout.snapshot().panelArea());
    }

    @Test public void autoHideChangesOnlyWindowReservationNotPopupOrTaskbarBounds() {
        final DesktopShellLayout layout = layout(1920, 1080, 0, 0, 64, false);
        final ShellLayout.Snapshot before = layout.snapshot();
        final ShellBounds paint = layout.taskbar().paint();
        ShellLayoutTestSupport.update(layout, new DesktopViewport(0, 0, 1920, 1080, 0, 0, 0, 0), 64, true);
        assertEquals(before.content(), layout.snapshot().workArea());
        assertEquals(before.panelArea(), layout.snapshot().panelArea());
        assertEquals(paint, layout.taskbar().paint());
    }

    @Test public void stableInputsReuseSnapshotAndDensityChangeRecomputesIt() {
        final DesktopShellLayout layout = layout(1920, 1080, 0, 0, 64, false);
        final DesktopViewport viewport = new DesktopViewport(0, 0, 1920, 1080, 0, 0, 0, 0);
        final ShellLayout.Snapshot before = layout.snapshot();
        ShellLayoutTestSupport.update(layout, viewport, 64, false);
        assertSame(before, layout.snapshot());
        ShellLayoutTestSupport.update(layout, viewport, 77, false);
        assertEquals(1003, layout.snapshot().workArea().bottom());
        layout.release();
        assertEquals(0, layout.snapshot().exclusions().size());
        assertEquals(viewport.contentGeometry(), layout.snapshot().workArea());
        ShellLayoutTestSupport.update(layout, viewport, 77, false);
        assertEquals(1, layout.snapshot().surfaces().size());
    }

    @Test public void degenerateViewportRetainsOnePixelWorkArea() {
        final DesktopShellLayout layout = layout(1, 1, 0, 0, 100, false);
        assertEquals(new ShellBounds(0, 0, 1, 1), layout.snapshot().workArea());
    }

    @Test public void fourEdgesShareOneWorkAreaAndResolveComponentsToTheirPanel() {
        final var viewport = new DesktopViewport(0, 0, 1000, 800, 0, 0, 0, 0);
        final var layout = new DesktopShellLayout();
        final var style = ShellAppearance.PanelStyle.defaults();
        final var panels = List.of(
                geometry(panel("top", ShellPanel.Edge.TOP, style, ShellComposition.Kind.CLOCK), viewport, 40),
                geometry(panel("bottom", ShellPanel.Edge.BOTTOM, style, ShellComposition.Kind.START), viewport, 60),
                geometry(panel("left", ShellPanel.Edge.LEFT, style, ShellComposition.Kind.TASKS), viewport, 50),
                geometry(panel("right", ShellPanel.Edge.RIGHT, style, ShellComposition.Kind.NOTIFICATIONS), viewport, 70));
        layout.update(viewport, panels, false);
        assertEquals(new ShellBounds(0, 0, 1000, 40), layout.panel("top").content());
        assertEquals(new ShellBounds(0, 740, 1000, 800), layout.panel("bottom").content());
        assertEquals(new ShellBounds(0, 40, 50, 740), layout.panel("left").content());
        assertEquals(new ShellBounds(930, 40, 1000, 740), layout.panel("right").content());
        assertEquals(new ShellBounds(50, 40, 930, 740), layout.snapshot().workArea());
        assertEquals(layout.snapshot().workArea(), layout.snapshot().panelArea());
        assertSame(layout.panel("top"), layout.taskbar());
        assertSame(layout.panel("left"), layout.panelFor(ShellComposition.Kind.TASKS));
        assertSame(layout.panel("bottom"), layout.panelFor(ShellComposition.Kind.START));
        assertSame(layout.taskbar(), layout.panelFor(ShellComposition.Kind.QUICK_CONTROLS));
        final var before = layout.snapshot();
        layout.update(viewport, panels, true);
        assertEquals(viewport.contentGeometry(), layout.snapshot().workArea());
        assertEquals(before.panelArea(), layout.snapshot().panelArea());
        before.surfaces().forEach((id, surface) -> assertEquals(surface.content(), layout.snapshot().surfaces().get(id).content()));
    }

    @Test public void sameEdgePanelsStackIncludingFloatingGapsAndAutoHiddenReservations() {
        final var viewport = new DesktopViewport(0, 0, 1000, 800, 0, 20, 0, 30);
        for (ShellPanel.Edge edge : ShellPanel.Edge.values()) {
            final var layout = new DesktopShellLayout();
            final var style = ShellAppearance.PanelStyle.floating();
            final var panels = List.of(
                    geometry(panel("outer", edge, style, ShellComposition.Kind.START), viewport, 40),
                    geometry(panel("inner", edge, style, ShellComposition.Kind.TASKS), viewport, 60));
            layout.update(viewport, panels, false);
            final var outer = layout.panel("outer").content();
            final var inner = layout.panel("inner").content();
            switch (edge) {
                case TOP -> assertEquals(outer.bottom() + 12, inner.top());
                case BOTTOM -> assertEquals(outer.top() - 12, inner.bottom());
                case LEFT -> assertEquals(outer.right() + 12, inner.left());
                case RIGHT -> assertEquals(outer.left() - 12, inner.right());
            }
            final var before = layout.snapshot();
            layout.update(viewport, panels, true);
            assertEquals(outer, layout.panel("outer").content());
            assertEquals(inner, layout.panel("inner").content());
            assertEquals(before.panelArea(), layout.snapshot().panelArea());
            assertEquals(viewport.contentGeometry(), layout.snapshot().workArea());
        }
    }

    @Test public void onlyOutermostFlushPanelPaintsTheSystemInset() {
        final var viewport = new DesktopViewport(0, 0, 1000, 800, 0, 20, 0, 30);
        final var layout = new DesktopShellLayout();
        final var style = ShellAppearance.PanelStyle.defaults();
        layout.update(viewport, List.of(
                geometry(panel("outer", ShellPanel.Edge.BOTTOM, style, ShellComposition.Kind.START), viewport, 40),
                geometry(panel("inner", ShellPanel.Edge.BOTTOM, style, ShellComposition.Kind.TASKS), viewport, 60)), false);
        assertEquals(800, layout.panel("outer").paint().bottom());
        assertEquals(770, layout.panel("outer").content().bottom());
        assertEquals(layout.panel("inner").content(), layout.panel("inner").paint());
        assertEquals(layout.panel("inner").paint().bottom(), layout.panel("outer").content().top());
    }

    @Test public void externalReservationsSuppressInsetPaintUntilTheNativePanelReturnsToTheContentEdge() {
        final var viewport = new DesktopViewport(100, 200, 1100, 1000, 30, 30, 30, 30);
        for (ShellPanel.Edge edge : ShellPanel.Edge.values()) {
            for (boolean absolute : new boolean[] {false, true}) {
                final var layout = new DesktopShellLayout();
                final var nativePanel = geometry(panel("main", edge, ShellAppearance.PanelStyle.defaults(),
                        ShellComposition.Kind.START), viewport, 40);
                layout.update(viewport, List.of(nativePanel), false);
                final var original = layout.taskbar();
                assertNotEquals(original.content(), original.paint());
                final var external = layout.bind();
                final var reservation = absolute
                        ? ShellReservation.absolute(nativePanel.reservationEdge(), 80,
                                edge.vertical() ? 200 : 100, edge.vertical() ? 1000 : 1100)
                        : ShellReservation.exclusive(nativePanel.reservationEdge(), 50, true);
                final int thickness = absolute ? 80 : 50;
                final var request = new ShellSurface("external", true,
                        absolute ? ShellSurface.Layer.TOP : ShellSurface.Layer.OVERLAY, ShellSurface.Keyboard.NONE,
                        new ShellSurface.Placement(absolute ? ShellSurface.Reference.OUTPUT : ShellSurface.Reference.CONTENT,
                                nativePanel.anchors(), edge.vertical() ? thickness : 0, edge.vertical() ? 0 : thickness,
                                ShellSurface.Margins.NONE), ShellSurface.Margins.NONE,
                        ShellSurface.Input.CONTENT, List.of(reservation));
                external.commit(List.of(request));
                assertEquals(edge + " absolute=" + absolute, layout.taskbar().content(), layout.taskbar().paint());
                assertTrue(layout.taskbar().input().intersect(external.surface("external").input()).isEmpty());
                layout.update(viewport, List.of(nativePanel), true);
                assertEquals(layout.taskbar().content(), layout.taskbar().paint());
                external.commit(List.of(new ShellSurface(request.id(), false, request.layer(), request.keyboard(),
                        request.placement(), request.paintExtension(), request.input(), request.reservations())));
                assertEquals(original.content(), layout.taskbar().content());
                assertEquals(original.paint(), layout.taskbar().paint());
                external.commit(List.of(request));
                assertEquals(layout.taskbar().content(), layout.taskbar().paint());
                external.close();
                assertEquals(original.paint(), layout.taskbar().paint());
            }
        }
    }

    @Test public void panelSetReplacementIsAtomicAndRetainsExternalReservations() {
        final var viewport = new DesktopViewport(0, 0, 1000, 800, 0, 0, 0, 0);
        final var layout = new DesktopShellLayout();
        final var style = ShellAppearance.PanelStyle.defaults();
        final var bottom = geometry(panel("bottom", ShellPanel.Edge.BOTTOM, style, ShellComposition.Kind.START), viewport, 40);
        layout.update(viewport, List.of(bottom), false);
        final var external = layout.bind();
        external.commit(List.of(new ShellSurface("external", true, ShellSurface.Layer.TOP, ShellSurface.Keyboard.NONE,
                new ShellSurface.Placement(ShellSurface.Reference.AVAILABLE, ShellSurface.LEFT | ShellSurface.TOP | ShellSurface.RIGHT,
                        0, 20, ShellSurface.Margins.NONE), ShellSurface.Margins.NONE, ShellSurface.Input.CONTENT,
                List.of(ShellReservation.absolute(ShellReservation.Edge.TOP, 20, 0, 1000)))));
        final int[] publications = {0};
        layout.listen(() -> {
            publications[0]++;
            assertNull(layout.panel("bottom"));
            assertSame(layout.panel("left"), layout.panelFor(ShellComposition.Kind.START));
            assertEquals(3, layout.snapshot().surfaces().size());
        });
        layout.update(viewport, List.of(
                geometry(panel("left", ShellPanel.Edge.LEFT, style, ShellComposition.Kind.START), viewport, 50),
                geometry(panel("top", ShellPanel.Edge.TOP, style, ShellComposition.Kind.CLOCK), viewport, 40)), false);
        assertEquals(1, publications[0]);
        assertEquals(new ShellBounds(50, 60, 1000, 800), layout.snapshot().workArea());
        assertEquals(new ShellBounds(0, 20, 50, 800), layout.panel("left").content());
        assertNotNull(external.surface("external"));
    }

    @Test public void exhaustedViewportKeepsEveryPanelAndBothAreasNonemptyAndBounded() {
        final var viewport = new DesktopViewport(10, 20, 11, 21, 0, 0, 0, 0);
        final var layout = new DesktopShellLayout();
        final var panels = java.util.Arrays.stream(ShellPanel.Edge.values()).map(edge ->
                geometry(panel(edge.name().toLowerCase(java.util.Locale.ROOT), edge,
                        ShellAppearance.PanelStyle.floating(), ShellComposition.Kind.SPACER), viewport, 100)).toList();
        layout.update(viewport, panels, false);
        assertEquals(viewport.contentGeometry(), layout.snapshot().workArea());
        assertEquals(viewport.contentGeometry(), layout.snapshot().panelArea());
        for (var surface : layout.snapshot().surfaces().values()) {
            assertEquals(viewport.contentGeometry(), surface.content());
            assertEquals(surface.content(), surface.paint());
        }
    }

    private static DesktopShellLayout layout(final int width, final int height,
            final int top, final int bottom, final int taskbar, final boolean autoHide) {
        final DesktopShellLayout layout = new DesktopShellLayout();
        ShellLayoutTestSupport.update(layout, new DesktopViewport(0, 0, width, height, 0, top, 0, bottom), taskbar, autoHide);
        return layout;
    }
}
