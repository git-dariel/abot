import { invoke, isTauri } from "@tauri-apps/api/core";
import { getCurrentWebview } from "@tauri-apps/api/webview";
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
  running: boolean;
  generation: number;
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
  lastCompleted?: {
    sequence: number;
    transferId: string;
    name: string;
    savedPath: string;
  };
  error?: string;
};

type SelectedFile = {
  id: string;
  name: string;
  size: number;
  sha256: string;
};

type OutgoingSnapshot = {
  state: "idle" | "armed" | "completed";
  fileName?: string;
};

const formatBytes = (bytes: number) => {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const unit = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  return `${(bytes / 1024 ** unit).toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
};

export function App() {
  const receiverAutoStarted = useRef(false);
  const lastCompletionSequence = useRef(0);
  const [receiver, setReceiver] = useState<ReceiverSnapshot>({ state: "stopped", running: false, generation: 0, pairingApproved: false, trustedDiscoveryAvailable: false, trustedReconnect: false });
  const [receiverError, setReceiverError] = useState<string | null>(null);
  const [isStartingReceiver, setIsStartingReceiver] = useState(false);
  const [showPairing, setShowPairing] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const [outgoing, setOutgoing] = useState<SelectedFile | null>(null);
  const [outgoingArmed, setOutgoingArmed] = useState(false);
  const [dragActive, setDragActive] = useState(false);
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
    const completion = receiver.lastCompleted;
    if (!completion || completion.sequence <= lastCompletionSequence.current) return;
    lastCompletionSequence.current = completion.sequence;
    setToast(`${completion.name} received and saved to Downloads.`);
  }, [receiver.lastCompleted]);

  useEffect(() => {
    if (!toast) return;
    const hide = window.setTimeout(() => setToast(null), 4_500);
    return () => window.clearTimeout(hide);
  }, [toast]);

  useEffect(() => {
    if (!nativeShell || !outgoingArmed) return;
    const timer = window.setInterval(() => {
      void invoke<OutgoingSnapshot>("outgoing_status").then(status => {
        if (status.state !== "completed") return;
        setToast(`${status.fileName ?? outgoing?.name ?? "File"} was received and verified on Android.`);
        setOutgoing(null);
        setOutgoingArmed(false);
      });
    }, 750);
    return () => window.clearInterval(timer);
  }, [nativeShell, outgoingArmed, outgoing?.name]);

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

  const chooseOutgoing = async () => {
    setReceiverError(null);
    try {
      const selected = await invoke<SelectedFile[]>("select_files");
      setOutgoing(selected[0] ?? null);
      setOutgoingArmed(false);
    } catch (reason) {
      setReceiverError(typeof reason === "string" ? reason : "The file could not be selected");
    }
  };

  useEffect(() => {
    if (!nativeShell) return;
    let disposed = false;
    let unlisten: (() => void) | undefined;
    void getCurrentWebview().onDragDropEvent(event => {
      if (disposed) return;
      if (event.payload.type === "enter" || event.payload.type === "over") {
        setDragActive(true);
      } else if (event.payload.type === "leave") {
        setDragActive(false);
      } else if (event.payload.type === "drop") {
        setDragActive(false);
        setReceiverError(null);
        void invoke<SelectedFile[]>("select_dropped_file", { paths: event.payload.paths })
          .then(selected => {
            setOutgoing(selected[0] ?? null);
            setOutgoingArmed(false);
          })
          .catch(reason => setReceiverError(typeof reason === "string" ? reason : "The dropped file could not be selected"));
      }
    }).then(stop => { unlisten = stop; });
    return () => {
      disposed = true;
      unlisten?.();
    };
  }, [nativeShell]);

  const armOutgoing = async () => {
    if (!outgoing) return;
    setReceiverError(null);
    try {
      await invoke("arm_outgoing_file", { fileId: outgoing.id });
      setOutgoingArmed(true);
      setToast(`${outgoing.name} is ready for your trusted phone.`);
    } catch (reason) {
      setReceiverError(typeof reason === "string" ? reason : "The file could not be armed");
    }
  };

  return (
    <main className="app-shell">
      <header>
        <a className="brand" href="#top" aria-label="ABOT home">abot.</a>
        <span className="status"><span aria-hidden="true" /> {receiver.running ? "Ready" : "Offline"}</span>
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
          {(receiver.state === "starting" || receiver.state === "restarting") && <><div className="pulse" /><strong>Preparing receiver…</strong></>}
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
      <section className="send-section" aria-labelledby="send-heading">
        <p className="eyebrow">SEND TO PHONE</p>
        <div className={`send-card${dragActive ? " drag-active" : ""}`}>
          {!outgoing && (
            <>
              <strong id="send-heading">Send from this laptop</strong>
              <p className="muted">Drop one file here or choose it, then grab it with your hand gesture.</p>
              <button type="button" className="secondary compact" onClick={() => void chooseOutgoing()}>Choose file</button>
            </>
          )}
          {outgoing && !outgoingArmed && (
            <>
              <strong id="send-heading">{outgoing.name}</strong>
              <small>{formatBytes(outgoing.size)}</small>
              <GestureAccept
                key={outgoing.id}
                onAccept={() => void armOutgoing()}
                intro="File ready. Show an open palm, then close it into a fist to grab the file."
                armed="PALM LOCKED - close your hand to send this file."
                action="send this file"
                firstPose="Open_Palm"
                secondPose="Closed_Fist"
              />
              <button type="button" onClick={() => void armOutgoing()}>Send without gesture</button>
              <button type="button" className="secondary" onClick={() => void chooseOutgoing()}>Choose a different file</button>
            </>
          )}
          {outgoing && outgoingArmed && (
            <>
              <div className="ready-mark" aria-hidden="true">✓</div>
              <strong id="send-heading">Ready for phone</strong>
              <p className="muted">Keep ABOT open on Android and accept {outgoing.name} when it appears.</p>
              <button type="button" className="secondary compact" onClick={() => void chooseOutgoing()}>Change file</button>
            </>
          )}
        </div>
      </section>
      {toast && <div className="toast" role="status">✓ {toast}</div>}
    </main>
  );
}
