package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArrayList;

/** App-owned global defaults. Resolution is independent of display IDs and Desktop lifetimes. */
public final class AppearanceStore {
    private static volatile ShellAppearance sCurrent = ShellAppearance.defaults();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static SharedPreferences sPreferences;
    private AppearanceStore() {}

    static synchronized void initialize(Context context) {
        if (sPreferences != null) return;
        sPreferences = context.getSharedPreferences("shell-appearance", Context.MODE_PRIVATE);
        try { sCurrent = ShellAppearanceJson.parse(sPreferences.getString("document", "{}")); }
        catch (Exception error) { android.util.Log.w("MagicDeskAppearance", "Invalid saved appearance", error); }
    }
    public static ShellAppearance current() { return sCurrent; }
    public static void listen(Runnable listener) { LISTENERS.addIfAbsent(listener); }
    public static void unlisten(Runnable listener) { LISTENERS.remove(listener); }
    static void apply(ShellAppearance value) {
        synchronized (AppearanceStore.class) {
            if (sPreferences == null) initialize(MagicDeskApplication.applicationContext());
            if (value.equals(sCurrent)) return;
            try { sPreferences.edit().putString("document", ShellAppearanceJson.encode(value).toString()).apply(); }
            catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
            sCurrent = value;
        }
        final Handler main = new Handler(Looper.getMainLooper());
        if (Looper.myLooper() == Looper.getMainLooper()) publish(); else main.post(AppearanceStore::publish);
    }
    private static void publish() {
        UiAppearance.refresh();
        for (Runnable listener : LISTENERS) listener.run();
    }
}
