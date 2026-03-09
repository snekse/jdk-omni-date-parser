# Implementation Spec: jdk-omni-date-parser - Phase 2

**Contract**: ./contract.md
**Estimated Effort**: L

## Technical Approach

Phase 2 extends the `DateAssembler` with the remaining format families and implements full ambiguity resolution. By the end of this phase every in-scope row in `examples.txt` should parse correctly.

The key complexity here is **ambiguity resolution**: inputs like `10/11/12` have three numeric components and no structural cue to determine which is year, month, and day. The `DateOrder` config (established in Phase 1) drives the classification decision. Additionally, any two-digit number that could be a year needs the pivot-year config to resolve to a full 4-digit year.

A secondary challenge is **named timezone abbreviation mapping**. Names like `EST`, `CET`, `HKT`, `NZDT`, `WET`, `KST`, `BST` are not standardized in `java.time` — they require a lookup table mapping abbreviation → `ZoneId` (e.g., `EST` → `America/New_York`). This lives in `ZoneResolver` which was stubbed in Phase 1.

The `DateAssembler.classify()` method, begun in Phase 1, gets the remaining format branches added here:
- Numeric-first Western (DD/MM/YYYY, MM/DD/YYYY, etc.)
- Alpha-first spelled-out month (`January 1, 1999`, `1 January 1999`, `Jan 1, 1999`)
- 12-hour clock with AM/PM / a.m./p.m.
- Compact numeric (`19990101`)
- All named timezone abbreviations

## Feedback Strategy

**Inner-loop command**: `./gradlew test --tests "*.ExamplesTxtTest"`

**Playground**: Test suite — the `ExamplesTxtTest` parameterized test reads the in-scope rows from `examples.txt` directly. Adding a new format variant and seeing it turn green is the tightest feedback loop available.

**Why this approach**: The examples.txt fixture is the acceptance test for this phase. Running it scoped gives sub-second feedback on which format families are passing.

## File Changes

### New Files

| File Path | Purpose |
|-----------|---------|
| `src/main/java/io/github/snekse/jdk/dateparser/internal/MonthNames.java` | Map of English month names (long + abbreviated) → month number |
| `src/main/java/io/github/snekse/jdk/dateparser/internal/TzAbbreviations.java` | Lookup table: TZ abbreviation string → `ZoneId` |
| `src/test/java/io/github/snekse/jdk/dateparser/ExamplesTxtTest.java` | Parameterized test reading in-scope rows from `examples.txt` |
| `src/test/java/io/github/snekse/jdk/dateparser/WesternFormatsTest.java` | Unit tests for slash/dot/dash Western formats |
| `src/test/java/io/github/snekse/jdk/dateparser/SpelledOutMonthTest.java` | Unit tests for English spelled-out month formats |
| `src/test/java/io/github/snekse/jdk/dateparser/AmPmTest.java` | Unit tests for 12-hour clock with AM/PM and a.m./p.m. |
| `src/test/java/io/github/snekse/jdk/dateparser/AmbiguityResolutionTest.java` | Tests for DateOrder.MDY / DMY / YMD on ambiguous inputs |
| `src/test/java/io/github/snekse/jdk/dateparser/ConcurrencyTest.java` | Verifies multiple threads sharing one OmniDateParser instance produce correct results |

### Modified Files

| File Path | Changes |
|-----------|---------|
| `src/main/java/io/github/snekse/jdk/dateparser/internal/DateAssembler.java` | Add classify() branches for all remaining format families |
| `src/main/java/io/github/snekse/jdk/dateparser/internal/ZoneResolver.java` | Replace stub with full TZ abbreviation lookup + numeric offset parsing |

## Implementation Details

### Named Timezone Abbreviations (`TzAbbreviations`, `ZoneResolver`)

**Overview**: Maps abbreviation strings to `ZoneId`. This is the most data-heavy component of Phase 2. The mapping must cover every abbreviation appearing in `examples.txt` plus the full sisyphsu showcase list.

