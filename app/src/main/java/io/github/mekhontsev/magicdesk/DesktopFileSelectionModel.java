package io.github.mekhontsev.magicdesk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Selection state for desktop files, independent of Android views. */
final class DesktopFileSelectionModel {
    private final LinkedHashSet<String> mSelected = new LinkedHashSet<>();
    private String mAnchor;
    private String mFocused;

    void selectOnly(final String itemId) {
        mSelected.clear();
        if (itemId != null) {
            mSelected.add(itemId);
        }
        mAnchor = itemId;
        mFocused = itemId;
    }

    /**
     * Replaces the selection with {@code itemIds} in the given order; the anchor is the first
     * item and the focused item the last. Used by marquee selection, where the set is recomputed
     * from geometry on every pointer move.
     */
    void setSelection(final Collection<String> itemIds) {
        mSelected.clear();
        if (itemIds != null) {
            mSelected.addAll(itemIds);
        }
        mAnchor = null;
        mFocused = null;
        for (final String itemId : mSelected) {
            if (mAnchor == null) {
                mAnchor = itemId;
            }
            mFocused = itemId;
        }
    }

    boolean selectModified(
            final String itemId,
            final List<String> visibleItemIds,
            final boolean control,
            final boolean shift) {
        if (itemId == null || visibleItemIds == null
                || !visibleItemIds.contains(itemId)) {
            return false;
        }
        if (!shift) {
            if (!control) {
                selectOnly(itemId);
                return true;
            }
            if (!mSelected.add(itemId)) {
                mSelected.remove(itemId);
            }
            mAnchor = itemId;
            mFocused = itemId;
            return true;
        }

        int targetIndex = visibleItemIds.indexOf(itemId);
        int anchorIndex = visibleItemIds.indexOf(mAnchor);
        if (anchorIndex < 0) {
            for (int index = 0; index < visibleItemIds.size(); index++) {
                if (mSelected.contains(visibleItemIds.get(index))) {
                    anchorIndex = index;
                    mAnchor = visibleItemIds.get(index);
                    break;
                }
            }
        }
        if (anchorIndex < 0) {
            anchorIndex = targetIndex;
            mAnchor = itemId;
        }
        if (!control) {
            mSelected.clear();
        }
        for (int index = Math.min(anchorIndex, targetIndex);
                index <= Math.max(anchorIndex, targetIndex); index++) {
            mSelected.add(visibleItemIds.get(index));
        }
        mFocused = itemId;
        return true;
    }

    void selectAll(final Collection<String> visibleItemIds) {
        mSelected.clear();
        if (visibleItemIds != null) {
            mSelected.addAll(visibleItemIds);
        }
        mAnchor = mSelected.isEmpty() ? null : mSelected.iterator().next();
        mFocused = mAnchor;
    }

    void retain(final Set<String> liveItemIds) {
        mSelected.retainAll(liveItemIds);
        if (mAnchor != null && !liveItemIds.contains(mAnchor)) {
            mAnchor = mSelected.isEmpty() ? null : mSelected.iterator().next();
        }
        if (mFocused != null && !liveItemIds.contains(mFocused)) {
            mFocused = mSelected.isEmpty() ? null : mSelected.iterator().next();
        }
    }

    void remove(final String itemId) {
        mSelected.remove(itemId);
        if (itemId != null && itemId.equals(mAnchor)) {
            mAnchor = mSelected.isEmpty() ? null : mSelected.iterator().next();
        }
        if (itemId != null && itemId.equals(mFocused)) {
            mFocused = mSelected.isEmpty() ? null : mSelected.iterator().next();
        }
    }

    void clear() {
        mSelected.clear();
        mAnchor = null;
        mFocused = null;
    }

    boolean contains(final String itemId) {
        return mSelected.contains(itemId);
    }

    boolean isEmpty() {
        return mSelected.isEmpty();
    }

    boolean isSingleSelection() {
        return mSelected.size() == 1;
    }

    List<String> selectedItemIds() {
        return new ArrayList<>(mSelected);
    }

    List<String> selectedItemIdsInOrder(
            final Collection<String> orderedItemIds) {
        final List<String> ordered = new ArrayList<>();
        if (orderedItemIds == null) {
            return ordered;
        }
        for (final String itemId : orderedItemIds) {
            if (mSelected.contains(itemId)) {
                ordered.add(itemId);
            }
        }
        return ordered;
    }

    String focusedItemId() {
        return mFocused;
    }
}
