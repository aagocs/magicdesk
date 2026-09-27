package io.github.mekhontsev.magicdesk;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

/** Pure copy-on-write state. Cached resolutions keep JSON and validation off View/draw paths. */
public final class WorkspaceAppearance {
    public static final int MAX_SCOPES = 32;
    public static final int MAX_SCOPE_BYTES = 512;
    public static final int MAX_OVERRIDE_BYTES = 256 * 1024;

    public record Snapshot(String scope, ShellAppearance current, ShellAppearance committed,
            String patch, String committedPatch, String previewId, long revision) { }

    private final AppearanceScope<ShellAppearance> mGlobal;
    private final Map<String, AppearanceScope<WorkspaceAppearancePatch>> mScopes;
    private final Map<String, Snapshot> mResolved;
    private final long mRevision;
    private final String mSavedGlobal, mSavedOverrides;
    private final Set<ShellResources> mResources;

    private WorkspaceAppearance(AppearanceScope<ShellAppearance> global,
            Map<String, AppearanceScope<WorkspaceAppearancePatch>> scopes, long revision) throws JSONException {
        if (scopes.size() > MAX_SCOPES) throw new IllegalArgumentException("Too many appearance scopes (maximum " + MAX_SCOPES + ")");
        mGlobal = global; mScopes = Map.copyOf(scopes); mRevision = revision;
        mSavedGlobal = ShellAppearanceJson.encode(global.committed()).toString();
        ShellAppearanceJson.parse(mSavedGlobal);
        ShellAppearanceJson.parse(ShellAppearanceJson.encode(global.current()).toString());
        JSONObject saved = new JSONObject(), previews = new JSONObject();
        Map<String, Snapshot> resolved = new LinkedHashMap<>();
        Set<ShellResources> resources = new LinkedHashSet<>();
        resources.add(global.current().resources()); resources.add(global.committed().resources());
        for (var item : scopes.entrySet()) {
            String key = requireScope(item.getKey());
            var state = item.getValue();
            var current = state.current().resolve(global.current());
            var committed = state.committed().resolve(global.committed());
            // Both cancellation orders must remain valid while global and local previews coexist.
            resources.add(state.committed().resolve(global.current()).resources());
            resources.add(state.current().resolve(global.committed()).resources());
            resources.add(current.resources()); resources.add(committed.resources());
            resolved.put(key, new Snapshot(key, current, committed, state.current().json(),
                    state.committed().json(), state.previewId(), revision));
            if (!state.committed().isEmpty()) saved.put(key, new JSONObject(state.committed().json()));
            if (state.previewId() != null) previews.put(key, new JSONObject(state.preview().json()));
        }
        mSavedOverrides = saved.toString();
        if (WorkspaceAppearancePatch.bytes(mSavedOverrides) + WorkspaceAppearancePatch.bytes(previews.toString())
                > MAX_OVERRIDE_BYTES) throw new IllegalArgumentException("Appearance overrides exceed 256 KiB");
        mResolved = Map.copyOf(resolved);
        for (ShellResources resource : resources) {
            if (resource.hasBundleAssets() && resource.bundle().isEmpty()) {
                throw ShellAppearanceSchema.invalid("/resources/bundle", "asset references require an installed bundle digest");
            }
        }
        mResources = Set.copyOf(resources);
    }

    static WorkspaceAppearance defaults() {
        try { return new WorkspaceAppearance(new AppearanceScope<>(ShellAppearance.defaults()), Map.of(), 0); }
        catch (JSONException error) { throw new IllegalStateException(error); }
    }

    static WorkspaceAppearance restore(String global, String overrides) throws JSONException {
        var values = WorkspaceAppearancePatch.object(overrides, MAX_OVERRIDE_BYTES);
        if (values.length() > MAX_SCOPES) throw new IllegalArgumentException("Too many saved appearance scopes");
        Map<String, AppearanceScope<WorkspaceAppearancePatch>> scopes = new LinkedHashMap<>();
        for (var keys = values.keys(); keys.hasNext();) {
            String key = requireScope(keys.next());
            var patch = WorkspaceAppearancePatch.parse(values.getJSONObject(key).toString());
            if (!patch.isEmpty()) scopes.put(key, new AppearanceScope<>(patch));
        }
        return new WorkspaceAppearance(new AppearanceScope<>(ShellAppearanceJson.parse(global)), scopes, 0);
    }

