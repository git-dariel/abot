use abot_security::{
    decrypt_frame, encrypt_frame, generate_session_token, AEAD_NONCE_BYTES, SESSION_KEY_BYTES,
};
use abot_transfer_core::{
    is_safe_leaf_name, parse_sha256, receive_verified, sha256_file, ExpectedFile,
};
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use base64::Engine;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::io::{self, Read, Write};
use std::net::{IpAddr, SocketAddr, TcpListener};
use std::path::PathBuf;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;
use tauri::{AppHandle, State};
use tauri_plugin_dialog::DialogExt;
use uuid::Uuid;

const MAGIC: &[u8; 4] = b"ABR1";
// These v1 wire domains intentionally retain their pre-ABOT bytes for compatibility.
const OFFER_AAD: &[u8] = b"AIRBRIDGE-OFFER-V1";
const DECISION_AAD: &[u8] = b"AIRBRIDGE-DECISION-V1";
const COMPLETE_AAD: &[u8] = b"AIRBRIDGE-COMPLETE-V1";
const MAX_CONTROL_FRAME: usize = 16 * 1024;
const MAX_DATA_FRAME: usize = 64 * 1024 + 16;
const MAX_FILE_SIZE: u64 = 20 * 1024 * 1024 * 1024;

#[derive(Default)]
struct SelectedFiles(Mutex<HashMap<Uuid, PathBuf>>);

#[derive(Default)]
struct ReceiverState(Arc<SharedReceiver>);

#[derive(Default)]
struct SharedReceiver {
    inner: Mutex<ReceiverInner>,
    changed: Condvar,
}

#[derive(Default)]
struct ReceiverInner {
    snapshot: ReceiverSnapshot,
    decision: Option<bool>,
}

