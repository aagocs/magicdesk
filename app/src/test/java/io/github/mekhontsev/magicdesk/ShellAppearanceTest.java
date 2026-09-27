package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ShellAppearanceTest {
    @Test public void defaultsAndPresetsRoundTripWithoutAndroid() throws Exception {
        assertEquals(ShellAppearance.defaults(), ShellAppearanceJson.parse("{}"));
        for (String preset : new String[] {"dark", "light", "contrast"}) {
            final var value = ShellAppearance.preset(preset).withTaskbar(ShellAppearance.Taskbar.floating());
            assertEquals(value, ShellAppearanceJson.parse(ShellAppearanceJson.encode(value).toString()));
        }
    }
    @Test public void partialDocumentInheritsDefaultsAndNeverMergesPreviousState() throws Exception {
        var value = ShellAppearanceJson.parse("{\"colors\":{\"accent\":\"#123456\"},\"taskbar\":{\"bottomGapDp\":12}}");
        assertEquals(0xff123456, value.palette().color(UiColor.ACCENT));
        assertEquals(ShellAppearance.defaults().typography(), value.typography());
        assertEquals(12, value.taskbar().bottomGapDp());
        assertEquals(ShellAppearance.Width.FILL, value.taskbar().width());
    }
    @Test public void presetSelectionPreservesGeometryAndSnapshotIsImmutable() {
        var source = ShellAppearance.defaults().withTaskbar(ShellAppearance.Taskbar.floating());
        var target = source.withStyle(ShellAppearance.preset("light"));
        assertEquals(source.taskbar(), target.taskbar());
        assertNotEquals(source.palette(), target.palette());
        assertThrows(UnsupportedOperationException.class, () -> target.palette().colors().put(UiColor.TEXT, 0));
    }
    @Test public void rejectsInvalidOrExecutableFieldsAtomically() {
        for (String invalid : new String[] {"{\"command\":\"id\"}", "{\"version\":2}",
                "{\"colors\":{\"text\":\"#00ffffff\"}}", "{\"colors\":{\"transparent\":\"#ffffff\"}}",
                "{\"colors\":{\"unknown\":\"#ffffff\"}}", "{\"typography\":{\"scale\":8}}",
                "{\"typography\":{\"font\":\"/sdcard/font.ttf\"}}", "{\"taskbar\":{\"opacity\":0}}",
                "{\"taskbar\":{\"maxWidthDp\":100000000}}", "{\"taskbar\":{\"sideGapDp\":1.5}}",
                "{\"taskbar\":{\"reserveSpace\":\"true\"}}", "{\"shape\":{\"borderDp\":null}}",
                "{\"taskbar\":{\"bottomGapDp\":-1}}", "{\"preset\":null}", "{\"typography\":{\"font\":null}}",
                "{\"colors\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}",
                "{} trailing", " ".repeat(32769)}) {
            assertThrows(invalid, Exception.class, () -> ShellAppearanceJson.parse(invalid));
        }
    }
    @Test public void overlayChangesReservationNotGeometry() {
        var style = ShellAppearance.Taskbar.floating();
        var viewport = new DesktopViewport(0, 0, 1920, 1080, 0, 0, 0, 0);
        var layout = new DesktopShellLayout();
        var geometry = TaskbarGeometry.resolve(style, 1, 1920, 1080, 64, 900, 500);
        layout.update(viewport, geometry, false);
        assertEquals(new ShellBounds(510, 1004, 1410, 1068), layout.taskbar().content());
        assertEquals(layout.taskbar().content(), layout.taskbar().paint());
        assertEquals(1004, layout.snapshot().workArea().bottom());
        var overlay = new ShellAppearance.Taskbar(style.width(), style.alignment(), style.maxWidthDp(),
                style.sideGapDp(), style.bottomGapDp(), style.paddingDp(), style.radiusDp(), style.opacity(), false);
        layout.update(viewport, TaskbarGeometry.resolve(overlay, 1, 1920, 1080, 64, 900, 500), false);
        assertEquals(1080, layout.snapshot().workArea().bottom());
        assertEquals(new ShellBounds(510, 1004, 1410, 1068), layout.taskbar().content());
    }
    @Test public void maximumWidthAndAccessibleMinimumAreBoundedByViewport() {
        var style = ShellAppearance.Taskbar.floating();
        assertEquals(1100, TaskbarGeometry.resolve(style, 1, 1920, 1080, 64, 2500, 600).width());
        assertEquals(374, TaskbarGeometry.resolve(style, 1, 374, 800, 64, 900, 500).width());
        assertEquals(1800, TaskbarGeometry.resolve(style, 2, 3840, 2160, 128, 1800, 1000).width());
    }
    @Test public void contentTaskbarCanStartBeforeItsViewsHaveBeenMeasured() {
        var style = ShellAppearance.Taskbar.floating();
        var viewport = new DesktopViewport(0, 0, 1920, 1080, 0, 0, 0, 0);
        var layout = new DesktopShellLayout();
        var pending = TaskbarGeometry.resolve(style, 1, 1920, 1080, 64, 0, 0);
        layout.update(viewport, pending, false);
        assertTrue(layout.taskbar().content().width() > 0);
        layout.update(viewport, TaskbarGeometry.resolve(style, 1, 1920, 1080, 64, 900, 500), false);
        assertEquals(900, layout.taskbar().content().width());
        assertEquals(1004, layout.snapshot().workArea().bottom());
    }
    @Test public void defaultPhonePanelExtendsPaintButFloatingOneDoesNot() {
        var viewport = new DesktopViewport(0, 0, 1080, 2400, 0, 80, 0, 120);
        var layout = new DesktopShellLayout();
        layout.update(viewport, TaskbarGeometry.resolve(ShellAppearance.Taskbar.defaults(), 3, 1080, 2200, 192, 900, 500), false);
        assertEquals(2400, layout.taskbar().paint().bottom());
        assertEquals(2280, layout.taskbar().content().bottom());
        layout.update(viewport, TaskbarGeometry.resolve(ShellAppearance.Taskbar.floating(), 3, 1080, 2200, 192, 900, 500), false);
        assertEquals(layout.taskbar().content(), layout.taskbar().paint());
        assertEquals(2244, layout.taskbar().paint().bottom());
    }
}
