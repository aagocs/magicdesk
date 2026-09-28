package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ShellDesktopTaskOwnershipTest {
    @Test public void freeformResidencyClaimsTasksOnEveryWorkspaceDisplay() {
        for (int display : new int[]{0, 4, 61}) {
            var ownership = new ShellDesktopTaskOwnership();
            ownership.configure(display);
            ownership.observeStandardTaskState(display, display, 41, 1, 1);
            ownership.observeStandardTaskState(display, display, 42, 2, 1);
            ownership.observeStandardTaskState(display, display, 43, 6, 1);
            assertArrayEquals(new int[0], ownership.desktopTaskIds());
            ownership.observeStandardTaskState(display, display, 41, 5, 2);
            ownership.observeStandardTaskState(display, display, 44, 5, 2);
            assertArrayEquals(new int[]{41, 44}, ownership.desktopTaskIds());
            ownership.observeStandardTaskState(display, display, 41, 1, 3);
            ownership.observeStandardTaskState(display, display, 44, 2, 3);
            assertArrayEquals(new int[]{41, 44}, ownership.desktopTaskIds());
        }
    }

    @Test public void displayDepartureAndSessionStopReleaseOwnership() {
        var ownership = new ShellDesktopTaskOwnership();
        ownership.configure(4);
        ownership.markDesktopHost(40);
        ownership.observeStandardTaskState(4, 4, 41, 5, 1);
        ownership.observeStandardTaskState(4, 0, 42, 5, 1);
        ownership.observeStandardTaskState(0, 0, 43, 5, 1);
        assertArrayEquals(new int[]{40, 41}, ownership.desktopTaskIds());
        ownership.onTaskDisplayChanged(41, 0);
        assertFalse(ownership.isRememberedDesktopTask(41));
        ownership.observeStandardTaskState(4, 4, 41, 5, 2);
        assertTrue(ownership.isRememberedDesktopTask(41));
        ownership.configure(-1);
        ownership.observeStandardTaskState(4, 4, 42, 5, 3);
        assertArrayEquals(new int[0], ownership.desktopTaskIds());
        assertFalse(ownership.isDesktopHostTask(40));
    }

    @Test public void hostIdentityAndMembershipSurviveBoundsReconfiguration() {
        var ownership = new ShellDesktopTaskOwnership();
        ownership.configure(0);
        ownership.markDesktopHost(40);
        ownership.markDesktop(41);
        ownership.configure(0);
        assertTrue(ownership.isDesktopHostTask(40));
        assertArrayEquals(new int[]{40, 41}, ownership.desktopTaskIds());
        ownership.forget(40);
        assertFalse(ownership.isDesktopHostTask(40));
        ownership.configure(4);
        assertArrayEquals(new int[0], ownership.desktopTaskIds());
    }

    @Test public void releaseIgnoresPreHandoffSamplesButAllowsNewFreeformResidency() {
        var ownership = new ShellDesktopTaskOwnership();
        ownership.configure(4);
        ownership.markDesktop(41);
        ownership.beginRelease(new int[]{41});
        ownership.observeStandardTaskState(4, 4, 41, 5, 10);
        assertFalse(ownership.isRememberedDesktopTask(41));
        ownership.finishRelease(new int[]{41}, 12);
        ownership.observeStandardTaskState(4, 4, 41, 5, 11);
        assertFalse(ownership.isRememberedDesktopTask(41));
        ownership.observeStandardTaskState(4, 4, 41, 1, 12);
        assertFalse(ownership.isRememberedDesktopTask(41));
        ownership.observeStandardTaskState(4, 4, 41, 5, 13);
        assertTrue(ownership.isRememberedDesktopTask(41));
    }

    @Test public void freshFreeformSampleIsAdoptedEvenWithoutIntermediateFullscreenSample() {
        var ownership = new ShellDesktopTaskOwnership();
        ownership.configure(0);
        ownership.markDesktop(41);
        ownership.beginRelease(new int[]{41});
        ownership.finishRelease(new int[]{41}, 12);
        ownership.observeStandardTaskState(0, 0, 41, 5, 12);
        assertTrue(ownership.isRememberedDesktopTask(41));
    }

    @Test public void failedReleaseAndExplicitLaunchClearTheAdmissionBarrier() {
        var ownership = new ShellDesktopTaskOwnership();
        ownership.configure(4);
        ownership.markDesktop(41);
        ownership.beginRelease(new int[]{41});
        ownership.markDesktop(41);
        ownership.finishRelease(new int[]{41}, 12);
        assertTrue(ownership.isRememberedDesktopTask(41));
        ownership.forget(41);
        ownership.observeStandardTaskState(4, 4, 41, 5, 11);
        assertTrue(ownership.isRememberedDesktopTask(41));
    }

    @Test public void rememberedIdentityStillRequiresTheOwningDisplay() {
        assertTrue(ShellDesktopTaskOwnership.isDesktopOwnedTask(true, true));
        assertFalse(ShellDesktopTaskOwnership.isDesktopOwnedTask(true, false));
        assertFalse(ShellDesktopTaskOwnership.isDesktopOwnedTask(false, true));
        assertFalse(ShellDesktopTaskOwnership.isDesktopOwnedTask(false, false));
    }
}
