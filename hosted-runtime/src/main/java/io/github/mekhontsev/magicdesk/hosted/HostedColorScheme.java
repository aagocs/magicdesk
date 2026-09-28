package io.github.mekhontsev.magicdesk.hosted;

/** freedesktop appearance preference; not a toolkit theme name or a renderer palette. */
public final class HostedColorScheme {
    public static final int NONE = 0, DARK = 1, LIGHT = 2;
    private HostedColorScheme() { }
    public static int require(int value) {
        if (value < NONE || value > LIGHT) throw new IllegalArgumentException("Invalid color scheme");
        return value;
    }
}
