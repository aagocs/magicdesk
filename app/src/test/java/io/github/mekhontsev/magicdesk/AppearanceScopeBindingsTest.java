package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class AppearanceScopeBindingsTest {
    @Test public void explicitContextBindingsUnwrapWithoutDisplayOrServiceLookups() throws Exception {
        RuntimeSourceFixture.verify("""
                static class Display {
                    final int id;
                    Display(int id) { this.id = id; }
                    int getDisplayId() { return id; }
                }
                static class Context {
                    Display display;
                    Display getDisplay() { return display; }
                }
                static class ContextWrapper extends Context {
                    Context base;
                    ContextWrapper(Context base) { this.base = base; }
                    Context getBaseContext() { return base; }
                }
                static class WorkspaceAppearance {
                    static String requireScope(String scope) { return Objects.requireNonNull(scope); }
                }
                private record Binding(String scope, int displayId) { }
                static final WeakHashMap<Context, Binding> SCOPES = new WeakHashMap<>();
                """ + RuntimeSourceFixture.methods("AppearanceScopeBindings", "bind", "unbind", "find") + """
                public static void verify() {
                    Context application = new Context();
                    Context desktop = new ContextWrapper(application);
                    Context chrome = new ContextWrapper(application);
                    Context dialog = new ContextWrapper(new ContextWrapper(desktop));
                    check(find(dialog) == null, "unbound context is global");
                    check(bind(desktop, "profile:0/work"), "initial binding");
                    check(!bind(desktop, "profile:0/work"), "unchanged binding");
                    check(find(dialog).equals("profile:0/work"), "dialog unwraps desktop");
                    check(find(chrome) == null && find(application) == null, "no application/display inference");
                    bind(chrome, "profile:0/work");
                    bind(dialog, "profile:10/work");
                    check(find(dialog).equals("profile:10/work"), "nearest explicit scope wins");
                    unbind(dialog);
                    check(find(dialog).equals("profile:0/work"), "unbinding restores inherited scope");
                    bind(desktop, "profile:0/portable");
                    check(find(dialog).equals("profile:0/portable"), "host rebinding propagates");
                    unbind(desktop);
                    check(find(dialog) == null, "closed host falls back to global");
                    check(find(chrome).equals("profile:0/work"), "independent host lifetime");
                    ContextWrapper cycle = new ContextWrapper(null);
                    cycle.base = cycle;
                    check(find(cycle) == null && find(null) == null, "bounded unwrap");
                    ContextWrapper first = new ContextWrapper(cycle), second = new ContextWrapper(first);
                    first.base = second;
                    check(find(first) == null, "multi-wrapper cycle is bounded");
                    Context host = new Context(), tool = new Context(), replacement = new Context();
                    tool.display = new Display(7);
                    bind(host, 7, "workspace:stable-profile");
                    check(find(tool).equals("workspace:stable-profile"), "ordinary tool inherits live binding");
                    bind(tool, "workspace:explicit");
                    check(find(tool).equals("workspace:explicit"), "explicit context outranks display");
                    unbind(tool);
                    unbind(host);
                    check(find(tool) == null, "display hint ends with host");
                    bind(replacement, 7, "workspace:replacement");
                    unbind(host);
                    check(find(tool).equals("workspace:replacement"), "old owner cannot release reused display");
                    boolean rejected = false;
                    try { bind(host, 7, "workspace:different"); }
                    catch (IllegalStateException expected) { rejected = true; }
                    check(rejected && find(tool).equals("workspace:replacement"), "conflicting binding is atomic");
                }
                """);
    }
}
