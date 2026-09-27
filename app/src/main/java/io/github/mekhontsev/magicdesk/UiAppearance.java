package io.github.mekhontsev.magicdesk;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.WeakHashMap;

/** Live native View styling. Weak registrations retain neither Activities nor detached tools. */
public final class UiAppearance {
    private enum Property { TEXT, HINT, BACKGROUND, BACKGROUND_TINT, IMAGE, COMPOUND, BUTTON, PROGRESS }
    @FunctionalInterface private interface Style { void apply(View view, ShellAppearance theme); }
    private static final WeakHashMap<Binding, Boolean> BINDINGS = new WeakHashMap<>();
    private static final WeakHashMap<Paint, Boolean> PAINTS = new WeakHashMap<>();
    private static final WeakHashMap<UiFeedbackDrawable, Boolean> FEEDBACK = new WeakHashMap<>();
    private static final WeakHashMap<UiSymbolDrawable, Boolean> SYMBOLS = new WeakHashMap<>();
    private UiAppearance() {}

    public static int color(UiColor role) { return AppearanceStore.current().palette().color(role); }
    static android.graphics.drawable.Drawable symbol(android.content.Context context, int resource, UiColor role) {
        final var drawable = new UiSymbolDrawable(context.getResources(), resource, role);
        synchronized (SYMBOLS) { SYMBOLS.put(drawable, true); }
        return drawable;
    }
    public static void text(TextView view, UiColor role) {
        bind(view, Property.TEXT, (v, t) -> ((TextView) v).setTextColor(t.palette().color(role)));
    }
    public static void textStates(TextView view, UiColor enabled) {
        bind(view, Property.TEXT, (v, t) -> ((TextView) v).setTextColor(states(t, enabled)));
    }
    public static void hint(TextView view, UiColor role) {
        bind(view, Property.HINT, (v, t) -> ((TextView) v).setHintTextColor(t.palette().color(role)));
    }
    public static void background(View view, UiColor role) {
        bind(view, Property.BACKGROUND, (v, t) -> v.setBackgroundColor(t.palette().color(role)));
    }
    public static void backgroundTint(View view, UiColor role) {
        bind(view, Property.BACKGROUND_TINT, (v, t) -> v.setBackgroundTintList(states(t, role)));
    }
    public static void image(ImageView view, UiColor role) {
        bind(view, Property.IMAGE, (v, t) -> ((ImageView) v).setColorFilter(t.palette().color(role)));
    }
    static void icon(ImageView view, int resource, UiColor role) {
        view.setImageDrawable(symbol(view.getContext(), resource, role));
        image(view, role);
    }
    public static void imageStates(ImageView view, UiColor role) {
        bind(view, Property.IMAGE, (v, t) -> ((ImageView) v).setImageTintList(states(t, role)));
    }
    public static void compound(TextView view, UiColor role) {
        bind(view, Property.COMPOUND, (v, t) -> ((TextView) v).setCompoundDrawableTintList(states(t, role)));
    }
    public static void button(android.widget.CompoundButton view, UiColor role) {
        bind(view, Property.BUTTON, (v, t) -> {
            final var tint = new ColorStateList(new int[][] {new int[] {android.R.attr.state_checked}, new int[0]},
                    new int[] {t.palette().color(role), t.palette().color(UiColor.MUTED)});
            if (v instanceof android.widget.Switch toggle) {
                toggle.setThumbTintList(tint);
                toggle.setTrackTintList(tint.withAlpha(90));
            } else ((android.widget.CompoundButton) v).setButtonTintList(tint);
        });
    }
    public static void progress(android.widget.ProgressBar view, UiColor role) {
        bind(view, Property.PROGRESS, (v, t) -> {
            final var tint = ColorStateList.valueOf(t.palette().color(role));
            ((android.widget.ProgressBar) v).setProgressTintList(tint);
            ((android.widget.ProgressBar) v).setProgressBackgroundTintList(
                    ColorStateList.valueOf(t.palette().color(UiColor.MUTED)).withAlpha(90));
            ((android.widget.ProgressBar) v).setIndeterminateTintList(tint);
            if (v instanceof android.widget.SeekBar seek) seek.setThumbTintList(tint);
        });
    }
    static void dialog(android.app.AlertDialog dialog, android.app.Activity owner) {
        final View root = dialog.getWindow().getDecorView();
        DialogContentInsets.bind(root, owner);
        dialog.getWindow().setBackgroundDrawable(paint(root.getResources().getDisplayMetrics().density,
                UiColor.PANEL, 8 * root.getResources().getDisplayMetrics().density, UiColor.HOVER));
        dialogContents(root);
    }
    private static void dialogContents(View view) {
        if (view instanceof TextView text && view.getTag(R.id.appearance_binding) == null) text(text, UiColor.TEXT);
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) dialogContents(group.getChildAt(i));
        }
    }
    private static ColorStateList states(ShellAppearance theme, UiColor role) {
        return new ColorStateList(new int[][] {new int[] {-android.R.attr.state_enabled}, new int[0]},
                new int[] {theme.palette().color(UiColor.MUTED), theme.palette().color(role)});
    }
    private static void bind(View view, Property property, Style style) {
        Binding binding = (Binding) view.getTag(R.id.appearance_binding);
        if (binding == null) {
            binding = new Binding(view);
            view.setTag(R.id.appearance_binding, binding);
            view.addOnAttachStateChangeListener(binding);
            BINDINGS.put(binding, true);
        }
        binding.styles.put(property, style);
        if (view instanceof android.widget.CompoundButton button && !binding.styles.containsKey(Property.BUTTON)) {
            button(button, UiColor.ACCENT);
        }
        style.apply(view, AppearanceStore.current());
        binding.applyTypography(AppearanceStore.current());
    }
    static void refresh() {
        UiMotion.refresh();
        for (var control : new ArrayList<>(FEEDBACK.keySet())) control.refresh();
        synchronized (SYMBOLS) {
            for (var drawable : new ArrayList<>(SYMBOLS.keySet())) drawable.refresh();
        }
        for (Paint paint : new ArrayList<>(PAINTS.keySet())) paint.refresh();
        for (Binding binding : new ArrayList<>(BINDINGS.keySet())) binding.refresh();
    }
    static android.graphics.drawable.StateListDrawable feedback(float density, int radius) {
        final var drawable = new UiFeedbackDrawable(density, radius);
        FEEDBACK.put(drawable, true);
        return drawable;
    }
    static GradientDrawable paint(float density, UiColor fill, float radiusPx, UiColor border) {
        final Paint paint = new Paint(density, fill, radiusPx, border);
        PAINTS.put(paint, true);
        paint.refresh();
        return paint;
    }
    static GradientDrawable taskbarPaint(float density) {
        final Paint paint = new Paint(density, UiColor.PANEL, 0, UiColor.SURFACE);
        paint.taskbar = true;
        PAINTS.put(paint, true);
        paint.refresh();
        return paint;
    }
    private static final class Binding implements View.OnAttachStateChangeListener {
        final View view;
        final EnumMap<Property, Style> styles = new EnumMap<>(Property.class);
        float baseSize;
        Typeface baseFace;
        boolean captured;
        ShellAppearance.Typography appliedTypography;
        Binding(View view) { this.view = view; }
        public void onViewAttachedToWindow(View view) { refresh(); }
        public void onViewDetachedFromWindow(View view) {}
        void refresh() {
            final ShellAppearance theme = AppearanceStore.current();
            for (Style style : styles.values()) style.apply(view, theme);
            applyTypography(theme);
        }
        void applyTypography(ShellAppearance theme) {
            if (view instanceof TextView text && view.isAttachedToWindow()) {
                if (!captured) {
                    baseSize = text.getTextSize(); baseFace = text.getTypeface(); captured = true;
                }
                if (theme.typography().equals(appliedTypography)) return;
                appliedTypography = theme.typography();
                final String family = switch (theme.typography().font()) {
                    case SANS -> "sans-serif"; case SERIF -> "serif"; case MONO -> "monospace";
                };
                text.setTypeface(theme.typography().font() == ShellAppearance.Font.SANS ? baseFace
                        : Typeface.create(family, baseFace == null ? Typeface.NORMAL : baseFace.getStyle()));
                if (text.getAutoSizeTextType() == TextView.AUTO_SIZE_TEXT_TYPE_NONE) {
                    text.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseSize * theme.typography().scale());
                }
            }
        }
    }
    private static final class Paint extends GradientDrawable {
        final float density;
        final UiColor fill;
        final float radius;
        final UiColor border;
        boolean taskbar;
        Paint(float density, UiColor fill, float radius, UiColor border) {
            this.density = density; this.fill = fill; this.radius = radius; this.border = border;
        }
        void refresh() {
            final ShellAppearance theme = AppearanceStore.current();
            final int color = theme.palette().color(fill);
            setColor(taskbar ? (Math.round(255 * theme.taskbar().opacity()) << 24) | (color & 0xffffff) : color);
            setCornerRadius(taskbar ? theme.taskbar().radiusDp() * density : radius * theme.shape().radiusScale());
            setStroke(border == UiColor.TRANSPARENT ? 0 : Math.round(density * theme.shape().borderDp()),
                    theme.palette().color(border));
            invalidateSelf();
        }
    }
}
