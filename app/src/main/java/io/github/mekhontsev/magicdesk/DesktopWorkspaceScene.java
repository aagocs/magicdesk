package io.github.mekhontsev.magicdesk;

import java.util.List;

/** Visible workspace content, independent of task ownership and input focus. */
enum DesktopWorkspaceScene {
    HOME,
    FREEFORM,
    FULLSCREEN,
    UNKNOWN;

    static DesktopWorkspaceScene resolve(final List<TaskRepository.TaskEntry> tasks) {
        return resolve(tasks, -1);
    }

    static DesktopWorkspaceScene resolve(
            final List<TaskRepository.TaskEntry> tasks, final int excludedTaskId) {
        if (tasks == null) {
            return UNKNOWN;
        }
        // Tasks are top-first. PiP and infrastructure do not define the scene
        // beneath them; a fullscreen plane can cover nominally visible windows.
        for (final TaskRepository.TaskEntry task : tasks) {
            if (task == null || task.taskId == excludedTaskId || !task.visible
                    || DesktopInfrastructureTasks.isTask(task)) {
                continue;
            }
            if (DesktopTaskController.isDesktopHostTask(task)) {
                return HOME;
            }
            if (task.isFreeform()) {
                return FREEFORM;
            }
            if (task.isFullscreen()) {
                return FULLSCREEN;
            }
        }
        return UNKNOWN;
    }
}
