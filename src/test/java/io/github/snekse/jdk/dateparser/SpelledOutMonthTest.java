package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for English spelled-out month formats (full names and abbreviations).
 */
class SpelledOutMonthTest {

    @Test
    void month_dd_yyyy_with_pm_and_offset() {
        // January 1, 1999 12:00 PM -0500
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 1, 1999 12:00 PM -0500");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1,    result.getMonthValue()),
                () -> assertEquals(1,    result.getDayOfMonth()),
                () -> assertEquals(12,   result.getHour()),
                () -> assertEquals(0,    result.getMinute())
        );
    }

    @Test
    void month_dd_yyyy_at_pm_with_tz() {
        // January 1, 1999 at 11:59 p.m. PST
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 1, 1999 at 11:59 p.m. PST");
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
    }

    @Test
    void dd_month_yyyy_with_time_and_tz() {
        // 31 December 1999 00:00:00 GMT
        ZonedDateTime result = OmniDateParser.toZonedDateTime("31 December 1999 00:00:00 GMT");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(12,   result.getMonthValue()),
                () -> assertEquals(31,   result.getDayOfMonth()),
                () -> assertEquals(0,    result.getHour())
        );
    }

    @Test
    void abbreviated_month_dd_yyyy_utc() {
        // Dec 31, 1999 00:00:00 UTC
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Dec 31, 1999 00:00:00 UTC");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(12,   result.getMonthValue()),
                () -> assertEquals(31,   result.getDayOfMonth()),
                () -> assertEquals(0,    result.getHour())
        );
    }

    @Test
    void year_first_spelled_month() {
        // 1999 January 1 00:00:00 UTC
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999 January 1 00:00:00 UTC");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1,    result.getMonthValue()),
                () -> assertEquals(1,    result.getDayOfMonth()),
                () -> assertEquals(0,    result.getHour())
        );
    }

    @Test
    void day_of_week_prefix_with_spelled_month() {
        // Friday, January 1, 1999 12:00:00 PM EST
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Friday, January 1, 1999 12:00:00 PM EST");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1,    result.getMonthValue()),
                () -> assertEquals(1,    result.getDayOfMonth()),
                () -> assertEquals(12,   result.getHour())
        );
    }

    @Test
    void dd_mon_yyyy_format() {
        // 01-Jan-1999 00:00:00 GMT
        ZonedDateTime result = OmniDateParser.toZonedDateTime("01-Jan-1999 00:00:00 GMT");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1,    result.getMonthValue()),
                () -> assertEquals(1,    result.getDayOfMonth()),
                () -> assertEquals(0,    result.getHour())
        );
    }

    @ParameterizedTest
    @CsvSource({
            "1 January 1999 12:00:00 BST,    1999, 1, 1, 12, 0, 0",
            "'Dec 31, 1999 12:00:00 PM EST', 1999,12,31, 12, 0, 0",
            "'Dec 31, 1999 11:59:59 PM PST', 1999,12,31, 23,59,59",
            "1999 January 1 12:00:00 EST,    1999, 1, 1, 12, 0, 0",
            "1999 January 1 23:59:00 PST,    1999, 1, 1, 23,59, 0",
    })
    void parameterized_spelled(String input, int year, int month, int day,
                                int hour, int minute, int second) {
        ZonedDateTime result = OmniDateParser.toZonedDateTime(input);
        assertAll(
                () -> assertEquals(year,   result.getYear()),
                () -> assertEquals(month,  result.getMonthValue()),
                () -> assertEquals(day,    result.getDayOfMonth()),
                () -> assertEquals(hour,   result.getHour()),
                () -> assertEquals(minute, result.getMinute()),
                () -> assertEquals(second, result.getSecond())
        );
    }
}
