//! Ship a JVM application as a native executable.
//!
//! A JVM app distributed as a bare `.jar` asks the user to have a JRE, know what a jar is, and open a
//! terminal. This crate is the small native program that stands in front of it: it finds a Java that
//! will actually run your bytecode, downloads one if there isn't a suitable one, unpacks the jar you
//! baked into the executable, and launches it.
//!
//! ```ignore
//! use jvm_bootstrap::{Bootstrap, Os};
//!
//! fn main() -> std::process::ExitCode {
//!     Bootstrap::new("My App")
//!         .java(21)
//!         .embedded_jar(include_bytes!(concat!(env!("OUT_DIR"), "/app.jar")))
//!         // GLFW on macOS must own the process's first thread.
//!         .jvm_arg_on(Os::MacOs, "-XstartOnFirstThread")
//!         .run()
//! }
//! ```
//!
//! That one is `ignore`d rather than run because `OUT_DIR` only exists for a crate with a build
//! script — see `template/` in the repository for the `build.rs` half. Pointing at a jar on disk
//! needs no build script and does compile:
//!
//! ```no_run
//! use jvm_bootstrap::{Bootstrap, Os};
//!
//! let code = Bootstrap::new("My App")
//!     .java(21)
//!     .jar_path("target/app.jar")
//!     .jvm_arg_on(Os::MacOs, "-XstartOnFirstThread")
//!     .run();
//! ```
//!
//! # What it does, in order
//!
//! 1. Looks for a usable JRE — `JAVA_HOME`, then `PATH`, then the per-OS install locations, then any
//!    runtime this crate downloaded on a previous run. Each candidate is *verified by running it*
//!    (see [`java`]), because a `java` on `PATH` is regularly a stub, a broken symlink, or too old.
//! 2. If none qualifies, fetches one from [Adoptium](https://adoptium.net) into a per-user cache
//!    directory and unpacks it. This is the only step that touches the network, and the only one that
//!    opens a window (with the `window` feature).
//! 3. Writes the embedded jar to a temporary file and runs it, forwarding stdio and waiting for exit,
//!    so the launcher's lifetime matches the app's.
//!
//! # Design notes
//!
//! **It is a library, not a framework.** Everything above is also available piecewise — [`java::find`],
//! [`provision::ensure`], [`launch::run_jar`] — because the moment a real application ships, it wants
//! to do one of these steps differently.
//!
//! **Nothing is shown on the happy path.** A user who already has Java sees their app start, not a
//! splash screen reporting that nothing needed doing. The progress window exists for the first run,
//! where 40+ MB is being fetched and silence reads as a hang.
//!
//! **Errors are values.** [`Bootstrap::run`] returns an [`std::process::ExitCode`] and reports through
//! [`Progress::failed`], but [`Bootstrap::try_run`] hands back a [`Error`] if you would rather decide
//! what a failure looks like.

use std::path::PathBuf;
use std::process::ExitCode;

pub mod archive;
#[cfg(feature = "window")]
mod font;
pub mod java;
pub mod launch;
pub mod progress;
pub mod provision;

#[cfg(feature = "window")]
pub mod window;

pub use java::JavaInstall;
pub use progress::{Console, Progress, Stage};

/// Which platform a setting applies to, for [`Bootstrap::jvm_arg_on`].
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Os {
    MacOs,
    Windows,
    Linux,
}

impl Os {
    /// The platform this binary was built for, or `None` on one this crate has no name for.
    pub fn current() -> Option<Self> {
        if cfg!(target_os = "macos") {
            Some(Os::MacOs)
        } else if cfg!(target_os = "windows") {
            Some(Os::Windows)
        } else if cfg!(target_os = "linux") {
            Some(Os::Linux)
        } else {
            None
        }
    }
}

/// Anything that can go wrong between starting up and the JVM taking over.
#[derive(Debug)]
pub enum Error {
    /// No suitable JRE was found and none could be fetched.
    NoJava(String),
    /// The network step failed — offline, a proxy, or Adoptium being unreachable.
    Download(String),
    /// A downloaded archive could not be unpacked.
    Unpack(String),
    /// There was no jar to run: none embedded, and none found on disk.
    NoJar(String),
    /// The JVM started but exited non-zero. Carries the code, or `None` if it was signalled.
    AppFailed(Option<i32>),
    /// A file could not be read or written.
    Io(std::io::Error),
}