```java
// TzAbbreviations.java
final class TzAbbreviations {
    private static final Map<String, ZoneId> MAP = Map.ofEntries(
        Map.entry("UTC",  ZoneOffset.UTC),
        Map.entry("GMT",  ZoneOffset.UTC),
        Map.entry("Z",    ZoneOffset.UTC),
        Map.entry("EST",  ZoneId.of("America/New_York")),
        Map.entry("EDT",  ZoneId.of("America/New_York")),
        Map.entry("CST",  ZoneId.of("America/Chicago")),   // US Central; not China Standard Time
        Map.entry("CDT",  ZoneId.of("America/Chicago")),
        Map.entry("MST",  ZoneId.of("America/Denver")),
        Map.entry("PST",  ZoneId.of("America/Los_Angeles")),
        Map.entry("PDT",  ZoneId.of("America/Los_Angeles")),
        Map.entry("CET",  ZoneId.of("Europe/Paris")),
        Map.entry("CEST", ZoneId.of("Europe/Paris")),
        Map.entry("WET",  ZoneId.of("Europe/Lisbon")),
        Map.entry("BST",  ZoneId.of("Europe/London")),
        Map.entry("JST",  ZoneId.of("Asia/Tokyo")),
        Map.entry("HKT",  ZoneId.of("Asia/Hong_Kong")),
        Map.entry("KST",  ZoneId.of("Asia/Seoul")),
        Map.entry("NZDT", ZoneId.of("Pacific/Auckland")),
        Map.entry("NZST", ZoneId.of("Pacific/Auckland"))
        // ... add all abbreviations from sisyphsu showcase
    );

    static Optional<ZoneId> resolve(String abbr) {
        return Optional.ofNullable(MAP.get(abbr.toUpperCase()));
    }
}
```

```java
// ZoneResolver.java (extended)
final class ZoneResolver {
    // Try named abbreviation first
    // Fall through to numeric offset parsing (+0500, +05:30, GMT+08:00)
    static ZoneId resolve(String token) {
        return TzAbbreviations.resolve(token)
            .orElseGet(() -> parseNumericOffset(token));
    }

    private static ZoneId parseNumericOffset(String token) {
        // handles +0500, -0500, +05:30, -05:30, GMT+08:00
    }
}
```

**Key decisions**:
- `CST` maps to `America/Chicago` (US Central). This is documented in a comment in `TzAbbreviations.java`. China Standard Time is not in scope.
- Named abbreviations return `ZoneId` (not `ZoneOffset`) so that DST-aware zones are represented correctly.
- Unknown abbreviations throw `DateParseException` with the abbreviation in the message, not a silent UTC fallback.

**Implementation steps**:
1. Write `TzAbbreviations` with the full lookup table (all abbreviations from examples.txt + sisyphsu showcase)
2. Extend `ZoneResolver.resolve()` to call `TzAbbreviations` first, then fall back to numeric offset parsing
3. Add numeric offset parser for `+HHMM`, `+HH:MM`, `GMT+HH:MM` formats

### `DateOrder` Ambiguity Resolution

**Overview**: `DateOrder` is **only consulted when the parser cannot determine component roles from the input alone**. Most inputs are not ambiguous — `31/12/1999` is always day=31 because 31 can't be a month or a 2-digit year. `DateOrder` only fires when all three components are small enough to validly occupy any role (e.g., `10/11/12`).

The three supported orderings cover all real-world date conventions: `MDY` (US), `DMY` (European), `YMD` (ISO-adjacent). No other orderings (YDM, DYM, MYD) exist as real-world conventions and are not supported. Default is `MDY`, which should be documented prominently alongside instructions for how to override via `OmniDateParserConfig`.

When the `DateAssembler` encounters three numeric components separated by `/`, `-`, or `.` where each could be year, month, or day, it applies `DateOrder` from config to assign roles.

```java
// Inside DateAssembler.java
private void resolveThreePartNumeric(int a, int b, int c, OmniDateParserConfig config) {
    switch (config.getDateOrder()) {
        case MDY -> { month = a; day = b; year = expandYear(c, config.getPivotYear()); }
        case DMY -> { day = a; month = b; year = expandYear(c, config.getPivotYear()); }
        case YMD -> { year = expandYear(a, config.getPivotYear()); month = b; day = c; }
    }
}

// 2-digit year expansion
private int expandYear(int twoDigit, int pivot) {
    if (twoDigit > 99) return twoDigit; // already 4-digit
    return twoDigit <= pivot ? 2000 + twoDigit : 1900 + twoDigit;
}
```

**Disambiguation heuristics** (applied before falling back to `DateOrder`):
- Any component > 31 → must be year (4-digit year is never ambiguous)
- Any component > 12 → cannot be month; narrows the possibilities
- 4-digit component → always year; no `DateOrder` needed for that slot
- Only fall back to `DateOrder` when heuristics cannot uniquely assign all three roles

**Feedback loop**:
- **Playground**: `AmbiguityResolutionTest.java` with three configs (MDY, DMY, YMD) and test inputs `"10/11/12"`, `"01/02/03"`, `"31/12/1999"` (unambiguous — day > 12)
- **Experiment**: Verify `"10/11/12"` resolves differently under each `DateOrder`; verify `"31/12/1999"` resolves correctly regardless of `DateOrder` (day=31 is unambiguous)
- **Check command**: `./gradlew test --tests "*.AmbiguityResolutionTest"`

