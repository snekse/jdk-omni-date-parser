# Step 5 — Benchmark Decision

Use the `AskUserQuestion` tool to present a structured choice. Do **not** ask in free text.

```
AskUserQuestion(
  question: "Should the new format be added to the JMH benchmarks?",
  header: "Benchmarks",
  options: [
    {
      label: "Yes, add to ALL (Recommended)",
      description: "Include in the full benchmark set. Use when the format requires
                    preprocessing or a special keyword that DateTimeFormatter can't handle."
    },
    {
      label: "Yes, add to CORE + ALL",
      description: "Include in both sets. Only appropriate if a standard DateTimeFormatter
                    pattern (with no extra preprocessing) could parse the new input."
    },
    {
      label: "No, skip benchmarks",
      description: "Don't add any new benchmark inputs for this format."
    }
  ]
)
```

## When to recommend CORE + ALL vs ALL only

**Default to ALL only.** Add to CORE as well only if **all** of the following are true:
- No ordinal day suffixes (`1st`, `2nd`, `7th`…)
- No period-suffix month abbreviations (`Jan.`, `Oct.`…)
- No `@` date-time separator
- No special alphabetic keywords beyond month names, day-of-week names, and timezone
  abbreviations (`noon`, `midnight`, `Uhr`, `o'clock`, etc.)
- The format is directly expressible as a `DateTimeFormatter` pattern without custom
  preprocessing

If in doubt, choose ALL only. It's always safe; it just means the shotgun comparison uses
the full pre-processing path for that input.

## Making the changes

In `src/jmh/java/io/github/snekse/jdk/dateparser/bench/BenchmarkInputs.java`:

1. Add one or two representative strings under an appropriate comment in the target list(s).
2. Update the Javadoc entry counts at the top of the class (e.g. `26 entries` → `28 entries`).
3. If adding to CORE + ALL, add the same strings to both lists.
