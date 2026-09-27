package io.github.mekhontsev.magicdesk;

import java.util.Objects;

/** Presentation only. A duration never gates a window or input transaction. */
public record ShellMotion(boolean reduced, Effect panels, Effect taskbar,
        int durationMs, int feedbackMs, Curve curve) {
    public enum Effect { NONE, FADE }
    public enum Curve { LINEAR, EASE_OUT, SMOOTH }
    public ShellMotion {
        Objects.requireNonNull(panels); Objects.requireNonNull(taskbar); Objects.requireNonNull(curve);
        ShellAppearance.range(durationMs, 0, 400, "motion duration");
        ShellAppearance.range(feedbackMs, 0, 250, "feedback duration");
    }
    public static ShellMotion defaults() { return new ShellMotion(false, Effect.NONE, Effect.NONE, 160, 0, Curve.EASE_OUT); }
    public int duration(Effect effect, boolean systemEnabled) {
        return !systemEnabled || reduced || effect == Effect.NONE ? 0 : durationMs;
    }
}
