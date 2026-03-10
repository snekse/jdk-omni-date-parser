# Step 3 — Build & Test

Run the full test suite and confirm all tests pass.

```bash
./gradlew test
```

For faster iteration during development, run only the fixture-driven tests:

```bash
./gradlew test --tests "*ExamplesTxtTest"
```

## Also verify fixture consistency

```bash
bash .claude/skills/add-format-support/check-fixtures.sh
```

This catches the common case of an entry appearing in both `examples.txt` and
`unsupported-examples.txt`.

## If tests fail

**`ExamplesTxtTest` — new example still throws:**
- Re-read the token trace from Step 0. Confirm `cur` is positioned at the right token
  when your new code runs.
- Add a temporary `System.out.println` or debugger breakpoint to confirm the branch is
  reached.
- Check that `skipSpaces()` is called before your new token peek if whitespace precedes it.

**`ExamplesTxtTest` — unsupported entry no longer throws:**
- An entry was left in `unsupported-examples.txt` that the parser now accepts. Remove it
  (or move it to `examples.txt` if it should be a passing case).

**`ExamplesTxtTest` — invalid entry no longer throws:**
- Your new validation logic may not be reached for that input path. Check whether
  `applyAmPm()` or another downstream method is handling the case before your new guard
  fires.

**Unrelated test failures:**
- Run `git diff src/main/java` to confirm no unintended edits to shared logic.
