package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.Display;
import java.util.Objects;
import java.util.WeakHashMap;

/** Hosts supply identity explicitly. Unwrapping never acquires a service or inspects a display. */
final class AppearanceScopeBindings {
    private record Binding(String scope, int displayId) { }
    private static final WeakHashMap<Context, Binding> SCOPES = new WeakHashMap<>();
    private AppearanceScopeBindings() { }

    static synchronized boolean bind(Context context, String scope) {
        return bind(context, -1, scope);
    }
    static synchronized boolean bind(Context context, int displayId, String scope) {
        Objects.requireNonNull(context, "scope context");
        WorkspaceAppearance.requireScope(scope);
        if (displayId < -1) throw new IllegalArgumentException("Invalid live display ID");
        for (var entry : SCOPES.entrySet()) {
            Binding binding = entry.getValue();
            if (displayId >= 0 && entry.getKey() != context && binding.displayId() == displayId
                    && !binding.scope().equals(scope)) throw new IllegalStateException("Display already has another appearance scope");
        }
        Binding binding = new Binding(scope, displayId);
        return !binding.equals(SCOPES.put(context, binding));
    }
    static synchronized boolean unbind(Context context) { return SCOPES.remove(context) != null; }
    static synchronized java.util.List<String> scopes() {
        return SCOPES.values().stream().map(Binding::scope).distinct().sorted().toList();
    }
    static synchronized String find(Context context) {
        Context caller = context;
        for (int depth = 0; context != null && depth < 64; depth++) {
            Binding binding = SCOPES.get(context);
            if (binding != null) return binding.scope();
            if (!(context instanceof ContextWrapper wrapper)) break;
            Context base = wrapper.getBaseContext();
            if (base == context) break;
            context = base;
        }
        if (caller == null) return null;
        final Display display;
        try { display = caller.getDisplay(); }
        catch (UnsupportedOperationException error) { return null; }
        if (display != null) for (Binding binding : SCOPES.values()) {
            if (binding.displayId() == display.getDisplayId()) return binding.scope();
        }
        return null;
    }
}
