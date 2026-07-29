//! A native launcher for a JVM application.
//!
//! Copy this directory, change the name and the jar path in `build.rs`, and you have a single-file
//! executable that runs your app on any machine — with or without Java already installed.

// A GUI app should not open a console window on Windows. Kept in debug builds so `cargo run` can
// still print.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::process::ExitCode;

use jvm_bootstrap::{Bootstrap, Os};

/// The jar baked in by `build.rs`, or nothing when the build had none.
#[cfg(has_jar)]
const JAR: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/app.jar"));
#[cfg(not(has_jar))]
const JAR: &[u8] = &[];

fn main() -> ExitCode {
    Bootstrap::new("My App")
        // The minimum feature release your class files need.
        .java(21)
        .embedded_jar(JAR)
        // Where to look when nothing was embedded, so `cargo run` works during development against a
        // jar your JVM build just rewrote.
        .jar_fallback("../build/libs/app-all.jar")
        // GLFW on macOS must own the process's first thread or it aborts on init. Harmless to omit if
        // your app does not open a window.
        .jvm_arg_on(Os::MacOs, "-XstartOnFirstThread")
        .run()
}
