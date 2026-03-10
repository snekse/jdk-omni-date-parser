---
name: add-format-support
description: >
  Step-by-step guide for adding support for a new date/time format, keyword, or separator
  style to jdk-omni-date-parser. Use this skill whenever the user wants to add a new
  format, move entries from unsupported-examples.txt to examples.txt, or recognize new
  input patterns. Trigger on phrases like "add support for", "parse X format", "recognize
  X", "add X to examples.txt", or "move X out of unsupported".
---

# add-format-support

## Overview

Adding format support requires changes across up to six areas. Follow the steps in order —
especially **write tests before implementation** and **build before touching benchmarks**.

| Step | File(s) touched | Purpose |
|------|----------------|---------|
| [0 — Read first](step-0-read-before-writing.md) | (read-only) | Understand tokens, existing logic, claimed ALPHA_SEQ values |
| [1 — Write tests](step-1-write-tests-first.md) | `examples.txt`, `unsupported-examples.txt`, `invalid-examples.txt` | TDD: write tests, confirm they fail |
| [2 — Implement](step-2-implement-parsing.md) | `DateAssembler.java` (or `MonthNames`, `TzAbbreviations`) | Add parsing logic |
| [3 — Build & test](step-3-build-and-test.md) | — | Confirm tests now pass |
| [4 — Update README](step-4-update-readme.md) | `README.md` | Document the new format |
| [5 — Benchmark decision](step-5-benchmark-decision.md) | `BenchmarkInputs.java` | Use `AskUserQuestion` tool with selectable options; default is yes → ALL |
| [6 — Run benchmarks](step-6-run-benchmarks.md) | `README.md` (perf table) | `/update-bench-results` |

## Scripts

Two helper scripts live in this directory:

- **`/tokenize-date-input` skill** — prints the Lexer token stream and OmniDateParser parse result
  (or exception) for any input string. Use this during Step 0 to verify your mental model
  before writing code.
  ```bash
  bash .claude/skills/tokenize-date-input/tokenize.sh "12:00 noon EST"
  ```

- **`check-fixtures.sh`** — detects lines that appear in both `examples.txt` and
  `unsupported-examples.txt`, and lines in `examples.txt` that appear in
  `invalid-examples.txt`. Run this after Step 1 and again after Step 3.
  ```bash
  bash .claude/skills/add-format-support/check-fixtures.sh
  ```
