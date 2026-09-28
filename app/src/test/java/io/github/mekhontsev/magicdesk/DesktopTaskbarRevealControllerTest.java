package io.github.mekhontsev.magicdesk;

import static io.github.mekhontsev.magicdesk.DesktopTaskbarRevealController.Presentation.EDGE;
import static io.github.mekhontsev.magicdesk.DesktopTaskbarRevealController.Presentation.VISIBLE;
import static io.github.mekhontsev.magicdesk.DesktopTaskbarRevealController.resolvePresentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DesktopTaskbarRevealControllerTest {
    @Test public void unchangedArmingPreservesPendingRevealAndHideCallbacks() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", """
                enum Presentation { EDGE, VISIBLE }
                static class Handler {
                    Set<Runnable> pending = new HashSet<>();
                    void postDelayed(Runnable r, long delay) { pending.add(r); }
                    void removeCallbacks(Runnable r) { pending.remove(r); }
                }
                Handler mHandler = new Handler();
                boolean mStarted=true, mReleased, mPolicyVisible,
                        mAutoHide, mAutomaticHold, mInteractionHold, mTouchEdgeEnabled;
                PointerEdgeRevealState mPointerState = new PointerEdgeRevealState();
                TouchEdgeRevealState mTouchState = new TouchEdgeRevealState();
                Runnable mRevealTimeout = () -> mPointerState.onRevealTimeout();
                Runnable mHideTimeout = () -> mPointerState.onHideTimeout();
                static final long REVEAL_DWELL_MILLIS=450, HIDE_DELAY_MILLIS=300;
                void applyPresentation() {}
                void fire(Runnable callback) {
                    check(mHandler.pending.remove(callback), "gesture callback was cancelled");
                    callback.run();
                }
                public static void verify() {
                    Fixture f = new Fixture(); f.updateArmedState();
                    f.applyTimerAction(f.mPointerState.onPointerEntered());
                    f.setAutoHide(true);
                    f.fire(f.mRevealTimeout);
                    check(f.mPointerState.isRevealed(), "dwell did not reveal");
                    f.applyTimerAction(f.mPointerState.onPointerExited());
                    f.setAutoHide(false);
                    f.fire(f.mHideTimeout);
                    check(!f.mPointerState.isRevealed(), "panel retained cancelled hide");
                    f.applyTimerAction(f.mPointerState.onPointerEntered());
                    f.setPolicyVisible(true);
                    check(f.mHandler.pending.isEmpty(), "disarm retained gesture callback");
                    check(!f.mPointerState.onRevealTimeout(), "obsolete callback revealed panel");
                }
                """ + RuntimeSourceFixture.methods("DesktopTaskbarRevealController",
                        "setAutoHide", "setPolicyVisible", "updateArmedState", "cancelTimers",
                        "applyTimerAction", "resolvePresentation"),
                "PointerEdgeRevealState", "TouchEdgeRevealState");
    }

    @Test public void externalLayersFollowFullscreenRevealWithoutChangingHome() {
        for (int flags = 0; flags < 16; flags++) {
            boolean policy = (flags & 1) != 0, automaticHold = (flags & 2) != 0,
                    pointer = (flags & 4) != 0, explicit = (flags & 8) != 0;
            var layers = DesktopTaskbarRevealController.resolveShellLayers(
                    policy, automaticHold, pointer, explicit);
            assertTrue(layers.contains(ShellSurface.Layer.BACKGROUND));
            assertTrue(layers.contains(ShellSurface.Layer.BOTTOM));
            assertTrue(layers.contains(ShellSurface.Layer.OVERLAY));
            assertEquals(explicit || policy || automaticHold || pointer,
                    layers.contains(ShellSurface.Layer.TOP));
        }
    }

    @Test
    public void navigationOnlyRevealsLiveHiddenPhoneChromeOnce() throws Exception {
        RuntimeSourceFixture.verify("""
                enum Presentation { EDGE, VISIBLE }
                boolean mStarted, mReleased, mTouchEdgeEnabled,
                        mPolicyVisible, mAutoHide, mAutomaticHold, mInteractionHold;
                static class RevealState {
                    boolean revealed; int requests;
                    boolean isRevealed() { return revealed; }
                    int reveal() {
                        if (revealed) return 0;
                        requests++; revealed = true; return 1;
                    }
                }
                RevealState mPointerState = new RevealState(), mTouchState = new RevealState();
                int updates;
                void applyTouchAction(int action, boolean afterDispatch) { if (action != 0) updates++; }
                public static void verify() {
                    for (int flags = 0; flags < 512; flags++) {
                        Fixture f = new Fixture();
                        f.mStarted = (flags & 1) != 0;
                        f.mReleased = (flags & 2) != 0;
                        f.mTouchEdgeEnabled = (flags & 4) != 0;
                        f.mPolicyVisible = (flags & 8) != 0;
                        f.mAutoHide = (flags & 16) != 0;
                        f.mAutomaticHold = (flags & 32) != 0;
                        f.mPointerState.revealed = (flags & 64) != 0;
                        f.mTouchState.revealed = (flags & 128) != 0;
                        f.mInteractionHold = (flags & 256) != 0;
                        boolean expected = f.mStarted && !f.mReleased && f.mTouchEdgeEnabled
                                && !f.mTouchState.revealed && !f.mInteractionHold
                                && !f.mAutomaticHold
                                    && !(f.mPolicyVisible && !f.mAutoHide)
                                    && !f.mPointerState.revealed;
                        f.reveal();
                        f.reveal();
                        check(f.updates == (expected ? 1 : 0), "unexpected UI mutation " + flags);
                        check(f.mTouchState.requests == f.updates, "repeat created reveal state");
                    }
                }
                """ + RuntimeSourceFixture.methods("DesktopTaskbarRevealController",
                        "reveal", "currentPresentation", "isExplicitlyRevealed", "resolvePresentation"));
    }

    @Test
    public void fullscreenRetainsRevealWithEitherAutoHidePreference() {
        for (final boolean autoHide : new boolean[] { false, true }) {
            assertEquals(EDGE, resolvePresentation(
                    false, autoHide, false, false, false));
            assertEquals(VISIBLE, resolvePresentation(
                    false, autoHide, false, false, true));
            assertEquals(VISIBLE, resolvePresentation(
                    false, autoHide, false, true, false));
        }
    }

    @Test
    public void liveSessionAlwaysHasEitherTaskbarOrRevealEdge() {
        for (int flags = 0; flags < 32; flags++) {
            boolean policy = (flags & 1) != 0, autoHide = (flags & 2) != 0,
                    automaticHold = (flags & 4) != 0, pointer = (flags & 8) != 0,
                    explicit = (flags & 16) != 0;
            assertEquals(policy && !autoHide || automaticHold || pointer || explicit ? VISIBLE : EDGE,
                    resolvePresentation(policy, autoHide, automaticHold, pointer, explicit));
        }
    }

    @Test
    public void desktopPreferenceChoosesPinnedPanelOrRevealEdge() {
        assertEquals(VISIBLE, resolvePresentation(
                true, false, false, false, false));
        assertEquals(EDGE, resolvePresentation(
                true, true, false, false, false));
        assertEquals(VISIBLE, resolvePresentation(
                true, true, false, true, false));
    }

    @Test
    public void automaticHoldOverridesFullscreenConcealment() {
        assertEquals(VISIBLE, resolvePresentation(
                false, false, true, false, false));
        assertEquals(EDGE, resolvePresentation(
                false, false, false, false, false));
    }

    @Test
    public void fullscreenCanRevealAgainAfterPointerLeaves() {
        final PointerEdgeRevealState pointer = new PointerEdgeRevealState();
        pointer.onPointerEntered();
        pointer.setArmed(resolvePresentation(
                false, false, false, false, false) == EDGE);
        assertEquals(VISIBLE, resolvePresentation(
                false, false, false, pointer.isRevealed(), false));

        assertEquals(PointerEdgeRevealState.TimerAction.START_HIDE,
                pointer.onPointerExited());
        assertTrue(pointer.onHideTimeout());
        assertEquals(EDGE, resolvePresentation(
                false, false, false, pointer.isRevealed(), false));

        assertEquals(PointerEdgeRevealState.TimerAction.START_REVEAL,
                pointer.onPointerEntered());
        assertTrue(pointer.onRevealTimeout());
        assertEquals(VISIBLE, resolvePresentation(
                false, false, false, pointer.isRevealed(), false));
    }

    @Test
    public void explicitRevealSurvivesSnapshotChangesUntilUserDismissesIt() throws Exception {
        RuntimeSourceFixture.verify("""
                enum Presentation { EDGE, VISIBLE }
                boolean mStarted = true, mTouchEdgeEnabled = true, mReleased,
                        mPolicyVisible, mAutoHide, mAutomaticHold, mInteractionHold;
                static class PointerEdgeRevealState {
                """ + RuntimeSourceFixture.methods("PointerEdgeRevealState", "setArmed", "isRevealed") + """
                    boolean mArmed, mPointerInside, mRevealed, mRevealPending, mHidePending;
                }
                static class TouchEdgeRevealState {
                    enum Action { NONE, REVEAL, DISMISS }
                    boolean mArmed, mTracking, mRevealed, mDismissOnUp;
                    float mDownX, mDownY;
                """ + RuntimeSourceFixture.methods("TouchEdgeRevealState",
                        "setArmed", "reveal", "isRevealed", "onDown", "onUp", "dismiss") + """
                }
                PointerEdgeRevealState mPointerState = new PointerEdgeRevealState();
                TouchEdgeRevealState mTouchState = new TouchEdgeRevealState();
                void cancelTimers() {}
                void applyPresentation() {}
                public static void verify() {
                    Fixture f = new Fixture();
                    f.updateArmedState();
                    check(f.mTouchState.mArmed, "fullscreen lost its reveal edge");
                    f.mTouchState.reveal();
                    for (boolean visible : new boolean[] {true, false, true, false}) {
                        f.setPolicyVisible(visible);
                        check(f.mTouchState.isRevealed(), "snapshot cancelled explicit reveal");
                    }
                    check(f.mTouchState.mArmed, "fullscreen lost edge gestures");
                    check(f.mTouchState.dismiss() == TouchEdgeRevealState.Action.DISMISS,
                            "outside touch must dismiss");
                    check(f.currentPresentation() == Presentation.EDGE,
                            "outside touch must restore the reveal edge");
                    f.mTouchState.reveal();
                    f.mTouchState.onDown(10, 10);
                    check(f.mTouchState.onUp() == TouchEdgeRevealState.Action.DISMISS,
                            "taskbar action must dismiss navigation reveal");
                    f.mTouchState.reveal();
                    f.setVisibilityHolds(false, true);
                    check(!f.mTouchState.isRevealed(), "Start did not consume navigation reveal");
                    check(f.currentPresentation() == Presentation.VISIBLE, "Start did not retain taskbar");
                    f.mTouchState.dismiss();
                    check(f.currentPresentation() == Presentation.VISIBLE,
                            "interaction inside Start cancelled its taskbar hold");
                    f.setVisibilityHolds(true, true);
                    check(f.currentPresentation() == Presentation.VISIBLE, "IME hid Start taskbar");
                    f.setVisibilityHolds(true, false);
                    check(f.currentPresentation() == Presentation.VISIBLE, "IME hold lost");
                    f.setVisibilityHolds(false, false);
                    check(f.currentPresentation() == Presentation.EDGE, "fullscreen edge lost");
                    f.setVisibilityHolds(false, true);
                    check(f.currentPresentation() == Presentation.VISIBLE, "Start hold lost");
                    f.setPolicyVisible(true);
                    f.setPolicyVisible(false);
                    check(f.currentPresentation() == Presentation.VISIBLE, "snapshot cancelled Start hold");
                    f.setVisibilityHolds(false, false);
                    check(f.currentPresentation() == Presentation.EDGE, "Start dismissal lost reveal edge");
                }
                """ + RuntimeSourceFixture.methods("DesktopTaskbarRevealController",
                        "setPolicyVisible", "setVisibilityHolds", "updateArmedState",
                        "currentPresentation", "isExplicitlyRevealed", "resolvePresentation"));
    }
}
