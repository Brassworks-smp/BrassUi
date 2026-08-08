#!/usr/bin/env bash
# Mirror the two Essential artifacts brassui's published POM depends on into the Brassworks Maven,
# so consumers need only one repository (maven.opnsoc.org) and no Essential repo line.
#
# Run after bumping elementa_version or universalcraft_version in gradle.properties:
#     ./scripts/mirror-essential.sh
#
# The mirror is a straight copy of Essential's public release (same coordinates, POMs as-is). Credentials
# come from the gitignored root .env (BRASSWORKS_MAVEN_USER / BRASSWORKS_MAVEN_KEY).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a

ESSENTIAL="https://repo.essential.gg/repository/maven-public"
MAVEN="${BRASSWORKS_MAVEN_URL:-https://maven.opnsoc.org/releases}"

ELEMENTA_VERSION="$(grep -E '^elementa_version=' "$ROOT/gradle.properties" | cut -d= -f2)"
UC_VERSION="$(grep -E '^universalcraft_version=' "$ROOT/gradle.properties" | cut -d= -f2)"

mirror() {
    local group_path="$1" artifact="$2" version="$3"
    local base="$ESSENTIAL/$group_path/$artifact/$version"
    local out="$MAVEN/$group_path/$artifact/$version"
    for ext in pom jar; do
        echo "mirroring $artifact-$version.$ext"
        curl -fsSL "$base/$artifact-$version.$ext" -o "/tmp/$artifact-$version.$ext"
        curl -fsS -u "${BRASSWORKS_MAVEN_USER}:${BRASSWORKS_MAVEN_KEY}" \
            -T "/tmp/$artifact-$version.$ext" "$out/$artifact-$version.$ext"
    done
}

mirror "gg/essential" "elementa" "$ELEMENTA_VERSION"
mirror "gg/essential" "universalcraft-1.21-neoforge" "$UC_VERSION"

echo "done — elementa $ELEMENTA_VERSION and universalcraft $UC_VERSION are now on $MAVEN"
