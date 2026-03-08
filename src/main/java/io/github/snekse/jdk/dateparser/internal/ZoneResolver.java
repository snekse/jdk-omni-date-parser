package io.github.snekse.jdk.dateparser.internal;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Maps named timezone abbreviations and offset strings to ZoneOffset/ZoneId.
 * Phase 1: handles Z, UTC, GMT only.
 * Phase 2: full TZ abbreviation list.
 */
public class ZoneResolver {

    private static final Map<String, ZoneId> KNOWN = Map.of(
            "Z",   ZoneOffset.UTC,
            "UTC", ZoneOffset.UTC,
            "GMT", ZoneOffset.UTC
    );

    /** Returns the ZoneId for a known abbreviation, or null if not recognized. */
    public static ZoneId resolve(String abbr) {
        return KNOWN.get(abbr.toUpperCase());
    }

    /**
     * Parses a numeric offset string.
     *
     * Accepts:
     *   +HHMM, -HHMM   (4-digit, no colon)
     *   +HH:MM, -HH:MM  (with colon)
     *
     * The sign character ('+' or '-') must be passed separately as signChar.
     * digits contains only the digit portion (e.g. "0500" or "05:30").
     */
    public static ZoneOffset parseNumericOffset(char signChar, String digits) {
        String clean = digits.replace(":", "");
        if (clean.length() < 3) {
            // e.g. "00" → treat as +00:00
            int totalSeconds = Integer.parseInt(clean) * 3600;
            return ZoneOffset.ofTotalSeconds(signChar == '-' ? -totalSeconds : totalSeconds);
        }
        int hours = Integer.parseInt(clean.substring(0, 2));
        int minutes = Integer.parseInt(clean.substring(2, 4));
        int totalSeconds = hours * 3600 + minutes * 60;
        return ZoneOffset.ofTotalSeconds(signChar == '-' ? -totalSeconds : totalSeconds);
    }
}
