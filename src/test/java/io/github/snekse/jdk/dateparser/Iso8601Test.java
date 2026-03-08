package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class Iso8601Test {

    @Test
    void date_only_local_date() {
        LocalDate result = OmniDateParser.toLocalDate("1999-01-31");
        assertEquals(LocalDate.of(1999, 1, 31), result);
    }

    @Test
    void datetime_with_Z() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01T00:00:00Z");
        assertEquals(1999, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
        assertEquals(0, result.getHour());
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    @Test
    void datetime_with_positive_offset_colon() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01T00:00:00.000+05:30");
        assertEquals(ZoneOffset.of("+05:30"), result.getOffset());
        assertEquals(1999, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(1, result.getDayOfMonth());
    }

    @Test
    void datetime_with_negative_offset_no_colon() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01 12:00:00 -0500");
        assertEquals(ZoneOffset.of("-05:00"), result.getOffset());
        assertEquals(12, result.getHour());
    }

    @Test
    void datetime_with_millis() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01T12:00:00.123Z");
        assertEquals(123_000_000, result.getNano());
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    @Test
    void datetime_with_utc_abbr() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01 12:00 UTC");
        assertEquals(ZoneOffset.UTC, result.getOffset());
        assertEquals(12, result.getHour());
    }

    @Test
    void datetime_with_gmt_abbr() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-01-01 12:00 GMT");
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

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
