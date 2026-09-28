package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import static io.github.mekhontsev.magicdesk.AppearanceSignal.*;
import java.util.EnumMap;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.Test;

public final class AppearanceSignalRegistryTest {
    static final class Sources implements AppearanceSignalRegistry.Provider {
        final EnumMap<AppearanceSignal, Consumer<AppearanceSignalRegistry.Sample>> sinks = new EnumMap<>(AppearanceSignal.class);
        int starts, stops;
        @Override public AppearanceSignalRegistry.Registration start(AppearanceSignal signal, Consumer<AppearanceSignalRegistry.Sample> sink) {
            starts++; sinks.put(signal, sink);
            return () -> { stops++; sinks.remove(signal); };
        }
        void emit(AppearanceSignal signal, float value, long until) {
            sinks.get(signal).accept(new AppearanceSignalRegistry.Sample(value, true, until));
        }
    }
    @Test public void unusedRegistryDoesNoWorkAndEmptyLeaseOpensNothing() {
        var source = new Sources(); var registry = new AppearanceSignalRegistry(source);
        assertEquals(0, source.starts);
        var empty = registry.acquire(Set.of());
        assertEquals(0, source.starts); empty.close(); assertEquals(0, source.stops);
    }
    @Test public void outputsShareOnlyRequestedSourcesAndLastReleaseStopsThem() {
        var source = new Sources(); var registry = new AppearanceSignalRegistry(source);
        var first = registry.acquire(Set.of(BATTERY_LEVEL));
        assertEquals(Set.of(BATTERY_LEVEL), source.sinks.keySet());
        var second = registry.acquire(Set.of(BATTERY_LEVEL, CPU_USAGE));
        assertEquals(2, source.starts);
        source.emit(BATTERY_LEVEL, .7f, Long.MAX_VALUE);
        assertSame(first.sample(BATTERY_LEVEL), second.sample(BATTERY_LEVEL));
        first.close(); first.close(); assertEquals(0, source.stops);
        assertFalse(first.sample(BATTERY_LEVEL).availableAt(0));
        second.close(); assertEquals(2, source.stops); assertTrue(source.sinks.isEmpty());
    }
    @Test public void staleCallbacksCannotContaminateAReplacementSubscription() {
        var source = new Sources(); var registry = new AppearanceSignalRegistry(source);
        var first = registry.acquire(Set.of(CPU_USAGE)); var stale = source.sinks.get(CPU_USAGE);
        first.close(); var next = registry.acquire(Set.of(CPU_USAGE));
        stale.accept(new AppearanceSignalRegistry.Sample(1, true, Long.MAX_VALUE));
        assertFalse(next.sample(CPU_USAGE).availableAt(0));
        source.emit(CPU_USAGE, 0, 20);
        assertTrue(next.sample(CPU_USAGE).availableAt(20));
        assertFalse(next.sample(CPU_USAGE).availableAt(21));
        assertEquals(0, next.sample(CPU_USAGE).value(), 0); next.close();
    }
    @Test public void partialAcquisitionFailureReleasesAlreadyStartedSources() {
        var source = new Sources();
        var registry = new AppearanceSignalRegistry((signal, publish) -> {
            if (signal == MEMORY_USAGE) throw new IllegalStateException("unavailable");
            return source.start(signal, publish);
        });
        assertThrows(IllegalStateException.class, () -> registry.acquire(java.util.EnumSet.of(CPU_USAGE, MEMORY_USAGE)));
        assertEquals(1, source.starts); assertEquals(1, source.stops);
    }
}
