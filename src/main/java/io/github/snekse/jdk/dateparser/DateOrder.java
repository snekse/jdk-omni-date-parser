package io.github.snekse.jdk.dateparser;

/**
 * Controls the assumed ordering of month, day, and year components when parsing
 * ambiguous numeric date strings such as {@code "10/11/12"} or {@code "01-02-03"}.
 *
 * <p>This setting is only consulted when the parser cannot determine component order
 * from context (e.g. a value greater than 12 unambiguously identifies the day or year).
 * Unambiguous formats such as ISO 8601 ({@code 1999-01-31}) and RFC 2822
 * ({@code 01 Jan 1999}) are always parsed correctly regardless of this setting.
 */
public enum DateOrder {
    /** Month / Day / Year — US convention (default). */
    MDY,
    /** Day / Month / Year — European convention. */
    DMY,
    /** Year / Month / Day — ISO-adjacent. */
    YMD
}
