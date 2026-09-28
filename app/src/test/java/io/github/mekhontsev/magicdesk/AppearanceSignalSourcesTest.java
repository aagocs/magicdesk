package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class AppearanceSignalSourcesTest {
    @Test public void publicSourcesNeedNoShellAndEveryResourceIsDemandOwned() throws Exception {
        RuntimeSourceFixture.verify("io.github.mekhontsev.magicdesk", """
            static class BatteryManager {
                static final String EXTRA_PRESENT="present", EXTRA_LEVEL="level", EXTRA_SCALE="scale", EXTRA_STATUS="status";
                static final int BATTERY_STATUS_UNKNOWN=1, BATTERY_STATUS_CHARGING=2, BATTERY_STATUS_DISCHARGING=3,
                    BATTERY_STATUS_NOT_CHARGING=4, BATTERY_STATUS_FULL=5;
            }
            static class Intent {
                static final String ACTION_BATTERY_CHANGED="battery";
                int level=75, scale=100, status=2; boolean present=true;
                boolean getBooleanExtra(String name, boolean fallback) { return present; }
                int getIntExtra(String name, int fallback) { return switch(name) { case "level" -> level; case "scale" -> scale; case "status" -> status; default -> fallback; }; }
            }
            record IntentFilter(String action) {}
            abstract static class BroadcastReceiver { abstract void onReceive(Context context, Intent intent); }
            static class ActivityManager {
                static class MemoryInfo { long totalMem=1000, availMem=400; }
                int reads; void getMemoryInfo(MemoryInfo info) { reads++; }
            }
            static class Context {
                static final int RECEIVER_NOT_EXPORTED=4;
                final ActivityManager memory = new ActivityManager();
                int registrations, unregistrations; BroadcastReceiver receiver;
                Context getApplicationContext() { return this; }
                <T> T getSystemService(Class<T> type) { return type.cast(memory); }
                Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter, int flags) {
                    this.receiver=receiver; registrations++; return new Intent();
                }
                void unregisterReceiver(BroadcastReceiver receiver) { check(this.receiver==receiver,"wrong receiver"); this.receiver=null; unregistrations++; }
            }
            static class ScheduledFuture<T> {
                final Runnable work; boolean cancelled;
                ScheduledFuture(Runnable work) { this.work=work; }
                void cancel(boolean interrupt) { cancelled=true; }
                void run() { if(!cancelled) work.run(); }
            }
            static class ScheduledThreadPoolExecutor {
                static final List<ScheduledThreadPoolExecutor> all = new ArrayList<>();
                final List<ScheduledFuture<?>> tasks = new ArrayList<>(); boolean closed;
                ScheduledThreadPoolExecutor(int n, ThreadFactory factory) { all.add(this); }
                void setRemoveOnCancelPolicy(boolean value) {}
                ScheduledFuture<?> scheduleWithFixedDelay(Runnable work, long initial, long delay, TimeUnit unit) {
                    check(initial==0 && delay==1 && unit==TimeUnit.SECONDS,"wrong sampling cadence");
                    var task=new ScheduledFuture<>(work); tasks.add(task); return task;
                }
                void shutdown() { closed=true; }
                void tick() { List.copyOf(tasks).forEach(ScheduledFuture::run); }
            }
            record SystemCpuSnapshot(long total,long idle) {
                static final SystemCpuSnapshot UNKNOWN=new SystemCpuSnapshot(-1,-1);
                boolean available() { return total>=0 && idle>=0 && idle<=total; }
            }
            static class ShellAccess {
                interface StateListener { void onShellStateChanged(Snapshot snapshot); }
                record Snapshot(boolean ready) { boolean isReady() { return ready; } }
                static final List<StateListener> listeners=new ArrayList<>();
                static boolean ready; static int reads;
                static void addStateListener(StateListener listener) { listeners.add(listener); listener.onShellStateChanged(new Snapshot(ready)); }
                static void removeStateListener(StateListener listener) { listeners.remove(listener); }
                static void ready(boolean value) { ready=value; List.copyOf(listeners).forEach(l->l.onShellStateChanged(new Snapshot(value))); }
                static SystemCpuSnapshot readSystemCpuSnapshot() { reads++; return new SystemCpuSnapshot(reads*100,reads*50); }
            }
            """ + RuntimeSourceFixture.nestedClass("SystemCpuUsage", "SystemCpuUsage").replace("final class SystemCpuUsage", "static final class SystemCpuUsage")
                + RuntimeSourceFixture.nestedClass("AppearanceSignalSources", "AppearanceSignalSources")
                    .replace("final class AppearanceSignalSources", "static final class AppearanceSignalSources")
                    .replace("Consumer<", "java.util.function.Consumer<").replace("Supplier<", "java.util.function.Supplier<")
                    .replaceAll("\\bSample\\b", "AppearanceSignalRegistry.Sample") + """
            public static void verify() {
                var context=new Context();
                check(AppearanceSignalSources.snapshot().isEmpty() && AppearanceSignalSources.shared==null,"diagnostic initialized sources");
                var level=AppearanceSignalSources.acquire(context,Set.of(AppearanceSignal.BATTERY_LEVEL));
                var both=AppearanceSignalSources.acquire(context,Set.of(AppearanceSignal.BATTERY_LEVEL,AppearanceSignal.BATTERY_CHARGING));
                check(context.registrations==1 && ScheduledThreadPoolExecutor.all.isEmpty() && ShellAccess.listeners.isEmpty(),"battery started polling or shell");
                check(level.sample(AppearanceSignal.BATTERY_LEVEL).value()==.75f,"sticky level lost");
                var retained=context.receiver;
                var full=new Intent(); full.status=5; retained.onReceive(context,full);
                check(both.sample(AppearanceSignal.BATTERY_CHARGING).value()==0,"full is not charging");
                full.status=1; retained.onReceive(context,full);
                check(!both.sample(AppearanceSignal.BATTERY_CHARGING).available(),"unknown charging became false");
                level.close(); check(context.unregistrations==0,"shared receiver stopped early");
                both.close(); check(context.unregistrations==1,"receiver leaked");
                var memory=AppearanceSignalSources.acquire(context,Set.of(AppearanceSignal.MEMORY_USAGE));
                var cpu=AppearanceSignalSources.acquire(context,Set.of(AppearanceSignal.CPU_USAGE));
                var worker=ScheduledThreadPoolExecutor.all.get(0); worker.tick();
                check(context.memory.reads==1 && ShellAccess.reads==0 && worker.tasks.size()==1,"unavailable CPU polled");
                check(Math.abs(memory.sample(AppearanceSignal.MEMORY_USAGE).value()-.6f)<.0001f,"RAM sample missing");
                ShellAccess.ready(true); worker.tick();
                check(!cpu.sample(AppearanceSignal.CPU_USAGE).available(),"CPU baseline reported as idle");
                worker.tick(); check(cpu.sample(AppearanceSignal.CPU_USAGE).value()==.5f,"CPU rate missing");
                check(ScheduledThreadPoolExecutor.all.size()==1,"separate workers for sources");
                ShellAccess.ready(false); worker.tick();
                check(ShellAccess.reads==2 && !cpu.sample(AppearanceSignal.CPU_USAGE).available(),"CPU did not stop on access loss");
                ShellAccess.ready(true); worker.tick();
                check(!cpu.sample(AppearanceSignal.CPU_USAGE).available(),"reconnected CPU reused baseline");
                cpu.close(); check(!worker.closed && ShellAccess.listeners.isEmpty(),"memory worker lost or shell listener leaked");
                memory.close(); check(worker.closed && AppearanceSignalSources.snapshot().isEmpty(),"last source did not stop");
                int memoryReads=context.memory.reads, cpuReads=ShellAccess.reads;
                worker.tick(); retained.onReceive(context,new Intent());
                check(context.memory.reads==memoryReads && ShellAccess.reads==cpuReads,"work after release");
                check(AppearanceSignalSources.memoryUsage(0,0)<0 && AppearanceSignalSources.memoryUsage(100,101)<0,"invalid RAM admitted");
                check(AppearanceSignalSources.batteryLevel(110,100,true)<0 && AppearanceSignalSources.batteryLevel(50,100,false)<0,"invalid battery admitted");
            }
            """, "AppearanceSignal", "AppearanceSignalRegistry");
    }
}
