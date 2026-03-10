package io.github.snekse.jdk.dateparser.bench;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
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
 * <p>Two entry points are provided:
 * <ul>
 *   <li>{@link #parseCore} — handles the 21 core inputs ({@link BenchmarkInputs#CORE}),
 *       without ordinal/period/@ preprocessing. 19 formatters. Fair baseline comparison.</li>
 *   <li>{@link #parse} — handles all 28 inputs ({@link BenchmarkInputs#ALL}), including
 *       ordinal-suffix, period-suffix, {@code @} separator, and noon/midnight keyword formats
 *       via extra preprocessing. 29 formatters.</li>
 * </ul>
 */
public final class ShotgunDateParser {

    private ShotgunDateParser() {}

    // Core formatters: covers BenchmarkInputs.CORE — no ordinal/period-specific formatters.
    private static final List<DateTimeFormatter> CORE_FORMATTERS = List.of(
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
        // RFC 850: "Sunday, 06-Nov-94 08:49:37 GMT" — needs lenient resolver + pivot year
        new DateTimeFormatterBuilder()
            .appendPattern("EEEE, dd-MMM-")
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1970)
            .appendPattern(" HH:mm:ss z")
            .toFormatter(Locale.ENGLISH)
            .withResolverStyle(ResolverStyle.LENIENT),
        // ISO-style with spelled month: "2013-Feb-03"
        DateTimeFormatter.ofPattern("yyyy-MMM-dd", Locale.ENGLISH),
        // Compact numeric: "19990101" (LocalDate → UTC start-of-day)
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        // ISO local date: "1999-01-31" (LocalDate → UTC start-of-day)
        DateTimeFormatter.ISO_LOCAL_DATE
    );

    // Full formatters: CORE plus formatters for ordinal-suffix, period-suffix, and @ separator formats.
    private static final List<DateTimeFormatter> ALL_FORMATTERS;
    static {
        var list = new java.util.ArrayList<>(CORE_FORMATTERS);
        // Insert before compact/ISO-local-date — spelled-out date-only after ordinal stripped
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
        // Abbreviated month date-only after period stripped (case-insensitive for "oct")
        list.add(list.size() - 2,
            new DateTimeFormatterBuilder().parseCaseInsensitive()
                .appendPattern("MMM d, yyyy").toFormatter(Locale.ENGLISH));
        // ISO-style spelled month with T-separated time (after @ → T preprocessing):
        // "2013-Feb-03@12:30:00" → "2013-Feb-03T12:30:00"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("yyyy-MMM-dd'T'HH:mm:ss", Locale.ENGLISH));
        // Western MDY with fractional seconds (after " @ " → " " preprocessing):
        // "12/31/2026 @ 18:00:09.001" → "12/31/2026 18:00:09.001"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss.SSS", Locale.ENGLISH));
        // Abbreviated month + 12h time, no TZ (after period + " @ " preprocessing):
        // "Jan. 31, 1999 @ 12:00 PM" → "Jan 31, 1999 12:00 PM"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("MMM d, yyyy hh:mm a", Locale.ENGLISH));
        // noon/midnight preprocessing produces 12h AM/PM strings not covered by core formatters:
        // "31 December 1999 12:00 PM UTC"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("d MMMM yyyy hh:mm a z", Locale.ENGLISH));
        // "1999-01-01 12:00:00 AM -0500"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a Z", Locale.ENGLISH));
        // "1999/12/31 12:00 PM CST"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("yyyy/MM/dd hh:mm a z", Locale.ENGLISH));
        // "1999 January 1 12:00:00 AM"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("yyyy MMMM d hh:mm:ss a", Locale.ENGLISH));
        // "1999-12-31 12:00:00.000 AM"
        list.add(list.size() - 2,
            DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss.SSS a", Locale.ENGLISH));
        ALL_FORMATTERS = java.util.List.copyOf(list);
    }

    /**
     * Parses {@code input} using the core formatter set (no ordinal/period support).
     * Use with {@link BenchmarkInputs#CORE} for a fair apples-to-apples comparison.
     *
     * @param input the date/time string to parse
     * @return the parsed ZonedDateTime
     * @throws DateTimeParseException if no formatter matched
     */
    public static ZonedDateTime parseCore(String input) {
        return tryFormatters(preprocessCore(input), CORE_FORMATTERS);
    }

    /**
     * Parses {@code input} using the full formatter set, with ordinal-suffix and
     * period-suffix preprocessing. Use with {@link BenchmarkInputs#ALL}.
     *
     * @param input the date/time string to parse
     * @return the parsed ZonedDateTime
     * @throws DateTimeParseException if no formatter matched
     */
    public static ZonedDateTime parse(String input) {
        return tryFormatters(preprocessAll(input), ALL_FORMATTERS);
    }

    private static ZonedDateTime tryFormatters(String normalised, List<DateTimeFormatter> formatters) {
        for (DateTimeFormatter fmt : formatters) {
            try {
                TemporalAccessor ta = fmt.parse(normalised);
                try {
                    return ZonedDateTime.from(ta);
                } catch (DateTimeException e1) {
                    try {
                        return LocalDateTime.from(ta).atZone(ZoneOffset.UTC);
                    } catch (DateTimeException e2) {
                        return LocalDate.from(ta).atStartOfDay(ZoneOffset.UTC);
                    }
                }
            } catch (DateTimeParseException ignored) {
                // try next — this exception creation is the bottleneck
            }
        }
        throw new DateTimeParseException("No formatter matched: " + normalised, normalised, 0);
    }

    /**
     * Base preprocessing: normalises {@code "a.m."}/{@code "p.m."} to {@code "AM"}/{@code "PM"}
     * and removes the word {@code " at "}.
     */
    private static String preprocessCore(String input) {
        return input
            .replace(" at ", " ")
            .replaceAll("(?i)p\\.m\\.", "PM")
            .replaceAll("(?i)a\\.m\\.", "AM");
    }

    /**
     * Full preprocessing: base preprocessing plus ordinal-suffix, period-suffix, @ separator,
     * and noon/midnight keywords.
     * <ul>
     *   <li>Ordinal suffixes stripped: {@code "7th"} → {@code "7"}, {@code "1st"} → {@code "1"}</li>
     *   <li>Abbreviated-month periods stripped: {@code "Oct."} → {@code "Oct"}</li>
     *   <li>{@code " @ "} → {@code " "} (spaced @ separator, e.g. {@code "Jan 31, 1999 @ 12:00 PM"})</li>
     *   <li>{@code "@"} → {@code "T"} (unspaced @ separator, e.g. {@code "2013-Feb-03@12:30:00"})</li>
     *   <li>{@code "noon"} → {@code "PM"}, {@code "midnight"} → {@code "AM"} (case-insensitive)</li>
     * </ul>
     */
    private static String preprocessAll(String input) {
        return preprocessCore(input)
            .replaceAll("(?i)(\\d+)(st|nd|rd|th)\\b", "$1")
            .replaceAll("(?i)\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)\\.", "$1")
            .replace(" @ ", " ")
            .replace("@", "T")
            .replaceAll("(?i)\\bnoon\\b", "PM")
            .replaceAll("(?i)\\bmidnight\\b", "AM");
    }
}
