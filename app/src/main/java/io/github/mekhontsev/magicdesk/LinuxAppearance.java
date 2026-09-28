package io.github.mekhontsev.magicdesk;

import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.Configuration;
import io.github.mekhontsev.magicdesk.hosted.HostedColorScheme;
import java.io.Closeable;
import java.util.function.IntConsumer;

/** One configuration subscription per retained application session, without Desktop prerequisites. */
final class LinuxAppearance implements Closeable, ComponentCallbacks {
    private final Context context;
    private final IntConsumer sink;
    private int current;
    private volatile boolean closed;

    LinuxAppearance(Context context, IntConsumer sink) {
        this.context = context.getApplicationContext();
        this.sink = sink;
        current = read(this.context);
        this.context.registerComponentCallbacks(this);
        sink.accept(current);
    }
    static int read(Context context) { return resolve(context.getResources().getConfiguration().uiMode); }
    static int resolve(int uiMode) {
        return switch (uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES -> HostedColorScheme.DARK;
            case Configuration.UI_MODE_NIGHT_NO -> HostedColorScheme.LIGHT;
            default -> HostedColorScheme.NONE;
        };
    }
    @Override public void onConfigurationChanged(Configuration configuration) {
        int value = resolve(configuration.uiMode);
        if (!closed && value != current) { current = value; sink.accept(value); }
    }
    @Override public void onLowMemory() { }
    @Override public void close() {
        if (!closed) { closed = true; context.unregisterComponentCallbacks(this); }
    }
}
