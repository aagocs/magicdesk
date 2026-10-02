package io.github.mekhontsev.magicdesk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class DesktopPreferences {
    static final int SYSTEM_DESKTOP_DPI = 0;

    private DesktopPreferences() {
    }

    static List<AppReference> taskbarApps() {
        return DesktopStateStore.read(state -> new ArrayList<>(state.taskbarApps),
                new ArrayList<>());
    }

    static void saveTaskbarApps(final Collection<AppReference> apps) {
        final List<AppReference> stored = new ArrayList<>();
        if (apps != null) {
            for (final AppReference app : apps) {
                if (app != null && !stored.contains(app)
                        && BuiltInDesktopAppCatalog.isPinnable(app.launchTarget())) {
                    stored.add(app);
                }
            }
        }
        DesktopStateStore.update(state -> {
            state.taskbarApps.clear();
            state.taskbarApps.addAll(stored);
            state.taskbarInitialized = true;
        });
    }

    /** Pins a fresh install's taskbar once; an explicit choice, including none, is kept. */
    static void initializeTaskbarApps(final AppIdentity builtInApplication) {
        if (DesktopStateStore.read(state -> state.taskbarInitialized, true)) {
            return;
        }
        final List<AppReference> defaults = defaultTaskbarApps(builtInApplication);
        DesktopStateStore.update(state -> initializeTaskbarApps(state, defaults));
    }

    static void initializeTaskbarApps(
            final DesktopStateStore.State state, final List<AppReference> defaults) {
        if (state.taskbarInitialized) {
            return;
        }
        if (state.taskbarApps.isEmpty()) {
            for (final AppReference app : defaults) {
                if (!state.taskbarApps.contains(app)) {
                    state.taskbarApps.add(app);
                }
            }
        }
        state.taskbarInitialized = true;
    }

    /** Built-in launchers only; the catalog decides which tools may be pinned. */
    static List<AppReference> defaultTaskbarApps(final AppIdentity builtInApplication) {
        final List<AppReference> result = new ArrayList<>();
        for (final AppLaunchTarget target : List.of(BuiltInDesktopAppCatalog.filesTarget())) {
            final AppReference app = AppReference.forTarget(builtInApplication, target);
            if (app != null && BuiltInDesktopAppCatalog.isPinnable(target)) {
                result.add(app);
            }
        }
        return result;
    }

}
