package io.github.mekhontsev.magicdesk;

/** Process-local preview lease. Persisted state is never replaced by an unconfirmed preview. */
public final class AppearanceTransaction {
    public record Snapshot(ShellAppearance current, ShellAppearance committed, String previewId, long revision) { }
    private AppearanceScope<ShellAppearance> mState;
    AppearanceTransaction(ShellAppearance initial) { mState = new AppearanceScope<>(initial); }
    synchronized Snapshot snapshot() {
        return new Snapshot(mState.current(), mState.committed(), mState.previewId(), mState.revision());
    }
    synchronized void apply(ShellAppearance value) {
        mState = mState.apply(value);
    }
    synchronized String preview(ShellAppearance value) {
        mState = mState.preview(value);
        return mState.previewId();
    }
    synchronized ShellAppearance requirePreview(String id) {
        return mState.requirePreview(id);
    }
    synchronized void cancel(String id) {
        mState = mState.cancel(id);
    }
}
