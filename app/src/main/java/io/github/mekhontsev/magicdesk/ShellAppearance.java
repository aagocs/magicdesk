package io.github.mekhontsev.magicdesk;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable, density-independent appearance. It owns no window or workspace state. */
public record ShellAppearance(Palette palette, Typography typography, Shape shape,
        ShellComposition composition, ShellMotion motion,
        Feedback feedback, ShellResources resources) {
    public record Feedback(UiColor normal, UiColor hover, UiColor pressed,
            UiColor selected, UiColor focused, UiColor disabled, UiColor outline) {
        public Feedback {
            Objects.requireNonNull(normal); Objects.requireNonNull(hover); Objects.requireNonNull(pressed);
            Objects.requireNonNull(selected); Objects.requireNonNull(focused); Objects.requireNonNull(disabled);
            Objects.requireNonNull(outline);
        }
        public static Feedback defaults() {
            return new Feedback(UiColor.TRANSPARENT, UiColor.SURFACE, UiColor.HOVER,
                    UiColor.SURFACE, UiColor.HOVER, UiColor.TRANSPARENT, UiColor.ACCENT);
        }
    }
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
    public record PanelStyle(Width length, Alignment alignment, int maxLengthDp, int sideGapDp,
            int edgeGapDp, int thicknessDp, int paddingDp, int radiusDp, float opacity, boolean reserveSpace) {
        public PanelStyle {
            Objects.requireNonNull(length); Objects.requireNonNull(alignment);
            range(maxLengthDp, 64, 4096, "maximum length");
            range(sideGapDp, 0, 96, "side gap"); range(edgeGapDp, 0, 96, "edge gap");
            if (thicknessDp != 0) range(thicknessDp, 40, 160, "panel thickness");
            range(paddingDp, 0, 16, "padding"); range(radiusDp, 0, 32, "radius");
            range(opacity, .15f, 1, "opacity");
        }
        public static PanelStyle defaults() {
            return new PanelStyle(Width.FILL, Alignment.CENTER, 4096, 0, 0, 0, 8, 0, 1, true);
        }
        public static PanelStyle floating() {
            return new PanelStyle(Width.CONTENT, Alignment.CENTER, 1100, 12, 12, 0, 8, 8, .88f, true);
        }
    }
    public ShellAppearance {
        Objects.requireNonNull(palette); Objects.requireNonNull(typography);
        Objects.requireNonNull(shape);
        Objects.requireNonNull(composition); Objects.requireNonNull(motion);
        Objects.requireNonNull(feedback); Objects.requireNonNull(resources);
    }
    public ShellAppearance withComposition(ShellComposition value) {
        return new ShellAppearance(palette, typography, shape, value, motion, feedback, resources);
    }
    public ShellAppearance withStyle(ShellAppearance value) {
        return new ShellAppearance(value.palette, value.typography, value.shape,
                composition, motion, value.feedback, resources);
    }
    public ShellAppearance withPalette(Palette value) {
        return new ShellAppearance(value, typography, shape, composition, motion, feedback, resources);
    }
    public ShellAppearance withTypography(Typography value) {
        return new ShellAppearance(palette, value, shape, composition, motion, feedback, resources);
    }
    public ShellAppearance withShape(Shape value) {
        return new ShellAppearance(palette, typography, value, composition, motion, feedback, resources);
    }
    public ShellAppearance withResources(ShellResources value) {
        return new ShellAppearance(palette, typography, shape, composition, motion, feedback, value);
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
                new Shape(1, 1), ShellComposition.defaults(), ShellMotion.defaults(),
                Feedback.defaults(), ShellResources.defaults());
    }
    static void range(float value, float min, float max, String name) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}
