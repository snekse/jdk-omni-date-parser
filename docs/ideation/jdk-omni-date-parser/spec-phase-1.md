# Implementation Spec: jdk-omni-date-parser - Phase 1

**Contract**: ./contract.md
**Estimated Effort**: M

## Technical Approach

Phase 1 establishes the project foundation and the core parsing engine. The deliverables are: a working Gradle Kotlin DSL build, the public API surface (`OmniDateParser`, `OmniDateParserConfig`, `DateOrder`, `DateParseException`), and the lexer + state machine implementation with enough format support to parse ISO 8601 and RFC 2822 inputs end-to-end.

The parsing architecture is a two-stage pipeline. Stage 1 is a **Lexer** that walks the input string character by character, emitting a flat list of typed tokens (`DIGIT_SEQ`, `ALPHA_SEQ`, `SEPARATOR`, `SIGN`, `COLON`, `DOT`). Stage 2 is a **DateAssembler** that classifies those tokens into date fields (year, month, day, hour, minute, second, offset, timezone) using contextual rules and the caller's `OmniDateParserConfig`, then assembles the final `java.time` result.

`OmniDateParser` is an immutable config holder — it stores a final `OmniDateParserConfig` reference and creates a fresh `Lexer` + `DateAssembler` pair on each `parse()` call. This gives thread safety for free with no synchronization overhead, modeled after `java.time.format.DateTimeFormatter`.

## Feedback Strategy

**Inner-loop command**: `./gradlew test --tests "*.Iso8601Test" --tests "*.Rfc2822Test"`

**Playground**: Test suite — this phase is pure library logic with no UI or external services. Tests run in milliseconds.

**Why this approach**: All work is in the parsing engine. A scoped test run catches regressions immediately and gives clear signal on which format family broke.

## File Changes

### New Files

| File Path | Purpose |
|-----------|---------|
| `build.gradle.kts` | Gradle Kotlin DSL build: Java 21 toolchain, Lombok, JUnit 5, JMH plugin scaffold, Maven Central publish scaffold |
| `settings.gradle.kts` | Project name and version catalog pointer |
| `gradle/libs.versions.toml` | Version catalog: Lombok 1.18.30+, JUnit 5, JMH |
| `src/main/java/io/github/snekse/jdk/dateparser/OmniDateParser.java` | Public API — static convenience methods + configurable instance |
| `src/main/java/io/github/snekse/jdk/dateparser/OmniDateParserConfig.java` | Immutable config (builder pattern). Holds `DateOrder`, `defaultZone`, pivot year. |
| `src/main/java/io/github/snekse/jdk/dateparser/DateOrder.java` | Enum: `MDY`, `DMY`, `YMD` |
| `src/main/java/io/github/snekse/jdk/dateparser/DateParseException.java` | Unchecked exception with descriptive message |
| `src/main/java/io/github/snekse/jdk/dateparser/internal/Lexer.java` | Character-by-character tokenizer |
| `src/main/java/io/github/snekse/jdk/dateparser/internal/Token.java` | Value record: `TokenType type`, `String value`, `int position` |
| `src/main/java/io/github/snekse/jdk/dateparser/internal/TokenType.java` | Enum of raw token categories emitted by the Lexer |
| `src/main/java/io/github/snekse/jdk/dateparser/internal/DateAssembler.java` | Classifies token list into date fields; resolves ambiguity; produces `java.time` results |
| `src/main/java/io/github/snekse/jdk/dateparser/internal/ZoneResolver.java` | Maps named TZ abbreviations and offset strings to `ZoneOffset` / `ZoneId` |
| `src/test/java/io/github/snekse/jdk/dateparser/Iso8601Test.java` | Parameterized tests for ISO 8601 inputs |
| `src/test/java/io/github/snekse/jdk/dateparser/Rfc2822Test.java` | Parameterized tests for RFC 2822 inputs |
| `src/test/java/io/github/snekse/jdk/dateparser/DateParseExceptionTest.java` | Verifies exception message content for bad inputs |

### Modified Files

_(None — greenfield phase)_

## Implementation Details

### Build Setup

**Overview**: Gradle Kotlin DSL project targeting Java 21. Lombok via annotation processor. JUnit 5 via `useJUnitPlatform()`. JMH plugin added but not configured until Phase 3. Maven Central publishing block scaffolded with `TODO: group ID` comment.

