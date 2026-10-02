package io.github.mekhontsev.magicdesk;

import java.util.ArrayList;
import java.util.List;

/** Pure geometry for selecting visible desktop items with a marquee. */
final class DesktopMarqueeSelection {
    static final class Rectangle {
        final float left;
        final float top;
        final float right;
        final float bottom;

        private Rectangle(
                final float left,
                final float top,
                final float right,
                final float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        static Rectangle normalized(
                final float startX,
                final float startY,
                final float endX,
                final float endY) {
            if (!Float.isFinite(startX) || !Float.isFinite(startY)
                    || !Float.isFinite(endX) || !Float.isFinite(endY)) {
                return empty();
            }
            return new Rectangle(
                    Math.min(startX, endX),
                    Math.min(startY, endY),
                    Math.max(startX, endX),
                    Math.max(startY, endY));
        }

        static Rectangle bounds(
                final float left,
                final float top,
                final float right,
                final float bottom) {
            return normalized(left, top, right, bottom);
        }

        static Rectangle empty() {
            return new Rectangle(0, 0, 0, 0);
        }

        Rectangle intersect(final Rectangle other) {
            if (other == null) {
                return empty();
            }
            final float intersectLeft = Math.max(left, other.left);
            final float intersectTop = Math.max(top, other.top);
            final float intersectRight = Math.min(right, other.right);
            final float intersectBottom = Math.min(bottom, other.bottom);
            if (intersectRight <= intersectLeft
                    || intersectBottom <= intersectTop) {
                return empty();
            }
            return new Rectangle(
                    intersectLeft,
                    intersectTop,
                    intersectRight,
                    intersectBottom);
        }

        boolean isEmpty() {
            return right <= left || bottom <= top;
        }
    }

    static final class ItemBounds {
        final String itemId;
        final Rectangle bounds;

        ItemBounds(final String itemId, final Rectangle bounds) {
            this.itemId = itemId;
            this.bounds = bounds;
        }
    }

    private DesktopMarqueeSelection() { }

    static List<String> intersectingItemIds(
            final Rectangle marquee,
            final Rectangle viewport,
            final List<ItemBounds> items) {
        final List<String> selected = new ArrayList<>();
        if (marquee == null || viewport == null || items == null) {
            return selected;
        }
        final Rectangle visibleMarquee = marquee.intersect(viewport);
        if (visibleMarquee.isEmpty()) {
            return selected;
        }
        for (final ItemBounds item : items) {
            if (item == null || item.itemId == null || item.bounds == null) {
                continue;
            }
            if (item.bounds.right > visibleMarquee.left
                    && item.bounds.left < visibleMarquee.right
                    && item.bounds.bottom > visibleMarquee.top
                    && item.bounds.top < visibleMarquee.bottom) {
                selected.add(item.itemId);
            }
        }
        return selected;
    }
}
