package io.github.snekse.jdk.dateparser.internal;

import java.util.ArrayList;
import java.util.List;

public class Lexer {

    private final String input;
    private int pos = 0;

    public Lexer(String input) {
        this.input = input == null ? "" : input.strip();
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c)) {
                tokens.add(readDigitSeq());
            } else if (c == 'T' && isTLiteral(tokens)) {
                tokens.add(new Token(TokenType.T_LITERAL, "T", pos++));
            } else if (c == 'W' && isWLiteral(tokens)) {
                tokens.add(new Token(TokenType.W_LITERAL, "W", pos++));
            } else if (Character.isLetter(c)) {
                tokens.add(readAlphaSeq());
            } else {
                tokens.add(readPunctuation(c));
            }
        }
        return tokens;
    }

    /**
     * T_LITERAL: current char is uppercase 'T', previous token was DIGIT_SEQ,
     * and next char exists and is a digit.
     */
    private boolean isTLiteral(List<Token> tokens) {
        if (tokens.isEmpty()) return false;
        if (tokens.get(tokens.size() - 1).type() != TokenType.DIGIT_SEQ) return false;
        return pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1));
    }

    /**
     * W_LITERAL: current char is uppercase 'W', previous token is DIGIT_SEQ (4-digit year)
     * or SEPARATOR("-"), and next char exists and is a digit.
     */
    private boolean isWLiteral(List<Token> tokens) {
        if (tokens.isEmpty()) return false;
        Token prev = tokens.get(tokens.size() - 1);
        if (prev.type() != TokenType.DIGIT_SEQ && !(prev.type() == TokenType.SEPARATOR && "-".equals(prev.value()))) {
            return false;
        }
        return pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1));
    }

    private Token readDigitSeq() {
        int start = pos;
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            sb.append(input.charAt(pos++));
        }
        return new Token(TokenType.DIGIT_SEQ, sb.toString(), start);
    }

    private Token readAlphaSeq() {
        int start = pos;
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && Character.isLetter(input.charAt(pos))) {
            sb.append(input.charAt(pos++));
        }
        return new Token(TokenType.ALPHA_SEQ, sb.toString(), start);
    }

    private Token readPunctuation(char c) {
        int start = pos++;
        return switch (c) {
            case ':' -> new Token(TokenType.COLON, ":", start);
            case '.' -> new Token(TokenType.DOT, ".", start);
            case '+' -> new Token(TokenType.SIGN, "+", start);
            case '@' -> new Token(TokenType.AT_LITERAL, "@", start);
            case '-' -> {
                // '-' is SIGN only if it appears after a space (offset context) or at start;
                // otherwise it's a SEPARATOR (date component delimiter).
                // We emit SEPARATOR for date separators; SIGN is determined by context
                // in the assembler. For the lexer, emit SEPARATOR for '-'.
                yield new Token(TokenType.SEPARATOR, "-", start);
            }
            default -> new Token(TokenType.SEPARATOR, String.valueOf(c), start);
        };
    }
}
