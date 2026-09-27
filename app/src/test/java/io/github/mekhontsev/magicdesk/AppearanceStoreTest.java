package io.github.mekhontsev.magicdesk;

import org.junit.Test;

public final class AppearanceStoreTest {
    @Test public void preparationAndPublicationAreAtomicAndNeverHoldTheStoreLockForDecoding() throws Exception {
        RuntimeSourceFixture.verify("""
                static class Context {}
                static class JSONException extends Exception {}
                record ShellResources(String name) { String bundle() { return name; } }
                record ShellAppearance(ShellResources resources) {}
                static class Looper {
                    static final Object MAIN = new Object();
                    static Object myLooper() { return null; }
                    static Object getMainLooper() { return MAIN; }
                }
                static class ThemeAssets {
                    record Prepared(String name) { static final Prepared EMPTY = new Prepared("empty"); }
                    static Set<String> retained;
                    static ThemeAssets get(Context ignored) { return new ThemeAssets(); }
                    ThemeAssets store() { return this; }
                    Set<String> prune(Set<String> references) throws IOException {
                        check(!Thread.holdsLock(AppearanceStore.class), "prune holds publication lock");
                        retained = Set.copyOf(references);
                        return Set.of("unused");
                    }
                }
                static class WorkspaceAppearance {
                    final String name;
                    WorkspaceAppearance(String name) { this.name = name; }
                    WorkspaceAppearance apply(ShellAppearance value) { return new WorkspaceAppearance(value.resources().name()); }
                    WorkspaceAppearance apply(String scope, String patch) { return new WorkspaceAppearance(patch); }
                    String savedGlobal() { return name; }
                    String savedOverrides() { return "overrides:" + name; }
                    Set<ShellResources> resources() { return Set.of(new ShellResources(name)); }
                }
                static class SharedPreferences {
                    int writes;
                    Map<String, String> saved = Map.of();
                    Editor edit() { return new Editor(); }
                    class Editor {
                        Map<String, String> next = new HashMap<>();
                        Editor putString(String key, String value) { next.put(key, value); return this; }
                        void apply() { writes++; saved = Map.copyOf(next); }
                    }
                }
                static class MagicDeskApplication { static Context applicationContext() { return null; } }
                static class WorkspaceAppearanceAssets {
                    static boolean fail, cachedOnly;
                    static Runnable during;
                    static Map<ShellResources, ThemeAssets.Prepared> prepare(Context context, Collection<ShellResources> resources,
                            Map<ShellResources, ThemeAssets.Prepared> active, Map<ShellResources, ThemeAssets.Prepared> staged) throws IOException {
                        check(!Thread.holdsLock(AppearanceStore.class), "asset decoding holds publication lock");
                        if (during != null) { Runnable action = during; during = null; action.run(); }
                        if (fail) throw new IOException("invalid asset");
                        Map<ShellResources, ThemeAssets.Prepared> prepared = new HashMap<>();
                        for (ShellResources resource : resources) {
                            ThemeAssets.Prepared asset = active.get(resource);
                            if (asset == null) asset = staged.get(resource);
                            if (asset == null && cachedOnly) throw new IllegalStateException("requires worker preparation");
                            prepared.put(resource, asset == null ? new ThemeAssets.Prepared(resource.name()) : asset);
                        }
                        return Map.copyOf(prepared);
                    }
                }
                static class AppearanceStore {
                    private static volatile Resolved sResolved = new Resolved(new WorkspaceAppearance("initial"), Map.of());
                    private static Map<ShellResources, ThemeAssets.Prepared> sStaged = Map.of();
                    private static WorkspaceAppearance sLoading;
                    private static boolean sPruning;
                    private static long sAssetGeneration;
                    private static SharedPreferences sPreferences = new SharedPreferences();
                    static int notifications;
                    static void initialize(Context ignored) { throw new AssertionError("already initialized"); }
                    static void changed() { notifications++; }
                    @FunctionalInterface private interface Mutation { WorkspaceAppearance apply(WorkspaceAppearance state) throws JSONException; }
                """ + RuntimeSourceFixture.nestedClass("AppearanceStore", "Resolved")
                        + RuntimeSourceFixture.methods("AppearanceStore", "change", "prepareAssets", "pruneUnusedBundles") + """
                }
                static void prune() {
                    try { AppearanceStore.pruneUnusedBundles(); }
                    catch (IOException error) { throw new AssertionError(error); }
                }
                public static void verify() throws Exception {
                    var initial = AppearanceStore.sResolved;
                    WorkspaceAppearanceAssets.fail = true;
                    boolean failed = false;
                    try { AppearanceStore.change(state -> new WorkspaceAppearance("bad"), true); }
                    catch (IllegalArgumentException expected) { failed = true; }
                    check(failed && AppearanceStore.sResolved == initial, "failed preparation published a theme");
                    check(AppearanceStore.sPreferences.writes == 0 && AppearanceStore.notifications == 0, "failed preparation escaped to preferences/listeners");
                    WorkspaceAppearanceAssets.fail = false;
                    AppearanceStore.prepareAssets(new ShellAppearance(new ShellResources("preview")));
                    check(AppearanceStore.sResolved == initial && AppearanceStore.sPreferences.writes == 0, "staging must not publish or persist");
                    WorkspaceAppearanceAssets.cachedOnly = true;
                    AppearanceStore.change(state -> new WorkspaceAppearance("preview"), false);
                    check(AppearanceStore.sResolved.state().name.equals("preview"), "preview not published");
                    check(AppearanceStore.sResolved.assets().get(new ShellResources("preview")).name().equals("preview"), "theme and prepared assets differ");
                    check(AppearanceStore.sPreferences.writes == 0 && AppearanceStore.sStaged.isEmpty(), "preview persisted or left staged resources retained");
                    AppearanceStore.change(state -> state, true);
                    check(AppearanceStore.sPreferences.saved.get("document").equals("preview"), "commit missing global preferences");
                    check(AppearanceStore.sPreferences.saved.get("overrides").equals("overrides:preview"), "commit missing scope preferences");
                    WorkspaceAppearanceAssets.cachedOnly = false;
                    WorkspaceAppearanceAssets.during = () -> AppearanceStore.change(state -> new WorkspaceAppearance("winner"), true);
                    failed = false;
                    try { AppearanceStore.change(state -> new WorkspaceAppearance("stale"), true); }
                    catch (IllegalStateException expected) { failed = true; }
                    check(failed && AppearanceStore.sResolved.state().name.equals("winner"), "late preparation overwrote a concurrent update");
                    check(AppearanceStore.sPreferences.saved.get("document").equals("winner"), "late preparation overwrote persisted state");
                    check(AppearanceStore.sPreferences.writes == 2, "rejected update persisted");
                    WorkspaceAppearanceAssets.during = () -> AppearanceStore.change(state -> new WorkspaceAppearance("newer"), true);
                    failed = false;
                    try { AppearanceStore.prepareAssets(new ShellAppearance(new ShellResources("late-stage"))); }
                    catch (IllegalStateException expected) { failed = true; }
                    check(failed && AppearanceStore.sStaged.isEmpty(), "stale staged assets were retained");
                    check(AppearanceStore.sResolved.state().name.equals("newer"), "stale stage changed current appearance");
                    var beforePrune = AppearanceStore.sResolved;
                    int writesBeforePrune = AppearanceStore.sPreferences.writes;
                    int notificationsBeforePrune = AppearanceStore.notifications;
                    long generation = AppearanceStore.sAssetGeneration;
                    WorkspaceAppearanceAssets.during = Fixture::prune;
                    failed = false;
                    try { AppearanceStore.change(state -> new WorkspaceAppearance("pruned-in-flight"), true); }
                    catch (IllegalStateException expected) { failed = true; }
                    check(failed && !AppearanceStore.sPruning, "publication accepted decoding spanning completed cleanup");
                    check(AppearanceStore.sResolved == beforePrune, "cleanup should invalidate by generation, not replace current state");
                    check(AppearanceStore.sAssetGeneration == generation + 1, "cleanup generation did not advance");
                    check(AppearanceStore.sPreferences.writes == writesBeforePrune, "pruned digest was persisted");
                    check(AppearanceStore.notifications == notificationsBeforePrune, "rejected publication notified listeners");
                    check(ThemeAssets.retained.equals(Set.of("newer")), "cleanup retained an unpublished digest");
                    WorkspaceAppearanceAssets.during = Fixture::prune;
                    failed = false;
                    try { AppearanceStore.prepareAssets(new ShellAppearance(new ShellResources("pruned-stage"))); }
                    catch (IllegalStateException expected) { failed = true; }
                    check(failed && AppearanceStore.sStaged.isEmpty(), "decoding spanning cleanup was staged for later publication");
                    AppearanceStore.prepareAssets(new ShellAppearance(new ShellResources("retained-stage")));
                    prune();
                    check(ThemeAssets.retained.containsAll(Set.of("newer", "retained-stage")), "cleanup removed staged or live references");
                    AppearanceStore.sLoading = new WorkspaceAppearance("startup-pending");
                    generation = AppearanceStore.sAssetGeneration;
                    failed = false;
                    try { AppearanceStore.pruneUnusedBundles(); }
                    catch (IllegalStateException expected) { failed = true; }
                    check(failed && generation == AppearanceStore.sAssetGeneration, "cleanup raced initialization preparation");
                    AppearanceStore.sLoading = null;
                    AppearanceStore.sPruning = true;
                    failed = false;
                    try { AppearanceStore.change(state -> new WorkspaceAppearance("during-cleanup"), true); }
                    catch (IllegalStateException expected) { failed = true; }
                    check(failed && AppearanceStore.sResolved.state().name.equals("newer"), "cleanup gate allowed a mutation");
                    failed = false;
                    try { AppearanceStore.prepareAssets(new ShellAppearance(new ShellResources("during-cleanup"))); }
                    catch (IllegalStateException expected) { failed = true; }
                    check(failed, "cleanup gate allowed new asset preparation");
                }
                """);
    }
}
