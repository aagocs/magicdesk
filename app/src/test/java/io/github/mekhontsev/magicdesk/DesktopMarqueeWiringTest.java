package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Source contracts that keep rubber-band selection from taking over other desktop gestures. */
public final class DesktopMarqueeWiringTest {
    private static String read(final String name) throws IOException {
        return Files.readString(Path.of("src/main/java/io/github/mekhontsev/magicdesk/" + name));
    }

    @Test
    public void theControllerObservesWithoutConsumingOrAddingGridChildren() throws IOException {
        final String controller = read("DesktopMarqueeController.java");
        assertTrue(controller.contains("void onTouch(final MotionEvent event)"));
        assertFalse("must never consume events", controller.contains("boolean onTouch("));
        assertFalse("overlay only, no layout children", controller.contains("addView("));
        assertTrue(controller.contains("getOverlay().add("));
    }

    @Test
    public void onlyAPrimaryMouseDragOnEmptyGridSpaceStartsTheGesture() throws IOException {
        final String controller = read("DesktopMarqueeController.java");
        assertTrue(controller.contains("event.isFromSource(InputDevice.SOURCE_MOUSE)"));
        assertTrue(controller.contains("(buttons & MotionEvent.BUTTON_PRIMARY) != 0"));
        assertTrue(controller.contains("(buttons & ~MotionEvent.BUTTON_PRIMARY) == 0"));
        assertTrue(controller.contains("coversAnyChild(grid, x, y)"));
    }

    @Test
    public void theRootListenerObservesAfterTheContextButtonAndBeforeTheGestureDetector()
            throws IOException {
        final String shell = read("DesktopShellActivity.java");
        final int context = shell.indexOf("mInputController.handleTouchEvent(event, true)) return true;");
        final int observe = shell.indexOf("mDesktopWorkspaceController.observeBackgroundPointer(event);");
        final int detector = shell.indexOf("desktopGestures.onTouchEvent(event)");
        assertTrue(context > 0 && observe > context && detector > observe);
    }

    @Test
    public void marqueeSelectionOnlyTouchesFileItemsThroughTheHostSeam() throws IOException {
        final String workspace = read("DesktopWorkspaceController.java");
        assertTrue(workspace.contains("return itemId.startsWith(FILE_PREFIX);"));
        assertTrue(workspace.contains("mFileSelection.setSelection(itemIds);\n                    updateRenderedSelection();"));
    }
}
