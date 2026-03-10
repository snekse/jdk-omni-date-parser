# Step 1 — Write Tests First

Write all fixture changes **before** touching implementation. Then run the tests and
confirm they fail. This validates that the tests are actually exercising new behavior.

## Update examples.txt

Add new entries under an appropriately named comment block. Cover:

- **Case variants** — if the feature is case-insensitive (e.g. `noon`, `Noon`, `NOON`)
- **Timezone variants** — with and without a trailing timezone
- **Date format variety** — pair the new feature with at least two different date formats
- **Boundary / edge values** — e.g. the maximum or minimum valid hour for a new keyword

Existing entries in `unsupported-examples.txt` that you are now supporting **must be
moved** to `examples.txt`, not copied. See the removal step below.

## Remove from unsupported-examples.txt

Delete every line you are now supporting. If a comment block becomes empty after removal,
remove the comment line too.

**This is the most commonly missed step.** `ExamplesTxtTest` reads both files. If an entry
remains in `unsupported-examples.txt` after you add support, the test will assert it
*throws* — and will fail when it parses successfully instead.

## Add to invalid-examples.txt (if applicable)

If the new feature introduces validation rules (e.g., "noon requires hour == 12"), add
entries that should throw `DateParseException` due to invalid *values* — not because the
format is unrecognized:

```
# noon/midnight — hour must be 12
January 1, 1999 11:00 noon
January 1, 1999 1:00 midnight
```

These are semantically different from `unsupported-examples.txt` entries: the format is
now recognized, but the value is illegal.

## Run the tests — expect failure

```bash
./gradlew test --tests "*ExamplesTxtTest"
```

Expected outcome:
- New `examples.txt` entries → tests fail because the parser throws (not yet implemented)
- New `invalid-examples.txt` entries → may pass already if the parser rejects them for
  other reasons; that is acceptable

## Investigate unexpected passes

If a new `examples.txt` entry **passes** without any implementation change, stop and
investigate before proceeding:

1. **Is the test written wrong?** — Check that the input string actually exercises the
   new token/keyword. A string that coincidentally parses via an existing code path is
   not a useful test.
2. **Is the format already supported?** — If so, the entry might not be testing what you
   think. Consider whether a more targeted input string would fail without implementation
   changes.
3. **Is the entry in a format family already handled?** — Some formats overlap. If the
   new string parses successfully, document why and either replace it with a sharper
   test case or confirm the feature is already done.

Do not proceed to Step 2 until you have at least one genuinely failing test.