impl std::fmt::Display for Error {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Error::NoJava(m) => write!(f, "no usable Java runtime: {m}"),
            Error::Download(m) => write!(f, "could not download a Java runtime: {m}"),
            Error::Unpack(m) => write!(f, "could not unpack the Java runtime: {m}"),
            Error::NoJar(m) => write!(f, "no application jar: {m}"),
            Error::AppFailed(Some(c)) => write!(f, "the application exited with code {c}"),
            Error::AppFailed(None) => write!(f, "the application was terminated"),
            Error::Io(e) => write!(f, "{e}"),
        }
    }
}

impl std::error::Error for Error {}

impl From<std::io::Error> for Error {
    fn from(e: std::io::Error) -> Self {
        Error::Io(e)
    }
}

/// Where the jar to run comes from.
enum JarSource {
    /// Baked into the executable — the normal case, written to a temp file at startup.
    Embedded(&'static [u8]),
    /// A path on disk, for development where the jar is rebuilt far more often than the launcher.
    Path(PathBuf),
    /// Neither was configured.
    None,
}

/// The launcher, configured and then [run](Bootstrap::run).
///
/// See the [crate docs](crate) for the sequence this drives.
pub struct Bootstrap {
    name: String,
    java_version: u32,
    jar: JarSource,
    jar_fallbacks: Vec<PathBuf>,
    jvm_args: Vec<String>,
    app_args: Vec<String>,
    cache_dir: Option<PathBuf>,
    allow_download: bool,
}

impl Bootstrap {
    /// A launcher for an app called `name` — used in the cache directory name, the progress window
    /// title, and error messages.
    pub fn new(name: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            java_version: 17,
            jar: JarSource::None,
            jar_fallbacks: Vec::new(),
            jvm_args: Vec::new(),
            app_args: Vec::new(),
            cache_dir: None,
            allow_download: true,
        }
    }

    /// The minimum feature release your class files need — 17, 21, and so on.
    ///
    /// This is a *floor*, not a pin: a newer runtime already installed is used rather than fetching
    /// the exact version, because the common case is a developer machine with a perfectly good JDK on
    /// it and downloading 40 MB alongside it would be rude.
    pub fn java(mut self, feature_release: u32) -> Self {
        self.java_version = feature_release;
        self
    }

    /// The jar to run, baked into the executable.
    ///
    /// Pair with a `build.rs` that copies your build output into `OUT_DIR` — see the template in the
    /// repository for the whole arrangement.
    pub fn embedded_jar(mut self, bytes: &'static [u8]) -> Self {
        if !bytes.is_empty() {
            self.jar = JarSource::Embedded(bytes);
        }
        self
    }

    /// Run this jar from disk instead of an embedded one.
    pub fn jar_path(mut self, path: impl Into<PathBuf>) -> Self {
        self.jar = JarSource::Path(path.into());
        self
    }

    /// A path to try when no jar was embedded.
    ///
    /// Worth setting to your build output during development: `cargo run` then works against a jar
    /// that was rebuilt a minute ago without relinking the launcher.
    pub fn jar_fallback(mut self, path: impl Into<PathBuf>) -> Self {
        self.jar_fallbacks.push(path.into());
        self
    }

    /// An argument for the JVM itself — `-Xmx2G`, `-Dfoo=bar`.
    pub fn jvm_arg(mut self, arg: impl Into<String>) -> Self {
        self.jvm_args.push(arg.into());
        self
    }

    /// A JVM argument applied only on one platform.
    ///
    /// The reason this exists rather than leaving it to the caller's `cfg!`: the arguments that differ
    /// per platform are exactly the ones that are mandatory rather than optional — `-XstartOnFirstThread`
    /// is not a preference on macOS, it is the difference between a window and an abort — so they read
    /// better as a declared list than as branches around the builder.
    pub fn jvm_arg_on(self, os: Os, arg: impl Into<String>) -> Self {
        if Os::current() == Some(os) {
            self.jvm_arg(arg)
        } else {
            self
        }
    }

