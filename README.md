# jdk-omni-date-parser

A lenient JDK date/time parser that converts almost any date string to `java.time` results — no format pattern required.

![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![JDK 21+](https://img.shields.io/badge/JDK-21%2B-orange.svg)

## Why This Library

Most projects handle multiple unknown date formats with the "shotgun" approach: try a list of `DateTimeFormatter` patterns in sequence, catching exceptions on each miss until one succeeds. This is slow, verbose, and brittle — every new format means another pattern to maintain.

**jdk-omni-date-parser** replaces all of that with a single-pass lexer and state machine. One call, no format patterns, pure `java.time` output. It handles ISO 8601, RFC 2822, slash/dash/dot separators, spelled-out months, AM/PM, 47 named timezone abbreviations, and more — running at ~1,089k ops/s, roughly **25x faster** than the shotgun approach and matching the throughput of a hand-picked single date parser.

Because this matches the throughput of a single known date parser, this library also works great for systems that have a handful of known date patterns without the need of declaring each.

## Quick Start

### Zero-Config

```java
ZonedDateTime zdt = OmniDateParser.toZonedDateTime("Fri, 01 Jan 1999 23:59:00 +0000");
LocalDate      ld = OmniDateParser.toLocalDate("March 14, 2024");
LocalDateTime ldt = OmniDateParser.toLocalDateTime("01/02/2024 3:04:05 PM");
Instant         i = OmniDateParser.toInstant("2024-06-15T10:30:00Z");
```

### Configured Instance

```java
OmniDateParser parser = new OmniDateParser(
    OmniDateParserConfig.builder()
        .dateOrder(DateOrder.DMY)
        .defaultZone(ZoneId.of("Europe/London"))
        .build());

var zdt = parser.parseZonedDateTime("01/02/2024 14:30:00");  // 1 Feb 2024 14:30, Europe/London
```

## Configuration

| Property | Type | Default | Description |
|---|---|---|---|
| `dateOrder` | `DateOrder` | `MDY` | Component ordering for ambiguous numeric inputs (e.g. `10/11/12`). Only applied when heuristics alone can't determine order. |
| `defaultZone` | `ZoneId` | `ZoneOffset.UTC` | Timezone applied when the input contains no timezone information. Supports half-hour and 45-minute offsets via standard `ZoneId`. |
| `pivotYear` | `int` | `70` | Two-digit year cutoff: years <= pivotYear map to 20xx, years > pivotYear map to 19xx. |

```java
OmniDateParser parser = new OmniDateParser(
    OmniDateParserConfig.builder()
        .dateOrder(DateOrder.DMY)
        .defaultZone(ZoneId.of("Asia/Kolkata"))
        .pivotYear(50)
        .build());
```

`DateOrder` has three values: `MDY`, `DMY`, and `YMD` — the only orderings that exist as real-world conventions.

## Installation

Not yet published to Maven Central. For now, install locally:

```bash
./gradlew publishToMavenLocal
```

Then add the dependency:

**Gradle (Kotlin DSL)**
```kotlin
implementation("io.github.snekse:jdk-omni-date-parser:0.1.0-SNAPSHOT")
```

**Maven**
```xml
<dependency>
    <groupId>io.github.snekse</groupId>
    <artifactId>jdk-omni-date-parser</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Supported Formats

| Format Family | Examples |
|---|---|
| ISO 8601 | `2024-01-15T10:30:00Z`, `2024-01-15T10:30:00+05:30` |
| RFC 2822 | `Fri, 01 Jan 1999 23:59:00 +0000` |
| Western numeric (slash/dash/dot) | `01/02/2024`, `15-06-2024`, `01.02.2024` |
| English spelled-out months | `March 14, 2024`, `14 Mar 2024`, `Jan 1 99` |
| 12-hour AM/PM | `01/02/2024 3:04:05 PM`, `1:30 a.m.` |
| Named TZ abbreviations (47) | `EST`, `PST`, `CET`, `JST`, `HKT`, `KST`, `NZDT` |
| UTC offsets | `+0500`, `+05:30`, `GMT+08:00` |
| Compact numeric | `19990101`, `19990101T235900` |

See [`src/test/resources/examples.txt`](src/test/resources/examples.txt) for the exhaustive list.

## Performance

Benchmarked with [JMH](https://github.com/openjdk/jmh) on JDK 21 (OpenJDK 64-Bit Server VM, 1 fork, 3 warmup + 5 measurement iterations, throughput mode).

Three strategies measured over 19 representative inputs covering ISO 8601, RFC 2822, Western slash/dash, spelled-out months, AM/PM, and compact numeric formats:

| Strategy | Throughput | vs. Shotgun |
|---|---|---|
| **OmniDateParser** (lexer + state machine) | ~1,089,000 ops/s | **~25x faster** |
| Shotgun (sequential `DateTimeFormatter` tries) | ~43,000 ops/s | baseline |
| Single known formatter (ceiling — one format only) | ~1,084,000 ops/s | ~25x faster |

The shotgun approach pays a steep cost in exception creation on every miss. OmniDateParser's single-pass lexer avoids this entirely.

To reproduce: `./gradlew jmh`

## Thread Safety

`OmniDateParser` and `OmniDateParserConfig` are immutable and safe for concurrent use by multiple threads. This follows the same design as `DateTimeFormatter`: an immutable config object paired with a fresh lexer allocated per parse call, so no mutable state is shared. This is validated by a dedicated concurrency test running 50 threads in parallel.

## Not Supported

- CJK date formats
- German `Uhr` / `MEZ` conventions
- Natural language (e.g. "yesterday", "noon", "midnight")
- Non-English month names
- Date-to-string formatting (this is a parser only)

## Building from Source

Requires JDK 21+.

```bash
./gradlew build                # compile + test
./gradlew jar                  # build the jar (build/libs/)
./gradlew test                 # tests only
./gradlew jmh                  # run benchmarks
./gradlew publishToMavenLocal  # install to ~/.m2 for local use
```

## Attribution

Inspired by these projects:

- [araddon/dateparse](https://github.com/araddon/dateparse) (Go) — the lexer + state machine approach was directly inspired by this project
- [sisyphsu/dateparser](https://github.com/sisyphsu/dateparser) (Java) — general inspiration for lenient date parsing on the JVM

## License

[Apache License 2.0](LICENSE)
