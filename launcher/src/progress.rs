//! Reporting what the launcher is doing.
//!
//! The interesting design point is that on the happy path this reports *nothing*. A user who already
//! has a working Java sees their application start; a splash screen announcing that no work was
//! needed is worse than silence. The reporting exists for the first run, where tens of megabytes are
//! being fetched and silence reads as a hang.

/// Where the launcher has got to.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Stage {
    /// Checking the machine for a usable runtime. Normally over in milliseconds.
    FindingJava,
    /// Asking Adoptium which build to fetch.
    Resolving,
    /// Downloading it — the long one, and the only stage with a meaningful fraction.
    Downloading,
    /// Unpacking the archive.
    Unpacking,
    /// Handing over to the JVM.
    Launching,
}

impl Stage {
    /// A short line to show the user.
    pub fn message(self) -> &'static str {
        match self {
            Stage::FindingJava => "Looking for Java…",
            Stage::Resolving => "Finding a Java runtime to download…",
            Stage::Downloading => "Downloading Java…",
            Stage::Unpacking => "Unpacking Java…",
            Stage::Launching => "Starting…",
        }
    }
}

/// Somewhere to report to — the console, a window, a log, or nothing at all.
pub trait Progress {
    /// The launcher moved to a new stage.
    fn stage(&mut self, stage: Stage);

    /// Bytes transferred so far, and the total if the server declared one.
    ///
    /// Called often during a download, so an implementation that draws should rate-limit itself
    /// rather than repainting per chunk.
    fn bytes(&mut self, _done: u64, _total: Option<u64>) {}

    /// Something went wrong. The launcher is about to exit.
    fn failed(&mut self, _message: &str) {}
}

/// Discards everything. For a launcher that should be seen and not heard.
pub struct Silent;

impl Progress for Silent {
    fn stage(&mut self, _stage: Stage) {}
}

/// Reports to stderr, with the download as a single rewritten line.
///
/// stderr rather than stdout because the application being launched inherits both, and its own
/// output is the thing worth piping.
#[derive(Default)]
pub struct Console {
    last_percent: u8,
    downloading: bool,
}

impl Progress for Console {
    fn stage(&mut self, stage: Stage) {
        // End the progress line before anything else is written over it.
        if self.downloading && stage != Stage::Downloading {
            eprintln!();
            self.downloading = false;
        }
        if stage == Stage::Downloading {
            self.downloading = true;
        }
        eprintln!("{}", stage.message());
    }

    fn bytes(&mut self, done: u64, total: Option<u64>) {
        let Some(total) = total.filter(|t| *t > 0) else {
            return;
        };
        let percent = ((done * 100) / total).min(100) as u8;
        // Only on change: at a few hundred chunks a second, repainting per chunk is most of the cost
        // of the download on a slow terminal.
        if percent != self.last_percent {
            self.last_percent = percent;
            eprint!("\r  {percent}% of {} MiB", total / (1024 * 1024));
        }
    }

    fn failed(&mut self, message: &str) {
        if self.downloading {
            eprintln!();
            self.downloading = false;
        }
        eprintln!("error: {message}");
    }
}
