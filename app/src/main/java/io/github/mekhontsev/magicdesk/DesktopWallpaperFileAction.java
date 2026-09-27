package io.github.mekhontsev.magicdesk;

import android.os.ParcelFileDescriptor;

import java.io.IOException;

final class DesktopWallpaperFileAction {
    private DesktopWallpaperFileAction() {
    }

    static boolean supports(final ShellFileInfo file) {
        return file != null
                && supports(file.mimeType, file.directory);
    }

    static boolean supports(
            final String mimeType, final boolean directory) {
        return !directory
                && mimeType != null
                && (mimeType.startsWith("image/") || mimeType.equals("video/mp4") || mimeType.equals("video/webm"));
    }

    static void apply(final ShellFileInfo file) throws IOException {
        if (!supports(file)) {
            throw new IOException("selected file is not wallpaper media");
        }
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(ShellAccess.openVerifiedShellFile(file, "r"))) {
            WallpaperAsset asset = WallpaperAsset.decode(ThemeBundleFiles.read(input,
                    ShellDesktopDirectory.MAX_WALLPAPER_BYTES), 1920, 1080);
            asset.poster().recycle();
        }
        try (ParcelFileDescriptor source =
                ShellAccess.openVerifiedShellFile(file, "r")) {
            ShellAccess.writeDesktopWallpaper(source);
        }
    }
}
