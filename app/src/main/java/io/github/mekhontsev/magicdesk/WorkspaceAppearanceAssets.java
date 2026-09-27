package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Prepared snapshots, not decoder cache size, own the aggregate live asset budget. */
final class WorkspaceAppearanceAssets {
    static final long MAX_IMAGE_BYTES = 96L << 20;
    static final int MAX_FONTS = 8;
    private WorkspaceAppearanceAssets() { }

    static Map<ShellResources, ThemeAssets.Prepared> prepare(Context context, Collection<ShellResources> resources,
            Map<ShellResources, ThemeAssets.Prepared> active, Map<ShellResources, ThemeAssets.Prepared> staged) throws IOException {
        Map<ShellResources, ThemeAssets.Prepared> result = new LinkedHashMap<>();
        Set<Bitmap> images = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Typeface> fonts = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<WallpaperAsset> wallpapers = Collections.newSetFromMap(new IdentityHashMap<>());
        long bytes = 0;
        for (ShellResources resource : resources) {
            ThemeAssets.Prepared prepared = active.get(resource);
            if (prepared == null) prepared = staged.get(resource);
            if (prepared == null) prepared = resource.hasAssets()
                    ? ThemeAssets.get(context).prepare(resource)
                    : ThemeAssets.Prepared.EMPTY;
            for (String path : resource.iconAssets().values()) bytes += imageBytes(prepared.icon(path), images);
            if (prepared.wallpaper() != null && wallpapers.add(prepared.wallpaper())) bytes += prepared.wallpaper().retainedBytes();
            if (prepared.font() != null) fonts.add(prepared.font());
            if (bytes > MAX_IMAGE_BYTES || fonts.size() > MAX_FONTS) {
                throw new IllegalArgumentException("Active appearance assets exceed 96 MiB of media or 8 fonts");
            }
            result.put(resource, prepared);
        }
        return Map.copyOf(result);
    }

    private static long imageBytes(Bitmap bitmap, Set<Bitmap> images) {
        return bitmap == null || !images.add(bitmap) ? 0 : bitmap.getAllocationByteCount();
    }
}
