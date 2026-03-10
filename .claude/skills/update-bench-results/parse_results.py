#!/usr/bin/env python3
"""
Parse JMH results and compare to published numbers in README.md.

Usage:
  python parse_results.py [results_file] [readme_file]

Defaults:
  results_file = build/results/jmh/results.txt
  readme_file  = README.md

Output:
  JSON to stdout with keys: new, existing, changes, update_needed
  Exit 0 = results within 5% of published, no update needed
  Exit 1 = significant change detected, README should be updated
  Exit 2 = error (results file missing or unparseable)

Expected results.txt format (produced by me.champeau.jmh plugin with resultFormat=TEXT):
  Benchmark                                      Mode  Cnt        Score       Error  Units
  OmniDateParserBenchmark.omniDateParser        thrpt    5  1182152.183 ± 80671.353  ops/s
  OmniDateParserBenchmark.omniDateParserCore    thrpt    5  1159685.896 ± 72850.146  ops/s
  OmniDateParserBenchmark.shotgun               thrpt    5    27604.068 ±  1573.988  ops/s
  OmniDateParserBenchmark.shotgunCore           thrpt    5    34538.207 ± 13157.536  ops/s
  OmniDateParserBenchmark.singleKnownFormatter  thrpt    5  1038119.025 ± 59960.111  ops/s
"""

import re
import sys
import json
from pathlib import Path

# Maps JMH method names to logical keys
METHOD_MAP = {
    "omniDateParser":      "omni_all",
    "omniDateParserCore":  "omni_core",
    "shotgun":             "shotgun_all",
    "shotgunCore":         "shotgun_core",
    "singleKnownFormatter": "single_all",
}

THRESHOLD = 0.05  # 5% change triggers an update


def parse_results(results_file: str) -> dict[str, float]:
    """Return {logical_key: raw_score} from results.txt."""
    path = Path(results_file)
    if not path.exists():
        print(f"ERROR: results file not found: {results_file}", file=sys.stderr)
        sys.exit(2)

    scores = {}
    for line in path.read_text().splitlines():
        line = line.strip()
        if "thrpt" not in line:
            continue
        # e.g. "OmniDateParserBenchmark.omniDateParser  thrpt  5  1182152.183 ± ..."
        m = re.match(r"[\w.$]+\.(\w+)\s+thrpt\s+\d+\s+([\d.]+)", line)
        if m:
            method = m.group(1)
            if method in METHOD_MAP:
                scores[METHOD_MAP[method]] = float(m.group(2))

    missing = set(METHOD_MAP.values()) - set(scores)
    if missing:
        print(f"WARNING: missing benchmarks in results: {missing}", file=sys.stderr)

    return scores


def round_display(value: float) -> int:
    """Round to nearest 1000 for display (e.g. 1182152 -> 1182000)."""
    return round(value / 1000) * 1000


def fmt(value: int) -> str:
    """Format as '~1,182,000' for display."""
    return f"~{value:,}"


def extract_readme_numbers(readme_file: str) -> dict[str, int]:
    """Extract current published numbers from README.md performance table."""
    text = Path(readme_file).read_text()
    result = {}

    # OmniDateParser row:
    # | **OmniDateParser** | ~1,182,000 ops/s | **~42x faster** | ~1,160,000 ops/s | **~34x faster** |
    m = re.search(
        r"\*\*OmniDateParser\*\*\s*\|"
        r"\s*~([\d,]+)\s*ops/s\s*\|\s*\*\*~(\d+)x faster\*\*\s*\|"
        r"\s*~([\d,]+)\s*ops/s\s*\|\s*\*\*~(\d+)x faster\*\*",
        text,
    )
    if m:
        result["omni_all"]              = int(m.group(1).replace(",", ""))
        result["omni_vs_shotgun_all"]   = int(m.group(2))
        result["omni_core"]             = int(m.group(3).replace(",", ""))
        result["omni_vs_shotgun_core"]  = int(m.group(4))

    # Shotgun row:
    # | Shotgun ... | ~28,000 ops/s | baseline | ~35,000 ops/s | baseline |
    m = re.search(
        r"Shotgun[^|]+\|\s*~([\d,]+)\s*ops/s\s*\|\s*baseline\s*\|\s*~([\d,]+)\s*ops/s",
        text,
    )
    if m:
        result["shotgun_all"]  = int(m.group(1).replace(",", ""))
        result["shotgun_core"] = int(m.group(2).replace(",", ""))

    # Single known formatter row:
    # | Single known formatter (ceiling) | ~1,038,000 ops/s | ~37x faster | — | — |
    m = re.search(
        r"Single known[^|]+\|\s*~([\d,]+)\s*ops/s\s*\|\s*~(\d+)x faster",
        text,
    )
    if m:
        result["single_all"]             = int(m.group(1).replace(",", ""))
        result["single_vs_shotgun_all"]  = int(m.group(2))

    return result


