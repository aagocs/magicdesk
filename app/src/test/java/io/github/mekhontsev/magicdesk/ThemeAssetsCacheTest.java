package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import org.junit.Test;

public final class ThemeAssetsCacheTest {
    @Test public void reusesIdentityAndEvictsLeastRecentlyUsedByBytes() {
        var cache = new ThemeAssetsCache<Object>(3, 10);
        Object a = new Object(), b = new Object(), c = new Object();
        cache.put("a", a, 4); cache.put("b", b, 4);
        assertSame(a, cache.get("a"));
        cache.put("c", c, 4);
        assertNull(cache.get("b"));
        assertSame(a, cache.get("a")); assertSame(c, cache.get("c"));
        assertEquals(8, cache.bytes());
    }
    @Test public void countBudgetReplacementAndOversizedAssetsAreBounded() {
        var cache = new ThemeAssetsCache<String>(2, 10);
        cache.put("a", "old", 3); cache.put("a", "new", 2);
        assertEquals(2, cache.bytes()); assertEquals(1, cache.size());
        cache.put("b", "b", 1); cache.put("c", "c", 1);
        assertNull(cache.get("a")); assertEquals(2, cache.size());
        cache.put("huge", "not retained", 11);
        assertNull(cache.get("huge")); assertEquals(2, cache.bytes());
        assertThrows(IllegalArgumentException.class, () -> cache.put("bad", "bad", -1));
    }
    @Test public void clearDropsReuseWithoutInvalidatingBorrowedValues() {
        var cache = new ThemeAssetsCache<StringBuilder>(1, 8);
        StringBuilder value = new StringBuilder("held");
        cache.put("a", value, 4);
        StringBuilder borrowed = cache.get("a");
        cache.clear();
        assertEquals(0, cache.size()); assertEquals(0, cache.bytes());
        assertEquals("held", borrowed.toString());
    }
}
