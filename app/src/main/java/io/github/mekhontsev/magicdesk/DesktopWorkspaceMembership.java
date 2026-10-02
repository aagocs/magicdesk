package io.github.mekhontsev.magicdesk;

import java.util.List;

/**
 * Persisted Desktop workspace membership, independent of who holds Android HOME.
 *
 * <p>Consumers that only ask which Desktop workspaces exist, are active or are
 * releasing read this boundary. Only HOME ownership, its recovery and its
 * diagnostics read {@link DesktopHomeRoleLease} directly. Membership is still
 * stored with the HOME lease record; separating storage belongs to the change
 * that stops external-only sessions from claiming the primary HOME role.
 */
final class DesktopWorkspaceMembership {
    enum Phase {
        PREPARED,
        ACTIVE,
        RELEASING,
        /** Ownership was lost with its process; only recovery may act on it. */
        RECOVERING
    }

    static final class Snapshot {
        final List<DesktopDisplayTarget> targets;
        final DesktopSessionPolicy policy;
        final DesktopCompatibilityPolicy compatibility;
        final Phase phase;
        final int closingDisplayId;

        Snapshot(final List<DesktopDisplayTarget> targets,
                final DesktopSessionPolicy policy,
                final DesktopCompatibilityPolicy compatibility,
                final Phase phase,
                final int closingDisplayId) {
            if (targets == null || targets.isEmpty() || policy == null
                    || compatibility == null || phase == null) {
                throw new IllegalArgumentException("complete workspace membership is required");
            }
            this.targets = List.copyOf(targets);
            this.policy = policy;
            this.compatibility = compatibility;
            this.phase = phase;
            this.closingDisplayId = closingDisplayId;
        }

        boolean isActive() {
            return phase == Phase.ACTIVE;
        }

        boolean matches(final DesktopDisplayTarget target) {
            return target != null && targets.stream().anyMatch(value -> value.sameBinding(target));
        }

        DesktopDisplayTarget targetForDisplay(final int displayId) {
            return targets.stream().filter(target -> target.ownsWorkspace(displayId))
                    .findFirst().orElse(null);
        }

        /** A target of an active session that is not currently closing. */
        DesktopDisplayTarget activeTargetForDisplay(final int displayId) {
            return isActive() && closingDisplayId != displayId ? targetForDisplay(displayId) : null;
        }

        boolean isReleasing(final int displayId) {
            return targetForDisplay(displayId) != null
                    && (phase == Phase.RELEASING || closingDisplayId == displayId);
        }
    }

    private DesktopWorkspaceMembership() {
    }

    /** Current membership, or {@code null} when no Desktop workspace is leased. */
    static Snapshot current() {
        return from(DesktopHomeRoleLease.snapshot());
    }

    /** Current membership only while its session is active. */
    static Snapshot active() {
        final Snapshot membership = current();
        return membership != null && membership.isActive() ? membership : null;
    }

    static boolean exists() {
        return current() != null;
    }

    static boolean isActiveForDisplay(final int displayId) {
        final Snapshot membership = current();
        return membership != null && membership.activeTargetForDisplay(displayId) != null;
    }

    static boolean isReleasingForDisplay(final int displayId) {
        final Snapshot membership = current();
        return membership != null && membership.isReleasing(displayId);
    }

    static Snapshot from(final DesktopHomeRoleLease.State lease) {
        if (lease == null) {
            return null;
        }
        return new Snapshot(lease.targets, lease.policy, lease.compatibility,
                phase(lease.phase), lease.closingDisplayId);
    }

    private static Phase phase(final DesktopHomeRoleLease.Phase phase) {
        return switch (phase) {
            case PREPARED -> Phase.PREPARED;
            case ACTIVE -> Phase.ACTIVE;
            case RELEASING -> Phase.RELEASING;
            case STARTUP_RELINQUISHED -> Phase.RECOVERING;
        };
    }
}
