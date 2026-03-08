# jdk-omni-date-parser Contract

**Created**: 2026-03-06
**Confidence Score**: 95/100
**Status**: Draft

## Problem Statement

JVM applications that consume external APIs, files, or data pipelines frequently receive date strings in dozens of different formats — ISO 8601, RFC 2822, locale-specific formats, named timezone abbreviations, and more. Today, every new format requires writing and testing a new `DateTimeFormatter` pattern. The common workaround is a "shotgun" approach: try a list of formatters sequentially, catching `DateTimeParseException` on each failure before moving to the next — which is both verbose and slow, since JVM exception creation is expensive.

The goal is a single library that absorbs this complexity: a developer passes any date string to `parser.toZonedDateTime(input)` without knowing or caring what format it is in. The library handles detection, parsing, and conversion — the caller just gets a `java.time` result.

## Goals

1. **Zero-configuration parsing** — `OmniDateParser.toZonedDateTime(input)`, `.toLocalDate(input)`, `.toLocalDateTime(input)`, and `.toInstant(input)` parse any supported format without the caller supplying a format pattern.
2. **Explicit ambiguity control** — A `DateOrder` enum (`MDY`, `DMY`, `YMD`) lets callers declare component ordering for ambiguous inputs like `10/10/10` where year, month, and day position are all uncertain.
3. **Broad format coverage** — Support all formats in this repo's `examples.txt` that fall within scope (see Scope Boundaries), plus all formats in the [sisyphsu/dateparser showcase](https://github.com/sisyphsu/dateparser?tab=readme-ov-file#showcase), on JDK 21.
4. **Thread-safe, JVM-friendly JAR** — An immutable `OmniDateParserConfig` + fresh-lexer-per-call design means any number of threads can share one `OmniDateParser` instance safely. Eventually published to Maven Central.
5. **Performance transparency** — JMH benchmarks comparing our lexer against a reference shotgun implementation (sequential formatter tries with exception catching) and against a single hand-crafted `DateTimeFormatter`, so users understand the cost of the status quo.

## Success Criteria

- [ ] `OmniDateParser.toZonedDateTime("1999-01-01T00:00:00Z")` returns `1999-01-01T00:00:00Z`
- [ ] `OmniDateParser.toLocalDate("January 31, 1999")` returns `1999-01-31`
- [ ] `OmniDateParser.toInstant("Fri, 01 Jan 1999 23:59:00 +0000")` returns correct `Instant`
- [ ] `DateOrder.MDY` config parses `10/10/10` as October 10, 2010
- [ ] `DateOrder.DMY` config parses `10/10/10` as October 10, 2010 (day=10, month=10, year=10 → 2010)
- [ ] `DateOrder.YMD` config parses `10/10/10` as October 10, 2010 (year=10 → 2010, month=10, day=10)
- [ ] Unparseable input throws unchecked `DateParseException` with a descriptive message
- [ ] All in-scope formats from `examples.txt` parse without error under default config
- [ ] All formats in the sisyphsu showcase parse without error
- [ ] JMH benchmarks run via `./gradlew jmh` and include three competitors: `OmniDateParser`, a shotgun implementation, and a single hand-crafted `DateTimeFormatter`
- [ ] Benchmark results show meaningful throughput advantage of lexer over shotgun on a mixed-format input set
- [ ] Library compiles and all tests pass on JDK 21 via `./gradlew test`
- [ ] Multiple threads can share one `OmniDateParser` instance without synchronization (validated by a concurrency test)

## Scope Boundaries

### In Scope

- **Build**: Gradle Kotlin DSL, JDK 21, Lombok 1.18.30+, Maven Central publishing config (group ID `io.github.snekse`, publishing pipeline deferred)
- **Public API**: `OmniDateParser` (static convenience methods + configurable instance), `OmniDateParserConfig` / builder, `DateParseException` (unchecked), `DateOrder` enum
- **Package**: `io.github.snekse.jdk.dateparser`
- **Return types**: `ZonedDateTime`, `LocalDate`, `LocalDateTime`, `Instant` — all from `java.time`
- **Parsing algorithm**: Lexer + state machine — immutable `OmniDateParserConfig`, fresh internal `Lexer` instance per parse call (thread-safe by design, modeled after `DateTimeFormatter`)
- **Format groups**:
  - ISO 8601: with/without `T`, `Z`, UTC offsets (`+05:30`, `-0500`, `+00:00`), milliseconds
  - RFC 2822: with/without day-of-week, numeric and named timezone offsets
  - Common Western separators: `/`, `-`, `.` in YYYY-MM-DD, DD/MM/YYYY, MM/DD/YYYY, YYYY/MM/DD
  - English spelled-out months: `January 1, 1999`, `1 January 1999`, `Jan 1, 1999`, `01-Jan-1999`
  - 12-hour clock: `AM`/`PM`, `a.m.`/`p.m.` — but NOT "noon" or "midnight"
  - Named timezone abbreviations: EST, PST, CET, JST, HKT, NZDT, WET, BST, KST, etc.
  - UTC numeric offsets: `+0500`, `+05:30`, `-0500`, `GMT+08:00`
  - Compact numeric: `19990101`
