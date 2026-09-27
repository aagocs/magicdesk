package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class ShaderWallpaperAssetTest {
    @Test public void compilationAndBindingsAreValidatedBeforeUseAndOutputsNeverShareMutableShaders() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", """
            static class Bitmap { int getAllocationByteCount() { return 128; } }
            static class Shader { enum TileMode { CLAMP } }
            static class BitmapShader extends Shader {
                static final int FILTER_MODE_LINEAR = 1;
                BitmapShader(Bitmap bitmap, TileMode x, TileMode y) {} void setFilterMode(int mode) {}
            }
            static class RuntimeShader {
                static boolean broken; final Map<String,Object> values = new HashMap<>();
                RuntimeShader(String source) { if (broken) throw new IllegalArgumentException("syntax error"); }
                void setFloatUniform(String name, float... value) { values.put(name, value); }
                void setColorUniform(String name, int value) { values.put(name, value); }
                void setInputShader(String name, Shader value) { values.put(name, value); }
            }
            """ + RuntimeSourceFixture.nestedClass("ShaderWallpaperAsset", "ShaderWallpaperAsset")
                    .replace("final class ShaderWallpaperAsset", "static final class ShaderWallpaperAsset") + """
            public static void verify() throws Exception {
                var spec = new ShaderWallpaper("half4 main(float2 p) { return half4(1); }", 30, -1,
                    List.of(new ShaderWallpaper.FloatUniform("direction", List.of(1f, 2f))),
                    List.of(new ShaderWallpaper.ColorUniform("ink", -1)),
                    List.of(new ShaderWallpaper.TextureUniform("paper", "wallpapers/paper.png")));
                Map<String,Bitmap> textures = new HashMap<>(); textures.put("wallpapers/paper.png", new Bitmap());
                var asset = new ShaderWallpaperAsset(spec, textures); textures.clear();
                var first = asset.createShader(100, 200); var second = asset.createShader(400, 300);
                check(first != second, "mutable shader shared by outputs");
                check(((float[]) first.values.get("md_resolution"))[0] == 100, "first output size overwritten");
                check(((float[]) second.values.get("md_resolution"))[0] == 400, "second output size missing");
                check(((float[]) first.values.get("direction"))[1] == 2 && first.values.get("paper") instanceof BitmapShader,
                    "uniform or texture not bound");
                check(asset.retainedBytes() > 128, "retained texture or source omitted from budget");
                boolean failed = false;
                try { new ShaderWallpaperAsset(spec, Map.of()); } catch (IOException expected) { failed = true; }
                check(failed, "missing texture accepted");
                RuntimeShader.broken = true; failed = false;
                try { new ShaderWallpaperAsset(spec, Map.of()); } catch (IOException expected) {
                    failed = expected.getMessage().contains("syntax error");
                }
                check(failed, "compile error not returned before publication");
            }
            """, "ShaderWallpaper", "ThemeBundleFiles", "ThemeBundle", "ThemeBundleLimits");
    }
}
