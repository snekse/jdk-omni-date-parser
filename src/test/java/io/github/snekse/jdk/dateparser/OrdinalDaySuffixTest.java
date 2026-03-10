package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ordinal day suffixes — {@code st}, {@code nd}, {@code rd}, {@code th} appended
 * to the day number in spelled-month formats.
 *
 * <p>Ordinal suffixes are purely cosmetic and carry no semantic information; the parser
 * strips them and uses only the preceding digit(s). They can appear in both MDY and DMY
 * orderings with full or abbreviated month names.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code October 7th, 1970} — full month, MDY with "th"</li>
 *   <li>{@code January 1st, 1999} — full month, MDY with "st"</li>
 *   <li>{@code March 2nd, 1999} — full month, MDY with "nd"</li>
 *   <li>{@code June 3rd, 1999} — full month, MDY with "rd"</li>
 *   <li>{@code 7th October 1970} — DMY with ordinal suffix</li>
 * </ul>
 */
class OrdinalDaySuffixTest {

    @Test
    void th_suffix_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("October 7th, 1970");
        assertAll(
                () -> assertEquals(1970, result.getYear()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }

    @Test
    void st_suffix_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 1st, 1999");
        assertEquals(1, result.getDayOfMonth());
    }

    @Test
    void nd_suffix_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("March 2nd, 1999");
        assertEquals(2, result.getDayOfMonth());
    }

    @Test
    void rd_suffix_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("June 3rd, 1999");
        assertEquals(3, result.getDayOfMonth());
    }

    @Test
    void th_suffix_dmy() {
        // Unique: DMY order — suffix appears before month name
        ZonedDateTime result = OmniDateParser.toZonedDateTime("7th October 1970");
        assertAll(
                () -> assertEquals(7, result.getDayOfMonth()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(1970, result.getYear())
        );
    }

    @Test
    void th_suffix_abbreviated_month_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Oct 7th, 1970");
        assertAll(
                () -> assertEquals(7, result.getDayOfMonth()),
                () -> assertEquals(10, result.getMonthValue())
        );
    }

    @Test
    void th_suffix_abbreviated_month_dmy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("7th Oct 1970");
        assertAll(
                () -> assertEquals(7, result.getDayOfMonth()),
                () -> assertEquals(10, result.getMonthValue())
        );
    }

    @Test
    void with_time_suffix() {
        // Unique: ordinal suffix + time — ensures suffix doesn't consume time tokens
        ZonedDateTime result = OmniDateParser.toZonedDateTime("January 1st, 1999 12:00 PM");
        assertAll(
                () -> assertEquals(1, result.getDayOfMonth()),
                () -> assertEquals(12, result.getHour())
        );
    }

    @Test
    void with_apostrophe_year() {
        // Unique: ordinal suffix + apostrophe year — two suffix features combined
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Oct 7th, '70");
        assertAll(
                () -> assertEquals(7, result.getDayOfMonth()),
                () -> assertEquals(2070, result.getYear())
        );
    }
}
