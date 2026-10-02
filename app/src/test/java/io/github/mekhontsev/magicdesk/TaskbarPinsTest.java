package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class TaskbarPinsTest {
    /** Stand-in for a profile-scoped AppReference: equal package, different profile. */
    private record Ref(int userId, String packageName) {
    }

    private static final Ref PERSONAL = new Ref(0, "example.app");
    private static final Ref WORK = new Ref(10, "example.app");

    @Test
    public void menuLabelAndToggleAgreeAcrossRepeatedToggles() {
        final List<Ref> pinned = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final boolean before = TaskbarPins.isPinned(pinned, PERSONAL);
            assertEquals(i % 2 == 1, before);
            assertEquals(!before, TaskbarPins.toggle(pinned, PERSONAL));
            assertEquals(!before, TaskbarPins.isPinned(pinned, PERSONAL));
        }
        assertTrue(pinned.isEmpty());
    }

    @Test
    public void samePackageInAnotherProfileIsADistinctPin() {
        final List<Ref> pinned = new ArrayList<>(List.of(PERSONAL));
        assertTrue(TaskbarPins.isPinned(pinned, PERSONAL));
        assertFalse(TaskbarPins.isPinned(pinned, WORK));
        assertTrue(TaskbarPins.toggle(pinned, WORK));
        assertEquals(List.of(PERSONAL, WORK), pinned);
        assertFalse(TaskbarPins.toggle(pinned, PERSONAL));
        assertTrue(TaskbarPins.isPinned(pinned, WORK));
        assertFalse(TaskbarPins.isPinned(pinned, PERSONAL));
    }

    @Test
    public void productionCallersUseTheSharedReferenceIdentity() throws Exception {
        final String root = "app/src/main/java/io/github/mekhontsev/magicdesk/";
        final java.nio.file.Path base = java.nio.file.Files.exists(
                java.nio.file.Path.of(root)) ? java.nio.file.Path.of(root)
                : java.nio.file.Path.of("src/main/java/io/github/mekhontsev/magicdesk/");
        final String menu = java.nio.file.Files.readString(
                base.resolve("DesktopContextMenuController.java"));
        final String taskbar = java.nio.file.Files.readString(
                base.resolve("TaskbarController.java"));
        assertTrue(menu.contains("TaskbarPins.isPinned(\n                    mActivity.getPinnedApps(), state.app.reference)"));
        assertFalse(menu.contains("getPinnedApps()\n                    .contains("));
        assertTrue(taskbar.contains("TaskbarPins.toggle(pinned, app.reference)"));
    }
}
