package io.github.mekhontsev.magicdesk;

import android.util.Log;
import android.view.Display;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Workspace membership from explicit launches and observed freeform residency. */
final class ShellDesktopTaskOwnership {
    private static final String TAG = "MagicDeskTasks";
    private static final int WINDOWING_MODE_FREEFORM = 5;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    private final Set<Integer> mDesktopTaskIds = new LinkedHashSet<>();
    private final Map<Integer, Long> mReleaseBarriers = new HashMap<>();

    private int mDesktopDisplayId = Display.INVALID_DISPLAY;
    private int mDesktopHostTaskId = -1;

    synchronized void configure(final int displayId) {
        if (mDesktopDisplayId == displayId) {
            return;
        }
        mDesktopDisplayId = displayId;
        mDesktopHostTaskId = -1;
        mDesktopTaskIds.clear();
        mReleaseBarriers.clear();
    }

    synchronized void markDesktopHost(final int taskId) {
        if (taskId < 0) {
            return;
        }
        mDesktopHostTaskId = taskId;
        markDesktop(taskId);
    }

    synchronized void markDesktop(final int taskId) {
        if (taskId < 0) {
            return;
        }
        mDesktopTaskIds.add(Integer.valueOf(taskId));
        mReleaseBarriers.remove(Integer.valueOf(taskId));
    }

    synchronized void forget(final int taskId) {
        mDesktopTaskIds.remove(Integer.valueOf(taskId));
        mReleaseBarriers.remove(Integer.valueOf(taskId));
        if (mDesktopHostTaskId == taskId) {
            mDesktopHostTaskId = -1;
        }
    }

    synchronized boolean isRememberedDesktopTask(final int taskId) {
        return taskId >= 0
                && mDesktopTaskIds.contains(Integer.valueOf(taskId));
    }

    synchronized boolean isDesktopHostTask(final int taskId) {
        return taskId >= 0 && taskId == mDesktopHostTaskId;
    }

    synchronized int desktopHostTaskId() {
        return mDesktopHostTaskId;
    }

    synchronized int[] desktopTaskIds() {
        final int[] taskIds = new int[mDesktopTaskIds.size()];
        int index = 0;
        for (final Integer taskId : mDesktopTaskIds) {
            taskIds[index++] = taskId.intValue();
        }
        return taskIds;
    }

    synchronized void beginRelease(final int[] taskIds) {
        for (final int taskId : taskIds) {
            mDesktopTaskIds.remove(taskId);
            mReleaseBarriers.put(taskId, Long.MAX_VALUE);
        }
    }

    synchronized void finishRelease(final int[] taskIds, final long nextSampleSequence) {
        for (final int taskId : taskIds) {
            // A failed handoff may already have restored explicit membership.
            if (mReleaseBarriers.containsKey(taskId)) {
                mReleaseBarriers.put(taskId, nextSampleSequence);
            }
        }
    }

    synchronized void onTaskDisplayChanged(final int taskId, final int displayId) {
        if (displayId != mDesktopDisplayId) {
            forget(taskId);
        }
    }

    synchronized void observeTasks(
            final int displayId,
            final List<?> tasks,
            final long sampleSequence) {
        if (mDesktopDisplayId == Display.INVALID_DISPLAY || tasks == null) {
            return;
        }
        for (final Object task : tasks) {
            observeTaskLocked(displayId, task, sampleSequence);
        }
    }

    synchronized boolean isDesktopTask(final Object task) {
        if (!isStandardTask(task)) {
            return false;
        }
        try {
            final int taskId = HiddenTaskApi.getTaskId(task);
            final boolean onWorkspaceDisplay = HiddenTaskApi.getTaskDisplayId(task)
                    == mDesktopDisplayId;
            return isDesktopOwnedTask(
                    onWorkspaceDisplay,
                    mDesktopTaskIds.contains(Integer.valueOf(taskId)));
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "could not inspect desktop task ownership", error);
            return false;
        }
    }

    static boolean isDesktopOwnedTask(
            final boolean onWorkspaceDisplay,
            final boolean rememberedDesktopTask) {
        return onWorkspaceDisplay && rememberedDesktopTask;
    }

    private void observeTaskLocked(
            final int displayId,
            final Object task,
            final long sampleSequence) {
        if (!isStandardTask(task)
                || HiddenTaskApi.getTaskDisplayId(task) != displayId) {
            return;
        }
        try {
            observeStandardTaskState(
                    displayId,
                    HiddenTaskApi.getTaskDisplayId(task),
                    HiddenTaskApi.getTaskId(task),
                    HiddenTaskApi.getTaskWindowingMode(task), sampleSequence);
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "could not classify desktop task", error);
        }
    }

    synchronized void observeStandardTaskState(
            final int displayId,
            final int taskDisplayId,
            final int taskId,
            final int mode,
            final long sampleSequence) {
        if (mDesktopDisplayId == Display.INVALID_DISPLAY
                || taskId < 0 || taskDisplayId != displayId) {
            return;
        }
        final Integer taskKey = Integer.valueOf(taskId);
        if (taskDisplayId != mDesktopDisplayId) {
            forget(taskId);
            return;
        }
        final Long releaseBarrier = mReleaseBarriers.get(taskKey);
        if (releaseBarrier != null) {
            if (sampleSequence < releaseBarrier) return;
            mReleaseBarriers.remove(taskKey);
        }
        if (mode == WINDOWING_MODE_FREEFORM) markDesktop(taskId);
    }

    private boolean isStandardTask(final Object task) {
        if (task == null
                || mDesktopDisplayId == Display.INVALID_DISPLAY
                || SYSTEM_UI_PACKAGE.equals(
                        HiddenTaskApi.getTaskPackage(task))) {
            return false;
        }
        try {
            return HiddenTaskApi.getTaskActivityType(task)
                    == FrameworkTaskSnapshot.ACTIVITY_TYPE_STANDARD
                    && !DesktopInfrastructureTasks.isComponent(
                            HiddenTaskApi.getTaskComponent(task))
                    && !DesktopInfrastructureTasks.isComponent(
                            HiddenTaskApi.getTaskTopComponent(task));
        } catch (ReflectiveOperationException | RuntimeException error) {
            Log.w(TAG, "could not inspect desktop task type", error);
            return false;
        }
    }
}
