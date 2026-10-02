package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Android storage and package identity for {@link McpClientRegistry}. */
final class McpClients {
    static final String ACTION_AUTHORIZE =
            "io.github.mekhontsev.magicdesk.action.AUTHORIZE_AUTOMATION_CLIENT";
    static final String EXTRA_PERMISSIONS = "io.github.mekhontsev.magicdesk.extra.PERMISSIONS";
    static final String EXTRA_TOKEN = "io.github.mekhontsev.magicdesk.extra.TOKEN";
    static final String EXTRA_ENDPOINT = "io.github.mekhontsev.magicdesk.extra.ENDPOINT";
    static final String EXTRA_CLIENT_ID = "io.github.mekhontsev.magicdesk.extra.CLIENT_ID";
    static final String EXTRA_ERROR = "io.github.mekhontsev.magicdesk.extra.ERROR";

    private static final String PREFERENCES = "magicdesk_mcp_clients";
    private static final String CLIENTS = "clients";
    private static volatile McpClientRegistry sRegistry;

    private McpClients() {
    }

    static McpClientRegistry get(final Context context) {
        McpClientRegistry registry = sRegistry;
        if (registry != null) return registry;
        synchronized (McpClients.class) {
            if (sRegistry == null) {
                final Context app = context.getApplicationContext();
                final SharedPreferences preferences =
                        app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
                sRegistry = new McpClientRegistry(new McpClientRegistry.Store() {
                    @Override public String read() {
                        return preferences.getString(CLIENTS, "");
                    }

                    @Override public boolean write(final String value) {
                        return preferences.edit().putString(CLIENTS, value).commit();
                    }
                }, packageName -> signingDigest(app.getPackageManager(), packageName),
                        new SecureRandom());
            }
            return sRegistry;
        }
    }

    /** SHA-256 of the package's current APK signing certificates, or null when unavailable. */
    static String signingDigest(final PackageManager manager, final String packageName) {
        if (packageName == null || packageName.isEmpty()) return null;
        try {
            final PackageInfo info = manager.getPackageInfo(packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
            final SigningInfo signing = info.signingInfo;
            if (signing == null) return null;
            final Signature[] signers = signing.getApkContentsSigners();
            if (signers == null || signers.length == 0) return null;
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final List<String> values = new ArrayList<>();
            for (Signature signer : signers) {
                values.add(McpClientRegistry.hex(digest.digest(signer.toByteArray())));
            }
            Collections.sort(values);
            return String.join(":", values);
        } catch (PackageManager.NameNotFoundException | NoSuchAlgorithmException error) {
            return null;
        }
    }
}
