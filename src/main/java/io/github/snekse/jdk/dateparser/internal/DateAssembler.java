package io.github.snekse.jdk.dateparser.internal;

import io.github.snekse.jdk.dateparser.DateParseException;
import io.github.snekse.jdk.dateparser.OmniDateParserConfig;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.OptionalInt;

/**
 * Takes a token list from the Lexer and classifies tokens into date fields,
 * then assembles the final java.time result.
 *
 * Phase 1: ISO 8601, RFC 2822.
 * Phase 2: Western slash/dot/dash formats, spelled-out months, AM/PM, compact YYYYMMDD,
 *           named TZ abbreviations, ambiguity resolution via DateOrder.
 */
public class DateAssembler {

    private final List<Token> tokens;
    private final OmniDateParserConfig config;
    private final String original;

    private int year = -1, month = -1, day = -1;
    private int hour = 0, minute = 0, second = 0, nano = 0;
    private int amPm = 0;          // 0 = not set, 1 = PM, -1 = AM
    private ZoneOffset offset = null;  // null means "use config.defaultZone"
    private ZoneId zoneId = null;

    // cursor into the token list
    private int cur = 0;

    public DateAssembler(String input, OmniDateParserConfig config) {
        if (input == null) {
            throw new DateParseException("input must not be null");
        }
        String stripped = input.strip();
        if (stripped.isEmpty()) {
            throw new DateParseException(input, "input is empty or blank");
        }
        this.original = input;
        this.config = config;
        this.tokens = new Lexer(stripped).tokenize();
    }

    public ZonedDateTime assembleZonedDateTime() {
        classify();
        applyAmPm();
        validate();
        return buildZonedDateTime();
    }

    // -----------------------------------------------------------------------
    // Format detection
    // -----------------------------------------------------------------------

    private void classify() {
        if (tokens.isEmpty()) {
            throw new DateParseException(original, "no tokens — input contains no recognizable date components");
        }

        Token first = tokens.get(0);

        // Unix timestamp: single DIGIT_SEQ token with 10, 13, 16, or 19 digits
        if (tokens.size() == 1 && first.type() == TokenType.DIGIT_SEQ) {
            int len = first.value().length();
            if (len == 10 || len == 13 || len == 16 || len == 19) {
                classifyUnixTimestamp(first.value(), len);
                return;
            }
            if (len > 8) {
                throw new DateParseException(original, "unrecognized numeric format (" + len + " digits)");
            }
        }

        // Compact numeric date: YYYYMMDD (8-digit single token)
        if (first.type() == TokenType.DIGIT_SEQ && first.value().length() == 8) {
            classifyCompact();
            return;
        }

        // 4-digit year first
        if (first.type() == TokenType.DIGIT_SEQ && first.value().length() == 4) {
            if (tokens.size() > 1) {
                Token second = tokens.get(1);
                if (second.type() == TokenType.SEPARATOR && "/".equals(second.value())) {
                    classifyWestern(); // YYYY/MM/DD
                    return;
                }
                if (second.type() == TokenType.SEPARATOR && " ".equals(second.value())
                        && tokens.size() > 2
                        && tokens.get(2).type() == TokenType.ALPHA_SEQ
                        && MonthNames.resolve(tokens.get(2).value()).isPresent()) {
                    classifyYearFirstSpelled(); // 1999 January 1 ...
                    return;
                }
            }
            classifyIso(); // YYYY-MM-DD (default 4-digit start)
            return;
        }

        // Alpha first: weekday prefix or month name
        if (first.type() == TokenType.ALPHA_SEQ) {
            String upper = first.value().toUpperCase();
            if (isWeekdayAbbr(upper) && cur + 1 < tokens.size()
                    && tokens.get(1).type() == TokenType.SEPARATOR) {
                cur = 1; // skip weekday token (index 0)
                advanceSeparators();
                classifyRfc2822Body();
                return;
            }
            if (MonthNames.resolve(upper).isPresent()) {
                classifyRfc2822Body(); // January 1, 1999 ... or Jan 1, 1999 ...
                return;
            }
        }

        // Non-4-digit DIGIT_SEQ first
        if (first.type() == TokenType.DIGIT_SEQ) {
            if (tokens.size() > 1) {
                Token sep = tokens.get(1);
                if (sep.type() == TokenType.SEPARATOR) {
                    if ("/".equals(sep.value()) || ".".equals(sep.value())) {
                        classifyWestern(); // DD/MM/YYYY or DD.MM.YYYY
                        return;
                    }
                    if ("-".equals(sep.value()) && tokens.size() > 2) {
                        Token afterSep = tokens.get(2);
                        if (afterSep.type() == TokenType.DIGIT_SEQ) {
                            classifyWestern(); // DD-MM-YYYY
                            return;
                        }
                        if (afterSep.type() == TokenType.ALPHA_SEQ
                                && MonthNames.resolve(afterSep.value()).isPresent()) {
                            classifyDDMonYYYY(); // DD-Mon-YYYY
                            return;
                        }
                    }
                }
            }
            // Default: RFC 2822 style (DD Mon YYYY or Mon DD YYYY)
            classifyRfc2822Body();
            return;
        }

        throw new DateParseException(original, "unrecognized date format");
    }

