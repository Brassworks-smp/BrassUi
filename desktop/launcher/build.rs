//! Bakes the desktop gallery's fat jar into the executable, so the wrapper ships as a single file.
//!
//! A copy of `launcher/template/build.rs` with the jar path pointed at Gradle's shadowJar output —
//! which is the whole of what adopting `jvm-bootstrap` for a real app involves.
//!
//! The jar is taken from `APP_JAR` when set, otherwise from the default build output path below —
//! point that at wherever your build system writes its fat jar.
//!
//! When no jar is found the build still succeeds, embedding nothing and leaving `HAS_JAR` unset. That
//! is deliberate: it keeps `cargo build` working on a fresh clone before anyone has run the JVM-side
//! build, and `main.rs` falls back to finding a jar on disk. A hard failure here would mean the two
//! builds had to be run in a particular order forever after.

use std::path::PathBuf;
use std::{env, fs};

/// Where to look when `APP_JAR` is not set. Change this to your build output.
const DEFAULT_JAR: &str = "../build/libs/brassui-desktop-all.jar";

fn main() -> std::io::Result<()> {
    println!("cargo:rerun-if-env-changed=APP_JAR");
    println!("cargo:rustc-check-cfg=cfg(has_jar)");

    let manifest_dir = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap());
    let jar = env::var("APP_JAR")
        .map(PathBuf::from)
        .unwrap_or_else(|_| manifest_dir.join(DEFAULT_JAR));

    let out = PathBuf::from(env::var("OUT_DIR").unwrap()).join("app.jar");

    if jar.is_file() {
        println!("cargo:rerun-if-changed={}", jar.display());
        fs::copy(&jar, &out)?;
        println!("cargo:rustc-cfg=has_jar");
    } else {
        println!(
            "cargo:warning=No jar at {} — building without one. \
             Set APP_JAR or build the JVM side, then rebuild.",
            jar.display()
        );
        // An empty file, so `include_bytes!` still has something to point at.
        fs::write(&out, b"")?;
    }

    Ok(())
}
