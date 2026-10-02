package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DesktopTaskbarSeedingTest {
    private static final AppIdentity MAGICDESK =
            new AppProfile(0, 0).application(BuildConfig.APPLICATION_ID);

    private MemoryStorage mStorage;

    @Before
    public void useMemoryStorage() {
        mStorage = new MemoryStorage();
        DesktopStateStore.useStorageForTests(mStorage);
    }

    @After
    public void restoreStorage() {
        DesktopStateStore.useStorageForTests(null);
    }

    @Test
    public void missingStateIsFreshButStoredStateWithoutMarkerIsUserChoice() throws Exception {
        assertFalse(DesktopStateStore.decode(null).taskbarInitialized);
        assertFalse(DesktopStateStore.decode(" ").taskbarInitialized);
        assertTrue(DesktopStateStore.decode("{\"format\":2,\"taskbar\":[]}").taskbarInitialized);
        assertFalse(DesktopStateStore.decode(
                "{\"format\":2,\"taskbar\":[],\"taskbarInitialized\":false}").taskbarInitialized);
    }

    @Test
    public void markerIsPersistedExplicitly() throws Exception {
        assertTrue(DesktopStateStore.update(state -> state.settings.taskbarAutoHide = true));
        assertFalse(DesktopStateStore.decode(mStorage.encoded).taskbarInitialized);

        DesktopPreferences.initializeTaskbarApps(MAGICDESK);
        assertTrue(DesktopStateStore.decode(mStorage.encoded).taskbarInitialized);
    }

    @Test
    public void freshTaskbarIsSeededOnceAndUnpinningEverythingIsKept() throws Exception {
        DesktopPreferences.initializeTaskbarApps(MAGICDESK);
        assertEquals(DesktopPreferences.defaultTaskbarApps(MAGICDESK), DesktopPreferences.taskbarApps());

        DesktopPreferences.saveTaskbarApps(List.of());
        DesktopPreferences.initializeTaskbarApps(MAGICDESK);
        DesktopStateStore.useStorageForTests(mStorage);
        DesktopPreferences.initializeTaskbarApps(MAGICDESK);

        assertTrue(DesktopPreferences.taskbarApps().isEmpty());
    }

    @Test
    public void savingPinsBeforeTheFirstTaskbarIsAnExplicitChoice() {
        DesktopPreferences.saveTaskbarApps(List.of());
        DesktopPreferences.initializeTaskbarApps(MAGICDESK);

        assertTrue(DesktopPreferences.taskbarApps().isEmpty());
    }

    @Test
    public void existingInstallIsNeverSeeded() {
        mStorage.encoded = "{\"format\":2,\"taskbar\":[]}";
        DesktopStateStore.useStorageForTests(mStorage);

        DesktopPreferences.initializeTaskbarApps(MAGICDESK);

        assertTrue(DesktopPreferences.taskbarApps().isEmpty());
    }

    @Test
    public void uninitializedStateKeepsPinsItAlreadyHas() {
        final AppReference existing =
                new AppProfile(0, 0).reference(AppLaunchTarget.packageDefault("example.application"));
        final DesktopStateStore.State state = new DesktopStateStore.State();
        state.taskbarApps.add(existing);

        DesktopPreferences.initializeTaskbarApps(state, DesktopPreferences.defaultTaskbarApps(MAGICDESK));

        assertEquals(List.of(existing), state.taskbarApps);
        assertTrue(state.taskbarInitialized);
    }

    @Test
    public void invalidStoredStateStartsFresh() {
        // Unreadable state already falls back to defaults; reseeding is the documented result.
        mStorage.encoded = "{invalid";
        DesktopStateStore.useStorageForTests(mStorage);

        DesktopPreferences.initializeTaskbarApps(MAGICDESK);

        assertEquals(DesktopPreferences.defaultTaskbarApps(MAGICDESK), DesktopPreferences.taskbarApps());
    }

    @Test
    public void defaultsArePinnableBuiltInsOnly() {
        final List<AppReference> defaults = DesktopPreferences.defaultTaskbarApps(MAGICDESK);

        assertFalse(defaults.isEmpty());
        for (final AppReference app : defaults) {
            assertNotNull(app.builtIn);
            assertEquals(BuildConfig.APPLICATION_ID, app.application.packageName);
            assertTrue(BuiltInDesktopAppCatalog.isPinnable(app.launchTarget()));
        }
        assertTrue(defaults.stream().anyMatch(
                app -> app.launchTarget().equals(BuiltInDesktopAppCatalog.filesTarget())));
    }

    @Test
    public void onlyTheDesktopTaskbarSeedsPins() throws Exception {
        final Path sources = Path.of("src/main/java/io/github/mekhontsev/magicdesk");
        final String taskbar = Files.readString(sources.resolve("TaskbarController.java"));
        final String create = taskbar.substring(taskbar.indexOf("    void create() {"));
        assertTrue(create.substring(0, create.indexOf("\n    }\n"))
                .contains("DesktopPreferences.initializeTaskbarApps("));
        final String preferences = Files.readString(sources.resolve("DesktopPreferences.java"));
        final String read = preferences.substring(preferences.indexOf("static List<AppReference> taskbarApps()"));
        assertFalse(read.substring(0, read.indexOf("\n    }\n")).contains("initializeTaskbarApps"));
        for (final String independent : new String[] {
                "FullscreenStartController.java", "StartMenuContent.java", "MagicDeskApplication.java"}) {
            assertFalse(independent, Files.readString(sources.resolve(independent))
                    .contains("initializeTaskbarApps"));
        }
    }

    private static final class MemoryStorage implements DesktopStateStore.Storage {
        String encoded = "";

        @Override
        public synchronized String read() {
            return encoded;
        }

        @Override
        public synchronized void write(final String value) {
            encoded = value;
        }
    }
}
