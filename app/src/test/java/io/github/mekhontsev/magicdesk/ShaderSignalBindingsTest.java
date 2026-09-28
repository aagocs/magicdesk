package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.*;
import static io.github.mekhontsev.magicdesk.AppearanceSignal.*;
import java.util.List;
import org.junit.Test;

public final class ShaderSignalBindingsTest {
    @Test public void bindingsStartWithPlaybackAndFramesReuseUniformArrays() {
        var sources = new AppearanceSignalRegistryTest.Sources(); var registry = new AppearanceSignalRegistry(sources);
        var bindings = new ShaderSignalBindings(List.of(
                new ShaderWallpaper.SignalUniform("load", CPU_USAGE, .25f, 0),
                new ShaderWallpaper.SignalUniform("other", CPU_USAGE, .5f, 0)), registry::acquire);
        assertEquals(0, sources.starts); assertEquals(2, bindings.size());
        assertEquals("load", bindings.name(0));
        float[] first = bindings.value(0), second = bindings.value(1);
        bindings.update(0); assertArrayEquals(new float[] {.25f, 0}, first, 0);
        bindings.start(); bindings.start(); assertEquals(1, sources.starts);
        sources.emit(CPU_USAGE, .8f, 100);
        bindings.update(50); assertArrayEquals(new float[] {.8f, 1}, first, 0);
        assertSame(first, bindings.value(0)); assertSame(second, bindings.value(1));
        bindings.update(101); assertArrayEquals(new float[] {.25f, 0}, first, 0);
        assertArrayEquals(new float[] {.5f, 0}, second, 0);
        bindings.close(); bindings.close(); assertEquals(1, sources.stops);
        bindings.start(); assertEquals(2, sources.starts);
        bindings.update(200); assertArrayEquals(new float[] {.25f, 0}, first, 0); bindings.close();
    }
    @Test public void interpolationUsesElapsedTimeAndMissingDataResetsToFallback() {
        var sources = new AppearanceSignalRegistryTest.Sources(); var registry = new AppearanceSignalRegistry(sources);
        var bindings = new ShaderSignalBindings(List.of(new ShaderWallpaper.SignalUniform("load", CPU_USAGE, 0, 1000)), registry::acquire);
        bindings.start(); sources.emit(CPU_USAGE, 1, Long.MAX_VALUE);
        bindings.update(1_000_000_000L); bindings.update(2_000_000_000L);
        assertEquals(1 - Math.exp(-1), bindings.value(0)[0], .00001);
        assertEquals(1, bindings.value(0)[1], 0);
        sources.sinks.get(CPU_USAGE).accept(AppearanceSignalRegistry.Sample.UNKNOWN);
        bindings.update(2_010_000_000L); assertArrayEquals(new float[] {0, 0}, bindings.value(0), 0);
        bindings.close();
    }
    @Test public void outputsHaveIndependentInterpolationButSharedMeasurements() {
        var sources = new AppearanceSignalRegistryTest.Sources(); var registry = new AppearanceSignalRegistry(sources);
        var uniforms = List.of(new ShaderWallpaper.SignalUniform("load", CPU_USAGE, 0, 500));
        var first = new ShaderSignalBindings(uniforms, registry::acquire);
        var second = new ShaderSignalBindings(uniforms, registry::acquire);
        first.start(); second.start(); assertEquals(1, sources.starts);
        sources.emit(CPU_USAGE, 1, Long.MAX_VALUE);
        first.update(0); first.update(500_000_000L); second.update(0);
        assertTrue(first.value(0)[0] > second.value(0)[0]);
        first.close(); assertEquals(0, sources.stops);
        second.update(500_000_000L); assertTrue(second.value(0)[0] > .5f);
        second.close(); assertEquals(1, sources.stops);
    }
}
