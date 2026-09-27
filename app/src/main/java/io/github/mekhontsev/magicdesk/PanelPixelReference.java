package io.github.mekhontsev.magicdesk;

import java.util.ArrayList;
import java.util.List;

/** Opaque foreground witnesses; a translucent backdrop cannot be its own visibility oracle. */
final class PanelPixelReference {
    record Point(int x, int y, int color) { }
    static List<Point> select(int[] pixels, int width, int height) {
        if (width < 1 || height < 1 || pixels.length != (long) width * height) {
            throw new IllegalArgumentException("Invalid panel image");
        }
        int count = 0;
        for (int y = 0; y + 1 < height; y++) for (int x = 0; x + 1 < width; x++) {
            if (opaquePatch(pixels, width, x, y)) count++;
        }
        int samples = Math.min(32, count), rank = 0;
        var result = new ArrayList<Point>(samples);
        // Quantiles of actual foreground pixels also cover sparse, icon-only panels.
        for (int y = 0; y + 1 < height && result.size() < samples; y++) for (int x = 0; x + 1 < width; x++) {
            if (!opaquePatch(pixels, width, x, y)) continue;
            if (result.size() < samples && rank == (long) result.size() * (count - 1) / Math.max(1, samples - 1)) {
                result.add(new Point(x, y, pixels[y * width + x]));
            }
            rank++;
        }
        return List.copyOf(result);
    }

    private static boolean opaquePatch(int[] pixels, int width, int x, int y) {
        int index = y * width + x, color = pixels[index];
        return (color >>> 24) == 255 && matches(color, pixels[index + 1], 2)
                && matches(color, pixels[index + width], 2) && matches(color, pixels[index + width + 1], 2);
    }

    static boolean matches(int expected, int actual, int tolerance) {
        if ((expected >>> 24) != 255 || (actual >>> 24) != 255) return false;
        for (int shift = 0; shift <= 16; shift += 8) {
            if (Math.abs(((expected >>> shift) & 255) - ((actual >>> shift) & 255)) > tolerance) return false;
        }
        return true;
    }

    static boolean matches(List<Point> expected, int[] actual) {
        if (expected.size() < 8 || expected.size() != actual.length) return false;
        for (int i = 0; i < actual.length; i++) if (!matches(expected.get(i).color(), actual[i], 12)) return false;
        return true;
    }
}
