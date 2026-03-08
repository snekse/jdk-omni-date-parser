package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DateOrder-driven ambiguity resolution on three-part numeric dates.
 */
class AmbiguityResolutionTest {

    private static OmniDateParser parser(DateOrder order) {
        return new OmniDateParser(OmniDateParserConfig.builder().dateOrder(order).build());
    }

    // --- Ambiguous input: 10/11/12 ---

    @Test
    void ambiguous_mdy_gives_month_day_year() {
        ZonedDateTime result = parser(DateOrder.MDY).parseZonedDateTime("10/11/12");
        assertAll(
                () -> assertEquals(10,   result.getMonthValue()),
                () -> assertEquals(11,   result.getDayOfMonth()),
                () -> assertEquals(2012, result.getYear())
        );
    }

    @Test
    void ambiguous_dmy_gives_day_month_year() {
        ZonedDateTime result = parser(DateOrder.DMY).parseZonedDateTime("10/11/12");
        assertAll(
                () -> assertEquals(10,   result.getDayOfMonth()),
                () -> assertEquals(11,   result.getMonthValue()),
                () -> assertEquals(2012, result.getYear())
        );
    }

    @Test
    void ambiguous_ymd_gives_year_month_day() {
        ZonedDateTime result = parser(DateOrder.YMD).parseZonedDateTime("10/11/12");
        assertAll(
                () -> assertEquals(2010, result.getYear()),
                () -> assertEquals(11,   result.getMonthValue()),
                () -> assertEquals(12,   result.getDayOfMonth())
        );
    }

    // --- Unambiguous: 31/12/1999 — day must be 31 regardless of DateOrder ---

    @Test
    void unambiguous_31_12_1999_mdy() {
        ZonedDateTime result = parser(DateOrder.MDY).parseZonedDateTime("31/12/1999");
        assertAll(
                () -> assertEquals(31,   result.getDayOfMonth()),
                () -> assertEquals(12,   result.getMonthValue()),
                () -> assertEquals(1999, result.getYear())
        );
    }

    @Test
    void unambiguous_31_12_1999_dmy() {
        ZonedDateTime result = parser(DateOrder.DMY).parseZonedDateTime("31/12/1999");
        assertAll(
                () -> assertEquals(31,   result.getDayOfMonth()),
                () -> assertEquals(12,   result.getMonthValue()),
                () -> assertEquals(1999, result.getYear())
        );
    }

    // --- Heuristic: b > 12, so b must be day ---

    @Test
    void b_greater_than_12_is_day() {
        // 01/31/1999 → month=1, day=31, year=1999 (MDY default, but b>12 forces it)
        ZonedDateTime result = OmniDateParser.toZonedDateTime("01/31/1999 12:00 PM");
        assertAll(
                () -> assertEquals(1,    result.getMonthValue()),
                () -> assertEquals(31,   result.getDayOfMonth()),
                () -> assertEquals(1999, result.getYear())
        );
    }

    // --- 2-digit year pivot ---

    @Test
    void two_digit_year_at_pivot_gives_2000s() {
        // Default pivotYear=70; year=70 → 2070
        OmniDateParser p = new OmniDateParser(OmniDateParserConfig.defaults());
        ZonedDateTime result = p.parseZonedDateTime("01/01/70");
        assertEquals(2070, result.getYear());
    }

    @Test
    void two_digit_year_above_pivot_gives_1900s() {
        // Default pivotYear=70; year=71 → 1971
        OmniDateParser p = new OmniDateParser(OmniDateParserConfig.defaults());
        ZonedDateTime result = p.parseZonedDateTime("01/01/71");
        assertEquals(1971, result.getYear());
    }
}
