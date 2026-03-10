package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ISO 8601 week dates — an alternative calendar representation within ISO 8601.
 *
 * <p><b>ISO 8601 week dates</b> express a date using the ISO week-numbering year, week number
 * (01–53), and day-of-week (1=Monday through 7=Sunday). This system is widely used in business
 * and manufacturing contexts (e.g. "delivery in W03") and in some European countries for
 * scheduling. Week 1 is defined as the week containing the year's first Thursday, which means
 * ISO week dates can map to dates in the previous or next calendar year.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code 2004-W53-6} — extended format (Saturday of week 53, 2004 = 2005-01-01)</li>
 *   <li>{@code 2004W536} — basic format (no dashes)</li>
 *   <li>{@code 2004-W53} — week only, day-of-week defaults to Monday</li>
 *   <li>{@code 2004-W01-1T00:00:00Z} — with time and timezone suffix</li>
 * </ul>
 *
 * @see Iso8601Test
 * @see IsoOrdinalDateTest
 */
class IsoWeekDateTest {

    /** Extended format: {@code YYYY-Www-D} with explicit day-of-week. */
    @Test
    void extended_format_with_day() {
        // 2004-W53-6 = Saturday of week 53, 2004 → 2005-01-01
        LocalDate result = OmniDateParser.toLocalDate("2004-W53-6");
        assertEquals(LocalDate.of(2005, 1, 1), result);
    }

    @Test
    void basic_format_with_day() {
        // Unique: compact form (no dashes) produces same result as extended
        LocalDate result = OmniDateParser.toLocalDate("2004W536");
        assertEquals(LocalDate.of(2005, 1, 1), result);
    }

    @Test
    void week_only_defaults_to_monday() {
        // Unique: no day-of-week → defaults to Monday (day 1)
        LocalDate result = OmniDateParser.toLocalDate("2004-W53");
        assertEquals(LocalDate.of(2004, 12, 27), result);
    }

    @Test
    void week_1_day_1() {
        // Unique: ISO week 1 can map to previous calendar year
        LocalDate result = OmniDateParser.toLocalDate("2004-W01-1");
        assertEquals(LocalDate.of(2003, 12, 29), result);
    }

    @Test
    void with_time_and_zone() {
        // Unique: proves time+zone suffix parsing works with week dates
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2004-W01-1T00:00:00Z");
        assertAll(
                () -> assertEquals(2003, result.getYear()),
                () -> assertEquals(12, result.getMonthValue()),
                () -> assertEquals(29, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour()),
                () -> assertEquals(0, result.getMinute()),
                () -> assertEquals(0, result.getSecond()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void with_time_and_positive_offset() {
        // Unique: non-UTC offset with week date + time
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2004-W53-6T12:30:00+05:30");
        assertAll(
                () -> assertEquals(LocalDate.of(2005, 1, 1), result.toLocalDate()),
                () -> assertEquals(12, result.getHour()),
                () -> assertEquals(30, result.getMinute()),
                () -> assertEquals(ZoneOffset.of("+05:30"), result.getOffset())
        );
    }

    @Test
    void day_7_sunday() {
        // Unique: day-of-week 7 = Sunday (edge of valid range)
        LocalDate result = OmniDateParser.toLocalDate("2004-W01-7");
        // Sunday of ISO week 1, 2004 = 2004-01-04
        assertEquals(LocalDate.of(2004, 1, 4), result);
    }

    @Test
    void invalid_week_54_throws() {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toLocalDate("2004-W54-1"));
    }

    @Test
    void invalid_week_0_throws() {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toLocalDate("2004-W00-1"));
    }
}
