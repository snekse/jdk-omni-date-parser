# jdk-omni-date-parser

A lenient JDK date/time parser that converts almost any date string to `java.time` results — no format pattern required.

![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![JDK 21+](https://img.shields.io/badge/JDK-21%2B-orange.svg)

## Why This Library

Most projects handle multiple unknown date formats with the "shotgun" approach: try a list of `DateTimeFormatter` patterns in sequence, catching exceptions on each miss until one succeeds. This is slow, verbose, and brittle — every new format means another pattern to maintain.

**jdk-omni-date-parser** replaces all of that with a single-pass lexer and state machine. One call, no format patterns, pure `java.time` output. It handles ISO 8601 (including week dates, ordinal dates, and RFC 9557 annotations), RFC 2822, RFC 850, slash/dash/dot separators, spelled-out months, ordinal day suffixes, period-suffix month abbreviations, AM/PM, noon/midnight keywords, 47 named timezone abbreviations, and more — running at ~1,370k ops/s, roughly **33–57x faster** than the shotgun approach and matching the throughput of a hand-picked single date parser.

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

**Gradle (Kotlin DSL)**
```kotlin
implementation("io.github.snekse:jdk-omni-date-parser:0.1.0")
```

**Maven**
```xml
<dependency>
    <groupId>io.github.snekse</groupId>
    <artifactId>jdk-omni-date-parser</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Supported Formats

| Format Family | Examples |
|---|---|
| ISO 8601 | `2024-01-15T10:30:00Z`, `2024-01-15T10:30:00+05:30` |
| ISO 8601 week dates | `2004-W53-6`, `2004W536`, `2004-W01-1T00:00:00Z` |
| ISO 8601 ordinal dates | `1999-001`, `1999365`, `2000-366` |
| RFC 2822 / RFC 1123 | `Fri, 01 Jan 1999 23:59:00 +0000` |
| RFC 850 (obsolete HTTP) | `Sunday, 06-Nov-94 08:49:37 GMT` |
| RFC 9557 (IXDTF) annotations | `2018-09-16T08:00:00+00:00[Europe/London]` |
| Western numeric (slash/dash/dot) | `01/02/2024`, `15-06-2024`, `01.02.2024`, `2024.03.30` |
| English spelled-out months | `March 14, 2024`, `14 Mar 2024`, `Jan 1 99` |
| Ordinal day suffixes | `October 7th, 1970`, `January 1st, 1999`, `7th October 1970` |
| Period-suffix month abbreviations | `Oct. 7, 1970`, `Jan. 31, 1999 12:00 PM`, `Oct. 7, '70` |
| ISO-style with spelled month (YYYY-Mon-DD) | `2013-Feb-03`, `2013-February-03` |
| 12-hour AM/PM | `01/02/2024 3:04:05 PM`, `1:30 a.m.` |
| `noon` / `midnight` keywords | `December 1, 1999 12:00 noon`, `1999-01-01 12:00:00 midnight -0500` (hour must be 12) |
| Named TZ abbreviations (47) | `EST`, `PST`, `CET`, `JST`, `HKT`, `KST`, `NZDT` |
| UTC offsets | `+0500`, `+05:30`, `GMT+08:00` |
| CJK date separators (年月日時分秒) | `1999年12月31日 00時00分00秒 JST`, `年1999月12日31 時00分00秒00` |
| Compact numeric | `19990101`, `19990101T235900Z`, `20140722105203` |
| Unix timestamps | `1332151919` (s), `1384216367189` (ms), `1384216367111222` (µs), `1384216367111222333` (ns) |

See [`src/test/resources/examples.txt`](src/test/resources/examples.txt) for the exhaustive list.

## How It Works

Parsing flows through four stages:

```
Input string
    │
    ▼
┌─────────┐     token stream     ┌─────────────────┐     java.time
│  Lexer  │ ──────────────────▶  │  DateAssembler  │ ──────────────▶  result
└─────────┘                      └─────────────────┘
                                         │
                          ┌──────────────┴──────────────┐
                          ▼                             ▼
                   ┌─────────────┐             ┌──────────────┐
                   │ZoneResolver │             │OmniDateParser│
                   │             │             │    Config    │
                   └─────────────┘             └──────────────┘
```

### Lexer

The `Lexer` does a single left-to-right scan of the input and emits a flat list of typed `Token` records. It does **not** interpret meaning — it only categorizes characters into token types:

| Token type | What it matches |
|---|---|
| `DIGIT_SEQ` | One or more consecutive digits |
| `ALPHA_SEQ` | One or more consecutive letters |
| `SEPARATOR` | Any whitespace, comma, slash, dash, or other delimiter |
| `COLON` | `:` |
| `DOT` | `.` |
| `SIGN` | `+` or `-` |
| `T_LITERAL` | `T` between digit sequences (ISO 8601 date/time separator) |
| `W_LITERAL` | `W` between digit sequences (ISO 8601 week separator) |

For example, `"December 1, 1999 12:00 noon"` tokenizes as:

```
Input: December 1, 1999 12:00 noon
#    Type           Value                  Pos
------------------------------------------------
0    ALPHA_SEQ      "December"             0
1    SEPARATOR      " "                    8
2    DIGIT_SEQ      "1"                    9
3    SEPARATOR      ","                    10
4    SEPARATOR      " "                    11
5    DIGIT_SEQ      "1999"                 12
6    SEPARATOR      " "                    16
7    DIGIT_SEQ      "12"                   17
8    COLON          ":"                    19
9    DIGIT_SEQ      "00"                   20
10   SEPARATOR      " "                    22
11   ALPHA_SEQ      "noon"                 23
```

Notice how the lexer emits `"noon"` as a plain `ALPHA_SEQ` — meaning doesn't get attached yet. That's the `DateAssembler`'s job.

### DateAssembler

The `DateAssembler` is the core state machine. It walks the token list, detects the format family (ISO 8601, RFC 2822, Western numeric, spelled month, Unix timestamp, CJK, etc.), extracts date/time fields, validates them, and assembles the final `java.time` result.

Format detection is driven by the **shape** of the token stream — the sequence of token types and separator characters — rather than regex matching against the raw string. This makes it fast: there's no backtracking, no exception-on-miss, just a single forward pass.

### ZoneResolver

`ZoneResolver` handles timezone parsing once the `DateAssembler` identifies a timezone token. It covers three forms:

- **Named abbreviations** — `EST`, `PST`, `JST`, etc. (47 total, via `TzAbbreviations`). Note: `CST` maps to `America/Chicago`.
- **UTC offsets** — `+0500`, `-0800`, `+05:30`
- **GMT-prefixed offsets** — `GMT+08:00`, `GMT-05:00`

Timezone information in the input always wins. The `defaultZone` from `OmniDateParserConfig` is only applied when the input contains no timezone at all.

### OmniDateParserConfig

`OmniDateParserConfig` controls three behaviors that can't be resolved from the input alone:

- **`dateOrder`** (`MDY` / `DMY` / `YMD`) — resolves ambiguous numeric inputs like `10/11/12`. Only consulted when heuristics can't determine order from context (e.g. a value > 12 forces the month position).
- **`defaultZone`** — the `ZoneId` to apply when the input has no timezone. Defaults to `UTC`.
- **`pivotYear`** — two-digit year cutoff. Years ≤ pivot map to 20xx; years > pivot map to 19xx. Defaults to `70`.

The config object is immutable. Each parse call allocates a fresh `Lexer`, so `OmniDateParser` instances are safe to share across threads — the same design as `DateTimeFormatter`.

## Performance

Benchmarked with [JMH](https://github.com/openjdk/jmh) on JDK 21 (OpenJDK 64-Bit Server VM, 1 fork, 3 warmup + 5 measurement iterations, throughput mode).

Benchmarked over two input sets. The **core** set (21 inputs) covers formats a hand-crafted shotgun can handle without special preprocessing. The **full** set (28 inputs) adds ordinal day suffixes, period-suffix month abbreviations, `@` date-time separator, and noon/midnight keyword formats, which require additional preprocessing in the shotgun but are handled natively by OmniDateParser's lexer:

| Strategy | Throughput (full, 28 inputs) | vs. Shotgun | Throughput (core†, 21 inputs) | vs. Shotgun (core†) |
|---|---|---|---|---|
| **OmniDateParser** | ~1,349,000 ops/s | **~57x faster** | ~1,370,000 ops/s | **~33x faster** |
| Shotgun (sequential `DateTimeFormatter` tries) | ~24,000 ops/s | baseline | ~41,000 ops/s | baseline |
| Single known formatter (ceiling) | ~1,095,000 ops/s | ~46x faster | — | — |

†Core excludes ordinal-suffix (`October 7th`), period-suffix (`Oct. 7`), `@` separator (`2013-Feb-03@12:30:00`), and noon/midnight keyword formats. The shotgun's extra preprocessing for those formats accounts for the wider gap in the full comparison.

The shotgun approach pays a steep cost in exception creation on every miss. OmniDateParser's single-pass lexer avoids this entirely.

To reproduce: `./gradlew jmh`

## Thread Safety

`OmniDateParser` and `OmniDateParserConfig` are immutable and safe for concurrent use by multiple threads. This follows the same design as `DateTimeFormatter`: an immutable config object paired with a fresh lexer allocated per parse call, so no mutable state is shared. This is validated by a dedicated concurrency test running 50 threads in parallel.

## Not Supported

- ISO 8601 durations (`P1Y2M3D`)
- Some CJK date formats
- Non-English or common conventions  (e.g. `Uhr` / `MEZ`)
- Natural language (e.g. "yesterday", "next Tuesday")
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
