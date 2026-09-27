package io.github.mekhontsev.magicdesk;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ShellThemesTest {
    static ShellAppearance load(String id) throws Exception {
        return ShellThemes.load(id, path -> Files.newInputStream(Path.of("src/main/assets", path)));
    }

    @Test public void bundledDocumentsUseTheProductionSchemaAndRoundTripInEitherScope() throws Exception {
        var ids = new HashSet<String>();
        var files = new HashSet<String>();
        for (var entry : ShellThemes.ENTRIES) {
            assertTrue(ids.add(entry.id())); files.add(entry.id() + ".json");
            var theme = load(entry.id());
            var json = ShellAppearanceJson.encode(theme);
            ShellAppearanceSchema.validate(json);
            assertEquals(theme, ShellAppearanceJson.parse(json.toString()));
            assertEquals(theme, WorkspaceAppearancePatch.parse(json.toString()).resolve(ShellAppearance.preset("contrast")));
            assertFalse(theme.resources().hasAssets());
            assertEquals(UiColor.TRANSPARENT, theme.feedback().normal());
            assertNotNull(theme.composition().panelFor(ShellComposition.Kind.START));
            assertTrue(theme.composition().start().sections().containsAll(List.of(ShellComposition.Section.values())));
        }
        try (var paths = Files.list(Path.of("src/main/assets/themes"))) {
            assertEquals(files, paths.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        assertEquals(3, ids.size());
    }

    @Test public void invalidNamesNeverOpenPathsAndMalformedDocumentsCloseTheirInput() {
        for (String id : new String[] {"../workbench", "", "dark", "WORKBENCH"}) {
            assertThrows(IllegalArgumentException.class, () -> ShellThemes.load(id, path -> { fail("unexpected file access"); return null; }));
        }
        boolean[] closed = {false};
        assertThrows(Exception.class, () -> ShellThemes.load("workbench", path -> new ByteArrayInputStream(new byte[] {'{'}) {
            @Override public void close() { closed[0] = true; }
        }));
        assertTrue(closed[0]);
    }

    @Test public void primaryTextRemainsLegibleAndThemesHaveDistinctLayouts() throws Exception {
        for (var entry : ShellThemes.ENTRIES) {
            var theme = load(entry.id());
            for (UiColor background : List.of(UiColor.BACKGROUND, UiColor.PANEL, UiColor.SURFACE, UiColor.HOVER)) {
                assertTrue(entry.id() + "/" + background, contrast(theme.palette().color(UiColor.TEXT), theme.palette().color(background)) >= 4.5);
            }
            assertTrue(entry.id(), contrast(theme.palette().color(UiColor.MUTED), theme.palette().color(UiColor.PANEL)) >= 4.5);
        }
        var workbench = load("workbench");
        assertEquals(ShellComposition.Presentation.LIST, workbench.composition().start().presentation());
        assertEquals(ShellAppearance.Width.FILL, workbench.composition().panels().get(0).style().length());
        var dock = load("glass-dock");
        assertTrue(dock.backdrop().opacity() < 1 && dock.backdrop().blurRadiusDp() > 0);
        assertTrue(dock.composition().panels().get(0).style().edgeGapDp() > 0);
        assertEquals(List.of(ShellPanel.Edge.TOP, ShellPanel.Edge.BOTTOM),
                load("two-panels").composition().panels().stream().map(ShellPanel::edge).toList());
    }

    @Test public void panelsAndStartFitPhoneTabletAndDesktopAtMultipleDensities() throws Exception {
        for (var entry : ShellThemes.ENTRIES) for (int[] size : new int[][] {{360, 800}, {800, 360}, {960, 540}, {1600, 900}}) {
            for (float density : new float[] {1, 1.5f, 3.25f}) {
                var theme = load(entry.id());
                int width = Math.round(size[0] * density), height = Math.round(size[1] * density);
                var viewport = new DesktopViewport(0, 0, width, height, 0, 0, 0, 0);
                var panels = new ArrayList<PanelGeometry>();
                for (var panel : theme.composition().panels()) {
                    int minimum = 2 * panel.style().paddingDp();
                    for (var item : panel.components()) if (item.visible(false, true, size[0])) {
                        minimum += item.widthDp() > 0 ? item.widthDp() : item.type() == ShellComposition.Kind.SPACER ? 0 : 64;
                    }
                    panels.add(PanelGeometry.resolve(panel, density, width, height, Math.round(64 * density),
                            Math.round((minimum + 5 * 48) * density), Math.round(minimum * density)));
                }
                var layout = new DesktopShellLayout();
                layout.update(viewport, panels, false);
                var output = viewport.contentGeometry();
                var work = layout.snapshot().workArea();
                assertEquals(work, work.intersect(output));
                assertTrue(work.height() > height / 2);
                var start = theme.composition().panelFor(ShellComposition.Kind.START);
                var owner = layout.panelFor(ShellComposition.Kind.START);
                var placement = new ShellPanelPlacement.BesideSurface(owner.request().id(), start.edge(),
                        Math.round(560 * density), Math.round(620 * density), false, 16, 0);
                var binding = layout.bind();
                binding.commit(List.of(new ShellSurface("start", true, ShellSurface.Layer.OVERLAY, ShellSurface.Keyboard.ON_DEMAND,
                        placement.resolve(layout.snapshot()), ShellSurface.Margins.NONE, ShellSurface.Input.CONTENT, List.of())));
                var bounds = binding.surface("start").content();
                assertEquals(entry.id(), bounds, bounds.intersect(work));
                binding.close();
            }
        }
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }
    private static double luminance(int color) {
        double value = 0;
        double[] weights = {.0722, .7152, .2126};
        for (int index = 0; index < 3; index++) {
            double channel = ((color >>> (index * 8)) & 255) / 255.0;
            value += weights[index] * (channel <= .04045 ? channel / 12.92 : Math.pow((channel + .055) / 1.055, 2.4));
        }
        return value;
    }
}
