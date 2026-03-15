#!/usr/bin/env bash
# Dry-run signed publish to local Maven repo (~/.m2).
# Verifies signing works before triggering a real release.
# Usage: ./scripts/publish-local-signed.sh [version]
#   version defaults to 0.1.0

set -euo pipefail

VERSION="${1:-0.1.0}"
if [[ -z "${GPG_KEY_ID:-}" ]]; then
  read -rp "Enter GPG key ID: " KEY_ID
else
  KEY_ID="$GPG_KEY_ID"
fi

read -rsp "Enter GPG passphrase: " GPG_PASS
echo

ORG_GRADLE_PROJECT_signingInMemoryKey="$(gpg --armor --export-secret-keys "$KEY_ID")"
export ORG_GRADLE_PROJECT_signingInMemoryKey
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$GPG_PASS"
export ORG_GRADLE_PROJECT_releaseVersion="$VERSION"

./gradlew publishToMavenLocal

unset ORG_GRADLE_PROJECT_signingInMemoryKey
unset ORG_GRADLE_PROJECT_signingInMemoryKeyPassword
unset ORG_GRADLE_PROJECT_releaseVersion
unset GPG_PASS

echo ""
echo "Artifacts published to ~/.m2/repository/io/github/snekse/jdk-omni-date-parser/$VERSION/"
ls ~/.m2/repository/io/github/snekse/jdk-omni-date-parser/"$VERSION"/
