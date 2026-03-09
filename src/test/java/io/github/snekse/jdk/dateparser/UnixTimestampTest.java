package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Unix epoch timestamp parsing (seconds, milliseconds, microseconds, nanoseconds).
 */
class UnixTimestampTest {

    @Test
    void seconds_10_digits() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1332151919");
        assertAll(
                () -> assertEquals(2012, result.getYear()),
                () -> assertEquals(3, result.getMonthValue()),
                () -> assertEquals(19, result.getDayOfMonth()),
                () -> assertEquals(10, result.getHour()),
                () -> assertEquals(11, result.getMinute()),
                () -> assertEquals(59, result.getSecond()),
                () -> assertEquals(0, result.getNano()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void milliseconds_13_digits() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1384216367189");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(11, result.getMonthValue()),
                () -> assertEquals(12, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour()),
                () -> assertEquals(32, result.getMinute()),
                () -> assertEquals(47, result.getSecond()),
                () -> assertEquals(189_000_000, result.getNano()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void microseconds_16_digits() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1384216367111222");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(11, result.getMonthValue()),
                () -> assertEquals(12, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour()),
                () -> assertEquals(32, result.getMinute()),
                () -> assertEquals(47, result.getSecond()),
                () -> assertEquals(111_222_000, result.getNano()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void nanoseconds_19_digits() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1384216367111222333");
        assertAll(
                () -> assertEquals(2013, result.getYear()),
                () -> assertEquals(11, result.getMonthValue()),
                () -> assertEquals(12, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour()),
                () -> assertEquals(32, result.getMinute()),
                () -> assertEquals(47, result.getSecond()),
                () -> assertEquals(111_222_333, result.getNano()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void epoch_zero() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("0000000000");
        assertAll(
                () -> assertEquals(1970, result.getYear()),
                () -> assertEquals(1, result.getMonthValue()),
                () -> assertEquals(1, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour()),
                () -> assertEquals(0, result.getMinute()),
                () -> assertEquals(0, result.getSecond()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void ignores_defaultZone_config() {
        OmniDateParser parser = new OmniDateParser(
                OmniDateParserConfig.builder()
                        .defaultZone(ZoneId.of("Europe/London"))
                        .build());
        ZonedDateTime result = parser.parseZonedDateTime("1332151919");
        assertEquals(ZoneOffset.UTC, result.getOffset());
    }

    @Test
    void eight_digits_still_yyyymmdd() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("20260309");
        assertAll(
                () -> assertEquals(2026, result.getYear()),
                () -> assertEquals(3, result.getMonthValue()),
                () -> assertEquals(9, result.getDayOfMonth())
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678901", "123456789012", "12345678901234"})
    void non_matching_digit_lengths_throw(String input) {
        assertThrows(DateParseException.class, () -> OmniDateParser.toZonedDateTime(input));
    }
}
