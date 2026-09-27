package io.github.mekhontsev.magicdesk;

import java.util.Objects;
import java.util.UUID;

/** Immutable preview lease shared by global values and sparse workspace patches. */
record AppearanceScope<T>(T committed, T preview, String previewId, long revision) {
    AppearanceScope(T initial) { this(Objects.requireNonNull(initial), null, null, 0); }
    T current() { return previewId == null ? committed : preview; }
    AppearanceScope<T> apply(T value) {
        return new AppearanceScope<>(Objects.requireNonNull(value), null, null, revision + 1);
    }
    AppearanceScope<T> preview(T value) {
        if (previewId != null) {
            throw new IllegalStateException("An appearance preview is already active; confirm or cancel it first");
        }
        return new AppearanceScope<>(committed, Objects.requireNonNull(value), UUID.randomUUID().toString(), revision + 1);
    }
    T requirePreview(String id) {
        if (id == null || !id.equals(previewId)) throw new IllegalArgumentException("Appearance preview is no longer active");
        return preview;
    }
    AppearanceScope<T> cancel(String id) {
        requirePreview(id);
        return new AppearanceScope<>(committed, null, null, revision + 1);
    }
}
