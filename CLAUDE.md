# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew build          # compile + test
./gradlew test           # run all tests
./gradlew jmh            # run JMH benchmarks

# Run a single test class
./gradlew test --tests "io.github.snekse.jdk.dateparser.Iso8601Test"
./gradlew test --tests "*LexerTest"

# Run a single test method
./gradlew test --tests "io.github.snekse.jdk.dateparser.Iso8601Test.testBasicIsoDate"
```

- JDK 21+ required
- Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`)
- No external runtime dependencies (Lombok is annotation-processor only)

## Architecture

**Parsing pipeline**: `OmniDateParser` → `Lexer` (tokenize) → `DateAssembler` (classify + assemble) → `java.time` result

All internal parsing logic lives in `src/main/java/io/github/snekse/jdk/dateparser/internal/`:

- **Lexer** — single-pass tokenizer emitting `Token` records with types: `DIGIT_SEQ`, `ALPHA_SEQ`, `SEPARATOR`, `COLON`, `SIGN`, `DOT`, `T_LITERAL`, `W_LITERAL`
- **DateAssembler** — the core state machine (~1000 lines). Takes token list, detects format family (ISO, RFC, Western, compact, Unix timestamp, etc.), extracts date/time fields, validates, and assembles into `java.time` types
- **MonthNames** — English month name/abbreviation lookup (case-insensitive)
- **TzAbbreviations** — 47 named timezone abbreviations → `ZoneId` (note: CST = America/Chicago)
- **ZoneResolver** — parses UTC offset formats (+0500, +05:30, GMT+08:00)

**Public API** (`io.github.snekse.jdk.dateparser`):
- `OmniDateParser` — static convenience methods (zero-config, UTC, MDY) + configurable instance via `OmniDateParserConfig`
- Returns `ZonedDateTime`, `LocalDate`, `LocalDateTime`, or `Instant`
- Throws `DateParseException` (unchecked) on failure

**Thread safety**: Immutable config + fresh `Lexer` per parse call (like `DateTimeFormatter`).

## Test Fixtures

Parameterized tests in `ExamplesTxtTest` read from `src/test/resources/`:
- `examples.txt` — must parse successfully (one date string per line, `#` comments)
- `invalid-examples.txt` — must throw (invalid date values)
- `unsupported-examples.txt` — must throw (valid formats but out of scope)

## Workflow: Adding/Removing Format Support

1. Update `examples.txt` / `unsupported-examples.txt` fixtures
2. Update the README supported formats section
3. Add/remove formats from JMH benchmark inputs (`BenchmarkInputs.java`)
4. Re-run benchmarks and update performance tables in README

## Key Configuration

`OmniDateParserConfig` (Lombok `@Value @Builder`):
- `dateOrder` — `MDY` (default), `DMY`, `YMD` — only used for ambiguous numeric inputs
- `defaultZone` — `ZoneOffset.UTC` (default) — applied when input has no timezone
- `pivotYear` — `70` (default) — two-digit year cutoff: ≤70 → 20xx, >70 → 19xx

## Publishing & Release

**Versioning**: Tag-driven. `version` in `build.gradle.kts` falls back to `dev-SNAPSHOT` for local builds — never update it manually. Releases are controlled entirely by git tags via GitHub Actions.

**Workflows**:
- `.github/workflows/ci.yml` — runs `./gradlew build` on every push/PR to `main`
- `.github/workflows/release.yml` — triggers on GitHub Release creation; extracts version from tag (e.g. `v0.1.0` → `0.1.0`), signs artifacts, publishes to Maven Central

**To release**: Create a GitHub Release with tag `v{version}` → workflow fires automatically → artifacts appear on Maven Central within ~30 minutes.

**Signed local dry-run** (validates signing before a real release):
```bash
./scripts/publish-local-signed.sh [version]
```
Prompts for GPG key ID and passphrase interactively. Verify `.asc` files appear in `~/.m2/repository/io/github/snekse/jdk-omni-date-parser/{version}/`.

**Required GitHub Actions secrets**: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `GPG_SIGNING_KEY`, `GPG_SIGNING_PASSWORD`

## Scope Boundaries

**Supported**: ISO 8601, RFC 2822/1123/850, RFC 9557 IXDTF, Western numeric (slash/dash/dot), spelled-out English months, 12-hour AM/PM, named TZ abbreviations, UTC offsets, CJK date separators, compact numeric, Unix timestamps.

**Excluded**: non-English month names, natural language ("noon", "midnight"), German Uhr/MEZ, date-to-string formatting.
