package io.github.mekhontsev.magicdesk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class McpClientRegistryTest {
    private final Map<String, String> mInstalled = new HashMap<>();
    private String mStored = "";
    private final McpClientRegistry mRegistry = new McpClientRegistry(new McpClientRegistry.Store() {
        @Override public String read() {
            return mStored;
        }

        @Override public boolean write(String value) {
            mStored = value;
            return true;
        }
    }, mInstalled::get, new SecureRandom());

    @Test public void issuedTokenResolvesToItsClientAndIsNeverStored() {
        mInstalled.put("app.one", "digest-1");
        final var issued = mRegistry.authorize("app.one", "digest-1",
                Set.of("control", "files_read"), 10L);
        assertTrue(issued.token.length() >= 43);
        assertFalse(mStored.contains(issued.token));
        final var client = mRegistry.resolve(issued.token);
        assertNotNull(client);
        assertEquals("app.one", client.packageName);
        assertEquals(Set.of("control", "files_read"), client.permissions);
        assertNull(mRegistry.resolve(issued.token + "x"));
        assertNull(mRegistry.resolve(""));
        assertNull(mRegistry.resolve(null));
    }

    @Test public void reauthorizingAPackageReplacesItsPreviousToken() {
        mInstalled.put("app.one", "digest-1");
        final var first = mRegistry.authorize("app.one", "digest-1", Set.of(), 1L);
        final var second = mRegistry.authorize("app.one", "digest-1", Set.of("control"), 2L);
        assertNull(mRegistry.resolve(first.token));
        assertNotNull(mRegistry.resolve(second.token));
        assertEquals(1, mRegistry.clients().size());
    }

    @Test public void revokingOneClientLeavesTheOthers() {
        mInstalled.put("app.one", "digest-1");
        mInstalled.put("app.two", "digest-2");
        final var one = mRegistry.authorize("app.one", "digest-1", Set.of(), 1L);
        final var two = mRegistry.authorize("app.two", "digest-2", Set.of(), 1L);
        assertTrue(mRegistry.revoke(one.client.id));
        assertNull(mRegistry.resolve(one.token));
        assertNotNull(mRegistry.resolve(two.token));
        assertFalse(mRegistry.revoke(one.client.id));
    }

    @Test public void aChangedSignatureOrUninstalledPackageNoLongerAuthenticates() {
        mInstalled.put("app.one", "digest-1");
        final var issued = mRegistry.authorize("app.one", "digest-1", Set.of(), 1L);
        mInstalled.put("app.one", "other-signer");
        assertNull(mRegistry.resolve(issued.token));
        mInstalled.remove("app.one");
        assertNull(mRegistry.resolve(issued.token));
        mInstalled.put("app.one", "digest-1");
        assertNotNull(mRegistry.resolve(issued.token));
    }

    @Test public void unknownPermissionNamesAreNotGranted() {
        mInstalled.put("app.one", "digest-1");
        final var issued = mRegistry.authorize("app.one", "digest-1",
                Set.of("control", "root", "observe"), 1L);
        assertEquals(Set.of("control"), mRegistry.resolve(issued.token).permissions);
    }

    @Test public void registryIsBoundedAndRejectsMissingIdentity() {
        for (int i = 0; i < McpClientRegistry.MAX_CLIENTS; i++) {
            mRegistry.authorize("app." + i, "digest", Set.of(), i);
        }
        assertThrows(IllegalStateException.class,
                () -> mRegistry.authorize("app.extra", "digest", Set.of(), 0L));
        // Re-authorizing an existing package still works when the registry is full.
        assertNotNull(mRegistry.authorize("app.0", "digest", Set.of(), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> mRegistry.authorize("app.x", "", Set.of(), 0L));
        assertThrows(IllegalArgumentException.class,
                () -> mRegistry.authorize(" ", "digest", Set.of(), 0L));
    }

    @Test public void unreadableStateAuthorizesNobody() {
        mInstalled.put("app.one", "digest-1");
        final var issued = mRegistry.authorize("app.one", "digest-1", Set.of(), 1L);
        mStored = "{not json";
        assertNull(mRegistry.resolve(issued.token));
        assertEquals(List.of(), mRegistry.clients());
    }

    @Test public void clientGrantsNeverExceedTheListener() {
        final var listener = new McpAccessPolicy(Set.of("control", "shell"));
        final var client = new McpAccessPolicy(Set.of("control", "files_read"));
        final var effective = listener.intersect(client);
        assertTrue(effective.has(McpAccessPolicy.Permission.CONTROL));
        assertFalse(effective.has(McpAccessPolicy.Permission.SHELL));
        assertFalse(effective.has(McpAccessPolicy.Permission.FILES_READ));
        assertTrue(effective.allows("get_state"));
    }

    @Test public void requestedPermissionsKeepCatalogOrderAndRejectUnknownNames() {
        final var requested = McpClientAuthorizationActivity.requested(
                new String[] {"files_read", "control"});
        assertEquals(List.of(McpAccessPolicy.Permission.CONTROL,
                McpAccessPolicy.Permission.FILES_READ), requested);
        assertEquals(List.of(), McpClientAuthorizationActivity.requested(null));
        assertNull(McpClientAuthorizationActivity.requested(new String[] {"control", "root"}));
    }
}
