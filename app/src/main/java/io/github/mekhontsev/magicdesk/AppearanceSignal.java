package io.github.mekhontsev.magicdesk;

/** Allowlisted, normalized device values. Independent of shaders and Android placement. */
public enum AppearanceSignal {
    CPU_USAGE("system.cpu.usage"),
    MEMORY_USAGE("system.memory.usage"),
    BATTERY_LEVEL("battery.level"),
    BATTERY_CHARGING("battery.charging");

    public final String id;
    AppearanceSignal(String id) { this.id = id; }
    static AppearanceSignal parse(String id) {
        for (var signal : values()) if (signal.id.equals(id)) return signal;
        throw new IllegalArgumentException("Unknown appearance signal: " + id);
    }
}
