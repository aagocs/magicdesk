package io.github.mekhontsev.magicdesk;

import android.view.KeyEvent;

import java.util.HashSet;
import java.util.Set;

/** One keyboard's balanced shortcut stream, independent of transport and windows. */
final class KeyboardShortcutStateMachine {
    enum Action {
        NONE,
        ALT_TAB_FORWARD,
        ALT_TAB_REVERSE,
        ALT_TAB_COMMIT,
        TOGGLE_LAYOUT,
        DISMISS,
        CLOSE,
        BACK,
        LOCK,
        NOTIFICATIONS,
        SYSTEM,
        SETTINGS,
        FULLSCREEN,
        RESTORE,
        SNAP_LEFT,
        SNAP_RIGHT,
        SNAP_TOP_LEFT,
        SNAP_TOP_RIGHT,
        SNAP_BOTTOM_LEFT,
        SNAP_BOTTOM_RIGHT,
        SHOW_DESKTOP,
        DISPLAY_FORWARD,
        DISPLAY_REVERSE,
        DISPLAY_COMMIT,
        DISPLAY_CANCEL,
        SCREENSHOT,
        SCREEN_RECORDING,
        SHORTCUT_HELP,
        ACTIVATE_TASKBAR_ENTRY,
        MOVE_DISPLAY_PREVIOUS,
        MOVE_DISPLAY_NEXT,
        OPEN_FILES,
        OPEN_TASK_MANAGER,
        OPEN_START,
        TOGGLE_START
    }


    static final class Result {
        final boolean consumed;
        final Action action;
        /** Zero-based taskbar position for {@link Action#ACTIVATE_TASKBAR_ENTRY}, otherwise -1. */
        final int index;
        Result(final boolean consumed, final Action action) {
            this(consumed, action, -1);
        }
        Result(final boolean consumed, final Action action, final int index) {
            this.consumed = consumed;
            this.action = action;
            this.index = index;
        }
    }

    private final Set<Integer> mConsumed = new HashSet<>();
    private boolean mAltTabActive;
    private boolean mDisplayActive;
    private Action mSnapSide = Action.NONE;
    private int mSnapRow;
    /** A Windows key went down alone and nothing else has been pressed since. */
    private boolean mStartTap;

    Result accept(final int key, final boolean down, final int repeats,
            final boolean ctrl, final boolean alt, final boolean shift, final boolean meta,
            final boolean desktop) {
        return acceptInternal(key, down, repeats, ctrl, alt, shift, meta, desktop);
    }

    Result accept(final int key, final boolean down, final int repeats,
            final boolean ctrl, final boolean alt, final boolean shift, final boolean meta) {
        return acceptInternal(key, down, repeats, ctrl, alt, shift, meta, true);
    }

    private Result acceptInternal(final int key, final boolean down, final int repeats,
            final boolean ctrl, final boolean alt, final boolean shift, final boolean meta,
            final boolean desktop) {
        if (!meta || !desktop || ctrl || alt || shift) clearSnapSequence();
        if (!down) {
            final boolean consumed = mConsumed.remove(key);
            if (consumed && isMeta(key) && !metaHeld()) {
                // The last Windows key released: a lone tap toggles Start, as on Windows.
                final boolean tap = mStartTap && desktop && !ctrl && !alt && !shift;
                mStartTap = false;
                if (tap) return new Result(true, Action.TOGGLE_START);
            }
            if ((key == KeyEvent.KEYCODE_ALT_LEFT || key == KeyEvent.KEYCODE_ALT_RIGHT)
                    && !alt && mDisplayActive) {
                mDisplayActive = false;
                return new Result(consumed, Action.DISPLAY_COMMIT);
            }
            if ((key == KeyEvent.KEYCODE_ALT_LEFT || key == KeyEvent.KEYCODE_ALT_RIGHT)
                    && !alt && mAltTabActive) {
                mAltTabActive = false;
                return new Result(consumed, Action.ALT_TAB_COMMIT);
            }
            return new Result(consumed, Action.NONE);
        }
        if (!isMeta(key)) mStartTap = false;
        if (mConsumed.contains(key)) {
            return new Result(true, Action.NONE);
        }
        if (mDisplayActive && key == KeyEvent.KEYCODE_ESCAPE) {
            mDisplayActive = false;
            mConsumed.add(key);
            return new Result(true, Action.DISPLAY_CANCEL);
        }
        if (!mAltTabActive && !meta && alt && key == KeyEvent.KEYCODE_TAB
                && (ctrl || mDisplayActive)) {
            mDisplayActive = true;
            mConsumed.add(key);
            return new Result(true, shift ? Action.DISPLAY_REVERSE : Action.DISPLAY_FORWARD);
        }
        if (!desktop || mDisplayActive) return new Result(false, Action.NONE);
        // Suppress the system's standalone Meta action along with our Meta chords.
        if (isMeta(key)) {
            if (!metaHeld()) mStartTap = !ctrl && !alt && !shift;
            mConsumed.add(key);
            return new Result(true, Action.NONE);
        }
        if (repeats != 0) {
            return new Result(false, Action.NONE);
        }
        final int taskbarIndex = taskbarIndex(key, ctrl, alt, shift, meta);
        if (taskbarIndex >= 0) {
            clearSnapSequence();
            mConsumed.add(key);
            return new Result(true, Action.ACTIVATE_TASKBAR_ENTRY, taskbarIndex);
        }
        final Action action = snapSequence(action(key, ctrl, alt, shift, meta));
        if (action == Action.ALT_TAB_FORWARD || action == Action.ALT_TAB_REVERSE) {
            mAltTabActive = true;
        }
        // Escape continues to the focused app, as well as dismissing our transient UI.
        final boolean consumed = action != Action.NONE && action != Action.DISMISS;
        if (consumed) {
            mConsumed.add(key);
        }
        return new Result(consumed, action);
    }

