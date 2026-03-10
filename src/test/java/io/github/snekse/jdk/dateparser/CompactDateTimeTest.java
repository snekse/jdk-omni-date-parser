package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for compact YYYYMMDDHHmmss datetime — a 12 or 14-digit numeric format.
 *
 * <p>This format concatenates date and time components into a single undelimited numeric string.
 * It extends the 8-digit compact date ({@code YYYYMMDD}) tested elsewhere by appending
 * {@code HHmm} (12 digits) or {@code HHmmss} (14 digits). It is commonly produced by legacy
 * systems, file-naming conventions, and some database exports where separators are undesirable.
 *
 * <p>Unlike the ISO 8601 basic format ({@code YYYYMMDD'T'HHmmss}), this variant omits the
 * {@code T} separator entirely.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code 20140722105203} — 14-digit: year, month, day, hour, minute, second</li>
 *   <li>{@code 201407221052} — 12-digit: year, month, day, hour, minute (seconds default to 0)</li>
 *   <li>{@code 20140722105203 UTC} — with trailing timezone</li>
 *   <li>{@code 20140722105203 +0900} — with trailing numeric offset</li>
 * </ul>
 */
class CompactDateTimeTest {

    /** 14-digit compact: all six components (YYYYMMDDHHmmss). */
    @Test
    void fourteen_digit_compact() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("20140722105203");
        assertAll(
                () -> assertEquals(2014, result.getYear()),
                () -> assertEquals(7, result.getMonthValue()),
                () -> assertEquals(22, result.getDayOfMonth()),
                () -> assertEquals(10, result.getHour()),
                () -> assertEquals(52, result.getMinute()),
                () -> assertEquals(3, result.getSecond()),
                // no zone in input → falls back to default (UTC)
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void twelve_digit_compact() {
        // Unique vs 14-digit: seconds omitted → defaults to 0
        ZonedDateTime result = OmniDateParser.toZonedDateTime("201407221052");
        assertAll(
                () -> assertEquals(2014, result.getYear()),
                () -> assertEquals(7, result.getMonthValue()),
                () -> assertEquals(22, result.getDayOfMonth()),
                () -> assertEquals(10, result.getHour()),
                () -> assertEquals(52, result.getMinute()),
                () -> assertEquals(0, result.getSecond()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void with_trailing_utc() {
        // Unique: trailing named timezone after compact digits
        ZonedDateTime result = OmniDateParser.toZonedDateTime("20140722105203 UTC");
        assertAll(
                () -> assertEquals(2014, result.getYear()),
                () -> assertEquals(7, result.getMonthValue()),
                () -> assertEquals(22, result.getDayOfMonth()),
                () -> assertEquals(10, result.getHour()),
                () -> assertEquals(52, result.getMinute()),
                () -> assertEquals(3, result.getSecond()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void with_trailing_named_zone() {
        // Unique: non-UTC named timezone after compact digits
        ZonedDateTime result = OmniDateParser.toZonedDateTime("20140722105203 EST");
        assertAll(
                () -> assertEquals(2014, result.getYear()),
                () -> assertEquals(10, result.getHour()),
                () -> assertEquals(ZoneId.of("America/New_York"), result.getZone())
        );
    }

    @Test
    void with_trailing_numeric_offset() {
        // Unique: numeric offset after compact digits
        ZonedDateTime result = OmniDateParser.toZonedDateTime("20140722105203 +0900");
        assertAll(
                () -> assertEquals(2014, result.getYear()),
                () -> assertEquals(10, result.getHour()),
                () -> assertEquals(ZoneOffset.of("+09:00"), result.getOffset())
        );
    }
}
