package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static io.github.mekhontsev.magicdesk.DesktopWorkspaceScene.*;

import org.junit.Test;

public final class DesktopTaskbarVisibilityPolicyTest {
    @Test
    public void localFreeformTaskKeepsTaskbarVisible() {
        assertTrue(DesktopTaskbarVisibilityPolicy.isVisible(
                true, FREEFORM, false));
    }

    @Test
    public void localFullscreenTaskAndLauncherHideTaskbar() {
        assertFalse(DesktopTaskbarVisibilityPolicy.isVisible(
                true, FULLSCREEN, true));
    }

    @Test
    public void visibleFreeformAboveActiveFullscreenKeepsTaskbarVisible() {
        assertTrue(DesktopTaskbarVisibilityPolicy.isVisible(
                false, FREEFORM, false));
    }

    @Test
    public void incompleteLocalSnapshotPreservesLifecycleState() {
        assertFalse(DesktopTaskbarVisibilityPolicy.isVisible(
                true, UNKNOWN, false));
        assertTrue(DesktopTaskbarVisibilityPolicy.isVisible(
                true, UNKNOWN, true));
    }

    @Test
    public void desktopHostAndIdleExternalDesktopShowTaskbar() {
        assertTrue(DesktopTaskbarVisibilityPolicy.isVisible(
                true, HOME, false));
        assertTrue(DesktopTaskbarVisibilityPolicy.isVisible(
                false, UNKNOWN, false));
    }

    @Test
    public void visibleFullscreenHidesTaskbarWithoutActiveFlag() {
        assertFalse(DesktopTaskbarVisibilityPolicy.isVisible(
                false, FULLSCREEN, true));
    }
}
