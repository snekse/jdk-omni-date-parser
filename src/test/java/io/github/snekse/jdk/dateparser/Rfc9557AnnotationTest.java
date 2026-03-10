package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RFC 9557 (IXDTF) — Internet Extended Date/Time Format bracket annotations.
 *
 * <p><b>RFC 9557</b> (2024) extends RFC 3339 with bracket-enclosed annotations appended after
 * the offset. These annotations can specify an IANA timezone ID (which preserves DST semantics
 * that a fixed offset cannot), or non-timezone metadata like calendar systems.
 *
 * <p>This format is increasingly used by modern runtimes and APIs (e.g. {@code Temporal} in
 * ECMAScript, Java's {@code ZonedDateTime.toString()}) to round-trip timezone information that
 * would otherwise be lost when collapsing to a fixed offset.
 *
 * <p>Example formats:
 * <ul>
 *   <li>{@code 2018-09-16T08:00:00+00:00[Europe/London]} — IANA timezone annotation</li>
 *   <li>{@code 2020-06-15T12:00:00-04:00[America/New_York]} — DST-aware timezone</li>
 *   <li>{@code 2018-09-16T08:00:00Z[u-ca=japanese]} — calendar annotation (silently ignored)</li>
 *   <li>{@code 2018-09-16T08:00:00+00:00[Europe/London][u-ca=japanese]} — multiple annotations</li>
 * </ul>
 *
 * <p>When a timezone annotation is present, it takes precedence over the numeric offset for
 * determining the {@code ZoneId} of the result. Non-timezone annotations (containing {@code =})
 * are silently ignored.
 *
 * @see Iso8601Test
 */
class Rfc9557AnnotationTest {

    /** IANA timezone annotation overrides the numeric offset in the result's ZoneId. */
    @Test
    void timezone_annotation_overrides_offset() {
        // [Europe/London] should be used as zoneId, not the +00:00 offset
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2018-09-16T08:00:00+00:00[Europe/London]");
        assertAll(
                () -> assertEquals(ZoneId.of("Europe/London"), result.getZone()),
                () -> assertEquals(2018, result.getYear()),
                () -> assertEquals(9, result.getMonthValue()),
                () -> assertEquals(16, result.getDayOfMonth()),
                () -> assertEquals(8, result.getHour())
        );
    }

    @Test
    void calendar_annotation_silently_ignored() {
        // [u-ca=japanese] contains '=' so it's not a timezone → ignored
        // Zone should fall back to the Z (UTC) offset from the input
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2018-09-16T08:00:00Z[u-ca=japanese]");
        assertAll(
                () -> assertEquals(2018, result.getYear()),
                () -> assertEquals(9, result.getMonthValue()),
                () -> assertEquals(16, result.getDayOfMonth()),
                () -> assertEquals(ZoneOffset.UTC, result.getOffset())
        );
    }

    @Test
    void timezone_plus_calendar_annotation() {
        // Unique: both TZ and calendar annotation — TZ wins, calendar ignored
        ZonedDateTime result = OmniDateParser.toZonedDateTime(
                "2018-09-16T08:00:00+00:00[Europe/London][u-ca=japanese]");
        assertAll(
                () -> assertEquals(ZoneId.of("Europe/London"), result.getZone()),
                () -> assertEquals(2018, result.getYear()),
                () -> assertEquals(8, result.getHour())
        );
    }

    @Test
    void us_timezone_annotation() {
        // Unique: different zone (America/New_York) with DST-aware offset -04:00
        ZonedDateTime result = OmniDateParser.toZonedDateTime(
                "2020-06-15T12:00:00-04:00[America/New_York]");
        assertAll(
                () -> assertEquals(ZoneId.of("America/New_York"), result.getZone()),
                () -> assertEquals(2020, result.getYear()),
                () -> assertEquals(6, result.getMonthValue()),
                () -> assertEquals(15, result.getDayOfMonth()),
                () -> assertEquals(12, result.getHour()),
                () -> assertEquals(0, result.getMinute())
        );
    }

    @Test
    void no_annotation_preserves_offset() {
        // Baseline: without bracket annotation, numeric offset is used
        ZonedDateTime result = OmniDateParser.toZonedDateTime("2018-09-16T08:00:00+05:30");
        assertAll(
                () -> assertEquals(ZoneOffset.of("+05:30"), result.getOffset()),
                () -> assertEquals(8, result.getHour())
        );
    }
}
