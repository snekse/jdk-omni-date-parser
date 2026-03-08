package io.github.snekse.jdk.dateparser.internal;

import io.github.snekse.jdk.dateparser.DateParseException;
import io.github.snekse.jdk.dateparser.OmniDateParserConfig;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Takes a token list from the Lexer and classifies tokens into date fields,
 * then assembles the final java.time result.
 *
 * Phase 1 handles: ISO 8601, RFC 2822.
 */
public class DateAssembler {

    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
            Map.entry("JANUARY", 1),  Map.entry("JAN", 1),
            Map.entry("FEBRUARY", 2), Map.entry("FEB", 2),
            Map.entry("MARCH", 3),    Map.entry("MAR", 3),
            Map.entry("APRIL", 4),    Map.entry("APR", 4),
            Map.entry("MAY", 5),
            Map.entry("JUNE", 6),     Map.entry("JUN", 6),
            Map.entry("JULY", 7),     Map.entry("JUL", 7),
            Map.entry("AUGUST", 8),   Map.entry("AUG", 8),
            Map.entry("SEPTEMBER", 9),Map.entry("SEP", 9), Map.entry("SEPT", 9),
            Map.entry("OCTOBER", 10), Map.entry("OCT", 10),
            Map.entry("NOVEMBER", 11),Map.entry("NOV", 11),
            Map.entry("DECEMBER", 12),Map.entry("DEC", 12)
    );

    private final List<Token> tokens;
    private final OmniDateParserConfig config;
    private final String original;

    private int year = -1, month = -1, day = -1;
    private int hour = 0, minute = 0, second = 0, nano = 0;
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

        if (first.type() == TokenType.DIGIT_SEQ && first.value().length() == 4) {
            // Starts with 4-digit year → ISO family
            classifyIso();
            return;
        }

        if (first.type() == TokenType.ALPHA_SEQ) {
            String upper = first.value().toUpperCase();
            // Skip day-of-week prefix: "Fri, ..."
            if (isWeekdayAbbr(upper) && cur + 1 < tokens.size()
                    && tokens.get(1).type() == TokenType.SEPARATOR) {
                cur = 1; // skip weekday token (index 0)
                // skip the comma separator and any following space
                advanceSeparators();
                classifyRfc2822Body();
                return;
            }
            // Month name first (no day-of-week): "Jan 01 1999 ..." or "January 1, 1999"
            if (MONTH_NAMES.containsKey(upper)) {
                classifyRfc2822Body();
                return;
            }
        }

        // Starts with DIGIT_SEQ (non-4-digit) → could be RFC 2822 without day-of-week
        // "01 Jan 1999 23:59:00 +0000"
        if (first.type() == TokenType.DIGIT_SEQ) {
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

        // optional zone info after time
        parseZoneSection();
    }

    // -----------------------------------------------------------------------
    // RFC 2822
    // -----------------------------------------------------------------------

    private void classifyRfc2822Body() {
        // Two forms:
        //   DD Mon YYYY HH:MM:SS ±HHMM
        //   Mon DD YYYY HH:MM:SS ±HHMM  (less common but accepted)

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
        } else if (t.type() == TokenType.ALPHA_SEQ && MONTH_NAMES.containsKey(t.value().toUpperCase())) {
            // Mon DD YYYY  or  Mon DD, YYYY
            month = MONTH_NAMES.get(t.value().toUpperCase()); cur++;
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

        if (tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            parseTimeSection();
            skipSpaces();
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

    /** Parses optional timezone info: Z, UTC, GMT, +HHMM, -HHMM, +HH:MM, -HH:MM */
    private void parseZoneSection() {
        if (cur >= tokens.size()) return;

        skipSpaces();
        if (cur >= tokens.size()) return;

        Token t = tokens.get(cur);

        // Named abbreviation: Z, UTC, GMT
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
        Integer m = MONTH_NAMES.get(t.value().toUpperCase());
        if (m == null) {
            throw new DateParseException(original, "unrecognized month name: " + t.value());
        }
        return m;
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
