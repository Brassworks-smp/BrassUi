//! Handing over to the JVM.

use std::path::Path;
use std::process::Command;

use crate::java::JavaInstall;
use crate::Error;

/// Run `jar` on `java` and wait for it to exit.
///
/// Blocking on purpose. A launcher that returned as soon as the JVM started would let the process
/// exit — tearing down the temporary directory holding the jar it just wrote, out from under a JVM
/// that has not finished reading it. It also means the launcher's exit code can be the app's, and
/// that a shell or a process supervisor sees one process for one application rather than a spawner
/// that vanishes.
///
/// stdio is inherited, so an app run from a terminal prints to that terminal.
pub fn run_jar(
    java: &JavaInstall,
    jar: &Path,
    jvm_args: &[String],
    app_args: &[String],
) -> Result<(), Error> {
    let mut command = Command::new(&java.path);
    command.args(jvm_args).arg("-jar").arg(jar).args(app_args);

    let status = command.status().map_err(|e| {
        Error::NoJava(format!("could not start {}: {e}", java.path.display()))
    })?;

    if status.success() {
        Ok(())
    } else {
        Err(Error::AppFailed(status.code()))
    }
}
