package io.github.snekse.jdk.dateparser;

import io.github.snekse.jdk.dateparser.internal.Lexer;
import io.github.snekse.jdk.dateparser.internal.Token;
import io.github.snekse.jdk.dateparser.internal.TokenType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LexerTest {

    @Test
    void smoke_isoDate() {
        // "1999-01-01" → [DIGIT_SEQ, SEPARATOR, DIGIT_SEQ, SEPARATOR, DIGIT_SEQ]
        List<Token> tokens = new Lexer("1999-01-01").tokenize();
        assertEquals(5, tokens.size());
        assertEquals(TokenType.DIGIT_SEQ, tokens.get(0).type());
        assertEquals("1999", tokens.get(0).value());
        assertEquals(TokenType.SEPARATOR, tokens.get(1).type());
        assertEquals(TokenType.DIGIT_SEQ, tokens.get(2).type());
        assertEquals("01", tokens.get(2).value());
    }

    @Test
    void iso_datetime_with_T_literal() {
        // "1999-01-01T12:00:00Z"
        List<Token> tokens = new Lexer("1999-01-01T12:00:00Z").tokenize();
        // DIGIT_SEQ(1999) SEP(-) DIGIT_SEQ(01) SEP(-) DIGIT_SEQ(01)
        // T_LITERAL(T) DIGIT_SEQ(12) COLON(:) DIGIT_SEQ(00) COLON(:) DIGIT_SEQ(00)
        // ALPHA_SEQ(Z)
        boolean hasT = tokens.stream().anyMatch(t -> t.type() == TokenType.T_LITERAL && t.value().equals("T"));
        assertTrue(hasT, "Expected T_LITERAL token for ISO 8601 date-time separator");
    }

    @Test
    void rfc2822_shape() {
        // "Fri, 01 Jan 1999 23:59:00 +0000"
        List<Token> tokens = new Lexer("Fri, 01 Jan 1999 23:59:00 +0000").tokenize();
        // ALPHA_SEQ(Fri) SEPARATOR(,) SEPARATOR( ) DIGIT_SEQ(01) SEPARATOR( )
        // ALPHA_SEQ(Jan) SEPARATOR( ) DIGIT_SEQ(1999) SEPARATOR( )
        // DIGIT_SEQ(23) COLON(:) DIGIT_SEQ(59) COLON(:) DIGIT_SEQ(00) SEPARATOR( )
        // SIGN(+) DIGIT_SEQ(0000)
        assertEquals(TokenType.ALPHA_SEQ, tokens.get(0).type());
        assertEquals("Fri", tokens.get(0).value());
        boolean hasSign = tokens.stream().anyMatch(t -> t.type() == TokenType.SIGN && t.value().equals("+"));
        assertTrue(hasSign, "Expected SIGN token for +0000 offset");
    }

    @Test
    void spelled_out_month() {
        // "January 1, 1999"
        List<Token> tokens = new Lexer("January 1, 1999").tokenize();
        assertEquals(TokenType.ALPHA_SEQ, tokens.get(0).type());
        assertEquals("January", tokens.get(0).value());
    }

    @Test
    void leading_trailing_whitespace_stripped() {
        List<Token> tokens1 = new Lexer("  1999-01-01  ").tokenize();
        List<Token> tokens2 = new Lexer("1999-01-01").tokenize();
        assertEquals(tokens2.size(), tokens1.size());
    }

    @Test
    void iso_week_date_W_literal_extended() {
        // "2004-W53-6" → DIGIT_SEQ(2004) SEP(-) W_LITERAL(W) DIGIT_SEQ(53) SEP(-) DIGIT_SEQ(6)
        List<Token> tokens = new Lexer("2004-W53-6").tokenize();
        boolean hasW = tokens.stream().anyMatch(t -> t.type() == TokenType.W_LITERAL && t.value().equals("W"));
        assertTrue(hasW, "Expected W_LITERAL token for ISO 8601 week date");
    }

    @Test
    void iso_week_date_W_literal_basic() {
        // "2004W536" → DIGIT_SEQ(2004) W_LITERAL(W) DIGIT_SEQ(536)
        List<Token> tokens = new Lexer("2004W536").tokenize();
        assertEquals(3, tokens.size());
        assertEquals(TokenType.W_LITERAL, tokens.get(1).type());
    }

    @Test
    void W_not_literal_when_standalone() {
        // "W" at start of input should be ALPHA_SEQ, not W_LITERAL
        List<Token> tokens = new Lexer("Wednesday").tokenize();
        assertEquals(TokenType.ALPHA_SEQ, tokens.get(0).type());
    }

    @Test
    void fractional_seconds_dot() {
        // "1999-01-01T12:00:00.123Z" — dot before 123 should be DOT token
        List<Token> tokens = new Lexer("1999-01-01T12:00:00.123Z").tokenize();
        boolean hasDot = tokens.stream().anyMatch(t -> t.type() == TokenType.DOT);
        assertTrue(hasDot, "Expected DOT token for fractional seconds separator");
    }
}
