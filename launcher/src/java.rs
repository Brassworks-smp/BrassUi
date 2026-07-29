//! Finding a Java runtime that will actually run your class files.
//!
//! The hard part of this is not locating `java` — it is establishing that a particular `java` works.
//! A path can be a wrapper script that prints an advert, a broken symlink left by an uninstall, a
//! 32-bit build on a 64-bit machine, or simply too old. So every candidate here is **verified by
//! running it** and reading the version out of the output. It costs a few milliseconds per candidate
//! and removes a whole category of "it launched fine on my machine".

use std::path::{Path, PathBuf};
use std::process::Command;

/// A Java runtime that has been verified to run.
#[derive(Debug, Clone)]
pub struct JavaInstall {
    /// The `java` executable.
    pub path: PathBuf,
    /// Feature release — 8, 11, 17, 21…
    pub version: u32,
}

/// The first runtime found that is at least `minimum`, or `None`.
///
/// Searched in order of how likely each is to be the one the user would expect: `JAVA_HOME`, then
/// `PATH`, then the conventional install directories for the platform.
pub fn find(minimum: u32) -> Option<JavaInstall> {
    candidates()
        .into_iter()
        .filter_map(|p| probe(&p))
        .find(|j| j.version >= minimum)
}

/// Every place worth looking, in priority order.
///
/// Deliberately not deduplicated: [`probe`] is cheap and a duplicate costs one extra process, while
/// getting the dedup wrong (`/usr/bin/java` vs a symlink to it) costs a runtime that was right there.
fn candidates() -> Vec<PathBuf> {
    let mut out = Vec::new();

    if let Some(home) = std::env::var_os("JAVA_HOME") {
        out.push(PathBuf::from(home).join("bin").join(exe()));
    }

    // Whatever `java` resolves to on PATH. Asking the shell's own lookup is more reliable than
    // walking PATH ourselves, which would have to reimplement PATHEXT on Windows.
    out.push(PathBuf::from(exe()));

    out.extend(platform_dirs().into_iter().flat_map(|dir| {
        // One level down: these directories hold one folder per installed runtime.
        std::fs::read_dir(&dir)
            .into_iter()
            .flatten()
            .flatten()
            .map(|e| e.path())
            .flat_map(|p| {
                [
                    p.join("bin").join(exe()),
                    // macOS bundles nest the runtime inside the .jdk package.
                    p.join("Contents").join("Home").join("bin").join(exe()),
                ]
            })
            .collect::<Vec<_>>()
    }));

    out
}

/// The conventional per-platform install roots.
fn platform_dirs() -> Vec<PathBuf> {
    let mut dirs: Vec<PathBuf> = Vec::new();

    if cfg!(target_os = "macos") {
        dirs.push(PathBuf::from("/Library/Java/JavaVirtualMachines"));
        if let Some(home) = dirs::home_dir() {
            dirs.push(home.join("Library/Java/JavaVirtualMachines"));
        }
    } else if cfg!(target_os = "windows") {
        for root in ["ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"] {
            if let Some(base) = std::env::var_os(root) {
                let base = PathBuf::from(base);
                dirs.push(base.join("Java"));
                dirs.push(base.join("Eclipse Adoptium"));
                dirs.push(base.join("Microsoft"));
            }
        }
    } else {
        dirs.push(PathBuf::from("/usr/lib/jvm"));
        dirs.push(PathBuf::from("/usr/java"));
        if let Some(home) = dirs::home_dir() {
            dirs.push(home.join(".sdkman/candidates/java"));
        }
    }

    dirs
}

/// Run `java -version` and read the feature release out of it, or `None` if it did not run.
///
/// The version goes to **stderr**, not stdout — a detail that predates the JDK being called the JDK
/// and has outlived several attempts to change it — so both streams are searched rather than assuming.
pub fn probe(java: &Path) -> Option<JavaInstall> {
    let out = Command::new(java).arg("-version").output().ok()?;
    if !out.status.success() {
        return None;
    }

    let text = String::from_utf8_lossy(&out.stderr).into_owned()
        + &String::from_utf8_lossy(&out.stdout);
    let version = parse_version(&text)?;

    Some(JavaInstall {
        // Resolve now, so the returned path does not depend on the caller's working directory or on
        // PATH still meaning the same thing later.
        path: which(java).unwrap_or_else(|| java.to_path_buf()),
        version,
    })
}

/// The feature release named in `java -version` output.
///
/// Handles both schemes, because a machine can still have either: `1.8.0_412` is Java 8 (the leading
/// `1.` is the historical prefix, and the release is the *second* component), while `21.0.3` is
/// Java 21. Reading the first number in both cases would call every modern JDK "Java 1".
pub fn parse_version(text: &str) -> Option<u32> {
    // The first quoted run is the version: `openjdk version "21.0.3" 2024-04-16`.
    let quoted = text.split('"').nth(1)?;
    let mut parts = quoted.split(['.', '_', '-', '+']);
    let first: u32 = parts.next()?.parse().ok()?;

    if first == 1 {
        parts.next()?.parse().ok()
    } else {
        Some(first)
    }
}

/// Resolve a bare command name to a full path by asking the platform's own lookup.
fn which(cmd: &Path) -> Option<PathBuf> {
    if cmd.is_absolute() {
        return Some(cmd.to_path_buf());
    }
    let finder = if cfg!(target_os = "windows") { "where" } else { "which" };
    let out = Command::new(finder).arg(cmd).output().ok()?;
    if !out.status.success() {
        return None;
    }
    String::from_utf8_lossy(&out.stdout)
        .lines()
        .next()
        .map(|l| PathBuf::from(l.trim()))
}

/// The platform's name for the java binary.
pub(crate) fn exe() -> &'static str {
    if cfg!(target_os = "windows") {
        "java.exe"
    } else {
        "java"
    }
}

#[cfg(test)]
mod tests {
    use super::parse_version;

    #[test]
    fn reads_the_modern_scheme() {
        let out = r#"openjdk version "21.0.3" 2024-04-16
OpenJDK Runtime Environment Temurin-21.0.3+9 (build 21.0.3+9)"#;
        assert_eq!(parse_version(out), Some(21));
    }

    #[test]
    fn reads_the_legacy_scheme_as_its_second_component() {
        // The whole point of the special case: this is Java 8, not Java 1.
        let out = r#"java version "1.8.0_412""#;
        assert_eq!(parse_version(out), Some(8));
    }

    #[test]
    fn handles_early_access_and_build_suffixes() {
        assert_eq!(parse_version(r#"openjdk version "24-ea" 2025-03-18"#), Some(24));
        assert_eq!(parse_version(r#"openjdk version "17.0.11+9""#), Some(17));
    }

    #[test]
    fn declines_output_with_no_version_in_it() {
        assert_eq!(parse_version("command not found"), None);
        assert_eq!(parse_version(""), None);
    }
}