    // -----------------------------------------------------------------------
    // ISO 8601
    // -----------------------------------------------------------------------

    private void classifyIso() {
        // year
        year = intOf(expect(TokenType.DIGIT_SEQ));
        // separator
        expectSeparator("-");
        // month
        month = intOf(expect(TokenType.DIGIT_SEQ));
        // separator
        expectSeparator("-");
        // day
        day = intOf(expect(TokenType.DIGIT_SEQ));

        if (cur >= tokens.size()) return; // date-only

        Token next = tokens.get(cur);

        // T separator between date and time (ISO 8601)
        if (next.type() == TokenType.T_LITERAL) {
            cur++;
        } else if (next.type() == TokenType.SEPARATOR && " ".equals(next.value())) {
            cur++;
        } else {
            return; // nothing more recognizable
        }

        if (cur >= tokens.size()) return;
        if (tokens.get(cur).type() != TokenType.DIGIT_SEQ) return;

        // time
        parseTimeSection();
        parseAmPm();
        // optional zone info after time
        parseZoneSection();
    }

    // -----------------------------------------------------------------------
    // Western numeric formats: DD/MM/YYYY, MM/DD/YYYY, YYYY/MM/DD, DD-MM-YYYY, DD.MM.YYYY
    // -----------------------------------------------------------------------

