package io.github.snekse.jdk.dateparser;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests driven by text fixture files:
 * <ul>
 *   <li>{@code examples.txt} — in-scope formats that must parse successfully</li>
 *   <li>{@code invalid-examples.txt} — invalid data values that must throw</li>
 *   <li>{@code unsupported-examples.txt} — valid but out-of-scope formats that must throw</li>
 * </ul>
 */
class ExamplesTxtTest {

    static Stream<Arguments> examplesSource() throws IOException {
        return readLines("examples.txt");
    }

    @ParameterizedTest(name = "line {0}: {1}")
    @MethodSource("examplesSource")
    void parsesWithoutError(int lineNumber, String input) {
        assertDoesNotThrow(() -> OmniDateParser.toZonedDateTime(input),
                "Failed to parse line " + lineNumber + ": " + input);
    }

    static Stream<Arguments> invalidExamplesSource() throws IOException {
        return readLines("invalid-examples.txt");
    }

    @ParameterizedTest(name = "line {0}: {1}")
    @MethodSource("invalidExamplesSource")
    void invalidInputThrows(int lineNumber, String input) {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime(input),
                "Expected DateParseException for line " + lineNumber + ": " + input);
    }

    static Stream<Arguments> unsupportedExamplesSource() throws IOException {
        return readLines("unsupported-examples.txt");
    }

    @ParameterizedTest(name = "line {0}: {1}")
    @MethodSource("unsupportedExamplesSource")
    void unsupportedFormatThrows(int lineNumber, String input) {
        assertThrows(DateParseException.class,
                () -> OmniDateParser.toZonedDateTime(input),
                "Expected DateParseException for unsupported format at line " + lineNumber + ": " + input);
    }

    private static Stream<Arguments> readLines(String resourceName) throws IOException {
        InputStream is = ExamplesTxtTest.class.getClassLoader().getResourceAsStream(resourceName);
        if (is == null) throw new IOException(resourceName + " not found in test resources");
        List<Arguments> args = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) continue;
                args.add(Arguments.of(lineNumber, line));
            }
        }
        return args.stream();
    }
}
