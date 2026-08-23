#!/usr/bin/env bash
# Build the brassui Ultralight native bundle for one os/arch and zip it.
#
# WHAT IT PRODUCES
#   `bin/`        - the engine's shared libraries (from Ultralight's official SDK) PLUS the JNI
#                   binding layer (libultralight-java + libultralight-java-gpu) built from source
#                   against that SDK. This is exactly what kit/html/internal/HtmlResources loads.
#   `resources/`  - ICU data + CA bundle from the SDK.
#   `licenses/`   - the SDK's license texts.
#   `VERSION`     - the JNI binding version + the engine SDK commit, for the runtime's freshness
#                   check.
#
# WHY IT IS SPLIT THIS WAY
#   UltralightCore + WebCore + Ultralight + AppCore are proprietary; Ultralight Inc. publishes them
#   as prebuilt SDK archives per OS/arch. Those are downloaded as-is. The LabyMod JNI layer is open
#   source, so it is compiled here. Nothing else is built.
#
# ARM64
#   Ultralight publishes no arm64 SDK today, so arm64 runs skip (exit 0) with a notice. The moment
#   Ultralight publishes an arm64 archive at `ultralight-sdk-latest-{os}-arm64.7z`, this script
#   produces the arm64 bundle unchanged. Ask them for it — see README.md.
#
# ENV
#   ULTRA_OS        mac | linux
#   ULTRA_ARCH      x64 | arm64
#   ULTRA_SDK_COMMIT  engine commit to package, or `latest` (the CDN's moving target)
#   ULTRA_JNI_COMMIT  LabyMod/ultralight-java commit to build the JNI layer from
#   ULTRA_OUT       directory for the finished zip
#   JAVA_HOME       JDK whose headers the JNI build links against
set -euo pipefail

: "${ULTRA_OS:?ULTRA_OS is required (mac|linux)}"
: "${ULTRA_ARCH:?ULTRA_ARCH is required (x64|arm64)}"
: "${ULTRA_JNI_COMMIT:?ULTRA_JNI_COMMIT is required}"
ULTRA_SDK_COMMIT="${ULTRA_SDK_COMMIT:-latest}"
ULTRA_OUT="${ULTRA_OUT:-$(pwd)/out}"

SDK_BUCKET="https://ultralight-sdk.sfo2.cdn.digitaloceanspaces.com"
JNI_REPO="https://github.com/LabyMod/ultralight-java.git"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/ultralight.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

echo "[ultralight] ${ULTRA_OS}-${ULTRA_ARCH} @ sdk=${ULTRA_SDK_COMMIT} jni=${ULTRA_JNI_COMMIT}"

# ---- 1. Fetch the official engine SDK ----------------------------------------------------------
SDK_NAME="ultralight-sdk-${ULTRA_SDK_COMMIT}-${ULTRA_OS}-${ULTRA_ARCH}.7z"
SDK_URL="${SDK_BUCKET}/${SDK_NAME}"

HTTP_CODE="$(curl -sL -o "${WORK}/sdk.7z" -w '%{http_code}' --fail --max-time 600 "$SDK_URL" || true)"
if [ "$HTTP_CODE" != "200" ]; then
    if [ "$ULTRA_ARCH" = "arm64" ]; then
        echo "[ultralight] no arm64 SDK at ${SDK_URL} — Ultralight publishes x64 only. Skipping (idempotent)."
        exit 0
    fi
    echo "[ultralight] FAILED to download ${SDK_URL} (HTTP ${HTTP_CODE})" >&2
    exit 1
fi

echo "[ultralight] extracting ${SDK_NAME}"
mkdir -p "${WORK}/sdk"
(cd "${WORK}/sdk" && 7z x -y "${WORK}/sdk.7z" >/dev/null)
test -d "${WORK}/sdk/bin" || { echo "[ultralight] SDK has no bin/ — layout changed?" >&2; exit 1; }

# ---- 2. Build the JNI binding layer from source, against that SDK ------------------------------
echo "[ultralight] cloning ultralight-java @ ${ULTRA_JNI_COMMIT}"
git init --quiet "${WORK}/jni"
git -C "${WORK}/jni" remote add origin "$JNI_REPO"
git -C "${WORK}/jni" fetch --quiet --depth 1 origin "$ULTRA_JNI_COMMIT"
git -C "${WORK}/jni" checkout --quiet FETCH_HEAD

