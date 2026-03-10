---
name: update-bench-results
description: >
  Runs JMH benchmarks for jdk-omni-date-parser and updates the README.md performance
  table and prose if results have changed significantly. Use this skill whenever the
  user wants to run benchmarks, refresh performance numbers, update the README perf
  table, or validate that throughput results are still accurate after code changes.
  Trigger on phrases like "run benchmarks", "update bench results", "refresh perf
  numbers", "how fast is it now", or after any change to parsing logic or benchmark
  inputs.
---

# update-bench-results

## Overview

This skill runs the JMH benchmarks, parses the results using a bundled script,
compares them to the published numbers in README.md, and updates the README if
the numbers have shifted more than ~5%. The script handles parsing so the steps
are deterministic regardless of machine or JVM variance in output formatting.

## Step 1 — Check input counts

Before running anything, count the entries in `BenchmarkInputs.java`:

```bash
grep -c '"' src/jmh/java/io/github/snekse/jdk/dateparser/bench/BenchmarkInputs.java
```

More precisely, count the string literals in the `CORE` and `ALL` lists. You'll
need these counts to update the README table headers if they've changed
(e.g., "full, 26 inputs" if ALL grew from 23 to 26).

## Step 2 — Run the benchmarks

```bash
./gradlew jmh
```

This takes several minutes (3 warmup + 5 measurement iterations, 1 fork).
The plugin writes structured results to `build/results/jmh/results.txt` —
this is the file we parse, not stdout.

## Step 3 — Parse and compare

Run the bundled script from the repo root:

```bash
python3 .claude/skills/update-bench-results/parse_results.py
```

Optional explicit paths:
```bash
python3 .claude/skills/update-bench-results/parse_results.py \
  build/results/jmh/results.txt \
  README.md
```

The script outputs JSON and exits:
- **Exit 0** — all throughput numbers within ~5% of published; no update needed
- **Exit 1** — one or more numbers differ by >5%; README should be updated
- **Exit 2** — error (missing results file or parse failure)

### JSON output structure

```json
{
  "new": {
    "omni_all":             1182000,
    "omni_core":            1160000,
    "shotgun_all":          28000,
    "shotgun_core":         35000,
    "single_all":           1038000,
    "omni_vs_shotgun_all":  42,
    "omni_vs_shotgun_core": 34,
    "single_vs_shotgun":    37,
    "raw_scores": { ... }
  },
  "existing": { ... },
  "changes": {
    "omni_all": { "old": 1182000, "new": 1250000, "delta_pct": 5.7, "old_fmt": "~1,182,000", "new_fmt": "~1,250,000" }
  },
  "update_needed": true,
  "summary": {
    "OmniDateParser (all)":  "~1,182,000 ops/s  (~42x vs shotgun)",
    ...
  }
}
```

All throughput values in the `new` block are **rounded to the nearest 1,000**
and ready to drop directly into the README.

## Step 4 — Evaluate the results

### Check for regressions first

Before deciding whether to update the README, check whether the JSON contains any
entries in the `regressions` field (OmniDateParser or Single formatter throughput
that dropped more than ~5%).

**If regressions are present**, stop and notify the user before touching the README:

> ⚠️ Performance regression detected since the last published run:
> - `omni_all`: ~1,182,000 → ~1,050,000 ops/s (–11.2%)
> _(list each regression)_
>
> The README has **not** been updated. Would you like me to investigate the cause?

If the user says yes (or asks you to investigate), look for likely causes:

1. **Recent code changes** — run `git log --oneline -10` and `git diff HEAD~5 -- src/main/java/` to see what changed in the parsing logic
2. **New benchmark inputs** — if `BenchmarkInputs.ALL` or `CORE` grew, a slow new format could be dragging down throughput; compare the old vs. new input counts
3. **Hot path changes** — check `DateAssembler.java` for anything that added branching, allocation, or extra token iteration in commonly-hit paths
4. **JVM / environment noise** — if code changes are minimal, the regression may be measurement noise; suggest re-running benchmarks to confirm

Share your findings with the user and let them decide how to proceed.

### Step 4a — No update needed (exit 0, no regressions)

Report to the user:

> New benchmark results are within ~5% of the published numbers — no README update needed.

Show the `summary` block from the JSON so they can see the actual numbers.

### Step 4b — Update README.md (exit 1, improvements or mixed changes, no regressions)

Update **two locations** in README.md:

### A. Performance table

Replace the three data rows and, if the input counts changed, the column headers:

```markdown
| Strategy | Throughput (full, N inputs) | vs. Shotgun | Throughput (core†, M inputs) | vs. Shotgun (core†) |
|---|---|---|---|---|
| **OmniDateParser** | ~X ops/s | **~Ax faster** | ~Y ops/s | **~Bx faster** |
| Shotgun (sequential `DateTimeFormatter` tries) | ~P ops/s | baseline | ~Q ops/s | baseline |
| Single known formatter (ceiling) | ~R ops/s | ~Cx faster | — | — |
```

Where:
- `N` = ALL input count, `M` = CORE input count
- `X` = `new.omni_all` formatted as `~1,182,000`, `Y` = `new.omni_core`
- `A` = `new.omni_vs_shotgun_all`, `B` = `new.omni_vs_shotgun_core`
- `P` = `new.shotgun_all`, `Q` = `new.shotgun_core`
- `R` = `new.single_all`, `C` = `new.single_vs_shotgun`

### B. "Why This Library" paragraph

This paragraph contains an inline performance claim, e.g.:
> running at ~1,160k ops/s, roughly **33–42x faster** than the shotgun approach

Update:
- The `~Xk ops/s` figure → use `new.omni_core` expressed in thousands (e.g., `1,160,000` → `~1,160k`)
- The multiplier range `~Lx–Hx faster` → use `min(omni_vs_shotgun_core, omni_vs_shotgun_all)`
  and `max(...)` as the low and high ends of the range

### C. Performance section prose (if input counts changed)

The paragraph above the table reads:
> The **core** set (21 inputs) covers formats ... The **full** set (23 inputs) adds ...

Update the numbers if `CORE` or `ALL` counts changed.

## Step 5 — Report to the user

Always end with a concise summary:

- New throughput numbers (from `summary`)
- Whether the README was updated
- If updated: a brief before/after diff of what changed
- If not updated: confirmation that numbers are stable
- If a regression was found: what was detected and what investigation (if any) was done