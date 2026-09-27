package io.github.mekhontsev.magicdesk;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Immutable, validated media plus a static poster. Playback instances are never shared. */
final class WallpaperAsset {
    enum Kind { IMAGE, ANIMATED_IMAGE, VIDEO, SHADER }
    private final Kind kind;
    private final Bitmap poster;
    private final byte[] encoded;
    private final int width, height;
    private final ShaderWallpaperAsset shader;

    private WallpaperAsset(Kind kind, Bitmap poster, byte[] encoded, int width, int height) {
        this(kind, poster, encoded, width, height, null);
    }
    private WallpaperAsset(Kind kind, Bitmap poster, byte[] encoded, int width, int height, ShaderWallpaperAsset shader) {
        this.kind = kind; this.poster = poster; this.encoded = encoded; this.width = width; this.height = height;
        this.shader = shader;
        poster.setDensity(Bitmap.DENSITY_NONE);
    }
    static WallpaperAsset image(Bitmap bitmap) {
        return new WallpaperAsset(Kind.IMAGE, bitmap, null, bitmap.getWidth(), bitmap.getHeight());
    }
    Kind kind() { return kind; }
    Bitmap poster() { return poster; }
    int width() { return width; }
    int height() { return height; }
    long retainedBytes() { return poster.getAllocationByteCount() + (encoded == null ? 0L : encoded.length)
            + (shader == null ? 0L : shader.retainedBytes()); }
    static WallpaperAsset shader(Bitmap poster, ShaderWallpaperAsset shader) {
        return new WallpaperAsset(Kind.SHADER, poster, null, poster.getWidth(), poster.getHeight(), shader);
    }
    ShaderWallpaperAsset shader() { return shader; }

    /** The worker transfers ownership of bytes; callers must not modify them after this call. */
    static WallpaperAsset decode(byte[] bytes, int targetWidth, int targetHeight) throws IOException {
        if (bytes.length == 0 || bytes.length > ShellDesktopDirectory.MAX_WALLPAPER_BYTES) {
            throw new IOException("Invalid wallpaper byte size");
        }
        if (videoSignature(bytes)) return video(bytes);
        boolean[] animated = {false};
        int[] size = new int[2];
        try {
            Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)), (decoder, info, source) -> {
                size[0] = info.getSize().getWidth(); size[1] = info.getSize().getHeight();
                animated[0] = info.isAnimated();
                if (animated[0] && (long) size[0] * size[1] > WallpaperPolicy.MAX_MOTION_PIXELS) {
                    throw new IllegalArgumentException("Animated wallpaper exceeds 4 megapixels");
                }
                decoder.setTargetSampleSize(WallpaperPolicy.calculateSampleSize(size[0], size[1], targetWidth, targetHeight));
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
                decoder.setOnPartialImageListener(error -> false);
            });
            return new WallpaperAsset(animated[0] ? Kind.ANIMATED_IMAGE : Kind.IMAGE,
                    bitmap, animated[0] ? bytes : null, size[0], size[1]);
        } catch (RuntimeException error) { throw new IOException("Invalid wallpaper image", error); }
    }

    static boolean videoSignature(byte[] bytes) {
        return bytes.length >= 12 && (ByteBuffer.wrap(bytes).getInt(4) == 0x66747970
                || ByteBuffer.wrap(bytes).getInt(0) == 0x1a45dfa3);
    }

    private static WallpaperAsset video(byte[] bytes) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try (MediaDataSource data = dataSource(bytes); MediaMetadataRetriever metadata = new MediaMetadataRetriever()) {
            extractor.setDataSource(data);
            MediaFormat video = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat track = extractor.getTrackFormat(i);
                String mime = track.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    if (video != null) throw new IOException("Wallpaper must contain one video track");
                    video = track;
                }
            }
            if (video == null || extractor.getDrmInitData() != null) throw new IOException("Wallpaper video must be unencrypted");
            int width = video.getInteger(MediaFormat.KEY_WIDTH), height = video.getInteger(MediaFormat.KEY_HEIGHT);
            metadata.setDataSource(data);
            long duration = Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            float rate = video.containsKey(MediaFormat.KEY_FRAME_RATE) ? video.getNumber(MediaFormat.KEY_FRAME_RATE).floatValue() : 0;
            WallpaperPolicy.video(width, height, duration, rate);
            Bitmap poster = metadata.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (poster == null) throw new IOException("Wallpaper video has no decodable frame");
            if ((long) poster.getWidth() * poster.getHeight() > WallpaperPolicy.MAX_MOTION_PIXELS) {
                poster.recycle();
                throw new IOException("Decoded wallpaper video exceeds 4 megapixels");
            }
            // Metadata rotation is already applied to the poster and to MediaPlayer output.
            return new WallpaperAsset(Kind.VIDEO, poster, bytes, poster.getWidth(), poster.getHeight());
        } catch (RuntimeException error) { throw new IOException("Invalid wallpaper video", error); }
        finally { extractor.release(); }
    }

    AnimatedImageDrawable animation(int displayWidth, int displayHeight) throws IOException {
        if (kind != Kind.ANIMATED_IMAGE) throw new IllegalStateException("Not an animated image");
        var drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(encoded)), (decoder, info, source) -> {
            decoder.setTargetSampleSize(WallpaperPolicy.calculateSampleSize(width, height, displayWidth, displayHeight));
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            decoder.setOnPartialImageListener(error -> false);
        });
        if (!(drawable instanceof AnimatedImageDrawable animation)) throw new IOException("Animated wallpaper decoder unavailable");
        animation.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
        return animation;
    }

    MediaDataSource openVideo() {
        if (kind != Kind.VIDEO) throw new IllegalStateException("Not a video");
        return dataSource(encoded);
    }

    private static MediaDataSource dataSource(byte[] bytes) {
        return new MediaDataSource() {
            @Override public int readAt(long position, byte[] buffer, int offset, int size) {
                if (position < 0) throw new IllegalArgumentException("Negative media offset");
                if (size == 0) return 0;
                if (position >= bytes.length) return -1;
                int count = (int) Math.min(size, bytes.length - position);
                System.arraycopy(bytes, (int) position, buffer, offset, count);
                return count;
            }
            @Override public long getSize() { return bytes.length; }
            @Override public void close() { }
        };
    }
}
