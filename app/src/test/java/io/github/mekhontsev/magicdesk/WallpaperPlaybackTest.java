package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class WallpaperPlaybackTest {
    @Test public void stoppedOrReplacedLoadsCannotAttachOrReportStaleErrors() throws Exception {
        verify("""
            var first = fixture.asset;
            fixture.refresh(); check(work.pending.size() == 1, "one decode per output");
            fixture.refresh(); check(work.pending.size() == 1, "duplicate decode");
            fixture.visible = false; fixture.refresh();
            work.run(); main.run();
            check(first.last.stopped && displayed == null, "late decode attached after hide");
            fixture.visible = true; fixture.asset = new WallpaperAsset(); fixture.refresh();
            var replacement = fixture.asset;
            work.run(); main.run();
            check(displayed == replacement.last && displayed.started, "replacement not playing");
            check(states.equals(List.of("paused", "playing")), "unexpected playback states " + states);
            fixture.endPlayback();
            check(displayed.stopped && displayed.callback == null && displayed.cleared, "decoder callback retained");
            first.broken = true; fixture.asset = first; fixture.refresh();
            fixture.endPlayback(); work.run(); main.run();
            check(failures.isEmpty() && !fixture.failed, "stale error contaminated new source");
            """);
    }
    @Test public void currentFailureKeepsPosterAndDoesNotRetryOnEveryEvent() throws Exception {
        verify("""
            fixture.asset.broken = true; fixture.refresh(); work.run(); main.run();
            check(fixture.failed && failures.size() == 1 && posters > 0, "missing static fallback");
            fixture.refresh(); fixture.refresh();
            check(work.pending.isEmpty() && failures.size() == 1, "failed source retried");
            fixture.asset = new WallpaperAsset(); fixture.failed = false;
            fixture.refresh(); work.run(); main.run();
            check(displayed.started, "new source cannot recover");
            fixture.closed = true; fixture.endPlayback();
            fixture.refresh(); check(work.pending.isEmpty() && fixture.playback == null, "closed host reopened");
            """);
    }
    @Test public void posterFramesHaveNoDensityScalingAndVideoIsContentSniffed() throws Exception {
        RuntimeSourceFixture.verify("""
            enum Kind { IMAGE }
            static class Bitmap {
                static final int DENSITY_NONE = 0; int density = 520;
                void setDensity(int value) { density = value; } int getWidth() { return 1920; } int getHeight() { return 1080; }
            }
            static class WallpaperAsset {
                final Kind kind; final Bitmap poster; final byte[] encoded; final int width, height;
            """ + RuntimeSourceFixture.topLevelMethods("WallpaperAsset", "<init>") + """
            }
            """ + RuntimeSourceFixture.methods("WallpaperAsset", "image", "videoSignature").replace("ByteBuffer", "java.nio.ByteBuffer") + """
            public static void verify() {
                var bitmap = new Bitmap(); var source = image(bitmap);
                check(source.poster == bitmap && bitmap.density == 0, "poster pixels must not inherit process density");
                byte[] mp4 = new byte[12]; java.nio.ByteBuffer.wrap(mp4).putInt(12).putInt(0x66747970);
                check(videoSignature(mp4), "MP4 content not detected");
                check(!videoSignature(new byte[4]) && !videoSignature(new byte[12]), "invalid data admitted as video");
            }
            """);
    }

    private static void verify(String body) throws Exception {
        RuntimeSourceFixture.verify("""
            interface Playback { void close(); }
            static class Queue implements java.util.concurrent.Executor {
                final List<Runnable> pending = new ArrayList<>();
                public void execute(Runnable r) { pending.add(r); } void post(Runnable r) { pending.add(r); }
                void run() { var copy = List.copyOf(pending); pending.clear(); copy.forEach(Runnable::run); }
            }
            static class AnimatedImageDrawable {
                boolean started, stopped, cleared; Object callback = new Object();
                void start() { started = true; } void stop() { stopped = true; }
                void clearAnimationCallbacks() { cleared = true; } void setCallback(Object value) { callback = value; }
            }
            static class WallpaperAsset {
                enum Kind { VIDEO, ANIMATED_IMAGE }
                boolean broken; AnimatedImageDrawable last;
                Kind kind() { return Kind.ANIMATED_IMAGE; }
                AnimatedImageDrawable animation(int w, int h) throws IOException {
                    if (broken) throw new IOException("decode failed");
                    return last = new AnimatedImageDrawable();
                }
            }
            final class VideoPlayback implements Playback { VideoPlayback(int token, WallpaperAsset asset) {} void attach() {} public void close() {} }
            static Queue work = new Queue(), main = new Queue();
            final java.util.concurrent.Executor decoder = work;
            WallpaperAsset asset = new WallpaperAsset(); Playback playback;
            static List<String> states = new ArrayList<>(); static List<Throwable> failures = new ArrayList<>();
            java.util.function.Consumer<String> state = states::add;
            java.util.function.Consumer<Throwable> failure = failures::add;
            int playbackGeneration, outputWidth = 1920, outputHeight = 1080;
            boolean preparing, failed, closed, visible = true;
            final Object image = new Object();
            static AnimatedImageDrawable displayed; static int posters;
            boolean shouldPlay() { return visible && !closed && !failed; }
            void setImage(AnimatedImageDrawable value) { displayed = value; }
            void showPoster() { posters++; }
            """ + RuntimeSourceFixture.methods("WallpaperView", "refresh", "endPlayback", "failed")
                + "public static void verify() { var fixture = new Fixture();\n" + body + "\n}");
    }
}
