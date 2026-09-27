package io.github.mekhontsev.magicdesk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable single-pass wallpaper program. Android rendering and resource loading are adapters. */
public record ShaderWallpaper(String source, int fps, int fallbackColor,
        List<FloatUniform> floats, List<ColorUniform> colors, List<TextureUniform> textures) {
    public static final int MAX_SOURCE_BYTES = 16384;
    public record FloatUniform(String name, List<Float> value) {
        public FloatUniform {
            ShaderWallpaper.name(name); value = List.copyOf(value);
            if (value.isEmpty() || value.size() > 4 || value.stream().anyMatch(v -> !Float.isFinite(v) || Math.abs(v) > 10000)) {
                throw new IllegalArgumentException("Shader float requires 1-4 finite values in [-10000,10000]");
            }
        }
    }
    public record ColorUniform(String name, int value) {
        public ColorUniform {
            ShaderWallpaper.name(name);
            if ((value >>> 24) != 255) throw new IllegalArgumentException("Shader color requires an opaque RGB value");
        }
    }
    public record TextureUniform(String name, String path) {
        public TextureUniform {
            ShaderWallpaper.name(name);
            try {
                if (ThemeBundleFiles.kind(path) != ThemeBundle.Kind.WALLPAPER
                        || !path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpe?g|webp)$")) {
                    throw new IllegalArgumentException("Shader textures must be static wallpaper images");
                }
            } catch (java.io.IOException error) { throw new IllegalArgumentException(error.getMessage(), error); }
        }
    }

    public ShaderWallpaper {
        if (source == null || source.isBlank() || source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES
                || fps < 1 || fps > 60 || (fallbackColor >>> 24) != 255) {
            throw new IllegalArgumentException("Invalid shader source, frame rate or fallback color");
        }
        floats = List.copyOf(floats); colors = List.copyOf(colors); textures = List.copyOf(textures);
        if (floats.size() > 16 || colors.size() > 16 || textures.size() > 4) throw new IllegalArgumentException("Too many shader uniforms");
        Set<String> names = new HashSet<>();
        for (var uniform : floats) unique(names, uniform.name());
        for (var uniform : colors) unique(names, uniform.name());
        for (var uniform : textures) unique(names, uniform.name());
    }

    private static void name(String value) {
        if (value == null || !value.matches("[A-Za-z][A-Za-z0-9_]{0,31}")
                || value.startsWith("md_") || value.startsWith("sk_") || value.startsWith("gl_")) {
            throw new IllegalArgumentException("Invalid or reserved shader uniform name: " + value);
        }
    }
    private static void unique(Set<String> names, String value) {
        if (!names.add(value)) throw new IllegalArgumentException("Duplicate shader uniform: " + value);
    }

    String program() {
        StringBuilder result = new StringBuilder("uniform float2 md_resolution;\nuniform float md_time;\n");
        for (var uniform : floats) result.append("uniform float").append(uniform.value().size() == 1 ? "" : uniform.value().size())
                .append(' ').append(uniform.name()).append(";\n");
        for (var uniform : colors) result.append("layout(color) uniform half4 ").append(uniform.name()).append(";\n");
        for (var uniform : textures) result.append("uniform shader ").append(uniform.name()).append(";\n");
        return result.append(source).toString();
    }
}
