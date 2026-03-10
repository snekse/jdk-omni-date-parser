package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RFC 850 — the obsolete HTTP date format.
 *
 * <p><b>RFC 850</b> (1983, obsoleted by RFC 1036) defined a date format used in early Usenet
 * and HTTP. It uses full weekday names (not abbreviated), dash-separated date components with
 * a 3-letter month abbreviation, and a 2-digit year. HTTP/1.1 (RFC 2616) required servers to
 * accept this format for backwards compatibility, even though RFC 1123 format is preferred.
 *
 * <p>The key differences from RFC 2822 / RFC 1123:
 * <ul>
 *   <li>Full weekday name ({@code Sunday} vs {@code Sun})</li>
 *   <li>Dash-separated date ({@code 06-Nov-94} vs {@code 06 Nov 1994})</li>
 *   <li>2-digit year ({@code 94} vs {@code 1994}) — resolved via {@code pivotYear} config</li>
 * </ul>
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code Sunday, 06-Nov-94 08:49:37 GMT}</li>
 *   <li>{@code Monday, 02-Jan-06 15:04:05 MST}</li>
 * </ul>
 *
 * @see Rfc2822Test
 */
class Rfc850Test {

    @Test
    void full_weekday_dash_mon_2digit_year_gmt() {
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Sunday, 06-Nov-94 08:49:37 GMT");
        assertAll(
                () -> assertEquals(1994, result.getYear()),
                () -> assertEquals(11, result.getMonthValue()),
                () -> assertEquals(6, result.getDayOfMonth()),
                () -> assertEquals(8, result.getHour()),
                () -> assertEquals(49, result.getMinute()),
                () -> assertEquals(37, result.getSecond()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void full_weekday_dash_mon_2digit_year_mst() {
        // Unique vs GMT test: timezone is MST → America/Denver
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Monday, 02-Jan-06 15:04:05 MST");
        assertAll(
                () -> assertEquals(2006, result.getYear()),
                () -> assertEquals(1, result.getMonthValue()),
                () -> assertEquals(2, result.getDayOfMonth()),
                () -> assertEquals(15, result.getHour()),
                () -> assertEquals(4, result.getMinute()),
                () -> assertEquals(5, result.getSecond()),
                () -> assertEquals(ZoneId.of("America/Denver"), result.getZone())
        );
    }

    @Test
    void pivot_year_boundary_70_maps_to_2070() {
        // 70 ≤ pivot (default 70) → 2000+70
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Wednesday, 01-Jan-70 00:00:00 GMT");
        assertEquals(2070, result.getYear());
    }

    @Test
    void pivot_year_boundary_71_maps_to_1971() {
        // 71 > pivot (default 70) → 1900+71
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Friday, 01-Jan-71 00:00:00 GMT");
        assertEquals(1971, result.getYear());
    }

    @Test
    void custom_pivot_year() {
        // pivot=50: 55 > 50 → 1955
        var config = OmniDateParserConfig.builder().pivotYear(50).build();
        var parser = new OmniDateParser(config);
        ZonedDateTime result = parser.parseZonedDateTime("Sunday, 06-Nov-55 08:49:37 GMT");
        assertAll(
                () -> assertEquals(1955, result.getYear()),
                () -> assertEquals(11, result.getMonthValue()),
                () -> assertEquals(6, result.getDayOfMonth())
        );
    }

    @Test
    void custom_pivot_year_below_boundary() {
        // pivot=50: 50 ≤ 50 → 2050
        var config = OmniDateParserConfig.builder().pivotYear(50).build();
        var parser = new OmniDateParser(config);
        ZonedDateTime result = parser.parseZonedDateTime("Sunday, 01-Jan-50 00:00:00 GMT");
        assertEquals(2050, result.getYear());
    }

    @Test
    void date_only_no_time() {
        // RFC 850 with weekday but no time section
        ZonedDateTime result = OmniDateParser.toZonedDateTime("Sunday, 06-Nov-94");
        assertAll(
                () -> assertEquals(1994, result.getYear()),
                () -> assertEquals(11, result.getMonthValue()),
                () -> assertEquals(6, result.getDayOfMonth()),
                () -> assertEquals(0, result.getHour()),
                () -> assertEquals(0, result.getMinute()),
                () -> assertEquals(0, result.getSecond())
        );
    }
}
