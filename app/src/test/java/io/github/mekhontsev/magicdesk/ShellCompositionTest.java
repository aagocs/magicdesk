package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import org.json.JSONObject;
import java.util.List;
import static org.junit.Assert.*;

public final class ShellCompositionTest {
    @Test public void allDocumentedExamplesUseTheProductionValidator() throws Exception {
        var directory = java.nio.file.Path.of("../docs/themes");
        try (var files = java.nio.file.Files.list(directory)) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var theme = ShellAppearanceJson.parse(java.nio.file.Files.readString(file));
                assertEquals(theme, ShellAppearanceJson.parse(ShellAppearanceJson.encode(theme).toString()));
            }
        }
    }
    @Test public void resolvedDocumentsValidateAgainstThePublishedSchema() throws Exception {
        for (String preset : new String[] {"dark", "light", "contrast"}) {
            var original = ShellAppearance.preset(preset);
            var json = ShellAppearanceJson.encode(original);
            ShellAppearanceSchema.validate(json);
            assertEquals(original, ShellAppearanceJson.parse(json.toString()));
        }
        JSONObject schema = ShellAppearanceSchema.document();
        schema.getJSONObject("properties").remove("colors");
        assertTrue(ShellAppearanceSchema.document().getJSONObject("properties").has("colors"));
    }
    @Test public void componentsAndMotionRoundTrip() throws Exception {
        var theme = ShellAppearanceJson.parse("""
                {"version":2,"composition":{"taskbar":[
                  {"type":"clock","clock":"date_time","widthDp":180},
                  {"type":"spacer"},{"type":"start","label":"Launch"},
                  {"type":"tasks"},{"type":"spacer"},
                  {"type":"quick_controls","minViewportDp":600}],
                  "start":{"sections":["apps","recent"],"presentation":"list","tileWidthDp":120,"iconSizeDp":32}},
                  "motion":{"panels":"fade","taskbar":"fade","feedbackMs":80,"curve":"smooth"},
                  "feedback":{"hover":"accent"},"resources":{"icons":{"desktop":"windows"}}}
                """);
        assertEquals(theme, ShellAppearanceJson.parse(ShellAppearanceJson.encode(theme).toString()));
        assertEquals(ShellResources.Icon.WINDOWS, theme.resources().resolve(ShellResources.Icon.DESKTOP));
        assertEquals(UiColor.ACCENT, theme.feedback().hover());
        assertEquals(theme.composition(), theme.withStyle(ShellAppearance.preset("light")).composition());
        assertEquals(theme.resources(), theme.withStyle(ShellAppearance.preset("light")).resources());
        assertThrows(UnsupportedOperationException.class, () -> theme.composition().taskbar().clear());
    }
    @Test public void errorsIdentifyTheExactBoundary() throws Exception {
        invalid("{\"composition\":{\"taskbar\":[{\"type\":\"exec\"}]}}", "/composition/taskbar/0/type");
        invalid("{\"composition\":{\"taskbar\":[{\"type\":\"clock\",\"label\":\"x\"}]}}", "/composition/taskbar/0/label");
        invalid("{\"composition\":{\"taskbar\":[{\"type\":\"start\",\"clock\":\"time\"}]}}", "/composition/taskbar/0/clock");
        invalid("{\"composition\":{\"taskbar\":[{\"type\":\"clock\"},{\"type\":\"clock\"}]}}", "/composition/taskbar/1/type");
        invalid("{\"composition\":{\"taskbar\":[{\"type\":\"clock\",\"widthDp\":1}]}}", "/composition/taskbar/0/widthDp");
        invalid("{\"composition\":{\"start\":{\"sections\":[\"recent\"]}}}", "/composition/start/sections");
        invalid("{\"composition\":{\"start\":{\"sections\":[\"apps\",\"apps\"]}}}", "/composition/start/sections/1");
        invalid("{\"motion\":{\"durationMs\":500}}", "/motion/durationMs");
        invalid("{\"motion\":{\"panels\":\"script\"}}", "/motion/panels");
        invalid("{\"resources\":{\"icons\":{\"desktop\":\"/sdcard/icon.svg\"}}}", "/resources/icons/desktop");
        invalid("{\"shape\":{\"wrong\":1}}", "/shape/wrong");
        invalid("{\"shape\":{\"borderDp\":null}}", "/shape/borderDp");
    }
    @Test public void conditionsAndSpaceAllocationHaveNoRuntimeSideEffects() {
        var phone = ShellComposition.Component.of(ShellComposition.Kind.PHONE_SCREEN);
        assertFalse(phone.visible(false, false, 1200));
        assertFalse(phone.visible(true, true, 1200));
        assertTrue(phone.visible(false, true, 1200));
        var slots = List.of(new ShellComponentLayout.Slot(48, false), new ShellComponentLayout.Slot(48, true),
                new ShellComponentLayout.Slot(0, true), new ShellComponentLayout.Slot(72, false));
        assertArrayEquals(new int[] {48, 114, 66, 72}, ShellComponentLayout.widths(slots, 300));
        assertArrayEquals(new int[] {48, 48, 0, 72}, ShellComponentLayout.widths(slots, 100));
        assertEquals(0, ShellComponentLayout.widths(List.of(), 100).length);
    }
    @Test public void reducedAndSystemDisabledAnimationsAlwaysResolveToZero() {
        var motion = new ShellMotion(false, ShellMotion.Effect.FADE, ShellMotion.Effect.FADE, 180, 60, ShellMotion.Curve.SMOOTH);
        assertEquals(180, motion.duration(motion.panels(), true));
        assertEquals(0, motion.duration(motion.panels(), false));
        assertEquals(0, motion.duration(ShellMotion.Effect.NONE, true));
        assertEquals(0, new ShellMotion(true, motion.panels(), motion.taskbar(), 180, 60, motion.curve()).duration(motion.panels(), true));
    }
    private static void invalid(String document, String path) {
        Exception error = assertThrows(Exception.class, () -> ShellAppearanceJson.parse(document));
        assertTrue(error.getMessage(), error.getMessage().startsWith(path + ":"));
    }
}
