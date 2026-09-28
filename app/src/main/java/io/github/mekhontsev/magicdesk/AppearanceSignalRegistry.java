package io.github.mekhontsev.magicdesk;

import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Demand-owned source sharing. Publication never calls a renderer or retains an Android View. */
final class AppearanceSignalRegistry {
    interface Registration { void close(); }
    interface Provider { Registration start(AppearanceSignal signal, Consumer<Sample> publish); }
    record Sample(float value, boolean available, long validUntilNanos) {
        static final Sample UNKNOWN = new Sample(0, false, Long.MAX_VALUE);
        Sample {
            if (!Float.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Signal outside [0,1]");
        }
        boolean availableAt(long now) { return available && now <= validUntilNanos; }
    }
    private static final class Entry {
        volatile Sample sample = Sample.UNKNOWN;
        boolean active = true;
        int references;
        Registration source;
        synchronized void publish(Sample value) { if (active) sample = value; }
        synchronized void stop() { active = false; sample = Sample.UNKNOWN; }
    }
    final class Lease implements Registration {
        private final Map<AppearanceSignal, Entry> entries;
        private volatile boolean closed;
        private Lease(Map<AppearanceSignal, Entry> entries) { this.entries = entries; }
        Sample sample(AppearanceSignal signal) {
            Entry entry = entries.get(signal);
            return closed || entry == null ? Sample.UNKNOWN : entry.sample;
        }
        @Override public void close() {
            synchronized (AppearanceSignalRegistry.this) {
                if (closed) return;
                closed = true;
                for (var item : entries.entrySet()) release(item.getKey(), item.getValue());
            }
        }
    }
    private final Provider provider;
    private final EnumMap<AppearanceSignal, Entry> active = new EnumMap<>(AppearanceSignal.class);
    AppearanceSignalRegistry(Provider provider) { this.provider = provider; }
    record Observation(AppearanceSignal source, int subscribers, Sample sample) { }
    synchronized List<Observation> snapshot() {
        return active.entrySet().stream().map(item -> new Observation(item.getKey(), item.getValue().references,
                item.getValue().sample)).toList();
    }

    synchronized Lease acquire(Set<AppearanceSignal> signals) {
        var entries = new EnumMap<AppearanceSignal, Entry>(AppearanceSignal.class);
        try {
            for (var signal : signals) {
                Entry entry = active.get(signal);
                if (entry == null) {
                    entry = new Entry();
                    entry.source = provider.start(signal, entry::publish);
                    active.put(signal, entry);
                }
                entry.references++;
                entries.put(signal, entry);
            }
            return new Lease(entries);
        } catch (RuntimeException error) {
            for (var item : entries.entrySet()) release(item.getKey(), item.getValue());
            throw error;
        }
    }
    private void release(AppearanceSignal signal, Entry entry) {
        if (--entry.references != 0) return;
        active.remove(signal);
        entry.stop();
        entry.source.close();
    }
}
