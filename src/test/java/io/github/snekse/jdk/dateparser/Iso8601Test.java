package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ISO 8601 — the international standard for date/time interchange.
 *
 * <p><b>ISO 8601</b> (The International Standard) defines a family of date/time string formats
 * using a year-month-day ordering with dash separators, a {@code T} separator between date and
 * time, and a UTC offset or {@code Z} for timezone. It is the foundation for most other modern
 * date formats on the internet.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code 1999-01-31} — date only</li>
 *   <li>{@code 1999-01-01T00:00:00Z} — datetime with UTC</li>
 *   <li>{@code 1999-01-01T00:00:00.000+05:30} — datetime with fractional seconds and offset</li>
 *   <li>{@code 1999-01-01 12:00:00 -0500} — space separator (common relaxation)</li>
 * </ul>
 *
 * <p>Several tests in this class also cover <b>RFC 3339</b> (The Modern Internet Standard), which
 * is a strict profile of ISO 8601 designed for internet protocols. RFC 3339 requires an offset
 * ({@code Z} or {@code +HH:MM}), uses {@code T} or space as the date-time separator, and allows
 * fractional seconds. Any valid RFC 3339 string is also valid ISO 8601, so the tests here cover
 * both. RFC 3339 tests are annotated in their method-level comments.
 *
 * @see IsoWeekDateTest
 * @see IsoOrdinalDateTest
 * @see Rfc9557AnnotationTest
 */
class Iso8601Test {

    /** ISO 8601 date-only: {@code YYYY-MM-DD} with no time or timezone component. */
    @Test
    void date_only_local_date() {
        LocalDate result = OmniDateParser.toLocalDate("1999-01-31");
        assertEquals(LocalDate.of(1999, 1, 31), result);
    }

    /** RFC 3339: datetime with {@code Z} (UTC) suffix — the most common API format. */
    @Test
    void datetime_with_Z() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01T00:00:00Z");
        assertEquals(1999, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
        assertEquals(0, result.getHour());
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    /** RFC 3339: datetime with fractional seconds and a colon-separated positive offset. */
    @Test
    void datetime_with_positive_offset_colon() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01T00:00:00.000+05:30");
        assertEquals(ZoneOffset.of("+05:30"), result.getOffset());
        assertEquals(1999, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
    }

    /**
     * ISO 8601 with space separator and no-colon offset ({@code -0500}).
     * This is a common relaxation seen in logs and databases. Not strictly RFC 3339 (which
     * requires {@code T} and colon in offset), but widely accepted.
     */
    @Test
    void datetime_with_negative_offset_no_colon() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01 12:00:00 -0500");
        assertEquals(ZoneOffset.of("-05:00"), result.getOffset());
        assertEquals(12, result.getHour());
    }

    /** RFC 3339: fractional seconds (milliseconds) with {@code Z} suffix. */
    @Test
    void datetime_with_millis() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01T12:00:00.123Z");
        assertEquals(123_000_000, result.getNano());
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    /** ISO 8601 with named timezone abbreviation {@code UTC} (not part of RFC 3339). */
    @Test
    void datetime_with_utc_abbr() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01 12:00 UTC");
        assertEquals(ZoneOffset.UTC, result.getOffset());
        assertEquals(12, result.getHour());
    }

    /** ISO 8601 with named timezone abbreviation {@code GMT} (not part of RFC 3339). */
    @Test
    void datetime_with_gmt_abbr() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01 12:00 GMT");
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    /** ISO 8601 date-only defaults to UTC when no timezone is present. */
    @Test
    void date_only_defaults_to_utc() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01");
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    @ParameterizedTest
    @CsvSource({
            "1999-01-01T00:00:00Z,        1999, 1,  1,  0,  0,  0",
            "2000-12-31T23:59:59Z,        2000,12, 31, 23, 59, 59",
            "1999-06-15T08:30:00Z,        1999, 6, 15,  8, 30,  0",
    })
    void parameterized_iso_datetime(String input, int year, int month, int day,
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
