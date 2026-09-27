package io.github.mekhontsev.magicdesk;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/** Fixed six-week grid, with the first weekday supplied by the user's locale. */
record CalendarMonth(YearMonth month, DayOfWeek firstWeekday) {
    CalendarMonth { Objects.requireNonNull(month); Objects.requireNonNull(firstWeekday); }
    LocalDate dayAt(int cell) {
        if (cell < 0 || cell >= 42) throw new IndexOutOfBoundsException(cell);
        final LocalDate first = month.atDay(1);
        final int offset = Math.floorMod(first.getDayOfWeek().getValue() - firstWeekday.getValue(), 7);
        return first.minusDays(offset).plusDays(cell);
    }
}
