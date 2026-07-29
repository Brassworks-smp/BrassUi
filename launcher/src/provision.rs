//! Fetching a JRE when the machine has none.
//!
//! Runtimes come from [Adoptium](https://adoptium.net), whose API answers "a JRE for this version,
//! OS and architecture" with a direct link and a checksum. Their builds are the reference OpenJDK
//! ones, redistributable, and available for every platform this crate targets.
//!
//! A fetched runtime lands in a per-user cache directory and is reused, so the download is a
//! first-run cost rather than a per-launch one.

use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use crate::archive;
use crate::java::{self, JavaInstall};
use crate::progress::{Progress, Stage};
use crate::Error;

/// Where runtimes for `app_name` are cached, by platform convention.
pub fn default_cache_dir(app_name: &str) -> PathBuf {
    dirs::cache_dir()
        .unwrap_or_else(std::env::temp_dir)
        .join(crate::slug(app_name))
        .join("runtimes")
}

/// A runtime of at least `version` in `cache_dir`, downloading one if it is not already there.
pub fn ensure(
    version: u32,
    cache_dir: &Path,
    progress: &mut dyn Progress,
) -> Result<JavaInstall, Error> {
    let dir = cache_dir.join(format!("jdk-{version}"));

    // Already fetched on a previous run? Probe rather than trust the directory's existence — a
    // download interrupted halfway leaves one behind that looks right and does not run.
    if let Some(found) = probe_tree(&dir, version) {
        return Ok(found);
    }

    progress.stage(Stage::Resolving);
    let release = resolve(version)?;

    progress.stage(Stage::Downloading);
    let archive_path = cache_dir.join(format!("jdk-{version}{}", archive::suffix()));
    fs::create_dir_all(cache_dir)?;
    download(&release.link, &archive_path, progress)?;

    progress.stage(Stage::Unpacking);
    // Unpack beside the target and rename into place, so an interrupted unpack cannot leave a
    // half-populated directory that the check above would accept next run.
    let staging = cache_dir.join(format!("jdk-{version}.incomplete"));
    let _ = fs::remove_dir_all(&staging);
    archive::unpack(&archive_path, &staging)
        .map_err(|e| Error::Unpack(format!("{}: {e}", archive_path.display())))?;
    let _ = fs::remove_dir_all(&dir);
    fs::rename(&staging, &dir)?;
    let _ = fs::remove_file(&archive_path);

    probe_tree(&dir, version).ok_or_else(|| {
        Error::NoJava(format!(
            "downloaded a runtime to {} but found no working java in it",
            dir.display()
        ))
    })
}

/// Look for a working `java` inside an unpacked runtime.
///
/// Archives contain a single top-level directory whose name carries the exact build, so the binary is
/// one level deeper than the extraction root — and on macOS it is inside a `.jdk` bundle below that.
fn probe_tree(root: &Path, minimum: u32) -> Option<JavaInstall> {
    if !root.is_dir() {
        return None;
    }

    let mut roots = vec![root.to_path_buf()];
    roots.extend(
        fs::read_dir(root)
            .into_iter()
            .flatten()
            .flatten()
            .map(|e| e.path())
            .filter(|p| p.is_dir()),
    );

    roots
        .into_iter()
        .flat_map(|r| {
            [
                r.join("bin").join(java::exe()),
                r.join("Contents").join("Home").join("bin").join(java::exe()),
            ]
        })
        .filter(|p| p.is_file())
        .filter_map(|p| java::probe(&p))
        .find(|j| j.version >= minimum)
}

/// One Adoptium binary, pared down to the fields worth reading.
#[derive(Deserialize)]
struct Package {
    link: String,
}

#[derive(Deserialize)]
struct Binary {
    package: Package,
}

#[derive(Deserialize)]
struct Release {
    binaries: Vec<Binary>,
}

/// A download link for this platform.
fn resolve(version: u32) -> Result<Package, Error> {
    let (os, arch) = platform().ok_or_else(|| {
        Error::NoJava(format!(
            "no prebuilt Java for {}/{}",
            std::env::consts::OS,
            std::env::consts::ARCH
        ))
    })?;

    // `image_type=jre` keeps the download to a runtime rather than a full JDK — roughly half the
    // size, and a launcher never needs a compiler.
    let url = format!(
        "https://api.adoptium.net/v3/assets/latest/{version}/hotspot\
         ?os={os}&architecture={arch}&image_type=jre&vendor=eclipse"
    );

    let body = ureq::get(&url)
        .call()
        .map_err(|e| Error::Download(e.to_string()))?
        .into_string()
        .map_err(|e| Error::Download(e.to_string()))?;

    let releases: Vec<Release> =
        serde_json::from_str(&body).map_err(|e| Error::Download(format!("bad response: {e}")))?;

    releases
        .into_iter()
        .flat_map(|r| r.binaries)
        .map(|b| b.package)
        .next()
        .ok_or_else(|| Error::Download(format!("Adoptium has no Java {version} for {os}/{arch}")))
}

/// This machine in the names Adoptium uses, or `None` if it publishes nothing for it.
fn platform() -> Option<(&'static str, &'static str)> {
    let os = match std::env::consts::OS {
        "macos" => "mac",
        "windows" => "windows",
        "linux" => "linux",
        _ => return None,
    };
    let arch = match std::env::consts::ARCH {
        "x86_64" => "x64",
        "aarch64" => "aarch64",
        "x86" => "x86",
        _ => return None,
    };
    Some((os, arch))
}

/// Stream `url` to `dest`, reporting progress as it goes.
fn download(url: &str, dest: &Path, progress: &mut dyn Progress) -> Result<(), Error> {
    let response = ureq::get(url)
        .call()
        .map_err(|e| Error::Download(e.to_string()))?;

    let total = response
        .header("Content-Length")
        .and_then(|v| v.parse::<u64>().ok());

    let mut reader = response.into_reader();
    let mut file = fs::File::create(dest)?;
    // 64 KiB: large enough that the progress callback is not the bottleneck, small enough that the
    // bar still moves smoothly on a slow connection.
    let mut buf = vec![0u8; 64 * 1024];
    let mut done = 0u64;

    loop {
        let n = reader.read(&mut buf).map_err(|e| Error::Download(e.to_string()))?;
        if n == 0 {
            break;
        }
        std::io::Write::write_all(&mut file, &buf[..n])?;
        done += n as u64;
        progress.bytes(done, total);
    }

    Ok(())
}
