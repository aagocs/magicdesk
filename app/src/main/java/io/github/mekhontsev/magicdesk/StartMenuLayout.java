package io.github.mekhontsev.magicdesk;

/** Grid capacity is local to the Start viewport, including IME resizing. */
final class StartMenuLayout {
    private StartMenuLayout() { }

    static int columns(final int widthDp, final int tileWidthDp, final int iconSizeDp) {
        return Math.max(1, widthDp / Math.max(tileWidthDp, iconSizeDp + 20));
    }

    static int rowHeight(final int iconSizeDp) {
        return Math.max(58, iconSizeDp + 12);
    }

    static int rows(final int bodyHeightDp) {
        // Pager and grid margins remain outside the fixed-height tiles.
        return Math.max(1, Math.min(6, (bodyHeightDp - 80) / 112));
    }
}
