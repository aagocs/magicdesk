package io.github.mekhontsev.magicdesk;

import org.junit.Test;

/** The queued production methods run against deterministic Binder and pointer doubles. */
public final class DisplayInputSessionLifecycleTest {
    @Test public void onlyExplicitSelectionRetriesFailedTarget() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    ShellAccess.failOpen = true;
                    ShellAccess.proceed.countDown();
                    f.reconcile(7, false);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(!f.isRoutingReady(7) && !f.mError.isEmpty(), "failed route became ready");
                    f.reconcile(7, false);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(ShellAccess.opens == 1, "reconcile automatically retried failed input");
                    ShellAccess.failOpen = false;
                    f.retryFailedSelection();
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(ShellAccess.opens == 2 && f.isRoutingReady(7) && f.mError.isEmpty(),
                            "explicit selection did not recover input");
                    f.retryFailedSelection();
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(ShellAccess.opens == 2, "retry restarted ready input");
                    f.stop(() -> {});
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void retryDoesNotSupersedePendingAcquisitionOrAcquireReleasedTarget() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    f.reconcile(7, false);
                    check(ShellAccess.entered.await(2, TimeUnit.SECONDS), "acquisition not reached");
                    f.retryFailedSelection();
                    ShellAccess.proceed.countDown();
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(ShellAccess.opens == 1 && f.isRoutingReady(7), "retry replaced pending input");
                    f.stop(() -> {});
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    f.retryFailedSelection();
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(ShellAccess.opens == 1 && !ShellAccess.route.active, "retry reacquired released input");
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void closeCompletesAfterInFlightAcquisitionAndRestoration() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    f.reconcile(7, true);
                    check(ShellAccess.entered.await(2, TimeUnit.SECONDS), "acquisition not reached");
                    CountDownLatch complete = new CountDownLatch(1);
                    f.stop(complete::countDown);
                    check(complete.getCount() == 1, "Close overtook acquisition");
                    ShellAccess.proceed.countDown();
                    check(complete.await(2, TimeUnit.SECONDS), "Close did not complete");
                    check(!f.isRoutingReady(7) && !ShellAccess.route.active,
                            "late startup acquired a closing display");
                    check(!f.mMouse.active, "pointer survived Close");
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void reopenSameDisplayCannotPublishAnOldReadyCallback() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    f.reconcile(7, true);
                    check(ShellAccess.entered.await(2, TimeUnit.SECONDS), "acquisition not reached");
                    f.stop(() -> {});
                    f.reconcile(7, true);
                    ShellAccess.proceed.countDown();
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(f.isRoutingReady(7) && f.mMouse.starts == 1,
                            "old generation started the pointer");
                    CountDownLatch complete = new CountDownLatch(1);
                    f.stop(complete::countDown);
                    check(complete.await(2, TimeUnit.SECONDS), "Close did not complete");
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void failedRestorationCannotBeReplacedByAnotherLease() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    f.mRouting = new ShellInputRoutingHandle(7);
                    f.mRouting.fail = true;
                    ShellAccess.proceed.countDown();
                    f.reconcile(8, false);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(ShellAccess.opens == 0 && !f.isRoutingReady(8),
                            "new lease overwrote failed cleanup");
                    f.mRouting.fail = false;
                    CountDownLatch complete = new CountDownLatch(1);
                    f.stop(complete::countDown);
                    check(complete.await(2, TimeUnit.SECONDS), "cleanup retry failed");
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void keyboardPlacementDoesNotRestartInputAndUsesLatestPreference() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    f.reconcile(7, true);
                    check(ShellAccess.entered.await(2, TimeUnit.SECONDS), "acquisition not reached");
                    f.setKeyboardOnAppDisplay(true, null);
                    ShellAccess.proceed.countDown();
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(ShellAccess.route.keyboardOnAppDisplay, "queued mode was lost");
                    f.setKeyboardOnAppDisplay(false, null);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(!ShellAccess.route.keyboardOnAppDisplay, "live switch ignored");
                    check(ShellAccess.opens == 1 && f.mMouse.starts == 1 && f.isRoutingReady(7),
                            "keyboard mode restarted input");
                    f.stop(() -> {});
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void failedKeyboardSwitchRetainsWorkingPointer() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    ShellAccess.proceed.countDown();
                    f.reconcile(7, true);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    ShellAccess.route.fail = true;
                    CountDownLatch failed = new CountDownLatch(1);
                    CountDownLatch completed = new CountDownLatch(1);
                    f.mKeyboardFailure = error -> failed.countDown();
                    f.setKeyboardOnAppDisplay(true, completed::countDown);
                    check(failed.await(2, TimeUnit.SECONDS), "failure not reported");
                    check(completed.await(2, TimeUnit.SECONDS), "failed switch retained the menu");
                    check(f.isRoutingReady(7) && f.mMouse.active && ShellAccess.route.active,
                            "keyboard failure dropped working input");
                    check(f.mError.isEmpty() && !f.mKeyboardError.isEmpty(),
                            "optional keyboard error contaminated routing state");
                    ShellAccess.route.fail = false;
                    f.stop(() -> {});
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void keyboardCompletionWaitsForThePolicyIncludingAnUnchangedRefresh() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    ShellAccess.proceed.countDown();
                    f.reconcile(7, true);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    CountDownLatch entered = new CountDownLatch(1);
                    CountDownLatch release = new CountDownLatch(1);
                    CountDownLatch completed = new CountDownLatch(2);
                    f.mWorker.execute(() -> {
                        entered.countDown();
                        try { check(release.await(2, TimeUnit.SECONDS), "fixture gate timed out"); }
                        catch (InterruptedException e) { throw new AssertionError(e); }
                    });
                    check(entered.await(2, TimeUnit.SECONDS), "worker gate not reached");
                    Runnable completion = () -> {
                        check(ShellAccess.route.keyboardOnAppDisplay, "completion preceded policy write");
                        completed.countDown();
                    };
                    f.setKeyboardOnAppDisplay(true, completion);
                    f.setKeyboardOnAppDisplay(true, completion);
                    check(completed.getCount() == 2, "refresh returned before the queued policy");
                    release.countDown();
                    check(completed.await(2, TimeUnit.SECONDS), "refresh did not finish");
                    check(ShellAccess.opens == 1 && f.mMouse.starts == 1, "refresh restarted input");
                    f.stop(() -> {});
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void keyboardCompletionIsDeliveredWithoutAnActiveRoute() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    CountDownLatch completed = new CountDownLatch(1);
                    f.setKeyboardOnAppDisplay(true, completed::countDown);
                    check(completed.await(2, TimeUnit.SECONDS), "inactive refresh did not finish");
                    check(ShellAccess.opens == 0, "refresh acquired input");
                    f.mDestroyed = true;
                    CountDownLatch destroyed = new CountDownLatch(1);
                    f.setKeyboardOnAppDisplay(false, destroyed::countDown);
                    check(destroyed.getCount() == 0, "destroyed refresh lost completion");
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                }
                """);
    }

    @Test public void initialKeyboardFailureRetainsInputAndLiveRecoveryDoesNotRestartIt() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    Fixture f = new Fixture();
                    ShellAccess.failInitialKeyboard = true;
                    CountDownLatch failed = new CountDownLatch(1);
                    f.mKeyboardFailure = error -> failed.countDown();
                    ShellAccess.proceed.countDown();
                    f.reconcile(7, false);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(failed.getCount() == 0 && !f.mKeyboardError.isEmpty(), "initial warning lost");
                    check(f.isRoutingReady(7) && f.mError.isEmpty() && !f.mTransitioning
                            && f.mMouse.active && ShellAccess.route.active, "IME failure aborted input");
                    ShellAccess.route.failIme = false;
                    f.setKeyboardOnAppDisplay(true, null);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(f.mKeyboardError.isEmpty() && ShellAccess.route.keyboardOnAppDisplay,
                            "successful live switch did not clear warning");
                    check(ShellAccess.opens == 1 && f.mMouse.starts == 1, "live switch restarted input");
                    ShellAccess.route.failIme = true;
                    f.setKeyboardOnAppDisplay(false, null);
                    f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                    check(!f.mKeyboardError.isEmpty(), "live failure lost");
                    f.stop(() -> {});
                    f.mWorker.shutdown();
                    check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                    check(f.mKeyboardError.isEmpty() && f.mError.isEmpty()
                            && !ShellAccess.route.active && !f.mMouse.active, "release retained optional error");
                }
                """);
    }

    @Test public void staleKeyboardWarningCannotOutliveReleaseOrSuccessfulSwitch() throws Exception {
        RuntimeSourceFixture.verify(fixture() + """
                public static void verify() throws Exception {
                    for (boolean release : new boolean[] {false, true}) {
                        Fixture f = new Fixture();
                        f.mHandler.defer = true;
                        ShellAccess.failInitialKeyboard = true;
                        ShellAccess.proceed.countDown();
                        f.reconcile(7, false);
                        f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                        check(!f.mKeyboardError.isEmpty(), "failure not reached");
                        if (release) f.stop(() -> {});
                        else {
                            ShellAccess.route.failIme = false;
                            f.setKeyboardOnAppDisplay(true, null);
                        }
                        f.mWorker.submit(() -> {}).get(2, TimeUnit.SECONDS);
                        f.mHandler.drain();
                        check(f.mKeyboardError.isEmpty(), "obsolete warning survived");
                        f.stop(() -> {});
                        f.mWorker.shutdown();
                        check(f.mWorker.awaitTermination(2, TimeUnit.SECONDS), "worker leaked");
                        f.mHandler.drain();
                    }
                }
                """);
    }

    private static String fixture() throws Exception {
        return """
                static class Display { static final int INVALID_DISPLAY = -1, DEFAULT_DISPLAY = 0; }
                static class Handler {
                    boolean defer;
                    final java.util.Queue<Runnable> pending = new java.util.concurrent.ConcurrentLinkedQueue<>();
                    void post(Runnable action) { if (defer) pending.add(action); else action.run(); }
                    void drain() { Runnable action; while ((action = pending.poll()) != null) action.run(); }
                }
                final Handler mHandler = new Handler();
                final Runnable mChanged = () -> {};
                java.util.function.Consumer<String> mKeyboardFailure = error -> { throw new AssertionError(error); };
                final Mouse mMouse = new Mouse();
                final ExecutorService mWorker = Executors.newSingleThreadExecutor();
                volatile int mRequestedDisplay = -1, mReadyDisplay = -1;
                volatile long mGeneration;
                boolean mDestroyed, mDesktopShortcuts, mTransitioning;
                volatile boolean mKeyboardOnAppDisplay;
                String mError = "", mKeyboardError = "";
                ShellInputRoutingHandle mRouting;
                static class Mouse {
                    volatile boolean active;
                    int starts;
                    void start() { active = true; starts++; }
                    void stop() { active = false; }
                }
                static class DesktopShortcutService {
                    static void setTargetDisplay(int id) {}
                    static void setTargetDisplay(int id, boolean desktop) {}
                }
                static class CompatibilityDiagnostics {
                    static void record(String code, String title, String detail, Throwable error) {}
                }
                static class InputSessionDiagnostics {
                    static void noteAttempt(int id) {}
                    static void noteReady() {}
                }
                static class ShellInputRoutingHandle {
                    final int id;
                    volatile boolean active = true, fail, failIme;
                    boolean keyboardOnAppDisplay;
                    ShellInputRoutingHandle(int id) { this.id = id; }
                    int displayId() { return id; }
                    void setKeyboardPlacement(boolean enabled) throws IOException {
                        if (fail || failIme) throw new IOException("policy failed");
                        keyboardOnAppDisplay = enabled;
                    }
                    void close() throws IOException {
                        if (fail) throw new IOException("restore failed");
                        active = false;
                    }
                }
                static class ShellAccess {
                    static final CountDownLatch entered = new CountDownLatch(1);
                    static final CountDownLatch proceed = new CountDownLatch(1);
                    static ShellInputRoutingHandle route;
                    static int opens;
                    static boolean failOpen, failInitialKeyboard;
                    static ShellInputRoutingHandle openInputRouting(int id, boolean shortcuts) throws IOException {
                        opens++;
                        entered.countDown();
                        try { check(proceed.await(2, TimeUnit.SECONDS), "fixture gate timed out"); }
                        catch (InterruptedException e) { throw new IOException(e); }
                        if (failOpen) throw new IOException("wrong pointer display");
                        route = new ShellInputRoutingHandle(id);
                        route.failIme = failInitialKeyboard;
                        return route;
                    }
                    static void cleanupInputRouting() throws IOException {}
                    static boolean isReady() { return true; }
                    static String usefulMessage(Throwable error) { return error.getMessage(); }
                }
                void report(String code, String title, IOException error) { mError = error.getMessage(); }
                """ + RuntimeSourceFixture.methods("DisplayInputSession",
                        "reconcile", "retryFailedSelection", "stop", "release", "isRoutingReady",
                        "setKeyboardOnAppDisplay", "applyKeyboardPlacement");
    }
}
