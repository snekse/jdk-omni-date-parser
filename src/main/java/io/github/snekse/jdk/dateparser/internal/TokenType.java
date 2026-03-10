package io.github.snekse.jdk.dateparser.internal;

public enum TokenType {
    DIGIT_SEQ,   // one or more digits: "1999", "01", "12"
    ALPHA_SEQ,   // one or more letters: "Jan", "Friday", "EST", "UTC", "PM"
    SEPARATOR,   // single: - / , space (each emitted separately)
    COLON,       // :
    SIGN,        // + or - (when appearing before a digit offset)
    DOT,         // . (fractional seconds separator)
    T_LITERAL,   // literal 'T' between date and time in ISO 8601
    AT_LITERAL,  // literal '@' as date-time separator (e.g. 2013-Feb-03@12:30:00)
    W_LITERAL,   // literal 'W' in ISO 8601 week dates (e.g. 2004-W53-6)
}
