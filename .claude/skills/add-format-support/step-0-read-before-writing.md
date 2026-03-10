# Step 0 — Read Before Writing

Before touching any code, build a mental model of how the new format will flow through the
parser.

## Files to read

| File | What to look for |
|------|-----------------|
| `src/main/java/.../internal/Lexer.java` | Token types emitted; how `T_LITERAL`, `W_LITERAL`, and `AT_LITERAL` are detected contextually |
| `src/main/java/.../internal/TokenType.java` | Full list of token types |
| `src/main/java/.../internal/DateAssembler.java` | The `assemble()` dispatch; existing `parseAmPm()`, `parseZoneSection()`, and `parseTimeSection()` methods; how `cur` advances; the `skipSpaces()` helper |
| `src/main/java/.../internal/MonthNames.java` | ALPHA_SEQ values already claimed by month-name lookup |
| `src/main/java/.../internal/TzAbbreviations.java` | ALPHA_SEQ values claimed by timezone abbreviation lookup |
| `src/test/resources/examples.txt` | Format and grouping of existing passing examples |
| `src/test/resources/unsupported-examples.txt` | Lines you'll be removing |
| `src/test/resources/invalid-examples.txt` | Existing invalid-value examples (distinct from unsupported *formats*) |

## Trace the token stream

Use the `/tokenize-date-input` skill to see the token stream and parse result for your new input:

```bash
bash .claude/skills/tokenize-date-input/tokenize.sh "your new input here"
```

Do this for **every example string** you plan to add to `examples.txt`. Surprises here
are far cheaper to catch than surprises in a failing test.

## Check for ALPHA_SEQ collisions

Any new alphabetic keyword (e.g. `noon`, `midnight`, a new timezone abbreviation) is an
`ALPHA_SEQ` token. Before adding it, verify:

1. It is not already a month name in `MonthNames.java`.
2. It is not already a timezone abbreviation in `TzAbbreviations.java`.
3. Trace the execution path through `assemble()` to confirm no earlier branch consumes the
   token before your new code sees it.

## Understand where `cur` lives

`cur` is the shared token cursor in `DateAssembler`. Every helper (`parseTimeSection`,
`parseAmPm`, `parseZoneSection`, etc.) reads and advances `cur`. Understand which helpers
run before yours and whether they could consume tokens you depend on.