```kotlin
// build.gradle.kts (key sections)
plugins {
    java
    `java-library`
    id("io.github.reyerizo.jmh") version "0.8.2"   // scaffolded, configured in Phase 3
    `maven-publish`
    signing
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(libs.junitJupiter)
    testRuntimeOnly(libs.junitPlatformLauncher)
}

tasks.test { useJUnitPlatform() }
```

```toml
# gradle/libs.versions.toml
[versions]
lombok = "1.18.36"
junit = "5.11.4"
jmh = "1.37"

[libraries]
lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
junitJupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }
junitPlatformLauncher = { module = "org.junit.platform:junit-platform-launcher" }
jmhCore = { module = "org.openjdk.jmh:jmh-core", version.ref = "jmh" }
jmhGeneratorAnnotationProcessor = { module = "org.openjdk.jmh:jmh-generator-annprocess", version.ref = "jmh" }
```

**Implementation steps**:
1. Create `settings.gradle.kts` with `rootProject.name = "jdk-omni-date-parser"`
2. Create `gradle/libs.versions.toml` with versions above
3. Create `build.gradle.kts` with toolchain, dependencies, test config, and stubbed publish block
4. Run `./gradlew build` to verify the project compiles (no sources yet)

### Public API (`OmniDateParser`, `OmniDateParserConfig`, `DateOrder`, `DateParseException`)

**Overview**: The public surface the caller sees. `OmniDateParser` is an immutable config holder with static convenience methods for zero-config use and instance methods for configured use.

```java
// OmniDateParser.java — package io.github.snekse.jdk.dateparser
@Value  // Lombok: final fields
public class OmniDateParser {

    OmniDateParserConfig config;

    // Zero-config static convenience methods (UTC default zone, MDY order)
    public static ZonedDateTime toZonedDateTime(String input) {
        return DEFAULT.parseZonedDateTime(input);
    }
    public static LocalDate toLocalDate(String input) { ... }  // strips time/zone silently
    public static LocalDateTime toLocalDateTime(String input) { ... }
    public static Instant toInstant(String input) { ... }

    // Configurable instance methods
    public ZonedDateTime parseZonedDateTime(String input) {
        return new DateAssembler(input, config).assembleZonedDateTime();
    }
    // ... parseLocalDate, parseLocalDateTime, parseInstant

    private static final OmniDateParser DEFAULT =
        new OmniDateParser(OmniDateParserConfig.defaults());
}
```

```java
// OmniDateParserConfig.java
@Value @Builder
public class OmniDateParserConfig {
    /**
     * Controls component ordering for ambiguous numeric inputs (e.g. "10/11/12").
     * Only applied when the parser cannot determine order from heuristics alone.
     * Default: MDY (US convention).
     */
    @Builder.Default DateOrder dateOrder = DateOrder.MDY;

    /**
     * Timezone applied when the input contains no timezone information.
     * Accepts any ZoneId:
     *   ZoneOffset.UTC               → always UTC (default)
     *   ZoneId.systemDefault()       → JVM system timezone
     *   ZoneId.of("Asia/Kolkata")    → specific zone, e.g. India (UTC+5:30)
     *   ZoneId.of("Asia/Kathmandu")  → specific zone, e.g. Nepal (UTC+5:45)
     * Note: half-hour and 45-minute offsets are fully supported via ZoneId.
     */
    @Builder.Default ZoneId defaultZone = ZoneOffset.UTC;

    /**
     * Two-digit year cutoff: years ≤ pivotYear map to 20xx, years > pivotYear map to 19xx.
     * Example with default 70: "99" → 1999, "70" → 2070, "69" → 2069.
     */
    @Builder.Default int pivotYear = 70;

    public static OmniDateParserConfig defaults() {
        return builder().build();
    }
}
```

```java
// DateOrder.java
public enum DateOrder {
    MDY,  // month / day / year  (US default)
    DMY,  // day / month / year  (European)
    YMD   // year / month / day  (ISO-adjacent)
}
```

```java
// DateParseException.java
public class DateParseException extends RuntimeException {
    public DateParseException(String input, String reason) {
        super("Cannot parse date: \"" + input + "\" — " + reason);
    }
}
```

**Key decisions**:
- Lombok `@Value` + `@Builder` on config — immutable by default, easy to construct
- `DEFAULT` instance is a class-level constant — shared safely across threads
- Static methods delegate to `DEFAULT` — zero-config callers pay no allocation cost
- `toLocalDate()` strips time and zone silently — the caller asked for a date, and the input's declared timezone determines which date it is; no opt-in required

