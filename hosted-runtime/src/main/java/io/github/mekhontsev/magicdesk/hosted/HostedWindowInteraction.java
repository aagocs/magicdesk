package io.github.mekhontsev.magicdesk.hosted;

/** A compositor-authorized request, distinct from observed Android task state. */
public record HostedWindowInteraction(long serial, Action action, boolean attention) {
    public enum Action { NONE, ACTIVATE, MINIMIZE }
    public record State(long serial, boolean active, boolean minimized, boolean attention) { }
    public HostedWindowInteraction {
        if (serial < 0 || action == null) throw new IllegalArgumentException("Invalid window interaction");
    }
}
