package io.github.mekhontsev.magicdesk;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import java.util.EnumMap;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import static io.github.mekhontsev.magicdesk.AppearanceSignalRegistry.Sample;

/** Lazy Android sources. No startup hook, task observer, command execution or privilege acquisition. */
final class AppearanceSignalSources implements AppearanceSignalRegistry.Provider {
    private static AppearanceSignalRegistry shared;
    static synchronized java.util.List<AppearanceSignalRegistry.Observation> snapshot() {
        return shared == null ? java.util.List.of() : shared.snapshot();
    }
    static synchronized AppearanceSignalRegistry.Lease acquire(Context context, Set<AppearanceSignal> signals) {
        if (signals.isEmpty()) throw new IllegalArgumentException("No appearance signals requested");
        if (shared == null) shared = new AppearanceSignalRegistry(new AppearanceSignalSources(context.getApplicationContext()));
        return shared.acquire(signals);
    }

    private final Context context;
    private ScheduledThreadPoolExecutor worker;
    private int pollers;
    private final EnumMap<AppearanceSignal, Consumer<Sample>> batterySinks = new EnumMap<>(AppearanceSignal.class);
    private BroadcastReceiver batteryReceiver;
    private Sample batteryLevel = Sample.UNKNOWN, batteryCharging = Sample.UNKNOWN;

    private AppearanceSignalSources(Context context) { this.context = context; }

    @Override public AppearanceSignalRegistry.Registration start(AppearanceSignal signal, Consumer<Sample> publish) {
        return switch (signal) {
            case CPU_USAGE -> new CpuSource(publish);
            case MEMORY_USAGE -> {
                var manager = context.getSystemService(ActivityManager.class);
                var info = new ActivityManager.MemoryInfo();
                yield poll(() -> {
                    manager.getMemoryInfo(info);
                    return memoryUsage(info.totalMem, info.availMem);
                }, publish);
            }
            case BATTERY_LEVEL, BATTERY_CHARGING -> battery(signal, publish);
        };
    }

    static float memoryUsage(long total, long available) {
        return total <= 0 || available < 0 || available > total ? -1 : 1 - (float) available / total;
    }
    static float batteryLevel(int level, int scale, boolean present) {
        return !present || scale <= 0 || level < 0 || level > scale ? -1 : (float) level / scale;
    }
    static float batteryCharging(int status, boolean present) {
        if (!present) return -1;
        return switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING -> 1;
            case BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING,
                    BatteryManager.BATTERY_STATUS_FULL -> 0;
            default -> -1;
        };
    }
    private static Sample sample(float value, long validUntil) {
        return value < 0 || !Float.isFinite(value) ? Sample.UNKNOWN : new Sample(value, true, validUntil);
    }

    private final class CpuSource implements AppearanceSignalRegistry.Registration, ShellAccess.StateListener {
        private final Consumer<Sample> publish;
        private AppearanceSignalRegistry.Registration polling;
        private boolean closed;
        CpuSource(Consumer<Sample> publish) { this.publish = publish; ShellAccess.addStateListener(this); }
        @Override public synchronized void onShellStateChanged(ShellAccess.Snapshot snapshot) {
            if (closed) return;
            if (snapshot.isReady()) {
                if (polling != null) return;
                var usage = new SystemCpuUsage();
                polling = poll(() -> {
                    SystemCpuSnapshot counters;
                    try { counters = ShellAccess.readSystemCpuSnapshot(); }
                    catch (Exception error) { counters = SystemCpuSnapshot.UNKNOWN; }
                    return usage.sample(counters);
                }, publish);
            } else {
                if (polling != null) { polling.close(); polling = null; }
                publish.accept(Sample.UNKNOWN);
            }
        }
        @Override public synchronized void close() {
            if (closed) return;
            closed = true; ShellAccess.removeStateListener(this);
            if (polling != null) { polling.close(); polling = null; }
        }
    }

    private synchronized AppearanceSignalRegistry.Registration poll(Supplier<Float> read, Consumer<Sample> publish) {
        if (worker == null) {
            worker = new ScheduledThreadPoolExecutor(1, r -> new Thread(r, "MagicDeskAppearanceSignals"));
            worker.setRemoveOnCancelPolicy(true);
        }
        var polling = new Poll(read, publish);
        pollers++;
        // PERIODIC_SAMPLE: requested CPU/RAM telemetry, not a state-settling wait. No catch-up bursts.
        polling.future = worker.scheduleWithFixedDelay(polling, 0, 1, TimeUnit.SECONDS);
        return polling;
    }
    private final class Poll implements Runnable, AppearanceSignalRegistry.Registration {
        private final Supplier<Float> read;
        private final Consumer<Sample> publish;
        private ScheduledFuture<?> future;
        private boolean closed;
        Poll(Supplier<Float> read, Consumer<Sample> publish) { this.read = read; this.publish = publish; }
        @Override public void run() {
            synchronized (this) { if (closed) return; }
            Sample value;
            try { value = sample(read.get(), System.nanoTime() + TimeUnit.SECONDS.toNanos(3)); }
            catch (RuntimeException error) { value = Sample.UNKNOWN; }
            synchronized (this) { if (!closed) publish.accept(value); }
        }
        @Override public void close() {
            synchronized (this) { if (closed) return; closed = true; }
            synchronized (AppearanceSignalSources.this) {
                future.cancel(false);
                if (--pollers == 0) { worker.shutdown(); worker = null; }
            }
        }
    }

    private synchronized AppearanceSignalRegistry.Registration battery(AppearanceSignal signal, Consumer<Sample> publish) {
        batterySinks.put(signal, publish);
        if (batteryReceiver == null) {
            var receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    synchronized (AppearanceSignalSources.this) {
                        if (batteryReceiver == this) updateBattery(intent);
                    }
                }
            };
            batteryReceiver = receiver;
            try {
                Intent sticky = context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED);
                updateBattery(sticky);
            } catch (RuntimeException error) {
                batteryReceiver = null;
                batteryLevel = batteryCharging = Sample.UNKNOWN;
            }
        }
        publish.accept(signal == AppearanceSignal.BATTERY_LEVEL ? batteryLevel : batteryCharging);
        return () -> {
            synchronized (AppearanceSignalSources.this) {
                batterySinks.remove(signal);
                if (batterySinks.isEmpty()) {
                    if (batteryReceiver != null) context.unregisterReceiver(batteryReceiver);
                    batteryReceiver = null; batteryLevel = batteryCharging = Sample.UNKNOWN;
                }
            }
        };
    }
    private void updateBattery(Intent intent) {
        boolean present = intent != null && intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false);
        batteryLevel = sample(intent == null ? -1 : batteryLevel(intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1), present), Long.MAX_VALUE);
        batteryCharging = sample(intent == null ? -1 : batteryCharging(intent.getIntExtra(BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN), present), Long.MAX_VALUE);
        var level = batterySinks.get(AppearanceSignal.BATTERY_LEVEL);
        var charging = batterySinks.get(AppearanceSignal.BATTERY_CHARGING);
        if (level != null) level.accept(batteryLevel);
        if (charging != null) charging.accept(batteryCharging);
    }
}
