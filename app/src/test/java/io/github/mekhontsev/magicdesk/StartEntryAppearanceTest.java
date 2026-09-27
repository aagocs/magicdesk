package io.github.mekhontsev.magicdesk;

import org.junit.Test;
import static org.junit.Assert.*;

public final class StartEntryAppearanceTest {
    @Test public void borderIndicatesOnlySelectionOrKeyboardFocus() throws Exception {
        RuntimeSourceFixture.verify("""
                static class android { static class R { static class attr {
                    static final int state_selected=1, state_focused=2, state_pressed=3, state_enabled=4, state_hovered=5;
                } } }
                enum Role { TRANSPARENT, HOVER, SURFACE, ACCENT }
                static final Role TRANSPARENT=Role.TRANSPARENT, HOVER=Role.HOVER, SURFACE=Role.SURFACE, ACCENT=Role.ACCENT;
                record Background(Role fill, int radius, Role border) {}
                Background rounded(Role fill, int radius, Role border) { return new Background(fill, radius, border); }
                Background filled(Role fill, int radius) { return rounded(fill, radius, TRANSPARENT); }
                static class StateListDrawable {
                    final Map<Integer, Background> states = new LinkedHashMap<>();
                    void addState(int[] state, Background background) { states.put(state.length == 0 ? 0 : state[0], background); }
                }
                public static void verify() {
                    for (int radius : new int[] {7, 12}) {
                        var states = new Fixture().flatButtonBackground(radius).states;
                        check(states.size() == 6, "missing interactive state");
                        for (int state : new int[] {1, 2}) check(states.get(state).border() == ACCENT, "focus/selection lost outline");
                        for (int state : new int[] {0, 3, 5, -4}) check(states.get(state).border() == TRANSPARENT, "permanent outline");
                        check(states.get(0).fill() == TRANSPARENT, "idle button paints its own background");
                        check(states.values().stream().allMatch(value -> value.radius() == radius), "state changes geometry");
                    }
                }
                """ + RuntimeSourceFixture.methods("DesktopUiFactory", "flatButtonBackground"));
    }
    @Test public void gridAndSearchShareAppearanceIndependentOfLaunchBackend() throws Exception {
        final String tile = RuntimeSourceFixture.methods("StartMenuContent", "createAppTile");
        assertTrue(tile.contains("tile.setBackground(entryBackground(12))"));
        assertFalse(tile.contains("canFloat"));
        final String row = RuntimeSourceFixture.methods("StartMenuContent", "createSearchRow");
        assertTrue(row.contains("row.setBackground(entryBackground(7))"));
        assertTrue(row.contains("row.setSelected(selected)"));
        assertTrue(RuntimeSourceFixture.methods("StartMenuContent", "entryBackground").contains("mUi.flatButtonBackground"));
    }
}
