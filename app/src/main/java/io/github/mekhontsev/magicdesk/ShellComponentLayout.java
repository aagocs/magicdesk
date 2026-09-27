package io.github.mekhontsev.magicdesk;

import java.util.List;

/** Fixed controls retain their size. Surplus is shared; overflow stays scrollable, never negative. */
final class ShellComponentLayout {
    record Slot(int minimum, boolean flexible) {
        Slot { if (minimum < 0) throw new IllegalArgumentException("negative component size"); }
    }
    static int[] widths(List<Slot> slots, int available) {
        int minimum = 0, flexible = 0;
        for (var slot : slots) { minimum += slot.minimum(); if (slot.flexible()) flexible++; }
        int extra = Math.max(0, available - minimum);
        int[] result = new int[slots.size()];
        for (int i = 0; i < result.length; i++) {
            var slot = slots.get(i);
            int share = slot.flexible() ? extra / flexible-- : 0;
            result[i] = slot.minimum() + share; extra -= share;
        }
        return result;
    }
}
