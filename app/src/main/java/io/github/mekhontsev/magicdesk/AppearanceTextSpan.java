package io.github.mekhontsev.magicdesk;

import android.text.TextPaint;
import android.text.style.CharacterStyle;

final class AppearanceTextSpan extends CharacterStyle {
    private final UiColor mRole;
    AppearanceTextSpan(UiColor role) { mRole = role; }
    @Override public void updateDrawState(TextPaint paint) { paint.setColor(UiAppearance.color(mRole)); }
}
