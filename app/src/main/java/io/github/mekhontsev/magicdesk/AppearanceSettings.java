package io.github.mekhontsev.magicdesk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.widget.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** UI and SAF adapters to the same validated appearance document used by automation. */
final class AppearanceSettings implements AutoCloseable {
    private static final int IMPORT = 6201, EXPORT = 6202;
    private final Activity mActivity;
    private final DesktopUiFactory mUi;
    private final ExecutorService mFiles = Executors.newSingleThreadExecutor();
    private final List<Runnable> mRefreshers = new ArrayList<>();
    private final Runnable mChanged = this::refresh;
    private AlertDialog mDialog;
    private boolean mRendering;
    private boolean mClosed;

    AppearanceSettings(Activity activity) { mActivity = activity; mUi = new DesktopUiFactory(activity); }

    void show() {
        if (mDialog != null) { mDialog.show(); return; }
        final LinearLayout page = new LinearLayout(mActivity);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(mUi.dp(16), mUi.dp(8), mUi.dp(16), mUi.dp(8));
        UiAppearance.background(page, UiColor.PANEL);
        heading(page, R.string.appearance_colors);
        final LinearLayout presets = new LinearLayout(mActivity);
        final String[] ids = {"dark", "light", "contrast"};
        final int[] names = {R.string.appearance_dark, R.string.appearance_light, R.string.appearance_contrast};
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            final Button button = mUi.menuItem(names[i], UiColor.TEXT);
            button.setGravity(android.view.Gravity.CENTER);
            button.setBackground(mUi.flatButtonBackground(mUi.dp(4)));
            mRefreshers.add(() -> {
                var current = AppearanceStore.current();
                button.setSelected(current.equals(current.withStyle(ShellAppearance.preset(id))));
            });
            button.setOnClickListener(v -> AppearanceStore.apply(AppearanceStore.current().withStyle(ShellAppearance.preset(id))));
            presets.addView(button, new LinearLayout.LayoutParams(0, mUi.dp(48), 1));
        }
        page.addView(presets);
        final LinearLayout swatches = new LinearLayout(mActivity);
        for (UiColor role : UiColor.values()) {
            if (role == UiColor.TRANSPARENT) continue;
            final View swatch = new View(mActivity);
            swatch.setBackground(mUi.rounded(role, mUi.dp(2), UiColor.MUTED));
            swatch.setContentDescription(role.name().toLowerCase(Locale.ROOT));
            swatch.setTooltipText(swatch.getContentDescription());
            swatch.setFocusable(true);
            swatch.setOnClickListener(v -> editColor(role));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, mUi.dp(40), 1);
            p.setMargins(mUi.dp(2), mUi.dp(4), mUi.dp(2), mUi.dp(4));
            swatches.addView(swatch, p);
        }
        page.addView(swatches);
        choice(page, R.string.appearance_font, new int[] {R.string.appearance_sans, R.string.appearance_serif, R.string.appearance_mono},
                () -> AppearanceStore.current().typography().font().ordinal(), index -> {
                    var t = AppearanceStore.current();
                    AppearanceStore.apply(new ShellAppearance(t.palette(), new ShellAppearance.Typography(
                            ShellAppearance.Font.values()[index], t.typography().scale()), t.shape(), t.taskbar()));
                });
        slider(page, R.string.appearance_text_scale, 80, 130,
                () -> Math.round(AppearanceStore.current().typography().scale() * 100), value -> {
                    var t = AppearanceStore.current();
                    AppearanceStore.apply(new ShellAppearance(t.palette(), new ShellAppearance.Typography(
                            t.typography().font(), value / 100f), t.shape(), t.taskbar()));
                });
        slider(page, R.string.appearance_border, 0, 3, () -> Math.round(AppearanceStore.current().shape().borderDp()), value -> {
            var t = AppearanceStore.current();
            AppearanceStore.apply(new ShellAppearance(t.palette(), t.typography(), new ShellAppearance.Shape(t.shape().radiusScale(), value), t.taskbar()));
        });
        slider(page, R.string.appearance_rounding, 0, 200, () -> Math.round(AppearanceStore.current().shape().radiusScale() * 100), value -> {
            var t = AppearanceStore.current();
            AppearanceStore.apply(new ShellAppearance(t.palette(), t.typography(), new ShellAppearance.Shape(value / 100f, t.shape().borderDp()), t.taskbar()));
        });
        heading(page, R.string.appearance_taskbar);
        choice(page, R.string.appearance_width, new int[] {R.string.appearance_fill, R.string.appearance_content},
                () -> AppearanceStore.current().taskbar().width().ordinal(), value -> changeBar("width", ShellAppearance.Width.values()[value].name().toLowerCase(Locale.ROOT)));
        choice(page, R.string.appearance_alignment, new int[] {R.string.appearance_start, R.string.appearance_center, R.string.appearance_end},
                () -> AppearanceStore.current().taskbar().alignment().ordinal(), value -> changeBar("alignment", ShellAppearance.Alignment.values()[value].name().toLowerCase(Locale.ROOT)));
        slider(page, R.string.appearance_max_width, 240, 4096, () -> AppearanceStore.current().taskbar().maxWidthDp(), v -> changeBar("maxWidthDp", v));
        slider(page, R.string.appearance_side_gap, 0, 96, () -> AppearanceStore.current().taskbar().sideGapDp(), v -> changeBar("sideGapDp", v));
        slider(page, R.string.appearance_bottom_gap, 0, 96, () -> AppearanceStore.current().taskbar().bottomGapDp(), v -> changeBar("bottomGapDp", v));
        slider(page, R.string.appearance_padding, 0, 16, () -> AppearanceStore.current().taskbar().paddingDp(), v -> changeBar("paddingDp", v));
        slider(page, R.string.appearance_radius, 0, 32, () -> AppearanceStore.current().taskbar().radiusDp(), v -> changeBar("radiusDp", v));
        slider(page, R.string.appearance_opacity, 15, 100, () -> Math.round(AppearanceStore.current().taskbar().opacity() * 100), v -> changeBar("opacity", v / 100f));
        final Switch reserve = new Switch(mActivity);
        UiAppearance.button(reserve, UiColor.ACCENT);
        reserve.setText(R.string.appearance_reserve);
        UiAppearance.text(reserve, UiColor.TEXT);
        reserve.setPadding(0, mUi.dp(8), 0, mUi.dp(8));
        mRefreshers.add(() -> reserve.setChecked(AppearanceStore.current().taskbar().reserveSpace()));
        reserve.setOnCheckedChangeListener((v, checked) -> { if (!mRendering) changeBar("reserveSpace", checked); });
        page.addView(reserve);
        final LinearLayout files = new LinearLayout(mActivity);
        addCommand(files, R.string.appearance_import, R.drawable.ic_folder_open, this::importDocument);
        addCommand(files, R.string.appearance_export, R.drawable.ic_arrow_down, this::exportDocument);
        addCommand(files, R.string.appearance_reset, R.drawable.ic_file_refresh, () -> AppearanceStore.apply(ShellAppearance.defaults()));
        page.addView(files);
        final ScrollView scroll = new ScrollView(mActivity);
        scroll.addView(page);
        mDialog = new AlertDialog.Builder(mActivity).setTitle(R.string.appearance_title).setView(scroll)
                .setPositiveButton(android.R.string.ok, null).create();
        mDialog.setOnDismissListener(d -> {
            AppearanceStore.unlisten(mChanged); mRefreshers.clear(); mDialog = null;
        });
        AppearanceStore.listen(mChanged);
        refresh();
        mDialog.show();
        UiAppearance.dialog(mDialog);
    }

    private void editColor(UiColor role) {
        final EditText text = new EditText(mActivity);
        text.setSingleLine(true);
        text.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(7)});
        text.setText(String.format(Locale.ROOT, "#%06X", UiAppearance.color(role) & 0xffffff));
        UiAppearance.text(text, UiColor.TEXT);
        final AlertDialog dialog = new AlertDialog.Builder(mActivity).setTitle(role.name().toLowerCase(Locale.ROOT)).setView(text)
                .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!text.getText().toString().matches("#[0-9a-fA-F]{6}")) { text.setError("#RRGGBB"); return; }
            var t = AppearanceStore.current();
            EnumMap<UiColor, Integer> colors = new EnumMap<>(UiColor.class);
            colors.putAll(t.palette().colors());
            colors.put(role, android.graphics.Color.parseColor(text.getText().toString()));
            AppearanceStore.apply(new ShellAppearance(new ShellAppearance.Palette(colors), t.typography(), t.shape(), t.taskbar()));
            dialog.dismiss();
        }));
        dialog.show();
        UiAppearance.dialog(dialog);
    }

    private void changeBar(String key, Object value) {
        try {
            var json = ShellAppearanceJson.encode(AppearanceStore.current());
            json.getJSONObject("taskbar").put(key, value);
            AppearanceStore.apply(ShellAppearanceJson.parse(json.toString()));
        } catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
    }

    private void heading(LinearLayout page, int title) { mUi.addControlSection(page, title, mUi.dp(12)); }
    private TextView label(LinearLayout page, int title) {
        TextView text = new TextView(mActivity); text.setText(title); text.setTextSize(14);
        UiAppearance.text(text, UiColor.TEXT); page.addView(text); return text;
    }
    private void choice(LinearLayout page, int title, int[] names, IntSupplier get, IntConsumer set) {
        label(page, title);
        final String[] labels = new String[names.length];
        for (int i = 0; i < names.length; i++) labels[i] = mActivity.getString(names[i]);
        final Spinner spinner = mUi.spinner(labels);
        spinner.setContentDescription(mActivity.getString(title));
        mRefreshers.add(() -> spinner.setSelection(get.getAsInt()));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mRendering && position != get.getAsInt()) set.accept(position);
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        page.addView(spinner, new LinearLayout.LayoutParams(-1, mUi.dp(48)));
    }
    private void slider(LinearLayout page, int title, int min, int max, IntSupplier get, IntConsumer set) {
        final TextView label = label(page, title);
        final SeekBar seek = new SeekBar(mActivity);
        UiAppearance.progress(seek, UiColor.ACCENT);
        seek.setMin(min); seek.setMax(max); seek.setContentDescription(mActivity.getString(title));
        final java.util.function.IntConsumer show = v -> label.setText(mActivity.getString(title) + ": " + v);
        mRefreshers.add(() -> { seek.setProgress(get.getAsInt()); show.accept(get.getAsInt()); });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) {
                show.accept(progress);
                if (user && !bar.isPressed()) set.accept(progress);
            }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) { if (!mRendering) set.accept(bar.getProgress()); }
        });
        page.addView(seek, new LinearLayout.LayoutParams(-1, mUi.dp(40)));
    }
    private void addCommand(LinearLayout row, int title, int icon, Runnable action) {
        ImageButton button = mUi.taskbarIconButton(icon, title, false);
        button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, mUi.dp(56), 1));
    }
    private void refresh() {
        mRendering = true;
        try { for (Runnable action : mRefreshers) action.run(); }
        finally { mRendering = false; }
    }
    private void importDocument() {
        mActivity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json"), IMPORT);
    }
    private void exportDocument() {
        mActivity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "magicdesk-theme.json"), EXPORT);
    }
    boolean onResult(int request, int result, Intent data) {
        if (request != IMPORT && request != EXPORT) return false;
        if (result != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        final var uri = data.getData();
        final var snapshot = AppearanceStore.current();
        mFiles.execute(() -> {
            try {
                if (request == IMPORT) {
                    final ShellAppearance value;
                    try (var input = mActivity.getContentResolver().openInputStream(uri)) {
                        if (input == null) throw new java.io.IOException("Document is unavailable");
                        byte[] bytes = input.readNBytes(ShellAppearanceJson.MAX_BYTES + 1);
                        if (bytes.length > ShellAppearanceJson.MAX_BYTES) throw new java.io.IOException("Appearance document exceeds 32 KiB");
                        value = ShellAppearanceJson.parse(new String(bytes, StandardCharsets.UTF_8));
                    }
                    mActivity.runOnUiThread(() -> { if (!mClosed) AppearanceStore.apply(value); });
                } else {
                    try (var output = mActivity.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException("Document is unavailable");
                        output.write(ShellAppearanceJson.encode(snapshot).toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception error) {
                mActivity.runOnUiThread(() -> { if (!mClosed) Toast.makeText(mActivity, error.getMessage(), Toast.LENGTH_LONG).show(); });
            }
        });
        return true;
    }
    public void close() {
        mClosed = true;
        if (mDialog != null) mDialog.dismiss();
        AppearanceStore.unlisten(mChanged);
        mFiles.shutdownNow();
    }
}