#[derive(Debug, Clone, Default, Serialize)]
#[serde(rename_all = "camelCase")]
struct ReceiverSnapshot {
    state: String,
    endpoint: Option<String>,
    pairing_uri: Option<String>,
    offer: Option<IncomingOffer>,
    saved_path: Option<String>,
    error: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct IncomingOffer {
    version: u16,
    transfer_id: Uuid,
    file_id: Uuid,
    name: String,
    size: u64,
    sha256: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct TransferDecision {
    accepted: bool,
    reason: Option<String>,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct SelectedFileMetadata {
    id: Uuid,
    name: String,
    size: u64,
    sha256: String,
}

#[tauri::command]
fn protocol_status() -> &'static str {
    "encrypted-single-file-receiver"
}

#[tauri::command]
fn select_files(
    app: AppHandle,
    selected_files: State<'_, SelectedFiles>,
) -> Result<Vec<SelectedFileMetadata>, String> {
    let Some(paths) = app.dialog().file().blocking_pick_files() else {
        return Ok(Vec::new());
    };
    let paths = paths
        .into_iter()
        .map(|file_path| {
            file_path
                .into_path()
                .map_err(|error| format!("Selected path is unavailable: {error}"))
        })
        .collect::<Result<Vec<_>, _>>()?;
    let (prepared, updated_selection) = prepare_selected_paths(paths)?;

    *selected_files
        .0
        .lock()
        .map_err(|_| "Selected-file state is unavailable".to_owned())? = updated_selection;
    Ok(prepared)
}

#[tauri::command]
fn start_receiver(
    app: AppHandle,
    receiver: State<'_, ReceiverState>,
) -> Result<ReceiverSnapshot, String> {
    {
        let inner = receiver
            .0
            .inner
            .lock()
            .map_err(|_| "Receiver state is unavailable".to_owned())?;
        if matches!(
            inner.snapshot.state.as_str(),
            "waiting" | "offerPending" | "receiving"
        ) {
            return Err("The receiver is already active".to_owned());
        }
    }

    let Some(destination) = app.dialog().file().blocking_pick_folder() else {
        return Err("Choose a destination folder to start receiving".to_owned());
    };
    let destination = destination
        .into_path()
        .map_err(|error| format!("Destination folder is unavailable: {error}"))?;
    let listener = TcpListener::bind(("0.0.0.0", 0))
        .map_err(|_| "Could not start the local receiver".to_owned())?;
    let port = listener
        .local_addr()
        .map_err(|_| "Could not determine the receiver port".to_owned())?
        .port();
    let local_ip = private_local_ipv4()
        .ok_or_else(|| "No private IPv4 network address is available".to_owned())?;
    let endpoint = format!("{local_ip}:{port}");
    let secret = generate_session_token();
    let pairing_uri = format!(
        "abot://pair?endpoint={endpoint}&secret={}",
        URL_SAFE_NO_PAD.encode(secret)
    );
    let snapshot = ReceiverSnapshot {
        state: "waiting".to_owned(),
        endpoint: Some(endpoint),
        pairing_uri: Some(pairing_uri),
        offer: None,
        saved_path: None,
        error: None,
    };
    set_receiver_snapshot(&receiver.0, snapshot.clone(), None)?;

    let shared = Arc::clone(&receiver.0);
    std::thread::spawn(move || {
        if let Err(error) = receive_once(listener, destination, secret, &shared) {
            let prior = snapshot_without_secret(&shared);
            let _ = set_receiver_snapshot(
                &shared,
                ReceiverSnapshot {
                    state: "failed".to_owned(),
                    error: Some(error),
                    ..prior
                },
                None,
            );
        }
    });
    Ok(snapshot)
}

#[tauri::command]
fn receiver_status(receiver: State<'_, ReceiverState>) -> Result<ReceiverSnapshot, String> {
    receiver
        .0
        .inner
        .lock()
        .map(|inner| inner.snapshot.clone())
        .map_err(|_| "Receiver state is unavailable".to_owned())
}

#[tauri::command]
fn decide_transfer(
    accepted: bool,
    receiver: State<'_, ReceiverState>,
) -> Result<ReceiverSnapshot, String> {
    let mut inner = receiver
        .0
        .inner
        .lock()
        .map_err(|_| "Receiver state is unavailable".to_owned())?;
    if inner.snapshot.state != "offerPending" {
        return Err("There is no transfer offer awaiting a decision".to_owned());
    }
    inner.decision = Some(accepted);
    inner.snapshot.state = if accepted { "receiving" } else { "rejected" }.to_owned();
    let snapshot = inner.snapshot.clone();
    receiver.0.changed.notify_all();
    Ok(snapshot)
}

fn private_local_ipv4() -> Option<std::net::Ipv4Addr> {
    match local_ip_address::local_ip().ok()? {
        IpAddr::V4(address) if address.is_private() && !address.is_loopback() => Some(address),
        _ => None,
    }
}

fn receive_once(
    listener: TcpListener,
    destination: PathBuf,
    secret: [u8; SESSION_KEY_BYTES],
    shared: &Arc<SharedReceiver>,
) -> Result<(), String> {
    let (mut stream, peer) = listener
        .accept()
        .map_err(|_| "The local receiver stopped unexpectedly".to_owned())?;
    if !is_local_peer(peer) {
        return Err("A connection from outside the local network was rejected".to_owned());
    }
    stream
        .set_read_timeout(Some(Duration::from_secs(30)))
        .map_err(|_| "Could not configure the incoming connection".to_owned())?;
    stream
        .set_write_timeout(Some(Duration::from_secs(30)))
        .map_err(|_| "Could not configure the incoming connection".to_owned())?;

    let mut magic = [0_u8; 4];
    stream
        .read_exact(&mut magic)
        .map_err(|_| "The sender did not provide a valid offer".to_owned())?;
    if &magic != MAGIC {
        return Err("The sender used an unsupported protocol".to_owned());
    }
    let offer_bytes = read_encrypted_frame(&mut stream, &secret, OFFER_AAD, MAX_CONTROL_FRAME)
        .map_err(|_| "The encrypted offer could not be authenticated".to_owned())?;
    let offer: IncomingOffer = serde_json::from_slice(&offer_bytes)
        .map_err(|_| "The sender provided malformed metadata".to_owned())?;
    validate_offer(&offer)?;

    {
        let mut inner = shared
            .inner
            .lock()
            .map_err(|_| "Receiver state is unavailable".to_owned())?;
        inner.snapshot.state = "offerPending".to_owned();
        inner.snapshot.offer = Some(offer.clone());
        inner.snapshot.pairing_uri = None;
        inner.decision = None;
    }
    shared.changed.notify_all();

    let mut inner = shared
        .inner
        .lock()
        .map_err(|_| "Receiver state is unavailable".to_owned())?;
    let timeout = Duration::from_secs(10 * 60);
    while inner.decision.is_none() {
        let (next, result) = shared
            .changed
            .wait_timeout(inner, timeout)
            .map_err(|_| "Receiver state is unavailable".to_owned())?;
        inner = next;
        if result.timed_out() {
            inner.snapshot.state = "failed".to_owned();
            inner.snapshot.error = Some("The transfer offer expired before a decision".to_owned());
            return Ok(());
        }
    }
    let accepted = inner.decision.take().unwrap_or(false);
    drop(inner);

    let decision = TransferDecision {
        accepted,
        reason: (!accepted).then(|| "The receiver declined the transfer".to_owned()),
    };
    let decision_bytes = serde_json::to_vec(&decision)
        .map_err(|_| "Could not create the transfer decision".to_owned())?;
    write_encrypted_frame(&mut stream, &secret, DECISION_AAD, &decision_bytes)
        .map_err(|_| "Could not send the transfer decision".to_owned())?;
    if !accepted {
        return Ok(());
    }

    stream
        .set_read_timeout(Some(Duration::from_secs(120)))
        .map_err(|_| "Could not configure the transfer timeout".to_owned())?;
    let expected = ExpectedFile {
        name: offer.name.clone(),
        size: offer.size,
        sha256: parse_sha256(&offer.sha256)
            .map_err(|_| "The offered SHA-256 value is invalid".to_owned())?,
    };
    let mut reader = EncryptedChunkReader::new(stream, secret, offer.transfer_id);
    let received = receive_verified(&mut reader, &destination, &expected, MAX_FILE_SIZE)
        .map_err(|error| format!("The incoming file was not saved: {error}"))?;
    let mut stream = reader.into_inner();
    let prior = snapshot_without_secret(shared);
    set_receiver_snapshot(
        shared,
        ReceiverSnapshot {
            state: "completed".to_owned(),
            saved_path: Some(received.path.display().to_string()),
            offer: Some(offer),
            error: None,
            ..prior
        },
        None,
    )?;
    if write_encrypted_frame(&mut stream, &secret, COMPLETE_AAD, br#"{"verified":true}"#).is_err() {
        if let Ok(mut inner) = shared.inner.lock() {
            inner.snapshot.error =
                Some("The file was saved, but the sender did not receive confirmation".to_owned());
        }
    }
    Ok(())
}

fn is_local_peer(peer: SocketAddr) -> bool {
    match peer.ip() {
        IpAddr::V4(address) => address.is_private() || address.is_loopback(),
        IpAddr::V6(address) => address.is_unique_local() || address.is_loopback(),
    }
}

fn validate_offer(offer: &IncomingOffer) -> Result<(), String> {
    if offer.version != 1 {
        return Err("The sender uses an unsupported protocol version".to_owned());
    }
    if !is_safe_leaf_name(&offer.name) {
        return Err("The sender provided an unsafe file name".to_owned());
    }
    if offer.size > MAX_FILE_SIZE {
        return Err("The offered file exceeds the 20 GiB limit".to_owned());
    }
    parse_sha256(&offer.sha256).map_err(|_| "The offered SHA-256 value is invalid".to_owned())?;
    Ok(())
}

fn set_receiver_snapshot(
    shared: &Arc<SharedReceiver>,
    snapshot: ReceiverSnapshot,
    decision: Option<bool>,
) -> Result<(), String> {
    let mut inner = shared
        .inner
        .lock()
        .map_err(|_| "Receiver state is unavailable".to_owned())?;
    inner.snapshot = snapshot;
    inner.decision = decision;
    shared.changed.notify_all();
    Ok(())
}

fn snapshot_without_secret(shared: &Arc<SharedReceiver>) -> ReceiverSnapshot {
    shared
        .inner
        .lock()
        .map(|inner| ReceiverSnapshot {
            state: inner.snapshot.state.clone(),
            endpoint: inner.snapshot.endpoint.clone(),
            pairing_uri: None,
            offer: inner.snapshot.offer.clone(),
            saved_path: inner.snapshot.saved_path.clone(),
            error: inner.snapshot.error.clone(),
        })
        .unwrap_or_default()
}

fn read_encrypted_frame<R: Read>(
    source: &mut R,
    key: &[u8; SESSION_KEY_BYTES],
    associated_data: &[u8],
    maximum_length: usize,
) -> io::Result<Vec<u8>> {
    let length = read_u32(source)? as usize;
    if length < 16 || length > maximum_length {
        return Err(io::Error::new(
            io::ErrorKind::InvalidData,
            "invalid frame length",
        ));
    }
    let mut nonce = [0_u8; AEAD_NONCE_BYTES];
    source.read_exact(&mut nonce)?;
    let mut ciphertext = vec![0_u8; length];
    source.read_exact(&mut ciphertext)?;
    decrypt_frame(key, &nonce, &ciphertext, associated_data)
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "authentication failed"))
}