**Implementation steps**:
1. Create `DateParseException` (trivial, no dependencies)
2. Create `DateOrder` enum (`MDY`, `DMY`, `YMD` only — other orderings don't exist as real-world conventions)
3. Create `OmniDateParserConfig` with Lombok `@Value @Builder`, including `defaultZone`, `dateOrder`, `pivotYear`
4. Create stub `OmniDateParser` with static methods that throw `UnsupportedOperationException` — fills in after the engine is built

### Lexer (tokenizer)

**Overview**: Walks the input string one character at a time, collecting runs of similar characters into tokens. Emits a `List<Token>`. Does not interpret meaning — that is the assembler's job.

```java
// TokenType.java
public enum TokenType {
    DIGIT_SEQ,   // one or more digits: "1999", "01", "12"
    ALPHA_SEQ,   // one or more letters: "Jan", "Friday", "EST", "UTC", "PM", "Uhr"
    SEPARATOR,   // single: - / . , space (each emitted separately)
    COLON,       // :
    SIGN,        // + or -  (when appearing before a digit offset)
    DOT,         // . (fractional seconds separator — distinct from SEPARATOR in some contexts)
    T_LITERAL,   // literal 'T' between date and time in ISO 8601
}
```

```java
// Token.java — Java 21 record
public record Token(TokenType type, String value, int position) {}
```

```java
// Lexer.java
public class Lexer {
    private final String input;
    private int pos = 0;

    public Lexer(String input) {
        this.input = input.strip();
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isDigit(c))       tokens.add(readDigitSeq());
            else if (Character.isLetter(c)) tokens.add(readAlphaSeq());
            else                            tokens.add(readPunctuation(c));
        }
        return tokens;
    }

    private Token readDigitSeq() { ... }
    private Token readAlphaSeq() { ... }
    private Token readPunctuation(char c) { ... }  // handles -, /, ., :, +, -, T
}
```

**Key decisions**:
- `T_LITERAL` is only emitted for an uppercase `T` surrounded by digit sequences (ISO 8601 date-time separator). Otherwise `T` falls into `ALPHA_SEQ`.
- Each space is emitted as its own `SEPARATOR` token (not accumulated) — simplifies assembler matching
- `strip()` on input before tokenizing handles leading/trailing whitespace

**Feedback loop**:
- **Playground**: Create `LexerTest.java` with a smoke test: `assertThat(new Lexer("1999-01-01").tokenize()).hasSize(5)` before writing the lexer
- **Experiment**: Tokenize `"1999-01-01T12:00:00Z"`, `"Fri, 01 Jan 1999 23:59:00 +0000"`, and `"January 1, 1999"` — verify each token list matches expected types and values
- **Check command**: `./gradlew test --tests "*LexerTest"`

**Implementation steps**:
1. Write `Token` record and `TokenType` enum
2. Write `Lexer` with `readDigitSeq()` and `readAlphaSeq()` first (most of the work)
3. Add `readPunctuation()` handling each symbol character
4. Add `T_LITERAL` detection: peek ahead — if current char is `T` and previous token was a `DIGIT_SEQ` and next char is a digit, emit `T_LITERAL`; otherwise fall through to `ALPHA_SEQ`

### DateAssembler — ISO 8601 + RFC 2822

**Overview**: Takes the `List<Token>` from the Lexer and classifies each token into a date field, then builds the `java.time` result. Phase 1 implements only ISO 8601 and RFC 2822 patterns; Phase 2 adds the rest.

The assembler uses the token list structure (not regex) to detect which format family applies:
- Starts with 4-digit `DIGIT_SEQ` → ISO family (YYYY-first)
- Starts with `ALPHA_SEQ` (3-letter) followed by `,` → RFC 2822 (day-of-week prefix)
- Starts with `ALPHA_SEQ` (3-letter) followed by digit → RFC 2822 without day-of-week

```java
// DateAssembler.java
public class DateAssembler {
    private final List<Token> tokens;
    private final OmniDateParserConfig config;
    private final String original; // for error messages

    // Extracted fields (mutable during assembly)
    private int year = -1, month = -1, day = -1;
    private int hour = 0, minute = 0, second = 0, nano = 0;
    private ZoneOffset offset = ZoneOffset.UTC;
    private ZoneId zoneId = null;

    public ZonedDateTime assembleZonedDateTime() {
        classify();
        validate();
        return buildZonedDateTime();
    }

    private void classify() {
        // Detect format family from token structure, then route
    }
}
```

**ISO 8601 token patterns to handle in Phase 1**:
- `YYYY-MM-DD` (date only)
- `YYYY-MM-DDTHH:MM:SSZ`
- `YYYY-MM-DDTHH:MM:SS+HH:MM`
- `YYYY-MM-DD HH:MM:SS +HHMM`
- `YYYY-MM-DDTHH:MM:SS.sssZ`
- `YYYY-MM-DD HH:MM TZ_ABBR` (UTC, GMT)

**RFC 2822 token patterns to handle in Phase 1**:
- `Fri, 01 Jan 1999 23:59:00 +0000`
- `01 Jan 1999 23:59:00 +0000` (no day-of-week)

**Key decisions**:
- `ZoneResolver` (separate class) handles mapping UTC/GMT to `ZoneOffset.UTC` in Phase 1; extended in Phase 2 for full TZ abbreviation list
- 4-digit year is never ambiguous — `DateOrder` only applies when year could be 2-digit
- If no timezone info is present in the input, default to UTC (not system timezone)

**Feedback loop**:
- **Playground**: `Iso8601Test.java` and `Rfc2822Test.java` with `@ParameterizedTest @ValueSource` for each format variant
- **Experiment**: Test each ISO variant; test with and without milliseconds; test with positive and negative offsets; test RFC 2822 with and without day-of-week prefix
- **Check command**: `./gradlew test --tests "*.Iso8601Test" --tests "*.Rfc2822Test"`

**Implementation steps**:
1. Write `ZoneResolver` stub — handles UTC, GMT, Z for now
2. Write `classify()` dispatch logic in `DateAssembler` (format family detection from token list)
3. Implement ISO 8601 classification branch: extract year, month, day, time, offset from token list positionally
4. Implement RFC 2822 classification branch: handle optional day-of-week prefix, month name → month number mapping
5. Implement `validate()` — range-check all extracted fields, throw `DateParseException` if invalid
6. Implement `buildZonedDateTime()` — assemble `ZonedDateTime` from fields; for `toLocalDate()`, extract `.toLocalDate()`
7. Wire `DateAssembler` into `OmniDateParser` methods (replace `UnsupportedOperationException` stubs)

## Testing Requirements

### Unit Tests

| Test File | Coverage |
|-----------|---------|
| `Iso8601Test.java` | All ISO 8601 variants from examples.txt lines 1–11, 34–36, 65–67, 74–76 |
| `Rfc2822Test.java` | RFC 2822 with and without day-of-week, `+0000` and named offset |
| `DateParseExceptionTest.java` | Bad input: empty string, null, letters-only, truncated date |
| `LexerTest.java` | Token list shape for representative inputs |

**Key test cases**:
- `"1999-01-01T00:00:00Z"` → `ZonedDateTime` at UTC
- `"1999-01-01T00:00:00.000+05:30"` → `ZonedDateTime` with +05:30 offset
- `"1999-01-01 12:00:00 -0500"` → correct offset
- `"Fri, 01 Jan 1999 23:59:00 +0000"` → correct RFC 2822 parse
- `"1999-01-31"` (date only) → `toLocalDate()` returns `1999-01-31`
- `""` (empty) → `DateParseException`
- `"not-a-date"` → `DateParseException` with descriptive message

## Error Handling

| Scenario | Handling |
|----------|---------|
| Null input | Throw `DateParseException` immediately with message `"input must not be null"` |
| Empty / blank input | Throw `DateParseException` with message |
| Token list does not match any known pattern | Throw `DateParseException("Cannot parse date: \"...\""`) |
| Valid tokens but invalid field values (month=13) | Throw `DateParseException` in `validate()` step |

## Validation Commands

```bash
# Build and compile
./gradlew build

# Run all tests
./gradlew test

# Run scoped to Phase 1 tests
./gradlew test --tests "*.Iso8601Test" --tests "*.Rfc2822Test" --tests "*.LexerTest" --tests "*.DateParseExceptionTest"
```

## Open Items

- [ ] Confirm Apache 2.0 `LICENSE` file is added to the repo root before first release

---

_This spec is ready for implementation. Follow the patterns and validate at each step._
