package io.github.snekse.jdk.dateparser.internal;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Maps named timezone abbreviations and offset strings to ZoneOffset/ZoneId.
 */
public class ZoneResolver {

    /** Returns the ZoneId for a known abbreviation, or null if not recognized. */
    public static ZoneId resolve(String abbr) {
        return TzAbbreviations.resolve(abbr).orElse(null);
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
        int len = clean.length();
        if (len == 3 || len > 4) {
            throw new IllegalArgumentException("invalid timezone offset: " + signChar + digits);
        }
        int totalSeconds;
        if (len <= 2) {
            // e.g. "00" or "5" → treat as hours only
            totalSeconds = Integer.parseInt(clean) * 3600;
        } else {
            // len == 4: HHMM
            int hours = Integer.parseInt(clean.substring(0, 2));
            int minutes = Integer.parseInt(clean.substring(2, 4));
            totalSeconds = hours * 3600 + minutes * 60;
        }
        try {
            return ZoneOffset.ofTotalSeconds(signChar == '-' ? -totalSeconds : totalSeconds);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("timezone offset out of range: " + signChar + digits, e);
        }
    }
}
