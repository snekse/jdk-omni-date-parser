package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ISO-style dates with spelled or abbreviated month names instead of numeric
 * months — the {@code YYYY-Mon-DD} format.
 *
 * <p>While ISO 8601 strictly requires numeric months ({@code YYYY-MM-DD}), this hybrid
 * format is common in log files, database exports, and developer tools where readability
 * is preferred. The parser accepts both abbreviated ({@code Feb}) and full ({@code February})
 * month names, with optional period suffix ({@code Feb.}).
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code 2013-Feb-03} — abbreviated month</li>
 *   <li>{@code 2013-February-03} — full month name</li>
 *   <li>{@code 2013-Feb-03 12:30:00} — with space-separated time</li>
 *   <li>{@code 2013-Feb-03T12:30:00Z} — with T separator and timezone</li>
 * </ul>
 */
class IsoSpelledMonthTest {

    @Test
    void abbreviated_month() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2013-Feb-03");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(2, result.getMonthValue()),
                () -> assertEquals(3, result.getDayOfMonth())
        );
    }

    @Test
    void full_month_name() {
        // Unique: full month name instead of abbreviation
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2013-February-03");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(2, result.getMonthValue()),
                () -> assertEquals(3, result.getDayOfMonth())
        );
    }

    @Test
    void with_space_separated_time() {
        // Unique: time suffix separated by space
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2013-Feb-03 12:30:00");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(2, result.getMonthValue()),
                () -> assertEquals(3, result.getDayOfMonth()),
                () -> assertEquals(12, result.getHour()),
                () -> assertEquals(30, result.getMinute())
        );
    }

    @Test
    void with_t_separator_and_zone() {
        // Unique: T separator + timezone — ISO hybrid format
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2013-Feb-03T12:30:00Z");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(12, result.getHour()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void with_numeric_offset() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2013-Feb-03 09:00:00 +0530");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(9, result.getHour()),
                () -> assertEquals(ZoneOffset.of("+05:30"), result.getOffset())
        );
    }

    @Test
    void period_suffix_on_month() {
        // Unique: period after abbreviated month in ISO-style: "2013-Feb.-03"
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2013-Feb.-03");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(2, result.getMonthValue()),
                () -> assertEquals(3, result.getDayOfMonth())
        );
    }
}
