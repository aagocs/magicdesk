package io.github.mekhontsev.magicdesk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

/** Filesystem mechanics only. ZIP attributes never become permissions or filesystem links. */
final class ThemeBundleFiles {
    private ThemeBundleFiles() {}

    static ThemeBundle.Kind kind(String path) throws IOException {
        checkPath(path, false);
        if (path.equals("theme.json")) return ThemeBundle.Kind.THEME;
        String lower = path.toLowerCase(Locale.ROOT);
        if (path.startsWith("fonts/") && (lower.endsWith(".ttf") || lower.endsWith(".otf"))) {
            return ThemeBundle.Kind.FONT;
        }
        if (lower.endsWith(".png") || lower.endsWith(".webp")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            if (path.startsWith("icons/")) return ThemeBundle.Kind.ICON;
            if (path.startsWith("wallpapers/")) return ThemeBundle.Kind.WALLPAPER;
        }
        if (path.startsWith("wallpapers/") && (lower.endsWith(".gif")
                || lower.endsWith(".mp4") || lower.endsWith(".webm"))) return ThemeBundle.Kind.WALLPAPER;
        throw new IOException("Non-allowlisted theme asset: " + path);
    }

    static void checkPath(String name, boolean directory) throws IOException {
        String path = directory && name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (path.isEmpty() || name.length() > ThemeBundleLimits.PATH_LENGTH) {
            throw new IOException("Invalid theme path length");
        }
        String[] parts = path.split("/", -1);
        if (parts.length > ThemeBundleLimits.PATH_DEPTH) throw new IOException("Theme path too deep");
        for (int i = 0; i < parts.length; i++) {
            String grammar = !directory && i == parts.length - 1
                    ? "[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+" : "[a-zA-Z0-9_-]+";
            if (!parts[i].matches(grammar)) throw new IOException("Unsafe theme path: " + name);
        }
        if (directory && !(parts[0].equals("icons") || parts[0].equals("wallpapers")
                || parts[0].equals("fonts"))) throw new IOException("Unexpected theme directory: " + name);
    }

    static void uniqueCase(Map<String, String> seen, String path) throws IOException {
        String[] parts = path.split("/");
        String prefix = "";
        for (String part : parts) {
            prefix = prefix.isEmpty() ? part : prefix + "/" + part;
            String previous = seen.putIfAbsent(prefix.toLowerCase(Locale.ROOT), prefix);
            if (previous != null && !previous.equals(prefix)) throw new IOException("Case-aliased theme path: " + path);
        }
    }

    static void checkDigest(String digest) throws IOException {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) throw new IOException("Invalid theme bundle digest");
    }

    static Path checkedFile(Path directory, String path) throws IOException {
        checkPath(path, false);
        Path cursor = directory;
        requireDirectory(cursor.getParent());
        requireDirectory(cursor);
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            cursor = cursor.resolve(parts[i]);
            requireDirectory(cursor);
        }
        Path file = cursor.resolve(parts[parts.length - 1]);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Theme asset is not a regular file: " + path);
        }
        return file;
    }

    static void requireDirectory(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Theme directory is absent or a link: " + path.getFileName());
        }
    }

    static byte[] read(Path path, long limit) throws IOException {
        try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            return read(input, limit);
        }
    }

    static byte[] read(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output, limit);
        return output.toByteArray();
    }

    static long copy(InputStream input, OutputStream output, long limit) throws IOException {
        byte[] buffer = new byte[8192];
        long count = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Theme operation cancelled");
            int read = input.read(buffer, 0, (int) Math.min(buffer.length - 1, limit - count) + 1);
            if (read == -1) return count;
            if (read == 0) {
                int single = input.read();
                if (single == -1) return count;
                buffer[0] = (byte) single;
                read = 1;
            }
            if (read > limit - count) throw new IOException("Theme byte budget exceeded");
            output.write(buffer, 0, read);
            count += read;
        }
    }

    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    static String sha256(byte[] bytes) { return hex(digest().digest(bytes)); }

    static String hex(byte[] bytes) {
        char[] text = new char[bytes.length * 2];
        String alphabet = "0123456789abcdef";
        for (int i = 0; i < bytes.length; i++) {
            text[i * 2] = alphabet.charAt((bytes[i] & 255) >>> 4);
            text[i * 2 + 1] = alphabet.charAt(bytes[i] & 15);
        }
        return new String(text);
    }

    static void permissions(Path path, boolean directory, boolean writable) throws IOException {
        // Android can expose POSIX attributes while its file-store query is unsupported.
        PosixFileAttributeView attributes = Files.getFileAttributeView(
                path, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes != null) {
            Set<PosixFilePermission> modes = new java.util.HashSet<>();
            modes.add(PosixFilePermission.OWNER_READ);
            if (directory) modes.add(PosixFilePermission.OWNER_EXECUTE);
            if (writable) modes.add(PosixFilePermission.OWNER_WRITE);
            attributes.setPermissions(modes);
        }
    }

    static void deleteTree(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            permissions(path, true, true);
            try (var children = Files.newDirectoryStream(path)) {
                for (Path child : children) deleteTree(child);
            }
        }
        Files.deleteIfExists(path);
    }
}
