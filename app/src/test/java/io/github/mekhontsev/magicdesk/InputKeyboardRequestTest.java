package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import static org.junit.Assert.*;

public final class InputKeyboardRequestTest {
    @Test public void requestUsesTheControlledDisplayAndTopEligibleWindow() throws Exception {
        observation("""
                var phone = new InputWindowHandle(0, 0, 1);
                var parent = new InputWindowHandle(7, 0, 1);
                var dialog = new InputWindowHandle(7, 0, 1003);
                var hidden = new InputWindowHandle(7, 2, 1);
                var inert = new InputWindowHandle(7, 4, 1000);
                var clone = new InputWindowHandle(7, 65536, 1);
                var noChannel = new InputWindowHandle(7, 1, 1);
                initial = new InputWindowHandle[]{null, phone, hidden, inert, clone, noChannel, dialog, parent};
                check(focusedWindowForInputMethod(7) == dialog.window, "must address active dialog on target display");
                check(waits == 0 && unregistered == 1, "cached snapshot should be immediate and released");
                initial = new InputWindowHandle[]{phone};
                try { focusedWindowForInputMethod(7); throw new AssertionError("fell back to phone"); }
                catch (IllegalStateException expected) { }
                check(unregistered == 2, "failure leaked listener");
                """);
    }

    @Test public void systemAndTokenlessWindowsDoNotFallThroughToAnApplication() throws Exception {
        observation("""
                var blocker = new InputWindowHandle(7, 0, 2000);
                initial = new InputWindowHandle[]{blocker, new InputWindowHandle(7, 0, 1)};
                try { focusedWindowForInputMethod(7); throw new AssertionError("system layer bypassed"); }
                catch (IllegalStateException expected) { }
                blocker.layoutParamsType = 1; blocker.window = null;
                try { focusedWindowForInputMethod(7); throw new AssertionError("missing endpoint bypassed"); }
                catch (IllegalStateException expected) { }
                check(unregistered == 2, "rejected targets leaked listeners");
                """);
    }

    @Test public void initialCallbackWinsAndEmptyCacheWaitsOnlyForAnEvent() throws Exception {
        observation("""
                var old = new InputWindowHandle(7, 0, 1);
                var current = new InputWindowHandle(7, 0, 1003);
                initial = new InputWindowHandle[]{old};
                publication = new InputWindowHandle[]{current};
                publishDuringRegister = true;
                check(focusedWindowForInputMethod(7) == current.window, "old initial cache replaced newer event");
                publishDuringRegister = false; initial = new InputWindowHandle[0];
                check(focusedWindowForInputMethod(7) == current.window, "first callback not used");
                check(waits == 1, "not an event-bound request");
                publication = new InputWindowHandle[0];
                try { focusedWindowForInputMethod(7); throw new AssertionError("empty event accepted"); }
                catch (IllegalStateException expected) { }
                check(unregistered == 3, "empty event leaked listener");
                interrupt = true;
                try { focusedWindowForInputMethod(7); throw new AssertionError("cancel accepted"); }
                catch (InterruptedException expected) { }
                check(unregistered == 4, "cancel leaked listener");
                """);
    }

    private static void observation(String scenario) throws Exception {
        RuntimeSourceFixture.verify("""
                static class IBinder { }
                public static class InputWindowHandle {
                    public int displayId, inputConfig, layoutParamsType;
                    IBinder window = new IBinder();
                    InputWindowHandle(int display, int config, int type) {
                        displayId = display; inputConfig = config; layoutParamsType = type;
                    }
                    public IBinder getWindowToken() { return window; }
                    boolean isFocusCandidate() { return displayId >= 0 && (inputConfig & (1|2|4|65536)) == 0; }
                }
                static class Adapter { InputWindowHandle read(InputWindowHandle handle) { return handle; } }
                static final Adapter HANDLE_ADAPTER = new Adapter();
                static InputWindowHandle[] initial, publication;
                static boolean publishDuringRegister, interrupt;
                static int unregistered, waits;
                static WindowInfosListener last;
                static class WindowInfosListener {
                    static class DisplayInfo { }
                    static class Initial { InputWindowHandle[] first; }
                    void onWindowInfosChanged(InputWindowHandle[] handles, DisplayInfo[] displays) { }
                    Initial register() {
                        last = this;
                        if (publishDuringRegister) onWindowInfosChanged(publication, null);
                        var result = new Initial(); result.first = initial; return result;
                    }
                    void unregister() { unregistered++; }
                }
                static class EventDrivenWaits {
                    enum Reason { INPUT_WINDOW_COMMIT }
                    static void await(Object lock, Reason reason, long timeout) throws InterruptedException {
                        check(Thread.holdsLock(lock) && timeout > 0 && timeout <= 1000, "invalid event wait");
                        waits++;
                        if (interrupt) throw new InterruptedException();
                        last.onWindowInfosChanged(publication, null);
                    }
                }
                public static void verify() throws Exception {
                """ + scenario + "}\n" + RuntimeSourceFixture.methods("FrameworkInputWindowObservationSource",
                        "focusedWindowForInputMethod", "selectInputMethodWindow"));
    }