    private void classifyWestern() {
        int a = intOf(expect(TokenType.DIGIT_SEQ));

        if (cur >= tokens.size() || tokens.get(cur).type() != TokenType.SEPARATOR) {
            throw new DateParseException(original, "expected date separator after first component");
        }
        String sep = tokens.get(cur).value();
        cur++; // consume separator

        int b = intOf(expect(TokenType.DIGIT_SEQ));

        // expect same separator
        if (cur >= tokens.size() || !TokenType.SEPARATOR.equals(tokens.get(cur).type())
                || !sep.equals(tokens.get(cur).value())) {
            throw new DateParseException(original, "inconsistent or missing date separator");
        }
        cur++; // consume separator

        int c = intOf(expect(TokenType.DIGIT_SEQ));

        resolveThreePartNumeric(a, b, c);

        skipSpaces();
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            parseTimeSection();
            parseAmPm();
            parseZoneSection();
        }
    }

    // -----------------------------------------------------------------------
    // Year-first spelled month: 1999 January 1 HH:MM:SS TZ
    // -----------------------------------------------------------------------

    private void classifyYearFirstSpelled() {
        year = intOf(expect(TokenType.DIGIT_SEQ));
        skipSpaces();
        month = expectMonthName();
        skipSpaces();
        day = intOf(expect(TokenType.DIGIT_SEQ));
        skipSpaces();
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            parseTimeSection();
            parseAmPm();
            parseZoneSection();
        }
    }

    // -----------------------------------------------------------------------
    // DD-Mon-YYYY: 01-Jan-1999, 01-Dec-1999
    // -----------------------------------------------------------------------

    private void classifyDDMonYYYY() {
        day = intOf(expect(TokenType.DIGIT_SEQ));
        expectSeparator("-");
        month = expectMonthName();
        expectSeparator("-");
        year = intOf(expect(TokenType.DIGIT_SEQ));
        skipSpaces();
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            parseTimeSection();
            parseAmPm();
            parseZoneSection();
        }
    }

    // -----------------------------------------------------------------------
    // Compact YYYYMMDD
    // -----------------------------------------------------------------------

    private void classifyCompact() {
        String val = expect(TokenType.DIGIT_SEQ).value();
        year  = Integer.parseInt(val.substring(0, 4));
        month = Integer.parseInt(val.substring(4, 6));
        day   = Integer.parseInt(val.substring(6, 8));
    }

    // -----------------------------------------------------------------------
    // Unix timestamp
    // -----------------------------------------------------------------------

    private void classifyUnixTimestamp(String digits, int length) {
        long val = Long.parseLong(digits);
        Instant instant = switch (length) {
            case 10 -> Instant.ofEpochSecond(val);
            case 13 -> Instant.ofEpochMilli(val);
            case 16 -> Instant.ofEpochSecond(val / 1_000_000, (val % 1_000_000) * 1000);
            case 19 -> Instant.ofEpochSecond(val / 1_000_000_000, val % 1_000_000_000);
            default -> throw new DateParseException(original, "unexpected digit length for unix timestamp: " + length);
        };
        ZonedDateTime zdt = instant.atZone(ZoneOffset.UTC);
        year   = zdt.getYear();
        month  = zdt.getMonthValue();
        day    = zdt.getDayOfMonth();
        hour   = zdt.getHour();
        minute = zdt.getMinute();
        second = zdt.getSecond();
        nano   = zdt.getNano();
        offset = ZoneOffset.UTC;
        zoneId = null;
    }

    // -----------------------------------------------------------------------
    // RFC 2822 / spelled-out month body
    // -----------------------------------------------------------------------

    private void classifyRfc2822Body() {
        // Two forms:
        //   DD Mon YYYY HH:MM:SS ±HHMM
        //   Mon DD YYYY HH:MM:SS ±HHMM  (and full names: January 1, 1999 ...)

        skipSpaces();
        if (cur >= tokens.size()) throw new DateParseException(original, "unexpected end of input");

        Token t = tokens.get(cur);

        if (t.type() == TokenType.DIGIT_SEQ) {
            // DD Mon YYYY
            day = intOf(t); cur++;
            skipSpaces();
            month = expectMonthName();
            skipSpaces();
            year = intOf(expect(TokenType.DIGIT_SEQ));
        } else if (t.type() == TokenType.ALPHA_SEQ && MonthNames.resolve(t.value()).isPresent()) {
            // Mon DD YYYY  or  Mon DD, YYYY
            month = MonthNames.resolve(t.value()).getAsInt(); cur++;
            skipSpaces();
            day = intOf(expect(TokenType.DIGIT_SEQ));
            // optional comma
            if (cur < tokens.size() && tokens.get(cur).type() == TokenType.SEPARATOR
                    && ",".equals(tokens.get(cur).value())) {
                cur++;
            }
            skipSpaces();
            year = intOf(expect(TokenType.DIGIT_SEQ));
        } else {
            throw new DateParseException(original, "expected day number or month name, got: " + t.value());
        }

        skipSpaces();
        if (cur >= tokens.size()) return; // date only

        // Skip "at" keyword (e.g., "January 1, 1999 at 11:59 p.m. PST")
        if (tokens.get(cur).type() == TokenType.ALPHA_SEQ
                && "AT".equals(tokens.get(cur).value().toUpperCase())) {
            cur++;
            skipSpaces();
        }

        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            parseTimeSection();
            parseAmPm();
            parseZoneSection();
        }
    }

    // -----------------------------------------------------------------------
    // Time + Zone helpers
    // -----------------------------------------------------------------------

    /** Parses HH:MM or HH:MM:SS or HH:MM:SS.nnn */
    private void parseTimeSection() {
        hour = intOf(expect(TokenType.DIGIT_SEQ));
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.COLON) {
            cur++;
            minute = intOf(expect(TokenType.DIGIT_SEQ));
        }
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.COLON) {
            cur++;
            second = intOf(expect(TokenType.DIGIT_SEQ));
        }
        // fractional seconds
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DOT) {
            cur++;
            String frac = expect(TokenType.DIGIT_SEQ).value();
            // normalize to 9 digits (nanoseconds)
            while (frac.length() < 9) frac = frac + "0";
            if (frac.length() > 9) frac = frac.substring(0, 9);
            nano = Integer.parseInt(frac);
        }
    }

    /**
     * Parses optional AM/PM or a.m./p.m. marker after the time section.
     * Sets amPm field: 1 = PM, -1 = AM, 0 = not present.
     */
    private void parseAmPm() {
        if (cur >= tokens.size()) return;
        skipSpaces();
        if (cur >= tokens.size()) return;

        Token t = tokens.get(cur);
        if (t.type() != TokenType.ALPHA_SEQ) return;

        String upper = t.value().toUpperCase();

        if ("AM".equals(upper)) {
            amPm = -1;
            cur++;
            return;
        }
        if ("PM".equals(upper)) {
            amPm = 1;
            cur++;
            return;
        }

        // Check for a.m. / p.m. pattern: ALPHA("a"/"p") DOT ALPHA("m") DOT
        if (("A".equals(upper) || "P".equals(upper))
                && cur + 3 < tokens.size()
                && tokens.get(cur + 1).type() == TokenType.DOT
                && tokens.get(cur + 2).type() == TokenType.ALPHA_SEQ
                && "M".equals(tokens.get(cur + 2).value().toUpperCase())
                && tokens.get(cur + 3).type() == TokenType.DOT) {
            amPm = "P".equals(upper) ? 1 : -1;
            cur += 4;
        }
    }

    /** Parses optional timezone info: Z, UTC, GMT, named abbr, +HHMM, -HHMM, +HH:MM, -HH:MM */
    private void parseZoneSection() {
        if (cur >= tokens.size()) return;

        skipSpaces();
        if (cur >= tokens.size()) return;

        Token t = tokens.get(cur);

        // Named abbreviation
        if (t.type() == TokenType.ALPHA_SEQ) {
            ZoneId resolved = ZoneResolver.resolve(t.value());
            if (resolved != null) {
                if (resolved instanceof ZoneOffset zo) {
                    offset = zo;
                } else {
                    zoneId = resolved;
                }
                cur++;
                return;
            }
            throw new DateParseException(original, "unrecognized timezone abbreviation: " + t.value());
        }

        // Numeric offset: +HHMM, -HHMM
        if (t.type() == TokenType.SIGN) {
            char sign = t.value().charAt(0);
            cur++;
            String digits = parseOffsetDigits();
            offset = ZoneResolver.parseNumericOffset(sign, digits);
            return;
        }

        // '-' emitted as SEPARATOR can also be a negative offset sign in offset position
        if (t.type() == TokenType.SEPARATOR && "-".equals(t.value())) {
            cur++;
            String digits = parseOffsetDigits();
            offset = ZoneResolver.parseNumericOffset('-', digits);
        }
    }

    /**
     * Reads offset digits: either "HHMM" (single DIGIT_SEQ) or "HH:MM" (DIGIT_SEQ COLON DIGIT_SEQ).
     */
    private String parseOffsetDigits() {
        String digits = expect(TokenType.DIGIT_SEQ).value();
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.COLON) {
            cur++;
            digits = digits + ":" + expect(TokenType.DIGIT_SEQ).value();
        }
        return digits;
    }

    // -----------------------------------------------------------------------
    // Ambiguity resolution
    // -----------------------------------------------------------------------

    /**
     * Assigns year/month/day from three numeric components using heuristics first,
     * then DateOrder from config for truly ambiguous cases.
     */
    private void resolveThreePartNumeric(int a, int b, int c) {
        // 4-digit (>99) component is unambiguously year
        if (a > 99) {
            // YYYY/MM/DD — year is first, always YMD regardless of DateOrder
            year = a;
            month = b;
            day = c;
            return;
        }
        if (c > 99) {
            year = c;
            // Determine month and day from a and b using heuristics
            if (a > 12) {
                // a can't be month → a is day, b is month
                day = a;
                month = b;
            } else if (b > 12) {
                // b can't be month → b is day, a is month
                month = a;
                day = b;
            } else {
                // Truly ambiguous: use DateOrder
                switch (config.getDateOrder()) {
                    case MDY -> { month = a; day = b; }
                    case DMY -> { day = a; month = b; }
                    case YMD -> { month = a; day = b; } // year is last; for remaining use MDY
                }
            }
            return;
        }
        // All three are small (≤99); use DateOrder with 2-digit year expansion
        switch (config.getDateOrder()) {
            case MDY -> { month = a; day = b; year = expandYear(c, config.getPivotYear()); }
            case DMY -> { day = a; month = b; year = expandYear(c, config.getPivotYear()); }
            case YMD -> { year = expandYear(a, config.getPivotYear()); month = b; day = c; }
        }
    }

    /** Expands a 2-digit year to 4 digits using the pivot year. */
    private static int expandYear(int twoDigit, int pivot) {
        if (twoDigit > 99) return twoDigit; // already 4-digit
        return twoDigit <= pivot ? 2000 + twoDigit : 1900 + twoDigit;
    }

    // -----------------------------------------------------------------------
    // AM/PM application
    // -----------------------------------------------------------------------

    private void applyAmPm() {
        if (amPm == 0) return;
        if (hour > 12) {
            throw new DateParseException(original, "a.m./p.m. with hour out of range: " + hour);
        }
        if (amPm == -1) {
            // AM: 12 AM = midnight (0), 1–11 AM stay as-is
            if (hour == 12) hour = 0;
        } else {
            // PM: 12 PM stays 12 (noon), 1–11 PM add 12
            if (hour != 12) hour += 12;
        }
    }

    // -----------------------------------------------------------------------
    // Validation
    // -----------------------------------------------------------------------

    private void validate() {
        if (year < 1 || year > 9999)
            throw new DateParseException(original, "year out of range: " + year);
        if (month < 1 || month > 12)
            throw new DateParseException(original, "month out of range: " + month);
        if (day < 1 || day > 31)
            throw new DateParseException(original, "day out of range: " + day);
        if (hour < 0 || hour > 23)
            throw new DateParseException(original, "hour out of range: " + hour);
        if (minute < 0 || minute > 59)
            throw new DateParseException(original, "minute out of range: " + minute);
        if (second < 0 || second > 59)
            throw new DateParseException(original, "second out of range: " + second);
    }

    // -----------------------------------------------------------------------
    // Build result
    // -----------------------------------------------------------------------

    private ZonedDateTime buildZonedDateTime() {
        LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second, nano);
        if (offset != null) {
            return ZonedDateTime.of(ldt, offset);
        }
        if (zoneId != null) {
            return ZonedDateTime.of(ldt, zoneId);
        }
        // No timezone in input — use configured default zone
        return ZonedDateTime.of(ldt, config.getDefaultZone());
    }

    // -----------------------------------------------------------------------
    // Token cursor helpers
    // -----------------------------------------------------------------------

    private Token expect(TokenType type) {
        if (cur >= tokens.size()) {
            throw new DateParseException(original, "unexpected end of input, expected " + type);
        }
        Token t = tokens.get(cur);
        if (t.type() != type) {
            throw new DateParseException(original,
                    "expected " + type + " at position " + t.position() + ", got " + t.type() + " (" + t.value() + ")");
        }
        cur++;
        return t;
    }

    private void expectSeparator(String value) {
        if (cur >= tokens.size()) {
            throw new DateParseException(original, "unexpected end of input, expected separator '" + value + "'");
        }
        Token t = tokens.get(cur);
        if (t.type() != TokenType.SEPARATOR || !value.equals(t.value())) {
            throw new DateParseException(original,
                    "expected separator '" + value + "' at position " + t.position() + ", got: " + t.value());
        }
        cur++;
    }

    private int expectMonthName() {
        Token t = expect(TokenType.ALPHA_SEQ);
        OptionalInt m = MonthNames.resolve(t.value());
        if (m.isEmpty()) {
            throw new DateParseException(original, "unrecognized month name: " + t.value());
        }
        return m.getAsInt();
    }

    private void skipSpaces() {
        while (cur < tokens.size()
                && tokens.get(cur).type() == TokenType.SEPARATOR
                && " ".equals(tokens.get(cur).value())) {
            cur++;
        }
    }

    private void advanceSeparators() {
        // skip comma and following spaces
        while (cur < tokens.size() && tokens.get(cur).type() == TokenType.SEPARATOR) {
            cur++;
        }
    }

    private static int intOf(Token t) {
        return Integer.parseInt(t.value());
    }

    private static boolean isWeekdayAbbr(String upper) {
        return switch (upper) {
            case "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN",
                 "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
                    -> true;
            default -> false;
        };
    }
}
