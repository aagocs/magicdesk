package io.github.mekhontsev.magicdesk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Verified immutable content identity. No external paths, URI grants or executable resources. */
public final class ThemeBundle {
    public enum Kind { THEME, ICON, WALLPAPER, FONT }
    public record ImageSize(int width, int height) {
        public static final ImageSize NONE = new ImageSize(0, 0);
        public long pixels() { return (long) width * height; }
    }
    public record Entry(String path, Kind kind, long bytes, String sha256, ImageSize image) {}

    /** Must fully decode media and reject invalid content, not merely accept its extension. */
    @FunctionalInterface public interface MediaValidator {
        ImageSize validate(String path, Kind kind, byte[] content,
                ThemeBundleLimits limits, long remainingPixels) throws IOException;
    }

    private final String digest;
    private final String themeJson;
    private final Map<String, Entry> entries;
    private final Path directory;

    ThemeBundle(String digest, String themeJson, Map<String, Entry> entries, Path directory) {
        this.digest = digest;
        this.themeJson = themeJson;
        this.entries = Collections.unmodifiableMap(new TreeMap<>(entries));
        this.directory = directory;
    }

    public String digest() { return digest; }
    public String themeJson() { return themeJson; }
    public Map<String, Entry> entries() { return entries; }

    public Entry require(String path, Kind kind) throws IOException {
        Entry entry = entries.get(path);
        if (entry == null || entry.kind() != kind) {
            throw new IOException("Missing or wrong-kind theme asset: " + path);
        }
        return entry;
    }

    /** Worker-thread read; rechecks bytes against the verified snapshot and never exposes a file. */
    public byte[] readAsset(String path, Kind kind) throws IOException {
        Entry entry = require(path, kind);
        Path file = ThemeBundleFiles.checkedFile(directory, path);
        byte[] bytes = ThemeBundleFiles.read(file, entry.bytes());
        if (bytes.length != entry.bytes() || !ThemeBundleFiles.sha256(bytes).equals(entry.sha256())) {
            throw new IOException("Installed theme asset changed: " + path);
        }
        return bytes;
    }
}
