package io.github.mekhontsev.magicdesk;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Per-output interpolation. Only immutable samples and preallocated uniform arrays enter a frame. */
final class ShaderSignalBindings {
    private final ShaderWallpaper.SignalUniform[] uniforms;
    private final float[][] values;
    private final Set<AppearanceSignal> requested;
    private final Function<Set<AppearanceSignal>, AppearanceSignalRegistry.Lease> subscribe;
    private AppearanceSignalRegistry.Lease lease;
    private long previousNanos;
    private boolean first = true;

    ShaderSignalBindings(List<ShaderWallpaper.SignalUniform> uniforms,
            Function<Set<AppearanceSignal>, AppearanceSignalRegistry.Lease> subscribe) {
        if (uniforms.isEmpty()) throw new IllegalArgumentException("No shader bindings");
        this.uniforms = uniforms.toArray(ShaderWallpaper.SignalUniform[]::new);
        this.subscribe = subscribe;
        values = new float[uniforms.size()][2];
        var requested = EnumSet.noneOf(AppearanceSignal.class);
        for (int i = 0; i < this.uniforms.length; i++) {
            requested.add(this.uniforms[i].source());
            values[i][0] = this.uniforms[i].fallback();
        }
        this.requested = Set.copyOf(requested);
    }
    void start() {
        if (lease == null) { lease = subscribe.apply(requested); first = true; }
    }
    void close() {
        if (lease != null) { lease.close(); lease = null; }
        for (int i = 0; i < uniforms.length; i++) { values[i][0] = uniforms[i].fallback(); values[i][1] = 0; }
    }
    int size() { return uniforms.length; }
    String name(int index) { return uniforms[index].name(); }
    float[] value(int index) { return values[index]; }
    void update(long now) {
        double elapsedMillis = first ? 0 : Math.max(0, now - previousNanos) / 1_000_000.0;
        previousNanos = now; first = false;
        for (int i = 0; i < uniforms.length; i++) {
            var binding = uniforms[i];
            var sample = lease == null ? AppearanceSignalRegistry.Sample.UNKNOWN : lease.sample(binding.source());
            boolean available = sample.availableAt(now);
            float target = available ? sample.value() : binding.fallback();
            float[] value = values[i];
            // Missing or expired data switches immediately to the declared fallback, never a stale measurement.
            if (!available || binding.smoothingMillis() == 0) value[0] = target;
            else value[0] += (target - value[0]) * (float) -Math.expm1(-elapsedMillis / binding.smoothingMillis());
            value[1] = available ? 1 : 0;
        }
    }
}
