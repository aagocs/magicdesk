package io.github.mekhontsev.magicdesk;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import java.io.IOException;
import java.util.Map;

/** Immutable inputs; each output creates its own mutable Android shader on a worker. */
final class ShaderWallpaperAsset {
    final ShaderWallpaper spec;
    private final Map<String, Bitmap> textures;
    private final String program;

    ShaderWallpaperAsset(ShaderWallpaper spec, Map<String, Bitmap> textures) throws IOException {
        this.spec = spec; this.textures = Map.copyOf(textures); this.program = spec.program();
        createShader(1, 1); // Validate generated declarations, compilation and bindings before publication.
    }

    RuntimeShader createShader(int width, int height) throws IOException {
        try {
            RuntimeShader shader = new RuntimeShader(program);
            shader.setFloatUniform("md_resolution", width, height);
            shader.setFloatUniform("md_time", 0);
            for (var uniform : spec.signals()) shader.setFloatUniform(uniform.name(), uniform.fallback(), 0);
            for (var uniform : spec.floats()) {
                float[] values = new float[uniform.value().size()];
                for (int i = 0; i < values.length; i++) values[i] = uniform.value().get(i);
                shader.setFloatUniform(uniform.name(), values);
            }
            for (var uniform : spec.colors()) shader.setColorUniform(uniform.name(), uniform.value());
            for (var uniform : spec.textures()) {
                Bitmap bitmap = textures.get(uniform.path());
                if (bitmap == null) throw new IllegalArgumentException("Missing shader texture: " + uniform.path());
                BitmapShader input = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                input.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);
                shader.setInputShader(uniform.name(), input);
            }
            return shader;
        } catch (IllegalArgumentException error) { throw new IOException("Invalid AGSL wallpaper: " + error.getMessage(), error); }
    }

    long retainedBytes() {
        long bytes = 2L * (program.length() + spec.source().length());
        for (Bitmap bitmap : textures.values()) bytes += bitmap.getAllocationByteCount();
        return bytes;
    }
}
