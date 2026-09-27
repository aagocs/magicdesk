package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Looper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Android decoder adapter, independent of appearance models and runtime services. Import/open and
 * prepare run on a worker before applying a theme. UI bindings retain Prepared; draw never loads.
 * Borrowed immutable bitmaps must not be recycled by consumers. Cache eviction does not recycle them.
 */
public final class ThemeAssets {
    private static ThemeAssets shared;
    private final ThemeAssetsCache<Bitmap> bitmaps = new ThemeAssetsCache<>(128, 48L << 20);
    private final ThemeAssetsCache<FontFaces> fonts = new ThemeAssetsCache<>(8, 16L << 20);
    private final ThemeBundleStore store;

    public static synchronized ThemeAssets get(Context context) {
        if (shared == null) {
            shared = new ThemeAssets(context.getApplicationContext().getNoBackupFilesDir().toPath().resolve("theme-bundles"));
        }
        return shared;
    }

    /** Root must be dedicated app-private storage. Construction performs no decoding or disk reads. */
    public ThemeAssets(Path root) {
        store = new ThemeBundleStore(root, this::validate);
    }

    public ThemeBundleStore store() { return store; }

    public Prepared prepare(String bundle, Collection<String> iconPaths, String font, String wallpaper)
            throws IOException {
        worker();
        Objects.requireNonNull(bundle);
        Objects.requireNonNull(iconPaths);
        Objects.requireNonNull(font);
        Objects.requireNonNull(wallpaper);
        if (bundle.isEmpty()) {
            if (!iconPaths.isEmpty() || !font.isEmpty() || !wallpaper.isEmpty()) {
                throw new IOException("Theme asset references require a bundle digest");
            }
            return Prepared.EMPTY;
        }
        return prepare(store.open(bundle), iconPaths, font, wallpaper);
    }

    /** Reuses an import/open receipt, checking every requested file against its recorded hash. */
    public Prepared prepare(ThemeBundle bundle, Collection<String> iconPaths, String font, String wallpaper)
            throws IOException {
        worker();
        Objects.requireNonNull(bundle);
        Objects.requireNonNull(iconPaths);
        Objects.requireNonNull(font);
        Objects.requireNonNull(wallpaper);
        if (iconPaths.size() > store.limits().entries()) throw new IOException("Too many theme icon references");
        var requested = new TreeSet<>(iconPaths);
        for (String path : requested) require(bundle, path, ThemeBundle.Kind.ICON);
        if (!font.isEmpty()) require(bundle, font, ThemeBundle.Kind.FONT);
        if (!wallpaper.isEmpty()) require(bundle, wallpaper, ThemeBundle.Kind.WALLPAPER);
        Map<String, Bitmap> icons = new HashMap<>();
        long pixels = 0;
        for (String path : requested) {
            Bitmap bitmap = bitmap(path, ThemeBundle.Kind.ICON, bundle.readAsset(path, ThemeBundle.Kind.ICON),
                    store.limits(), store.limits().totalPixels() - pixels);
            pixels += (long) bitmap.getWidth() * bitmap.getHeight();
            icons.put(path, bitmap);
        }
        Bitmap background = wallpaper.isEmpty() ? null : bitmap(wallpaper, ThemeBundle.Kind.WALLPAPER,
                bundle.readAsset(wallpaper, ThemeBundle.Kind.WALLPAPER), store.limits(), store.limits().totalPixels() - pixels);
        FontFaces faces = font.isEmpty() ? null : font(font, bundle.readAsset(font, ThemeBundle.Kind.FONT));
        return new Prepared(bundle.digest(), icons, faces, background);
    }

    /** Memory pressure hook. Existing prepared snapshots remain valid. */
    public synchronized void clearCache() { bitmaps.clear(); fonts.clear(); }

    private void require(ThemeBundle bundle, String path, ThemeBundle.Kind kind) throws IOException {
        if (bundle.require(path, kind).bytes() > store.limits().bytes(kind)) {
            throw new IOException("Theme reference exceeds asset byte budget: " + path);
        }
    }

