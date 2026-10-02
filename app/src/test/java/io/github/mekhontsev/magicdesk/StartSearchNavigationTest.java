package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class StartSearchNavigationTest {
    private static final String KEYS = """
            static class KeyEvent {
                static final int ACTION_DOWN = 0, ACTION_UP = 1, KEYCODE_DPAD_DOWN = 20,
                        KEYCODE_DPAD_UP = 19, KEYCODE_ENTER = 66,
                        KEYCODE_NUMPAD_ENTER = 160, KEYCODE_ESCAPE = 111,
                        KEYCODE_PAGE_DOWN = 93, KEYCODE_PAGE_UP = 92;
                int action, repeats;
                KeyEvent() { }
                KeyEvent(int action, int repeats) { this.action = action; this.repeats = repeats; }
                int getAction() { return action; }
                int getRepeatCount() { return repeats; }
            }
            record StartMenuEntry(String stableKey) { }
            static class View {
                boolean selected; int height = 20;
                void setSelected(boolean value) { selected = value; }
                int getHeight() { return height; }
            }
            static class LinearLayout {
                final List<View> children = new ArrayList<>();
                int getChildCount() { return children.size(); }
                View getChildAt(int index) { return children.get(index); }
            }
            static class SearchResultScrollView {
                View selected; int reveals, scrollY, height = 80;
                void setSelectedResult(View row, boolean reveal) { selected = row; if (reveal) reveals++; }
                void scrollTo(int x, int y) { scrollY = y; }
                int getHeight() { return height; }
            }
            static class Host { int dismisses; void dismiss() { dismisses++; } }
            Host mHost = new Host();
            final StartSearchSelection mSearchSelection = new StartSearchSelection();
            List<StartMenuEntry> mSearchResults = List.of();
            LinearLayout mSearchResultsList = new LinearLayout();
            SearchResultScrollView mSearchResultsScroll = new SearchResultScrollView();
            StartMenuEntry opened; int opens, renders;
            void renderBody() { renders++; }
            void openSearchResult(StartMenuEntry entry) { opened = entry; opens++; }
            void results(String... keys) {
                mSearchResults = Arrays.stream(keys).map(StartMenuEntry::new).toList();
                mSearchSelection.update(List.of(keys));
                mSearchResultsList.children.clear();
                for (String key : keys) mSearchResultsList.children.add(new View());
            }
            """;

    private static String keyMethods() throws Exception {
        return RuntimeSourceFixture.methods("StartMenuContent",
                "handleSearchKey", "updateSearchSelection", "resetSearchSelection", "searchPageSize");
    }

    @Test public void arrowsUpdateExistingRowsAndRevealSelectionWithoutRebuilding() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", KEYS + """
                public static void verify() {
                    Fixture f = new Fixture(); f.results("A", "B", "C");
                    View first = f.mSearchResultsList.getChildAt(0), second = f.mSearchResultsList.getChildAt(1);
                    check(f.handleSearchKey(KeyEvent.KEYCODE_DPAD_DOWN, new KeyEvent()), "Down not consumed");
                    check(!first.selected && second.selected, "highlight did not move in place");
                    check(f.mSearchResultsScroll.selected == second && f.mSearchResultsScroll.reveals == 1,
                            "selected row was not revealed");
                    check(f.mSearchResultsList.getChildAt(0) == first && f.renders == 0,
                            "selection movement replaced the viewport");
                    f.handleSearchKey(KeyEvent.KEYCODE_DPAD_UP, new KeyEvent());
                    check(first.selected && !second.selected, "Up did not restore the first highlight");
                }
                """ + keyMethods(), "StartSearchSelection");
    }

    @Test public void enterUsesTheRenderedSelectionIdentityAfterResultsMove() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", KEYS + """
                public static void verify() {
                    Fixture f = new Fixture(); f.results("A", "B", "C");
                    f.handleSearchKey(KeyEvent.KEYCODE_DPAD_DOWN, new KeyEvent());
                    f.results("X", "A", "B", "C");
                    f.handleSearchKey(KeyEvent.KEYCODE_ENTER, new KeyEvent());
                    check(f.opened.stableKey().equals("B"), "Enter launched a different result after an update");
                    f.handleSearchKey(KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_DOWN, 1));
                    check(f.opens == 1, "held Enter launched the entry again");
                    f.handleSearchKey(KeyEvent.KEYCODE_NUMPAD_ENTER, new KeyEvent());
                    check(f.opens == 2 && f.opened.stableKey().equals("B"), "numpad Enter selected another entry");
                }
                """ + keyMethods(), "StartSearchSelection");
    }

    @Test public void pageKeysUseMeasuredViewportAndClampWithoutRebuilding() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", KEYS + """
                public static void verify() {
                    Fixture f = new Fixture(); f.results("A", "B", "C", "D", "E", "F", "G", "H", "I");
                    View first = f.mSearchResultsList.getChildAt(0);
                    check(f.handleSearchKey(KeyEvent.KEYCODE_PAGE_DOWN, new KeyEvent()), "Page Down not consumed");
                    check(f.mSearchSelection.index() == 4, "Page Down did not use visible row count");
                    check(f.mSearchResultsScroll.selected == f.mSearchResultsList.getChildAt(4), "page selection not revealed");
                    f.handleSearchKey(KeyEvent.KEYCODE_PAGE_DOWN, new KeyEvent());
                    f.handleSearchKey(KeyEvent.KEYCODE_PAGE_DOWN, new KeyEvent());
                    check(f.mSearchSelection.index() == 8, "Page Down passed the final result");
                    f.handleSearchKey(KeyEvent.KEYCODE_PAGE_UP, new KeyEvent());
                    check(f.mSearchSelection.index() == 4, "Page Up did not reverse the page");
                    f.mSearchResultsScroll.height = 10;
                    f.handleSearchKey(KeyEvent.KEYCODE_PAGE_DOWN, new KeyEvent());
                    check(f.mSearchSelection.index() == 5, "tiny viewport did not move by one");
                    first.height = 0;
                    f.handleSearchKey(KeyEvent.KEYCODE_PAGE_UP, new KeyEvent());
                    check(f.mSearchSelection.index() == 4, "unlaid row did not use safe navigation fallback");
                    check(f.renders == 0 && f.mSearchResultsList.getChildAt(0) == first, "page navigation rebuilt rows");
                    f.results();
                    check(!f.handleSearchKey(KeyEvent.KEYCODE_PAGE_DOWN, new KeyEvent()), "empty Page Down consumed");
                    check(!f.handleSearchKey(KeyEvent.KEYCODE_PAGE_UP, new KeyEvent()), "empty Page Up consumed");
                }
                """ + keyMethods(), "StartSearchSelection");
    }

    @Test public void emptyResultsKeyUpAndEscapeDoNotLaunchAnEntry() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", KEYS + """
                public static void verify() {
                    Fixture f = new Fixture();
                    check(!f.handleSearchKey(KeyEvent.KEYCODE_ENTER, new KeyEvent()), "empty Enter consumed");
                    f.results("A");
                    check(!f.handleSearchKey(KeyEvent.KEYCODE_ENTER, new KeyEvent(KeyEvent.ACTION_UP, 0)),
                            "key up was treated as a launch");
                    f.handleSearchKey(KeyEvent.KEYCODE_ESCAPE, new KeyEvent());
                    check(f.opens == 0 && f.mHost.dismisses == 1, "Escape did not dismiss without launch");
                    f.mSearchResultsScroll.scrollY = 120;
                    f.resetSearchSelection();
                    check(f.mSearchSelection.index() == -1 && f.mSearchResultsScroll.scrollY == 0,
                            "query/destination reset retained navigation state");
                }
                """ + keyMethods(), "StartSearchSelection");
    }

    private static final String LAYOUT = """
            static class Activity { }
            static class Rect {
                int left, top, right, bottom;
                Rect() { }
                Rect(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
                int height() { return bottom - top; }
            }
            static class View {
                boolean layoutRequested; int width = 100, height = 60, visibleHeight = 60, reveals;
                Rect requested;
                boolean isLayoutRequested() { return layoutRequested; }
                int getWidth() { return width; }
                int getHeight() { return height; }
                boolean getLocalVisibleRect(Rect rect) { rect.bottom = visibleHeight; return visibleHeight > 0; }
                boolean requestRectangleOnScreen(Rect rect, boolean immediate) {
                    check(immediate, "reveal animated instead of following selection directly");
                    requested = rect; reveals++; return true;
                }
            }
            static class ScrollView extends View {
                int scrollY;
                ScrollView(Activity context) { }
                void requestLayout() { layoutRequested = true; }
                void scrollTo(int x, int y) { scrollY = y; }
                protected void onLayout(boolean changed, int l, int t, int r, int b) {
                    layoutRequested = false; scrollY = 0;
                }
            }
            """;

    @Test public void pendingRowLayoutDefersRevealAndRetainsItThroughAnUpdate() throws Exception {
        RuntimeSourceFixture.verify(LAYOUT + """
                public static void verify() {
                    SearchResultScrollView scroll = new SearchResultScrollView(new Activity());
                    View first = new View(); first.layoutRequested = true; first.height = 0;
                    scroll.setSelectedResult(first, true);
                    check(first.reveals == 0, "requested row geometry before layout");
                    check(scroll.shouldKeepSelectionVisible(), "result update lost pending keyboard reveal");
                    View replacement = new View(); replacement.visibleHeight = 0;
                    boolean reveal = scroll.shouldKeepSelectionVisible();
                    scroll.setSelectedResult(replacement, false);
                    scroll.restoreViewport(120, reveal);
                    scroll.onLayout(false, 0, 0, 100, 200);
                    check(scroll.scrollY == 120 && replacement.reveals == 1 && first.reveals == 0,
                            "layout restored or revealed the wrong row");
                    check(replacement.requested.right == 100 && replacement.requested.bottom == 60,
                            "reveal did not use the laid-out row bounds");
                }
                """ + RuntimeSourceFixture.nestedClass("StartMenuContent", "SearchResultScrollView"));
    }

    @Test public void wheelViewportIsRestoredOnceWithoutPullingAnOffscreenSelectionBack() throws Exception {
        RuntimeSourceFixture.verify(LAYOUT + """
                public static void verify() {
                    SearchResultScrollView scroll = new SearchResultScrollView(new Activity());
                    View row = new View(); row.visibleHeight = 0;
                    scroll.setSelectedResult(row, false);
                    check(!scroll.shouldKeepSelectionVisible(), "offscreen selection forced into view");
                    scroll.restoreViewport(180, false);
                    scroll.onLayout(false, 0, 0, 100, 200);
                    check(scroll.scrollY == 180 && row.reveals == 0, "update lost the mouse-scroll viewport");
                    scroll.onLayout(false, 0, 0, 100, 200);
                    check(scroll.scrollY == 0, "later layout replayed a stale scroll restoration");
                    row.visibleHeight = 20;
                    check(!scroll.shouldKeepSelectionVisible(), "partially clipped row treated as fully visible");
                    row.visibleHeight = 60;
                    check(scroll.shouldKeepSelectionVisible(), "visible keyboard selection not retained");
                    scroll.setSelectedResult(row, true);
                    check(row.reveals == 1, "laid-out keyboard selection was not revealed immediately");
                }
                """ + RuntimeSourceFixture.nestedClass("StartMenuContent", "SearchResultScrollView"));
    }
}
