---
name: sync-format-diagram
description: >
  Reads DateAssembler.java and updates FORMAT-DETECTION.md — which contains the
  Mermaid format-detection flowchart and the end-to-end parsing walkthrough — to
  match the current routing logic. Use this skill whenever the DateAssembler's
  classify() routing logic changes (e.g. a new format family is added, detection
  conditions change, or a new token type is introduced). Trigger on phrases like
  "sync the diagram", "update format-detection", "update the flowchart", or after
  any change to classify() or the top-level routing in DateAssembler.
---

# sync-format-diagram

## Overview

`FORMAT-DETECTION.md` (project root) contains two things:
1. A **Mermaid flowchart** of the format-detection decision tree in `DateAssembler.classify()`
2. An **end-to-end walkthrough** tracing one concrete input through every stage

This skill reads the current `DateAssembler.java` and updates the diagram and
walkthrough if the routing logic has changed.

---

## Step 1 — Read the current routing logic

Read the full `DateAssembler.java` and focus on:

```
src/main/java/io/github/snekse/jdk/dateparser/internal/DateAssembler.java
```

You only need the **top-level `classify()` method** and the first few lines of
each `classify*()` sub-method (just enough to confirm the discriminating condition).
Do **not** deep-read field-extraction or assembly logic — that is not reflected in
the diagram.

Key things to capture:
- The **order** of format-family checks in `classify()`
- The **discriminating condition** for each branch (token count, first token type,
  first token length, second token type/value, etc.)
- Any **new token types** introduced (check `TokenType.java` if needed)
- Any **new CJK characters** added as separator literals

---

## Step 2 — Read the existing diagram

```
FORMAT-DETECTION.md  (project root)
```

Note which format families are represented and compare their discriminating
conditions against what you found in Step 1.

---

## Step 3 — Identify what changed

List every difference between the current code and the diagram:
- New format family added? → add a branch
- Detection condition changed? → update the label or edge
- Format family removed? → remove the branch
- New token type? → update the token-type table in the Mermaid source

If nothing changed, stop here and report "diagram is already up-to-date."

---

## Step 4 — Update the Mermaid flowchart

Edit the `flowchart TD` block in `FORMAT-DETECTION.md`.

### Diagram structure rules

The diagram uses a **main diagram + sub-diagrams/tables** layout to keep each
diagram narrow. Decision nodes in the main diagram should have **≤ 3 outgoing
edges**. When a node would exceed this limit, replace the fan-out with a single
edge to a subprocess node (`[["..."]]`) that links to a **table** (for simple
key→value mappings) or a **sub-diagram** (for multi-condition branching).

**Current layout:**

| Section | Representation | Why |
|---------|---------------|-----|
| Q1 — single DIGIT_SEQ (all-digits) | Markdown **table** (digit count → format) | Simple key→value mapping |
| Q3 — year-leading (4-digit first token) | **Sub-diagram** (separate `flowchart TD`) | Multi-condition branching on second token |
| Q5 — short-digit-leading (non-4-digit first token) | **Sub-diagram** (separate `flowchart TD`) | Multi-condition branching on second token |

The main diagram reflects the **exact branching order** in `classify()`:

```
classify()
  ├─ single DIGIT_SEQ token?
  │   └─ [subprocess → all-digits-input table]
  ├─ first token is 4-digit DIGIT_SEQ?
  │   └─ [subprocess → year-leading sub-diagram]
  ├─ first token is ALPHA_SEQ?
  │   └─ branch on value (≤ 3 edges)
  │       (年 → CJK before; weekday + RFC-850 shape → RFC 850;
  │        weekday or month name → RFC 2822)
  └─ first token is DIGIT_SEQ (not 4 digits)?
      └─ [subprocess → short-digit-leading sub-diagram]
```

### Mermaid style conventions

- Use `flowchart TD` (top-down)
- Decision nodes: `{...}` — keep labels short (1–2 lines max); use `<br/>` for line breaks
- Subprocess nodes: `[["..."]]` — used when a decision would fan out to > 3 edges
- Leaf nodes (format families): `["..."]` — include format family name + one representative example
- Error node: `["DateParseException"]`
- All edges to the same format family (e.g. RFC 2822) should converge on the same node ID
- Do not attempt to show field-extraction or assembly logic — the diagram ends at the
  format family leaf node
- Keep each diagram ≤ ~35 nodes; if any single diagram grows beyond that, split further

---

## Step 5 — Update the end-to-end walkthrough (if needed)

The walkthrough in `FORMAT-DETECTION.md` traces `"Fri, 01 Jan 1999 23:59:00 +0000"`
through the full pipeline. Update it only if:
- The token output for that string would change (e.g., a new token type was added)
- The routing path for that string changed
- The field-extraction or zone-resolution steps changed for RFC 2822

If you want to use a different example (e.g., a newly-added format), replace the
walkthrough entirely and note why the example was changed.

---

## Step 6 — Report to the user

Summarize:
- What changed in the diagram (added/removed/updated nodes or edges)
- Whether the walkthrough was updated
- Whether the diagram is already up-to-date (if no changes were needed)