git -C "${WORK}/jni" apply "${SCRIPT_DIR}/Ultralight.cmake.patch"
export ULTRALIGHT_SDK_DIR="${WORK}/sdk"

build_jni() {
    local module="$1" out="${WORK}/native"
    echo "[ultralight] building ${module}"
    cmake -S "${WORK}/jni/${module}" -B "${WORK}/build-${module}" -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="${out}" \
        -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="${out}" \
        -DCMAKE_ARCHIVE_OUTPUT_DIRECTORY="${out}" >/dev/null
    cmake --build "${WORK}/build-${module}" --parallel >/dev/null
}

build_jni ultralight-java-native
build_jni ultralight-java-gpu-native

# ---- 3. Assemble the bundle brassui loads ------------------------------------------------------
BUNDLE="${WORK}/bundle"
mkdir -p "${BUNDLE}/bin" "${BUNDLE}/resources" "${BUNDLE}/licenses"
cp -a "${WORK}/sdk/bin/." "${BUNDLE}/bin/"
# Normalise the ICU data / CA bundle to top-level `resources/`: newer SDKs ship it there directly,
# older ones (pre-b8daecd, e.g. the 5011dbf pin) dropped it under `bin/resources/`. brassui's bundle
# mirrors the known-good LiquidBounce layout (top-level resources/), which the engine's own ICU
# search knows how to find.
if [ -d "${WORK}/sdk/resources" ]; then
    cp -a "${WORK}/sdk/resources/." "${BUNDLE}/resources/"
fi
if [ -d "${BUNDLE}/bin/resources" ]; then
    cp -a "${BUNDLE}/bin/resources/." "${BUNDLE}/resources/"
    rm -rf "${BUNDLE}/bin/resources"
fi
if [ -d "${WORK}/sdk/license" ]; then cp -a "${WORK}/sdk/license/." "${BUNDLE}/licenses/"; fi
if [ -d "${WORK}/sdk/licenses" ]; then cp -a "${WORK}/sdk/licenses/." "${BUNDLE}/licenses/"; fi
cp -a "${WORK}/native/." "${BUNDLE}/bin/"
[ -f "${WORK}/sdk/LOG.txt" ] && cp "${WORK}/sdk/LOG.txt" "${BUNDLE}/LOG.txt"

# Make the JNI libraries find the engine libraries sitting next to them in `bin/`. The SDK dylibs
# reference each other via @rpath (macOS) / DT_NEEDED (Linux); the engine dylibs load first at
# runtime (UltralightJava.load), which is how the original bundle resolved them — but a local rpath
# makes it deterministic instead of relying on load order.
case "$ULTRA_OS" in
    mac)
        for dylib in "${BUNDLE}"/bin/libultralight-java*.dylib; do
            install_name_tool -add_rpath @loader_path "$dylib" 2>/dev/null || true
        done
        ;;
    linux)
        for so in "${BUNDLE}"/bin/libultralight-java*.so; do
            patchelf --set-rpath '$ORIGIN' "$so" 2>/dev/null || true
        done
        ;;
esac

# The runtime's version gate keys on this line (see HtmlResources.ensure).
echo "0.4.12/${ULTRA_SDK_COMMIT}" > "${BUNDLE}/VERSION"

# macOS: ad-hoc sign the dylibs so a hardened macOS still loads them.
if [ "$ULTRA_OS" = "mac" ] && command -v codesign >/dev/null; then
    find "${BUNDLE}/bin" -name '*.dylib' -exec codesign --force -s - {} \; 2>/dev/null || true
fi

echo "[ultralight] bundle contents:"
ls -1 "${BUNDLE}/bin"

# ---- 4. Zip ------------------------------------------------------------------------------------
mkdir -p "$ULTRA_OUT"
ZIP="${ULTRA_OUT}/ultralight-${ULTRA_OS}-${ULTRA_ARCH}.zip"
rm -f "$ZIP"
(cd "$BUNDLE" && zip -qr "$ZIP" .)
echo "[ultralight] wrote ${ZIP}"
