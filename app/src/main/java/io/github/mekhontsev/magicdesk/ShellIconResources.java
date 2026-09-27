package io.github.mekhontsev.magicdesk;

/** Android resource mapping stays outside the portable configuration contract. */
final class ShellIconResources {
    static int resolve(int original, ShellResources resources) {
        for (var role : ShellResources.Icon.values()) if (resource(role) == original) return resource(resources.resolve(role));
        return original;
    }
    private static int resource(ShellResources.Icon icon) {
        return switch (icon) {
            case DESKTOP -> R.drawable.ic_show_desktop;
            case WINDOWS -> R.drawable.ic_file_new_window;
            case NOTIFICATIONS -> R.drawable.ic_notifications;
            case KEYBOARD -> R.drawable.ic_keyboard;
            case CONTROLS -> R.drawable.ic_quick_controls;
            case FILES -> R.drawable.ic_desktop_folder;
            case TERMINAL -> R.drawable.ic_file_console;
            case SETTINGS -> R.drawable.ic_settings;
            case SEARCH -> R.drawable.ic_search;
            case CAMERA -> R.drawable.ic_camera;
            case VIDEO -> R.drawable.ic_video;
        };
    }
}
