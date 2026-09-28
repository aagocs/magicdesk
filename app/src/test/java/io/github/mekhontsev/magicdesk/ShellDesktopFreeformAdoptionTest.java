package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class ShellDesktopFreeformAdoptionTest {
    @Test public void inventoryIncludesHiddenWindowsAndExcludesSystemTasks() throws Exception {
        RuntimeSourceFixture.verify("""
                static class Display { static final int INVALID_DISPLAY = -1; }
                static class Log { static void w(String tag, String message, Throwable error) {} }
                static class FrameworkTaskSnapshot { static final int ACTIVITY_TYPE_STANDARD = 1; }
                static class ComponentName {
                    final String pkg, name;
                    ComponentName(String p, String n) { pkg = p; name = n; }
                    String getPackageName() { return pkg; }
                    String getClassName() { return name; }
                }
                static class DesktopInfrastructureTasks {
                    static boolean isComponent(ComponentName c) {
                        return c != null && c.pkg.equals("magicdesk")
                                && (c.name.equals("Chrome") || c.name.equals("Backstop"));
                    }
                }
                record Task(int id, int display, int mode, int type, ComponentName component) {}
                static class HiddenTaskApi {
                    static int getTaskId(Object t) throws ReflectiveOperationException { return ((Task)t).id(); }
                    static int getTaskDisplayId(Object t) { return ((Task)t).display(); }
                    static int getTaskWindowingMode(Object t) throws ReflectiveOperationException { return ((Task)t).mode(); }
                    static int getTaskActivityType(Object t) throws ReflectiveOperationException { return ((Task)t).type(); }
                    static ComponentName getTaskComponent(Object t) { return ((Task)t).component(); }
                    static ComponentName getTaskTopComponent(Object t) { return getTaskComponent(t); }
                    static String getTaskPackage(Object t) { return getTaskComponent(t).getPackageName(); }
                }
                static Task task(int id, int display, int mode, int type, String pkg, String component) {
                    return new Task(id, display, mode, type, new ComponentName(pkg, component));
                }
                public static void verify() {
                    var ownership = new ShellDesktopTaskOwnership();
                    ownership.configure(4);
                    List<Task> tasks = new ArrayList<>();
                    for (int id = 1; id <= 30; id++) tasks.add(task(id, 4, 5, 1, "app", "Window"));
                    ownership.observeTasks(4, tasks, 0);
                    check(Arrays.equals(java.util.stream.IntStream.rangeClosed(1, 30).toArray(),
                            ownership.desktopTaskIds()), "startup inventory omitted hidden windows");
                    for (Task task : tasks) check(ownership.isDesktopTask(task), "window not owned");
                    ownership.configure(-1);
                    ownership.configure(4);
                    ownership.observeTasks(4, List.of(
                            task(1, 4, 5, 1, "app", "Window"),
                            task(2, 4, 1, 1, "app", "Fullscreen"),
                            task(3, 4, 2, 1, "app", "Pip"),
                            task(4, 0, 5, 1, "app", "Phone"),
                            task(5, 4, 5, 2, "launcher", "Home"),
                            task(6, 4, 5, 3, "launcher", "Recents"),
                            task(7, 4, 5, 1, "com.android.systemui", "Window"),
                            task(8, 4, 5, 1, "magicdesk", "Chrome"),
                            task(9, 4, 5, 1, "magicdesk", "Backstop")), 1);
                    check(Arrays.equals(new int[]{1}, ownership.desktopTaskIds()), "adopted a non-application/window");
                }
                """ + "static " + RuntimeSourceFixture.nestedClass(
                        "ShellDesktopTaskOwnership", "ShellDesktopTaskOwnership"));
    }
}
