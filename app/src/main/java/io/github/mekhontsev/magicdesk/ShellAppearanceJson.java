package io.github.mekhontsev.magicdesk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import static io.github.mekhontsev.magicdesk.ShellAppearanceSchema.name;
import static io.github.mekhontsev.magicdesk.ShellAppearanceSchema.invalid;

/** Validate once at the boundary; renderers consume immutable typed values. */
final class ShellAppearanceJson {
    static final int MAX_BYTES = 32768;
    static ShellAppearance parse(String text) throws JSONException {
        if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid("", "document exceeds 32 KiB");
        final JSONTokener tokener = new JSONTokener(text) {
            private int depth;
            @Override public Object nextValue() throws JSONException {
                if (++depth > 8) throw new JSONException("Appearance nesting is too deep");
                try { return super.nextValue(); } finally { depth--; }
            }
        };
        final JSONObject root = new JSONObject(tokener);
        if (tokener.nextClean() != 0) throw invalid("", "trailing JSON content");
        ShellAppearanceSchema.validate(root);
        final ShellAppearance base = ShellAppearance.preset(root.optString("preset", "dark"));
        final EnumMap<UiColor, Integer> colors = new EnumMap<>(UiColor.class);
        colors.putAll(base.palette().colors());
        final JSONObject palette = object(root, "colors");
        for (var it = palette.keys(); it.hasNext();) {
            String key = it.next();
            colors.put(value(UiColor.class, key), (int) (0xff000000L | Long.parseLong(palette.getString(key).substring(1), 16)));
        }
        final JSONObject type = object(root, "typography"), shape = object(root, "shape"), bar = object(root, "taskbar");
        final JSONObject motion = object(root, "motion"), feedback = object(root, "feedback");
        final var b = base.taskbar();
        final EnumMap<ShellResources.Icon, ShellResources.Icon> icons = new EnumMap<>(ShellResources.Icon.class);
        final JSONObject resources = object(object(root, "resources"), "icons");
        for (var it = resources.keys(); it.hasNext();) {
            String key = it.next();
            icons.put(value(ShellResources.Icon.class, key), value(ShellResources.Icon.class, resources.getString(key)));
        }
        return new ShellAppearance(new ShellAppearance.Palette(colors),
                new ShellAppearance.Typography(value(ShellAppearance.Font.class, type.optString("font", "sans")), number(type, "scale", 1)),
                new ShellAppearance.Shape(number(shape, "radiusScale", 1), number(shape, "borderDp", 1)),
                new ShellAppearance.Taskbar(value(ShellAppearance.Width.class, bar.optString("width", "fill")),
                        value(ShellAppearance.Alignment.class, bar.optString("alignment", "center")),
                        bar.optInt("maxWidthDp", b.maxWidthDp()), bar.optInt("sideGapDp", b.sideGapDp()),
                        bar.optInt("bottomGapDp", b.bottomGapDp()), bar.optInt("paddingDp", b.paddingDp()),
                        bar.optInt("radiusDp", b.radiusDp()), number(bar, "opacity", b.opacity()), bar.optBoolean("reserveSpace", b.reserveSpace())),
                composition(object(root, "composition")),
                new ShellMotion(motion.optBoolean("reduced", false), value(ShellMotion.Effect.class, motion.optString("panels", "none")),
                        value(ShellMotion.Effect.class, motion.optString("taskbar", "none")), motion.optInt("durationMs", 160),
                        motion.optInt("feedbackMs", 0), value(ShellMotion.Curve.class, motion.optString("curve", "ease_out"))),
                new ShellAppearance.Feedback(role(feedback, "normal", UiColor.TRANSPARENT), role(feedback, "hover", UiColor.SURFACE),
                        role(feedback, "pressed", UiColor.HOVER), role(feedback, "selected", UiColor.SURFACE),
                        role(feedback, "focused", UiColor.HOVER), role(feedback, "disabled", UiColor.TRANSPARENT),
                        role(feedback, "outline", UiColor.ACCENT)), new ShellResources(icons));
    }

