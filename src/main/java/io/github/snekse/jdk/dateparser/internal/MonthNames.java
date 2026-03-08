package io.github.snekse.jdk.dateparser.internal;

import java.util.Map;
import java.util.OptionalInt;

/**
 * Maps English month names (full and abbreviated) to their month number (1–12).
 * Lookup is case-insensitive.
 */
final class MonthNames {

    private static final Map<String, Integer> MAP = Map.ofEntries(
            Map.entry("january",   1), Map.entry("jan",  1),
            Map.entry("february",  2), Map.entry("feb",  2),
            Map.entry("march",     3), Map.entry("mar",  3),
            Map.entry("april",     4), Map.entry("apr",  4),
            Map.entry("may",       5),
            Map.entry("june",      6), Map.entry("jun",  6),
            Map.entry("july",      7), Map.entry("jul",  7),
            Map.entry("august",    8), Map.entry("aug",  8),
            Map.entry("september", 9), Map.entry("sep",  9), Map.entry("sept", 9),
            Map.entry("october",  10), Map.entry("oct", 10),
            Map.entry("november", 11), Map.entry("nov", 11),
            Map.entry("december", 12), Map.entry("dec", 12)
    );

    static OptionalInt resolve(String token) {
        Integer v = MAP.get(token.toLowerCase());
        return v != null ? OptionalInt.of(v) : OptionalInt.empty();
    }

    private MonthNames() {}
}
