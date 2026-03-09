package io.github.snekse.jdk.dateparser.bench;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;

/**
 * Naive reference implementation: tries each formatter sequentially, catching
 * {@link DateTimeParseException} on each failure before trying the next.
 *
 * <p>This is the pattern most Java developers write when they need to handle multiple
 * date formats. It exists only in {@code src/jmh/} as a benchmark reference — it is
 * not part of the shipped library.
 *
 * <p>The list covers all 19 inputs in {@link BenchmarkInputs#ALL}.
 * Total formatters: 16 (after preprocessing normalizes a.m./p.m. and "at " tokens).
 */
public final class ShotgunDateParser {

    private ShotgunDateParser() {}

    // Ordered list of formatters to try. Every failure throws an exception — expensive on JVM.
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
        // ISO 8601 variants (ZonedDateTime / OffsetDateTime)
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        // RFC 2822 / RFC 1123: "Fri, 01 Jan 1999 23:59:00 +0000"
        DateTimeFormatter.RFC_1123_DATE_TIME,
        // RFC 2822 without day-of-week: "01 Jan 1999 00:00:00 GMT"
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss z", Locale.ENGLISH),
        // Western dash/slash — DMY + 24h + named TZ: "31/12/1999 00:00 GMT"
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z", Locale.ENGLISH),
        // Western dash — DMY + 12h + named TZ: "31-12-1999 11:59 PM GMT"
        DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a z", Locale.ENGLISH),
        // Western slash — MDY + 12h + named TZ: "01/01/1999 12:00 PM PST"
        DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a z", Locale.ENGLISH),
        // Western slash — YMD + 24h + named TZ: "1999/01/01 00:00 JST"
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm z", Locale.ENGLISH),
        // Spelled-out + offset: "January 1, 1999 12:00 PM -0500"
        DateTimeFormatter.ofPattern("MMMM d, yyyy hh:mm a Z", Locale.ENGLISH),
        // Spelled-out + named TZ: "January 1, 1999 11:59 PM PST" (after p.m. → PM preprocessing)
        DateTimeFormatter.ofPattern("MMMM d, yyyy hh:mm a z", Locale.ENGLISH),
        // Spelled-out no TZ: "January 31, 1999 12:00 PM" (LocalDateTime → UTC)
        DateTimeFormatter.ofPattern("MMMM d, yyyy hh:mm a", Locale.ENGLISH),
        // Spelled-out long: "31 December 1999 00:00:00 GMT"
        DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm:ss z", Locale.ENGLISH),
        // Spelled-out abbreviated: "Dec 31, 1999 00:00:00 UTC"
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss z", Locale.ENGLISH),
        // Full day-of-week: "Friday, January 1, 1999 12:00:00 PM EST"
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy hh:mm:ss a z", Locale.ENGLISH),
        // Year-first spelled-out: "1999 January 1 00:00:00 UTC"
        DateTimeFormatter.ofPattern("yyyy MMMM d HH:mm:ss z", Locale.ENGLISH),
        // Compact numeric: "19990101" (LocalDate → UTC start-of-day)
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        // ISO local date: "1999-01-31" (LocalDate → UTC start-of-day)
        DateTimeFormatter.ISO_LOCAL_DATE
    );

    /**
     * Parses {@code input} to a {@link ZonedDateTime}.
     *
     * <p>Preprocessing: normalises {@code "a.m."}/{@code "p.m."} to {@code "AM"}/{@code "PM"}
     * and removes the word {@code " at "} so that formatters can be applied uniformly.
     *
     * @param input the date/time string to parse
     * @return the parsed ZonedDateTime
     * @throws DateTimeParseException if no formatter matched
     */
    public static ZonedDateTime parse(String input) {
        String normalised = preprocess(input);

        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                TemporalAccessor ta = fmt.parse(normalised);
                try {
                    return ZonedDateTime.from(ta);
                } catch (DateTimeException e1) {
                    try {
                        // LocalDateTime result (no zone info) — apply UTC
                        return LocalDateTime.from(ta).atZone(ZoneOffset.UTC);
                    } catch (DateTimeException e2) {
                        // LocalDate result — start of day UTC
                        return LocalDate.from(ta).atStartOfDay(ZoneOffset.UTC);
                    }
                }
            } catch (DateTimeParseException ignored) {
                // try next — this exception creation is the bottleneck
            }
        }
        throw new DateTimeParseException("No formatter matched: " + input, input, 0);
    }

    /**
     * Normalises non-standard tokens so that {@link DateTimeFormatter} patterns can match.
     * <ul>
     *   <li>{@code " at "} → {@code " "} (e.g. "January 1, 1999 at 11:59 p.m. PST")</li>
     *   <li>{@code "p.m."} → {@code "PM"}, {@code "a.m."} → {@code "AM"} (case-insensitive)</li>
     * </ul>
     */
    private static String preprocess(String input) {
        return input
            .replace(" at ", " ")
            .replace("p.m.", "PM")
            .replace("a.m.", "AM")
            .replace("P.M.", "PM")
            .replace("A.M.", "AM");
    }
}
