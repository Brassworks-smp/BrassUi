#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
wiki="${1:?path to a clone of https://github.com/Brassworks-smp/BrassUi.wiki.git}"
tmp="$(mktemp -d)"
python3 "$here/apigen.py" "$repo/core/src/main/kotlin" "$tmp/core.json"
python3 "$here/apigen.py" "$repo/mod/src/main/kotlin" "$tmp/mod.json"
version="$(grep -E '^version=' "$repo/gradle.properties" | cut -d= -f2)"
python3 "$here/apirender.py" "$tmp/core.json" "$tmp/mod.json" "$wiki" "$version"
rm -rf "$tmp"
