package io.github.snekse.jdk.dateparser.internal;

public enum TokenType {
    DIGIT_SEQ,   // one or more digits: "1999", "01", "12"
    ALPHA_SEQ,   // one or more letters: "Jan", "Friday", "EST", "UTC", "PM"
    SEPARATOR,   // single: - / , space (each emitted separately)
    COLON,       // :
    SIGN,        // + or - (when appearing before a digit offset)
    DOT,         // . (fractional seconds separator)
    T_LITERAL,   // literal 'T' between date and time in ISO 8601
}
