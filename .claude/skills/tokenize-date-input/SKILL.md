---
name: tokenize-date-input
description: >
  Prints the Lexer token stream and OmniDateParser parse result (or exception) for any
  input date string. Use this skill to inspect how a string tokenizes and whether it
  parses successfully before writing implementation or tests. Trigger on phrases like
  "tokenize", "how does X tokenize", "what tokens does X produce", "does X parse",
  "what happens when I parse X", or "test parsing X".
---

# tokenize-date-input

Runs `tokenize.sh` against the given input string, which prints:
1. The token stream the Lexer emits
2. The `OmniDateParser.toZonedDateTime()` result, or the exception message if it throws

## Usage

```bash
bash .claude/skills/tokenize-date-input/tokenize.sh "<date-string>"
```

## Example output

```
Input: December 1, 1999 12:00 noon
#    Type           Value                  Pos
------------------------------------------------
0    ALPHA_SEQ      "December"             0
1    SEPARATOR      " "                    8
...
11   ALPHA_SEQ      "noon"                 23

Parse result: 1999-12-01T12:00Z
```

Or on failure:

```
Parse result: ERROR — noon requires exactly 12:00:00, got: 12:35:0
```

## Notes

- Builds test classes automatically (`./gradlew testClasses`) if not already compiled.
- The token stream is printed first so you can inspect it even if parsing fails.
- Use this during Step 0 of `add-format-support` to verify mental models before coding.
