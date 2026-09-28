package io.github.mekhontsev.magicdesk;

import android.content.res.Configuration;
import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LinuxAppearanceTest {
    @Test public void systemPreferenceDoesNotDependOnDesktopOrShellPalette() {
        assertEquals(1, LinuxAppearance.resolve(Configuration.UI_MODE_TYPE_DESK | Configuration.UI_MODE_NIGHT_YES));
        assertEquals(2, LinuxAppearance.resolve(Configuration.UI_MODE_NIGHT_NO));
        assertEquals(0, LinuxAppearance.resolve(Configuration.UI_MODE_NIGHT_UNDEFINED));
    }

    @Test public void wholeDesktopHasNoSettingsOwnerOrClientWrapper() {
        var launch = new LinuxAppearanceLaunch("/lib", false, false, 1);
        var env = new HashMap<String, String>();
        launch.configure(env);
        assertTrue(env.isEmpty());
        assertEquals("", launch.exports());
        assertEquals("exec desktop", launch.command("exec desktop", "/bin/sh"));
    }

    @Test public void clientsUseAnIsolatedReadOnlySubscriptionAndPreserveCommandSyntax() {
        var launch = new LinuxAppearanceLaunch("/lib ' files", true, false, 2);
        var env = new HashMap<String, String>();
        launch.configure(env);
        assertEquals("2", env.get("MAGICDESK_COLOR_SCHEME"));
        assertTrue(env.get("MAGICDESK_APPEARANCE_TOKEN").matches("[0-9a-f]{64}"));
        String command = "printf '%s' \"$HOME\"; exec app --arg='value'";
        assertEquals("exec " + ShellCommandLine.quote("/lib ' files/libmagicdesk_linux_settings.so")
                + " -- '/bin/sh' -c " + ShellCommandLine.quote(command), launch.command(command, "/bin/sh"));
        assertFalse(launch.exports().contains("GTK_THEME"));
        var guest = new LinuxAppearanceLaunch("/lib", true, true, 1);
        assertEquals(command, guest.command(command, "/bin/sh"));
        assertFalse(guest.exports().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new LinuxAppearanceLaunch("/lib", true, false, 3));
    }

    @Test public void lifecycleUsesConfigurationEventsAndReleasesTheSubscription() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(RuntimeSourceFixture.MAIN, "LinuxAppearance.java"));
        assertTrue(source.contains("registerComponentCallbacks(this)"));
        assertTrue(source.contains("unregisterComponentCallbacks(this)"));
        assertTrue(source.contains("value != current"));
        assertFalse(source.contains("postDelayed"));
        assertFalse(source.contains("ShellAccess"));
    }
}
