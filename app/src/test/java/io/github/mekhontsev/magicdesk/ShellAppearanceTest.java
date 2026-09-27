package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public final class ShellAppearanceTest {
    @Test public void defaultsAndPresetsRoundTripWithoutAndroid() throws Exception {
        assertEquals(ShellAppearance.defaults(), ShellAppearanceJson.parse("{}"));
        for (String preset : new String[] {"dark", "light", "contrast"}) {
            final var value = floating(ShellAppearance.preset(preset));
            assertEquals(value, ShellAppearanceJson.parse(ShellAppearanceJson.encode(value).toString()));
        }
    }
    @Test public void partialDocumentInheritsDefaultsAndNeverMergesPreviousState() throws Exception {
        var value = ShellAppearanceJson.parse("""
                {"colors":{"accent":"#123456"},"composition":{"panels":[
                  {"id":"main","style":{"edgeGapDp":12},"components":[{"type":"start"}]}]}}
                """);
        assertEquals(0xff123456, value.palette().color(UiColor.ACCENT));
        assertEquals(ShellAppearance.defaults().typography(), value.typography());
        assertEquals(12, value.composition().panels().get(0).style().edgeGapDp());
        assertEquals(ShellAppearance.Width.FILL, value.composition().panels().get(0).style().length());
        assertEquals(ShellPanel.Edge.BOTTOM, value.composition().panels().get(0).edge());
        assertEquals(ShellAppearance.defaults(), ShellAppearanceJson.parse("{}"));
    }
    @Test public void presetSelectionPreservesGeometryAndSnapshotIsImmutable() {
        var source = floating(ShellAppearance.defaults());
        var target = source.withStyle(ShellAppearance.preset("light"));
        assertEquals(source.composition(), target.composition());
        assertNotEquals(source.palette(), target.palette());
        assertThrows(UnsupportedOperationException.class, () -> target.palette().colors().put(UiColor.TEXT, 0));
    }
    @Test public void rejectsInvalidOrExecutableFieldsAtomically() {
        for (String invalid : new String[] {"{\"command\":\"id\"}", "{\"version\":1}",
                "{\"colors\":{\"text\":\"#00ffffff\"}}", "{\"colors\":{\"transparent\":\"#ffffff\"}}",
                "{\"colors\":{\"unknown\":\"#ffffff\"}}", "{\"typography\":{\"scale\":8}}",
                "{\"typography\":{\"font\":\"/sdcard/font.ttf\"}}", "{\"taskbar\":{\"opacity\":0}}",
                "{\"composition\":{\"taskbar\":[]}}", "{\"shape\":{\"borderDp\":null}}",
                "{\"preset\":null}", "{\"typography\":{\"font\":null}}",
                "{\"colors\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}",
                "{} trailing", " ".repeat(32769)}) {
            assertThrows(invalid, Exception.class, () -> ShellAppearanceJson.parse(invalid));
        }
    }

    @Test public void invalidPanelStylesAreRejectedAtTheirCurrentJsonBoundary() {
        for (String invalid : new String[] {"\"opacity\":0", "\"maxLengthDp\":100000000",
                "\"sideGapDp\":1.5", "\"reserveSpace\":\"true\"", "\"edgeGapDp\":-1",
                "\"thicknessDp\":1", "\"length\":\"wide\"", "\"bottomGapDp\":12", "\"width\":\"fill\""}) {
            final var json = "{\"composition\":{\"panels\":[{\"id\":\"main\",\"components\":[{\"type\":\"start\"}],\"style\":{" + invalid + "}}]}}";
            assertThrows(invalid, Exception.class, () -> ShellAppearanceJson.parse(json));
        }
    }

    @Test public void multiplePanelEdgesAndStylesRoundTripWithoutFlattening() throws Exception {
        final var panels = List.of(
                ShellLayoutTestSupport.panel("main", ShellPanel.Edge.LEFT, ShellAppearance.PanelStyle.floating(), ShellComposition.Kind.START),
                ShellLayoutTestSupport.panel("status", ShellPanel.Edge.TOP, ShellAppearance.PanelStyle.defaults(), ShellComposition.Kind.CLOCK));
        final var appearance = ShellAppearance.defaults().withComposition(new ShellComposition(panels, ShellComposition.Start.defaults()));
        assertEquals(appearance, ShellAppearanceJson.parse(ShellAppearanceJson.encode(appearance).toString()));
    }

    private static ShellAppearance floating(ShellAppearance appearance) {
        final var composition = appearance.composition();
        return appearance.withComposition(new ShellComposition(List.of(
                composition.panels().get(0).withStyle(ShellAppearance.PanelStyle.floating())), composition.start()));
    }
}
