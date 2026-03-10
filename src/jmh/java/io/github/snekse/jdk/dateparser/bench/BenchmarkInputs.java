package io.github.snekse.jdk.dateparser.bench;

import java.util.List;

/**
 * Representative mixed-format date strings used by all benchmarks.
 *
 * <p>{@link #CORE} — 21 inputs covering formats that a hand-crafted shotgun parser can handle
 * without special preprocessing. Used for apples-to-apples comparisons.
 *
 * <p>{@link #ALL} — 26 inputs; extends CORE with ordinal day suffixes, period-suffix month
 * abbreviations, and {@code @} date-time separator formats, all of which require additional
 * preprocessing in the shotgun approach.
 */
public final class BenchmarkInputs {

    private BenchmarkInputs() {}

    /**
     * Core input set (21 entries): excludes ordinal-suffix, period-suffix, and @ separator formats.
     * Used by the "reduced" shotgun benchmark for a fair baseline comparison.
     */
    public static final List<String> CORE = List.of(
        // ISO 8601
        "1999-01-01T00:00:00Z",
        "1999-01-01T12:00:00+00:00",
        "1999-01-01T00:00:00.000+05:30",
        "1999-12-31T23:59:59+01:00",
        // RFC 2822
        "Fri, 01 Jan 1999 23:59:00 +0000",
        "01 Jan 1999 00:00:00 GMT",
        // Western slash/dot
        "01/01/1999 12:00 PM PST",
        "31/12/1999 00:00 GMT",
        "31-12-1999 11:59 PM GMT",
        "1999/01/01 00:00 JST",
        // Spelled-out month
        "January 1, 1999 12:00 PM -0500",
        "31 December 1999 00:00:00 GMT",
        "Dec 31, 1999 00:00:00 UTC",
        "Friday, January 1, 1999 12:00:00 PM EST",
        // a.m./p.m.
        "January 1, 1999 at 11:59 p.m. PST",
        "January 31, 1999 12:00 p.m.",
        // Year-first
        "1999 January 1 00:00:00 UTC",
        // RFC 850 (obsolete HTTP format)
        "Sunday, 06-Nov-94 08:49:37 GMT",
        // YYYY-Mon-DD (ISO-style with spelled month)
        "2013-Feb-03",
        // No timezone (date only)
        "1999-01-31",
        "19990101"
    );

    /**
     * Full input set (28 entries): CORE plus ordinal-suffix, period-suffix, @ separator, and noon/midnight formats.
     * The shotgun approach requires extra preprocessing for these additions.
     */
    public static final List<String> ALL = List.of(
        // ISO 8601
        "1999-01-01T00:00:00Z",
        "1999-01-01T12:00:00+00:00",
        "1999-01-01T00:00:00.000+05:30",
        "1999-12-31T23:59:59+01:00",
        // RFC 2822
        "Fri, 01 Jan 1999 23:59:00 +0000",
        "01 Jan 1999 00:00:00 GMT",
        // Western slash/dot
        "01/01/1999 12:00 PM PST",
        "31/12/1999 00:00 GMT",
        "31-12-1999 11:59 PM GMT",
        "1999/01/01 00:00 JST",
        // Spelled-out month
        "January 1, 1999 12:00 PM -0500",
        "31 December 1999 00:00:00 GMT",
        "Dec 31, 1999 00:00:00 UTC",
        "Friday, January 1, 1999 12:00:00 PM EST",
        // a.m./p.m.
        "January 1, 1999 at 11:59 p.m. PST",
        "January 31, 1999 12:00 p.m.",
        // Year-first
        "1999 January 1 00:00:00 UTC",
        // RFC 850 (obsolete HTTP format)
        "Sunday, 06-Nov-94 08:49:37 GMT",
        // YYYY-Mon-DD (ISO-style with spelled month)
        "2013-Feb-03",
        // @ as date-time separator
        "2013-Feb-03@12:30:00",
        "Jan. 31, 1999 @ 12:00 PM",
        "12/31/2026 @ 18:00:09.001",
        // Ordinal day suffix — requires regex preprocessing in shotgun
        "October 7th, 1970",
        // Period-suffix month abbreviation — requires regex preprocessing in shotgun
        "Oct. 7, 1970",
        // noon/midnight keywords — no equivalent DateTimeFormatter pattern
        "December 1, 1999 12:00 noon",
        "1999-01-01 12:00:00 midnight -0500",
        // No timezone (date only)
        "1999-01-31",
        "19990101"
    );
}
