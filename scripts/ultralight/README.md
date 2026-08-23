# Building the Ultralight native bundles

This folder is the build side of brassui's embedded-HTML widget (`kit/html`). The widget's runtime
downloads a per-OS natives bundle and loads it (see `HtmlResources`); this pipeline produces that
bundle, on a schedule and by hand, and publishes it to the `ultralight-natives` GitHub Release.

## What the bundle is, and where each piece comes from

| Piece | Source | Built here? |
|---|---|---|
| `libUltralightCore`, `libWebCore`, `libUltralight`, `libAppCore` | Ultralight Inc.'s official SDK (`ultralight-sdk.sfo2.cdn.digitaloceanspaces.com`) | No — downloaded as-is |
| `libultralight-java`, `libultralight-java-gpu` | github.com/LabyMod/ultralight-java (open source, LGPL) | Yes — CMake against the SDK |
| `resources/` (ICU data, CA bundle) | the SDK | No — copied as-is |

Only the open-source JNI layer is compiled; the proprietary engine is redistributed unchanged under
Ultralight's free license (free for non-commercial use, and for commercial use while under $100k/yr
revenue — see the SDK's `license/LICENSE.txt`).

## How to run

```sh
# one platform
ULTRA_OS=mac ULTRA_ARCH=x64 ULTRA_JNI_COMMIT=<sha> bash scripts/ultralight/build-ultralight-natives.sh

# all platforms, from CI (recommended)
#   Actions → "Build Ultralight natives" → Run workflow
```

The workflow matrix: `mac-x64`, `mac-arm64`, `linux-x64`, `linux-arm64` on the matching runners,
weekly on schedule and on demand. Output goes to the `ultralight-natives` release as
`ultralight-{os}-{arch}.zip`, which the runtime can be pointed at with:

```
-Dbrassui.html.resourcesUrl=https://github.com/Brassworks-smp/BrassUi/releases/latest/download/ultralight-{os}-{arch}.zip
```

(`{os}`/`{arch}` are filled in by `HtmlResources`.)

## The arm64 situation — please ask Ultralight

Ultralight only publishes **x64** SDK archives (every asset on both their `ultralight-sdk` and
`webcore-bin` buckets is `*-x64`). Because of that, the arm64 jobs in the workflow **skip with a
notice** until arm64 archives exist — this is deliberate: the moment Ultralight publishes
`ultralight-sdk-latest-mac-arm64.7z` (or linux), the next run produces and ships the arm64 bundle
with **no changes to this repo**.

Until then, the honest coverage story is:

- Windows / Linux: effectively all x64 → works.
- Apple Silicon *in-game with the vanilla launcher*: works — Minecraft 1.21's bundled macOS JRE is
  x86_64, so it runs under Rosetta and reports `os.arch == x86_64`, which loads the x64 bundle.
- Arm64-native JVMs (third-party launchers like Prism/MultiMC, and the desktop app on a modern Mac
  JDK): the widget shows its "engine unavailable" card until arm64 natives exist.

**To close the gap**, email Ultralight Inc. (contact@ultralight.ai) and ask for arm64 macOS/Linux
SDK binaries under the free license. The SDK is already free for non-commercial use; arm64 CI builds
for their own Windows/Linux/macOS targets are a reasonable ask. The moment they land, this pipeline
does the rest.

## Version pins

- `ULTRA_SDK_COMMIT` defaults to `latest` (the CDN's moving target) — override with
  `workflow_dispatch` for a reproducible pin (e.g. `5011dbf`, the commit brassui's original bundle
  was built from).
- `ULTRA_JNI_COMMIT` is pinned in the workflow to a LabyMod `develop` commit; bump it when
  brassui bumps `ultralight_java_version` in `gradle.properties`.

## Local notes

- Needs `7z` (p7zip), `cmake`, `ninja`, a JDK (`JAVA_HOME`), and `git`.
- The JNI build uses the SDK headers/libs from the downloaded archive; no network access beyond the
  SDK download. `Ultralight.cmake.patch` (applied to the JNI checkout) makes the SDK a pre-placed
  directory and teaches it about `arm64`; keep it in sync if upstream's file changes.
