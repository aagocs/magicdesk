package io.github.mekhontsev.magicdesk;

final class ShellLayoutTestSupport {
    static void update(DesktopShellLayout layout, DesktopViewport viewport, int height, boolean autoHide) {
        layout.update(viewport, TaskbarGeometry.resolve(ShellAppearance.Taskbar.defaults(), 1,
                viewport.contentWidth(), viewport.contentHeight(), height, 0, 0), autoHide);
    }
}
