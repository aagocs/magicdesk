package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public final class ShaderWallpaperTest {
    @Test public void signalsRoundTripAndInvalidBindingsCannotAcquireSources() throws Exception {
        String json = """
            {"resources":{"shader":{"source":"half4 main(float2 p) { return half4(load.x, load.y, 0, 1); }",
            "signals":[{"name":"load","source":"system.cpu.usage","fallback":0.3,"smoothingMillis":800}]}}}
            """;
        var theme = ShellAppearanceJson.parse(json);
        assertEquals(theme, ShellAppearanceJson.parse(ShellAppearanceJson.encode(theme).toString()));
        assertTrue(theme.resources().shader().program().contains("uniform float2 load;"));
        assertEquals(.3, theme.resources().shader().signals().get(0).fallback(), .00001);
        assertTrue(WorkspaceAppearancePatch.parse("{\"resources\":{\"shader\":{\"signals\":[]}}}")
                .resolve(theme).resources().shader().signals().isEmpty());
        for (String invalid : List.of(json.replace("system.cpu.usage", "shell.exec"), json.replace("0.3", "-1"),
                json.replace("800", "10001"), json.replace("800", "0.5"), json.replace("\"load\"", "\"md_time\""),
                json.replace("\"source\":\"system.cpu.usage\",", ""))) {
            assertThrows(Exception.class, () -> ShellAppearanceJson.parse(invalid));
        }
        var uniform = new ShaderWallpaper.SignalUniform("speed", AppearanceSignal.CPU_USAGE, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper("x", 30, -1,
                List.of(new ShaderWallpaper.FloatUniform("speed", List.of(1f))), List.of(), List.of(), List.of(uniform)));
        assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper.SignalUniform("load", AppearanceSignal.CPU_USAGE, Float.NaN, 0));
    }

    @Test public void documentRoundTripAndWorkspaceParametersShareTheUsualTransaction() throws Exception {
        ShellAppearance theme = ShellThemesTest.load("contours");
        ShaderWallpaper shader = theme.resources().shader();
        assertNotNull(shader);
        assertTrue(theme.resources().hasAssets());
        assertFalse(theme.resources().hasBundleAssets());
        assertEquals(theme, ShellAppearanceJson.parse(ShellAppearanceJson.encode(theme).toString()));
        var patch = WorkspaceAppearancePatch.parse("{\"resources\":{\"shader\":{\"fps\":15}}}");
        assertEquals(15, patch.resolve(theme).resources().shader().fps());
        assertEquals(shader.source(), patch.resolve(theme).resources().shader().source());
        assertNull(WorkspaceAppearancePatch.parse("{\"resources\":{\"shader\":null}}").resolve(theme).resources().shader());
        assertNotNull(WorkspaceAppearancePatch.EMPTY.resolve(theme).resources().shader());
        var state = WorkspaceAppearance.defaults().apply(theme);
        var preview = state.preview(ShellAppearance.defaults());
        assertEquals(theme, preview.cancel(preview.snapshot().previewId()).current());
    }

    @Test public void sourceAndBindingsAreBoundedAndImmutable() throws Exception {
        var mutable = new ArrayList<>(List.of(1f, 2f));
        var value = new ShaderWallpaper.FloatUniform("direction", mutable);
        mutable.clear(); assertEquals(List.of(1f, 2f), value.value());
        assertThrows(UnsupportedOperationException.class, () -> value.value().clear());
        assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper.ColorUniform("ink", 0x00202428));
        for (String name : List.of("", "md_time", "sk_color", "gl_FragCoord", "hello;", "abc\n", "a".repeat(33))) {
            assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper.FloatUniform(name, List.of(1f)));
        }
        for (List<Float> numbers : List.of(List.<Float>of(), List.of(Float.NaN), List.of(Float.POSITIVE_INFINITY),
                List.of(10001f), List.of(1f, 2f, 3f, 4f, 5f))) {
            assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper.FloatUniform("x", numbers));
        }
        for (String path : List.of("icons/a.png", "../a.png", "wallpapers/a.mp4", "wallpapers/a.gif", "https://a/b.png")) {
            assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper.TextureUniform("image", path));
        }
        assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper("x", 30, 0xff202428,
                List.of(value), List.of(new ShaderWallpaper.ColorUniform("direction", -1)), List.of(), List.of()));
        for (int fps : new int[] {0, 61, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper("x", fps, -1, List.of(), List.of(), List.of(), List.of()));
        }
        for (String source : List.of("", " ", "x".repeat(16385), "\u03a3".repeat(8193))) {
            assertThrows(IllegalArgumentException.class, () -> new ShaderWallpaper(source, 30, -1, List.of(), List.of(), List.of(), List.of()));
        }
    }

    @Test public void schemaRejectsWrongTypesAndOnlyBundleTexturesAcquireStorageDependencies() throws Exception {
        for (String invalid : List.of("false", "{\"source\":42}", "{\"source\":\"x\",\"fps\":1.5}",
                "{\"source\":\"x\",\"unknown\":1}", "{\"source\":\"x\",\"colors\":[{\"name\":\"x\",\"value\":\"red\"}]}",
                "{\"source\":\"x\",\"floats\":[{\"name\":\"x\",\"value\":1}]}")) {
            assertThrows(Exception.class, () -> ShellAppearanceJson.parse("{\"resources\":{\"shader\":" + invalid + "}}"));
        }
        var document = ShellAppearanceJson.encode(ShellThemesTest.load("contours"));
        document.getJSONObject("resources").getJSONObject("shader").put("textures",
                new org.json.JSONArray().put(new JSONObject().put("name", "paper").put("path", "wallpapers/paper.png")));
        var theme = ShellAppearanceJson.parse(document.toString());
        assertTrue(theme.resources().hasBundleAssets());
        assertThrows(IllegalArgumentException.class, () -> WorkspaceAppearance.defaults().apply(theme));
        String program = theme.resources().shader().program();
        assertTrue(program.startsWith("uniform float2 md_resolution;\nuniform float md_time;"));
        assertTrue(program.contains("uniform float speed;"));
        assertTrue(program.contains("layout(color) uniform half4 base;"));
        assertTrue(program.contains("uniform shader paper;"));
    }

    @Test public void vsyncPacingCapsFramesWithoutCatchUpWorkOrWallClockTime() {
        var clock = new WallpaperFrameClock(30);
        assertTrue(clock.frame(10_000_000_000L)); assertEquals(0, clock.seconds(), 0);
        assertFalse(clock.frame(10_016_666_667L));
        assertTrue(clock.frame(10_033_333_334L));
        assertFalse(clock.frame(10_033_333_334L));
        assertTrue(clock.frame(30_000_000_000L)); assertEquals(20, clock.seconds(), .0001);
        assertFalse(clock.frame(30_000_000_001L));
        assertThrows(IllegalArgumentException.class, () -> new WallpaperFrameClock(0));
        assertThrows(IllegalArgumentException.class, () -> new WallpaperFrameClock(61));
    }
}
