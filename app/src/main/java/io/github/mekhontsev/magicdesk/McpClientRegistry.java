package io.github.mekhontsev.magicdesk;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * On-device MCP clients authorized by the user. Each client owns one revocable token bound to its
 * package and signing certificate; only a hash of the token is stored.
 */
final class McpClientRegistry {
    static final int MAX_CLIENTS = 16;

    /** Private persistence of the encoded registry. */
    interface Store {
        String read();

        boolean write(String value);
    }

    /** Current signing digest of an installed package, or null when it is not installed. */
    interface Identity {
        String signingDigest(String packageName);
    }

    static final class Client {
        final String id;
        final String packageName;
        final String signingDigest;
        final Set<String> permissions;
        final long createdAtMillis;
        private final String mTokenHash;

        Client(String id, String packageName, String signingDigest, Set<String> permissions,
                long createdAtMillis, String tokenHash) {
            this.id = id;
            this.packageName = packageName;
            this.signingDigest = signingDigest;
            this.permissions = new McpAccessPolicy(permissions).names();
            this.createdAtMillis = createdAtMillis;
            mTokenHash = tokenHash;
        }

        McpAccessPolicy access() {
            return new McpAccessPolicy(permissions);
        }
    }

    static final class Issued {
        final Client client;
        final String token;

        Issued(Client client, String token) {
            this.client = client;
            this.token = token;
        }
    }

    private final Store mStore;
    private final Identity mIdentity;
    private final SecureRandom mRandom;

    McpClientRegistry(Store store, Identity identity, SecureRandom random) {
        if (store == null || identity == null || random == null) {
            throw new IllegalArgumentException("MCP client registry dependencies are required");
        }
        mStore = store;
        mIdentity = identity;
        mRandom = random;
    }

    /** Issues a new token for the package, replacing any earlier authorization it held. */
    synchronized Issued authorize(String packageName, String signingDigest,
            Set<String> permissions, long nowMillis) {
        if (packageName == null || packageName.isBlank()
                || signingDigest == null || signingDigest.isBlank()) {
            throw new IllegalArgumentException("Client package and signing identity are required");
        }
        final List<Client> clients = load();
        clients.removeIf(client -> client.packageName.equals(packageName));
        if (clients.size() >= MAX_CLIENTS) {
            throw new IllegalStateException("Too many authorized automation apps; revoke one first");
        }
        final String token = randomToken(32);
        final Client client = new Client(randomToken(12), packageName, signingDigest,
                permissions == null ? Set.of() : permissions, nowMillis, hash(token));
        clients.add(client);
        if (!save(clients)) throw new IllegalStateException("Authorization could not be saved");
        return new Issued(client, token);
    }

    /** Returns the client owning this token while its package keeps the authorized signature. */
    synchronized Client resolve(String token) {
        if (token == null || token.isEmpty()) return null;
        final byte[] presented = hash(token).getBytes(StandardCharsets.US_ASCII);
        Client match = null;
        for (Client client : load()) {
            // Compare every entry so the response time does not reveal which one matched.
            if (MessageDigest.isEqual(presented,
                    client.mTokenHash.getBytes(StandardCharsets.US_ASCII))) {
                match = client;
            }
        }
        if (match == null) return null;
        return match.signingDigest.equals(mIdentity.signingDigest(match.packageName)) ? match : null;
    }

    synchronized Client find(String id) {
        for (Client client : load()) {
            if (client.id.equals(id)) return client;
        }
        return null;
    }

    synchronized List<Client> clients() {
        return List.copyOf(load());
    }

    synchronized boolean revoke(String id) {
        final List<Client> clients = load();
        return clients.removeIf(client -> client.id.equals(id)) && save(clients);
    }

    private List<Client> load() {
        final List<Client> clients = new ArrayList<>();
        final String encoded = mStore.read();
        if (encoded == null || encoded.isEmpty()) return clients;
        try {
            final JSONArray values = new JSONObject(encoded).getJSONArray("clients");
            for (int i = 0; i < values.length() && clients.size() < MAX_CLIENTS; i++) {
                final JSONObject value = values.getJSONObject(i);
                final Set<String> permissions = new HashSet<>();
                final JSONArray granted = value.getJSONArray("permissions");
                for (int p = 0; p < granted.length(); p++) permissions.add(granted.getString(p));
                clients.add(new Client(value.getString("id"), value.getString("package"),
                        value.getString("signingDigest"), permissions,
                        value.getLong("createdAtMillis"), value.getString("tokenHash")));
            }
        } catch (JSONException error) {
            // Unreadable state authorizes nobody; the user authorizes clients again.
            clients.clear();
        }
        return clients;
    }

    private boolean save(List<Client> clients) {
        try {
            final JSONArray values = new JSONArray();
            for (Client client : clients) {
                final JSONArray permissions = new JSONArray();
                for (String permission : client.permissions) permissions.put(permission);
                values.put(new JSONObject().put("id", client.id)
                        .put("package", client.packageName)
                        .put("signingDigest", client.signingDigest)
                        .put("permissions", permissions)
                        .put("createdAtMillis", client.createdAtMillis)
                        .put("tokenHash", client.mTokenHash));
            }
            return mStore.write(new JSONObject().put("clients", values).toString());
        } catch (JSONException error) {
            return false;
        }
    }

    private String randomToken(int bytes) {
        final byte[] value = new byte[bytes];
        mRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    static String hash(String value) {
        try {
            return hex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    static String hex(byte[] bytes) {
        final StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(String.format(java.util.Locale.ROOT, "%02x", value));
        return text.toString();
    }
}
