package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for TzAbbreviations zone mappings.
 *
 * <p>Covers three categories:
 * <ul>
 *   <li>Conflict overrides — codes present in {@code ZoneId.SHORT_IDS} but intentionally mapped
 *       to a different (more real-world correct) zone (e.g. EST → New_York, not Panama)</li>
 *   <li>SHORT_IDS pass-throughs — codes seeded from {@code ZoneId.SHORT_IDS} without override
 *       (e.g. IET → America/Indiana/Indianapolis)</li>
 *   <li>Custom additions — codes not in SHORT_IDS at all (UTC/GMT/Z aliases, DST variants,
 *       regional codes like HKT, SAST, etc.)</li>
 * </ul>
 */
class TzAbbreviationsTest {

    private static final String D = "1999-06-15 12:00:00 "; // date prefix; June so DST zones are in summer offset

    // -------------------------------------------------------------------------
    // UTC / GMT / Z aliases — these are our own additions, not in SHORT_IDS
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} zone id = \"{1}\"")
    @CsvSource({
        "UTC, Z",
        "GMT, Z",
    })
    void utc_aliases_resolve_to_utc_offset(String abbr, String expectedZoneId) {
        var result = OmniDateParser.toZonedDateTime(D + abbr);
        assertEquals(expectedZoneId, result.getZone().getId());
    }

    // ISO 8601 trailing Z tested separately because the lexer tokenizes it differently
    void z_suffix_in_iso_resolves_to_utc() {
        var result = OmniDateParser.toZonedDateTime("1999-06-15T12:00:00Z");
        assertEquals("Z", result.getZone().getId());
    }

    // -------------------------------------------------------------------------
    // Conflict overrides — our mapping wins over ZoneId.SHORT_IDS
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} -> {1}  (not SHORT_IDS: {2})")
    @CsvSource({
        // abbr,  our zone,                        SHORT_IDS zone (comment only)
        "EST,  America/New_York,                 America/Panama",
        "AST,  America/Halifax,                  America/Anchorage",
        "BST,  Europe/London,                    Asia/Dhaka",
        "ART,  America/Argentina/Buenos_Aires,   Africa/Cairo",
        "NST,  America/St_Johns,                 Pacific/Auckland",
        "MST,  America/Denver,                   America/Phoenix",
        "SST,  Pacific/Pago_Pago,                Pacific/Guadalcanal",
    })
    void short_ids_conflict_uses_our_override(String abbr, String expectedZone, String ignoredShortIdsZone) {
        var result = OmniDateParser.toZonedDateTime(D + abbr);
        assertEquals(expectedZone.strip(), result.getZone().getId());
    }

    // -------------------------------------------------------------------------
    // SHORT_IDS pass-throughs — seeded from SHORT_IDS, no custom override
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} -> {1}  (SHORT_IDS pass-through)")
    @CsvSource({
        "IET, America/Indiana/Indianapolis",
        "PLT, Asia/Karachi",
        "NET, Asia/Yerevan",
        "CTT, Asia/Shanghai",
        "ACT, Australia/Darwin",
        "VST, Asia/Ho_Chi_Minh",
        "PNT, America/Phoenix",
        "PRT, America/Puerto_Rico",
    })
    void short_ids_pass_through_resolves_correctly(String abbr, String expectedZone) {
        var result = OmniDateParser.toZonedDateTime(D + abbr);
        assertEquals(expectedZone, result.getZone().getId());
    }

    // -------------------------------------------------------------------------
    // Custom additions — not in SHORT_IDS; our own entries
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} -> {1}  (custom addition)")
    @CsvSource({
        // DST variants of North American zones
        "EDT,  America/New_York",
        "CDT,  America/Chicago",
        "MDT,  America/Denver",
        "PDT,  America/Los_Angeles",
        "ADT,  America/Halifax",
        "NDT,  America/St_Johns",
        "AKST, America/Anchorage",
        "AKDT, America/Anchorage",
        // Brazil / South America
        "BRT,  America/Sao_Paulo",
        // Europe DST variants and extras
        "WET,  Europe/Lisbon",
        "WEST, Europe/Lisbon",
        "CEST, Europe/Paris",
        "EEST, Europe/Helsinki",
        "MSK,  Europe/Moscow",
        "TRT,  Europe/Istanbul",
        // Asia regional
        "HKT,  Asia/Hong_Kong",
        "KST,  Asia/Seoul",
        "SGT,  Asia/Singapore",
        "ICT,  Asia/Bangkok",
        "PHT,  Asia/Manila",
        // Oceania
        "NZDT, Pacific/Auckland",
        "NZST, Pacific/Auckland",
        "AEDT, Australia/Sydney",
        "AWST, Australia/Perth",
        "ACDT, Australia/Adelaide",
        // Africa
        "WAT,  Africa/Lagos",
        "SAST, Africa/Johannesburg",
        // Pacific
        "WST,  Pacific/Apia",
    })
    void custom_addition_resolves_correctly(String abbr, String expectedZone) {
        var result = OmniDateParser.toZonedDateTime(D + abbr);
        assertEquals(expectedZone.strip(), result.getZone().getId());
    }
}
