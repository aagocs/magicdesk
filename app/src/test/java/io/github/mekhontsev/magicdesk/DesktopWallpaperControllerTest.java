package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public final class DesktopWallpaperControllerTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void absentCustomFileRestoresBundledWallpaperAndClearsCache() throws Exception {
        verifySourceSelection("""
                Files.writeString(cache.toPath(), "valid");
                WallpaperResult result = fixture.loadWallpaper(1920, 1080, () -> false);
                check(result.asset.equals("bundled"), "no custom file selects the bundled image");
                check(!result.custom && !result.fallback, "bundled artwork is a normal source");
                check(!cache.exists(), "removed override cannot return through the cache");
                check(diagnostics == 0, "default selection is not a compatibility failure");
                """);
    }

    @Test
    public void customFileTakesPriorityOverCacheAndBundledWallpaper() throws Exception {
        verifySourceSelection("""
                customAvailable = true;
                Files.writeString(cache.toPath(), "valid");
                WallpaperResult result = fixture.loadWallpaper(2560, 1080, () -> false);
                check(result.asset.equals("custom") && result.custom, "custom image takes priority");
                check(defaultLoads == 0 && diagnostics == 0, "no default load or spurious warning");
                """);
    }

    @Test
    public void failedCustomReadUsesLastValidCacheOrBundledWallpaper() throws Exception {
        verifySourceSelection("""
                customReadFails = true;
                Files.writeString(cache.toPath(), "valid");
                WallpaperResult result = fixture.loadWallpaper(1920, 1080, () -> false);
                check(result.asset.equals("cached") && result.custom, "last valid image is retained");
                Files.writeString(cache.toPath(), "broken");
                result = fixture.loadWallpaper(1920, 1080, () -> false);
                check(result.asset.equals("bundled"), "invalid cache falls back to bundled artwork");
                check(!cache.exists(), "invalid cache is discarded");
                check(diagnostics == 2, "custom failures remain observable");
                """);
    }

    @Test
    public void unavailableShellDoesNotPreventBundledOrCachedWallpaper() throws Exception {
        verifySourceSelection("""
                ShellAccess.ready = false;
                WallpaperResult result = fixture.loadWallpaper(1216, 2688, () -> false);
                check(result.asset.equals("bundled"), "bundled image needs no shell service");
                Files.writeString(cache.toPath(), "valid");
                result = fixture.loadWallpaper(1216, 2688, () -> false);
                check(result.asset.equals("cached"), "cached custom selection remains usable");
                check(copyAttempts == 0 && diagnostics == 0, "no privileged read or warning");
                """);
    }

    @Test
    public void cancelledCustomReadDoesNotLoadFallbackOrReportFailure() throws Exception {
        verifySourceSelection("""
                try {
                    fixture.loadWallpaper(1920, 1080, () -> true);
                    throw new AssertionError("cancelled load returned a frame");
                } catch (InterruptedIOException expected) { }
                check(defaultLoads == 0 && diagnostics == 0, "cancellation is not a source failure");
                """);
    }

    private static void verifySourceSelection(final String assertions) throws Exception {
        RuntimeSourceFixture.verify("""
                interface BooleanSupplier extends java.util.function.BooleanSupplier { }
                static final String TAG = "test";
                static File directory, pending;
                static boolean customAvailable, customReadFails;
                static int copyAttempts, defaultLoads, diagnostics;
                static final class Context { File getCacheDir() { return directory; } }
                final Context mContext = new Context();
                static final class ShellAccess {
                    static boolean ready = true;
                    static boolean isReady() { return ready; }
                }
                static final class Log { static void w(String tag, String text, Throwable error) { } }
                static final class CompatibilityDiagnostics {
                    static void record(String code, String text, String detail, Throwable error) {
                        diagnostics++;
                    }
                }
                static final class ContentStreamCopy {
                    static void checkCancelled(BooleanSupplier cancelled) throws IOException {
                        if (cancelled.getAsBoolean()) throw new InterruptedIOException("cancelled");
                    }
                }
                static final class WallpaperResult {
                    final String asset;
                    final boolean custom, fallback;
                    WallpaperResult(String b, boolean c, boolean f) { asset=b; custom=c; fallback=f; }
                }
                static File createPendingFile(File cache) throws IOException {
                    pending = File.createTempFile("wallpaper-", ".pending", cache);
                    return pending;
                }
                static boolean copyCustomWallpaper(File file, BooleanSupplier cancelled) throws IOException {
                    ContentStreamCopy.checkCancelled(cancelled);
                    copyAttempts++;
                    if (customReadFails) throw new IOException("unreadable");
                    return customAvailable;
                }
                WallpaperResult decodeAndCache(File source, File cache, int w, int h,
                        BooleanSupplier cancelled) { return new WallpaperResult("custom", true, false); }
                String decodeWallpaper(File file, int w, int h) throws IOException {
                    if (!Files.readString(file.toPath()).equals("valid")) throw new IOException("corrupt");
                    return "cached";
                }
                WallpaperResult defaultWallpaper(int w, int h) {
                    defaultLoads++;
                    return new WallpaperResult("bundled", false, false);
                }
                """ + RuntimeSourceFixture.methods("DesktopWallpaperController",
                        "loadWallpaper", "cachedOrDefault", "usefulMessage")
                + """
                public static void verify() throws Exception {
                    directory = Files.createTempDirectory("wallpaper-selection-").toFile();
                    File cache = new File(directory, "desktop-custom-wallpaper");
                    Fixture fixture = new Fixture();
                    try {
                """ + assertions + """
                        check(pending == null || !pending.exists(), "temporary transfer is cleaned up");
                    } finally {
                        try (var files = Files.walk(directory.toPath())) {
                            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                                Files.delete(path);
                            }
                        }
                    }
                }
                """);
    }

    @Test
    public void panoramicAndPortraitImagesHaveABoundedDecodedAllocation() {
        assertEquals(4, WallpaperPolicy.calculateSampleSize(
                100_000, 1_000, 1920, 1080));
        assertEquals(4, WallpaperPolicy.calculateSampleSize(
                1_000, 100_000, 1080, 1920));
    }

    @Test
    public void ordinaryWallpapersKeepExistingResolutionSampling() {
        assertEquals(1, WallpaperPolicy.calculateSampleSize(
                1920, 1080, 1920, 1080));
        assertEquals(4, WallpaperPolicy.calculateSampleSize(
                7680, 4320, 1920, 1080));
        assertEquals(1, WallpaperPolicy.calculateSampleSize(
                640, 480, 1920, 1080));
    }

    @Test
    public void samplingBoundsRoundedDimensionsWithoutIntegerOverflow() {
        final int[][] dimensions = {
                {Integer.MAX_VALUE, Integer.MAX_VALUE},
                {Integer.MAX_VALUE, 1},
                {1, Integer.MAX_VALUE},
                {100_001, 1_001},
                {4097, 4097}
        };
        for (final int[] size : dimensions) {
            final int sample = WallpaperPolicy.calculateSampleSize(
                    size[0], size[1], Integer.MAX_VALUE, Integer.MAX_VALUE);
            assertTrue(sample > 0 && (sample & (sample - 1)) == 0);
            final long width = (size[0] + sample - 1L) / sample;
            final long height = (size[1] + sample - 1L) / sample;
            assertTrue(width * height <= 16L * 1024 * 1024);
        }
    }

    @Test
    public void samplingRejectsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class, () ->
                WallpaperPolicy.calculateSampleSize(0, 100, 100, 100));
        assertThrows(IllegalArgumentException.class, () ->
                WallpaperPolicy.calculateSampleSize(100, 100, 0, 100));
    }

    @Test
    public void overlappingLoadsOwnIndependentTemporaryFiles() throws IOException {
        final File directory = temporary.newFolder();
        final File first = DesktopWallpaperController.createPendingFile(directory);
        Files.writeString(first.toPath(), "first image");
        final File second = DesktopWallpaperController.createPendingFile(directory);
        Files.writeString(second.toPath(), "second image");

        assertNotEquals(first, second);
        assertEquals("first image", Files.readString(first.toPath()));
        assertEquals("second image", Files.readString(second.toPath()));
        Files.delete(first.toPath());
        assertEquals("second image", Files.readString(second.toPath()));
    }

    @Test
    public void pendingFileIsReservedAndDoesNotReplaceUnownedFiles() throws IOException {
        final File directory = temporary.newFolder();
        final File unrelated = new File(directory, "desktop-wallpaper.pending");
        Files.writeString(unrelated.toPath(), "not ours");
        final File pending = DesktopWallpaperController.createPendingFile(directory);

        assertNotEquals(unrelated, pending);
        assertTrue(pending.isFile());
        assertEquals(directory.getCanonicalFile(), pending.getParentFile().getCanonicalFile());
        assertEquals(0, pending.length());
        assertEquals("not ours", Files.readString(unrelated.toPath()));
    }
}
