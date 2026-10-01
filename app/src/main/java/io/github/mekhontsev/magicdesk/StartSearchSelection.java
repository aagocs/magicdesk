package io.github.mekhontsev.magicdesk;

import java.util.List;

/** Keeps keyboard selection attached to a result identity across search updates. */
final class StartSearchSelection {
    private List<String> mKeys = List.of();
    private int mIndex = -1;

    void update(final List<String> keys) {
        final String previousKey = mIndex < 0 ? null : mKeys.get(mIndex);
        final int previousIndex = mIndex;
        mKeys = List.copyOf(keys);
        if (mKeys.isEmpty()) {
            mIndex = -1;
            return;
        }
        final int retained = previousKey == null ? -1 : mKeys.indexOf(previousKey);
        mIndex = retained >= 0 ? retained
                : Math.min(mKeys.size() - 1, Math.max(0, previousIndex));
    }

    void move(final int delta) {
        if (!mKeys.isEmpty()) {
            mIndex = Math.min(mKeys.size() - 1, Math.max(0, mIndex + delta));
        }
    }

    int index() { return mIndex; }

    void reset() {
        mKeys = List.of();
        mIndex = -1;
    }
}
