package io.github.snekse.jdk.dateparser;

import io.github.snekse.jdk.dateparser.internal.DateAssembler;
import lombok.Value;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Lenient date/time parser that converts almost any date string to a {@link java.time} result
 * without requiring a format pattern.
 *
 * <h2>Zero-config usage</h2>
 * <pre>{@code
 * ZonedDateTime zdt = OmniDateParser.toZonedDateTime("Fri, 01 Jan 1999 23:59:00 +0000");
 * LocalDate     ld  = OmniDateParser.toLocalDate("1999-01-31");
 * Instant       i   = OmniDateParser.toInstant("1999-01-01T00:00:00Z");
 * }</pre>
 *
 * <h2>Configured usage</h2>
 * <pre>{@code
 * OmniDateParser parser = new OmniDateParser(
 *     OmniDateParserConfig.builder()
 *         .dateOrder(DateOrder.DMY)
 *         .defaultZone(ZoneId.of("Europe/London"))
 *         .build());
 * ZonedDateTime zdt = parser.parseZonedDateTime("01/02/2024");
 * }</pre>
 *
 * <p>Instances are immutable and safe for use by multiple threads concurrently,
 * modeled after {@link java.time.format.DateTimeFormatter}.
 *
 * <p>All methods throw {@link DateParseException} (unchecked) if the input cannot be parsed.
 */
@Value
public class OmniDateParser {

    /** The configuration used by this instance. */
    OmniDateParserConfig config;

    private static final OmniDateParser DEFAULT =
            new OmniDateParser(OmniDateParserConfig.defaults());

    // ------------------------------------------------------------------
    // Zero-config static convenience methods (UTC default zone, MDY order)
    // ------------------------------------------------------------------

    /**
     * Parses {@code input} and returns a {@link ZonedDateTime} using default config.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link ZonedDateTime}
     * @throws DateParseException if the input cannot be parsed
     */
    public static ZonedDateTime toZonedDateTime(String input) {
        return DEFAULT.parseZonedDateTime(input);
    }

    /**
     * Parses {@code input} and returns a {@link LocalDate} using default config.
     * Time and timezone components in the input are silently ignored.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link LocalDate}
     * @throws DateParseException if the input cannot be parsed
     */
    public static LocalDate toLocalDate(String input) {
        return DEFAULT.parseLocalDate(input);
    }

    /**
     * Parses {@code input} and returns a {@link LocalDateTime} using default config.
     * Timezone components in the input are silently ignored.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link LocalDateTime}
     * @throws DateParseException if the input cannot be parsed
     */
    public static LocalDateTime toLocalDateTime(String input) {
        return DEFAULT.parseLocalDateTime(input);
    }

    /**
     * Parses {@code input} and returns an {@link Instant} using default config.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link Instant}
     * @throws DateParseException if the input cannot be parsed
     */
    public static Instant toInstant(String input) {
        return DEFAULT.parseInstant(input);
    }

    // ------------------------------------------------------------------
    // Configurable instance methods
    // ------------------------------------------------------------------

    /**
     * Parses {@code input} and returns a {@link ZonedDateTime} using this instance's config.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link ZonedDateTime}
     * @throws DateParseException if the input cannot be parsed
     */
    public ZonedDateTime parseZonedDateTime(String input) {
        return new DateAssembler(input, config).assembleZonedDateTime();
    }

    /**
     * Parses {@code input} and returns a {@link LocalDate} using this instance's config.
     * Time and timezone components in the input are silently ignored.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link LocalDate}
     * @throws DateParseException if the input cannot be parsed
     */
    public LocalDate parseLocalDate(String input) {
        return parseZonedDateTime(input).toLocalDate();
    }

    /**
     * Parses {@code input} and returns a {@link LocalDateTime} using this instance's config.
     * Timezone components in the input are silently ignored.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link LocalDateTime}
     * @throws DateParseException if the input cannot be parsed
     */
    public LocalDateTime parseLocalDateTime(String input) {
        return parseZonedDateTime(input).toLocalDateTime();
    }

    /**
     * Parses {@code input} and returns an {@link Instant} using this instance's config.
     *
     * @param input the date/time string to parse
     * @return the parsed {@link Instant}
     * @throws DateParseException if the input cannot be parsed
     */
    public Instant parseInstant(String input) {
        return parseZonedDateTime(input).toInstant();
    }
}
