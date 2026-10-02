package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DesktopMarqueeIntegrationContractTest {
    private static final Path SOURCES =
            Path.of("src/main/java/io/github/mekhontsev/magicdesk");

    @Test
    public void rootGestureKeepsBackgroundAndInputRoutingOwners() throws IOException {
        final String shell = Files.readString(
                SOURCES.resolve("DesktopShellActivity.java"));
        assertTrue(shell.contains(
                "mInputController.handleTouchEvent(event, true)"));
        assertTrue(shell.contains("isInsideGrid(desktopIcons, gridPoint)"));
        assertTrue(shell.contains("isPrimaryMarqueePointer(event)"));
        assertTrue(shell.contains("marqueeGesture.move("));
        assertTrue(shell.contains("desktopGestures.onTouchEvent(cancelGesture)"));
        assertTrue(shell.contains("desktopGestures.onTouchEvent(event)"));
        assertTrue(shell.contains("mDesktopWorkspaceController.clearFileSelection()"));
        assertTrue(shell.contains("showDesktopContextMenu(event.getRawX(), event.getRawY())"));
    }

    @Test
    public void selectionUsesRenderedFileBoundsAndKeepsGridDropDispatch() throws IOException {
        final String workspace = Files.readString(
                SOURCES.resolve("DesktopWorkspaceController.java"));
        assertTrue(workspace.contains("visibleFileItemIds()"));
        assertTrue(workspace.contains("itemId.startsWith(FILE_PREFIX)"));
        assertTrue(workspace.contains("item.getLeft(), item.getTop(),"));
        assertTrue(workspace.contains("item.getRight(), item.getBottom()"));
        assertTrue(workspace.contains("mFileSelection.selectMarquee("));
        assertTrue(workspace.contains("updateRenderedSelection()"));

        final String grid = Files.readString(SOURCES.resolve("DesktopGridLayout.java"));
        assertTrue(grid.contains("setOnDragListener((view, event) -> handleDrag(event, 0, 0))"));
        assertTrue(grid.contains("setOnDragListener((target, event) -> handleDrag("));
        assertTrue(grid.contains("super.dispatchDraw(canvas)"));
        assertTrue(grid.contains("canvas.drawRect(mMarqueeBounds, mMarqueeFill)"));
        assertTrue(grid.indexOf("super.dispatchDraw(canvas)")
                < grid.indexOf("canvas.drawRect(mMarqueeBounds, mMarqueeFill)"));
    }
}