    @Test public void queuedRequestsAreScopedToTheCurrentInputLease() throws Exception {
        RuntimeSourceFixture.verify("""
                static class TaskRepository {
                    record ActionResult(boolean success, String message) { }
                    interface ActionCallback { void onComplete(ActionResult result); }
                }
                static class Worker {
                    Runnable pending;
                    void execute(Runnable action) { pending = action; }
                    void drain() { Runnable action = pending; pending = null; action.run(); }
                }
                static class Handler { void post(Runnable action) { action.run(); } }
                static class ShellAccess { static String usefulMessage(Exception error) { return error.getMessage(); } }
                static class Routing {
                    int display = 7, requests;
                    boolean fail;
                    int displayId() { return display; }
                    void requestKeyboard() throws IOException {
                        if (fail) throw new IOException("rejected");
                        requests++;
                    }
                }
                boolean mDestroyed, mTransitioning, ready = true;
                long mGeneration;
                Routing mRouting = new Routing();
                Worker mWorker = new Worker();
                Handler mHandler = new Handler();
                boolean isRoutingReady(int display) { return ready && display == 7; }
                static TaskRepository.ActionResult result;
                static int replies;
                static TaskRepository.ActionCallback callback = value -> { result = value; replies++; };
                public static void verify() {
                    var f = new Fixture();
                    f.showKeyboard(7, callback); f.mWorker.drain();
                    check(result.success() && f.mRouting.requests == 1 && replies == 1, "ordinary request failed");
                    f.showKeyboard(7, callback); f.mWorker.drain();
                    check(f.mRouting.requests == 2, "repeat must request show, never toggle");
                    f.showKeyboard(7, callback); f.mGeneration++; f.mWorker.drain();
                    check(!result.success() && f.mRouting.requests == 2, "stale request reached replacement route");
                    f.showKeyboard(8, callback);
                    check(!result.success() && f.mWorker.pending == null, "other display queued");
                    f.mTransitioning = true; f.showKeyboard(7, callback);
                    check(!result.success() && f.mWorker.pending == null, "transition queued");
                    f.mTransitioning = false; f.mRouting.fail = true;
                    f.showKeyboard(7, callback); f.mWorker.drain();
                    check(!result.success() && f.ready, "IME failure must not disable pointer routing");
                    f.mDestroyed = true; f.showKeyboard(7, callback);
                    check(!result.success() && f.mWorker.pending == null && replies == 7, "closed runtime accepted");
                }
                """ + RuntimeSourceFixture.methods("DisplayInputSession", "showKeyboard"));
    }

    @Test public void observationDoesNotHoldTheCleanupLockAndCloseCancelsDispatch() throws Exception {
        RuntimeSourceFixture.verify("""
                static class FrameworkInputWindowObservationSource {
                    static Fixture session;
                    static boolean closeWhileReading;
                    static Object focusedWindowForInputMethod(int id) {
                        check(id == 7 && !Thread.holdsLock(session), "observation holds routing lock");
                        if (closeWhileReading) session.closed = true;
                        return session;
                    }
                }
                static class FrameworkRuntime {
                    static int shows;
                    static FrameworkRuntime current() { return new FrameworkRuntime(); }
                    FrameworkRuntime inputMethod() { return this; }
                    void requestShow(Object window) { shows++; }
                }
                int mDisplayId = 7;
                boolean closed;
                void requireOwnedDisplay() throws IOException { if (closed) throw new IOException("closed"); }
                public static void verify() throws Exception {
                    var f = new Fixture(); FrameworkInputWindowObservationSource.session = f;
                    f.requestKeyboard();
                    check(FrameworkRuntime.shows == 1, "not dispatched");
                    FrameworkInputWindowObservationSource.closeWhileReading = true;
                    try { f.requestKeyboard(); throw new AssertionError("closed route accepted"); }
                    catch (IOException expected) { }
                    check(FrameworkRuntime.shows == 1, "late observation revived released route");
                }
                """ + RuntimeSourceFixture.methods("DisplayInputRoutingSession", "requestKeyboard"));
    }

    @Test public void touchpadDoesNotAcquireEditorFocusOrUseAProtocolSpecificProxy() throws Exception {
        String show = RuntimeSourceFixture.methods("MagicDeskTouchpadActivity", "showKeyboard");
        assertTrue(show.contains("MagicDeskRuntime.showInputKeyboard"));
        assertFalse(show.contains("requestFocus"));
        assertFalse(show.contains("DesktopOperations"));
        String update = RuntimeSourceFixture.methods("MagicDeskTouchpadActivity", "updateDesktopActions");
        assertTrue(update.contains("MagicDeskRuntime.readyInputDisplayId()"));
        assertFalse(update.contains("mKeyboardButton.setEnabled(desktop"));
    }
}
