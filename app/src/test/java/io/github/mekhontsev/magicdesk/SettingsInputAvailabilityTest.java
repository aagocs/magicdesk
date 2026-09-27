package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SettingsInputAvailabilityTest {
    @Test public void keyboardPlacementDoesNotUseDesktopAvailability() throws Exception {
        final String render = RuntimeSourceFixture.methods("SettingsView", "render");
        assertTrue(render.contains("mKeyboardOnAppDisplay.setEnabled(settings != null)"));
        assertFalse(render.contains("mKeyboardOnAppDisplay,"));
    }

    @Test public void systemInputOptionRemainsEditableWithoutDesktop() throws Exception {
        RuntimeSourceFixture.verify("""
                static class Switch {
                    boolean enabled;
                    void setEnabled(boolean value) { enabled = value; }
                    void setChecked(boolean value) {}
                    void setAlpha(float value) {}
                    void setText(int value) {}
                }
                boolean mRendering, mDesktopSettingsAvailable;
                Switch mSystemDesktopMode = new Switch();
                Switch mResetCompatibilityDefaults = new Switch();
                Switch mSystemDesktopModeStatus = new Switch();
                java.util.Map<String, Switch> mCompatibility = java.util.Map.of("test", new Switch());
                public static void verify() {
                    Fixture f = new Fixture();
                    f.renderSystemDesktopMode(false, true, false, 1);
                    check(f.mSystemDesktopMode.enabled, "independent system input option disabled");
                    check(!f.mResetCompatibilityDefaults.enabled && !f.mCompatibility.get("test").enabled,
                            "Desktop compatibility was enabled without Desktop");
                    f.renderSystemDesktopMode(true, false, false, 1);
                    check(!f.mSystemDesktopMode.enabled, "capability ignored");
                    f.renderSystemDesktopMode(true, true, true, 1);
                    check(!f.mSystemDesktopMode.enabled, "pending update remained editable");
                    f.renderSystemDesktopMode(null, true, false, 1);
                    check(!f.mSystemDesktopMode.enabled, "unknown state remained editable");
                    f.mDesktopSettingsAvailable = true;
                    f.renderSystemDesktopMode(true, true, false, 1);
                    check(f.mSystemDesktopMode.enabled && f.mResetCompatibilityDefaults.enabled
                            && f.mCompatibility.get("test").enabled, "Desktop settings did not recover");
                }
                """ + RuntimeSourceFixture.methods("SettingsView", "renderSystemDesktopMode"));
    }
}
