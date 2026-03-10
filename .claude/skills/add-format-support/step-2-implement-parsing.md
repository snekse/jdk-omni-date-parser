# Step 2 — Implement Parsing Logic

## Where to add code

| Change type | Where |
|-------------|-------|
| New time-of-day keyword (`noon`, `midnight`) | Extend `parseAmPm()` in `DateAssembler.java` — `.toUpperCase()` is already called, so case-insensitivity is free |
| New separator style | `parseDateSection()` / `parseTimeSection()` in `DateAssembler.java` |
| New format family | New detection branch in `assemble()`, following the existing pattern of inspecting the first significant token |
| New timezone abbreviation | `TzAbbreviations.java` — not DateAssembler |
| New month name / abbreviation | `MonthNames.java` — not DateAssembler |

## Key invariants

**Advance `cur` for every token consumed.**
Every `tokens.get(cur)` must be followed by `cur++` (or a helper that advances). Leaving
`cur` pointing at a consumed token causes the next section parser to misread it.

**Always bounds-check before accessing.**
```java
if (cur < tokens.size() && tokens.get(cur).type() == TokenType.ALPHA_SEQ) { ... }
```

**Call `skipSpaces()` before peeking when whitespace may appear.**
`skipSpaces()` consumes `SEPARATOR` tokens. Without it, a space between your keyword and
the timezone will prevent `parseZoneSection()` from finding the zone token.

**Validate first, then assign.**
Do not mutate shared fields (`hour`, `minute`, `amPm`, `zone`, etc.) until validation
passes. If validation throws, partially updated fields corrupt the assembled result.

```java
// Good
if (hour != 12) throw new DateParseException(original, "noon requires hour 12, got: " + hour);
amPm = 1;
cur++;

// Bad — mutates then validates
amPm = 1;
cur++;
if (hour != 12) throw new DateParseException(...); // state already dirty
```

**Use `DateParseException(original, "message")` for all validation failures.**
Never throw a generic `IllegalArgumentException` or `RuntimeException`.

## Pitfall: partial token consumption

If a keyword spans multiple tokens (e.g. the dotted `a.m.` form is four tokens:
`ALPHA_SEQ("a")`, `DOT`, `ALPHA_SEQ("m")`, `DOT`), increment `cur` for each token
consumed. Missing even one leaves a dangling token that breaks the zone section.

## Pitfall: ALPHA_SEQ consumed earlier in the pipeline

Trace `assemble()` to confirm no earlier branch consumes the token your new code depends
on. In particular, `parseMonthName()` and the timezone abbreviation lookup in
`parseZoneSection()` both consume `ALPHA_SEQ` tokens. If your keyword could be reached by
either of those paths first, your new branch will never fire.
