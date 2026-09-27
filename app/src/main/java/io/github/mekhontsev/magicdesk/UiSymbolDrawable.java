package io.github.mekhontsev.magicdesk;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Retains drawable identity while symbolic resources change. No resolution occurs in draw(). */
final class UiSymbolDrawable extends Drawable implements Drawable.Callback {
    private final Resources mResources;
    private final int mOriginal;
    private final UiColor mRole;
    private Drawable mDrawable;
    private int mResolved, mAlpha = 255;
    private ColorFilter mFilter;
    UiSymbolDrawable(Resources resources, int original, UiColor role) {
        mResources = resources; mOriginal = original; mRole = role; refresh();
    }
    void refresh() {
        var theme = AppearanceStore.current();
        int resource = ShellIconResources.resolve(mOriginal, theme.resources());
        if (resource != mResolved) {
            if (mDrawable != null) mDrawable.setCallback(null);
            mDrawable = mResources.getDrawable(resource, null).mutate();
            mResolved = resource;
            mDrawable.setCallback(this); mDrawable.setBounds(getBounds());
            mDrawable.setAlpha(mAlpha); mDrawable.setColorFilter(mFilter);
        }
        mDrawable.setTint(theme.palette().color(mRole)); invalidateSelf();
    }
    @Override public void draw(Canvas canvas) { mDrawable.draw(canvas); }
    @Override protected void onBoundsChange(Rect bounds) { if (mDrawable != null) mDrawable.setBounds(bounds); }
    @Override public void setAlpha(int alpha) { mAlpha = alpha; mDrawable.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter filter) { mFilter = filter; mDrawable.setColorFilter(filter); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return mDrawable.getIntrinsicWidth(); }
    @Override public int getIntrinsicHeight() { return mDrawable.getIntrinsicHeight(); }
    @Override public void invalidateDrawable(Drawable drawable) { invalidateSelf(); }
    @Override public void scheduleDrawable(Drawable drawable, Runnable what, long when) { scheduleSelf(what, when); }
    @Override public void unscheduleDrawable(Drawable drawable, Runnable what) { unscheduleSelf(what); }
}
