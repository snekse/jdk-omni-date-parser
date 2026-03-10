package io.github.snekse.jdk.dateparser.internal;

import io.github.snekse.jdk.dateparser.DateParseException;
import io.github.snekse.jdk.dateparser.OmniDateParserConfig;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.OptionalInt;

/**
 * Takes a token list from the Lexer and classifies tokens into date fields,
 * then assembles the final java.time result.
 *
 * Phase 1: ISO 8601, RFC 2822.
 * Phase 2: Western slash/dot/dash formats, spelled-out months, AM/PM, compact YYYYMMDD,
 *           named TZ abbreviations, ambiguity resolution via DateOrder.
 * Phase 2+: CJK date separators 年月日時分秒 in label-after and label-before orderings.
 */
public class DateAssembler {

    // CJK date/time unit labels — used as separators in East Asian date formats
    private static final String CJK_YEAR   = "年";
    private static final String CJK_MONTH  = "月";
    private static final String CJK_DAY    = "日";
    private static final String CJK_HOUR   = "時";
    private static final String CJK_MINUTE = "分";
    private static final String CJK_SECOND = "秒";

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

        Token first = tokens.getFirst();

        // Unix timestamp: single DIGIT_SEQ token with 10, 13, 16, or 19 digits
        if (tokens.size() == 1 && first.type() == TokenType.DIGIT_SEQ) {
            int len = first.value().length();
            if (len == 10 || len == 13 || len == 16 || len == 19) {
                classifyUnixTimestamp(first.value(), len);
                return;
            }
            if (len == 7) {
                classifyOrdinalCompact(first.value());
                return;
            }
            if (len == 12 || len == 14) {
                classifyCompactDateTime(first.value(), len);
                return;
            }
            if (len > 8) {
                throw new DateParseException(original, "unrecognized numeric format (" + len + " digits)");
            }
        }

        // Compact datetime: 12 or 14 digit token (with optional trailing zone)
        if (first.type() == TokenType.DIGIT_SEQ && (first.value().length() == 12 || first.value().length() == 14)) {
            classifyCompactDateTime(first.value(), first.value().length());
            return;
        }

