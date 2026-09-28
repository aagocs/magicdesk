package io.github.mekhontsev.magicdesk.hosted;

/** Client content limits in protocol units; zero is unspecified. Android decorations are separate. */
public record HostedWindowConstraints(int minWidth, int minHeight, int maxWidth, int maxHeight, HostedResizeRules resize) {
    public static final HostedWindowConstraints NONE = new HostedWindowConstraints(0, 0, 0, 0);

    public HostedWindowConstraints {
        java.util.Objects.requireNonNull(resize);
        if (minWidth < 0 || minHeight < 0 || maxWidth < 0 || maxHeight < 0
                || maxWidth != 0 && maxWidth < minWidth || maxHeight != 0 && maxHeight < minHeight)
            throw new IllegalArgumentException("Invalid client size constraints");
    }
    public HostedWindowConstraints(int minWidth, int minHeight, int maxWidth, int maxHeight) {
        this(minWidth, minHeight, maxWidth, maxHeight, HostedResizeRules.NONE);
    }
    public HostedResizeRules.Size size(int width, int height) {
        int w = width(width), h = height(height);
        if (resize.equals(HostedResizeRules.NONE)) return new HostedResizeRules.Size(w, h);
        return resize.apply(w, h, Math.max(1, minWidth), Math.max(1, minHeight),
                maxWidth == 0 ? 16384 : Math.min(16384, maxWidth), maxHeight == 0 ? 16384 : Math.min(16384, maxHeight));
    }
    public int width(int offered) { return constrain(offered, minWidth, maxWidth); }
    public int height(int offered) { return constrain(offered, minHeight, maxHeight); }
    private static int constrain(int offered, int min, int max) {
        return Math.max(Math.max(1, min), max == 0 ? offered : Math.min(offered, max));
    }
}