    private Action snapSequence(final Action action) {
        if (action == Action.SNAP_LEFT || action == Action.SNAP_RIGHT) {
            mSnapSide = action;
            mSnapRow = 0;
            return action;
        }
        if (mSnapSide != Action.NONE
                && (action == Action.FULLSCREEN || action == Action.RESTORE)) {
            mSnapRow = Math.max(-1, Math.min(1,
                    mSnapRow + (action == Action.FULLSCREEN ? -1 : 1)));
            if (mSnapRow == 0) return mSnapSide;
            if (mSnapRow < 0) return mSnapSide == Action.SNAP_LEFT
                    ? Action.SNAP_TOP_LEFT : Action.SNAP_TOP_RIGHT;
            return mSnapSide == Action.SNAP_LEFT
                    ? Action.SNAP_BOTTOM_LEFT : Action.SNAP_BOTTOM_RIGHT;
        }
        clearSnapSequence();
        return action;
    }

    private void clearSnapSequence() {
        mSnapSide = Action.NONE;
        mSnapRow = 0;
    }

    private static boolean isMeta(final int key) {
        return key == KeyEvent.KEYCODE_META_LEFT || key == KeyEvent.KEYCODE_META_RIGHT;
    }

    private boolean metaHeld() {
        return mConsumed.contains(KeyEvent.KEYCODE_META_LEFT)
                || mConsumed.contains(KeyEvent.KEYCODE_META_RIGHT);
    }

    boolean reset() {
        final boolean cancel = mAltTabActive || mDisplayActive;
        mAltTabActive = false;
        mDisplayActive = false;
        mStartTap = false;
        mConsumed.clear();
        clearSnapSequence();
        return cancel;
    }

    /** Meta+1…9 address the taskbar's rendered entries; the dispatcher resolves the entry. */
    private static int taskbarIndex(final int key, final boolean ctrl, final boolean alt,
            final boolean shift, final boolean meta) {
        return meta && !ctrl && !alt && !shift
                && key >= KeyEvent.KEYCODE_1 && key <= KeyEvent.KEYCODE_9
                ? key - KeyEvent.KEYCODE_1 : -1;
    }

    private static Action action(final int key, final boolean ctrl, final boolean alt,
            final boolean shift, final boolean meta) {
        if (alt && !ctrl && !meta) {
            if (key == KeyEvent.KEYCODE_TAB) {
                return shift ? Action.ALT_TAB_REVERSE : Action.ALT_TAB_FORWARD;
            }
            if (!shift && key == KeyEvent.KEYCODE_F4) {
                return Action.CLOSE;
            }
        }
        if (!ctrl && !alt && !shift && !meta && key == KeyEvent.KEYCODE_ESCAPE) {
            return Action.DISMISS;
        }
        if (ctrl && !alt && !meta && key == KeyEvent.KEYCODE_ESCAPE) {
            return shift ? Action.OPEN_TASK_MANAGER : Action.OPEN_START;
        }
        if (!meta || ctrl || alt) {
            return Action.NONE;
        }
        if (key == KeyEvent.KEYCODE_SYSRQ) {
            return shift ? Action.SCREEN_RECORDING : Action.SCREENSHOT;
        }
        if (shift) {
            return key == KeyEvent.KEYCODE_DPAD_LEFT ? Action.MOVE_DISPLAY_PREVIOUS
                    : key == KeyEvent.KEYCODE_DPAD_RIGHT ? Action.MOVE_DISPLAY_NEXT
                    : Action.NONE;
        }
        return switch (key) {
            case KeyEvent.KEYCODE_DEL -> Action.BACK;
            case KeyEvent.KEYCODE_L -> Action.LOCK;
            case KeyEvent.KEYCODE_N -> Action.NOTIFICATIONS;
            // Win+A matches Windows quick settings; Win+Q stays as the documented alias.
            case KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_Q -> Action.SYSTEM;
            case KeyEvent.KEYCODE_I -> Action.SETTINGS;
            case KeyEvent.KEYCODE_DPAD_UP -> Action.FULLSCREEN;
            case KeyEvent.KEYCODE_DPAD_DOWN -> Action.RESTORE;
            case KeyEvent.KEYCODE_DPAD_LEFT -> Action.SNAP_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> Action.SNAP_RIGHT;
            case KeyEvent.KEYCODE_D -> Action.SHOW_DESKTOP;
            case KeyEvent.KEYCODE_SLASH -> Action.SHORTCUT_HELP;
            case KeyEvent.KEYCODE_SPACE -> Action.TOGGLE_LAYOUT;
            case KeyEvent.KEYCODE_E -> Action.OPEN_FILES;
            case KeyEvent.KEYCODE_S -> Action.OPEN_START;
            default -> Action.NONE;
        };
    }
}
