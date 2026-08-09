#!/usr/bin/env bash
# Mirror MongoDB's pure-JVM BSON library into the Brassworks Maven, so brassui consumers need only
# maven.opnsoc.org + mavenCentral and never a MongoDB repository line.
#
# Run after bumping bson_version in gradle.properties:
#     ./scripts/mirror-bson.sh
#
# The mirror is a straight copy of the Maven Central release (same coordinates, POM as-is; its only
# dependency is an *optional* slf4j-api, so nothing else needs mirroring). Credentials come from the
# gitignored root .env (BRASSWORKS_MAVEN_USER / BRASSWORKS_MAVEN_KEY).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

set -a
# shellcheck disable=SC1091
source "$ROOT/.env"
set +a

CENTRAL="https://repo1.maven.org/maven2"
MAVEN="${BRASSWORKS_MAVEN_URL:-https://maven.opnsoc.org/releases}"

BSON_VERSION="$(grep -E '^bson_version=' "$ROOT/gradle.properties" | cut -d= -f2)"

base="$CENTRAL/org/mongodb/bson/$BSON_VERSION"
out="$MAVEN/org/mongodb/bson/$BSON_VERSION"
for ext in pom jar; do
    echo "mirroring bson-$BSON_VERSION.$ext"
    curl -fsSL "$base/bson-$BSON_VERSION.$ext" -o "/tmp/bson-$BSON_VERSION.$ext"
    curl -fsS -u "${BRASSWORKS_MAVEN_USER}:${BRASSWORKS_MAVEN_KEY}" \
        -T "/tmp/bson-$BSON_VERSION.$ext" "$out/bson-$BSON_VERSION.$ext"
done

echo "done — bson $BSON_VERSION is now on $MAVEN"