- **`OmniDateParserConfig`**: `DateOrder` enum (`MDY` default — only consulted for genuinely ambiguous inputs), `defaultZone` (`ZoneId`, default `ZoneOffset.UTC`, supports half-hour and 45-minute offsets), `pivotYear` (2-digit year cutoff, default 70)
- **`toLocalDate()`**: silently strips time and timezone — the input's declared timezone determines which calendar date it is
- **Benchmarks**: JMH suite with `OmniDateParser` vs shotgun (sequential formatter tries + exception catching) vs single known `DateTimeFormatter`; run via `./gradlew jmh`
- **Testing**: JUnit 5 unit tests; `examples.txt` in-scope rows as a parameterized test fixture; concurrency test for thread safety
- **License**: Apache 2.0

### Out of Scope

- CJK date formats (年月日時分秒) — significant complexity, deferred entirely
- German locale tokens (`Uhr`, `MEZ`) — not worth the specificity for a general-purpose library
- Natural language tokens: "noon", "midnight", "next Tuesday" — NLP territory
- `"hrs"` token (e.g., `1 January 1999, 00:00 hrs, CET`) — overly specific, not a common API format
- Date-to-string formatting — parse-only library by design
- `java.util.Calendar` and `java.util.Date` return types — legacy, not `java.time`
- Non-English non-CJK locale support (Spanish, French, Italian month names) — not in v1
- Custom user-defined parsing rules — stretch goal, deferred
- Full Maven Central publishing pipeline — config scaffolded, publishing deferred until group ID is decided

### Out-of-scope rows in examples.txt

The following rows from `examples.txt` are excluded from the test fixture (AI-generated examples outside the library's intended scope):

- Lines 18, 50–52: German `Uhr` / `MEZ` tokens
- Lines 21, 54, 79: "noon" natural language token
- Line 20: "hrs" token
- Lines 31–33, 62–64, 72–73, 81–82: CJK formats

### Future Considerations

- CJK date/time support (年月日時分秒)
- Custom rule registration API (allow users to add formats the library misses)
- Non-English locale support (Spanish, French, German month names)
- Natural language tokens ("noon", "midnight")
- `java.util.Date` for legacy interop
- Maven Central publish pipeline once group ID is settled
- Graal native-image compatibility

## Execution Plan

### Dependency Graph

```
Phase 1: Foundation + Core Lexer + ISO 8601 / RFC 2822  (blocking)
  └── Phase 2: Western Formats + Ambiguity Resolution    (blocked by Phase 1)
        └── Phase 3: Benchmarks + Polish + Publishing    (blocked by Phase 2)
```

### Execution Steps

**Strategy**: Sequential — each phase is a prerequisite for the next.

1. **Phase 1** — Foundation + Core Lexer + ISO 8601 / RFC 2822 _(blocking)_
   ```bash
   /execute-spec docs/ideation/jdk-omni-date-parser/spec-phase-1.md
   ```
   Deliverable: compiling Gradle project, public API skeleton, lexer engine, ISO 8601 and RFC 2822 parsing end-to-end.

2. **Phase 2** — Western Formats + Ambiguity Resolution _(blocked by Phase 1)_
   ```bash
   /execute-spec docs/ideation/jdk-omni-date-parser/spec-phase-2.md
   ```
   Deliverable: all in-scope `examples.txt` rows parsing, `DateOrder` ambiguity resolution, named TZ abbreviations, AM/PM, concurrency test passing.

3. **Phase 3** — Benchmarks + Polish + Publishing Config _(blocked by Phase 2)_
   ```bash
   /execute-spec docs/ideation/jdk-omni-date-parser/spec-phase-3.md
   ```
   Deliverable: JMH benchmarks (`OmniDateParser` vs shotgun vs single formatter), Javadoc on public API, Maven Central publishing scaffold.
