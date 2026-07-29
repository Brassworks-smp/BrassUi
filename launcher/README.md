# jvm-bootstrap

Ship a JVM application as a native executable. Finds a Java that will actually run your bytecode,
downloads one if there isn't a suitable one, unpacks the jar you baked into the binary, and launches it.

```rust
use jvm_bootstrap::{Bootstrap, Os};

fn main() -> std::process::ExitCode {
    Bootstrap::new("My App")
        .java(21)
        .embedded_jar(include_bytes!(concat!(env!("OUT_DIR"), "/app.jar")))
        .jvm_arg_on(Os::MacOs, "-XstartOnFirstThread")
        .run()
}
```

Copy [`template/`](template/) and change two lines — the app name and the jar path in `build.rs` — and
you have a single-file executable that runs your app on a machine with or without Java on it.

## What it does

1. **Finds Java.** `JAVA_HOME`, then `PATH`, then the per-OS install directories, then anything it
   downloaded on a previous run. Every candidate is verified *by running it*, because a `java` on
   `PATH` is regularly a stub, a broken symlink, or too old.
2. **Downloads one if it has to**, from [Adoptium](https://adoptium.net), into a per-user cache. This
   is the only step that touches the network and the only one that opens a window.
3. **Launches your jar**, forwarding stdio and waiting for exit, so the launcher's lifetime and exit
   code match the app's.

## Design

**Nothing is shown on the happy path.** A user who already has Java sees their app start, not a splash
screen announcing that no work was needed. The progress window exists for the first run, where tens of
megabytes are being fetched and silence reads as a hang.

**It's a library, not a framework.** `java::find`, `provision::ensure` and `launch::run_jar` are public
and usable on their own, because the moment a real application ships it wants one of those steps done
differently.

**The window is cheap.** Behind the optional `window` feature, and built from `winit` + `softbuffer`
only — no rasteriser, no GPU stack, no font crate. It draws filled rectangles and a 5×7 bitmap font
defined in `font.rs`, which is all a progress bar and one line of status need, and which avoids
embedding a font file and honouring its licence.

```toml
[dependencies]
jvm-bootstrap = { version = "0.1", features = ["window"] }
```

Leave the feature off for a console-only launcher — smaller binary, fewer dependencies, and the right
choice if your app bundles its own runtime and never downloads one.

## Licence

[PolyForm Noncommercial 1.0.0](LICENSE.md). Source-available, not open source: noncommercial use is
granted, commercial use by anyone else is not.
