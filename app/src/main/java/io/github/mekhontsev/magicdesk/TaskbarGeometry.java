package io.github.mekhontsev.magicdesk;

/** Resolves taskbar size constraints before the shared shell placement pass. */
record TaskbarGeometry(int width, int height, int anchors, ShellSurface.Margins margins,
        boolean extendNavigation, boolean reserveSpace) {
    static TaskbarGeometry resolve(ShellAppearance.Taskbar style, float density,
            int availableWidth, int availableHeight, int height, int preferredWidth, int minimumWidth) {
        int side = Math.min(Math.round(style.sideGapDp() * density), Math.max(0, (availableWidth - minimumWidth) / 2));
        int bottom = Math.min(Math.round(style.bottomGapDp() * density), Math.max(0, availableHeight - height));
        int limit = Math.max(1, availableWidth - 2 * side);
        int width = style.width() == ShellAppearance.Width.FILL ? limit
                : Math.min(limit, Math.max(minimumWidth, Math.min(preferredWidth, Math.round(style.maxWidthDp() * density))));
        int anchors = ShellSurface.BOTTOM | switch (style.alignment()) {
            case START -> ShellSurface.LEFT; case CENTER -> 0; case END -> ShellSurface.RIGHT;
        };
        return new TaskbarGeometry(Math.max(1, width), Math.max(1, Math.min(height, availableHeight)), anchors,
                new ShellSurface.Margins(side, 0, side, bottom),
                side == 0 && bottom == 0 && width == availableWidth, style.reserveSpace());
    }

    static ShellBounds reveal(ShellBounds output, ShellBounds surface, int height) {
        return new ShellBounds(surface.left(), Math.max(output.top(), output.bottom() - Math.max(1, height)),
                surface.right(), output.bottom());
    }
    static ShellBounds presented(ShellBounds output, ShellBounds surface, boolean visible, boolean edge, int edgeHeight) {
        if (!visible) return new ShellBounds(0, 0, 0, 0);
        return edge ? reveal(output, surface, edgeHeight) : surface;
    }
    static int paintAlpha(boolean visible, boolean edge) { return visible && !edge ? 255 : 0; }
}
