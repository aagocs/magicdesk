package io.github.mekhontsev.magicdesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets the user authorize an on-device app for loopback MCP with its own revocable token.
 * The caller is identified by Android ({@link #getCallingPackage()}), never by request data.
 */
public final class McpClientAuthorizationActivity extends Activity {
    private boolean mFinished;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String caller = getCallingPackage();
        if (caller == null) {
            // Only startActivityForResult() identifies the caller.
            fail("caller_unknown");
            return;
        }
        if (caller.equals(getPackageName())) {
            fail("caller_invalid");
            return;
        }
        if (!MagicDeskMcpPreferences.isEnabled(this)) {
            fail("automation_disabled");
            return;
        }
        final List<McpAccessPolicy.Permission> requested = requested(
                getIntent().getStringArrayExtra(McpClients.EXTRA_PERMISSIONS));
        if (requested == null) {
            fail("permission_unknown");
            return;
        }
        final String digest = McpClients.signingDigest(getPackageManager(), caller);
        if (digest == null) {
            fail("caller_unknown");
            return;
        }
        showConsent(caller, digest, requested);
    }

    private void showConsent(final String caller, final String digest,
            final List<McpAccessPolicy.Permission> requested) {
        final McpAccessPolicy local = MagicDeskMcpPreferences.load(this).localAccess;
        final CharSequence[] labels = new CharSequence[requested.size()];
        final boolean[] checked = new boolean[requested.size()];
        for (int i = 0; i < labels.length; i++) {
            final McpAccessPolicy.Permission permission = requested.get(i);
            final String label = getString(permission.label);
            labels[i] = local.has(permission) ? label
                    : getString(R.string.mcp_client_permission_not_enabled, label);
            checked[i] = true;
        }
        final String shortDigest = digest.length() > 23 ? digest.substring(0, 23) + '…' : digest;
        final String message = getString(R.string.mcp_client_authorize_message,
                caller, shortDigest);
        final AlertDialog.Builder builder = UiDialogs.builder(this)
                .setTitle(getString(R.string.mcp_client_authorize_title, label(caller)))
                .setNegativeButton(R.string.mcp_client_deny, (dialog, which) -> fail("denied"))
                .setPositiveButton(R.string.mcp_client_allow, (dialog, which) -> {
                    final Set<String> granted = new HashSet<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) granted.add(requested.get(i).id);
                    }
                    approve(caller, digest, granted);
                })
                .setOnCancelListener(dialog -> fail("denied"));
        if (labels.length == 0) {
            builder.setMessage(message + "\n\n" + getString(R.string.mcp_client_observe_only));
        } else {
            // Same layout as the MCP permission settings: grants, then the explanation.
            final android.widget.TextView explanation = new android.widget.TextView(this);
            explanation.setText(message);
            final int padding = Math.round(16 * getResources().getDisplayMetrics().density);
            explanation.setPadding(padding, padding, padding, padding);
            builder.setView(explanation)
                    .setMultiChoiceItems(labels, checked,
                            (dialog, index, selected) -> checked[index] = selected);
        }
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(shown -> dialog.getWindow().getDecorView()
                // Reject taps while another window covers the consent dialog.
                .setFilterTouchesWhenObscured(true));
        dialog.show();
    }

    private void approve(final String caller, final String digest, final Set<String> granted) {
        final McpClientRegistry.Issued issued;
        try {
            issued = McpClients.get(this).authorize(caller, digest, granted,
                    System.currentTimeMillis());
        } catch (IllegalStateException error) {
            fail("registry_full");
            return;
        }
        DesktopAutomationEventJournal.record("mcp", "authorize_client", true, caller);
        final Intent result = new Intent()
                .putExtra(McpClients.EXTRA_TOKEN, issued.token)
                .putExtra(McpClients.EXTRA_ENDPOINT,
                        MagicDeskMcpPreferences.load(this).endpoint())
                .putExtra(McpClients.EXTRA_CLIENT_ID, issued.client.id)
                .putExtra(McpClients.EXTRA_PERMISSIONS,
                        issued.client.permissions.toArray(new String[0]));
        finishWith(RESULT_OK, result);
    }

    private void fail(final String reason) {
        finishWith(RESULT_CANCELED, new Intent().putExtra(McpClients.EXTRA_ERROR, reason));
    }

    private void finishWith(final int code, final Intent data) {
        if (mFinished) return;
        mFinished = true;
        setResult(code, data);
        finish();
    }

    private CharSequence label(final String packageName) {
        try {
            final PackageManager manager = getPackageManager();
            final ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return manager.getApplicationLabel(info);
        } catch (PackageManager.NameNotFoundException error) {
            return packageName;
        }
    }

    /** Requested permissions in catalog order, or null when a name is unknown. */
    static List<McpAccessPolicy.Permission> requested(final String[] names) {
        final Set<String> wanted = new HashSet<>();
        if (names != null) {
            for (String name : names) wanted.add(name);
        }
        final List<McpAccessPolicy.Permission> result = new ArrayList<>();
        for (McpAccessPolicy.Permission permission : McpAccessPolicy.Permission.values()) {
            if (wanted.remove(permission.id)) result.add(permission);
        }
        return wanted.isEmpty() ? result : null;
    }
}
