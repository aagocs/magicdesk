package io.github.mekhontsev.magicdesk.x11;

/** X window-manager intent and confirmed host state, separate from catalog metadata. */
public record X11WindowManagement(boolean managed, Request request, State actual, Maximization maximization,
        io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction interaction) {
    public record Request(int serial, boolean fullscreen) { }
    public record State(boolean fullscreen) { }
    public record Maximization(int serial, io.github.mekhontsev.magicdesk.hosted.HostedMaximization requested,
            io.github.mekhontsev.magicdesk.hosted.HostedMaximization actual) { }

    static io.github.mekhontsev.magicdesk.hosted.HostedMaximization axes(int value) {
        if ((value & ~3) != 0) throw new IllegalArgumentException("Invalid maximization axes");
        return io.github.mekhontsev.magicdesk.hosted.HostedMaximization.of((value & 1) != 0, (value & 2) != 0);
    }

    public X11WindowManagement {
        java.util.Objects.requireNonNull(request);
        java.util.Objects.requireNonNull(actual);
        java.util.Objects.requireNonNull(maximization);
        java.util.Objects.requireNonNull(interaction);
    }

    // JNI constructs the snapshot at the native boundary; callers consume the typed records.
    private X11WindowManagement(boolean managed, int serial, boolean requested, boolean actual,
            int maximizeSerial, int maximizeRequested, int maximizeActual, int interactionSerial, int action, boolean attention) {
        this(managed, new Request(serial, requested), new State(actual),
                new Maximization(maximizeSerial, axes(maximizeRequested), axes(maximizeActual)),
                new io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction(Integer.toUnsignedLong(interactionSerial),
                        io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction.Action.values()[action], attention));
    }
}
