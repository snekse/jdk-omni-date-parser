# Implementation Spec: jdk-omni-date-parser - Phase 3

**Contract**: ./contract.md
**Estimated Effort**: S

## Technical Approach

Phase 3 adds three things: JMH benchmarks, public API Javadoc, and the Maven Central publishing config scaffold.

The benchmark suite is the centrepiece. It measures three competitors on identical mixed-format input sets:

1. **`OmniDateParser`** — our lexer + state machine
2. **Shotgun** — a naive reference implementation that tries a list of `DateTimeFormatter` patterns sequentially, catching `DateTimeParseException` on each failure before moving to the next. This is what most developers currently write by hand.
3. **Single known formatter** — a hand-crafted `DateTimeFormatter` called with exactly the right format for each input. This is the theoretical ceiling — what you get when you know the format in advance.

The shotgun implementation is bundled inside the benchmark source set (`src/jmh/java/`). It exists only to serve as a fair reference point, not as a shipping API.

The benchmark runs a warmup, then measures throughput (ops/second) across a representative sample of date strings covering the formats supported by the library.

## Feedback Strategy

**Inner-loop command**: `./gradlew jmh`

**Playground**: JMH CLI output — JMH prints throughput numbers to stdout after each benchmark. The agent interacts with this directly.

**Why this approach**: The benchmark is the deliverable. JMH output is the signal. No web server or test framework needed.

## File Changes

### New Files

| File Path | Purpose |
|-----------|---------|
| `src/jmh/java/io/github/snekse/jdk/dateparser/bench/OmniDateParserBenchmark.java` | JMH benchmark: OmniDateParser vs shotgun vs single formatter |
| `src/jmh/java/io/github/snekse/jdk/dateparser/bench/ShotgunDateParser.java` | Reference shotgun implementation (sequential formatter tries + exception catching) |
| `src/jmh/java/io/github/snekse/jdk/dateparser/bench/BenchmarkInputs.java` | Static list of representative mixed-format date strings used by all benchmarks |

### Modified Files

| File Path | Changes |
|-----------|---------|
| `build.gradle.kts` | Wire JMH plugin config: sourceSet, forks, warmup, measurement iterations, output format |
| `src/main/java/io/github/snekse/jdk/dateparser/OmniDateParser.java` | Add Javadoc to all public methods |
| `src/main/java/io/github/snekse/jdk/dateparser/OmniDateParserConfig.java` | Add Javadoc |
| `src/main/java/io/github/snekse/jdk/dateparser/DateOrder.java` | Add Javadoc to each enum constant |
| `src/main/java/io/github/snekse/jdk/dateparser/DateParseException.java` | Add Javadoc |

## Implementation Details

### JMH Build Config

**Overview**: Wire the JMH Gradle plugin to compile `src/jmh/java/` and produce an executable fat jar.

```kotlin
// build.gradle.kts additions
dependencies {
    jmh(libs.jmhCore)
    jmhAnnotationProcessor(libs.jmhGeneratorAnnotationProcessor)
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    resultFormat = "TEXT"
    benchmarkMode = listOf("thrpt")   // throughput: ops/second
}
```

**Implementation steps**:
1. Uncomment / configure the JMH plugin block that was scaffolded in Phase 1
2. Add `jmh` and `jmhAnnotationProcessor` dependencies to the `dependencies` block
3. Run `./gradlew jmh` to verify the plugin wires up (no benchmarks yet, should be a no-op run)

### `BenchmarkInputs`

**Overview**: A static list of representative date strings covering all format families supported by the library. All three benchmarks use the same list so comparisons are apples-to-apples.

```java
// BenchmarkInputs.java
public final class BenchmarkInputs {
    public static final List<String> ALL = List.of(
        // ISO 8601
        "1999-01-01T00:00:00Z",
        "1999-01-01T12:00:00+00:00",
        "1999-01-01T00:00:00.000+05:30",
        "1999-12-31T23:59:59+01:00",
        // RFC 2822
        "Fri, 01 Jan 1999 23:59:00 +0000",
        "01 Jan 1999 00:00:00 GMT",
        // Western slash/dot
        "01/01/1999 12:00 PM PST",
        "31/12/1999 00:00 GMT",
        "31.12.1999 00:00 CET",
        "1999/01/01 00:00 JST",
        // Spelled-out month
        "January 1, 1999 12:00 PM -0500",
        "31 December 1999 00:00:00 GMT",
        "Dec 31, 1999 00:00:00 UTC",
        "Friday, January 1, 1999 12:00:00 PM EST",
        // a.m./p.m.
        "January 1, 1999 at 11:59 p.m. PST",
        "January 31, 1999 12:00 p.m.",
        // Year-first
        "1999 January 1 00:00:00 UTC",
        // No timezone (date only)
        "1999-01-31",
        "19990101"
    );
}
```

