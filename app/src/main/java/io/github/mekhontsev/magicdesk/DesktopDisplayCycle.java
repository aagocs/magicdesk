package io.github.mekhontsev.magicdesk;

import java.util.Collection;
import java.util.List;

/** Stable previous/next Desktop display order for keyboard moves; Android exposes no arrangement. */
final class DesktopDisplayCycle {
    private DesktopDisplayCycle() {
    }

    /** Returns the adjacent workspace display, or -1 when there is no other one. */
    static int adjacent(final int currentDisplayId, final Collection<Integer> workspaceDisplayIds,
            final boolean previous) {
        final List<Integer> displays = workspaceDisplayIds == null ? List.of()
                : workspaceDisplayIds.stream().filter(id -> id != null && id >= 0)
                        .distinct().sorted().toList();
        final int position = displays.indexOf(currentDisplayId);
        if (position < 0) {
            return displays.isEmpty() ? -1
                    : previous ? displays.get(displays.size() - 1) : displays.get(0);
        }
        if (displays.size() < 2) {
            return -1;
        }
        return displays.get(Math.floorMod(position + (previous ? -1 : 1), displays.size()));
    }
}
