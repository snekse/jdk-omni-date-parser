package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for period-suffixed month abbreviations — a trailing dot after a shortened month
 * name (e.g. {@code Oct.}, {@code Jan.}).
 *
 * <p>This convention is standard in many style guides (AP, Chicago) and common in printed
 * dates. The parser consumes the trailing dot as punctuation belonging to the abbreviation,
 * preventing it from being misinterpreted as a date component separator.
 *
 * <p>Period-suffix can appear in both MDY and DMY orderings and can combine with other
 * features like apostrophe-prefixed 2-digit years and time suffixes.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code oct. 7, 1970} — abbreviated month with period, MDY</li>
 *   <li>{@code Oct. 7, '70} — abbreviated month with period + apostrophe year</li>
 *   <li>{@code Jan. 31, 1999 12:00 PM} — with time suffix</li>
 *   <li>{@code 7 Oct. 1970} — DMY with period-suffix month</li>
 * </ul>
 */
class MonthPeriodSuffixTest {

    @Test
    void period_suffix_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("oct. 7, 1970");
        assertAll(
                () -> assertEquals(1970, result.getYear()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }

    @Test
    void period_suffix_with_apostrophe_year() {
        // Unique: period-suffix month + apostrophe year — two abbreviation conventions combined
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Oct. 7, '70");
        assertAll(
                () -> assertEquals(2070, result.getYear()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }

    @Test
    void period_suffix_with_time() {
        // Unique: period-suffix month + time — ensures DOT consumption doesn't affect time parsing
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Jan. 31, 1999 12:00 PM");
        assertAll(
                () -> assertEquals(1, result.getMonthValue()),
                () -> assertEquals(31, result.getDayOfMonth()),
                () -> assertEquals(12, result.getHour())
        );
    }

    @Test
    void period_suffix_dmy() {
        // Unique: DMY order — period after month in DD Mon. YYYY position
        ZonedDateTime result = OmniDateParser.toZonedDateTime("7 Oct. 1970");
        assertAll(
                () -> assertEquals(7, result.getDayOfMonth()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(1970, result.getYear())
        );
    }

    @Test
    void period_suffix_dmy_with_apostrophe_year() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("7 Oct. '70");
        assertAll(
                () -> assertEquals(7, result.getDayOfMonth()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(2070, result.getYear())
        );
    }

    @Test
    void full_month_name_no_period_still_works() {
        // Sanity check: full month name (no period) is not affected
        ZonedDateTime result = OmniDateParser.toZonedDateTime("October 7, 1970");
        assertAll(
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }
}