### `ShotgunDateParser` (reference implementation)

**Overview**: A naive implementation that tries each formatter in a fixed list, catching `DateTimeParseException` on failure. This is the pattern most Java developers currently write when they need to handle multiple formats. It exists only in `src/jmh/` — it is not part of the shipped library.

```java
// ShotgunDateParser.java
public final class ShotgunDateParser {

    // Ordered list of formatters to try. Every failure throws an exception — expensive on JVM.
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm z"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm a z"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm z"),
        DateTimeFormatter.ofPattern("MMMM d, yyyy hh:mm a z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMMM yyyy HH:mm:ss z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy hh:mm:ss a z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy MMMM d HH:mm:ss z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyyMMdd"),
        DateTimeFormatter.ISO_LOCAL_DATE
        // ... enough formatters to cover BenchmarkInputs.ALL
    );

    public static ZonedDateTime parse(String input) {
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                TemporalAccessor ta = fmt.parse(input);
                // attempt to build ZonedDateTime, LocalDate, etc.
                return ZonedDateTime.from(ta);
            } catch (DateTimeParseException | DateTimeException ignored) {
                // try next — this exception creation is the bottleneck
            }
        }
        throw new DateTimeParseException("No formatter matched: " + input, input, 0);
    }
}
```

**Key decisions**:
- The shotgun list must be comprehensive enough to actually parse all inputs in `BenchmarkInputs.ALL`. The benchmark result is only meaningful if the shotgun succeeds at least most of the time.
- Add formatters until the shotgun success rate on `BenchmarkInputs.ALL` is 100% — document the final formatter count in a comment.

### `OmniDateParserBenchmark`

**Overview**: Three JMH benchmarks, one per competitor, all running over `BenchmarkInputs.ALL` in a round-robin. The shared state is the input list and parsers (both are thread-safe).

```java
// OmniDateParserBenchmark.java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class OmniDateParserBenchmark {

    private List<String> inputs;
    private int index;

    // Single known formatter for ISO 8601 Z — the theoretical ceiling
    private static final DateTimeFormatter ISO_KNOWN =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");

    @Setup
    public void setup() {
        inputs = BenchmarkInputs.ALL;
        index = 0;
    }

    // Round-robin through inputs so each call processes a different format
    private String next() {
        String s = inputs.get(index % inputs.size());
        index++;
        return s;
    }

    @Benchmark
    public ZonedDateTime omniDateParser() {
        return OmniDateParser.toZonedDateTime(next());
    }

    @Benchmark
    public ZonedDateTime shotgun() {
        return ShotgunDateParser.parse(next());
    }

    @Benchmark
    public ZonedDateTime singleKnownFormatter() {
        // Always parse an ISO 8601 input — this is the ceiling, not apples-to-apples
        // Label clearly in the output that this only works on one format
        return ZonedDateTime.parse("1999-01-01T00:00:00Z", ISO_KNOWN);
    }
}
```

**Key decisions**:
- `singleKnownFormatter` uses a fixed ISO 8601 input (not round-robin) to show what the throughput ceiling looks like when the format is known in advance. This must be labeled clearly in any documentation derived from the results.
- `@State(Scope.Benchmark)` — a single state instance per benchmark, accessed by one thread. Appropriate here since the parser is stateless and the index is benchmark-local.
- `index` is not `volatile` or `AtomicInteger` — JMH single-thread benchmarks don't need it.

**Feedback loop**:
- **Playground**: JMH console output from `./gradlew jmh`
- **Experiment**: Run benchmarks; adjust `BenchmarkInputs.ALL` size (try 5, 10, 20 inputs); observe how throughput scales with shotgun formatter list length
- **Check command**: `./gradlew jmh`

**Implementation steps**:
1. Write `BenchmarkInputs` with the representative input list
2. Write `ShotgunDateParser` — add formatters until all inputs in `BenchmarkInputs.ALL` parse successfully (test this with a simple `main()` before wiring into JMH)
3. Write `OmniDateParserBenchmark` with all three `@Benchmark` methods
4. Run `./gradlew jmh` and verify all three benchmarks produce output
5. Confirm shotgun throughput is meaningfully lower than `OmniDateParser` — if not, investigate (may mean the lexer has a hot path regression)

