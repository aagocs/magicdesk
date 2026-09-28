package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class ShaderWallpaperDrawableTest {
    @Test public void playbackFailureAndCloseReleaseBindingsAndStaticShadersDoNotAcquireThem() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", """
            static class Canvas { boolean isHardwareAccelerated(){return true;} void drawColor(int c){} void drawRect(Object r,Paint p){} }
            static class ColorFilter {}
            static class Paint { void setShader(RuntimeShader s){} void setAlpha(int a){} void setColorFilter(ColorFilter f){} }
            static class PixelFormat { static final int TRANSLUCENT=-3; }
            static class Drawable {
                void setCallback(Object o){} void invalidateSelf(){} Object getBounds(){return null;}
                public void draw(Canvas c){} public int getIntrinsicWidth(){return 0;} public int getIntrinsicHeight(){return 0;}
                public void setAlpha(int a){} public void setColorFilter(ColorFilter f){} public int getOpacity(){return 0;}
            }
            static class Choreographer {
                interface FrameCallback { void doFrame(long now); }
                static final Choreographer instance=new Choreographer();
                final Set<FrameCallback> frames=new HashSet<>();
                static Choreographer getInstance(){return instance;}
                void postFrameCallback(FrameCallback f){frames.add(f);}
                void removeFrameCallback(FrameCallback f){frames.remove(f);}
                void frame(long now){var pending=List.copyOf(frames);frames.clear();pending.forEach(f->f.doFrame(now));}
            }
            static class RuntimeShader {
                final Map<String,float[]> values=new HashMap<>(); boolean fail;
                void setFloatUniform(String name,float[] value){if(fail)throw new IllegalArgumentException("failed");values.put(name,value);}
            }
            """ + RuntimeSourceFixture.nestedClass("ShaderWallpaperDrawable", "ShaderWallpaperDrawable")
                    .replace("final class ShaderWallpaperDrawable", "static final class ShaderWallpaperDrawable")
                    .replace("Consumer<", "java.util.function.Consumer<") + """
            public static void verify() {
                int[] starts={0},stops={0};
                var registry=new AppearanceSignalRegistry((source,publish)->{starts[0]++;return ()->stops[0]++;});
                var bindings=new ShaderSignalBindings(List.of(new ShaderWallpaper.SignalUniform("load",AppearanceSignal.CPU_USAGE,.2f,0)),registry::acquire);
                var shader=new RuntimeShader(); var errors=new ArrayList<Throwable>();
                var view=new ShaderWallpaperDrawable(shader,100,100,30,-1,bindings,errors::add);
                check(starts[0]==0,"constructor subscribed"); view.start(); check(starts[0]==1,"playback did not subscribe");
                Choreographer.instance.frame(0); var first=shader.values.get("load");
                Choreographer.instance.frame(40_000_000); check(first==shader.values.get("load"),"per-frame uniform allocation");
                shader.fail=true; Choreographer.instance.frame(80_000_000);
                check(errors.size()==1 && stops[0]==1 && Choreographer.instance.frames.isEmpty(),"failed renderer retained sources or frames");
                view.close(); view.start(); check(starts[0]==1,"broken renderer restarted");
                var plain=new ShaderWallpaperDrawable(new RuntimeShader(),100,100,30,-1,null,errors::add);
                plain.start();Choreographer.instance.frame(100_000_000);plain.close();
                check(starts[0]==1 && Choreographer.instance.frames.isEmpty(),"plain wallpaper acquired telemetry");
            }
            """, "WallpaperFrameClock", "ShaderSignalBindings", "ShaderWallpaper", "AppearanceSignal", "AppearanceSignalRegistry",
                "ThemeBundleFiles", "ThemeBundle", "ThemeBundleLimits");
    }
}
