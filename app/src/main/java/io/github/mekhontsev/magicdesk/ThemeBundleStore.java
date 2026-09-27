package io.github.mekhontsev.magicdesk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * App-private, worker-thread package storage. No Android, shell, Desktop or URI dependencies.
 * Callers own streams, scheduling and theme-model parsing. The root must be dedicated app-owned
 * storage, never a user-selected directory. All published files are regular, non-executable and
 * read-only on POSIX filesystems. Same-UID code is trusted; hashes detect subsequent corruption.
 */
public final class ThemeBundleStore {
    @FunctionalInterface public interface ThemeValidator {
        void validate(String json, Map<String, ThemeBundle.Entry> entries) throws IOException;
    }

    private static final Object IO_LOCK = new Object();
    private final Path root;
    private final ThemeBundleLimits limits;
    private final ThemeBundle.MediaValidator media;

    public ThemeBundleStore(Path root, ThemeBundle.MediaValidator media) {
        this(root, ThemeBundleLimits.DEFAULT, media);
    }

    public ThemeBundleStore(Path root, ThemeBundleLimits limits, ThemeBundle.MediaValidator media) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        this.limits = Objects.requireNonNull(limits);
        this.media = Objects.requireNonNull(media);
    }

    public ThemeBundleLimits limits() { return limits; }

    public ThemeBundle importBundle(InputStream input) throws IOException {
        return importBundle(input, (json, entries) -> {});
    }

    /** Optional model validation runs before publication; any exception leaves no installed bundle. */
    public ThemeBundle importBundle(InputStream input, ThemeValidator validator) throws IOException {
        Objects.requireNonNull(input);
        Objects.requireNonNull(validator);
        return locked(() -> {
            recoverImports();
            Path work = Files.createTempDirectory(root, ".import-");
            ThemeBundleFiles.permissions(work, true, true);
            Path archive = null;
            try {
                archive = Files.createTempFile(root, ".import-", ".zip");
                try (OutputStream output = Files.newOutputStream(archive)) {
                    ThemeBundleFiles.copy(input, output, limits.archiveBytes());
                }
                Path payload = work;
                Map<String, ThemeBundle.Entry> entries = extract(archive, payload);
                Files.delete(archive);
                String json = ThemeBundleFormat.theme(ThemeBundleFiles.read(payload.resolve("theme.json"), limits.themeBytes()));
                validator.validate(json, Map.copyOf(entries));
                String digest = ThemeBundleFormat.contentDigest(entries);
                Path target = root.resolve(digest);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return verify(digest);
                checkQuota(entries.values().stream().mapToLong(ThemeBundle.Entry::bytes).sum());
                seal(payload);
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Theme import cancelled");
                // No copy fallback: readers must never observe a partially published directory.
                Files.move(payload, target, StandardCopyOption.ATOMIC_MOVE);
                return new ThemeBundle(digest, json, entries, target);
            } finally {
                ThemeBundleFiles.deleteTree(work);
                if (archive != null) Files.deleteIfExists(archive);
            }
        });
    }

    /** Revalidates the complete installed tree and digest; a directory name is not proof of integrity. */
    public ThemeBundle open(String digest) throws IOException {
        ThemeBundleFiles.checkDigest(digest);
        return locked(() -> verify(digest));
    }

    /**
     * Explicit cleanup, never admission-time eviction. The caller must hold its appearance-state
     * gate for the entire call and retain every committed, preview, global and local bundle.
     * The filesystem lock serializes import/export/prune, not the caller's active-reference state.
     * Each unused digest is atomically retired before unlinking; no links are followed.
     */
    public Set<String> prune(Set<String> retainedDigests) throws IOException {
        Set<String> retained = Set.copyOf(retainedDigests);
        for (String digest : retained) ThemeBundleFiles.checkDigest(digest);
        return locked(() -> {
            recoverImports();
            Set<String> candidates = new TreeSet<>();
            try (var children = Files.newDirectoryStream(root)) {
                for (Path child : children) {
                    String digest = child.getFileName().toString();
                    if (digest.equals(".lock")) continue;
                    ThemeBundleFiles.checkDigest(digest);
                    ThemeBundleFiles.requireDirectory(child);
                    if (!retained.contains(digest)) candidates.add(digest);
                }
            }
            Set<String> removed = new TreeSet<>();
            for (String digest : candidates) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Theme cleanup cancelled");
                Path retired = root.resolve(".import-pruned-" + java.util.UUID.randomUUID());
                Files.move(root.resolve(digest), retired, StandardCopyOption.ATOMIC_MOVE);
                ThemeBundleFiles.deleteTree(retired);
                removed.add(digest);
            }
            return Set.copyOf(removed);
        });
    }

    /** Canonical ZIP, without imported permissions, links, comments or other ZIP metadata. */
    public void exportBundle(String digest, OutputStream output) throws IOException {
        export(digest, null, output);
    }

    /** Exports current model JSON with installed assets; the parent removes its bundle digest first. */
    public void exportBundle(String digest, String themeJson, OutputStream output) throws IOException {
        byte[] bytes = Objects.requireNonNull(themeJson).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limits.bytes(ThemeBundle.Kind.THEME)) throw new IOException("Export theme.json exceeds budget");
        ThemeBundleFormat.theme(bytes);
        export(digest, bytes, output);
    }

    private void export(String digest, byte[] themeOverride, OutputStream output) throws IOException {
        ThemeBundleFiles.checkDigest(digest);
        Objects.requireNonNull(output);
        locked(() -> {
            ThemeBundle bundle = verify(digest);
            Map<String, ZipEntry> plan = new TreeMap<>();
            long size = 22, expanded = 0;
            for (var asset : bundle.entries().values()) {
                byte[] bytes = exportBytes(bundle, asset, themeOverride);
                if (bytes.length > limits.expandedBytes() - expanded) throw new IOException("Export expansion budget exceeded");
                expanded += bytes.length;
                long compressed = compressedSize(bytes);
                boolean deflated = compressed < bytes.length && !excessiveRatio(bytes.length, compressed);
                CRC32 crc = new CRC32(); crc.update(bytes);
                ZipEntry entry = new ZipEntry(asset.path());
                entry.setMethod(deflated ? ZipEntry.DEFLATED : ZipEntry.STORED);
                entry.setSize(bytes.length);
                entry.setCompressedSize(deflated ? compressed : bytes.length);
                entry.setCrc(crc.getValue());
                entry.setTime(315619200000L); // 1980-01-02 is in DOS range in every time zone.
                long entrySize = entry.getCompressedSize() + 76L + 2L * asset.path().length();
                if (entrySize > limits.archiveBytes() - size) throw new IOException("Export exceeds compressed bundle budget");
                size += entrySize;
                plan.put(asset.path(), entry);
            }
            Path work = Files.createTempDirectory(root, ".import-export-");
            try {
                Path archive = work.resolve("export.zip");
                try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                    for (var asset : bundle.entries().values()) {
                        zip.putNextEntry(plan.get(asset.path()));
                        zip.write(exportBytes(bundle, asset, themeOverride));
                        zip.closeEntry();
                    }
                }
                if (Files.size(archive) > limits.archiveBytes()) throw new IOException("Export exceeds compressed bundle budget");
                try (InputStream input = Files.newInputStream(archive)) {
                    ThemeBundleFiles.copy(input, output, limits.archiveBytes());
                }
            } finally {
                ThemeBundleFiles.deleteTree(work);
            }
            return null;
        });
    }

    private byte[] exportBytes(ThemeBundle bundle, ThemeBundle.Entry asset, byte[] themeOverride) throws IOException {
        return asset.kind() == ThemeBundle.Kind.THEME && themeOverride != null
                ? themeOverride : bundle.readAsset(asset.path(), asset.kind());
    }

    private static long compressedSize(byte[] bytes) throws IOException {
        Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        try {
            deflater.setInput(bytes); deflater.finish();
            byte[] buffer = new byte[8192];
            long compressed = 0;
            while (!deflater.finished()) {
                if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("Theme export cancelled");
                compressed += deflater.deflate(buffer);
            }
            return compressed;
        } finally { deflater.end(); }
    }

    private Map<String, ThemeBundle.Entry> extract(Path archive, Path payload) throws IOException {
        Map<String, ZipEntry> catalog = new HashMap<>();
        Map<String, String> casePaths = new HashMap<>();
        long declaredBytes = 0;
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                String name = entry.getName();
                ThemeBundleFiles.checkPath(name, entry.isDirectory());
                ThemeBundleFiles.uniqueCase(casePaths, name);
                if (catalog.size() >= limits.entries() || catalog.putIfAbsent(name, entry) != null) {
                    throw new IOException("Duplicate or excessive theme ZIP entries");
                }
                if (entry.getMethod() != ZipEntry.STORED && entry.getMethod() != ZipEntry.DEFLATED) {
                    throw new IOException("Unsupported theme ZIP compression");
                }
                long maximum = entry.isDirectory() ? 0 : limits.bytes(ThemeBundleFiles.kind(name));
                if (entry.getSize() < 0 || entry.getSize() > maximum || entry.getCompressedSize() < 0
                        || entry.getCompressedSize() > limits.archiveBytes()
                        || excessiveRatio(entry.getSize(), entry.getCompressedSize())) {
                    throw new IOException("Theme ZIP entry exceeds budget: " + name);
                }
                if (entry.getSize() > limits.expandedBytes() - declaredBytes) throw new IOException("Theme expansion limit");
                declaredBytes += entry.getSize();
            }
        }
        if (!catalog.containsKey("theme.json")) throw new IOException("Missing theme.json");
        for (String name : catalog.keySet()) {
            String base = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
            if (name.endsWith("/") && catalog.containsKey(base)) throw new IOException("Theme path collision");
            for (int slash = base.indexOf('/'); slash >= 0; slash = base.indexOf('/', slash + 1)) {
                if (catalog.containsKey(base.substring(0, slash))) throw new IOException("Theme file/directory collision");
            }
        }
        Map<String, ThemeBundle.Entry> entries = new TreeMap<>();
        var seen = new HashSet<String>();
        long expanded = 0;
        long pixels = 0;
        // Read local records as well as the central directory, with actual-byte bounds and CRC checks.
        try (var zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry local;
            while ((local = zip.getNextEntry()) != null) {
                String path = local.getName();
                ZipEntry declared = catalog.get(path);
                if (declared == null || !seen.add(path) || local.getMethod() != declared.getMethod()) {
                    throw new IOException("Inconsistent theme ZIP directory");
                }
                long maximum = Math.min(declared.getSize(), limits.expandedBytes() - expanded);
                byte[] bytes = ThemeBundleFiles.read(zip, maximum);
                if (bytes.length != declared.getSize() || local.getCrc() != declared.getCrc()
                        || local.getCompressedSize() != declared.getCompressedSize()) {
                    throw new IOException("Inconsistent theme ZIP entry: " + path);
                }
                zip.closeEntry();
                if (declared.isDirectory()) continue;
                ThemeBundle.Kind kind = ThemeBundleFiles.kind(path);
                ThemeBundle.Entry entry = validate(path, kind, bytes, limits.totalPixels() - pixels);
                expanded += bytes.length;
                pixels += entry.image().pixels();
                entries.put(path, entry);
                Path file = payload.resolve(path);
                Files.createDirectories(file.getParent());
                try (var channel = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    ByteBuffer content = ByteBuffer.wrap(bytes);
                    while (content.hasRemaining()) channel.write(content);
                    channel.force(true);
                }
            }
        }
        if (seen.size() != catalog.size()) throw new IOException("Missing local theme ZIP entries");
        return entries;
    }

    private boolean excessiveRatio(long size, long compressed) {
        return size > 0 && (compressed == 0 || (size - 1) / compressed >= limits.compressionRatio());
    }

    private ThemeBundle.Entry validate(String path, ThemeBundle.Kind kind, byte[] bytes, long remainingPixels)
            throws IOException {
        ThemeBundleFormat.signature(path, kind, bytes);
        ThemeBundle.ImageSize image = ThemeBundle.ImageSize.NONE;
        if (kind == ThemeBundle.Kind.THEME) ThemeBundleFormat.theme(bytes);
        else {
            image = Objects.requireNonNull(media.validate(path, kind, bytes, limits, remainingPixels));
            if (kind == ThemeBundle.Kind.FONT) {
                if (!image.equals(ThemeBundle.ImageSize.NONE)) throw new IOException("Invalid font validation result");
            } else limits.checkImage(kind, image.width(), image.height(), remainingPixels);
        }
        return new ThemeBundle.Entry(path, kind, bytes.length, ThemeBundleFiles.sha256(bytes), image);
    }

    private ThemeBundle verify(String digest) throws IOException {
        Path directory = root.resolve(digest);
        ThemeBundleFiles.requireDirectory(directory);
        Map<String, ThemeBundle.Entry> entries = new TreeMap<>();
        Map<String, String> casePaths = new HashMap<>();
        long expanded = 0;
        long pixels = 0;
        int nodes = 0;
        try (var walk = Files.walk(directory, ThemeBundleLimits.PATH_DEPTH + 1)) {
            var iterator = walk.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (file.equals(directory)) continue;
                if (++nodes > limits.entries() * (ThemeBundleLimits.PATH_DEPTH + 1L)) {
                    throw new IOException("Excessive installed theme paths");
                }
                String path = directory.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                ThemeBundleFiles.uniqueCase(casePaths, path);
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    ThemeBundleFiles.checkPath(path + "/", true);
                    continue;
                }
                if (entries.size() >= limits.entries()) throw new IOException("Excessive installed theme entries");
                ThemeBundle.Kind kind = ThemeBundleFiles.kind(path);
                byte[] bytes = ThemeBundleFiles.read(ThemeBundleFiles.checkedFile(directory, path),
                        Math.min(limits.bytes(kind), limits.expandedBytes() - expanded));
                ThemeBundle.Entry entry = validate(path, kind, bytes, limits.totalPixels() - pixels);
                entries.put(path, entry);
                expanded += bytes.length;
                pixels += entry.image().pixels();
            }
        } catch (java.io.UncheckedIOException error) { throw error.getCause(); }
        if (!entries.containsKey("theme.json") || !ThemeBundleFormat.contentDigest(entries).equals(digest)) {
            throw new IOException("Installed theme bundle digest mismatch");
        }
        String json = ThemeBundleFormat.theme(ThemeBundleFiles.read(directory.resolve("theme.json"), limits.themeBytes()));
        return new ThemeBundle(digest, json, entries, directory);
    }

    private void checkQuota(long additional) throws IOException {
        long bytes = 0;
        int count = 0;
        try (var children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                String name = child.getFileName().toString();
                if (name.equals(".lock") || name.startsWith(".import-")) continue;
                ThemeBundleFiles.checkDigest(name);
                if (++count >= limits.installedBundles()) throw new IOException("Installed theme bundle count limit");
                ThemeBundleFiles.requireDirectory(child);
                int nodes = 0;
                try (var walk = Files.walk(child, ThemeBundleLimits.PATH_DEPTH + 1)) {
                    var iterator = walk.iterator();
                    while (iterator.hasNext()) {
                        Path item = iterator.next();
                        if (++nodes > limits.entries() * (ThemeBundleLimits.PATH_DEPTH + 1L)) {
                            throw new IOException("Excessive installed theme paths");
                        }
                        if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) continue;
                        if (!Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Linked theme storage");
                        long size = Files.size(item);
                        if (size > limits.installedBytes() - bytes) throw new IOException("Installed theme storage limit");
                        bytes += size;
                    }
                }
            }
        } catch (java.io.UncheckedIOException error) { throw error.getCause(); }
        if (additional > limits.installedBytes() - bytes) throw new IOException("Installed theme storage limit");
    }

    private void seal(Path directory) throws IOException {
        try (var children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) seal(child);
                else ThemeBundleFiles.permissions(child, false, false);
            }
        }
        ThemeBundleFiles.permissions(directory, true, false);
    }

    private void recoverImports() throws IOException {
        // The cross-process lock guarantees these belong to interrupted imports, not live operations.
        try (var children = Files.newDirectoryStream(root, ".import-*")) {
            for (Path child : children) ThemeBundleFiles.deleteTree(child);
        }
    }

    @FunctionalInterface private interface Operation<T> { T run() throws IOException; }
    private <T> T locked(Operation<T> operation) throws IOException {
        synchronized (IO_LOCK) {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(root);
            ThemeBundleFiles.requireDirectory(root);
            ThemeBundleFiles.permissions(root, true, true);
            try (var channel = FileChannel.open(root.resolve(".lock"), StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    var lock = channel.lock()) {
                return operation.run();
            }
        }
    }
}