**Implementation steps**:
1. Add heuristic disambiguation to `DateAssembler` — runs before `DateOrder` switch
2. Implement `resolveThreePartNumeric()` with switch on `DateOrder`
3. Implement `expandYear()` with `pivotYear` config

### Western Format Families

**Overview**: The remaining numeric-first date formats beyond ISO 8601. Separator character (/, -, .) and component order (from `DateOrder`) drive classification.

**Format patterns to handle**:
- `DD/MM/YYYY HH:MM TZ` — e.g., `31/12/1999 00:00 GMT`
- `MM/DD/YYYY HH:MM AM/PM` — e.g., `01/31/1999 12:00 PM`
- `DD-MM-YYYY HH:MM:SS.mmm +OFFSET` — e.g., `31-12-1999 12:00:00.000 +0100`
- `DD.MM.YYYY HH:MM TZ` — e.g., `31.12.1999 00:00 CET`
- `YYYY/MM/DD HH:MM TZ` — e.g., `1999/01/01 00:00 JST`
- `DD-Mon-YYYY HH:MM TZ` — e.g., `01-Jan-1999 00:00:00 GMT`, `01-Dec-1999 12:00 PM`
- `YYYYMMDD` compact — e.g., `19990101`

**Key decisions**:
- Separator character is recorded from the first separator token encountered; subsequent separators must match (prevents `01-12/1999` from parsing)
- `DD-Mon-YYYY` format (e.g., `01-Jan-1999`) is detected when the middle component is an `ALPHA_SEQ` of length 3 matching a month abbreviation

**Feedback loop**:
- **Playground**: `WesternFormatsTest.java` parameterized over all Western formats in examples.txt
- **Experiment**: Test each separator type (/, -, .); test mixed AM/PM; test with and without seconds
- **Check command**: `./gradlew test --tests "*.WesternFormatsTest"`

### Spelled-Out English Month Formats

**Overview**: Formats where the month appears as a full name or abbreviation (`January`, `Jan`). These are detected when an `ALPHA_SEQ` token matches a key in `MonthNames`.

**Format patterns to handle**:
- `Month DD, YYYY HH:MM TZ` — e.g., `January 1, 1999 12:00 PM -0500`
- `Month DD, YYYY at HH:MM p.m. TZ` — e.g., `January 1, 1999 at 11:59 p.m. PST`
- `DD Month YYYY HH:MM:SS TZ` — e.g., `31 December 1999 00:00:00 GMT`
- `Mon DD, YYYY HH:MM:SS TZ` — e.g., `Dec 31, 1999 00:00:00 UTC`
- `YYYY Month DD HH:MM:SS TZ` — e.g., `1999 January 1 00:00:00 UTC`
- `Day, Month DD, YYYY HH:MM:SS AM TZ` — e.g., `Friday, January 1, 1999 12:00:00 PM EST`

```java
// MonthNames.java
final class MonthNames {
    private static final Map<String, Integer> MAP = Map.ofEntries(
        Map.entry("january", 1), Map.entry("jan", 1),
        Map.entry("february", 2), Map.entry("feb", 2),
        // ...
        Map.entry("december", 12), Map.entry("dec", 12)
    );

    static OptionalInt resolve(String token) {
        return Optional.ofNullable(MAP.get(token.toLowerCase()))
            .map(OptionalInt::of).orElse(OptionalInt.empty());
    }
}
```

**Key decisions**:
- `"at"` and `","` are noise tokens — the assembler skips them when encountered in alpha/punctuation position
- Day-of-week names (`Friday`, `Fri`) are recognized and discarded (not used in result)
- `p.m.` and `a.m.` with dots: the Lexer emits `ALPHA_SEQ("p")`, `DOT`, `ALPHA_SEQ("m")`, `DOT` — the assembler combines these into a single AM/PM classification

**Feedback loop**:
- **Playground**: `SpelledOutMonthTest.java` and `AmPmTest.java`
- **Experiment**: Test `"January 1, 1999 at 11:59 p.m. PST"` (dots in a.m./p.m.), `"Friday, January 1, 1999 12:00:00 PM EST"` (day-of-week prefix), `"1999 January 1 00:00:00 UTC"` (year-first with spelled month)
- **Check command**: `./gradlew test --tests "*.SpelledOutMonthTest" --tests "*.AmPmTest"`

### `examples.txt` Parameterized Test Fixture

