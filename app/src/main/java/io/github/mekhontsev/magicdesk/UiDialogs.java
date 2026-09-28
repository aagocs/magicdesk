package io.github.mekhontsev.magicdesk;

import android.app.Activity;
import android.app.AlertDialog;

/** Native modal creation. Callers retain their show, dismiss and button callbacks. */
final class UiDialogs {
    private UiDialogs() {}

    static AlertDialog.Builder builder(Activity owner) {
        return new Builder(owner, false);
    }

    static AlertDialog.Builder themedBuilder(Activity owner) {
        return new Builder(owner, true);
    }

    private static final class Builder extends AlertDialog.Builder {
        private final Activity owner;
        private final boolean themed;

        Builder(Activity owner, boolean themed) {
            super(owner);
            this.owner = owner;
            this.themed = themed;
        }

        @Override public AlertDialog create() {
            AlertDialog dialog = super.create();
            dialog.setOwnerActivity(owner);
            dialog.create();
            if (themed) UiAppearance.dialog(dialog);
            DialogContentInsets.bind(dialog.findViewById(android.R.id.content), owner);
            return dialog;
        }
    }
}
