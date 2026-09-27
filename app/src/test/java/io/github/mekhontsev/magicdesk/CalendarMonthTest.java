package io.github.mekhontsev.magicdesk;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CalendarMonthTest {
    @Test public void leapMonthAndLocaleWeekStart() {
        var monday = new CalendarMonth(YearMonth.of(2024, 2), DayOfWeek.MONDAY);
        assertEquals(LocalDate.of(2024, 1, 29), monday.dayAt(0));
        assertEquals(LocalDate.of(2024, 2, 29), monday.dayAt(31));
        assertEquals(LocalDate.of(2024, 3, 10), monday.dayAt(41));
        var sunday = new CalendarMonth(monday.month(), DayOfWeek.SUNDAY);
        assertEquals(LocalDate.of(2024, 1, 28), sunday.dayAt(0));
    }
    @Test public void yearBoundaryAndFixedCellRange() {
        var month = new CalendarMonth(YearMonth.of(2026, 1), DayOfWeek.MONDAY);
        assertEquals(LocalDate.of(2025, 12, 29), month.dayAt(0));
        for (int i = 1; i < 42; i++) assertEquals(month.dayAt(i - 1).plusDays(1), month.dayAt(i));
        assertThrows(IndexOutOfBoundsException.class, () -> month.dayAt(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> month.dayAt(42));
    }
}
