package io.github.mekhontsev.magicdesk;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONException;

/** Theme-model adapter; archive admission, media decoding and storage stay in ThemeBundleStore. */
final class AppearanceBundles {
    private AppearanceBundles() {}

    static ShellAppearance read(InputStream input) throws IOException {
        return read(ThemeAssets.get(MagicDeskApplication.applicationContext()).store(), input);
    }

    static ShellAppearance read(ThemeBundleStore store, InputStream input) throws IOException {
        var bundle = store.importBundle(input, AppearanceBundles::validate);
        var appearance = parse(bundle.themeJson());
        return appearance.withResources(appearance.resources().withBundle(bundle.digest()));
    }

    private static void validate(String json, Map<String, ThemeBundle.Entry> entries) throws IOException {
        var resources = parse(json).resources();
        if (!resources.bundle().isEmpty()) throw new IOException("Portable theme.json must omit its installed bundle digest");
        for (var item : references(resources).entrySet()) {
            var entry = entries.get(item.getKey());
            if (entry == null || entry.kind() != item.getValue()) {
                throw new IOException("Missing or wrong-kind theme resource: " + item.getKey());
            }
        }
    }

    private static ShellAppearance parse(String document) throws IOException {
        try { return ShellAppearanceJson.parse(document); }
        catch (JSONException | IllegalArgumentException error) { throw new IOException(error.getMessage(), error); }
    }

    private static Map<String, ThemeBundle.Kind> references(ShellResources resources) throws IOException {
        Map<String, ThemeBundle.Kind> references = new TreeMap<>();
        for (String path : resources.iconAssets().values()) reference(references, path, ThemeBundle.Kind.ICON);
        reference(references, resources.font(), ThemeBundle.Kind.FONT);
        reference(references, resources.wallpaper(), ThemeBundle.Kind.WALLPAPER);
        return references;
    }

    private static void reference(Map<String, ThemeBundle.Kind> references, String path, ThemeBundle.Kind kind) throws IOException {
        if (path.isEmpty()) return;
        var previous = references.putIfAbsent(path, kind);
        if (previous != null && previous != kind) throw new IOException("Theme resource is used for incompatible roles: " + path);
    }

    static void write(ShellAppearance appearance, OutputStream output) throws IOException {
        write(ThemeAssets.get(MagicDeskApplication.applicationContext()).store(), appearance, output);
    }

    static void write(ThemeBundleStore store, ShellAppearance appearance, OutputStream output) throws IOException {
        final String document;
        try {
            document = ShellAppearanceJson.encode(appearance.withResources(appearance.resources().withBundle(""))).toString(2);
        } catch (JSONException error) { throw new IOException(error.getMessage(), error); }
        var limits = store.limits();
        byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limits.themeBytes()) throw new IOException("Appearance document exceeds theme byte budget");
        var references = references(appearance.resources());
        if (!references.isEmpty()) {
            var bundle = store.open(appearance.resources().bundle());
            for (var item : references.entrySet()) bundle.require(item.getKey(), item.getValue());
            store.exportBundle(bundle.digest(), document, output);
            return;
        }
        if (bytes.length > limits.expandedBytes() || 22 + bytes.length + 96 > limits.archiveBytes()) {
            throw new IOException("Export exceeds theme byte budget");
        }
        // An appearance without binary assets needs no installed bundle or storage allocation.
        try (var zip = new ZipOutputStream(new FilterOutputStream(output) {
            @Override public void close() throws IOException { flush(); }
        })) {
            writeEntry(zip, "theme.json", bytes);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Theme export cancelled");
        CRC32 crc = new CRC32(); crc.update(bytes);
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED); entry.setSize(bytes.length); entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue()); entry.setTime(315619200000L);
        zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
    }
}
