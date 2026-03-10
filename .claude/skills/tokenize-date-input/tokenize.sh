#!/usr/bin/env bash
# tokenize.sh
#
# Prints the token stream the Lexer emits for a given input string.
# Uses TokenizePrinter (src/test/java/.../TokenizePrinter.java) compiled by Gradle.
#
# Usage:
#   bash .claude/skills/tokenize-date-input/tokenize.sh "12:00 noon EST"
#   bash .claude/skills/tokenize-date-input/tokenize.sh "Jan. 1, 1999 @ 12:00 PM"

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
MAIN_CLASSES="$REPO_ROOT/build/classes/java/main"
TEST_CLASSES="$REPO_ROOT/build/classes/java/test"

if [[ $# -eq 0 ]]; then
    echo "Usage: tokenize.sh <date-string>"
    exit 1
fi

# Build if test classes are missing
if [[ ! -d "$TEST_CLASSES" ]]; then
    echo "Building project..."
    (cd "$REPO_ROOT" && ./gradlew testClasses -q)
fi

java -cp "$MAIN_CLASSES:$TEST_CLASSES" \
    io.github.snekse.jdk.dateparser.TokenizePrinter "$@"
