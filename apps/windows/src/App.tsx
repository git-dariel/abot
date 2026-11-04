import { invoke, isTauri } from "@tauri-apps/api/core";
import { useEffect, useRef, useState } from "react";
import { GestureAccept } from "./GestureAccept";

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
  pairingQrDataUrl?: string;
  pairingCode?: string;
  pairingApproved: boolean;
  trustedDiscoveryAvailable: boolean;
  trustedReconnect: boolean;
  identityFingerprint?: string;
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
  const receiverAutoStarted = useRef(false);
  const [receiver, setReceiver] = useState<ReceiverSnapshot>({ state: "stopped", pairingApproved: false, trustedDiscoveryAvailable: false, trustedReconnect: false });
  const [receiverError, setReceiverError] = useState<string | null>(null);
  const [isStartingReceiver, setIsStartingReceiver] = useState(false);
  const [showPairing, setShowPairing] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const nativeShell = isTauri();

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

  useEffect(() => {
    if (!nativeShell || receiverAutoStarted.current) return;
    receiverAutoStarted.current = true;
    void startReceiver();
  }, [nativeShell]);

  useEffect(() => {
    if (receiver.state !== "completed" && receiver.state !== "rejected") return;
    if (receiver.state === "completed") {
      const name = receiver.savedPath?.split(/[\\/]/).pop();
      setToast(name ? `${name} received and saved to Downloads.` : "File received and saved to Downloads.");
    }
    const restart = window.setTimeout(() => void startReceiver(), 900);
    return () => window.clearTimeout(restart);
  }, [receiver.state, receiver.savedPath]);

  useEffect(() => {
    if (!toast) return;
    const hide = window.setTimeout(() => setToast(null), 4_500);
    return () => window.clearTimeout(hide);
  }, [toast]);

  const decideTransfer = async (accepted: boolean, transferId = receiver.offer?.transferId) => {
    setReceiverError(null);
    try {
      setReceiver(await invoke<ReceiverSnapshot>("decide_transfer", { accepted, transferId }));
    } catch (reason) {
      setReceiverError(typeof reason === "string" ? reason : "The decision could not be applied");
    }
  };

  const approvePairing = async () => {
    setReceiverError(null);
    try {
      setReceiver(await invoke<ReceiverSnapshot>("approve_pairing"));
    } catch (reason) {
      setReceiverError(typeof reason === "string" ? reason : "The pairing approval could not be applied");
    }
  };

  return (
    <main className="app-shell">
      <header>
        <a className="brand" href="#top" aria-label="ABOT home">abot.</a>
        <span className="status"><span aria-hidden="true" /> {receiver.state === "stopped" ? "Offline" : "Ready"}</span>
      </header>

      <section className="hero" id="top">
        <p className="eyebrow">LOCAL FILE TRANSFER</p>
        <h1>Ready when<br />you are.</h1>
        <p className="lede">Keep ABOT open. Trusted phones reconnect automatically and received files go to Downloads.</p>
      </section>

      <section className="receiver" aria-labelledby="receive-heading">
        <div className="receiver-card" aria-live="polite">
          {receiver.state === "stopped" && (
            <button type="button" onClick={startReceiver} disabled={!nativeShell || isStartingReceiver}>
              {isStartingReceiver ? "Starting…" : "Start ABOT"}
            </button>
          )}
          {receiver.state === "waiting" && receiver.pairingUri && (
            <>
              <div className="ready-mark" aria-hidden="true">✓</div>
              <strong id="receive-heading">Ready to receive</strong>
              <p className="muted">Trusted phones connect automatically.</p>
              <button className="secondary compact" type="button" onClick={() => setShowPairing(value => !value)}>
                {showPairing ? "Hide pairing" : "Pair a new phone"}
              </button>
              {showPairing && <div className="pairing-panel">
                {receiver.pairingQrDataUrl && <img className="pairing-qr" src={receiver.pairingQrDataUrl} alt="Signed ABOT pairing QR code" />}
                <p className="pairing-code" aria-label={`Pairing code ${receiver.pairingCode}`}>{receiver.pairingCode}</p>
                <p className="muted">Scan once, confirm the same code, then future sessions reconnect automatically.</p>
                <button type="button" onClick={() => void approvePairing()} disabled={receiver.pairingApproved}>
                  {receiver.pairingApproved ? "PC approved" : "Code matches"}
                </button>
              </div>}
            </>
          )}
          {receiver.state === "offerPending" && receiver.offer && (
            <>
              <p className="eyebrow">INCOMING</p>
              <strong>Catch this file</strong>
              <p className="incoming-name">{receiver.offer.name}</p>
              <small>{formatBytes(receiver.offer.size)}</small>
              <div className="decision-actions">
                <button type="button" onClick={() => void decideTransfer(true)} disabled={!receiver.pairingApproved}>Receive</button>
                <button className="secondary" type="button" onClick={() => void decideTransfer(false)}>Reject</button>
              </div>
              {!receiver.pairingApproved && <button className="pairing-approval" type="button" onClick={() => void approvePairing()}>Code matches</button>}
              {receiver.pairingApproved && <GestureAccept key={receiver.offer.transferId} onAccept={() => void decideTransfer(true, receiver.offer?.transferId)} />}
            </>
          )}
          {receiver.state === "receiving" && <><div className="pulse" /><strong>Receiving securely…</strong></>}
          {receiver.state === "rejected" && <strong>Rejected. Re-arming ABOT…</strong>}
          {receiver.state === "completed" && (
            <>
              <div className="ready-mark" aria-hidden="true">✓</div>
              <strong>Received</strong>
              <p className="muted">Re-arming automatically…</p>
            </>
          )}
          {receiver.state === "failed" && (
            <>
              <strong>Transfer failed</strong>
              <p className="inline-error">{receiver.error}</p>
              <button type="button" onClick={startReceiver}>Try again</button>
            </>
          )}
          {!nativeShell && <p className="notice">Open this page in the native Windows app.</p>}
          {receiverError && <p className="inline-error" role="alert">{receiverError}</p>}
        </div>
      </section>
      {toast && <div className="toast" role="status">✓ {toast}</div>}
    </main>
  );
}