    /// An argument passed to the application's `main`, after the jar.
    pub fn app_arg(mut self, arg: impl Into<String>) -> Self {
        self.app_args.push(arg.into());
        self
    }

    /// Where downloaded runtimes are kept. Defaults to the per-user cache directory.
    pub fn cache_dir(mut self, dir: impl Into<PathBuf>) -> Self {
        self.cache_dir = Some(dir.into());
        self
    }

    /// Refuse to download a runtime, failing instead when none is installed.
    ///
    /// For a build that must not touch the network — a packaged app that bundles its own JRE, or a
    /// corporate environment where the download would fail slowly and confusingly rather than fast.
    pub fn offline(mut self) -> Self {
        self.allow_download = false;
        self
    }

    /// Run it, reporting to `progress`, and hand back what happened.
    pub fn try_run_with(self, progress: &mut dyn Progress) -> Result<(), Error> {
        let jar = self.resolve_jar()?;

        progress.stage(Stage::FindingJava);
        let java = match java::find(self.java_version) {
            Some(found) => found,
            None if !self.allow_download => {
                return Err(Error::NoJava(format!(
                    "Java {}+ is required and downloading is disabled",
                    self.java_version
                )))
            }
            None => {
                let cache = self
                    .cache_dir
                    .clone()
                    .unwrap_or_else(|| provision::default_cache_dir(&self.name));
                provision::ensure(self.java_version, &cache, progress)?
            }
        };

        progress.stage(Stage::Launching);
        launch::run_jar(&java, &jar, &self.jvm_args, &self.app_args)
    }

    /// Run it with the default reporting for this build: the progress window if the `window` feature
    /// is on, otherwise the console.
    pub fn try_run(self) -> Result<(), Error> {
        #[cfg(feature = "window")]
        {
            let title = self.name.clone();
            window::run(self, &title)
        }
        #[cfg(not(feature = "window"))]
        {
            self.try_run_with(&mut Console::default())
        }
    }

    /// [`try_run`](Self::try_run), reporting any failure and collapsing it to an exit code.
    ///
    /// The shape `fn main` wants: `Bootstrap::new("App").java(21).run()`.
    pub fn run(self) -> ExitCode {
        match self.try_run() {
            Ok(()) => ExitCode::SUCCESS,
            Err(Error::AppFailed(Some(code))) => {
                // The app ran and decided its own exit code; passing it through is more useful than
                // flattening every non-zero exit to 1.
                ExitCode::from(u8::try_from(code).unwrap_or(1))
            }
            Err(e) => {
                eprintln!("{e}");
                #[cfg(feature = "window")]
                window::message_box("Could not start", &e.to_string());
                ExitCode::FAILURE
            }
        }
    }

    /// Materialise the configured jar as a path on disk.
    fn resolve_jar(&self) -> Result<PathBuf, Error> {
        match &self.jar {
            JarSource::Path(p) if p.is_file() => Ok(p.clone()),
            JarSource::Path(p) => Err(Error::NoJar(format!("{} does not exist", p.display()))),
            JarSource::Embedded(bytes) => {
                let dir = std::env::temp_dir().join(format!("{}-jvm-bootstrap", slug(&self.name)));
                std::fs::create_dir_all(&dir)?;
                let path = dir.join("app.jar");
                // Rewritten every run rather than cached: the bytes are already in memory, the write
                // is milliseconds, and a stale jar from a previous version is a genuinely baffling
                // bug to chase.
                std::fs::write(&path, bytes)?;
                Ok(path)
            }
            JarSource::None => self
                .jar_fallbacks
                .iter()
                .find(|p| p.is_file())
                .cloned()
                .ok_or_else(|| {
                    Error::NoJar(
                        "none embedded at build time and no fallback path exists".to_string(),
                    )
                }),
        }
    }
}

/// Lowercase `name` and reduce it to characters that are safe in a path.
pub(crate) fn slug(name: &str) -> String {
    name.chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() {
                c.to_ascii_lowercase()
            } else {
                '-'
            }
        })
        .collect()
}
