package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for apostrophe-prefixed 2-digit years — a casual convention where a leading
 * apostrophe indicates a shortened year (e.g. {@code '70} for 1970).
 *
 * <p>This convention appears with spelled-out or abbreviated month names where the year
 * comes last. The apostrophe is consumed and the 2-digit year is expanded using the
 * configured {@code pivotYear} (default 70: years <= 70 map to 20xx, years > 70 to 19xx).
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code oct 7, '70} — abbreviated month, MDY order</li>
 *   <li>{@code October 7, '70} — full month name, MDY order</li>
 *   <li>{@code 7 oct '70} — abbreviated month, DMY order</li>
 * </ul>
 */
class ApostropheYearTest {

    @Test
    void abbreviated_month_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("oct 7, '70");
        assertAll(
                () -> assertEquals(2070, result.getYear()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }

    @Test
    void full_month_name_mdy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("October 7, '70");
        assertAll(
                () -> assertEquals(2070, result.getYear()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }

    @Test
    void abbreviated_month_dmy() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("7 oct '70");
        assertAll(
                () -> assertEquals(2070, result.getYear()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }

    @Test
    void pivot_year_boundary_70_maps_to_2070() {
        // default pivot=70: '70 ≤ 70 → 2070
        ZonedDateTime result = OmniDateParser.toZonedDateTime("oct 7, '70");
        assertEquals(2070, result.getYear());
    }

    @Test
    void pivot_year_boundary_71_maps_to_1971() {
        // default pivot=70: '71 > 70 → 1971
        ZonedDateTime result = OmniDateParser.toZonedDateTime("oct 7, '71");
        assertEquals(1971, result.getYear());
    }

    @Test
    void custom_pivot_year() {
        // pivot=50: '55 > 50 → 1955
        var config = OmniDateParserConfig.builder().pivotYear(50).build();
        var parser = new OmniDateParser(config);
        ZonedDateTime result = parser.parseZonedDateTime("October 7, '55");
        assertAll(
                () -> assertEquals(1955, result.getYear()),
                () -> assertEquals(10, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth())
        );
    }

    @Test
    void custom_pivot_year_below_boundary() {
        // pivot=50: '50 ≤ 50 → 2050
        var config = OmniDateParserConfig.builder().pivotYear(50).build();
        var parser = new OmniDateParser(config);
        ZonedDateTime result = parser.parseZonedDateTime("October 7, '50");
        assertEquals(2050, result.getYear());
    }

    @Test
    void with_time_suffix() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("oct 7, '70 12:30:00");
        assertAll(
                () -> assertEquals(2070, result.getYear()),
                () -> assertEquals(12, result.getHour()),
                () -> assertEquals(30, result.getMinute())
        );
    }

    @Test
    void full_month_dmy_with_time() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("7 December '99 15:00:00");
        assertAll(
                () -> assertEquals(1999, result.getYear()),
                () -> assertEquals(12, result.getMonthValue()),
                () -> assertEquals(7, result.getDayOfMonth()),
                () -> assertEquals(15, result.getHour())
        );
    }
}
