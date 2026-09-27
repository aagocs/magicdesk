package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArrayList;

/** App-owned global defaults. Resolution is independent of display IDs and Desktop lifetimes. */
public final class AppearanceStore {
    private static volatile ShellAppearance sCurrent = ShellAppearance.defaults();
    private static AppearanceTransaction sTransaction = new AppearanceTransaction(sCurrent);
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static SharedPreferences sPreferences;
    private AppearanceStore() {}

    static synchronized void initialize(Context context) {
        if (sPreferences != null) return;
        sPreferences = context.getSharedPreferences("shell-appearance", Context.MODE_PRIVATE);
        try { sCurrent = ShellAppearanceJson.parse(sPreferences.getString("document", "{}")); }
        catch (Exception error) { android.util.Log.w("MagicDeskAppearance", "Invalid saved appearance", error); }
        sTransaction = new AppearanceTransaction(sCurrent);
    }
    public static ShellAppearance current() { return sCurrent; }
    public static void listen(Runnable listener) { LISTENERS.addIfAbsent(listener); }
    public static void unlisten(Runnable listener) { LISTENERS.remove(listener); }
    static synchronized AppearanceTransaction.Snapshot snapshot() { return sTransaction.snapshot(); }
    static String preview(ShellAppearance value) {
        final String id;
        synchronized (AppearanceStore.class) {
            id = sTransaction.preview(value); sCurrent = value;
        }
        changed();
        return id;
    }
    static void cancel(String id) {
        synchronized (AppearanceStore.class) {
            sTransaction.cancel(id); sCurrent = sTransaction.snapshot().current();
        }
        changed();
    }
    static void confirm(String id) {
        synchronized (AppearanceStore.class) { persist(sTransaction.requirePreview(id)); }
        changed();
    }
    static void apply(ShellAppearance value) {
        synchronized (AppearanceStore.class) {
            if (value.equals(sCurrent) && sTransaction.snapshot().previewId() == null) return;
            persist(value);
        }
        changed();
    }
    private static void persist(ShellAppearance value) {
        if (sPreferences == null) initialize(MagicDeskApplication.applicationContext());
        try { sPreferences.edit().putString("document", ShellAppearanceJson.encode(value).toString()).apply(); }
        catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
        sTransaction.apply(value); sCurrent = value;
    }
    private static void changed() {
        final Handler main = new Handler(Looper.getMainLooper());
        if (Looper.myLooper() == Looper.getMainLooper()) publish(); else main.post(AppearanceStore::publish);
    }
    private static void publish() {
        UiAppearance.refresh();
        for (Runnable listener : LISTENERS) listener.run();
    }
}
