use sha2::{Digest, Sha256};
use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use thiserror::Error;
use uuid::Uuid;

const BUFFER_SIZE: usize = 64 * 1024;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExpectedFile {
    pub name: String,
    pub size: u64,
    pub sha256: [u8; 32],
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ReceivedFile {
    pub path: PathBuf,
    pub bytes_written: u64,
    pub sha256: [u8; 32],
}

#[derive(Debug, Error)]
pub enum ReceiveError {
    #[error("unsafe file name")]
    UnsafeFileName,
    #[error("declared size {declared} exceeds configured limit {limit}")]
    SizeLimitExceeded { declared: u64, limit: u64 },
    #[error("received {actual} bytes but expected {expected}")]
    SizeMismatch { expected: u64, actual: u64 },
    #[error("file checksum did not match the offer")]
    ChecksumMismatch,
    #[error("destination already exists")]
    DestinationExists,
    #[error(transparent)]
    Io(#[from] io::Error),
}

pub fn parse_sha256(value: &str) -> Result<[u8; 32], hex::FromHexError> {
    let mut output = [0_u8; 32];
    hex::decode_to_slice(value, &mut output)?;
    Ok(output)
}

pub fn is_safe_leaf_name(name: &str) -> bool {
    if name.is_empty() || name.len() > 255 || name == "." || name == ".." {
        return false;
    }
    if name.ends_with('.') || name.ends_with(' ') || name.chars().any(is_forbidden_character) {
        return false;
    }

    let stem = name
        .split('.')
        .next()
        .unwrap_or_default()
        .to_ascii_uppercase();
    !matches!(stem.as_str(), "CON" | "PRN" | "AUX" | "NUL")
        && !(stem.len() == 4
            && (stem.starts_with("COM") || stem.starts_with("LPT"))
            && matches!(stem.as_bytes()[3], b'1'..=b'9'))
}

fn is_forbidden_character(character: char) -> bool {
    character.is_control()
        || matches!(
            character,
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|'
        )
}

pub fn receive_verified<R: Read>(
    mut source: R,
    destination_directory: &Path,
    expected: &ExpectedFile,
    maximum_file_size: u64,
) -> Result<ReceivedFile, ReceiveError> {
    if !is_safe_leaf_name(&expected.name) {
        return Err(ReceiveError::UnsafeFileName);
    }
    if expected.size > maximum_file_size {
        return Err(ReceiveError::SizeLimitExceeded {
            declared: expected.size,
            limit: maximum_file_size,
        });
    }

    fs::create_dir_all(destination_directory)?;
    let final_path = destination_directory.join(&expected.name);
    if final_path.exists() {
        return Err(ReceiveError::DestinationExists);
    }

    let temporary_path =
        destination_directory.join(format!(".{}.{}.part", expected.name, Uuid::new_v4()));
    let result = receive_to_temporary(&mut source, &temporary_path, expected);
    match result {
        Ok((bytes_written, sha256)) => {
            fs::rename(&temporary_path, &final_path)?;
            Ok(ReceivedFile {
                path: final_path,
                bytes_written,
                sha256,
            })
        }
        Err(error) => {
            let _ = fs::remove_file(&temporary_path);
            Err(error)
        }
    }
}

fn receive_to_temporary<R: Read>(
    source: &mut R,
    temporary_path: &Path,
    expected: &ExpectedFile,
) -> Result<(u64, [u8; 32]), ReceiveError> {
    let mut output = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(temporary_path)?;
    let mut hasher = Sha256::new();
    let mut bytes_written = 0_u64;
    let mut buffer = [0_u8; BUFFER_SIZE];

    loop {
        let read = source.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        bytes_written =
            bytes_written
                .checked_add(read as u64)
                .ok_or(ReceiveError::SizeMismatch {
                    expected: expected.size,
                    actual: u64::MAX,
                })?;
        if bytes_written > expected.size {
            return Err(ReceiveError::SizeMismatch {
                expected: expected.size,
                actual: bytes_written,
            });
        }
        output.write_all(&buffer[..read])?;
        hasher.update(&buffer[..read]);
    }
    output.flush()?;
    output.sync_all()?;

    if bytes_written != expected.size {
        return Err(ReceiveError::SizeMismatch {
            expected: expected.size,
            actual: bytes_written,
        });
    }
    let digest: [u8; 32] = hasher.finalize().into();
    if digest != expected.sha256 {
        return Err(ReceiveError::ChecksumMismatch);
    }
    Ok((bytes_written, digest))
}

pub fn sha256_file(path: &Path) -> io::Result<[u8; 32]> {
    let mut file = File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; BUFFER_SIZE];
    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(hasher.finalize().into())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn streams_and_finalizes_verified_bytes() {
        let directory = tempfile::tempdir().unwrap();
        let bytes = b"hello world\n";
        let expected = ExpectedFile {
            name: "hello.txt".into(),
            size: bytes.len() as u64,
            sha256: Sha256::digest(bytes).into(),
        };

        let received =
            receive_verified(Cursor::new(bytes), directory.path(), &expected, 1024).unwrap();

        assert_eq!(fs::read(received.path).unwrap(), bytes);
        assert!(directory.path().read_dir().unwrap().all(|entry| {
            !entry
                .unwrap()
                .file_name()
                .to_string_lossy()
                .ends_with(".part")
        }));
    }

    #[test]
    fn removes_partial_file_after_checksum_failure() {
        let directory = tempfile::tempdir().unwrap();
        let expected = ExpectedFile {
            name: "file.bin".into(),
            size: 3,
            sha256: [0; 32],
        };

        let result = receive_verified(Cursor::new(b"abc"), directory.path(), &expected, 1024);

        assert!(matches!(result, Err(ReceiveError::ChecksumMismatch)));
        assert_eq!(directory.path().read_dir().unwrap().count(), 0);
    }

    #[test]
    fn rejects_unsafe_names_and_existing_destinations() {
        for name in ["../secret", "folder/file", "folder\\file", "CON", "name. "] {
            assert!(!is_safe_leaf_name(name), "{name}");
        }
        assert!(is_safe_leaf_name("résumé 2026.pdf"));
    }
}
