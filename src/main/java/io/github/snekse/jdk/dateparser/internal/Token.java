package io.github.snekse.jdk.dateparser.internal;

public record Token(TokenType type, String value, int position) {}