def main():
    results_file = sys.argv[1] if len(sys.argv) > 1 else "build/results/jmh/results.txt"
    readme_file  = sys.argv[2] if len(sys.argv) > 2 else "README.md"

    # Parse raw scores
    raw = parse_results(results_file)

    omni_all    = raw.get("omni_all", 0)
    omni_core   = raw.get("omni_core", 0)
    shotgun_all = raw.get("shotgun_all", 0)
    shotgun_core= raw.get("shotgun_core", 0)
    single_all  = raw.get("single_all", 0)

    # Compute multipliers (rounded to nearest integer)
    omni_vs_shotgun_all  = round(omni_all  / shotgun_all)  if shotgun_all  else 0
    omni_vs_shotgun_core = round(omni_core / shotgun_core) if shotgun_core else 0
    single_vs_shotgun    = round(single_all / shotgun_all) if shotgun_all  else 0

    new = {
        "omni_all":             round_display(omni_all),
        "omni_core":            round_display(omni_core),
        "shotgun_all":          round_display(shotgun_all),
        "shotgun_core":         round_display(shotgun_core),
        "single_all":           round_display(single_all),
        "omni_vs_shotgun_all":  omni_vs_shotgun_all,
        "omni_vs_shotgun_core": omni_vs_shotgun_core,
        "single_vs_shotgun":    single_vs_shotgun,
        "raw_scores":           {k: round(v, 1) for k, v in raw.items()},
    }

    # Read existing README numbers
    existing = extract_readme_numbers(readme_file)

    # Find significant changes (>5% delta on throughput numbers)
    # direction: "up" = faster (good), "down" = slower (regression)
    changes = {}
    regressions = {}
    for key in ("omni_all", "omni_core", "shotgun_all", "shotgun_core", "single_all"):
        old_val = existing.get(key, 0)
        new_val = new[key]
        if old_val > 0:
            signed_delta = (new_val - old_val) / old_val
            delta = abs(signed_delta)
            if delta > THRESHOLD:
                entry = {
                    "old": old_val,
                    "new": new_val,
                    "delta_pct": round(delta * 100, 1),
                    "old_fmt": fmt(old_val),
                    "new_fmt": fmt(new_val),
                    "direction": "up" if signed_delta > 0 else "down",
                }
                changes[key] = entry
                # Regressions: OmniDateParser or Single going down,
                # or Shotgun going *up* (would mean our baseline got faster,
                # making relative gains look smaller — not a regression per se,
                # so we only flag OmniDateParser and Single going down)
                if signed_delta < 0 and key in ("omni_all", "omni_core", "single_all"):
                    regressions[key] = entry

    # Human-readable summary lines for each strategy
    summary = {
        "OmniDateParser (all)":  f"{fmt(new['omni_all'])} ops/s  (~{omni_vs_shotgun_all}x vs shotgun)",
        "OmniDateParser (core)": f"{fmt(new['omni_core'])} ops/s  (~{omni_vs_shotgun_core}x vs shotgun)",
        "Shotgun (all)":         f"{fmt(new['shotgun_all'])} ops/s",
        "Shotgun (core)":        f"{fmt(new['shotgun_core'])} ops/s",
        "Single formatter":      f"{fmt(new['single_all'])} ops/s  (~{single_vs_shotgun}x vs shotgun)",
    }

    output = {
        "new":           new,
        "existing":      existing,
        "changes":       changes,
        "regressions":   regressions,
        "update_needed": len(changes) > 0,
        "summary":       summary,
    }

    print(json.dumps(output, indent=2))
    sys.exit(1 if output["update_needed"] else 0)


if __name__ == "__main__":
    main()