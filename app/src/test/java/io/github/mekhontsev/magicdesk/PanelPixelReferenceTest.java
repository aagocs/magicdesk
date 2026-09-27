package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import java.util.Arrays;
import org.junit.Test;

public final class PanelPixelReferenceTest {
    @Test public void sparseIconRowsStillSupplyDistributedWitnesses() {
        int width = 800, height = 64;
        int[] pixels = new int[width * height];
        for (int left : new int[] {12, 54, 105, 700, 744}) {
            for (int y = 24; y < 32; y++) for (int x = left; x < left + 8; x++) pixels[y * width + x] = 0xffeeeeee;
        }
        var reference = PanelPixelReference.select(pixels, width, height);
        assertEquals(32, reference.size());
        assertTrue(reference.stream().anyMatch(p -> p.x() < 54));
        assertTrue(reference.stream().anyMatch(p -> p.x() >= 744));
        assertEquals(reference.size(), reference.stream().distinct().count());
    }

    @Test public void translucentAndBlurredBackdropsDoNotSupplyOpaqueWitnesses() {
        int width = 80, height = 40;
        for (int background : new int[] {0, 0x66111827, 0x26111827, 0xe0111827}) {
            int[] pixels = new int[width * height];
            Arrays.fill(pixels, background);
            assertTrue(PanelPixelReference.select(pixels, width, height).isEmpty());
            for (int row = 0; row < 4; row++) for (int column = 0; column < 8; column++) {
                int x = column * 10 + 4, y = row * 10 + 4;
                pixels[y * width + x] = pixels[y * width + x + 1]
                        = pixels[(y + 1) * width + x] = pixels[(y + 1) * width + x + 1] = 0xffeeeeee;
            }
            var reference = PanelPixelReference.select(pixels, width, height);
            assertEquals(32, reference.size());
            assertTrue(reference.stream().allMatch(point -> point.color() == 0xffeeeeee));
            int[] actual = new int[32];
            Arrays.fill(actual, 0xffe9e9e9);
            assertTrue(PanelPixelReference.matches(reference, actual));
            Arrays.fill(actual, DesktopSelfTestFixtureAppearance.PRIMARY.color());
            assertFalse("A panel behind a fullscreen task must fail", PanelPixelReference.matches(reference, actual));
            Arrays.fill(actual, 0xffeeeeee); actual[12] = 0xff000000;
            assertFalse("Partial occlusion must fail", PanelPixelReference.matches(reference, actual));
        }
    }

    @Test public void antialiasEdgesSparseSamplesAndIncorrectAlphaCannotPass() {
        int[] pixels = new int[800];
        Arrays.fill(pixels, 0xfeffffff);
        assertTrue(PanelPixelReference.select(pixels, 40, 20).isEmpty());
        pixels[41] = 0xffffffff;
        assertTrue(PanelPixelReference.select(pixels, 40, 20).isEmpty());
        pixels[42] = pixels[81] = pixels[82] = 0xffffffff;
        var sparse = PanelPixelReference.select(pixels, 40, 20);
        assertEquals(1, sparse.size());
        assertFalse(PanelPixelReference.matches(sparse, new int[] {0xffffffff}));
        assertFalse(PanelPixelReference.matches(0xffffffff, 0xfeffffff, 12));
        assertThrows(IllegalArgumentException.class, () -> PanelPixelReference.select(pixels, 10, 10));
    }
}
