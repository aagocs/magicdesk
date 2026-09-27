package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ShellMotionTest {
    @Test public void wallpaperPolicyRoundTripsIndependentlyOfPanelEffects() throws Exception {
        assertTrue(ShellAppearanceJson.parse("{}").motion().wallpaper());
        var theme = ShellAppearanceJson.parse("{\"motion\":{\"wallpaper\":false,\"panels\":\"slide\"}}");
        assertFalse(theme.motion().wallpaper());
        assertEquals(ShellMotion.Effect.SLIDE, theme.motion().panels());
        assertEquals(theme, ShellAppearanceJson.parse(ShellAppearanceJson.encode(theme).toString()));
        assertThrows(IllegalArgumentException.class, () -> ShellAppearanceJson.parse("{\"motion\":{\"wallpaper\":\"true\"}}"));
    }
    @Test public void allEffectsRoundTripThroughPublishedSchema() throws Exception {
        for (var effect : ShellMotion.Effect.values()) {
            var theme = ShellAppearanceJson.parse("{\"motion\":{\"panels\":\"" + effect.name().toLowerCase(java.util.Locale.ROOT)
                    + "\",\"taskbar\":\"slide_scale\",\"distanceDp\":32,\"scaleFrom\":0.85,\"durationMs\":400}}");
            assertEquals(effect, theme.motion().panels());
            assertEquals(32, theme.motion().distanceDp());
            assertEquals(.85f, theme.motion().scaleFrom(), 0);
            var encoded = ShellAppearanceJson.encode(theme);
            ShellAppearanceSchema.validate(encoded);
            assertEquals(theme, ShellAppearanceJson.parse(encoded.toString()));
            assertEquals(0, theme.motion().duration(effect, false));
            var reduced = new ShellMotion(true, effect, effect, 400, 0, ShellMotion.Curve.LINEAR, 32, .85f, true);
            assertEquals(0, reduced.duration(effect, true));
        }
    }

    @Test public void rejectsOutOfRangeAndWrongTypes() throws Exception {
        for (String fields : new String[] {"\"distanceDp\":-1", "\"distanceDp\":33", "\"distanceDp\":1.5",
                "\"distanceDp\":\"12\"", "\"scaleFrom\":0.849", "\"scaleFrom\":1.001", "\"scaleFrom\":null",
                "\"scaleFrom\":\"0.9\"", "\"panels\":\"spin\""}) {
            assertThrows(fields, IllegalArgumentException.class, () -> ShellAppearanceJson.parse("{\"motion\":{" + fields + "}}"));
        }
        assertEquals(12, ShellAppearanceJson.parse("{}").motion().distanceDp());
        assertEquals(.96f, ShellAppearanceJson.parse("{}").motion().scaleFrom(), 0);
    }
}
