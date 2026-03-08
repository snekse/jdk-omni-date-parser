package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class Rfc2822Test {

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
