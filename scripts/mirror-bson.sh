#!/usr/bin/env bash
# OBSOLETE — kept for history only.
#
# This used to mirror MongoDB's pure-JVM BSON into the Brassworks Maven so brassui consumers never
# needed a MongoDB repository line. Since the relocation work the `:bson` project builds the library
# IN-REPO (ASM-rewriting org.mongodb:bson into net.swzo.brass.vendor.bson — see bson/build.gradle) and
# `./gradlew publish` ships it as net.swzo.brass:bson; consumers no longer resolve org.mongodb:bson
# from anywhere. Do not re-run this. The real bson is still fetched from Maven Central at build time by
# `:bson`'s own configuration.
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
