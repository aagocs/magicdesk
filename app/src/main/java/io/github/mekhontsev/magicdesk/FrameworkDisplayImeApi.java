package io.github.mekhontsev.magicdesk;

import android.os.IBinder;

import java.lang.reflect.Method;

/** Optional per-display IME policy; resolved only when placement is requested. */
final class FrameworkDisplayImeApi {
    private final Object mWindowManager;
    private final Method mGet;
    private final Method mSet;

    FrameworkDisplayImeApi() throws ReflectiveOperationException {
        final IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "window");
        if (binder == null) throw new IllegalStateException("window service is unavailable");
        final Class<?> interfaceType = Class.forName("android.view.IWindowManager");
        mWindowManager = Class.forName("android.view.IWindowManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        if (mWindowManager == null) throw new IllegalStateException("window manager interface is unavailable");
        mGet = interfaceType.getMethod("getDisplayImePolicy", int.class);
        mSet = interfaceType.getMethod("setDisplayImePolicy", int.class, int.class);
    }

    int get(final int displayId) throws ReflectiveOperationException {
        return (Integer) mGet.invoke(mWindowManager, displayId);
    }

    void set(final int displayId, final int policy) throws ReflectiveOperationException {
        mSet.invoke(mWindowManager, displayId, policy);
    }
}
