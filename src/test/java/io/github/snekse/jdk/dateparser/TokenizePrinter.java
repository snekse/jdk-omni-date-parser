package io.github.snekse.jdk.dateparser;

import io.github.snekse.jdk.dateparser.internal.Lexer;
import io.github.snekse.jdk.dateparser.internal.Token;

import java.util.List;

/**
 * Skill utility — prints the Lexer token stream and OmniDateParser parse result for a
 * given input string. Run via:
 *   bash .claude/skills/tokenize-date-input/tokenize.sh "12:00 noon EST"
 */
public class TokenizePrinter {

    public static void main(String[] args) {
        String input = args.length > 0 ? String.join(" ", args) : "";
        List<Token> tokens = new Lexer(input).tokenize();

        System.out.printf("Input: %s%n", input);
        System.out.printf("%-4s %-14s %-22s %s%n", "#", "Type", "Value", "Pos");
        System.out.println("-".repeat(48));
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            System.out.printf("%-4d %-14s %-22s %d%n", i, t.type(), "\"" + t.value() + "\"", t.position());
        }

        System.out.println();
        try {
            var result = OmniDateParser.toZonedDateTime(input);
            System.out.println("Parse result: " + result);
        } catch (Exception e) {
            System.out.println("Parse result: ERROR — " + e.getMessage());
        }
    }
}
