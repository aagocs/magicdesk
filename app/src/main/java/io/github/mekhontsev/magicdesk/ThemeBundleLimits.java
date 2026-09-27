package io.github.mekhontsev.magicdesk;

/** One immutable budget, shared by ZIP admission, installed verification and media decoding. */
public record ThemeBundleLimits(long archiveBytes, long expandedBytes, int entries,
        long entryBytes, int themeBytes, int fontBytes, int compressionRatio,
        int imageDimension, long iconPixels, long imagePixels, long totalPixels,
        int installedBundles, long installedBytes) {
    public static final int PATH_LENGTH = 160;
    public static final int PATH_DEPTH = 8;
    public static final ThemeBundleLimits DEFAULT = new ThemeBundleLimits(
            32L << 20, 64L << 20, 256, 16L << 20, 32768, 4 << 20, 200,
            8192, 1024L * 1024, 16L << 20, 24L << 20, 16, 256L << 20);

    public ThemeBundleLimits {
        if (archiveBytes < 1 || expandedBytes < 1 || entries < 1 || entryBytes < 1
                || entryBytes > Integer.MAX_VALUE || themeBytes < 1 || fontBytes < 1
                || compressionRatio < 1 || imageDimension < 1 || iconPixels < 1
                || imagePixels < 1 || totalPixels < 1 || installedBundles < 1
                || installedBytes < 1 || totalPixels > Long.MAX_VALUE / 8) {
            throw new IllegalArgumentException("Invalid theme bundle budget");
        }
    }

    public void checkImage(ThemeBundle.Kind kind, int width, int height, long remainingPixels)
            throws java.io.IOException {
        long pixels = (long) width * height;
        if ((kind != ThemeBundle.Kind.ICON && kind != ThemeBundle.Kind.WALLPAPER)
                || width < 1 || height < 1 || width > imageDimension || height > imageDimension
                || pixels > (kind == ThemeBundle.Kind.ICON ? iconPixels : imagePixels)
                || pixels > remainingPixels) {
            throw new java.io.IOException("Theme image exceeds dimension/pixel budget");
        }
    }

    long bytes(ThemeBundle.Kind kind) {
        return Math.min(entryBytes, switch (kind) {
            case THEME -> themeBytes;
            case FONT -> fontBytes;
            default -> entryBytes;
        });
    }
}
