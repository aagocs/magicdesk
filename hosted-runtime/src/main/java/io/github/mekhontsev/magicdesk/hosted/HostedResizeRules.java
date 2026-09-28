package io.github.mekhontsev.magicdesk.hosted;

/** Protocol-neutral size lattice and aspect constraints, in client units. */
public record HostedResizeRules(int baseWidth, int baseHeight, int widthIncrement, int heightIncrement,
        int minAspectX, int minAspectY, int maxAspectX, int maxAspectY, int aspectBaseWidth, int aspectBaseHeight) {
    public static final HostedResizeRules NONE = new HostedResizeRules(0, 0, 1, 1, 0, 0, 0, 0, 0, 0);
    public record Size(int width, int height) { }

    public HostedResizeRules {
        if (baseWidth < 0 || baseHeight < 0 || baseWidth > 16384 || baseHeight > 16384
                || widthIncrement < 1 || heightIncrement < 1 || widthIncrement > 16384 || heightIncrement > 16384
                || minAspectX < 0 || minAspectY < 0 || maxAspectX < 0 || maxAspectY < 0
                || aspectBaseWidth < 0 || aspectBaseHeight < 0 || aspectBaseWidth > 16384 || aspectBaseHeight > 16384
                || ((minAspectX | minAspectY | maxAspectX | maxAspectY) != 0
                    && (minAspectX == 0 || minAspectY == 0 || maxAspectX == 0 || maxAspectY == 0
                        || (long) minAspectX * maxAspectY > (long) maxAspectX * minAspectY)))
            throw new IllegalArgumentException("Invalid client resize rules");
    }

    Size apply(int width, int height, int minw, int minh, int maxw, int maxh) {
        int sx = widthIncrement, sy = heightIncrement;
        int lw = ceil(minw, baseWidth, sx), hw = floor(maxw, baseWidth, sx);
        int lh = ceil(minh, baseHeight, sy), hh = floor(maxh, baseHeight, sy);
        if (lw > hw) { lw = minw; hw = maxw; sx = 1; }
        if (lh > hh) { lh = minh; hh = maxh; sy = 1; }
        int bestw = nearest(width, lw, hw, sx), besth = nearest(height, lh, hh, sy);
        if (minAspectX != 0) {
            long score = Long.MAX_VALUE;
            for (int w = lw; w <= hw; w += sx) {
                long aw = w - aspectBaseWidth;
                if (aw <= 0) continue;
                long low = aspectBaseHeight + (aw * maxAspectY + maxAspectX - 1) / maxAspectX;
                long high = aspectBaseHeight + aw * minAspectY / minAspectX;
                if (low > hh || high < lh) continue;
                int lower = ceil((int) Math.max(low, lh), lh, sy);
                int upper = floor((int) Math.min(high, hh), lh, sy);
                if (lower > upper) continue;
                int h = nearest(height, lower, upper, sy);
                long dw = (long) w - width, dh = (long) h - height, distance = dw * dw + dh * dh;
                if (distance < score) { score = distance; bestw = w; besth = h; }
            }
        }
        return new Size(bestw, besth);
    }
    private static int ceil(int value, int base, int step) { return base + (Math.max(0, value - base) + step - 1) / step * step; }
    private static int floor(int value, int base, int step) { return value < base ? base - step : base + (value - base) / step * step; }
    private static int nearest(int value, int low, int high, int step) {
        if (value <= low) return low;
        if (value >= high) return high;
        return Math.min(high, low + (value - low + (step - 1) / 2) / step * step);
    }
}