    private ThemeBundle.ImageSize validate(String path, ThemeBundle.Kind kind, byte[] bytes,
            ThemeBundleLimits limits, long remainingPixels) throws IOException {
        worker();
        if (kind == ThemeBundle.Kind.FONT) {
            font(path, bytes);
            return ThemeBundle.ImageSize.NONE;
        }
        Bitmap bitmap = bitmap(path, kind, bytes, limits, remainingPixels);
        return new ThemeBundle.ImageSize(bitmap.getWidth(), bitmap.getHeight());
    }

    private synchronized Bitmap bitmap(String path, ThemeBundle.Kind kind, byte[] bytes,
            ThemeBundleLimits limits, long remainingPixels) throws IOException {
        ThemeBundleFormat.signature(path, kind, bytes);
        String key = ThemeBundleFiles.sha256(bytes);
        Bitmap cached = bitmaps.get(key);
        if (cached != null && !cached.isRecycled()) {
            limits.checkImage(kind, cached.getWidth(), cached.getHeight(), remainingPixels);
            return cached;
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        final String expected = lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : "image/jpeg";
        final Bitmap decoded;
        try {
            decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), (decoder, info, source) -> {
                if (!expected.equals(info.getMimeType()) || info.isAnimated()) {
                    throw new IllegalArgumentException("Theme images must be static allowlisted raster content");
                }
                try { limits.checkImage(kind, info.getSize().getWidth(), info.getSize().getHeight(), remainingPixels); }
                catch (IOException error) { throw new IllegalArgumentException(error.getMessage(), error); }
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setMutableRequired(false);
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                decoder.setOnPartialImageListener(error -> false);
            });
        } catch (RuntimeException error) { throw new IOException("Invalid theme image: " + path, error); }
        try {
            limits.checkImage(kind, decoded.getWidth(), decoded.getHeight(), remainingPixels);
            if (decoded.isMutable() || decoded.getAllocationByteCount() > remainingPixels * 4
                    || decoded.getAllocationByteCount() > (long) decoded.getWidth() * decoded.getHeight() * 4) {
                throw new IOException("Theme decoded image exceeds memory budget");
            }
        } catch (IOException error) { decoded.recycle(); throw error; }
        bitmaps.put(key, decoded, decoded.getAllocationByteCount());
        return decoded;
    }

    private synchronized FontFaces font(String path, byte[] bytes) throws IOException {
        ThemeBundleFormat.signature(path, ThemeBundle.Kind.FONT, bytes);
        String key = ThemeBundleFiles.sha256(bytes);
        FontFaces cached = fonts.get(key);
        if (cached != null) return cached;
        try {
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.put(bytes).flip();
            Font decoded = new Font.Builder(buffer.asReadOnlyBuffer()).build();
            Typeface base = new Typeface.CustomFallbackBuilder(new FontFamily.Builder(decoded).build()).build();
            FontFaces faces = new FontFaces(decoded, new Typeface[] {base,
                    Typeface.create(base, Typeface.BOLD), Typeface.create(base, Typeface.ITALIC),
                    Typeface.create(base, Typeface.BOLD_ITALIC)});
            fonts.put(key, faces, bytes.length);
            return faces;
        } catch (RuntimeException error) { throw new IOException("Invalid theme font: " + path, error); }
    }

    private static void worker() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Prepare/import theme assets on a worker, never in a draw or UI callback");
        }
    }

    private record FontFaces(Font source, Typeface[] styles) {}

    /** Fully prepared memory-only resolution. Null means no requested override; use the symbolic default. */
    public static final class Prepared {
        public static final Prepared EMPTY = new Prepared("", Map.of(), null, null);
        private final String bundle;
        private final Map<String, Bitmap> icons;
        private final FontFaces font;
        private final Bitmap wallpaper;

        private Prepared(String bundle, Map<String, Bitmap> icons, FontFaces font, Bitmap wallpaper) {
            this.bundle = bundle;
            this.icons = Map.copyOf(icons);
            this.font = font;
            this.wallpaper = wallpaper;
        }
        public String bundle() { return bundle; }
        public Bitmap icon(String path) { return icons.get(path); }
        public Typeface font() { return font(Typeface.NORMAL); }
        public Typeface font(int style) {
            if (style < 0 || style > Typeface.BOLD_ITALIC) throw new IllegalArgumentException("Invalid font style");
            return font == null ? null : font.styles()[style];
        }
        public Bitmap wallpaper() { return wallpaper; }
    }
}
