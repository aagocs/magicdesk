package io.github.mekhontsev.magicdesk;

import java.util.LinkedHashMap;

/** Bounded strong reuse, not resource ownership. Eviction must not invalidate a prepared snapshot. */
final class ThemeAssetsCache<T> {
    private record Item<T>(T value, long bytes) {}
    private final LinkedHashMap<String, Item<T>> items = new LinkedHashMap<>(16, 0.75f, true);
    private final int maximumCount;
    private final long maximumBytes;
    private long bytes;

    ThemeAssetsCache(int maximumCount, long maximumBytes) {
        if (maximumCount < 1 || maximumBytes < 1) throw new IllegalArgumentException("Invalid asset cache budget");
        this.maximumCount = maximumCount;
        this.maximumBytes = maximumBytes;
    }

    synchronized T get(String key) {
        Item<T> item = items.get(key);
        return item == null ? null : item.value();
    }

    synchronized void put(String key, T value, long size) {
        if (size < 0) throw new IllegalArgumentException("Negative asset size");
        Item<T> previous = items.remove(key);
        if (previous != null) bytes -= previous.bytes();
        if (size > maximumBytes) return;
        while (!items.isEmpty() && (items.size() >= maximumCount || size > maximumBytes - bytes)) {
            var iterator = items.entrySet().iterator();
            bytes -= iterator.next().getValue().bytes();
            iterator.remove();
        }
        items.put(key, new Item<>(value, size));
        bytes += size;
    }

    synchronized void clear() { items.clear(); bytes = 0; }
    synchronized int size() { return items.size(); }
    synchronized long bytes() { return bytes; }
}
