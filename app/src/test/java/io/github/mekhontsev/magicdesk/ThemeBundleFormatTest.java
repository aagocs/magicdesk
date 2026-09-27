package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class ThemeBundleFormatTest {
    @Test public void fontDirectoryRangesAreCheckedBeforePlatformDecoding() throws Exception {
        byte[] font = font();
        ThemeBundleFormat.signature("fonts/font.ttf", ThemeBundle.Kind.FONT, font);
        assertThrows(IOException.class, () -> ThemeBundleFormat.signature("fonts/font.otf", ThemeBundle.Kind.FONT, font));
        ByteBuffer.wrap(font).putInt(20, Integer.MAX_VALUE);
        assertThrows(IOException.class, () -> ThemeBundleFormat.signature("fonts/font.ttf", ThemeBundle.Kind.FONT, font));
        byte[] hugeTable = font(); ByteBuffer.wrap(hugeTable).putInt(24, -1);
        assertThrows(IOException.class, () -> ThemeBundleFormat.signature("fonts/font.ttf", ThemeBundle.Kind.FONT, hugeTable));
        byte[] excessive = font(); ByteBuffer.wrap(excessive).putShort(4, (short) 129);
        assertThrows(IOException.class, () -> ThemeBundleFormat.signature("fonts/font.ttf", ThemeBundle.Kind.FONT, excessive));
    }

    @Test public void imageTypeSniffDoesNotAcceptAnotherCodecOrTruncatedHeaders() throws Exception {
        for (String name : new String[] {"icons/a.png", "icons/a.jpg", "icons/a.webp", "fonts/a.ttf"}) {
            assertThrows(IOException.class, () -> ThemeBundleFormat.signature(name, ThemeBundle.Kind.ICON, new byte[0]));
        }
        byte[] riff = new byte[20];
        ByteBuffer.wrap(riff).putInt(0, 0x52494646).putInt(4, Integer.reverseBytes(12)).putInt(8, 0x57454250);
        ThemeBundleFormat.signature("icons/a.WEBP", ThemeBundle.Kind.ICON, riff);
        riff[4] = 99;
        assertThrows(IOException.class, () -> ThemeBundleFormat.signature("icons/a.webp", ThemeBundle.Kind.ICON, riff));
    }

    @Test public void pathKindIsSeparateFromRequestedResourceRole() throws Exception {
        assertEquals(ThemeBundle.Kind.ICON, ThemeBundleFiles.kind("icons/Home.PNG"));
        assertEquals(ThemeBundle.Kind.WALLPAPER, ThemeBundleFiles.kind("wallpapers/City.jpeg"));
        assertEquals(ThemeBundle.Kind.FONT, ThemeBundleFiles.kind("fonts/Interface.OTF"));
        assertThrows(IOException.class, () -> ThemeBundleFiles.kind("icons/font.ttf"));
        assertThrows(IOException.class, () -> ThemeBundleFiles.kind("fonts/image.png"));
        assertThrows(IOException.class, () -> ThemeBundleFiles.kind("other/image.png"));
    }

    @Test public void imageBudgetUsesLongArithmeticAndRejectsInvalidDecoderDimensions() throws Exception {
        ThemeBundleLimits limits = ThemeBundleLimits.DEFAULT;
        limits.checkImage(ThemeBundle.Kind.ICON, 1024, 1024, 1024L * 1024);
        for (int[] size : new int[][] {{0, 1}, {-1, -1}, {Integer.MAX_VALUE, Integer.MAX_VALUE}, {1025, 1024}}) {
            assertThrows(IOException.class, () -> limits.checkImage(ThemeBundle.Kind.ICON, size[0], size[1], Long.MAX_VALUE));
        }
        assertThrows(IOException.class, () -> limits.checkImage(ThemeBundle.Kind.FONT, 1, 1, 1));
        assertThrows(IOException.class, () -> limits.checkImage(ThemeBundle.Kind.WALLPAPER, 1, 1, 0));
    }

    @Test public void manifestIsUtf8ObjectWithoutTrailingContentOrDeepNesting() throws Exception {
        assertEquals("{}", ThemeBundleFormat.theme("{}".getBytes(StandardCharsets.UTF_8)));
        for (String text : new String[] {"null", "[]", "{}{}", "{\"a\":" + "[".repeat(32) + "0" + "]".repeat(32) + "}"}) {
            assertThrows(IOException.class, () -> ThemeBundleFormat.theme(text.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static byte[] font() {
        byte[] bytes = new byte[32];
        ByteBuffer.wrap(bytes).putInt(0, 0x00010000).putShort(4, (short) 1)
                .putInt(12, 0x68656164).putInt(20, 28).putInt(24, 4);
        return bytes;
    }
}