fn write_encrypted_frame<W: Write>(
    target: &mut W,
    key: &[u8; SESSION_KEY_BYTES],
    associated_data: &[u8],
    plaintext: &[u8],
) -> io::Result<()> {
    let frame = encrypt_frame(key, plaintext, associated_data);
    let length = u32::try_from(frame.ciphertext.len())
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidInput, "frame too large"))?;
    target.write_all(&length.to_be_bytes())?;
    target.write_all(&frame.nonce)?;
    target.write_all(&frame.ciphertext)?;
    target.flush()
}

fn read_u32<R: Read>(source: &mut R) -> io::Result<u32> {
    let mut bytes = [0_u8; 4];
    source.read_exact(&mut bytes)?;
    Ok(u32::from_be_bytes(bytes))
}

fn data_aad(transfer_id: Uuid, sequence: u64) -> Vec<u8> {
    let mut aad = Vec::with_capacity(42);
    aad.extend_from_slice(b"AIRBRIDGE-DATA-V1\0");
    aad.extend_from_slice(transfer_id.as_bytes());
    aad.extend_from_slice(&sequence.to_be_bytes());
    aad
}

struct EncryptedChunkReader<R> {
    source: R,
    key: [u8; SESSION_KEY_BYTES],
    transfer_id: Uuid,
    sequence: u64,
    plaintext: io::Cursor<Vec<u8>>,
    finished: bool,
}

