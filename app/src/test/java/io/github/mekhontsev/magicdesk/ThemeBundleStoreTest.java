package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Real ZIP/filesystem tests with a deterministic decoder boundary, not Android's stub graphics. */
public final class ThemeBundleStoreTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZ1cAAAAASUVORK5CYII=");
    private static final byte[] JSON = "{}".getBytes(StandardCharsets.UTF_8);
    private static final ThemeBundle.MediaValidator HEADER = (path, kind, bytes, limits, remaining) -> {
        if (kind == ThemeBundle.Kind.FONT) return ThemeBundle.ImageSize.NONE;
        return new ThemeBundle.ImageSize(ByteBuffer.wrap(bytes).getInt(16), ByteBuffer.wrap(bytes).getInt(20));
    };
    private Path temporary;
    private Path root;
    private ThemeBundleStore store;

    @Before public void setup() throws Exception {
        temporary = Files.createTempDirectory("theme-bundle-test-");
        root = temporary.resolve("bundles");
        store = new ThemeBundleStore(root, HEADER);
    }
    @After public void cleanup() throws Exception { ThemeBundleFiles.deleteTree(temporary); }

    @Test public void importOpenExportRoundTripUsesContentIdentity() throws Exception {
        ThemeBundle first = store.importBundle(stream(zip(assets(), false)));
        assertTrue(first.digest().matches("[0-9a-f]{64}"));
        assertEquals("{}", first.themeJson());
        assertEquals(2, first.entries().size());
        assertArrayEquals(PNG, first.readAsset("icons/test.png", ThemeBundle.Kind.ICON));
        assertEquals(first.entries(), store.open(first.digest()).entries());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        store.exportBundle(first.digest(), output);
        ThemeBundle second = new ThemeBundleStore(temporary.resolve("other"), HEADER).importBundle(stream(output.toByteArray()));
        assertEquals(first.digest(), second.digest());
        assertEquals(first.entries(), second.entries());
        assertThrows(UnsupportedOperationException.class, () -> first.entries().clear());
    }

    @Test public void zipOrderCompressionAndDirectoriesDoNotChangeDigest() throws Exception {
        ThemeBundle first = store.importBundle(stream(zip(assets(), false)));
        Map<String, byte[]> reordered = new LinkedHashMap<>();
        reordered.put("icons/", new byte[0]);
        reordered.put("icons/test.png", PNG);
        reordered.put("theme.json", JSON);
        ThemeBundle second = store.importBundle(stream(zip(reordered, true)));
        assertEquals(first.digest(), second.digest());
        assertEquals(1, installedCount());
    }

    @Test public void changedContentHasNewDigestAndDoesNotModifyPreviousBundle() throws Exception {
        ThemeBundle first = store.importBundle(stream(zip(assets(), false)));
        var changed = assets();
        changed.put("theme.json", "{\"preset\":\"light\"}".getBytes(StandardCharsets.UTF_8));
        ThemeBundle second = store.importBundle(stream(zip(changed, false)));
        assertNotEquals(first.digest(), second.digest());
        assertEquals("{}", store.open(first.digest()).themeJson());
        assertEquals(2, installedCount());
    }

    @Test public void rejectsEscapingAmbiguousAndNonAllowlistedPaths() throws Exception {
        for (String name : new String[] {"../outside.png", "/icons/x.png", "icons/../x.png", "icons/./x.png",
                "icons//x.png", "icons\\x.png", "C:/x.png", "icons/x.png/", "icons/.x.png", "icons/x.y.png",
                "icons/x\u0000.png", "icons/x\n.png", "icons/\u00e9.png", "icons/x.svg", "icons/x.xml",
                "fonts/x.woff", "fonts/x.ttc", "icons/x.png.sh", "run.sh", "app.apk", "lib.so",
                "icons/" + "a/".repeat(9) + "x.png", "icons/" + "a".repeat(161) + ".png"}) {
            var files = new LinkedHashMap<String, byte[]>();
            files.put("theme.json", JSON);
            files.put(name, PNG);
            assertThrows(name, IOException.class, () -> store.importBundle(stream(zip(files, false))));
            assertClean();
        }
        assertFalse(Files.exists(temporary.resolve("outside.png")));
    }

    @Test public void rejectsDuplicateFilesAndDirectories() throws Exception {
        var files = assets();
        files.put("icons/tast.png", PNG);
        byte[] duplicate = replace(zip(files, false), "icons/tast.png", "icons/test.png");
        assertThrows(IOException.class, () -> store.importBundle(stream(duplicate)));
        files = assets();
        files.put("icons/a/", new byte[0]);
        files.put("icons/b/", new byte[0]);
        byte[] directories = replace(zip(files, false), "icons/b/", "icons/a/");
        assertThrows(IOException.class, () -> store.importBundle(stream(directories)));
        assertClean();
    }

    @Test public void asciiCaseIsPreservedButCaseAliasesAreRejected() throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        files.put("theme.json", JSON); files.put("icons/Group/Home.PNG", PNG);
        ThemeBundle bundle = store.importBundle(stream(zip(files, false)));
        assertArrayEquals(PNG, bundle.readAsset("icons/Group/Home.PNG", ThemeBundle.Kind.ICON));
        files.put("icons/Group/home.png", PNG);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(files, false))));
        files.remove("icons/Group/home.png"); files.put("icons/group/Other.png", PNG);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(files, false))));
        assertEquals(1, installedCount());
    }

    @Test public void rejectsFileDirectoryCollisionsBeforePublication() throws Exception {
        for (String path : new String[] {"icons/test.png/", "icons/test.png/child.png"}) {
            var files = assets();
            files.put(path, path.endsWith("/") ? new byte[0] : PNG);
            assertThrows(IOException.class, () -> store.importBundle(stream(zip(files, false))));
        }
        assertClean();
    }

    @Test public void requiresCompleteZipAndThemeObject() throws Exception {
        for (byte[] bytes : new byte[][] {new byte[0], JSON, zip(Map.of("icons/a.png", PNG), false),
                zip(Map.of("theme.json", "[]".getBytes(StandardCharsets.UTF_8)), false),
                zip(Map.of("theme.json", "{} trailing".getBytes(StandardCharsets.UTF_8)), false),
                zip(Map.of("theme.json", new byte[] {(byte) 0xc3, 0x28}), false),
                zip(Map.of("theme.json", ("{\"a\":".repeat(30) + "0" + "}".repeat(30)).getBytes(StandardCharsets.UTF_8)), false)}) {
            assertThrows(IOException.class, () -> store.importBundle(stream(bytes)));
        }
        byte[] valid = zip(assets(), false);
        assertThrows(IOException.class, () -> store.importBundle(stream(Arrays.copyOf(valid, valid.length - 22))));
        assertClean();
    }

    @Test public void rejectsExecutableBytesDisguisedAsImagesOrFontsBeforeDecoder() throws Exception {
        AtomicInteger decoded = new AtomicInteger();
        store = new ThemeBundleStore(root, (path, kind, bytes, limits, remaining) -> {
            decoded.incrementAndGet(); return ThemeBundle.ImageSize.NONE;
        });
        for (String path : new String[] {"icons/a.png", "wallpapers/a.jpg", "icons/a.webp", "fonts/a.ttf", "fonts/a.otf"}) {
            byte[] executable = "#!/bin/sh\nid".getBytes(StandardCharsets.US_ASCII);
            assertThrows(IOException.class, () -> store.importBundle(stream(zip(Map.of("theme.json", JSON, path, executable), false))));
        }
        assertEquals(0, decoded.get());
        assertClean();
    }

    @Test public void platformMediaFailureAbortsPublication() throws Exception {
        store = new ThemeBundleStore(root, (path, kind, bytes, limits, remaining) -> { throw new IOException("bad raster"); });
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(assets(), false))));
        assertClean();
    }

    @Test public void schemaAndReferencesCanBeValidatedBeforeAtomicPublication() throws Exception {
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(assets(), false)), (json, entries) -> {
            assertEquals("{}", json);
            assertEquals(ThemeBundle.Kind.ICON, entries.get("icons/test.png").kind());
            assertEquals(0, installedCount());
            throw new IOException("unknown model property");
        }));
        assertClean();
        assertThrows(IllegalArgumentException.class, () -> store.importBundle(stream(zip(assets(), false)),
                (json, entries) -> { throw new IllegalArgumentException("invalid reference"); }));
        assertClean();
    }

    @Test public void compressedInputIsBoundedEvenWithoutALength() throws Exception {
        Budget budget = new Budget(); budget.archive = 512;
        store = new ThemeBundleStore(root, budget.build(), HEADER);
        AtomicInteger reads = new AtomicInteger();
        InputStream endless = new InputStream() {
            @Override public int read() { reads.incrementAndGet(); return 0; }
            @Override public int read(byte[] b, int off, int len) { Arrays.fill(b, off, off + len, (byte) 0); reads.addAndGet(len); return len; }
        };
        assertThrows(IOException.class, () -> store.importBundle(endless));
        assertEquals(513, reads.get());
        assertClean();
    }

    @Test public void zeroReturningInputCannotSpinOrBypassBudget() throws Exception {
        byte[] content = zip(assets(), false);
        ByteArrayInputStream underlying = stream(content);
        InputStream awkward = new InputStream() {
            @Override public int read(byte[] b, int off, int len) { return 0; }
            @Override public int read() { return underlying.read(); }
        };
        assertEquals(2, store.importBundle(awkward).entries().size());
    }

    @Test public void checksExpandedEntryDocumentCountAndCompressionBudgets() throws Exception {
        byte[] archive = zip(assets(), false);
        for (int mode = 0; mode < 5; mode++) {
            Budget budget = new Budget();
            switch (mode) {
                case 0 -> budget.expanded = PNG.length + 1;
                case 1 -> budget.entry = PNG.length - 1;
                case 2 -> budget.theme = 1;
                case 3 -> budget.entries = 1;
                case 4 -> budget.ratio = 1;
            }
            ThemeBundleStore limited = new ThemeBundleStore(root, budget.build(), HEADER);
            byte[] input = mode == 4 ? zip(Map.of("theme.json", ("{\"a\":\"" + "a".repeat(2048) + "\"}").getBytes(StandardCharsets.UTF_8)), true) : archive;
            assertThrows("budget " + mode, IOException.class, () -> limited.importBundle(stream(input)));
            assertClean();
        }
    }

    @Test public void directoryRecordsCountTowardEntryLimitAndCannotCarryData() throws Exception {
        Budget budget = new Budget(); budget.entries = 2;
        var files = assets(); files.put("icons/", new byte[0]);
        ThemeBundleStore limited = new ThemeBundleStore(root, budget.build(), HEADER);
        assertThrows(IOException.class, () -> limited.importBundle(stream(zip(files, false))));
        files.put("icons/", JSON);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(files, false))));
        assertClean();
    }

    @Test public void exactLimitsAreAccepted() throws Exception {
        byte[] bytes = zip(assets(), false);
        Budget budget = new Budget(); budget.archive = bytes.length; budget.expanded = PNG.length + JSON.length;
        budget.entry = PNG.length; budget.theme = JSON.length; budget.entries = 2; budget.totalPixels = 1;
        store = new ThemeBundleStore(root, budget.build(), HEADER);
        assertEquals(2, store.importBundle(stream(bytes)).entries().size());
    }

    @Test public void verifiesActualBytesCrcAndBothZipDirectories() throws Exception {
        byte[] original = zip(assets(), false);
        byte[] crc = original.clone();
        int dataOffset = 30 + "theme.json".length();
        crc[dataOffset] ^= 1;
        assertThrows(IOException.class, () -> store.importBundle(stream(crc)));
        byte[] size = original.clone();
        int central = find(size, new byte[] {0x50, 0x4b, 1, 2}, 0);
        ByteBuffer.wrap(size).order(ByteOrder.LITTLE_ENDIAN).putInt(central + 24, 1);
        assertThrows(IOException.class, () -> store.importBundle(stream(size)));
        byte[] mismatch = original.clone();
        mismatch[30] = 'x';
        assertThrows(IOException.class, () -> store.importBundle(stream(mismatch)));
        assertClean();
    }

    @Test public void dimensionIndividualAndAggregatePixelBudgetsAreIndependent() throws Exception {
        byte[] large = PNG.clone();
        ByteBuffer.wrap(large).putInt(16, 2000).putInt(20, 2000);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(Map.of("theme.json", JSON, "icons/a.png", large), false))));
        ByteBuffer.wrap(large).putInt(16, 9000).putInt(20, 1);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(Map.of("theme.json", JSON, "wallpapers/a.png", large), false))));
        Budget budget = new Budget(); budget.totalPixels = 1;
        store = new ThemeBundleStore(root, budget.build(), HEADER);
        var files = assets(); files.put("wallpapers/a.png", PNG);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(files, false))));
        assertClean();
    }

    @Test public void declaredSymlinkAndExecutableModesNeverCreateLinksOrExecutables() throws Exception {
        byte[] bytes = zip(assets(), false);
        int central = find(bytes, new byte[] {0x50, 0x4b, 1, 2}, 0);
        central = find(bytes, new byte[] {0x50, 0x4b, 1, 2}, central + 4);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(central + 4, (short) 0x0314);
        buffer.putInt(central + 38, 0120777 << 16);
        ThemeBundle bundle = store.importBundle(stream(bytes));
        Path icon = root.resolve(bundle.digest()).resolve("icons/test.png");
        assertTrue(Files.isRegularFile(icon, LinkOption.NOFOLLOW_LINKS));
        assertFalse(Files.isSymbolicLink(icon));
        var attributes = Files.getFileAttributeView(icon, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes != null) {
            assertEquals(Set.of(PosixFilePermission.OWNER_READ), attributes.readAttributes().permissions());
        }
    }

    @Test public void permissionsUsePathAttributesWithoutAndroidUnsupportedFileStoreProbe() throws Exception {
        Path source = Path.of("src/main/java/io/github/mekhontsev/magicdesk/ThemeBundleFiles.java");
        if (!Files.exists(source)) source = Path.of("app").resolve(source);
        assertFalse(Files.readString(source).contains(".getFileStore("));
        Path directory = Files.createDirectory(temporary.resolve("permissions"));
        var attributes = Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes == null) {
            ThemeBundleFiles.permissions(directory, true, false);
            return;
        }
        ThemeBundleFiles.permissions(directory, true, false);
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE),
                attributes.readAttributes().permissions());
        ThemeBundleFiles.permissions(directory, true, true);
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE),
                attributes.readAttributes().permissions());
        assertThrows(IOException.class, () -> ThemeBundleFiles.permissions(directory.resolve("missing"), false, false));
    }

    @Test public void installedCorruptionIsNotTrustedOrOverwrittenByReimport() throws Exception {
        byte[] archive = zip(assets(), false);
        ThemeBundle bundle = store.importBundle(stream(archive));
        Path json = root.resolve(bundle.digest()).resolve("theme.json");
        ThemeBundleFiles.permissions(json, false, true);
        Files.writeString(json, "{ }");
        assertThrows(IOException.class, () -> store.open(bundle.digest()));
        assertThrows(IOException.class, () -> bundle.readAsset("theme.json", ThemeBundle.Kind.THEME));
        assertThrows(IOException.class, () -> store.importBundle(stream(archive)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> store.exportBundle(bundle.digest(), output));
        assertEquals(0, output.size());
        assertEquals("{ }", Files.readString(json));
    }

    @Test public void installedLinksExtraFilesAndWrongKindsAreRejected() throws Exception {
        ThemeBundle bundle = store.importBundle(stream(zip(assets(), false)));
        assertThrows(IOException.class, () -> bundle.require("icons/test.png", ThemeBundle.Kind.WALLPAPER));
        assertThrows(IOException.class, () -> bundle.require("icons/missing.png", ThemeBundle.Kind.ICON));
        Path directory = root.resolve(bundle.digest());
        Path icons = directory.resolve("icons");
        ThemeBundleFiles.permissions(icons, true, true);
        Files.delete(icons.resolve("test.png"));
        Path outside = temporary.resolve("outside");
        Files.write(outside, PNG);
        Files.createSymbolicLink(icons.resolve("test.png"), outside);
        assertThrows(IOException.class, () -> store.open(bundle.digest()));
        assertThrows(IOException.class, () -> bundle.readAsset("icons/test.png", ThemeBundle.Kind.ICON));
        assertArrayEquals(PNG, Files.readAllBytes(outside));
        Files.delete(icons.resolve("test.png"));
        Files.write(icons.resolve("test.png"), PNG);
        ThemeBundleFiles.permissions(directory, true, true);
        Files.writeString(directory.resolve("run.sh"), "id");
        assertThrows(IOException.class, () -> store.open(bundle.digest()));
    }

    @Test public void symlinkedStoreOrDigestCannotRedirectPublication() throws Exception {
        Path outside = Files.createDirectory(temporary.resolve("outside"));
        Files.createSymbolicLink(root, outside);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(assets(), false))));
        Files.delete(root);
        ThemeBundle valid = store.importBundle(stream(zip(assets(), false)));
        Path directory = root.resolve(valid.digest());
        ThemeBundleFiles.deleteTree(directory);
        Files.createSymbolicLink(directory, outside);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(assets(), false))));
        assertThrows(IOException.class, () -> store.open(valid.digest()));
        try (var files = Files.list(outside)) { assertEquals(0, files.count()); }
    }

    @Test public void digestCannotSelectAnArbitraryFilesystemPath() throws Exception {
        for (String digest : new String[] {"../other", "/etc/passwd", "A".repeat(64), "f".repeat(63), ""}) {
            assertThrows(IOException.class, () -> store.open(digest));
        }
        assertFalse(Files.exists(root));
    }

    @Test public void installedCountAndByteQuotaDoNotRejectDeduplication() throws Exception {
        Budget budget = new Budget(); budget.installed = 1; budget.disk = PNG.length + JSON.length;
        store = new ThemeBundleStore(root, budget.build(), HEADER);
        ThemeBundle first = store.importBundle(stream(zip(assets(), false)));
        assertEquals(first.digest(), store.importBundle(stream(zip(assets(), true))).digest());
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(Map.of("theme.json", "{ }".getBytes(StandardCharsets.UTF_8)), false))));
        assertEquals(first.digest(), store.open(first.digest()).digest());
        budget.installed = 16;
        store = new ThemeBundleStore(root, budget.build(), HEADER);
        assertThrows(IOException.class, () -> store.importBundle(stream(zip(Map.of("theme.json", "{ }".getBytes(StandardCharsets.UTF_8)), false))));
        assertEquals(1, installedCount());
    }

    @Test public void failedInterruptedAndAbandonedImportsLeaveNoPublishedPartialTree() throws Exception {
        Files.createDirectories(root.resolve(".import-old/payload/icons"));
        Files.writeString(root.resolve(".import-old/payload/theme.json"), "unfinished");
        assertThrows(IOException.class, () -> store.importBundle(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("provider disconnected"); }
        }));
        assertClean();
        Thread.currentThread().interrupt();
        try { assertThrows(IOException.class, () -> store.importBundle(stream(zip(assets(), false)))); }
        finally { Thread.interrupted(); }
        assertClean();
    }

    @Test public void importAndExportDoNotCloseCallerOwnedStreams() throws Exception {
        boolean[] closed = new boolean[2];
        InputStream input = new ByteArrayInputStream(zip(assets(), false)) {
            @Override public void close() { closed[0] = true; }
        };
        ThemeBundle bundle = store.importBundle(input);
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public void close() { closed[1] = true; }
        };
        store.exportBundle(bundle.digest(), output);
        assertFalse(closed[0]); assertFalse(closed[1]);
        assertTrue(output.size() > 0);
    }

    @Test public void explicitPruningRetainsEveryRequestedDigestAndReclaimsQuota() throws Exception {
        Budget budget = new Budget(); budget.installed = 2;
        store = new ThemeBundleStore(root, budget.build(), HEADER);
        ThemeBundle active = store.importBundle(stream(zip(assets(), false)));
        ThemeBundle abandoned = store.importBundle(stream(zip(Map.of("theme.json", "{ }".getBytes(StandardCharsets.UTF_8)), false)));
        assertEquals(Set.of(), store.prune(Set.of(active.digest(), abandoned.digest())));
        assertEquals(Set.of(abandoned.digest()), store.prune(Set.of(active.digest())));
        assertEquals(active.digest(), store.open(active.digest()).digest());
        assertThrows(IOException.class, () -> store.open(abandoned.digest()));
        assertEquals(abandoned.digest(), store.importBundle(stream(zip(Map.of("theme.json", "{ }".getBytes(StandardCharsets.UTF_8)), false))).digest());
        assertEquals(Set.of(active.digest(), abandoned.digest()), store.prune(Set.of()));
        assertClean();
    }

    @Test public void pruningCorruptUnusedContentUnlinksLinksWithoutTouchingTargets() throws Exception {
        ThemeBundle bundle = store.importBundle(stream(zip(assets(), false)));
        Path directory = root.resolve(bundle.digest());
        ThemeBundleFiles.permissions(directory, true, true);
        Path outside = temporary.resolve("outside"); Files.writeString(outside, "untouched");
        Files.createSymbolicLink(directory.resolve("injected-link"), outside);
        assertThrows(IOException.class, () -> store.prune(Set.of("../outside")));
        assertEquals(1, installedCount());
        assertEquals(Set.of(bundle.digest()), store.prune(Set.of()));
        assertEquals("untouched", Files.readString(outside));
        assertClean();
    }

    @Test public void exportCanReplaceThemeJsonWithoutChangingInstalledSnapshot() throws Exception {
        ThemeBundle original = store.importBundle(stream(zip(assets(), false)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        String json = "{\"preset\":\"light\"}";
        store.exportBundle(original.digest(), json, output);
        ThemeBundle replacement = store.importBundle(stream(output.toByteArray()));
        assertNotEquals(original.digest(), replacement.digest());
        assertEquals(json, replacement.themeJson());
        assertEquals("{}", store.open(original.digest()).themeJson());
        assertArrayEquals(PNG, replacement.readAsset("icons/test.png", ThemeBundle.Kind.ICON));
        output.reset();
        assertThrows(IOException.class, () -> store.exportBundle(original.digest(), "[]", output));
        assertEquals(0, output.size());
    }

    @Test public void exportUsesBoundedCompressionAndCannotProduceARatioBomb() throws Exception {
        String json = "{\"name\":\"" + "abcdef0123456789".repeat(100) + "\"}";
        byte[] input = zip(Map.of("theme.json", json.getBytes(StandardCharsets.UTF_8)), true);
        Budget budget = new Budget(); budget.archive = input.length;
        store = new ThemeBundleStore(root, budget.build(), HEADER);
        ThemeBundle original = store.importBundle(stream(input));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        store.exportBundle(original.digest(), output);
        assertTrue(output.size() <= input.length);
        assertEquals(original.digest(), store.importBundle(stream(output.toByteArray())).digest());
        Budget strict = new Budget(); strict.ratio = 1;
        store = new ThemeBundleStore(temporary.resolve("strict"), strict.build(), HEADER);
        ThemeBundle uncompressed = store.importBundle(stream(zip(Map.of("theme.json", json.getBytes(StandardCharsets.UTF_8)), false)));
        output.reset(); store.exportBundle(uncompressed.digest(), output);
        assertEquals(uncompressed.digest(), store.importBundle(stream(output.toByteArray())).digest());
    }

    @Test public void invalidExportBudgetLeavesCallerStreamUntouched() throws Exception {
        ThemeBundle original = store.importBundle(stream(zip(assets(), false)));
        Budget budget = new Budget(); budget.archive = 32;
        ThemeBundleStore limited = new ThemeBundleStore(root, budget.build(), HEADER);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> limited.exportBundle(original.digest(), output));
        assertEquals(0, output.size());
        assertEquals(original.digest(), store.open(original.digest()).digest());
    }

    private long installedCount() throws IOException {
        if (!Files.exists(root)) return 0;
        try (var files = Files.list(root)) { return files.filter(path -> path.getFileName().toString().matches("[0-9a-f]{64}")).count(); }
    }
    private void assertClean() throws IOException {
        assertEquals(0, installedCount());
        if (Files.exists(root)) try (var files = Files.list(root)) {
            assertEquals(0, files.filter(path -> !path.getFileName().toString().equals(".lock")).count());
        }
    }
    private static LinkedHashMap<String, byte[]> assets() {
        var files = new LinkedHashMap<String, byte[]>(); files.put("theme.json", JSON); files.put("icons/test.png", PNG); return files;
    }
    private static ByteArrayInputStream stream(byte[] bytes) { return new ByteArrayInputStream(bytes); }
    private static byte[] zip(Map<String, byte[]> files, boolean deflate) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(315532800000L);
                if (!deflate) {
                    CRC32 crc = new CRC32(); crc.update(file.getValue());
                    entry.setMethod(ZipEntry.STORED); entry.setSize(file.getValue().length); entry.setCrc(crc.getValue());
                }
                zip.putNextEntry(entry); zip.write(file.getValue()); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
    private static byte[] replace(byte[] bytes, String oldName, String newName) {
        byte[] old = oldName.getBytes(StandardCharsets.UTF_8), replacement = newName.getBytes(StandardCharsets.UTF_8);
        assertEquals(old.length, replacement.length);
        int from = 0, index;
        while ((index = find(bytes, old, from)) >= 0) {
            System.arraycopy(replacement, 0, bytes, index, replacement.length); from = index + old.length;
        }
        return bytes;
    }
    private static int find(byte[] bytes, byte[] sequence, int start) {
        for (int i = start; i <= bytes.length - sequence.length; i++) {
            boolean match = true;
            for (int j = 0; j < sequence.length; j++) if (bytes[i + j] != sequence[j]) { match = false; break; }
            if (match) return i;
        }
        return -1;
    }
    private static final class Budget {
        final ThemeBundleLimits base = ThemeBundleLimits.DEFAULT;
        long archive = base.archiveBytes(), expanded = base.expandedBytes(), entry = base.entryBytes();
        long totalPixels = base.totalPixels(), disk = base.installedBytes();
        int entries = base.entries(), theme = base.themeBytes(), ratio = base.compressionRatio(), installed = base.installedBundles();
        ThemeBundleLimits build() { return new ThemeBundleLimits(archive, expanded, entries, entry, theme, base.fontBytes(),
                ratio, base.imageDimension(), base.iconPixels(), base.imagePixels(), totalPixels, installed, disk); }
    }
}
