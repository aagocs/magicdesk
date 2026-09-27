package io.github.mekhontsev.magicdesk;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Portable format checks; the injected media decoder and parent's theme schema remain authoritative. */
final class ThemeBundleFormat {
    private ThemeBundleFormat() {}

    static String theme(byte[] bytes) throws IOException {
        final String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException error) { throw new IOException("theme.json is not UTF-8", error); }
        try {
            JSONTokener parser = new JSONTokener(text) {
                private int depth;
                @Override public Object nextValue() throws JSONException {
                    if (++depth > 8) throw new JSONException("Theme JSON nesting exceeds limit");
                    try { return super.nextValue(); } finally { depth--; }
                }
            };
            new JSONObject(parser);
            if (parser.nextClean() != 0) throw new JSONException("Trailing theme JSON content");
        } catch (JSONException error) { throw new IOException("Invalid theme.json", error); }
        return text;
    }

    static void signature(String path, ThemeBundle.Kind kind, byte[] data) throws IOException {
        boolean valid;
        if (kind == ThemeBundle.Kind.THEME) return;
        path = path.toLowerCase(java.util.Locale.ROOT);
        if (path.endsWith(".png")) {
            valid = data.length >= 33 && integer(data, 0) == 0x89504e47
                    && integer(data, 4) == 0x0d0a1a0a && integer(data, 8) == 13
                    && integer(data, 12) == 0x49484452;
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            valid = data.length >= 4 && (data[0] & 255) == 255 && (data[1] & 255) == 216
                    && (data[2] & 255) == 255 && (data[data.length - 2] & 255) == 255
                    && (data[data.length - 1] & 255) == 217;
        } else if (path.endsWith(".webp")) {
            valid = data.length >= 20 && integer(data, 0) == 0x52494646
                    && integer(data, 8) == 0x57454250
                    && Integer.toUnsignedLong(Integer.reverseBytes(integer(data, 4))) == data.length - 8;
        } else if (kind == ThemeBundle.Kind.WALLPAPER && path.endsWith(".gif")) {
            valid = data.length >= 13 && integer(data, 0) == 0x47494638 && data[5] == 'a'
                    && (data[4] == '7' || data[4] == '9');
        } else if (kind == ThemeBundle.Kind.WALLPAPER && path.endsWith(".mp4")) {
            valid = data.length >= 12 && integer(data, 4) == 0x66747970;
        } else if (kind == ThemeBundle.Kind.WALLPAPER && path.endsWith(".webm")) {
            valid = data.length >= 12 && integer(data, 0) == 0x1a45dfa3;
        } else {
            valid = data.length >= 12 && integer(data, 0) == (path.endsWith(".otf") ? 0x4f54544f : 0x00010000);
            if (valid) fontTables(data);
        }
        if (!valid) throw new IOException("Theme asset signature does not match allowlisted type: " + path);
    }

    private static void fontTables(byte[] bytes) throws IOException {
        ByteBuffer input = ByteBuffer.wrap(bytes);
        int count = Short.toUnsignedInt(input.getShort(4));
        int end = 12 + count * 16;
        if (count < 1 || count > 128 || end > bytes.length) throw new IOException("Invalid font table directory");
        var tags = new HashSet<Integer>();
        for (int i = 0; i < count; i++) {
            int base = 12 + i * 16;
            long offset = Integer.toUnsignedLong(input.getInt(base + 8));
            long size = Integer.toUnsignedLong(input.getInt(base + 12));
            if (!tags.add(input.getInt(base)) || offset < end || offset > bytes.length
                    || size > bytes.length - offset) throw new IOException("Invalid font table range");
        }
    }

    private static int integer(byte[] data, int offset) { return ByteBuffer.wrap(data).getInt(offset); }

    static String contentDigest(Map<String, ThemeBundle.Entry> entries) {
        var hash = ThemeBundleFiles.digest();
        hash.update("MagicDesk theme bundle 1\n".getBytes(StandardCharsets.US_ASCII));
        // Sorted names and fixed-width lengths/hashes make this independent of ZIP metadata and order.
        for (var entry : new java.util.TreeMap<>(entries).values()) {
            byte[] name = entry.path().getBytes(StandardCharsets.US_ASCII);
            hash.update(ByteBuffer.allocate(4).putInt(name.length).array());
            hash.update(name);
            hash.update(ByteBuffer.allocate(8).putLong(entry.bytes()).array());
            hash.update(entry.sha256().getBytes(StandardCharsets.US_ASCII));
        }
        return ThemeBundleFiles.hex(hash.digest());
    }
}
