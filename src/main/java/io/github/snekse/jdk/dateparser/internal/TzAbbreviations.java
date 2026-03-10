package io.github.snekse.jdk.dateparser.internal;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lookup table mapping timezone abbreviation strings to ZoneId.
 * Named abbreviations return ZoneId (not ZoneOffset) so DST-aware zones are represented correctly.
 * CST maps to America/Chicago (US Central Standard Time), not China Standard Time.
 * Seeded from ZoneId.SHORT_IDS (Java legacy 3-letter TZ codes) with real-world overrides layered on top.
 * <p>
 * Eventually we would like to incorporate in a TZ database and maybe some optional configs.
 * <a href="https://en.wikipedia.org/wiki/List_of_tz_database_time_zones">List of TZs</a>
 * <a href="https://en.wikipedia.org/wiki/Tz_database">TZ Database</a>
 */
final class TzAbbreviations {

    private static final Map<String, ZoneId> MAP;
    static {
        Map<String, ZoneId> m = new HashMap<>(64);
        // Seed with Java's legacy SHORT_IDS — covers ACT, AET, AGT, BET, CNT,
        // CTT, ECT, IET, MIT, NET, PLT, PNT, PRT, VST and others.
        ZoneId.SHORT_IDS.forEach((k, v) -> m.put(k, ZoneId.of(v)));
        // Our custom/override entries — these win on any conflict with SHORT_IDS
        m.put("UTC",  ZoneOffset.UTC);
        m.put("GMT",  ZoneOffset.UTC);
        m.put("Z",    ZoneOffset.UTC);
        // North America — common real-world abbreviations (override SHORT_IDS where they differ)
        m.put("EST",  ZoneId.of("America/New_York"));   // Eastern Standard Time; SHORT_IDS: Panama
        m.put("EDT",  ZoneId.of("America/New_York"));
        m.put("CST",  ZoneId.of("America/Chicago"));    // US Central; not China Standard Time
        m.put("CDT",  ZoneId.of("America/Chicago"));
        m.put("MST",  ZoneId.of("America/Denver"));     // Mountain Standard Time
        m.put("MDT",  ZoneId.of("America/Denver"));
        m.put("PST",  ZoneId.of("America/Los_Angeles"));
        m.put("PDT",  ZoneId.of("America/Los_Angeles"));
        m.put("AST",  ZoneId.of("America/Halifax"));    // Atlantic Standard Time; SHORT_IDS: Anchorage
        m.put("ADT",  ZoneId.of("America/Halifax"));
        m.put("NST",  ZoneId.of("America/St_Johns"));   // Newfoundland Standard Time; SHORT_IDS: Auckland
        m.put("NDT",  ZoneId.of("America/St_Johns"));
        m.put("AKST", ZoneId.of("America/Anchorage"));
        m.put("AKDT", ZoneId.of("America/Anchorage"));
        m.put("HST",  ZoneId.of("Pacific/Honolulu"));
        m.put("BRT",  ZoneId.of("America/Sao_Paulo"));
        m.put("ART",  ZoneId.of("America/Argentina/Buenos_Aires")); // Argentina Time; SHORT_IDS: Cairo
        // Europe
        m.put("WET",  ZoneId.of("Europe/Lisbon"));
        m.put("WEST", ZoneId.of("Europe/Lisbon"));
        m.put("BST",  ZoneId.of("Europe/London"));      // British Summer Time; SHORT_IDS: Dhaka
        m.put("CET",  ZoneId.of("Europe/Paris"));
        m.put("CEST", ZoneId.of("Europe/Paris"));
        m.put("EET",  ZoneId.of("Europe/Helsinki"));
        m.put("EEST", ZoneId.of("Europe/Helsinki"));
        m.put("MSK",  ZoneId.of("Europe/Moscow"));
        m.put("TRT",  ZoneId.of("Europe/Istanbul"));
        // Asia
        m.put("IST",  ZoneId.of("Asia/Kolkata"));
        m.put("JST",  ZoneId.of("Asia/Tokyo"));
        m.put("HKT",  ZoneId.of("Asia/Hong_Kong"));
        m.put("KST",  ZoneId.of("Asia/Seoul"));
        m.put("SGT",  ZoneId.of("Asia/Singapore"));
        m.put("ICT",  ZoneId.of("Asia/Bangkok"));
        m.put("PHT",  ZoneId.of("Asia/Manila"));
        // Oceania
        m.put("NZDT", ZoneId.of("Pacific/Auckland"));
        m.put("NZST", ZoneId.of("Pacific/Auckland"));
        m.put("AEST", ZoneId.of("Australia/Sydney"));
        m.put("AEDT", ZoneId.of("Australia/Sydney"));
        m.put("AWST", ZoneId.of("Australia/Perth"));
        m.put("ACST", ZoneId.of("Australia/Darwin"));
        m.put("ACDT", ZoneId.of("Australia/Adelaide"));
        // Africa
        m.put("EAT",  ZoneId.of("Africa/Nairobi"));
        m.put("WAT",  ZoneId.of("Africa/Lagos"));
        m.put("CAT",  ZoneId.of("Africa/Harare"));
        m.put("SAST", ZoneId.of("Africa/Johannesburg"));
        // Pacific
        m.put("SST",  ZoneId.of("Pacific/Pago_Pago")); // Samoa Standard Time; SHORT_IDS: Guadalcanal
        m.put("WST",  ZoneId.of("Pacific/Apia"));
        MAP = Collections.unmodifiableMap(m);
    }

    static Optional<ZoneId> resolve(String abbr) {
        return Optional.ofNullable(MAP.get(abbr.toUpperCase()));
    }

    private TzAbbreviations() {}
}
