package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.text.TextPaint;
import android.text.style.CharacterStyle;

final class AppearanceTextSpan extends CharacterStyle {
    private final UiColor mRole;
    private final AppearanceScopeSource mSource;
    private int mColor;
    AppearanceTextSpan(Context context, UiColor role) {
        mRole = role; mSource = new AppearanceScopeSource(context);
        refresh(); UiAppearance.registerSpan(this);
    }
    void refresh() { mColor = mSource.current().palette().color(mRole); }
    @Override public void updateDrawState(TextPaint paint) { paint.setColor(mColor); }
}
