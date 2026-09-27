package io.github.mekhontsev.magicdesk;

import java.util.Map;

/** Symbolic references, not Android resource IDs or executable/file-system resources. */
public record ShellResources(Map<Icon, Icon> icons) {
    public enum Icon { DESKTOP, WINDOWS, NOTIFICATIONS, KEYBOARD, CONTROLS, FILES, TERMINAL, SETTINGS, SEARCH, CAMERA, VIDEO }
    public ShellResources { icons = Map.copyOf(icons); }
    public Icon resolve(Icon role) { return icons.getOrDefault(role, role); }
    public static ShellResources defaults() { return new ShellResources(Map.of()); }
}