    private static ShellComposition composition(JSONObject input) throws JSONException {
        var defaults = ShellComposition.defaults();
        var components = new ArrayList<ShellComposition.Component>();
        var seen = new HashSet<ShellComposition.Kind>();
        JSONArray bar = input.optJSONArray("taskbar");
        if (bar == null) components.addAll(defaults.taskbar());
        else for (int i = 0; i < bar.length(); i++) {
            JSONObject item = bar.getJSONObject(i);
            var kind = value(ShellComposition.Kind.class, item.getString("type"));
            String path = "/composition/taskbar/" + i;
            if (kind != ShellComposition.Kind.SPACER && !seen.add(kind)) throw invalid(path + "/type", "component already present");
            if (item.has("label") && kind != ShellComposition.Kind.START) throw invalid(path + "/label", "only valid for start");
            if (item.has("clock") && kind != ShellComposition.Kind.CLOCK) throw invalid(path + "/clock", "only valid for clock");
            components.add(new ShellComposition.Component(kind, item.optInt("widthDp", 0), item.optInt("minViewportDp", 0),
                    value(ShellComposition.Visibility.class, item.optString("visibility", name(ShellComposition.Component.of(kind).visibility()))),
                    item.optString("label", ""), value(ShellComposition.Clock.class, item.optString("clock", "time"))));
        }
        JSONObject start = object(input, "start");
        var sections = new ArrayList<ShellComposition.Section>();
        JSONArray requested = start.optJSONArray("sections");
        if (requested == null) sections.addAll(defaults.start().sections());
        else for (int i = 0; i < requested.length(); i++) sections.add(value(ShellComposition.Section.class, requested.getString(i)));
        if (!sections.contains(ShellComposition.Section.APPS)) throw invalid("/composition/start/sections", "apps is required");
        return new ShellComposition(components, new ShellComposition.Start(sections,
                value(ShellComposition.Presentation.class, start.optString("presentation", "grid")),
                start.optInt("tileWidthDp", 100), start.optInt("iconSizeDp", 44)));
    }

    static JSONObject encode(ShellAppearance value) throws JSONException {
        final JSONObject colors = new JSONObject();
        for (UiColor role : UiColor.values()) if (role != UiColor.TRANSPARENT) {
            colors.put(name(role), String.format(Locale.ROOT, "#%06X", value.palette().color(role) & 0xffffff));
        }
        var t = value.taskbar(); var m = value.motion(); var f = value.feedback(); var s = value.composition().start();
        JSONArray components = new JSONArray(), sections = new JSONArray();
        for (var c : value.composition().taskbar()) {
            JSONObject item = new JSONObject().put("type", name(c.type())).put("widthDp", c.widthDp())
                    .put("minViewportDp", c.minViewportDp()).put("visibility", name(c.visibility()));
            if (c.type() == ShellComposition.Kind.START) item.put("label", c.label());
            if (c.type() == ShellComposition.Kind.CLOCK) item.put("clock", name(c.clock()));
            components.put(item);
        }
        for (var section : s.sections()) sections.put(name(section));
        JSONObject icons = new JSONObject();
        for (var icon : value.resources().icons().entrySet()) icons.put(name(icon.getKey()), name(icon.getValue()));
        return new JSONObject().put("version", 2).put("colors", colors)
                .put("typography", new JSONObject().put("font", name(value.typography().font())).put("scale", Float.valueOf(value.typography().scale())))
                .put("shape", new JSONObject().put("radiusScale", Float.valueOf(value.shape().radiusScale())).put("borderDp", Float.valueOf(value.shape().borderDp())))
                .put("taskbar", new JSONObject().put("width", name(t.width())).put("alignment", name(t.alignment()))
                        .put("maxWidthDp", t.maxWidthDp()).put("sideGapDp", t.sideGapDp()).put("bottomGapDp", t.bottomGapDp())
                        .put("paddingDp", t.paddingDp()).put("radiusDp", t.radiusDp()).put("opacity", Float.valueOf(t.opacity())).put("reserveSpace", t.reserveSpace()))
                .put("composition", new JSONObject().put("taskbar", components).put("start", new JSONObject()
                        .put("sections", sections).put("presentation", name(s.presentation())).put("tileWidthDp", s.tileWidthDp()).put("iconSizeDp", s.iconSizeDp())))
                .put("motion", new JSONObject().put("reduced", m.reduced()).put("panels", name(m.panels())).put("taskbar", name(m.taskbar()))
                        .put("durationMs", m.durationMs()).put("feedbackMs", m.feedbackMs()).put("curve", name(m.curve())))
                .put("feedback", new JSONObject().put("normal", name(f.normal())).put("hover", name(f.hover())).put("pressed", name(f.pressed()))
                        .put("selected", name(f.selected())).put("focused", name(f.focused())).put("disabled", name(f.disabled())).put("outline", name(f.outline())))
                .put("resources", new JSONObject().put("icons", icons));
    }
    private static <T extends Enum<T>> T value(Class<T> type, String value) { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
    private static JSONObject object(JSONObject root, String key) throws JSONException { return root.has(key) ? root.getJSONObject(key) : new JSONObject(); }
    private static float number(JSONObject value, String key, float fallback) { return (float) value.optDouble(key, fallback); }
    private static UiColor role(JSONObject value, String key, UiColor fallback) { return value(UiColor.class, value.optString(key, name(fallback))); }
}
