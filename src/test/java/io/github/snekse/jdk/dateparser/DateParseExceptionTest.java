package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DateParseExceptionTest {

    @Test
    void null_input_throws_with_null_message() {
        DateParseException ex = assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime(null));
        assertTrue(ex.getMessage().contains("null"), "Message should mention null");
    }

    @Test
    void empty_input_throws() {
        DateParseException ex = assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime(""));
        assertNotNull(ex.getMessage());
    }

    @Test
    void blank_input_throws() {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("   "));
    }

    @Test
    void letters_only_throws_with_descriptive_message() {
        DateParseException ex = assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("not-a-date"));
        assertNotNull(ex.getMessage());
        assertFalse(ex.getMessage().isBlank(), "Exception message should not be blank");
    }

    @Test
    void truncated_date_throws() {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("1999-01"));
    }

    @Test
    void exception_message_format_contains_input() {
        DateParseException ex = assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime("bogus-value"));
        assertTrue(ex.getMessage().contains("bogus-value"), "Message should contain the offending input");
    }
}
