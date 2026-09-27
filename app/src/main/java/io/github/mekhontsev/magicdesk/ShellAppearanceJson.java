package io.github.mekhontsev.magicdesk;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Set;

/** Bounded declarative document; missing fields inherit the built-in defaults. */
final class ShellAppearanceJson {
    static final int MAX_BYTES = 32768;
    static ShellAppearance parse(String text) throws JSONException {
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Appearance document exceeds 32 KiB");
        }
        final JSONTokener tokener = new JSONTokener(text) {
            private int depth;
            @Override public Object nextValue() throws JSONException {
                if (++depth > 8) throw new JSONException("Appearance nesting is too deep");
                try { return super.nextValue(); }
                finally { depth--; }
            }
        };
        final JSONObject root = new JSONObject(tokener);
        if (tokener.nextClean() != 0) throw new IllegalArgumentException("Trailing JSON content");
        keys(root, "version", "preset", "colors", "typography", "shape", "taskbar");
        if (integer(root, "version", 1) != 1) throw new IllegalArgumentException("Unsupported appearance version");
        final ShellAppearance base = ShellAppearance.preset(string(root, "preset", "dark"));
        final EnumMap<UiColor, Integer> colors = new EnumMap<>(UiColor.class);
        colors.putAll(base.palette().colors());
        final JSONObject palette = object(root, "colors");
        for (var it = palette.keys(); it.hasNext();) {
            String key = it.next();
            UiColor role = enumValue(UiColor.class, key);
            if (role == UiColor.TRANSPARENT) throw new IllegalArgumentException("Transparent is not configurable");
            Object value = palette.get(key);
            if (!(value instanceof String color) || !color.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Color must be #RRGGBB: " + key);
            }
            colors.put(role, (int) (0xff000000L | Long.parseLong(color.substring(1), 16)));
        }
        final JSONObject type = object(root, "typography");
        keys(type, "font", "scale");
        final JSONObject shape = object(root, "shape");
        keys(shape, "radiusScale", "borderDp");
        final JSONObject bar = object(root, "taskbar");
        keys(bar, "width", "alignment", "maxWidthDp", "sideGapDp", "bottomGapDp", "paddingDp", "radiusDp", "opacity", "reserveSpace");
        final ShellAppearance.Taskbar b = base.taskbar();
        return new ShellAppearance(new ShellAppearance.Palette(colors),
                new ShellAppearance.Typography(enumValue(ShellAppearance.Font.class, string(type, "font", "sans")),
                        number(type, "scale", 1)),
                new ShellAppearance.Shape(number(shape, "radiusScale", 1), number(shape, "borderDp", 1)),
                new ShellAppearance.Taskbar(enumValue(ShellAppearance.Width.class, string(bar, "width", "fill")),
                        enumValue(ShellAppearance.Alignment.class, string(bar, "alignment", "center")),
                        integer(bar, "maxWidthDp", b.maxWidthDp()), integer(bar, "sideGapDp", b.sideGapDp()),
                        integer(bar, "bottomGapDp", b.bottomGapDp()), integer(bar, "paddingDp", b.paddingDp()),
                        integer(bar, "radiusDp", b.radiusDp()), number(bar, "opacity", b.opacity()),
                        bool(bar, "reserveSpace", b.reserveSpace())));
    }
    static JSONObject encode(ShellAppearance value) throws JSONException {
        final JSONObject colors = new JSONObject();
        for (UiColor role : UiColor.values()) if (role != UiColor.TRANSPARENT) {
            colors.put(name(role), String.format(Locale.ROOT, "#%06X", value.palette().color(role) & 0xffffff));
        }
        var t = value.taskbar();
        return new JSONObject().put("version", 1).put("colors", colors)
                .put("typography", new JSONObject().put("font", name(value.typography().font())).put("scale", Float.valueOf(value.typography().scale())))
                .put("shape", new JSONObject().put("radiusScale", Float.valueOf(value.shape().radiusScale())).put("borderDp", Float.valueOf(value.shape().borderDp())))
                .put("taskbar", new JSONObject().put("width", name(t.width())).put("alignment", name(t.alignment()))
                        .put("maxWidthDp", t.maxWidthDp()).put("sideGapDp", t.sideGapDp()).put("bottomGapDp", t.bottomGapDp())
                        .put("paddingDp", t.paddingDp()).put("radiusDp", t.radiusDp()).put("opacity", Float.valueOf(t.opacity()))
                        .put("reserveSpace", t.reserveSpace()));
    }
    private static String name(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }
    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
    private static JSONObject object(JSONObject root, String key) throws JSONException {
        return root.has(key) ? root.getJSONObject(key) : new JSONObject();
    }
    private static String string(JSONObject value, String key, String fallback) throws JSONException {
        if (!value.has(key)) return fallback;
        if (!(value.get(key) instanceof String result)) throw new IllegalArgumentException("Expected string: " + key);
        return result;
    }
    private static void keys(JSONObject value, String... allowed) {
        final Set<String> keys = Set.of(allowed);
        for (var it = value.keys(); it.hasNext();) {
            String key = it.next();
            if (!keys.contains(key)) throw new IllegalArgumentException("Unknown appearance field: " + key);
        }
    }
    private static float number(JSONObject value, String key, float fallback) throws JSONException {
        if (!value.has(key)) return fallback;
        Object item = value.get(key);
        if (!(item instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException("Expected number: " + key);
        }
        return number.floatValue();
    }
    private static int integer(JSONObject value, String key, int fallback) throws JSONException {
        if (!value.has(key)) return fallback;
        if (!(value.get(key) instanceof Number raw)) throw new IllegalArgumentException("Expected integer: " + key);
        double number = raw.doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Expected integer: " + key);
        }
        return (int) number;
    }
    private static boolean bool(JSONObject value, String key, boolean fallback) throws JSONException {
        if (!value.has(key)) return fallback;
        if (!(value.get(key) instanceof Boolean result)) throw new IllegalArgumentException("Expected boolean: " + key);
        return result;
    }
}
