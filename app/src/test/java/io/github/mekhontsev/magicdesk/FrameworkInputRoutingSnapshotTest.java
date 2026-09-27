package io.github.mekhontsev.magicdesk;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public final class FrameworkInputRoutingSnapshotTest {
    private static FrameworkInputRoutingSnapshot state(String pointer, String requested) throws IOException {
        return FrameworkInputRoutingSnapshot.parse("Input Manager State:\n" + pointer
                + "\nEvent Hub State:\nInput Manager Service (Java) State:\n"
                + "Unique Id Associations:\nport: magicdesk-mouse uniqueId: wifi:external\n"
                + requested + "\n");
    }

    @Test public void associationDoesNotProveAndroid14PointerReadiness() throws Exception {
        var snapshot = state("PointerController:\nPointer Display ID: 0", "mRequestedPointerDisplayId=0");
        assertEquals("wifi:external", snapshot.uniqueIds.get("magicdesk-mouse"));
        IOException failure = assertThrows(IOException.class,
                () -> FrameworkInputRoutingApi.requirePointerTarget(34, 10, snapshot));
        assertTrue(failure.getMessage().contains("controller=0"));
        assertTrue(failure.getMessage().contains("reconnect"));
    }

    @Test public void matchingGlobalTargetAllowsRouting() throws Exception {
        var snapshot = state("PointerController:\nPointer Display ID: 10", "mRequestedPointerDisplayId=10");
        FrameworkInputRoutingApi.requirePointerTarget(34, 10, snapshot);
        assertThrows(IOException.class, () -> FrameworkInputRoutingApi.requirePointerTarget(34, 11, snapshot));
        assertThrows(IOException.class, () -> FrameworkInputRoutingApi.requirePointerTarget(34, 0, snapshot));
    }

    @Test public void pendingPointerUpdateIsNotReady() throws Exception {
        var snapshot = state("PointerController:\nPointer Display ID: 0", "mRequestedPointerDisplayId=10");
        assertThrows(IOException.class, () -> FrameworkInputRoutingApi.requirePointerTarget(34, 10, snapshot));
    }

    @Test public void firstMouseCanBeRegisteredAfterSystemTargetSelection() throws Exception {
        FrameworkInputRoutingApi.requirePointerTarget(34, 10, state("", "mRequestedPointerDisplayId=10"));
    }

    @Test public void unknownOrMalformedStateIsNotReadiness() throws Exception {
        for (String requested : new String[]{"", "mRequestedPointerDisplayId=unknown", "mRequestedPointerDisplayId=-1"}) {
            var snapshot = state("", requested);
            assertNull(snapshot.requestedPointerDisplayId);
            assertThrows(IOException.class, () -> FrameworkInputRoutingApi.requirePointerTarget(34, 10, snapshot));
        }
        for (String pointer : new String[]{"PointerController:", "Pointer Display ID: invalid", "Pointer Display ID: -1"}) {
            var snapshot = state(pointer, "mRequestedPointerDisplayId=10");
            assertNull(snapshot.pointerDisplayId);
            assertThrows(IOException.class, () -> FrameworkInputRoutingApi.requirePointerTarget(34, 10, snapshot));
        }
    }

    @Test public void newerAndroidUsesPerDeviceRouting() throws Exception {
        for (int sdk : new int[]{35, 36, 37}) {
            assertFalse(FrameworkInputRoutingApi.usesGlobalPointerRouting(sdk));
            FrameworkInputRoutingApi.requirePointerTarget(sdk, 10, state("", ""));
        }
    }
}
