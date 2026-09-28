#pragma once
#include <stdint.h>
#include <limits.h>

typedef struct {
    int baseWidth, baseHeight, widthIncrement, heightIncrement;
    int minAspectX, minAspectY, maxAspectX, maxAspectY;
    int aspectBaseWidth, aspectBaseHeight;
} HostedResizeRules;

static inline int hosted_grid_ceil(int value, int base, int step) {
    return base + ((value > base ? value - base : 0) + step - 1) / step * step;
}
static inline int hosted_grid_floor(int value, int base, int step) {
    return value < base ? base - step : base + (value - base) / step * step;
}
static inline int hosted_grid_nearest(int value, int low, int high, int step) {
    if (value <= low) return low;
    if (value >= high) return high;
    int result = low + (value - low + (step - 1) / 2) / step * step;
    return result > high ? high : result;
}

// Refine a min/max-constrained offer on the client's size lattice. Aspect search
// is bounded by the native geometry limit, on resize/property events only.
static inline void hosted_resize_rules(const HostedResizeRules* r, int minw, int minh,
        int maxw, int maxh, int* width, int* height) {
    int sx = r->widthIncrement > 0 ? r->widthIncrement : 1;
    int sy = r->heightIncrement > 0 ? r->heightIncrement : 1;
    int lw = hosted_grid_ceil(minw, r->baseWidth, sx), hw = hosted_grid_floor(maxw, r->baseWidth, sx);
    int lh = hosted_grid_ceil(minh, r->baseHeight, sy), hh = hosted_grid_floor(maxh, r->baseHeight, sy);
    // An impossible lattice must not override hard minimum/maximum limits.
    if (lw > hw) { lw = minw; hw = maxw; sx = 1; }
    if (lh > hh) { lh = minh; hh = maxh; sy = 1; }
    int bestw = hosted_grid_nearest(*width, lw, hw, sx);
    int besth = hosted_grid_nearest(*height, lh, hh, sy);
    if (r->minAspectX > 0 && r->minAspectY > 0 && r->maxAspectX > 0 && r->maxAspectY > 0) {
        int64_t score = INT64_MAX;
        for (int w = lw; w <= hw; w += sx) {
            int64_t aw = w - r->aspectBaseWidth;
            if (aw <= 0) continue;
            int64_t low = r->aspectBaseHeight + (aw * r->maxAspectY + r->maxAspectX - 1) / r->maxAspectX;
            int64_t high = r->aspectBaseHeight + aw * r->minAspectY / r->minAspectX;
            if (low > hh || high < lh) continue;
            int lower = hosted_grid_ceil(low > lh ? (int)low : lh, lh, sy);
            int upper = hosted_grid_floor(high < hh ? (int)high : hh, lh, sy);
            if (lower > upper) continue;
            int h = hosted_grid_nearest(*height, lower, upper, sy);
            int64_t dw = (int64_t)w - *width, dh = (int64_t)h - *height;
            int64_t distance = dw * dw + dh * dh;
            if (distance < score) { score = distance; bestw = w; besth = h; }
        }
    }
    *width = bestw; *height = besth;
}