impl<R: Read> EncryptedChunkReader<R> {
    fn new(source: R, key: [u8; SESSION_KEY_BYTES], transfer_id: Uuid) -> Self {
        Self {
            source,
            key,
            transfer_id,
            sequence: 0,
            plaintext: io::Cursor::new(Vec::new()),
            finished: false,
        }
    }

    fn into_inner(self) -> R {
        self.source
    }
}

impl<R: Read> Read for EncryptedChunkReader<R> {
    fn read(&mut self, output: &mut [u8]) -> io::Result<usize> {
        let buffered = self.plaintext.read(output)?;
        if buffered > 0 || self.finished || output.is_empty() {
            return Ok(buffered);
        }

        let length = read_u32(&mut self.source)? as usize;
        if length == 0 {
            self.finished = true;
            return Ok(0);
        }
        if !(16..=MAX_DATA_FRAME).contains(&length) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidData,
                "invalid data frame length",
            ));
        }
        let mut nonce = [0_u8; AEAD_NONCE_BYTES];
        self.source.read_exact(&mut nonce)?;
        let mut ciphertext = vec![0_u8; length];
        self.source.read_exact(&mut ciphertext)?;
        let plaintext = decrypt_frame(
            &self.key,
            &nonce,
            &ciphertext,
            &data_aad(self.transfer_id, self.sequence),
        )
        .map_err(|_| io::Error::new(io::ErrorKind::InvalidData, "data authentication failed"))?;
        self.sequence = self
            .sequence
            .checked_add(1)
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "sequence overflow"))?;
        self.plaintext = io::Cursor::new(plaintext);
        self.plaintext.read(output)
    }
}

