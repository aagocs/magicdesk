package io.github.mekhontsev.magicdesk;

import java.util.List;

/**
 * Pin membership decisions shared by the taskbar and its context menu. Generic so both
 * arguments must share one identity type; an {@code Object} lookup would silently compare a
 * package name against profile-scoped references.
 */
final class TaskbarPins {
    private TaskbarPins() {
    }

    static <T> boolean isPinned(final List<T> pinned, final T identity) {
        return pinned.contains(identity);
    }

    /** Flips membership in place and returns the new pinned state. */
    static <T> boolean toggle(final List<T> pinned, final T identity) {
        if (pinned.remove(identity)) {
            return false;
        }
        pinned.add(identity);
        return true;
    }
}
