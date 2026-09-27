package io.github.mekhontsev.magicdesk;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RuntimeShader;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;
import java.util.function.Consumer;

/** A borrowed Android render target. Frame callbacks only update time and invalidate the drawable. */
final class ShaderWallpaperDrawable extends Drawable implements Choreographer.FrameCallback {
    private final RuntimeShader shader;
    private final Paint paint = new Paint();
    private final float[] time = new float[1];
    private final WallpaperFrameClock clock;
    private final Choreographer frames = Choreographer.getInstance();
    private final Consumer<Throwable> failure;
    private final int width, height, fallbackColor;
    private boolean running, broken;

    ShaderWallpaperDrawable(RuntimeShader shader, int width, int height, int fps, int fallbackColor, Consumer<Throwable> failure) {
        this.shader = shader; this.width = width; this.height = height; this.failure = failure;
        this.fallbackColor = fallbackColor;
        clock = new WallpaperFrameClock(fps);
        paint.setShader(shader);
    }
    void start() { if (!running) { running = true; frames.postFrameCallback(this); } }
    void close() { running = false; frames.removeFrameCallback(this); setCallback(null); }

    @Override public void doFrame(long frameTimeNanos) {
        if (!running) return;
        try {
            if (clock.frame(frameTimeNanos)) {
                time[0] = clock.seconds();
                shader.setFloatUniform("md_time", time);
                invalidateSelf();
            }
            frames.postFrameCallback(this);
        } catch (RuntimeException error) { fail(error); }
    }
    @Override public void draw(Canvas canvas) {
        if (broken || !canvas.isHardwareAccelerated()) { canvas.drawColor(fallbackColor); return; }
        try { canvas.drawRect(getBounds(), paint); }
        catch (RuntimeException error) { canvas.drawColor(fallbackColor); fail(error); }
    }
    private void fail(RuntimeException error) {
        if (broken) return;
        broken = true; close(); failure.accept(error);
    }
    @Override public int getIntrinsicWidth() { return width; }
    @Override public int getIntrinsicHeight() { return height; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
