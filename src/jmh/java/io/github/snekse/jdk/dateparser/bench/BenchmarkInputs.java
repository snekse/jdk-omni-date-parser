package io.github.snekse.jdk.dateparser.bench;

import java.util.List;

/**
 * Representative mixed-format date strings used by all benchmarks.
 * Covers every format family supported by the library so comparisons are apples-to-apples.
 */
public final class BenchmarkInputs {

    private BenchmarkInputs() {}

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
        // No timezone (date only)
        "1999-01-31",
        "19990101"
    );
}
