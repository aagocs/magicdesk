package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TouchpadAppearanceTest {
    @Test public void inputSurfaceAndToolbarAreIndependentOfThemeRefresh() throws Exception {
        String content = RuntimeSourceFixture.methods("MagicDeskTouchpadActivity", "createContent", "onCreate");
        assertTrue(content.contains("host.setBackgroundColor(Color.BLACK)"));
        assertTrue(content.contains("root.setBackgroundColor(Color.BLACK)"));
        assertTrue(content.contains("touchSurface.setBackgroundColor(Color.BLACK)"));
        assertFalse(content.contains("UiAppearance.background"));
        assertFalse(content.contains("UiAppearance.image"));
        String button = RuntimeSourceFixture.methods("MagicDeskTouchpadActivity", "touchpadIcon");
        assertTrue(button.contains("state_enabled"));
        assertTrue(button.contains("state_selected"));
        assertTrue(button.contains("Color.WHITE"));
    }
}
