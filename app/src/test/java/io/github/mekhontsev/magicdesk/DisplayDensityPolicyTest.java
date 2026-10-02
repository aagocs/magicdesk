package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DisplayDensityPolicyTest {
    @Test
    public void recommendedDpiKeepsAboutFullHdLogicalSpace() {
        assertEquals(108,
                DisplayDensityPolicy.recommendedExternalDpi(1280, 720, 520));
        assertEquals(160,
                DisplayDensityPolicy.recommendedExternalDpi(1920, 1080, 520));
        assertEquals(176,
                DisplayDensityPolicy.recommendedExternalDpi(1920, 1200, 520));
        assertEquals(212,
                DisplayDensityPolicy.recommendedExternalDpi(2560, 1440, 520));
        assertEquals(320,
                DisplayDensityPolicy.recommendedExternalDpi(3840, 2160, 520));
    }

    @Test
    public void invalidResolutionFallsBackToDefaultExternalDensity() {
        assertEquals(192,
                DisplayDensityPolicy.recommendedExternalDpi(0, 0, 520));
    }

    @Test
    public void recommendationNeverExceedsTheDisplayMaximum() {
        assertEquals(240,
                DisplayDensityPolicy.recommendedExternalDpi(3840, 2160, 240));
        assertEquals(DisplayDensityPolicy.MIN_DPI,
                DisplayDensityPolicy.recommendedExternalDpi(3840, 2160, 10));
        assertEquals(DisplayDensityPolicy.MIN_DPI,
                DisplayDensityPolicy.recommendedExternalDpi(0, 0, 10));
    }

    @Test
    public void snapRoundsToStepsAboveTheMinimum() {
        assertEquals(DisplayDensityPolicy.MIN_DPI, DisplayDensityPolicy.snapDpi(50, 520));
        assertEquals(100, DisplayDensityPolicy.snapDpi(101, 520));
        assertEquals(104, DisplayDensityPolicy.snapDpi(103, 520));
        assertEquals(520, DisplayDensityPolicy.snapDpi(900, 520));
        assertEquals(DisplayDensityPolicy.MIN_DPI, DisplayDensityPolicy.snapDpi(200, 50));
    }
}
