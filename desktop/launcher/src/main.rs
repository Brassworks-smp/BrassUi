//! Native launcher for the brassui desktop gallery.
//!
//! Everything interesting is in [`jvm_bootstrap`]; this file is the configuration. It is deliberately
//! almost identical to `launcher/template/` — if it needed more than this to wrap a real application,
//! the crate would not be pulling its weight.

#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::process::ExitCode;

use jvm_bootstrap::{Bootstrap, Os};

#[cfg(has_jar)]
const JAR: &[u8] = include_bytes!(concat!(env!("OUT_DIR"), "/app.jar"));
#[cfg(not(has_jar))]
const JAR: &[u8] = &[];

fn main() -> ExitCode {
    Bootstrap::new("brassui")
        // The Gradle toolchain is 21, so the class files need a 21+ runtime.
        .java(21)
        .embedded_jar(JAR)
        // So `cargo run` works against whatever shadowJar last produced.
        .jar_fallback("../build/libs/brassui-desktop-all.jar")
        // GLFW must own the process's first thread on macOS or glfwInit aborts.
        .jvm_arg_on(Os::MacOs, "-XstartOnFirstThread")
        .run()
}
