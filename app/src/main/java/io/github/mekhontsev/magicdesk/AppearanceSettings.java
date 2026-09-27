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
    private static final int IMPORT = 6201, EXPORT = 6202, SCHEMA = 6203;
    private final Activity mActivity;
    private final DesktopUiFactory mUi;
    private final ExecutorService mFiles = Executors.newSingleThreadExecutor();
    private final List<Runnable> mRefreshers = new ArrayList<>();
    private final Runnable mChanged = this::refresh;
    private AlertDialog mDialog;
    private AlertDialog mPreviewDialog;
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
                    AppearanceStore.apply(t.withTypography(new ShellAppearance.Typography(
                            ShellAppearance.Font.values()[index], t.typography().scale())));
                });
        slider(page, R.string.appearance_text_scale, 80, 130,
                () -> Math.round(AppearanceStore.current().typography().scale() * 100), value -> {
                    var t = AppearanceStore.current();
                    AppearanceStore.apply(t.withTypography(new ShellAppearance.Typography(
                            t.typography().font(), value / 100f)));
                });
        slider(page, R.string.appearance_border, 0, 3, () -> Math.round(AppearanceStore.current().shape().borderDp()), value -> {
            var t = AppearanceStore.current();
            AppearanceStore.apply(t.withShape(new ShellAppearance.Shape(t.shape().radiusScale(), value)));
        });
        slider(page, R.string.appearance_rounding, 0, 200, () -> Math.round(AppearanceStore.current().shape().radiusScale() * 100), value -> {
            var t = AppearanceStore.current();
            AppearanceStore.apply(t.withShape(new ShellAppearance.Shape(value / 100f, t.shape().borderDp())));
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
        heading(page, R.string.appearance_composition);
        Button composition = mUi.menuItem(R.string.appearance_components, UiColor.TEXT);
        composition.setOnClickListener(v -> editComponents());
        page.addView(composition);
        choice(page, R.string.appearance_start_presentation, new int[] {R.string.appearance_grid, R.string.appearance_list},
                () -> AppearanceStore.current().composition().start().presentation().ordinal(),
                v -> change("composition.start", "presentation", v == 0 ? "grid" : "list"));
        slider(page, R.string.appearance_tile_width, 80, 200, () -> AppearanceStore.current().composition().start().tileWidthDp(),
                v -> change("composition.start", "tileWidthDp", v));
        slider(page, R.string.appearance_icon_size, 24, 64, () -> AppearanceStore.current().composition().start().iconSizeDp(),
                v -> change("composition.start", "iconSizeDp", v));
        heading(page, R.string.appearance_motion);
        final Switch reduced = new Switch(mActivity);
        reduced.setText(R.string.appearance_reduced_motion); UiAppearance.text(reduced, UiColor.TEXT);
        mRefreshers.add(() -> reduced.setChecked(AppearanceStore.current().motion().reduced()));
        reduced.setOnCheckedChangeListener((v, checked) -> { if (!mRendering) change("motion", "reduced", checked); });
        page.addView(reduced);
        int[] effects = {R.string.appearance_none, R.string.appearance_fade};
        choice(page, R.string.appearance_panel_effect, effects, () -> AppearanceStore.current().motion().panels().ordinal(),
                v -> change("motion", "panels", v == 0 ? "none" : "fade"));
        choice(page, R.string.appearance_taskbar_effect, effects, () -> AppearanceStore.current().motion().taskbar().ordinal(),
                v -> change("motion", "taskbar", v == 0 ? "none" : "fade"));
        slider(page, R.string.appearance_duration, 0, 400, () -> AppearanceStore.current().motion().durationMs(), v -> change("motion", "durationMs", v));
        slider(page, R.string.appearance_feedback_duration, 0, 250, () -> AppearanceStore.current().motion().feedbackMs(), v -> change("motion", "feedbackMs", v));
        final LinearLayout files = new LinearLayout(mActivity);
        addCommand(files, R.string.appearance_import, R.drawable.ic_folder_open, this::importDocument);
        addCommand(files, R.string.appearance_export, R.drawable.ic_arrow_down, this::exportDocument);
        addCommand(files, R.string.appearance_reset, R.drawable.ic_file_refresh, () -> AppearanceStore.apply(ShellAppearance.defaults()));
        page.addView(files);
        final LinearLayout document = new LinearLayout(mActivity);
        addCommand(document, R.string.appearance_edit_document, R.drawable.ic_file_rename, this::editDocument);
        addCommand(document, R.string.appearance_export_schema, R.drawable.ic_file_properties, () ->
                mActivity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/json").putExtra(Intent.EXTRA_TITLE, "magicdesk-theme.schema.json"), SCHEMA));
        page.addView(document);
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
        UiAppearance.dialog(mDialog, mActivity);
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
            AppearanceStore.apply(t.withPalette(new ShellAppearance.Palette(colors)));
            dialog.dismiss();
        }));
        dialog.show();
        UiAppearance.dialog(dialog, mActivity);
    }

    private void changeBar(String key, Object value) {
        change("taskbar", key, value);
    }

    private void change(String section, String key, Object value) {
        try {
            var json = ShellAppearanceJson.encode(AppearanceStore.current());
            var target = json;
            for (String part : section.split("\\.")) target = target.getJSONObject(part);
            target.put(key, value);
            AppearanceStore.apply(ShellAppearanceJson.parse(json.toString()));
        } catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
    }

    private void editDocument() {
        final EditText text = new EditText(mActivity);
        text.setTypeface(android.graphics.Typeface.MONOSPACE); text.setTextSize(12);
        text.setGravity(android.view.Gravity.TOP);
        text.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        text.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(ShellAppearanceJson.MAX_BYTES)});
        try { text.setText(ShellAppearanceJson.encode(AppearanceStore.current()).toString(2)); }
        catch (org.json.JSONException error) { throw new IllegalArgumentException(error); }
        UiAppearance.text(text, UiColor.TEXT);
        final ScrollView scroll = new ScrollView(mActivity); scroll.addView(text);
        final AlertDialog editor = new AlertDialog.Builder(mActivity).setTitle(R.string.appearance_edit_document).setView(scroll)
                .setPositiveButton(R.string.appearance_preview, null).setNegativeButton(android.R.string.cancel, null).create();
        editor.setOnShowListener(d -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { preview(ShellAppearanceJson.parse(text.getText().toString())); }
            catch (Exception error) { text.setError(error.getMessage()); }
        }));
        editor.show(); UiAppearance.dialog(editor, mActivity);
    }

    private void preview(ShellAppearance value) {
        final String id = AppearanceStore.preview(value);
        mPreviewDialog = new AlertDialog.Builder(mActivity).setTitle(R.string.appearance_preview)
                .setPositiveButton(R.string.appearance_keep, (d, which) -> {
                    if (id.equals(AppearanceStore.snapshot().previewId())) AppearanceStore.confirm(id);
                }).setNegativeButton(android.R.string.cancel, null).create();
        mPreviewDialog.setOnDismissListener(d -> {
            if (id.equals(AppearanceStore.snapshot().previewId())) AppearanceStore.cancel(id);
            mPreviewDialog = null;
        });
        mPreviewDialog.show(); UiAppearance.dialog(mPreviewDialog, mActivity);
    }

    private void editComponents() {
        final List<ShellComposition.Component> items = new ArrayList<>(AppearanceStore.current().composition().taskbar());
        final LinearLayout rows = new LinearLayout(mActivity); rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(mUi.dp(16), mUi.dp(8), mUi.dp(16), mUi.dp(8));
        final Runnable render = new Runnable() {
            public void run() {
                rows.removeAllViews();
                for (int i = 0; i < items.size(); i++) {
                    final int index = i;
                    final LinearLayout row = new LinearLayout(mActivity); row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    TextView title = new TextView(mActivity);
                    title.setText(componentLabel(items.get(i).type()));
                    title.setSingleLine(true);
                    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    title.setAutoSizeTextTypeUniformWithConfiguration(8, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
                    title.setTooltipText(title.getText());
                    UiAppearance.text(title, UiColor.TEXT); row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
                    ImageButton up = mUi.taskbarIconButton(R.drawable.ic_arrow_up, R.string.appearance_move_up, true);
                    up.setEnabled(i > 0);
                    up.setOnClickListener(v -> { java.util.Collections.swap(items, index, index - 1); run(); });
                    row.addView(up, new LinearLayout.LayoutParams(mUi.dp(40), mUi.dp(44)));
                    ImageButton remove = mUi.taskbarIconButton(R.drawable.ic_remove, R.string.action_delete, true);
                    remove.setOnClickListener(v -> { items.remove(index); run(); });
                    row.addView(remove, new LinearLayout.LayoutParams(mUi.dp(40), mUi.dp(44)));
                    rows.addView(row);
                }
                Button add = mUi.menuItem(R.string.appearance_add_component, UiColor.TEXT);
                add.setOnClickListener(v -> {
                    var choices = java.util.Arrays.stream(ShellComposition.Kind.values()).filter(kind -> kind == ShellComposition.Kind.SPACER
                            || items.stream().noneMatch(item -> item.type() == kind)).toList();
                    String[] labels = choices.stream().map(AppearanceSettings.this::componentLabel).toArray(String[]::new);
                    var dialog = new AlertDialog.Builder(mActivity).setTitle(R.string.appearance_add_component)
                            .setItems(labels, (d, which) -> { items.add(ShellComposition.Component.of(choices.get(which))); run(); }).show();
                    UiAppearance.dialog(dialog, mActivity);
                });
                add.setEnabled(items.size() < 24);
                rows.addView(add);
            }
        };
        render.run();
        ScrollView scroll = new ScrollView(mActivity); scroll.addView(rows);
        AlertDialog dialog = new AlertDialog.Builder(mActivity).setTitle(R.string.appearance_components).setView(scroll)
                .setPositiveButton(R.string.appearance_preview, null).setNegativeButton(android.R.string.cancel, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                var theme = AppearanceStore.current();
                preview(new ShellAppearance(theme.palette(), theme.typography(), theme.shape(), theme.taskbar(),
                        new ShellComposition(items, theme.composition().start()), theme.motion(), theme.feedback(), theme.resources()));
            } catch (Exception error) { Toast.makeText(mActivity, error.getMessage(), Toast.LENGTH_LONG).show(); }
        }));
        dialog.show(); UiAppearance.dialog(dialog, mActivity);
    }

    private String componentLabel(ShellComposition.Kind kind) {
        return mActivity.getString(switch (kind) {
            case START -> R.string.action_start;
            case TASKS -> R.string.appearance_tasks;
            case SHOW_DESKTOP -> R.string.action_show_desktop;
            case OPEN_TASKS -> R.string.action_open_tasks;
            case NOTIFICATIONS -> R.string.action_notifications;
            case KEYBOARD_LAYOUT -> R.string.appearance_keyboard_layout;
            case PHONE_SCREEN -> R.string.tooltip_phone_screen;
            case QUICK_CONTROLS -> R.string.section_quick_controls;
            case BATTERY -> R.string.appearance_battery;
            case CLOCK -> R.string.appearance_clock;
            case SPACER -> R.string.appearance_spacer;
        });
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
        if (request != IMPORT && request != EXPORT && request != SCHEMA) return false;
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
                    mActivity.runOnUiThread(() -> {
                        if (!mClosed) try { preview(value); }
                        catch (Exception error) { Toast.makeText(mActivity, error.getMessage(), Toast.LENGTH_LONG).show(); }
                    });
                } else {
                    try (var output = mActivity.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new java.io.IOException("Document is unavailable");
                        output.write((request == SCHEMA ? ShellAppearanceSchema.document() : ShellAppearanceJson.encode(snapshot))
                                .toString(2).getBytes(StandardCharsets.UTF_8));
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
        if (mPreviewDialog != null) mPreviewDialog.dismiss();
        if (mDialog != null) mDialog.dismiss();
        AppearanceStore.unlisten(mChanged);
        mFiles.shutdownNow();
    }
}
