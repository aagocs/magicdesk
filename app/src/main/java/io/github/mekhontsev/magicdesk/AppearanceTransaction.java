package io.github.mekhontsev.magicdesk;

import java.util.Objects;

/** Process-local preview lease. Persisted state is never replaced by an unconfirmed preview. */
final class AppearanceTransaction {
    record Snapshot(ShellAppearance current, ShellAppearance committed, String previewId, long revision) { }
    private ShellAppearance mCommitted, mPreview;
    private String mPreviewId;
    private long mRevision;
    AppearanceTransaction(ShellAppearance initial) { mCommitted = Objects.requireNonNull(initial); }
    synchronized Snapshot snapshot() {
        return new Snapshot(mPreview == null ? mCommitted : mPreview, mCommitted, mPreviewId, mRevision);
    }
    synchronized void apply(ShellAppearance value) {
        mCommitted = Objects.requireNonNull(value);
        mPreview = null; mPreviewId = null; mRevision++;
    }
    synchronized String preview(ShellAppearance value) {
        if (mPreviewId != null) throw new IllegalStateException("An appearance preview is already active; confirm or cancel it first");
        mPreview = Objects.requireNonNull(value);
        mPreviewId = java.util.UUID.randomUUID().toString(); mRevision++;
        return mPreviewId;
    }
    synchronized ShellAppearance requirePreview(String id) {
        if (id == null || !id.equals(mPreviewId)) throw new IllegalArgumentException("Appearance preview is no longer active");
        return mPreview;
    }
    synchronized void cancel(String id) {
        requirePreview(id); mPreview = null; mPreviewId = null; mRevision++;
    }
}
