package io.github.mekhontsev.magicdesk;

/** Vsync pacing, not a timer or state poll. No catch-up bursts after a stalled frame. */
final class WallpaperFrameClock {
    private final long interval;
    private long started = -1, last = -1;

    WallpaperFrameClock(int fps) {
        if (fps < 1 || fps > 60) throw new IllegalArgumentException("Invalid wallpaper frame rate");
        interval = 1_000_000_000L / fps;
    }
    boolean frame(long nanos) {
        if (started < 0) started = nanos;
        if (last >= 0 && nanos - last < interval) return false;
        last = nanos;
        return true;
    }
    float seconds() { return Math.max(0, last - started) / 1_000_000_000f; }
}
