package io.github.snekse.jdk.dateparser.internal;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup table mapping timezone abbreviation strings to ZoneId.
 * Named abbreviations return ZoneId (not ZoneOffset) so DST-aware zones are represented correctly.
 * CST maps to America/Chicago (US Central Standard Time), not China Standard Time.
 */
final class TzAbbreviations {

    private static final Map<String, ZoneId> MAP = Map.ofEntries(
            Map.entry("UTC",  ZoneOffset.UTC),
            Map.entry("GMT",  ZoneOffset.UTC),
            Map.entry("Z",    ZoneOffset.UTC),
            Map.entry("EST",  ZoneId.of("America/New_York")),
            Map.entry("EDT",  ZoneId.of("America/New_York")),
            Map.entry("CST",  ZoneId.of("America/Chicago")),   // US Central; not China Standard Time
            Map.entry("CDT",  ZoneId.of("America/Chicago")),
            Map.entry("MST",  ZoneId.of("America/Denver")),
            Map.entry("MDT",  ZoneId.of("America/Denver")),
            Map.entry("PST",  ZoneId.of("America/Los_Angeles")),
            Map.entry("PDT",  ZoneId.of("America/Los_Angeles")),
            Map.entry("AST",  ZoneId.of("America/Halifax")),
            Map.entry("ADT",  ZoneId.of("America/Halifax")),
            Map.entry("NST",  ZoneId.of("America/St_Johns")),
            Map.entry("NDT",  ZoneId.of("America/St_Johns")),
            Map.entry("AKST", ZoneId.of("America/Anchorage")),
            Map.entry("AKDT", ZoneId.of("America/Anchorage")),
            Map.entry("HST",  ZoneId.of("Pacific/Honolulu")),
            Map.entry("BRT",  ZoneId.of("America/Sao_Paulo")),
            Map.entry("ART",  ZoneId.of("America/Argentina/Buenos_Aires")),
            Map.entry("WET",  ZoneId.of("Europe/Lisbon")),
            Map.entry("WEST", ZoneId.of("Europe/Lisbon")),
            Map.entry("BST",  ZoneId.of("Europe/London")),
            Map.entry("CET",  ZoneId.of("Europe/Paris")),
            Map.entry("CEST", ZoneId.of("Europe/Paris")),
            Map.entry("EET",  ZoneId.of("Europe/Helsinki")),
            Map.entry("EEST", ZoneId.of("Europe/Helsinki")),
            Map.entry("MSK",  ZoneId.of("Europe/Moscow")),
            Map.entry("TRT",  ZoneId.of("Europe/Istanbul")),
            Map.entry("IST",  ZoneId.of("Asia/Kolkata")),
            Map.entry("JST",  ZoneId.of("Asia/Tokyo")),
            Map.entry("HKT",  ZoneId.of("Asia/Hong_Kong")),
            Map.entry("KST",  ZoneId.of("Asia/Seoul")),
            Map.entry("SGT",  ZoneId.of("Asia/Singapore")),
            Map.entry("ICT",  ZoneId.of("Asia/Bangkok")),
            Map.entry("PHT",  ZoneId.of("Asia/Manila")),
            Map.entry("NZDT", ZoneId.of("Pacific/Auckland")),
            Map.entry("NZST", ZoneId.of("Pacific/Auckland")),
            Map.entry("AEST", ZoneId.of("Australia/Sydney")),
            Map.entry("AEDT", ZoneId.of("Australia/Sydney")),
            Map.entry("AWST", ZoneId.of("Australia/Perth")),
            Map.entry("ACST", ZoneId.of("Australia/Darwin")),
            Map.entry("ACDT", ZoneId.of("Australia/Adelaide")),
            Map.entry("EAT",  ZoneId.of("Africa/Nairobi")),
            Map.entry("WAT",  ZoneId.of("Africa/Lagos")),
            Map.entry("CAT",  ZoneId.of("Africa/Harare")),
            Map.entry("SAST", ZoneId.of("Africa/Johannesburg")),
            Map.entry("SST",  ZoneId.of("Pacific/Pago_Pago")),
            Map.entry("WST",  ZoneId.of("Pacific/Apia"))
    );

    static Optional<ZoneId> resolve(String abbr) {
        return Optional.ofNullable(MAP.get(abbr.toUpperCase()));
    }

    private TzAbbreviations() {}
}
