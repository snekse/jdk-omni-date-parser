#!/usr/bin/env bash
# check-fixtures.sh
#
# Validates consistency of the three test fixture files:
#   src/test/resources/examples.txt
#   src/test/resources/unsupported-examples.txt
#   src/test/resources/invalid-examples.txt
#
# Checks:
#   1. No entry appears in both examples.txt and unsupported-examples.txt
#   2. No entry appears in both examples.txt and invalid-examples.txt
#   3. No entry appears in both unsupported-examples.txt and invalid-examples.txt
#
# Usage:
#   bash .claude/skills/add-format-support/check-fixtures.sh

REPO_ROOT="$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null || pwd)"
EXAMPLES="$REPO_ROOT/src/test/resources/examples.txt"
UNSUPPORTED="$REPO_ROOT/src/test/resources/unsupported-examples.txt"
INVALID="$REPO_ROOT/src/test/resources/invalid-examples.txt"

# Extract non-blank, non-comment lines from a file
entries() {
    grep -v '^\s*#' "$1" | grep -v '^\s*$' | sort
}

errors=0

check_overlap() {
    local file_a="$1"
    local label_a="$2"
    local file_b="$3"
    local label_b="$4"

    overlap=$(comm -12 <(entries "$file_a") <(entries "$file_b"))
    if [[ -n "$overlap" ]]; then
        echo "ERROR: entries found in both $label_a and $label_b:"
        while IFS= read -r line; do
            echo "  >> $line"
        done <<< "$overlap"
        errors=$((errors + 1))
    fi
}

echo "Checking fixture consistency..."
echo

check_overlap "$EXAMPLES"    "examples.txt"    "$UNSUPPORTED" "unsupported-examples.txt"
check_overlap "$EXAMPLES"    "examples.txt"    "$INVALID"     "invalid-examples.txt"
check_overlap "$UNSUPPORTED" "unsupported-examples.txt" "$INVALID" "invalid-examples.txt"

if [[ $errors -eq 0 ]]; then
    ex_count=$(entries "$EXAMPLES" | wc -l | tr -d ' ')
    un_count=$(entries "$UNSUPPORTED" | wc -l | tr -d ' ')
    inv_count=$(entries "$INVALID" | wc -l | tr -d ' ')
    echo "OK — no overlaps found."
    echo "  examples.txt:           $ex_count entries"
    echo "  unsupported-examples.txt: $un_count entries"
    echo "  invalid-examples.txt:   $inv_count entries"
    exit 0
else
    echo
    echo "$errors overlap(s) detected. Fix before running tests."
    exit 1
fi
