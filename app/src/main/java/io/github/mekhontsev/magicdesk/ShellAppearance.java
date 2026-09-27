package io.github.mekhontsev.magicdesk;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, density-independent appearance. It owns no window or workspace state. */
public record ShellAppearance(Palette palette, Typography typography, Shape shape,
        Taskbar taskbar) {
    public record Palette(Map<UiColor, Integer> colors) {
        public Palette {
            colors = Map.copyOf(colors);
            for (UiColor role : UiColor.values()) Objects.requireNonNull(colors.get(role), role.name());
            if (colors.get(UiColor.TRANSPARENT) != 0) throw new IllegalArgumentException("transparent");
        }
        public int color(UiColor role) { return colors.get(role); }
    }
    public enum Font { SANS, SERIF, MONO }
    public record Typography(Font font, float scale) {
        public Typography { Objects.requireNonNull(font); range(scale, .8f, 1.3f, "text scale"); }
    }
    public record Shape(float radiusScale, float borderDp) {
        public Shape { range(radiusScale, 0, 2, "radius scale"); range(borderDp, 0, 3, "border"); }
    }
    public enum Width { FILL, CONTENT }
    public enum Alignment { START, CENTER, END }
    public record Taskbar(Width width, Alignment alignment, int maxWidthDp, int sideGapDp,
            int bottomGapDp, int paddingDp, int radiusDp, float opacity, boolean reserveSpace) {
        public Taskbar {
            Objects.requireNonNull(width); Objects.requireNonNull(alignment);
            range(maxWidthDp, 240, 4096, "maximum width");
            range(sideGapDp, 0, 96, "side gap"); range(bottomGapDp, 0, 96, "bottom gap");
            range(paddingDp, 0, 16, "padding"); range(radiusDp, 0, 32, "radius");
            range(opacity, .15f, 1, "opacity");
        }
        public static Taskbar defaults() {
            return new Taskbar(Width.FILL, Alignment.CENTER, 4096, 0, 0, 8, 0, 1, true);
        }
        public static Taskbar floating() {
            return new Taskbar(Width.CONTENT, Alignment.CENTER, 1100, 12, 12, 8, 8, .88f, true);
        }
    }
    public ShellAppearance {
        Objects.requireNonNull(palette); Objects.requireNonNull(typography);
        Objects.requireNonNull(shape); Objects.requireNonNull(taskbar);
    }
    public ShellAppearance withTaskbar(Taskbar value) {
        return new ShellAppearance(palette, typography, shape, value);
    }
    public ShellAppearance withStyle(ShellAppearance value) {
        return new ShellAppearance(value.palette, value.typography, value.shape, taskbar);
    }
    public static ShellAppearance defaults() { return preset("dark"); }
    public static ShellAppearance preset(String name) {
        final int[] values = switch (name) {
            case "dark" -> new int[] {0xff090d14, 0xff111827, 0xff172033, 0xffe5e7eb,
                    0xff94a3b8, 0xff22d3ee, 0xfff43f5e, 0xfff59e0b, 0xff26344a, 0xffe5e7eb, 0};
            case "light" -> new int[] {0xffeef1f4, 0xfffafbfc, 0xffe2e7eb, 0xff18222b,
                    0xff52616f, 0xff007a83, 0xffb51c36, 0xff925800, 0xffccd8df, 0xffe5e7eb, 0};
            case "contrast" -> new int[] {0xff000000, 0xff000000, 0xff202020, 0xffffffff,
                    0xffcccccc, 0xff00ffff, 0xffff8080, 0xffffff00, 0xff454545, 0xffffffff, 0};
            default -> throw new IllegalArgumentException("Unknown appearance preset: " + name);
        };
        final EnumMap<UiColor, Integer> colors = new EnumMap<>(UiColor.class);
        for (UiColor role : UiColor.values()) colors.put(role, values[role.ordinal()]);
        return new ShellAppearance(new Palette(colors), new Typography(Font.SANS, 1),
                new Shape(1, 1), Taskbar.defaults());
    }
    static void range(float value, float min, float max, String name) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}
