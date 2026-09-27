package io.github.mekhontsev.magicdesk;

import java.util.List;

final class ShellLayoutTestSupport {
    static void update(DesktopShellLayout layout, DesktopViewport viewport, int height, boolean autoHide) {
        layout.update(viewport, List.of(PanelGeometry.resolve(ShellComposition.defaults().panels().get(0), 1,
                viewport.contentWidth(), viewport.contentHeight(), height, 0, 0)), autoHide);
    }

    static ShellPanel panel(String id, ShellPanel.Edge edge, ShellAppearance.PanelStyle style,
            ShellComposition.Kind component) {
        return new ShellPanel(id, edge, style, List.of(ShellComposition.Component.of(component)));
    }

    static PanelGeometry geometry(ShellPanel panel, DesktopViewport viewport, int thickness) {
        return PanelGeometry.resolve(panel, 1, viewport.contentWidth(), viewport.contentHeight(), thickness, 900, 500);
    }
}
