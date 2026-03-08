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
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Acceptance test: reads all in-scope rows from examples.txt and verifies each
 * parses without throwing an exception.
 */
class ExamplesTxtTest {

    // Lines excluded from scope (1-indexed): CJK, German Uhr/MEZ, noon, hrs
    private static final Set<Integer> EXCLUDED_LINES = Set.of(
            18, 20, 21, 31, 32, 33, 50, 51, 52, 54, 62, 63, 64, 72, 73, 79, 81, 82
    );

    static Stream<Arguments> examplesSource() throws IOException {
        InputStream is = ExamplesTxtTest.class.getClassLoader()
                .getResourceAsStream("examples.txt");
        if (is == null) {
            throw new IOException("examples.txt not found in test resources");
        }
        List<Arguments> args = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                if (EXCLUDED_LINES.contains(lineNumber)) continue;
                args.add(Arguments.of(lineNumber, line));
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "line {0}: {1}")
    @MethodSource("examplesSource")
    void parsesWithoutError(int lineNumber, String input) {
        assertDoesNotThrow(() -> OmniDateParser.toZonedDateTime(input),
                "Failed to parse line " + lineNumber + ": " + input);
    }
}