        // Compact ordinal: 7-digit token with optional trailing time/zone
        if (first.type() == TokenType.DIGIT_SEQ && first.value().length() == 7 && tokens.size() > 1) {
            classifyOrdinalCompact(first.value());
            return;
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
                if (second.type() == TokenType.DOT) {
                    classifyWestern(); // YYYY.MM.DD
                    return;
                }
                if (isCjkToken(second, CJK_YEAR)) {
                    classifyCjkLabelAfter(); // 1999年12月31日 ...
                    return;
                }
                if (second.type() == TokenType.SEPARATOR && " ".equals(second.value())
                        && tokens.size() > 2
                        && tokens.get(2).type() == TokenType.ALPHA_SEQ
                        && MonthNames.resolve(tokens.get(2).value()).isPresent()) {
                    classifyYearFirstSpelled(); // 1999 January 1 ...
                    return;
                }
                // ISO 8601 week date: 2004W536 or 2004-W53-6
                if (second.type() == TokenType.W_LITERAL) {
                    classifyIsoWeekDate(); // compact: 2004W536
                    return;
                }
                if (second.type() == TokenType.SEPARATOR && "-".equals(second.value())
                        && tokens.size() > 2 && tokens.get(2).type() == TokenType.W_LITERAL) {
                    classifyIsoWeekDate(); // extended: 2004-W53-6
                    return;
                }
                // ISO 8601 ordinal date: 1999-001
                if (second.type() == TokenType.SEPARATOR && "-".equals(second.value())
                        && tokens.size() > 2 && tokens.get(2).type() == TokenType.DIGIT_SEQ
                        && tokens.get(2).value().length() == 3) {
                    classifyIsoOrdinal();
                    return;
                }
                // ISO-style with spelled month: 2013-Feb-03
                if (second.type() == TokenType.SEPARATOR && "-".equals(second.value())
                        && tokens.size() > 2 && tokens.get(2).type() == TokenType.ALPHA_SEQ
                        && MonthNames.resolve(tokens.get(2).value()).isPresent()) {
                    classifyYearDashMonthNameDay();
                    return;
                }
            }
            classifyIso(); // YYYY-MM-DD (default 4-digit start)
            return;
        }

        // Alpha first: weekday prefix, month name, or CJK year label
        if (first.type() == TokenType.ALPHA_SEQ) {
            if (CJK_YEAR.equals(first.value())) {
                classifyCjkLabelBefore(); // 年1999月12日31 ...
                return;
            }
            String upper = first.value().toUpperCase();
            if (isWeekdayAbbr(upper) && cur + 1 < tokens.size()
                    && tokens.get(1).type() == TokenType.SEPARATOR) {
                cur = 1; // skip weekday token (index 0)
                advanceSeparators();
                // Peek ahead: RFC 850 has DD-Mon-YY pattern (digit, dash, alpha month)
                if (isRfc850Body()) {
                    classifyRfc850Body();
                } else {
                    classifyRfc2822Body();
                }
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
                    if ("/".equals(sep.value())) {
                        classifyWestern(); // DD/MM/YYYY
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
                if (sep.type() == TokenType.DOT) {
                    classifyWestern(); // DD.MM.YYYY
                    return;
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

        // T or @ separator between date and time (ISO 8601 / extended)
        if (next.type() == TokenType.T_LITERAL) {
            cur++;
        } else if (next.type() == TokenType.AT_LITERAL) {
            cur++;
            skipSpaces();
        } else if (next.type() == TokenType.SEPARATOR && " ".equals(next.value())) {
            cur++;
            skipAtLiteral(); // handle "1999-01-01 @ 12:00"
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
        // RFC 9557 bracket annotations
        parseBracketAnnotations();
    }

    // -----------------------------------------------------------------------
    // Western numeric formats: DD/MM/YYYY, MM/DD/YYYY, YYYY/MM/DD, DD-MM-YYYY, DD.MM.YYYY
    // -----------------------------------------------------------------------

    private void classifyWestern() {
        int a = intOf(expect(TokenType.DIGIT_SEQ));

        if (cur >= tokens.size() || !isDateSepToken(tokens.get(cur))) {
            throw new DateParseException(original, "expected date separator after first component");
        }
        Token sepToken = tokens.get(cur);
        String sep = dateSepValue(sepToken);
        cur++; // consume separator

        int b = intOf(expect(TokenType.DIGIT_SEQ));

        // expect same separator
        if (cur >= tokens.size() || !matchesSep(tokens.get(cur), sep)) {
            throw new DateParseException(original, "inconsistent or missing date separator");
        }
        cur++; // consume separator

        int c = intOf(expect(TokenType.DIGIT_SEQ));

        resolveThreePartNumeric(a, b, c);

        skipSpaces();
        skipAtLiteral();
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
        skipAtLiteral();
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
        skipAtLiteral();
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            parseTimeSection();
            parseAmPm();
            parseZoneSection();
        }
    }

    // -----------------------------------------------------------------------
    // YYYY-Mon-DD: ISO-style with spelled/abbreviated month name
    // -----------------------------------------------------------------------

    private void classifyYearDashMonthNameDay() {
        year = intOf(expect(TokenType.DIGIT_SEQ));
        expectSeparator("-");
        month = expectMonthName();
        expectSeparator("-");
        day = intOf(expect(TokenType.DIGIT_SEQ));
        parseOptionalTimeSuffix();
    }

    // -----------------------------------------------------------------------
    // Compact YYYYMMDD and YYYYMMDDTHHmmSS
    // -----------------------------------------------------------------------

    private void classifyCompact() {
        String val = expect(TokenType.DIGIT_SEQ).value();
        year  = Integer.parseInt(val.substring(0, 4));
        month = Integer.parseInt(val.substring(4, 6));
        day   = Integer.parseInt(val.substring(6, 8));

        // Optional compact time: T_LITERAL or AT_LITERAL followed by HHMMSS or HHMM digits
        if (cur < tokens.size() && (tokens.get(cur).type() == TokenType.T_LITERAL
                || tokens.get(cur).type() == TokenType.AT_LITERAL)) {
            cur++; // consume T or @
            if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
                String time = tokens.get(cur++).value();
                if (time.length() >= 2) hour   = Integer.parseInt(time.substring(0, 2));
                if (time.length() >= 4) minute = Integer.parseInt(time.substring(2, 4));
                if (time.length() >= 6) second = Integer.parseInt(time.substring(4, 6));
            }
            parseZoneSection();
        }
    }

    // -----------------------------------------------------------------------
    // Compact YYYYMMDDHHmmss (12 or 14 digits)
    // -----------------------------------------------------------------------

    private void classifyCompactDateTime(String val, int len) {
        cur++; // consume the digit token
        year   = Integer.parseInt(val.substring(0, 4));
        month  = Integer.parseInt(val.substring(4, 6));
        day    = Integer.parseInt(val.substring(6, 8));
        hour   = Integer.parseInt(val.substring(8, 10));
        minute = Integer.parseInt(val.substring(10, 12));
        if (len == 14) {
            second = Integer.parseInt(val.substring(12, 14));
        }
        skipSpaces();
        parseZoneSection();
    }

    // -----------------------------------------------------------------------
    // ISO 8601 ordinal dates: 1999-001, 1999001
    // -----------------------------------------------------------------------

    private void classifyOrdinalCompact(String val) {
        cur++; // consume the digit token
        int y = Integer.parseInt(val.substring(0, 4));
        int doy = Integer.parseInt(val.substring(4, 7));
        setFromOrdinal(y, doy);
        parseOptionalTimeSuffix();
    }

    private void classifyIsoOrdinal() {
        int y = intOf(expect(TokenType.DIGIT_SEQ));
        expectSeparator("-");
        int doy = intOf(expect(TokenType.DIGIT_SEQ));
        setFromOrdinal(y, doy);
        parseOptionalTimeSuffix();
    }

    private void setFromOrdinal(int y, int dayOfYear) {
        try {
            LocalDate ld = LocalDate.ofYearDay(y, dayOfYear);
            year  = ld.getYear();
            month = ld.getMonthValue();
            day   = ld.getDayOfMonth();
        } catch (java.time.DateTimeException e) {
            throw new DateParseException(original, "invalid ordinal date: year=" + y + ", day=" + dayOfYear);
        }
    }

    // -----------------------------------------------------------------------
    // ISO 8601 week dates: 2004-W53-6, 2004W536
    // -----------------------------------------------------------------------

    private void classifyIsoWeekDate() {
        int y = intOf(expect(TokenType.DIGIT_SEQ));
        // consume optional '-'
        boolean extended = cur < tokens.size()
                && tokens.get(cur).type() == TokenType.SEPARATOR
                && "-".equals(tokens.get(cur).value());
        if (extended) cur++;
        // consume W
        expect(TokenType.W_LITERAL);
        String weekDigits = expect(TokenType.DIGIT_SEQ).value();
        int week;
        int dow = 1; // default to Monday

        if (!extended && weekDigits.length() == 3) {
            // Basic format: "536" → week=53, day=6
            week = Integer.parseInt(weekDigits.substring(0, 2));
            dow = Integer.parseInt(weekDigits.substring(2, 3));
        } else {
            week = Integer.parseInt(weekDigits);
            if (cur < tokens.size()) {
                if (extended && tokens.get(cur).type() == TokenType.SEPARATOR
                        && "-".equals(tokens.get(cur).value())) {
                    cur++;
                    dow = intOf(expect(TokenType.DIGIT_SEQ));
                }
            }
        }

        try {
            LocalDate ld = LocalDate.of(y, 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                    .with(ChronoField.DAY_OF_WEEK, dow);
            year  = ld.getYear();
            month = ld.getMonthValue();
            day   = ld.getDayOfMonth();
        } catch (java.time.DateTimeException e) {
            throw new DateParseException(original, "invalid ISO week date: year=" + y + ", week=" + week + ", day=" + dow);
        }

        parseOptionalTimeSuffix();
    }

    // -----------------------------------------------------------------------
    // RFC 850 — obsolete HTTP format: Sunday, 06-Nov-94 08:49:37 GMT
    // -----------------------------------------------------------------------

    private boolean isRfc850Body() {
        // Peek: cur should point to DIGIT_SEQ, then SEPARATOR("-"), then ALPHA_SEQ (month name)
        if (cur >= tokens.size() || tokens.get(cur).type() != TokenType.DIGIT_SEQ) return false;
        if (cur + 1 >= tokens.size()) return false;
        Token sep = tokens.get(cur + 1);
        if (sep.type() != TokenType.SEPARATOR || !"-".equals(sep.value())) return false;
        if (cur + 2 >= tokens.size()) return false;
        Token monthTok = tokens.get(cur + 2);
        return monthTok.type() == TokenType.ALPHA_SEQ && MonthNames.resolve(monthTok.value()).isPresent();
    }

    private void classifyRfc850Body() {
        day = intOf(expect(TokenType.DIGIT_SEQ));
        expectSeparator("-");
        month = expectMonthName();
        expectSeparator("-");
        int rawYear = intOf(expect(TokenType.DIGIT_SEQ));
        year = expandYear(rawYear, config.getPivotYear());
        skipSpaces();
        skipAtLiteral();
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            parseTimeSection();
            parseAmPm();
            parseZoneSection();
        }
    }

    // -----------------------------------------------------------------------
    // Optional time suffix helper (for week/ordinal/compact formats)
    // -----------------------------------------------------------------------

    private void parseOptionalTimeSuffix() {
        if (cur >= tokens.size()) return;
        Token next = tokens.get(cur);
        if (next.type() == TokenType.T_LITERAL) {
            cur++;
        } else if (next.type() == TokenType.AT_LITERAL) {
            cur++;
            skipSpaces();
        } else if (next.type() == TokenType.SEPARATOR && " ".equals(next.value())) {
            cur++;
            skipAtLiteral(); // handle "... @ 12:00"
        } else {
            return;
        }
        if (cur >= tokens.size()) return;
        if (tokens.get(cur).type() != TokenType.DIGIT_SEQ) return;
        parseTimeSection();
        parseAmPm();
        parseZoneSection();
    }

    // -----------------------------------------------------------------------
    // RFC 9557 bracket annotations: [Europe/London], [u-ca=japanese]
    // -----------------------------------------------------------------------

    private void parseBracketAnnotations() {
        while (cur < tokens.size()
                && tokens.get(cur).type() == TokenType.SEPARATOR
                && "[".equals(tokens.get(cur).value())) {
            cur++; // consume '['
            StringBuilder content = new StringBuilder();
            while (cur < tokens.size()) {
                Token t = tokens.get(cur);
                if (t.type() == TokenType.SEPARATOR && "]".equals(t.value())) {
                    cur++; // consume ']'
                    break;
                }
                content.append(t.value());
                cur++;
            }
            String annotation = content.toString();
            // If it contains '/' and no '=', it's a timezone ID
            if (annotation.contains("/") && !annotation.contains("=")) {
                try {
                    zoneId = ZoneId.of(annotation);
                } catch (java.time.DateTimeException e) {
                    // silently ignore unrecognized zone annotations
                }
            }
            // else silently ignore (e.g. calendar annotations)
        }
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
            // DD Mon YYYY  or  DD Mon 'YY  or  7th Oct 1970
            day = intOf(t); cur++;
            skipOrdinalSuffix();
            skipSpaces();
            month = expectMonthName();
            skipSpaces();
            year = readYear();
        } else if (t.type() == TokenType.ALPHA_SEQ) {
            OptionalInt monthOpt = MonthNames.resolve(t.value());
            if (monthOpt.isEmpty()) {
                throw new DateParseException(original, "expected day number or month name, got: " + t.value());
            }
            // Mon DD YYYY  or  Mon DD, YYYY  or  Mon DD, 'YY  or  Oct. 7, 1970
            month = monthOpt.getAsInt(); cur++;
            // skip optional abbreviation period (e.g., "Oct." → "Oct" + DOT)
            if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DOT) {
                cur++;
            }
            skipSpaces();
            day = intOf(expect(TokenType.DIGIT_SEQ));
            skipOrdinalSuffix();
            // optional comma
            if (cur < tokens.size() && tokens.get(cur).type() == TokenType.SEPARATOR
                    && ",".equals(tokens.get(cur).value())) {
                cur++;
            }
            skipSpaces();
            year = readYear();
        } else {
            throw new DateParseException(original, "expected day number or month name, got: " + t.value());
        }

        skipSpaces();
        if (cur >= tokens.size()) return; // date only

        // Skip "at" keyword or '@' literal (e.g., "January 1, 1999 at 11:59 p.m. PST" or "Jan. 31, 1999 @ 12:00 PM")
        if (tokens.get(cur).type() == TokenType.ALPHA_SEQ
                && "AT".equalsIgnoreCase(tokens.get(cur).value())) {
            cur++;
            skipSpaces();
        } else if (tokens.get(cur).type() == TokenType.AT_LITERAL) {
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
    // CJK date formats: 年月日時分秒
    //
    // Label-after:  1999年12月31日 00時00分00秒 JST
    // Label-before: 年1999月12日31 時00分00秒00
    // -----------------------------------------------------------------------

    /**
     * Parses CJK label-after format: digits followed by their unit label.
     * Example: {@code 1999年12月31日 00時00分00秒 JST}
     * Entry: cur=0, tokens[0]=DIGIT_SEQ("1999"), tokens[1]=ALPHA_SEQ("年")
     */
    private void classifyCjkLabelAfter() {
        year = intOf(expect(TokenType.DIGIT_SEQ));
        expectCjkLabel(CJK_YEAR);
        month = intOf(expect(TokenType.DIGIT_SEQ));
        expectCjkLabel(CJK_MONTH);
        day = intOf(expect(TokenType.DIGIT_SEQ));
        expectCjkLabel(CJK_DAY);

        skipSpaces();
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
            hour = intOf(expect(TokenType.DIGIT_SEQ));
            expectCjkLabel(CJK_HOUR);
            minute = intOf(expect(TokenType.DIGIT_SEQ));
            expectCjkLabel(CJK_MINUTE);
            if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DIGIT_SEQ) {
                second = intOf(expect(TokenType.DIGIT_SEQ));
                // optional trailing 秒
                if (cur < tokens.size() && isCjkToken(tokens.get(cur), CJK_SECOND)) cur++;
            }
        }

        parseZoneSection();
    }

    /**
     * Parses CJK label-before format: unit label followed by its digits.
     * Example: {@code 年1999月12日31 時00分00秒00}
     * Entry: cur=0, tokens[0]=ALPHA_SEQ("年")
     */
    private void classifyCjkLabelBefore() {
        expectCjkLabel(CJK_YEAR);
        year = intOf(expect(TokenType.DIGIT_SEQ));
        expectCjkLabel(CJK_MONTH);
        month = intOf(expect(TokenType.DIGIT_SEQ));
        expectCjkLabel(CJK_DAY);
        day = intOf(expect(TokenType.DIGIT_SEQ));

        skipSpaces();
        if (cur < tokens.size() && isCjkToken(tokens.get(cur), CJK_HOUR)) {
            cur++;
            hour = intOf(expect(TokenType.DIGIT_SEQ));
            expectCjkLabel(CJK_MINUTE);
            minute = intOf(expect(TokenType.DIGIT_SEQ));
            if (cur < tokens.size() && isCjkToken(tokens.get(cur), CJK_SECOND)) {
                cur++;
                second = intOf(expect(TokenType.DIGIT_SEQ));
            }
        }

        parseZoneSection();
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
            frac = (frac + "000000000").substring(0, 9);
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
                && "M".equalsIgnoreCase(tokens.get(cur + 2).value())
                && tokens.get(cur + 3).type() == TokenType.DOT) {
            amPm = "P".equals(upper) ? 1 : -1;
            cur += 4;
            return;
        }

        if ("NOON".equals(upper)) {
            if (hour != 12 || minute != 0 || second != 0)
                throw new DateParseException(original, "noon requires exactly 12:00:00, got: " + hour + ":" + minute + ":" + second);
            amPm = 1;   // 12 PM → stays 12
            cur++;
            return;
        }
        if ("MIDNIGHT".equals(upper)) {
            if (hour != 12 || minute != 0 || second != 0)
                throw new DateParseException(original, "midnight requires exactly 12:00:00, got: " + hour + ":" + minute + ":" + second);
            amPm = -1;  // 12 AM → becomes 0
            cur++;
        }
    }

    /** Parses optional timezone info: Z, UTC, GMT, named abbr, +HHMM, -HHMM, +HH:MM, -HH:MM, GMT+HH:MM */
    private void parseZoneSection() {
        if (cur >= tokens.size()) return;

        skipSpaces();
        if (cur >= tokens.size()) return;

        Token t = tokens.get(cur);

        // Named abbreviation (including GMT/UTC with optional offset suffix)
        if (t.type() == TokenType.ALPHA_SEQ) {
            String upper = t.value().toUpperCase();

            // GMT+HH:MM / UTC+HH:MM / GMT-HH:MM / UTC-HH:MM — offset is authoritative
            if ("GMT".equals(upper) || "UTC".equals(upper)) {
                cur++; // consume GMT/UTC
                if (cur < tokens.size()) {
                    Token next = tokens.get(cur);
                    if (next.type() == TokenType.SIGN) {
                        char sign = next.value().charAt(0);
                        cur++;
                        try {
                            offset = ZoneResolver.parseNumericOffset(sign, parseOffsetDigits());
                        } catch (IllegalArgumentException e) {
                            throw new DateParseException(original, e.getMessage());
                        }
                        return;
                    }
                    if (next.type() == TokenType.SEPARATOR && "-".equals(next.value())) {
                        cur++;
                        try {
                            offset = ZoneResolver.parseNumericOffset('-', parseOffsetDigits());
                        } catch (IllegalArgumentException e) {
                            throw new DateParseException(original, e.getMessage());
                        }
                        return;
                    }
                }
                offset = ZoneOffset.UTC;
                return;
            }

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
            try {
                offset = ZoneResolver.parseNumericOffset(sign, digits);
            } catch (IllegalArgumentException e) {
                throw new DateParseException(original, e.getMessage());
            }
            return;
        }

        // '-' emitted as SEPARATOR can also be a negative offset sign in offset position
        if (t.type() == TokenType.SEPARATOR && "-".equals(t.value())) {
            cur++;
            String digits = parseOffsetDigits();
            try {
                offset = ZoneResolver.parseNumericOffset('-', digits);
            } catch (IllegalArgumentException e) {
                throw new DateParseException(original, e.getMessage());
            }
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
        // zoneId (from bracket annotation or named TZ) takes precedence over numeric offset
        if (zoneId != null) {
            return ZonedDateTime.of(ldt, zoneId);
        }
        if (offset != null) {
            return ZonedDateTime.of(ldt, offset);
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

    private void expectToken(TokenType type, String value) {
        if (cur >= tokens.size()) {
            throw new DateParseException(original,
                    "unexpected end of input, expected " + type + "('" + value + "')");
        }
        Token t = tokens.get(cur);
        if (t.type() != type || !value.equals(t.value())) {
            throw new DateParseException(original,
                    "expected " + type + "('" + value + "') at position " + t.position()
                            + ", got " + t.type() + "('" + t.value() + "')");
        }
        cur++;
    }

    private void expectSeparator(String value) { expectToken(TokenType.SEPARATOR, value); }

    private void expectCjkLabel(String label)  { expectToken(TokenType.ALPHA_SEQ, label); }

    private int expectMonthName() {
        Token t = expect(TokenType.ALPHA_SEQ);
        OptionalInt m = MonthNames.resolve(t.value());
        if (m.isEmpty()) {
            throw new DateParseException(original, "unrecognized month name: " + t.value());
        }
        // consume optional trailing period (abbreviation dot: "Oct.", "Jan.")
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.DOT) {
            cur++;
        }
        return m.getAsInt();
    }

    /**
     * Reads a year value, handling an optional leading apostrophe for 2-digit years.
     * {@code '70} is consumed as SEPARATOR("'") + DIGIT_SEQ("70") and expanded via pivotYear.
     * A bare 4-digit or 2-digit year without apostrophe is read as-is.
     */
    private int readYear() {
        boolean apostrophe = cur < tokens.size()
                && tokens.get(cur).type() == TokenType.SEPARATOR
                && "'".equals(tokens.get(cur).value());
        if (apostrophe) {
            cur++; // consume apostrophe
            int raw = intOf(expect(TokenType.DIGIT_SEQ));
            return expandYear(raw, config.getPivotYear());
        }
        return intOf(expect(TokenType.DIGIT_SEQ));
    }

    /** Skips ordinal day suffixes: st, nd, rd, th (e.g. "7th", "1st"). */
    private void skipOrdinalSuffix() {
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.ALPHA_SEQ) {
            String upper = tokens.get(cur).value().toUpperCase();
            if ("ST".equals(upper) || "ND".equals(upper) || "RD".equals(upper) || "TH".equals(upper)) {
                cur++;
            }
        }
    }

    private void skipAtLiteral() {
        if (cur < tokens.size() && tokens.get(cur).type() == TokenType.AT_LITERAL) {
            cur++;
            skipSpaces();
        }
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

    // -----------------------------------------------------------------------
    // Static helpers
    // -----------------------------------------------------------------------

    private static int intOf(Token t) {
        return Integer.parseInt(t.value());
    }

    /** Returns true if the token can serve as a date component separator (/, -, .). */
    private static boolean isDateSepToken(Token t) {
        if (t.type() == TokenType.DOT) return true;
        if (t.type() != TokenType.SEPARATOR) return false;
        return "/".equals(t.value()) || "-".equals(t.value());
    }

    /** Returns the separator string value for a date separator token (DOT → "."). */
    private static String dateSepValue(Token t) {
        return t.type() == TokenType.DOT ? "." : t.value();
    }

    /** Returns true if the token matches the given separator string. */
    private static boolean matchesSep(Token t, String sep) {
        if (".".equals(sep)) return t.type() == TokenType.DOT;
        return t.type() == TokenType.SEPARATOR && sep.equals(t.value());
    }

    private static boolean isCjkToken(Token t, String label) {
        return t.type() == TokenType.ALPHA_SEQ && label.equals(t.value());
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