    public static String requireScope(String scope) {
        if (scope == null || scope.isBlank() || !scope.equals(scope.strip())
                || WorkspaceAppearancePatch.bytes(scope) > MAX_SCOPE_BYTES) {
            throw new IllegalArgumentException("Expected a stable workspace identity of 1-512 UTF-8 bytes");
        }
        for (int i = 0; i < scope.length(); i++) {
            char c = scope.charAt(i);
            if (Character.isISOControl(c) || Character.isLowSurrogate(c)
                    || (Character.isHighSurrogate(c) && (i + 1 == scope.length() || !Character.isLowSurrogate(scope.charAt(++i))))) {
                throw new IllegalArgumentException("Invalid workspace identity");
            }
        }
        return scope;
    }

    ShellAppearance current() { return mGlobal.current(); }
    ShellAppearance current(String scope) {
        Snapshot value = mResolved.get(scope);
        return value == null ? mGlobal.current() : value.current();
    }
    AppearanceTransaction.Snapshot snapshot() {
        return new AppearanceTransaction.Snapshot(mGlobal.current(), mGlobal.committed(), mGlobal.previewId(), mGlobal.revision());
    }
    Snapshot snapshot(String scope) {
        requireScope(scope);
        Snapshot value = mResolved.get(scope);
        return value != null ? value : new Snapshot(scope, mGlobal.current(), mGlobal.committed(), "{}", "{}", null, mRevision);
    }
    List<String> listScopes() { return mScopes.keySet().stream().sorted().toList(); }
    String savedGlobal() { return mSavedGlobal; }
    String savedOverrides() { return mSavedOverrides; }
    Set<ShellResources> resources() { return mResources; }

    WorkspaceAppearance apply(ShellAppearance value) throws JSONException {
        return new WorkspaceAppearance(mGlobal.apply(value), mScopes, mRevision + 1);
    }
    WorkspaceAppearance preview(ShellAppearance value) throws JSONException {
        return new WorkspaceAppearance(mGlobal.preview(value), mScopes, mRevision + 1);
    }
    WorkspaceAppearance preview(ShellAppearance value, long expectedRevision) throws JSONException {
        requireRevision(mGlobal.revision(), expectedRevision);
        return preview(value);
    }
    WorkspaceAppearance confirm(String id) throws JSONException { return apply(mGlobal.requirePreview(id)); }
    WorkspaceAppearance cancel(String id) throws JSONException {
        return new WorkspaceAppearance(mGlobal.cancel(id), mScopes, mRevision + 1);
    }
    WorkspaceAppearance apply(String scope, String patch) throws JSONException {
        return replace(scope, state(scope).apply(WorkspaceAppearancePatch.parse(patch)));
    }
    WorkspaceAppearance preview(String scope, String patch) throws JSONException {
        return replace(scope, state(scope).preview(WorkspaceAppearancePatch.parse(patch)));
    }
    WorkspaceAppearance preview(String scope, String patch, long expectedRevision) throws JSONException {
        requireScope(scope);
        requireRevision(mRevision, expectedRevision);
        return preview(scope, patch);
    }
    WorkspaceAppearance confirm(String scope, String id) throws JSONException {
        var state = state(scope);
        return replace(scope, state.apply(state.requirePreview(id)));
    }
    WorkspaceAppearance cancel(String scope, String id) throws JSONException {
        return replace(scope, state(scope).cancel(id));
    }
    WorkspaceAppearance removeOverride(String scope) throws JSONException {
        return replace(scope, new AppearanceScope<>(WorkspaceAppearancePatch.EMPTY));
    }
    private static void requireRevision(long actual, long expected) {
        if (actual != expected) throw new IllegalStateException("Appearance changed since this edit started; reload and retry");
    }
    private AppearanceScope<WorkspaceAppearancePatch> state(String scope) {
        return mScopes.getOrDefault(requireScope(scope), new AppearanceScope<>(WorkspaceAppearancePatch.EMPTY));
    }
    private WorkspaceAppearance replace(String scope, AppearanceScope<WorkspaceAppearancePatch> value) throws JSONException {
        requireScope(scope);
        var scopes = new LinkedHashMap<>(mScopes);
        if (value.committed().isEmpty() && value.previewId() == null) scopes.remove(scope);
        else scopes.put(scope, value);
        return new WorkspaceAppearance(mGlobal, scopes, mRevision + 1);
    }
}
