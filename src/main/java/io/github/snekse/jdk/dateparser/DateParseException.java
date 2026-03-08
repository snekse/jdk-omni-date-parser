package io.github.snekse.jdk.dateparser;

/**
 * Thrown when {@link OmniDateParser} cannot parse an input string into a date/time value.
 *
 * <p>The exception message always includes the offending input and a human-readable
 * reason, e.g.: {@code Cannot parse date: "foobar" — unrecognized date format}.
 *
 * <p>This is an unchecked exception; callers are not required to catch it.
 */
public class DateParseException extends RuntimeException {

    /**
     * Creates an exception with a pre-formatted message (used for null/blank input).
     *
     * @param message the exception message
     */
    public DateParseException(String message) {
        super(message);
    }

    /**
     * Creates an exception that includes the offending input and a reason.
     *
     * @param input  the string that could not be parsed
     * @param reason a human-readable explanation of why parsing failed
     */
    public DateParseException(String input, String reason) {
        super("Cannot parse date: \"" + input + "\" \u2014 " + reason);
    }
}
