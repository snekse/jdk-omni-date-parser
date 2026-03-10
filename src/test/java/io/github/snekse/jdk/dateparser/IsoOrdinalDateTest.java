package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ISO 8601 ordinal dates — an alternative calendar representation within ISO 8601.
 *
 * <p><b>ISO 8601 ordinal dates</b> express a date using the year and day-of-year (001–365, or
 * 366 in leap years) rather than year-month-day. This compact representation is commonly used
 * in scientific data, satellite imagery timestamps, and military/aviation contexts where a
 * single sequential day number is more convenient than month+day.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code 1999-001} — extended format (January 1)</li>
 *   <li>{@code 1999365} — basic/compact format, no dash (December 31)</li>
 *   <li>{@code 2000-366} — leap year day 366 (December 31)</li>
 *   <li>{@code 1999-001T00:00:00Z} — with time and timezone suffix</li>
 * </ul>
 *
 * @see Iso8601Test
 * @see IsoWeekDateTest
 */
class IsoOrdinalDateTest {

    /** Extended ordinal format: {@code YYYY-DDD} where DDD is the day-of-year. */
    @Test
    void extended_format_day_1() {
        // Day 1 → January 1
        LocalDate result = OmniDateParser.toLocalDate("1999-001");
        assertEquals(LocalDate.of(1999, 1, 1), result);
    }

    @Test
    void extended_format_mid_year() {
        // Day 100 → April 10 in non-leap year
        LocalDate result = OmniDateParser.toLocalDate("1999-100");
        assertEquals(LocalDate.of(1999, 4, 10), result);
    }

    @Test
    void compact_format_day_365() {
        // Unique: 7-digit compact form (no dash)
        LocalDate result = OmniDateParser.toLocalDate("1999365");
        assertEquals(LocalDate.of(1999, 12, 31), result);
    }

    @Test
    void leap_year_day_366() {
        // Unique: day 366 valid only in leap year
        LocalDate result = OmniDateParser.toLocalDate("2000-366");
        assertEquals(LocalDate.of(2000, 12, 31), result);
    }

    @Test
    void with_time_and_zone() {
        // Unique: ordinal date with T-separator time and UTC zone
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-001T00:00:00Z");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1, result.getMonthValue()),
                () -> assertEquals(1, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour()),
                () -> assertEquals(0, result.getMinute()),
                () -> assertEquals(0, result.getSecond()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void with_time_and_offset() {
        // Unique: ordinal date with non-UTC offset
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2000-060T14:30:00+09:00");
        assertAll(
                () -> assertEquals(LocalDate.of(2000, 2, 29), result.toLocalDate()),
                () -> assertEquals(14, result.getHour()),
                () -> assertEquals(30, result.getMinute()),
                () -> assertEquals(ZoneOffset.of("+09:00"), result.getOffset())
        );
    }

    @Test
    void invalid_day_367_throws() {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toLocalDate("2000-367"));
    }

    @Test
    void non_leap_year_day_366_throws() {
        // Unique: 366 is invalid in a non-leap year
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toLocalDate("1999-366"));
    }

    @Test
    void day_zero_throws() {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toLocalDate("1999-000"));
    }
}
