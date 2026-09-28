package io.github.mekhontsev.magicdesk;

import android.os.IBinder;

import java.lang.reflect.Method;

/** Shell-authorized IME requests; editor connections remain owned by Android windows. */
final class FrameworkInputMethodApi {
    private Object mStatusBar;
    private Method mHideCurrentInputMethod;
    private Method mWindowInterface;
    private Method mShowInsets;

    private synchronized void resolveHide() throws ReflectiveOperationException {
        if (mHideCurrentInputMethod != null) return;
        final IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "statusbar");
        if (binder == null) {
            throw new IllegalStateException("Android status bar service is unavailable");
        }
        mStatusBar = Class.forName("com.android.internal.statusbar.IStatusBarService$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        mHideCurrentInputMethod = Class.forName(
                "com.android.internal.statusbar.IStatusBarService")
                .getMethod("hideCurrentInputMethodForBubbles", int.class);
    }

    void hideCurrentInputMethod(final int originatingDisplayId)
            throws ReflectiveOperationException {
        if (originatingDisplayId < 0) {
            throw new IllegalArgumentException("originating display is required");
        }
        resolveHide();
        // The display supplies user context, not a filter for the current editor's display.
        mHideCurrentInputMethod.invoke(mStatusBar, originatingDisplayId);
    }

    private synchronized void resolveShow() throws ReflectiveOperationException {
        if (mShowInsets != null) return;
        mWindowInterface = Class.forName("android.view.IWindow$Stub")
                .getMethod("asInterface", IBinder.class);
        mShowInsets = Class.forName("android.view.IWindow").getMethod("showInsets",
                int.class, boolean.class, Class.forName("android.view.inputmethod.ImeTracker$Token"));
    }

    void requestShow(IBinder window) throws ReflectiveOperationException {
        if (window == null) throw new IllegalArgumentException("input window is required");
        resolveShow();
        // The client runs its normal InsetsController/IMM path using its existing focused editor.
        mShowInsets.invoke(mWindowInterface.invoke(null, window), android.view.WindowInsets.Type.ime(), false, null);
    }
}