**Overview**: Reads `examples.txt`, strips the out-of-scope rows (by line number), and runs every remaining row through `OmniDateParser.toZonedDateTime()`. A failed parse fails the test. This is the acceptance gate for Phase 2.

```java
// ExamplesTxtTest.java
class ExamplesTxtTest {
    // Out-of-scope line numbers (1-indexed): CJK, German Uhr, noon, hrs
    private static final Set<Integer> EXCLUDED_LINES = Set.of(18, 20, 21, 31, 32, 33, 50, 51, 52, 54, 62, 63, 64, 72, 73, 79, 81, 82);

    static Stream<Arguments> examplesSource() throws IOException {
        // Read src/test/resources/examples.txt (copy from repo root)
        // Skip blank lines and excluded line numbers
        // Return Stream<Arguments> of (lineNumber, inputString)
    }

    @ParameterizedTest(name = "line {0}: {1}")
    @MethodSource("examplesSource")
    void parsesWithoutError(int lineNumber, String input) {
        assertDoesNotThrow(() -> OmniDateParser.toZonedDateTime(input),
            "Failed to parse line " + lineNumber + ": " + input);
    }
}
```

**Implementation steps**:
1. Copy `examples.txt` to `src/test/resources/examples.txt`
2. Write `ExamplesTxtTest` with the `EXCLUDED_LINES` set and `@MethodSource` provider
3. Run and iterate until all non-excluded rows pass

### Concurrency Test

**Overview**: Verifies that a single shared `OmniDateParser` instance produces correct results when called from multiple threads simultaneously.

```java
// ConcurrencyTest.java
class ConcurrencyTest {
    @Test
    void sharedInstanceIsThreadSafe() throws InterruptedException {
        OmniDateParser parser = new OmniDateParser(OmniDateParserConfig.defaults());
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<ZonedDateTime>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() ->
                parser.parseZonedDateTime("1999-01-01T00:00:00Z")));
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        ZonedDateTime expected = ZonedDateTime.of(1999, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        for (Future<ZonedDateTime> f : futures) {
            assertThat(f.get()).isEqualTo(expected);
        }
    }
}
```

## Testing Requirements

### Unit Tests

| Test File | Coverage |
|-----------|---------|
| `AmbiguityResolutionTest.java` | MDY/DMY/YMD on ambiguous and unambiguous inputs; 2-digit year pivot |
| `WesternFormatsTest.java` | All separator types, compact numeric |
| `SpelledOutMonthTest.java` | Long names, short names, year-first, day-of-week prefix |
| `AmPmTest.java` | AM, PM, a.m., p.m. with various surrounding formats |
| `ExamplesTxtTest.java` | All in-scope rows from examples.txt — acceptance gate |
| `ConcurrencyTest.java` | 50 threads sharing one parser instance |

**Key test cases**:
- `"10/11/12"` with `MDY` → month=10, day=11, year=2012
- `"10/11/12"` with `DMY` → day=10, month=11, year=2012
- `"31/12/1999"` → always day=31 (unambiguous), regardless of `DateOrder`
- `"01/31/1999 12:00 PM"` → must be MDY (31 > 12, can't be month)
- `"January 1, 1999 at 11:59 p.m. PST"` → 23:59 PST
- `"Dec 31, 1999 11:59:59 PM PST"` → 23:59:59 PST
- `"1999/12/31 23:59:59 KST"` → KST resolved to Asia/Seoul
- `"19990101"` (compact) → 1999-01-01

## Error Handling

| Scenario | Handling |
|----------|---------|
| Unknown TZ abbreviation (not in lookup table) | Throw `DateParseException` naming the unrecognized abbreviation |
| Day-of-month out of range for given month (e.g., Feb 30) | Throw `DateParseException` from `validate()` |
| Ambiguous input where heuristics can't narrow and no `DateOrder` is set | Not possible — `DateOrder` always has a default (MDY) |
| `a.m.`/`p.m.` with hour > 12 | Throw `DateParseException` |

## Validation Commands

```bash
# Full test suite
./gradlew test

# Scoped to Phase 2 tests
./gradlew test --tests "*.ExamplesTxtTest" --tests "*.AmbiguityResolutionTest" --tests "*.WesternFormatsTest" --tests "*.ConcurrencyTest"

# examples.txt acceptance gate only
./gradlew test --tests "*.ExamplesTxtTest"
```

## Open Items

- [X] Verify final TZ abbreviation list covers all formats in the sisyphsu showcase not already in `examples.txt`

---

_This spec is ready for implementation. Follow the patterns and validate at each step._
