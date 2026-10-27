import { invoke, isTauri } from "@tauri-apps/api/core";
import { useEffect, useId, useMemo, useRef, useState } from "react";
import type { ChangeEvent } from "react";

type SelectedFile = {
  id?: string;
  name: string;
  size: number;
  type?: string;
  sha256?: string;
};

type IncomingOffer = {
  transferId: string;
  fileId: string;
  name: string;
  size: number;
  sha256: string;
};

type ReceiverSnapshot = {
  state: string;
  endpoint?: string;
  pairingUri?: string;
  offer?: IncomingOffer;
  savedPath?: string;
  error?: string;
};

const formatBytes = (bytes: number) => {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const unit = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** unit).toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
};

export function App() {
  const inputId = useId();
  const fileInput = useRef<HTMLInputElement>(null);
  const [files, setFiles] = useState<SelectedFile[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isSelecting, setIsSelecting] = useState(false);
  const [receiver, setReceiver] = useState<ReceiverSnapshot>({ state: "stopped" });
  const [receiverError, setReceiverError] = useState<string | null>(null);
  const [isStartingReceiver, setIsStartingReceiver] = useState(false);
  const nativeShell = isTauri();
  const totalBytes = useMemo(() => files.reduce((total, file) => total + file.size, 0), [files]);

  const selectFiles = (event: ChangeEvent<HTMLInputElement>) => {
    setError(null);
    setFiles(Array.from(event.target.files ?? [], ({ name, size, type }) => ({ name, size, type })));
  };

  const chooseFiles = async () => {
    if (!nativeShell) {
      fileInput.current?.click();
      return;
    }
    setError(null);
    setIsSelecting(true);
    try {
      const selected = await invoke<SelectedFile[]>("select_files");
      if (selected.length > 0) setFiles(selected);
    } catch (reason) {
      setError(typeof reason === "string" ? reason : "Files could not be selected");
    } finally {
      setIsSelecting(false);
    }
  };

  useEffect(() => {
    if (!nativeShell || receiver.state === "stopped") return;
    const refresh = () => {
      void invoke<ReceiverSnapshot>("receiver_status")
        .then(setReceiver)
        .catch(() => setReceiverError("Receiver status is unavailable"));
    };
    refresh();
    const timer = window.setInterval(refresh, 750);
    return () => window.clearInterval(timer);
  }, [nativeShell, receiver.state === "stopped"]);

  const startReceiver = async () => {
    setReceiverError(null);
    setIsStartingReceiver(true);
    try {
      setReceiver(await invoke<ReceiverSnapshot>("start_receiver"));
    } catch (reason) {
      setReceiverError(typeof reason === "string" ? reason : "The receiver could not start");
    } finally {
      setIsStartingReceiver(false);
    }
  };

  const decideTransfer = async (accepted: boolean) => {
    setReceiverError(null);
    try {
      setReceiver(await invoke<ReceiverSnapshot>("decide_transfer", { accepted }));
    } catch (reason) {
      setReceiverError(typeof reason === "string" ? reason : "The decision could not be applied");
    }
  };

  return (
    <main>
      <header>
        <a className="brand" href="#top" aria-label="ABOT home">abot.</a>
        <span className="status"><span aria-hidden="true" /> {nativeShell ? "Native shell ready" : "Browser development mode"}</span>
      </header>

      <section className="hero" id="top">
        <p className="eyebrow">GESTURE-POWERED FILE SHARING</p>
        <h1>Abot mo, <em>salo ko.</em></h1>
        <p className="lede">Local-first file sharing for Android and Windows. Send one file over an encrypted connection, with approval on this PC before any file bytes move.</p>
      </section>

      <section className="receiver" aria-labelledby="receive-heading">
        <div>
          <p className="step">01 / RECEIVE</p>
          <h2 id="receive-heading">Receive from Android</h2>
          <p>Choose the destination on this PC, then paste the one-time ABOT pairing link into the Android app.</p>
        </div>
        <div className="receiver-card" aria-live="polite">
          {receiver.state === "stopped" && (
            <button type="button" onClick={startReceiver} disabled={!nativeShell || isStartingReceiver}>
              {isStartingReceiver ? "Starting receiver…" : "Choose folder and start receiver"}
            </button>
          )}
          {receiver.state === "waiting" && receiver.pairingUri && (
            <>
              <strong>Waiting for an encrypted offer</strong>
              <p>On Android, paste this one-time pairing link:</p>
              <code>{receiver.pairingUri}</code>
              <small>Endpoint {receiver.endpoint}</small>
            </>
          )}
          {receiver.state === "offerPending" && receiver.offer && (
            <>
              <strong>Incoming file</strong>
              <p className="incoming-name">{receiver.offer.name}</p>
              <small>{formatBytes(receiver.offer.size)} · SHA-256 {receiver.offer.sha256.slice(0, 16)}…</small>
              <div className="decision-actions">
                <button type="button" onClick={() => void decideTransfer(true)}>Accept and receive</button>
                <button className="secondary" type="button" onClick={() => void decideTransfer(false)}>Reject</button>
              </div>
            </>
          )}
          {receiver.state === "receiving" && <strong>Receiving encrypted file…</strong>}
          {receiver.state === "rejected" && <strong>Transfer rejected. No file bytes were accepted.</strong>}
          {receiver.state === "completed" && (
            <>
              <strong>File received and verified</strong>
              <p>{receiver.savedPath}</p>
              {receiver.error && <p className="inline-error">{receiver.error}</p>}
              <button type="button" onClick={startReceiver}>Receive another file</button>
            </>
          )}
          {receiver.state === "failed" && (
            <>
              <strong>Transfer failed</strong>
              <p className="inline-error">{receiver.error}</p>
              <button type="button" onClick={startReceiver}>Start again</button>
            </>
          )}
          {!nativeShell && <p className="notice">Receiving requires the native Windows app.</p>}
          {receiverError && <p className="inline-error" role="alert">{receiverError}</p>}
        </div>
      </section>

      <section className="workspace" aria-labelledby="send-heading">
        <div>
          <p className="step">01 / SELECT</p>
          <h2 id="send-heading">Choose what to send</h2>
          <p>The receiver must accept the offer before ABOT sends any bytes.</p>
        </div>

        <button className="drop-zone" type="button" onClick={chooseFiles} disabled={isSelecting}>
          <span className="plus" aria-hidden="true">+</span>
          <strong>{isSelecting ? "Preparing files…" : files.length ? "Change selection" : "Select files"}</strong>
          <span>{nativeShell ? "Native selection with streamed SHA-256" : "Browser metadata preview"}</span>
        </button>
        <input ref={fileInput} id={inputId} className="visually-hidden" type="file" multiple onChange={selectFiles} />
        {error && <p className="error" role="alert">{error}</p>}

        {files.length > 0 && (
          <div className="selection" aria-live="polite">
            <div className="selection-summary">
              <strong>{files.length} {files.length === 1 ? "file" : "files"}</strong>
              <span>{formatBytes(totalBytes)}</span>
            </div>
            <ul>
              {files.map((file, index) => (
                <li key={file.id ?? `${file.name}-${index}`}>
                  <span>{file.name}{file.sha256 && <small className="digest">SHA-256 {file.sha256.slice(0, 12)}…</small>}</span>
                  <small>{formatBytes(file.size)}</small>
                </li>
              ))}
            </ul>
            <button type="button" disabled title="Windows-to-Android sending is a later milestone">Send to Android</button>
            <p className="notice">This milestone implements Android → Windows first.</p>
          </div>
        )}
      </section>
    </main>
  );
}
