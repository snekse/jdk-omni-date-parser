package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RFC 2822 — the email date format and its predecessors.
 *
 * <p><b>RFC 2822</b> (Internet Message Format, 2001) defines the date format used in email
 * headers. It superseded <b>RFC 822</b> (1982), which was the original email date standard.
 * <b>RFC 1123</b> (1989) updated RFC 822 and is also used as one of the standard HTTP date
 * formats. All three share the same essential wire format: an optional weekday abbreviation,
 * followed by day, abbreviated month name, year, time, and a numeric UTC offset.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code Fri, 01 Jan 1999 23:59:00 +0000} — with weekday prefix (RFC 2822 / RFC 1123)</li>
 *   <li>{@code 01 Jan 1999 23:59:00 +0000} — without weekday (RFC 2822 body only)</li>
 *   <li>{@code 15 DEC 2000 10:30:00 +0000} — case-insensitive month abbreviation</li>
 * </ul>
 *
 * <p>The format used in HTTP {@code Date} headers ({@code Mon, 01 Jan 2000 00:00:00 GMT}) is
 * defined by RFC 1123 and is syntactically identical to RFC 2822 with a weekday prefix, so it
 * is covered by the tests here.
 *
 * @see Rfc850Test
 */
class Rfc2822Test {

    /** Full RFC 2822 / RFC 1123 with weekday prefix: {@code Fri, 01 Jan 1999 23:59:00 +0000}. */
    @Test
    void rfc2822_with_day_of_week() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Fri, 01 Jan 1999 23:59:00 +0000");
        assertEquals(1999, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
        assertEquals(0, result.getSecond());
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    /** RFC 2822 without weekday prefix — just the date body: {@code DD Mon YYYY HH:MM:SS +HHMM}. */
    @Test
    void rfc2822_without_day_of_week() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("01 Jan 1999 23:59:00 +0000");
        assertEquals(1999, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    @Test
    void rfc2822_with_positive_nonzero_offset() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("01 Jan 1999 12:00:00 +0530");
        assertEquals(ZoneOffset.of("+05:30"), result.getOffset());
    }

    @Test
    void rfc2822_abbreviated_month_case_insensitive() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("15 DEC 2000 10:30:00 +0000");
        assertEquals(12, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
        assertEquals(2000, result.getYear());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mon, 01 Jan 2000 00:00:00 +0000",
            "Tue, 15 Mar 2005 14:30:00 +0100",
            "Wed, 31 Dec 1999 23:59:59 +0000",
    })
    void parameterized_rfc2822_with_day_of_week(String input) {
        assertDoesNotThrow(() -> OmniDateParser.toZonedDateTime(input));
    }
}