### Javadoc

**Overview**: Add Javadoc to all public-facing classes and methods. Internal classes (`internal/` package) do not need Javadoc.

```java
/**
 * A lenient, thread-safe date parser that converts almost any date/time string
 * to a {@code java.time} result without requiring a format pattern.
 *
 * <p>The parser uses a lexer + state-machine approach: the input is tokenized in a
 * single pass, then assembled into date fields using the configured {@link DateOrder}
 * for ambiguous inputs.</p>
 *
 * <p>Instances are immutable and thread-safe. Multiple threads may share a single
 * {@code OmniDateParser} instance without synchronization.</p>
 *
 * <p>Static convenience methods use a default configuration (MDY order, UTC fallback):
 * <pre>{@code
 *   ZonedDateTime dt = OmniDateParser.toZonedDateTime("Fri, 01 Jan 1999 23:59:00 +0000");
 * }</pre>
 *
 * <p>For custom configuration:
 * <pre>{@code
 *   OmniDateParser parser = new OmniDateParser(
 *       OmniDateParserConfig.builder().dateOrder(DateOrder.DMY).build());
 *   ZonedDateTime dt = parser.parseZonedDateTime("01/11/2024");
 * }</pre>
 *
 * @see OmniDateParserConfig
 * @see DateOrder
 * @see DateParseException
 */
public class OmniDateParser { ... }
```

**Implementation steps**:
1. Add class-level Javadoc to `OmniDateParser`, `OmniDateParserConfig`, `DateOrder`, `DateParseException`
2. Add method-level Javadoc to all public methods in `OmniDateParser`
3. Document each `DateOrder` enum constant with an example
4. Run `./gradlew javadoc` and fix any warnings

### Maven Central Publishing Scaffold

**Overview**: The `build.gradle.kts` publishing block is configured but not activated. It establishes the structure needed when a group ID is chosen. A `TODO` comment marks where the group ID goes.

```kotlin
// build.gradle.kts additions
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "io.github.snekse"
            artifactId = "jdk-omni-date-parser"
            version = project.version.toString()

            pom {
                name = "jdk-omni-date-parser"
                description = "A lenient universal date parser for JVM languages"
                url = "https://github.com/snekse/jdk-omni-date-parser"
                licenses {
                    license {
                        name = "Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }
                developers {
                    developer { id = "snekse" }
                }
                scm {
                    connection = "scm:git:git://github.com/snekse/jdk-omni-date-parser.git"
                    url = "https://github.com/snekse/jdk-omni-date-parser"
                }
            }
        }
    }
    repositories {
        // TODO: configure Sonatype OSSRH when publishing
    }
}

// Signing — required for Maven Central
// signing { sign(publishing.publications["mavenJava"]) }
// TODO: uncomment and configure signing key when publishing
```

**Implementation steps**:
1. Add `publishing` and `signing` plugin to `build.gradle.kts`
2. Add `publishing { ... }` block with POM metadata
3. Leave `signing { ... }` commented out with a TODO
4. Run `./gradlew generatePomFileForMavenJavaPublication` to verify POM generates without error

## Testing Requirements

### Benchmark Validation

- [ ] `./gradlew jmh` runs without error
- [ ] All three benchmarks (`omniDateParser`, `shotgun`, `singleKnownFormatter`) appear in output
- [ ] `shotgun` benchmark parses all inputs in `BenchmarkInputs.ALL` without throwing (verify via a standalone test)
- [ ] `OmniDateParser` throughput exceeds `shotgun` throughput (document the ratio in a comment)

### Javadoc Validation

```bash
./gradlew javadoc
# Verify: build/docs/javadoc/ is generated, no broken @link references
```

## Validation Commands

```bash
# Full test suite (must still pass)
./gradlew test

# Benchmarks
./gradlew jmh

# Javadoc generation
./gradlew javadoc

# Full build including sources and javadoc jars
./gradlew build

# Verify POM generation
./gradlew generatePomFileForMavenJavaPublication
```

## Open Items

- [ ] Add Apache 2.0 `LICENSE` file to repo root and `NOTICE` file before first Maven Central release
- [ ] Determine Maven Central publishing workflow (Sonatype OSSRH vs Central Portal) and add to a future spec

---

_This spec is ready for implementation. Follow the patterns and validate at each step._
