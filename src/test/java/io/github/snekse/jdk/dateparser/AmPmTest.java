package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for 12-hour clock with AM/PM and a.m./p.m. markers.
 */
class AmPmTest {

    @Test
    void twelve_pm_is_noon() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 1, 1999 12:00 PM -0500");
        assertEquals(12, result.getHour());
    }

    @Test
    void twelve_am_is_midnight() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Friday, January 1, 1999 12:00 AM PST");
        assertEquals(0, result.getHour());
    }

    @Test
    void eleven_pm_is_23() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Dec 31, 1999 11:59:59 PM PST");
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
        assertEquals(59, result.getSecond());
    }

    @Test
    void dotted_pm_is_converted() {
        // January 1, 1999 at 11:59 p.m. PST → 23:59
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 1, 1999 at 11:59 p.m. PST");
        assertEquals(23, result.getHour());
        assertEquals(59, result.getMinute());
    }

    @Test
    void dotted_am_no_timezone() {
        // January 31, 1999 12:00 p.m. → 12:00
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 31, 1999 12:00 p.m.");
        assertEquals(12, result.getHour());
    }

    @Test
    void eleven_dotted_pm_no_timezone() {
        // January 31, 1999 11:59 p.m. → 23:59
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 31, 1999 11:59 p.m.");
        assertEquals(23, result.getHour());
    }

    @Test
    void iso_with_pm_and_named_tz() {
        // 1999-12-31 12:00 PM CET
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-12-31 12:00 PM CET");
        assertEquals(12, result.getHour());
    }

    @Test
    void iso_with_pm_cst() {
        // 1999-12-31 12:00:00 PM CST
        ZonedDateTime result = OmniDateParser.toZonedDateTime("1999-12-31 12:00:00 PM CST");
        assertEquals(12, result.getHour());
    }

    @Test
    void hour_above_12_with_pm_throws() {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("January 1, 1999 13:00 PM EST"));
    }

    @ParameterizedTest
    @CsvSource({
            "01/01/1999 12:00 PM PST, 12",
            "01/01/1999 11:59 PM EST, 23",
            "01/31/1999 12:00 PM,     12",
            "01/31/1999 11:59 PM,     23",
            "01-Dec-1999 12:00 PM,    12",
            "01-Dec-1999 11:59 PM,    23",
    })
    void parameterized_ampm(String input, int expectedHour) {
        ZonedDateTime result = OmniDateParser.toZonedDateTime(input);
        assertEquals(expectedHour, result.getHour());
    }
}
