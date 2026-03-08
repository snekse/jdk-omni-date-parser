package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for slash, dash, and dot-separated Western date formats.
 */
class WesternFormatsTest {

    // --- Slash-separated ---

    @Test
    void slash_ddmmyyyy_with_gmt() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("31/12/1999 00:00 GMT");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(12, result.getMonthValue()),
                () -> assertEquals(31, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour())
        );
    }

    @Test
    void slash_mmddyyyy_pm_pst() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("01/31/1999 12:00 PM");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1, result.getMonthValue()),
                () -> assertEquals(31, result.getDayOfMonth()),
                () -> assertEquals(12, result.getHour())
        );
    }

    @Test
    void slash_yyyymmdd_jst() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999/01/01 00:00 JST");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1, result.getMonthValue()),
                () -> assertEquals(1, result.getDayOfMonth())
        );
    }

    // --- Dash-separated western (not ISO) ---

    @Test
    void dash_ddmmyyyy_with_offset() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("31-12-1999 12:00:00.000 +0100");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(12, result.getMonthValue()),
                () -> assertEquals(31, result.getDayOfMonth()),
                () -> assertEquals(12, result.getHour()),
                () -> assertEquals(0, result.getNano())
        );
    }

    @Test
    void dash_ddmmyyyy_pm_gmt() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("31-12-1999 11:59 PM GMT");
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
    }

    // --- Compact numeric YYYYMMDD ---

    @Test
    void compact_yyyymmdd() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("19990101");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(1, result.getMonthValue()),
                () -> assertEquals(1, result.getDayOfMonth())
        );
    }

    // --- Parameterized coverage ---

    @ParameterizedTest
    @CsvSource({
            "31/12/1999 00:00 GMT,      1999,12,31,  0, 0, 0",
            "31/12/1999 12:00:00 WET,   1999,12,31, 12, 0, 0",
            "31/12/1999 23:59 CET,      1999,12,31, 23,59, 0",
            "1999/12/31 00:00:00 +09:00,1999,12,31,  0, 0, 0",
            "1999/12/31 12:00 HKT,      1999,12,31, 12, 0, 0",
            "1999/12/31 23:59:59 KST,   1999,12,31, 23,59,59",
    })
    void parameterized_western(String input, int year, int month, int day,
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
