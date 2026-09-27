package io.github.mekhontsev.magicdesk;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import java.util.List;

/** Main-thread, on-demand native content reference for explicit visual self-tests. */
final class UiRenderReference {
    static List<PanelPixelReference.Point> capture(View view) {
        if (view == null || !view.isShown() || !view.isAttachedToWindow() || UiMotion.runningWithin(view)
                || view.getAlpha() != 1 || view.getWidth() < 1 || view.getHeight() < 1) return List.of();
        int width = view.getWidth(), height = view.getHeight();
        if ((long) width * height > 8_388_608) return List.of();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            view.draw(new Canvas(bitmap));
            int[] pixels = new int[width * height];
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
            int[] origin = new int[2];
            view.getLocationOnScreen(origin);
            return PanelPixelReference.select(pixels, width, height).stream()
                    .map(p -> new PanelPixelReference.Point(p.x() + origin[0], p.y() + origin[1], p.color())).toList();
        } finally { bitmap.recycle(); }
    }
}
