package io.github.mekhontsev.magicdesk;

import android.content.Context;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** Native shell calendar; date arithmetic is delegated to java.time. */
final class CalendarMonthView extends LinearLayout {
    private final Locale mLocale = getResources().getConfiguration().getLocales().get(0);
    private final Button[] mDays = new Button[42];
    private final TextView mTitle;
    private LocalDate mSelected = LocalDate.now();
    private CalendarMonth mMonth = monthOf(mSelected);

    CalendarMonthView(Context context, DesktopUiFactory ui) {
        super(context);
        setOrientation(VERTICAL);
        final LinearLayout navigation = new LinearLayout(context);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        final var previous = ui.menuIconButton(R.drawable.ic_file_back, R.string.action_previous);
        previous.setOnClickListener(v -> move(-1));
        navigation.addView(previous, new LayoutParams(ui.dp(40), ui.dp(40)));
        mTitle = new TextView(context);
        mTitle.setGravity(Gravity.CENTER); mTitle.setTextSize(14);
        UiAppearance.text(mTitle, UiColor.TEXT);
        navigation.addView(mTitle, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        final var next = ui.menuIconButton(R.drawable.ic_file_forward, R.string.action_next);
        next.setOnClickListener(v -> move(1));
        navigation.addView(next, new LayoutParams(ui.dp(40), ui.dp(40)));
        addView(navigation);
        final LinearLayout weekdays = new LinearLayout(context);
        for (int i = 0; i < 7; i++) {
            final TextView name = new TextView(context);
            name.setText(mMonth.firstWeekday().plus(i).getDisplayName(TextStyle.SHORT_STANDALONE, mLocale));
            name.setGravity(Gravity.CENTER); name.setTextSize(11);
            UiAppearance.text(name, UiColor.MUTED);
            weekdays.addView(name, new LayoutParams(0, ui.dp(24), 1));
        }
        addView(weekdays);
        for (int row = 0; row < 6; row++) {
            final LinearLayout week = new LinearLayout(context);
            for (int column = 0; column < 7; column++) {
                final int cell = row * 7 + column;
                final Button day = ui.smallButton("", UiColor.TRANSPARENT);
                day.setBackground(ui.flatButtonBackground(ui.dp(4)));
                day.setTextSize(14);
                day.setOnClickListener(v -> { mSelected = mMonth.dayAt(cell); render(); });
                week.addView(day, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
                mDays[cell] = day;
            }
            addView(week, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        }
        render();
    }
    void today() { mSelected = LocalDate.now(); mMonth = monthOf(mSelected); render(); }
    private CalendarMonth monthOf(LocalDate date) {
        return new CalendarMonth(YearMonth.from(date), WeekFields.of(mLocale).getFirstDayOfWeek());
    }
    private void move(int delta) {
        mMonth = new CalendarMonth(mMonth.month().plusMonths(delta), mMonth.firstWeekday());
        render();
    }
    private void render() {
        mTitle.setText(mMonth.month().format(DateTimeFormatter.ofPattern("LLLL uuuu", mLocale)));
        final LocalDate today = LocalDate.now();
        final var description = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(mLocale);
        for (int i = 0; i < mDays.length; i++) {
            final LocalDate date = mMonth.dayAt(i);
            final Button day = mDays[i];
            day.setText(Integer.toString(date.getDayOfMonth()));
            day.setContentDescription(date.format(description));
            day.setSelected(date.equals(mSelected));
            UiAppearance.textStates(day, date.equals(today) ? UiColor.ACCENT
                    : YearMonth.from(date).equals(mMonth.month()) ? UiColor.TEXT : UiColor.MUTED);
        }
    }
}
