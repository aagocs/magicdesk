package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class DesktopWorkspaceMembershipTest {
    private static final DesktopDisplayTarget PHONE = DesktopDisplayTarget.phone();
    private static final DesktopDisplayTarget EXTERNAL = DesktopDisplayTarget.simulated(7);

    @Test
    public void absentLeaseHasNoMembership() {
        assertNull(DesktopWorkspaceMembership.from(null));
    }

    @Test
    public void mapsEveryLeasePhaseWithoutHomeOwnershipDetail() {
        assertEquals(DesktopWorkspaceMembership.Phase.PREPARED,
                membership(DesktopHomeRoleLease.Phase.PREPARED, -1).phase);
        assertEquals(DesktopWorkspaceMembership.Phase.ACTIVE,
                membership(DesktopHomeRoleLease.Phase.ACTIVE, -1).phase);
        assertEquals(DesktopWorkspaceMembership.Phase.RELEASING,
                membership(DesktopHomeRoleLease.Phase.RELEASING, -1).phase);
        assertEquals(DesktopWorkspaceMembership.Phase.RECOVERING,
                membership(DesktopHomeRoleLease.Phase.STARTUP_RELINQUISHED, -1).phase);
    }

    @Test
    public void carriesSessionIdentityFromLease() {
        final DesktopWorkspaceMembership.Snapshot membership =
                membership(DesktopHomeRoleLease.Phase.ACTIVE, -1);

        assertEquals(List.of(PHONE, EXTERNAL), membership.targets);
        assertSame(DesktopSessionPolicy.USER, membership.policy);
        assertSame(DesktopCompatibilityPolicy.NONE, membership.compatibility);
        assertTrue(membership.matches(EXTERNAL));
        assertFalse(membership.matches(DesktopDisplayTarget.simulated(8)));
        assertSame(EXTERNAL, membership.targetForDisplay(7));
        assertNull(membership.targetForDisplay(8));
    }

    @Test
    public void closingWorkspaceIsReleasingButNotActive() {
        final DesktopWorkspaceMembership.Snapshot membership =
                membership(DesktopHomeRoleLease.Phase.ACTIVE, 7);

        assertTrue(membership.isActive());
        assertNull(membership.activeTargetForDisplay(7));
        assertTrue(membership.isReleasing(7));
        assertSame(PHONE, membership.activeTargetForDisplay(0));
        assertFalse(membership.isReleasing(0));
    }

    @Test
    public void onlyActivePhaseExposesActiveTargets() {
        for (final DesktopHomeRoleLease.Phase phase : DesktopHomeRoleLease.Phase.values()) {
            final DesktopWorkspaceMembership.Snapshot membership = membership(phase, -1);
            assertEquals(phase == DesktopHomeRoleLease.Phase.ACTIVE,
                    membership.activeTargetForDisplay(7) != null);
        }
    }

    @Test
    public void releasingPhaseReleasesEveryOwnedWorkspaceOnly() {
        final DesktopWorkspaceMembership.Snapshot membership =
                membership(DesktopHomeRoleLease.Phase.RELEASING, -1);

        assertTrue(membership.isReleasing(0));
        assertTrue(membership.isReleasing(7));
        assertFalse(membership.isReleasing(8));
    }

    @Test
    public void onlyHomeOwnershipReadsTheHomeLeaseDirectly() throws IOException {
        // Membership consumers use DesktopWorkspaceMembership so the primary HOME
        // role can become a phone-only sub-lease without touching them again.
        final Set<String> allowed = Set.of(
                "CompatibilityDiagnostics", // HOME-ROLE-001 previous launcher report
                "DesktopAutomationStateReader", // published homeLease state
                "DesktopHomeStartupGuard", // stale HOME relinquishment
                "DesktopSelfTestCleanup", // HOME restoration assertion
                "DesktopTaskWatcher", // phone Recents to MagicDesk HOME routing
                "DesktopWorkspaceMembership",
                "FullscreenStartController", // previous launcher for phone HOME recents
                "RuntimeDesktopSessionCoordinator"); // HOME lease reconciliation
        final Set<String> readers = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/io/github/mekhontsev/magicdesk"))) {
            for (final Path file : (Iterable<Path>) files::iterator) {
                if (file.toString().endsWith(".java")
                        && Files.readString(file).contains("DesktopHomeRoleLease.snapshot()")) {
                    readers.add(file.getFileName().toString().replace(".java", ""));
                }
            }
        }
        readers.removeAll(allowed);
        assertEquals(Set.of(), readers);
    }

    private static DesktopWorkspaceMembership.Snapshot membership(
            final DesktopHomeRoleLease.Phase phase, final int closingDisplayId) {
        return DesktopWorkspaceMembership.from(new DesktopHomeRoleLease.State(
                0, AndroidHomeSelection.unresolved("com.example.launcher"),
                "com.example.launcher/.SecondaryHome", List.of(PHONE, EXTERNAL),
                DesktopSessionPolicy.USER, DesktopCompatibilityPolicy.NONE, phase,
                closingDisplayId));
    }
}