fn prepare_selected_paths(
    paths: Vec<PathBuf>,
) -> Result<(Vec<SelectedFileMetadata>, HashMap<Uuid, PathBuf>), String> {
    let mut prepared = Vec::with_capacity(paths.len());
    let mut updated_selection = HashMap::with_capacity(paths.len());

    for path in paths {
        let metadata = path
            .metadata()
            .map_err(|_| "A selected file is no longer accessible".to_owned())?;
        if !metadata.is_file() {
            return Err("Only files can be selected".to_owned());
        }
        let name = path
            .file_name()
            .and_then(|name| name.to_str())
            .filter(|name| is_safe_leaf_name(name))
            .ok_or_else(|| "A selected file name is not portable or safe".to_owned())?
            .to_owned();
        let id = Uuid::new_v4();
        let digest =
            sha256_file(&path).map_err(|_| "A selected file could not be read".to_owned())?;
        prepared.push(SelectedFileMetadata {
            id,
            name,
            size: metadata.len(),
            sha256: hex::encode(digest),
        });
        updated_selection.insert(id, path);
    }
    Ok((prepared, updated_selection))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .manage(SelectedFiles::default())
        .manage(ReceiverState::default())
        .invoke_handler(tauri::generate_handler![
            protocol_status,
            select_files,
            start_receiver,
            receiver_status,
            decide_transfer
        ])
        .run(tauri::generate_context!())
        .expect("failed to run ABOT");
}

#[cfg(test)]
mod tests {
    use super::*;
    use sha2::{Digest, Sha256};
    use std::fs;
    use std::net::TcpStream;
    use std::thread;
    use std::time::Instant;

    #[test]
    fn prepares_real_file_metadata_without_exposing_paths() {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("hello.txt");
        fs::write(&path, b"hello world\n").unwrap();

        let (metadata, selected) = prepare_selected_paths(vec![path.clone()]).unwrap();

        assert_eq!(metadata.len(), 1);
        assert_eq!(metadata[0].name, "hello.txt");
        assert_eq!(metadata[0].size, 12);
        assert_eq!(
            metadata[0].sha256,
            "a948904f2f0f479b8f8197694b30184b0d2ed1c1cd2a1ec0fb85d299a192a447"
        );
        assert_eq!(selected.get(&metadata[0].id), Some(&path));
    }

    #[test]
    fn decrypts_streamed_chunks_before_verified_finalization() {
        let directory = tempfile::tempdir().unwrap();
        let key = [9_u8; SESSION_KEY_BYTES];
        let transfer_id = Uuid::new_v4();
        let bytes = vec![42_u8; 130_000];
        let mut wire = Vec::new();
        for (sequence, chunk) in bytes.chunks(64 * 1024).enumerate() {
            write_encrypted_frame(
                &mut wire,
                &key,
                &data_aad(transfer_id, sequence as u64),
                chunk,
            )
            .unwrap();
        }
        wire.extend_from_slice(&0_u32.to_be_bytes());

        let expected = ExpectedFile {
            name: "payload.bin".to_owned(),
            size: bytes.len() as u64,
            sha256: Sha256::digest(&bytes).into(),
        };
        let reader = EncryptedChunkReader::new(io::Cursor::new(wire), key, transfer_id);
        let received =
            receive_verified(reader, directory.path(), &expected, MAX_FILE_SIZE).unwrap();

        assert_eq!(fs::read(received.path).unwrap(), bytes);
    }

