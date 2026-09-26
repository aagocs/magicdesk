package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class HostedShellRedrawTest {
    @Test public void resizeAcknowledgesPixelsBeforeAndroidLayoutAndInput() throws Exception {
        verify("""
            var view = new Fixture(); int[] done = {0};
            view.synchronizeLayout(); var sync = view.resizeSync;
            view.redrawNeeded(() -> done[0]++);
            long generation = view.admission.generation(); view.render(generation);
            check(done[0] == 0 && view.published == 0 && sync.ready == 0, "resize admitted old pixels");
            view.output.frames.get(0).complete(null); drain();
            check(done[0] == 1 && view.published == 0 && sync.ready == 1 && view.resizeSync == null,
                "redraw waited for Android layout (deadlock)");
            check(view.admission.layout(generation), "matching layout could not join pixels");
            view.finishRedraws(); check(done[0] == 1, "duplicate redraw completion");
            """);
    }

    @Test public void replacementCoalescesRedrawsAndRejectsStalePixels() throws Exception {
        verify("""
            var view = new Fixture(); int[] done = {0};
            view.synchronizeLayout(); var sync = view.resizeSync;
            view.redrawNeeded(() -> done[0]++); view.render(view.admission.generation());
            view.synchronizeLayout(); check(view.resizeSync == sync, "replacement lost pending root sync");
            view.redrawNeeded(() -> done[0]++); long current = view.admission.generation(); view.render(current);
            view.output.frames.get(0).complete(null); drain();
            check(done[0] == 0 && sync.ready == 0, "stale frame completed replacement resize");
            view.admission.layout(current);
            view.output.frames.get(1).complete(null); drain();
            check(done[0] == 2 && view.published == 1 && sync.ready == 1,
                "latest pixels did not release both redraws");
            """);
    }

    @Test public void failureSurfaceLossAndCloseReleaseAndroidWithoutAdmittingInput() throws Exception {
        verify("""
            var failed = new Fixture(); int[] done = {0};
            failed.synchronizeLayout(); var sync = failed.resizeSync;
            failed.redrawNeeded(() -> done[0]++); failed.render(failed.admission.generation());
            failed.output.frames.get(0).completeExceptionally(new IOException("output failed")); drain();
            check(done[0] == 1 && failed.published == 0 && failed.pending == null && sync.ready == 1,
                "failure leaked resize");
            var lost = new Fixture(); lost.redrawNeeded(() -> done[0]++); lost.render(lost.admission.generation());
            lost.surfaceChanged(null, 0, 0);
            lost.output.frames.get(0).complete(null); drain();
            check(done[0] == 2 && lost.published == 0, "surface loss retained redraw or accepted late pixels");
            var closed = new Fixture(); closed.redrawNeeded(() -> done[0]++); closed.render(closed.admission.generation());
            closed.close(); closed.close();
            closed.output.frames.get(0).complete(null); drain();
            closed.redrawNeeded(() -> done[0]++);
            check(done[0] == 4 && closed.published == 0 && closed.content.releases == 1, "close leaked redraw");
            """);
    }

    private static void verify(String body) throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk",
                RuntimeSourceFixture.methods("HostedShellSurfaceView", "redrawNeeded", "finishRedraws", "render",
                        "fail", "close", "surfaceChanged", "invalidatePresentation", "synchronizeLayout")
                        .replace("android.window.SurfaceSyncGroup", "SurfaceSyncGroup") + STUBS
                + "public static void verify() throws Exception { " + body + " }", "ShellFrameAdmission");
    }

    private static final String STUBS = """
        static class Surface { }
        static class Frame { Object viewport() { return this; } }
        static class Output {
            final List<CompletableFuture<Void>> frames = new ArrayList<>();
            CompletableFuture<Void> present(Surface surface, Object viewport) {
                var future = new CompletableFuture<Void>(); frames.add(future); return future;
            }
            void setSurface(Surface surface, int w, int h) { }
        }
        static class Content { int releases; void release() { releases++; } }
        static class SurfaceSyncGroup {
            int ready;
            SurfaceSyncGroup(String name) { }
            boolean add(Object root, Runnable change) { return true; }
            void markSyncReady() { ready++; }
        }
        SurfaceSyncGroup resizeSync;
        Object getRootSurfaceControl() { return this; }
        static final ArrayDeque<Runnable> events = new ArrayDeque<>();
        static class Handler { void post(Runnable task) { events.add(task); } }
        static void drain() { while (!events.isEmpty()) events.remove().run(); }
        final ArrayList<Runnable> redraws = new ArrayList<>();
        final ShellFrameAdmission admission = new ShellFrameAdmission();
        final Output output = new Output();
        final Content content = new Content();
        final Handler main = new Handler();
        Surface surface = new Surface();
        Frame frame = new Frame();
        boolean closed, ownsOutput = true;
        int surfaceWidth, surfaceHeight, published;
        CompletableFuture<Void> pending = new CompletableFuture<>();
        java.util.function.Consumer<Throwable> failure;
        void clearInput() { }
        void invalidate() { }
        void publishRegion(long generation) { published++; }
        void checkThread() { }
        void cancelPending(String reason) { }
        """;
}
