package io.github.mekhontsev.magicdesk;

import java.util.List;

/** Desktop shell policy expressed as layout intents; contains no Android window operations. */
final class DesktopShellLayout {
    static final String TASKBAR = "taskbar";
    private final ShellLayoutScope mScope = new ShellLayoutScope();
    private ShellLayoutScope.Binding mTaskbar = mScope.bind();
    private DesktopViewport mViewport;
    private TaskbarGeometry mGeometry;
    private boolean mAutoHide;

    void update(final DesktopViewport viewport, final TaskbarGeometry geometry, final boolean autoHide) {
        if (viewport.equals(mViewport) && geometry.equals(mGeometry) && autoHide == mAutoHide) {
            return;
        }
        final ShellSurface taskbar = new ShellSurface(TASKBAR, true, ShellSurface.Layer.TOP,
                ShellSurface.Keyboard.NONE,
                new ShellSurface.Placement(ShellSurface.Reference.CONTENT,
                        geometry.anchors(), geometry.width(), geometry.height(), geometry.margins()),
                new ShellSurface.Margins(0, 0, 0, geometry.extendNavigation() ? viewport.insetBottom() : 0),
                ShellSurface.Input.PAINT,
                geometry.reserveSpace() ? List.of(ShellReservation.exclusive(
                        ShellReservation.Edge.BOTTOM, geometry.height(), !autoHide)) : List.of());
        mTaskbar.commit(viewport.outputGeometry(), viewport.contentGeometry(), List.of(taskbar));
        mViewport = viewport;
        mGeometry = geometry;
        mAutoHide = autoHide;
    }

    ShellLayout.Snapshot snapshot() { return mScope.snapshot(); }
    ShellLayoutScope scope() { return mScope; }
    ShellLayout.Surface taskbar() { return mTaskbar.surface(TASKBAR); }
    ShellLayoutScope.Binding bind() { return mScope.bind(); }
    void listen(final Runnable listener) { mScope.listen(listener); }
    void unlisten(final Runnable listener) { mScope.unlisten(listener); }

    void release() {
        mScope.clear();
        mTaskbar = mScope.bind();
        mViewport = null;
    }
}
