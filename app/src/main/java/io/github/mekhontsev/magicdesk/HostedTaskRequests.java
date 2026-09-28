package io.github.mekhontsev.magicdesk;

import android.app.Activity;
import android.app.ActivityManager;
import io.github.mekhontsev.magicdesk.hosted.HostedWindowInteraction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Protocol requests borrow task control; they never own Android placement or focus policy. */
final class HostedTaskRequests implements AutoCloseable {
    private final Activity activity;
    private final BooleanSupplier foregroundSession;
    private final Consumer<HostedWindowInteraction.State> confirmation;
    private HostedWindowInteraction.State confirmed;
    private HostedWindowInteraction request;
    private long serial = -1;
    private boolean attention, independentMinimized, minimizePending, submitting, closed;

    HostedTaskRequests(Activity activity, BooleanSupplier foregroundSession,
            Consumer<HostedWindowInteraction.State> confirmation) {
        this.activity = activity; this.foregroundSession = foregroundSession; this.confirmation = confirmation;
    }

    void update(HostedWindowInteraction request) {
        if (closed) return;
        this.request = request;
        observe();
    }

    private void apply(HostedWindowInteraction request, boolean managed) {
        serial = request.serial(); attention = request.attention();
        submitting = false;
        if (request.action() == HostedWindowInteraction.Action.NONE) { observe(); return; }
        if (request.action() == HostedWindowInteraction.Action.ACTIVATE && !foregroundSession.getAsBoolean()) {
            attention = true; observe(); return;
        }
        long command = serial;
        if (managed) {
            submitting = true;
            TaskRepository.ActionCallback done = result -> activity.runOnUiThread(() -> {
                if (closed || serial != command) return;
                submitting = false;
                if (!result.success && request.action() == HostedWindowInteraction.Action.ACTIVATE) attention = true;
                observe();
            });
            if (request.action() == HostedWindowInteraction.Action.MINIMIZE)
                MagicDeskRuntime.concealTask(display(), activity.getTaskId(), done);
            else MagicDeskRuntime.focusDesktopTask(display(), activity.getTaskId(), done);
        } else {
            try {
                if (request.action() == HostedWindowInteraction.Action.MINIMIZE) {
                    // onStop, not acceptance of moveTaskToBack, confirms an independent host's demotion.
                    minimizePending = true;
                    if (!activity.moveTaskToBack(true)) minimizePending = false;
                } else {
                    ActivityManager manager = activity.getSystemService(ActivityManager.class);
                    if (manager != null) for (var task : manager.getAppTasks())
                        if (task.getTaskInfo().taskId == activity.getTaskId()) { task.moveToFront(); break; }
                }
            } catch (RuntimeException error) {
                minimizePending = false;
                if (request.action() == HostedWindowInteraction.Action.ACTIVATE) attention = true;
                android.util.Log.w("MagicDeskGraphics", "Android rejected client task request", error);
            }
            observe();
        }
    }

    void visible(boolean visible) {
        if (visible) { independentMinimized = false; minimizePending = false; }
        else if (minimizePending) { independentMinimized = true; minimizePending = false; }
        observe();
    }
    boolean attention() { return attention && !activity.hasWindowFocus(); }
    void observe() {
        if (closed || request == null) return;
        Boolean managed = managed();
        if (managed == null) return;
        if (serial != request.serial()) { apply(request, managed); return; }
        if (submitting || minimizePending || serial < 0) return;
        boolean minimized = managed
                ? MagicDeskRuntime.isTaskConcealed(display(), activity.getTaskId()) : independentMinimized;
        boolean active = activity.hasWindowFocus() && !minimized;
        if (active) attention = false;
        var state = new HostedWindowInteraction.State(serial, active, minimized, attention());
        if (state.equals(confirmed)) return;
        boolean presentationChanged = confirmed == null || confirmed.attention() != state.attention();
        confirmed = state;
        confirmation.accept(state);
        if (presentationChanged) DesktopRuntimeBridge.refreshTaskPresentations();
    }

    private Boolean managed() {
        int display = display();
        if (!DesktopRuntimeBridge.hasWorkspace(display)) return false;
        // The existing task observer publishes ownership; unknown never means independent.
        var observed = MagicDeskRuntime.observedTaskSnapshot(display);
        if (observed == null || !observed.available) return null;
        boolean present = false;
        for (var task : observed.tasks) if (task.taskId == activity.getTaskId()) { present = true; break; }
        if (!present) return null;
        var owned = MagicDeskRuntime.selectDesktopTaskSnapshot(display, observed);
        if (!owned.available) return null;
        for (var task : owned.tasks) if (task.taskId == activity.getTaskId()) return true;
        return false;
    }
    private int display() { return activity.getDisplay() == null ? 0 : activity.getDisplay().getDisplayId(); }
    @Override public void close() { closed = true; }
}
