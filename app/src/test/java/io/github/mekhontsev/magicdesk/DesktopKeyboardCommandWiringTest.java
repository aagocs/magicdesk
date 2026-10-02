package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source contracts for which shared file commands the desktop surface acts on. */
public final class DesktopKeyboardCommandWiringTest {
    private static String handler() throws IOException {
        final String source = Files.readString(Path.of(
                "src/main/java/io/github/mekhontsev/magicdesk/DesktopWorkspaceController.java"));
        final int start = source.indexOf("boolean handleKeyboardCommand(");
        final int end = source.indexOf("void copyFilePath(", start);
        assertTrue(start > 0 && end > start);
        return source.substring(start, end);
    }

    @Test
    public void newFolderReusesTheContextMenuActionOnTheDesktopSurface() throws IOException {
        final String handler = handler();
        final int newFolder = handler.indexOf("case NEW_FOLDER:");
        assertTrue(newFolder > 0);
        final String body = handler.substring(newFolder, handler.indexOf("default:", newFolder));
        assertTrue(body.contains("mActivity.createDesktopFile(true);"));
        assertTrue(body.contains("return true;"));
    }

    @Test
    public void commandsWithNoDesktopTargetStayUnhandled() throws IOException {
        final String handler = handler();
        for (final String command : new String[] {
                "case FIND:", "case FOCUS_LOCATION:", "case TOGGLE_HIDDEN:",
                "case NEW_WINDOW:", "case UP:"}) {
            assertFalse(command, handler.contains(command));
        }
    }
}
