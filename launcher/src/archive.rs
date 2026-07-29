//! Unpacking a downloaded runtime.
//!
//! Adoptium ships `.zip` for Windows and `.tar.gz` everywhere else, so both are handled and the
//! format is chosen by extension rather than by platform — a caller pointing this at their own
//! mirror should not have to care which convention it followed.

use std::fs;
use std::io;
use std::path::{Component, Path, PathBuf};

/// The archive extension this platform's runtimes come in.
pub fn suffix() -> &'static str {
    if cfg!(target_os = "windows") {
        ".zip"
    } else {
        ".tar.gz"
    }
}

/// Extract `archive` into `dest`, creating it.
pub fn unpack(archive: &Path, dest: &Path) -> io::Result<()> {
    fs::create_dir_all(dest)?;
    let name = archive.to_string_lossy();

    if name.ends_with(".zip") {
        unpack_zip(archive, dest)
    } else {
        unpack_tar_gz(archive, dest)
    }
}

fn unpack_zip(archive: &Path, dest: &Path) -> io::Result<()> {
    let file = fs::File::open(archive)?;
    let mut zip = zip::ZipArchive::new(file)
        .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e.to_string()))?;

    for i in 0..zip.len() {
        let mut entry = zip
            .by_index(i)
            .map_err(|e| io::Error::new(io::ErrorKind::InvalidData, e.to_string()))?;

        // `enclosed_name` is the library's own traversal check; entries that escape the destination
        // are skipped rather than trusted. See the note on safe_join below for why this matters.
        let Some(rel) = entry.enclosed_name() else {
            continue;
        };
        let Some(out) = safe_join(dest, &rel) else {
            continue;
        };

        if entry.is_dir() {
            fs::create_dir_all(&out)?;
            continue;
        }
        if let Some(parent) = out.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut writer = fs::File::create(&out)?;
        io::copy(&mut entry, &mut writer)?;

        // Zip carries unix permissions in the external attributes, and the JRE's `java` is useless
        // without its executable bit.
        #[cfg(unix)]
        if let Some(mode) = entry.unix_mode() {
            use std::os::unix::fs::PermissionsExt;
            fs::set_permissions(&out, fs::Permissions::from_mode(mode))?;
        }
    }

    Ok(())
}

fn unpack_tar_gz(archive: &Path, dest: &Path) -> io::Result<()> {
    let file = fs::File::open(archive)?;
    let decoder = flate2::read::GzDecoder::new(file);
    let mut tar = tar::Archive::new(decoder);
    // Preserves the executable bit, which the runtime needs.
    tar.set_preserve_permissions(true);

    for entry in tar.entries()? {
        let mut entry = entry?;
        let rel = entry.path()?.into_owned();
        let Some(out) = safe_join(dest, &rel) else {
            continue;
        };
        if let Some(parent) = out.parent() {
            fs::create_dir_all(parent)?;
        }
        entry.unpack(&out)?;
    }

    Ok(())
}

/// Join `rel` onto `base`, or `None` if it would escape.
///
/// An archive entry is untrusted input even from a source you trust — this is the `../../../etc`
/// path-traversal shape, and the cost of getting it wrong is writing outside the cache directory. The
/// tar crate's `unpack` does its own checking and the zip crate offers `enclosed_name`, but both are
/// applied here as well: a downloaded archive is the one input this crate cannot inspect beforehand,
/// so it is worth being sure rather than being sure the library is sure.
fn safe_join(base: &Path, rel: &Path) -> Option<PathBuf> {
    let mut out = base.to_path_buf();
    for part in rel.components() {
        match part {
            Component::Normal(p) => out.push(p),
            // Anything that is not a plain name — `..`, a root, a Windows prefix — makes the whole
            // entry suspect. Skipping the component would silently flatten `a/../b` into `a/b`.
            _ => return None,
        }
    }
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::safe_join;
    use std::path::{Path, PathBuf};

    #[test]
    fn joins_a_plain_relative_path() {
        assert_eq!(
            safe_join(Path::new("/cache"), Path::new("jdk-21/bin/java")),
            Some(PathBuf::from("/cache/jdk-21/bin/java"))
        );
    }

    #[test]
    fn refuses_to_climb_out() {
        assert_eq!(safe_join(Path::new("/cache"), Path::new("../evil")), None);
        assert_eq!(
            safe_join(Path::new("/cache"), Path::new("jdk/../../evil")),
            None
        );
    }

    #[test]
    fn refuses_an_absolute_entry() {
        assert_eq!(safe_join(Path::new("/cache"), Path::new("/etc/passwd")), None);
    }
}