    #[test]
    fn receiver_waits_for_consent_then_saves_verified_encrypted_bytes() {
        let directory = tempfile::tempdir().unwrap();
        let destination = directory.path().to_path_buf();
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let endpoint = listener.local_addr().unwrap();
        let shared = Arc::new(SharedReceiver::default());
        let receiver_shared = Arc::clone(&shared);
        let key = [11_u8; SESSION_KEY_BYTES];
        let transfer_id = Uuid::new_v4();
        let bytes = b"encrypted local transfer".to_vec();
        let offer = IncomingOffer {
            version: 1,
            transfer_id,
            file_id: Uuid::new_v4(),
            name: "received.txt".to_owned(),
            size: bytes.len() as u64,
            sha256: hex::encode(Sha256::digest(&bytes)),
        };

        let receiver = thread::spawn(move || {
            receive_once(listener, destination, key, &receiver_shared).unwrap()
        });
        let mut client = TcpStream::connect(endpoint).unwrap();
        client.write_all(MAGIC).unwrap();
        write_encrypted_frame(
            &mut client,
            &key,
            OFFER_AAD,
            &serde_json::to_vec(&offer).unwrap(),
        )
        .unwrap();

        let deadline = Instant::now() + Duration::from_secs(2);
        loop {
            if shared.inner.lock().unwrap().snapshot.state == "offerPending" {
                break;
            }
            assert!(
                Instant::now() < deadline,
                "offer was not presented for consent"
            );
            thread::sleep(Duration::from_millis(10));
        }
        assert_eq!(directory.path().read_dir().unwrap().count(), 0);

        {
            let mut inner = shared.inner.lock().unwrap();
            inner.decision = Some(true);
            inner.snapshot.state = "receiving".to_owned();
            shared.changed.notify_all();
        }
        let decision =
            read_encrypted_frame(&mut client, &key, DECISION_AAD, MAX_CONTROL_FRAME).unwrap();
        assert!(
            serde_json::from_slice::<TransferDecision>(&decision)
                .unwrap()
                .accepted
        );

        write_encrypted_frame(&mut client, &key, &data_aad(transfer_id, 0), &bytes).unwrap();
        client.write_all(&0_u32.to_be_bytes()).unwrap();
        client.flush().unwrap();
        let completion =
            read_encrypted_frame(&mut client, &key, COMPLETE_AAD, MAX_CONTROL_FRAME).unwrap();
        assert_eq!(completion, br#"{"verified":true}"#);
        receiver.join().unwrap();

        assert_eq!(
            fs::read(directory.path().join("received.txt")).unwrap(),
            bytes
        );
        assert_eq!(shared.inner.lock().unwrap().snapshot.state, "completed");
    }

    #[test]
    fn rejected_offer_never_creates_a_file() {
        let directory = tempfile::tempdir().unwrap();
        let destination = directory.path().to_path_buf();
        let listener = TcpListener::bind(("127.0.0.1", 0)).unwrap();
        let endpoint = listener.local_addr().unwrap();
        let shared = Arc::new(SharedReceiver::default());
        let receiver_shared = Arc::clone(&shared);
        let key = [17_u8; SESSION_KEY_BYTES];
        let offer = IncomingOffer {
            version: 1,
            transfer_id: Uuid::new_v4(),
            file_id: Uuid::new_v4(),
            name: "rejected.txt".to_owned(),
            size: 4,
            sha256: hex::encode(Sha256::digest(b"data")),
        };

        let receiver = thread::spawn(move || {
            receive_once(listener, destination, key, &receiver_shared).unwrap()
        });
        let mut client = TcpStream::connect(endpoint).unwrap();
        client.write_all(MAGIC).unwrap();
        write_encrypted_frame(
            &mut client,
            &key,
            OFFER_AAD,
            &serde_json::to_vec(&offer).unwrap(),
        )
        .unwrap();

        let deadline = Instant::now() + Duration::from_secs(2);
        loop {
            if shared.inner.lock().unwrap().snapshot.state == "offerPending" {
                break;
            }
            assert!(
                Instant::now() < deadline,
                "offer was not presented for consent"
            );
            thread::sleep(Duration::from_millis(10));
        }
        {
            let mut inner = shared.inner.lock().unwrap();
            inner.decision = Some(false);
            inner.snapshot.state = "rejected".to_owned();
            shared.changed.notify_all();
        }
        let decision =
            read_encrypted_frame(&mut client, &key, DECISION_AAD, MAX_CONTROL_FRAME).unwrap();
        assert!(
            !serde_json::from_slice::<TransferDecision>(&decision)
                .unwrap()
                .accepted
        );
        receiver.join().unwrap();

        assert_eq!(directory.path().read_dir().unwrap().count(), 0);
        assert_eq!(shared.inner.lock().unwrap().snapshot.state, "rejected");
    }
}
