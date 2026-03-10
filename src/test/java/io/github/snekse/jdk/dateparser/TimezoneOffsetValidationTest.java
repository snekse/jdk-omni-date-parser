package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for malformed timezone offset inputs.
 *
 * <p>Prior to the fix, these inputs leaked internal exceptions ({@link StringIndexOutOfBoundsException}
 * or {@link java.time.DateTimeException}) instead of the expected {@link DateParseException}.
 */
class TimezoneOffsetValidationTest {

    // --- Bug 1: 3-digit offset digits cause StringIndexOutOfBoundsException ---

    @ParameterizedTest(name = "GMT+{0} should throw DateParseException, not StringIndexOutOfBoundsException")
    @ValueSource(strings = {"GMT+123", "GMT-123", "UTC+123", "UTC-123"})
    void three_digit_gmt_offset_throws_DateParseException(String input) {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("2024-01-15T12:00:00 " + input),
                "Expected DateParseException for malformed offset in: " + input);
    }

    @ParameterizedTest(name = "+{0} should throw DateParseException, not StringIndexOutOfBoundsException")
    @ValueSource(strings = {"+123", "-123"})
    void three_digit_numeric_offset_throws_DateParseException(String input) {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("2024-01-15T12:00:00" + input),
                "Expected DateParseException for 3-digit offset: " + input);
    }

    // --- Bug 2: Out-of-range offsets cause DateTimeException ---

    @ParameterizedTest(name = "GMT+{0} should throw DateParseException, not DateTimeException")
    @ValueSource(strings = {"GMT+2500", "GMT-2500", "UTC+1900", "UTC-1900"})
    void out_of_range_gmt_offset_throws_DateParseException(String input) {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("2024-01-15T12:00:00 " + input),
                "Expected DateParseException for out-of-range offset in: " + input);
    }

    @ParameterizedTest(name = "{0} should throw DateParseException, not DateTimeException")
    @ValueSource(strings = {"+2500", "-2500", "+1900", "-1900"})
    void out_of_range_numeric_offset_throws_DateParseException(String input) {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("2024-01-15T12:00:00" + input),
                "Expected DateParseException for out-of-range numeric offset: " + input);
    }

    // --- Verify exception type is strictly DateParseException, not a subtype of something else ---

    @Test
    void three_digit_offset_is_not_index_out_of_bounds() {
        Exception ex = assertThrows(RuntimeException.class,
                () -> OmniDateParser.toZonedDateTime("2024-01-15T12:00:00 GMT+123"));
        assertInstanceOf(DateParseException.class, ex,
                "Should be DateParseException, not " + ex.getClass().getSimpleName());
    }

    @Test
    void out_of_range_offset_is_not_datetime_exception() {
        Exception ex = assertThrows(RuntimeException.class,
                () -> OmniDateParser.toZonedDateTime("2024-01-15T12:00:00 GMT+2500"));
        assertInstanceOf(DateParseException.class, ex,
                "Should be DateParseException, not " + ex.getClass().getSimpleName());
    }

    // --- Sanity: valid edge-case offsets still parse correctly ---

    @Test
    void max_valid_positive_offset_parses() {
        // +18:00 is the maximum valid ZoneOffset
        var result = OmniDateParser.toZonedDateTime("2024-01-15T12:00:00+1800");
        assertEquals(18, result.getOffset().getTotalSeconds() / 3600);
    }

    @Test
    void max_valid_negative_offset_parses() {
        var result = OmniDateParser.toZonedDateTime("2024-01-15T12:00:00-1800");
        assertEquals(-18, result.getOffset().getTotalSeconds() / 3600);
    }
}
