package io.github.mekhontsev.magicdesk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import static io.github.mekhontsev.magicdesk.ShellAppearanceSchema.*;

/** One vocabulary for UI, bundles, workspace patches and automation. */
final class ShaderWallpaperJson {
    private ShaderWallpaperJson() { }

    static JSONObject schema() throws JSONException {
        return new JSONObject().put("oneOf", new JSONArray().put(type("null")).put(object(new JSONObject()
                .put("source", type("string").put("maxLength", ShaderWallpaper.MAX_SOURCE_BYTES))
                .put("fps", number(true, 1, 60).put("default", 30))
                .put("fallbackColor", color())
                .put("floats", uniforms("value", type("array").put("items", number(false, -10000, 10000))
                        .put("minItems", 1).put("maxItems", 4), 16))
                .put("colors", uniforms("value", color(), 16))
                .put("textures", uniforms("path", assetPath(false), 4)))));
    }
    private static JSONObject color() throws JSONException { return type("string").put("pattern", "^#[0-9a-fA-F]{6}$"); }
    private static JSONObject uniforms(String field, JSONObject value, int max) throws JSONException {
        return type("array").put("minItems", 0).put("maxItems", max).put("items", object(new JSONObject()
                .put("name", type("string").put("pattern", "^[A-Za-z][A-Za-z0-9_]{0,31}$"))
                .put(field, value)).put("required", new JSONArray().put("name").put(field)));
    }

    static ShaderWallpaper parse(JSONObject object) throws JSONException {
        if (object == null) return null;
        var floats = new ArrayList<ShaderWallpaper.FloatUniform>();
        for (JSONObject uniform : entries(object, "floats")) {
            JSONArray values = uniform.getJSONArray("value");
            List<Float> vector = new ArrayList<>();
            for (int i = 0; i < values.length(); i++) vector.add((float) values.getDouble(i));
            floats.add(new ShaderWallpaper.FloatUniform(uniform.getString("name"), vector));
        }
        var colors = new ArrayList<ShaderWallpaper.ColorUniform>();
        for (JSONObject uniform : entries(object, "colors")) {
            colors.add(new ShaderWallpaper.ColorUniform(uniform.getString("name"), color(uniform.getString("value"))));
        }
        var textures = new ArrayList<ShaderWallpaper.TextureUniform>();
        for (JSONObject uniform : entries(object, "textures")) {
            textures.add(new ShaderWallpaper.TextureUniform(uniform.getString("name"), uniform.getString("path")));
        }
        return new ShaderWallpaper(object.getString("source"), object.optInt("fps", 30),
                color(object.optString("fallbackColor", "#202428")), floats, colors, textures);
    }
    private static List<JSONObject> entries(JSONObject object, String key) throws JSONException {
        JSONArray array = object.optJSONArray(key);
        List<JSONObject> result = new ArrayList<>();
        if (array != null) for (int i = 0; i < array.length(); i++) result.add(array.getJSONObject(i));
        return result;
    }
    private static int color(String value) { return (int) (0xff000000L | Long.parseLong(value.substring(1), 16)); }
    private static String color(int value) { return String.format(Locale.ROOT, "#%06X", value & 0xffffff); }

    static Object encode(ShaderWallpaper spec) throws JSONException {
        if (spec == null) return JSONObject.NULL;
        JSONArray floats = new JSONArray(), colors = new JSONArray(), textures = new JSONArray();
        for (var uniform : spec.floats()) floats.put(new JSONObject().put("name", uniform.name()).put("value", new JSONArray(uniform.value())));
        for (var uniform : spec.colors()) colors.put(new JSONObject().put("name", uniform.name()).put("value", color(uniform.value())));
        for (var uniform : spec.textures()) textures.put(new JSONObject().put("name", uniform.name()).put("path", uniform.path()));
        return new JSONObject().put("source", spec.source()).put("fps", spec.fps()).put("fallbackColor", color(spec.fallbackColor()))
                .put("floats", floats).put("colors", colors).put("textures", textures);
    }
}
