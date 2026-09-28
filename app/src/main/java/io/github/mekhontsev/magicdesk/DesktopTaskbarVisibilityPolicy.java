package io.github.mekhontsev.magicdesk;

final class DesktopTaskbarVisibilityPolicy {
    private DesktopTaskbarVisibilityPolicy() {
    }

    static boolean isVisible(
            final boolean localDisplay,
            final DesktopWorkspaceScene scene,
            final boolean previouslyVisible) {
        // This is workspace policy visibility. The reveal controller still
        // applies the user's auto-hide setting to the rendered taskbar.
        return switch (scene) {
            case HOME, FREEFORM -> true;
            case FULLSCREEN -> false;
            case UNKNOWN -> localDisplay ? previouslyVisible : true;
        };
    }
}
