import { invoke, isTauri } from "@tauri-apps/api/core";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { useEffect, useRef, useState } from "react";
import { GestureAccept } from "./GestureAccept";
import abotMark from "./assets/abot-mark.svg";

type IncomingOffer = { transferId: string; fileId: string; name: string; size: number; sha256: string };
type ReceiverSnapshot = {
  state: string; running: boolean; generation: number; endpoint?: string; pairingUri?: string;
  pairingQrDataUrl?: string; pairingCode?: string; pairingApproved: boolean;
  trustedDiscoveryAvailable: boolean; trustedReconnect: boolean; identityFingerprint?: string;
  offer?: IncomingOffer; savedPath?: string;
  lastCompleted?: { sequence: number; transferId: string; name: string; savedPath: string };
  error?: string;
};
type SelectedFile = { id: string; name: string; size: number; sha256: string };
type OutgoingSnapshot = { state: "idle" | "armed" | "completed"; fileName?: string };
type AppView = "overview" | "activity" | "settings";

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
  const [activeView, setActiveView] = useState<AppView>("overview");
  const nativeShell = isTauri();

  useEffect(() => {
    if (!nativeShell || receiver.state === "stopped") return;
    const refresh = () => void invoke<ReceiverSnapshot>("receiver_status").then(setReceiver).catch(() => setReceiverError("Receiver status is unavailable"));
    refresh();
    const timer = window.setInterval(refresh, 750);
    return () => window.clearInterval(timer);
  }, [nativeShell, receiver.state === "stopped"]);

  const startReceiver = async () => {
    setReceiverError(null);
    setIsStartingReceiver(true);
    try { setReceiver(await invoke<ReceiverSnapshot>("start_receiver")); }
    catch (reason) { setReceiverError(typeof reason === "string" ? reason : "The receiver could not start"); }
    finally { setIsStartingReceiver(false); }
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
    const timer = window.setInterval(() => void invoke<OutgoingSnapshot>("outgoing_status").then(status => {
      if (status.state !== "completed") return;
      setToast(`${status.fileName ?? outgoing?.name ?? "File"} was received and verified on Android.`);
      setOutgoing(null);
      setOutgoingArmed(false);
    }), 750);
    return () => window.clearInterval(timer);
  }, [nativeShell, outgoingArmed, outgoing?.name]);

  const decideTransfer = async (accepted: boolean, transferId = receiver.offer?.transferId) => {
    setReceiverError(null);
    try { setReceiver(await invoke<ReceiverSnapshot>("decide_transfer", { accepted, transferId })); }
    catch (reason) { setReceiverError(typeof reason === "string" ? reason : "The decision could not be applied"); }
  };

  const approvePairing = async () => {
    setReceiverError(null);
    try { setReceiver(await invoke<ReceiverSnapshot>("approve_pairing")); }
    catch (reason) { setReceiverError(typeof reason === "string" ? reason : "The pairing approval could not be applied"); }
  };

  const chooseOutgoing = async () => {
    setReceiverError(null);
    try {
      const selected = await invoke<SelectedFile[]>("select_files");
      setOutgoing(selected[0] ?? null);
      setOutgoingArmed(false);
    } catch (reason) { setReceiverError(typeof reason === "string" ? reason : "The file could not be selected"); }
  };

  useEffect(() => {
    if (!nativeShell) return;
    let disposed = false;
    let unlisten: (() => void) | undefined;
    void getCurrentWebview().onDragDropEvent(event => {
      if (disposed) return;
      if (event.payload.type === "enter" || event.payload.type === "over") setDragActive(true);
      else if (event.payload.type === "leave") setDragActive(false);
      else if (event.payload.type === "drop") {
        setDragActive(false);
        setReceiverError(null);
        void invoke<SelectedFile[]>("select_dropped_file", { paths: event.payload.paths }).then(selected => {
          setOutgoing(selected[0] ?? null);
          setOutgoingArmed(false);
        }).catch(reason => setReceiverError(typeof reason === "string" ? reason : "The dropped file could not be selected"));
      }
    }).then(stop => { unlisten = stop; });
    return () => { disposed = true; unlisten?.(); };
  }, [nativeShell]);

  const armOutgoing = async () => {
    if (!outgoing) return;
    setReceiverError(null);
    try {
      await invoke("arm_outgoing_file", { fileId: outgoing.id });
      setOutgoingArmed(true);
      setToast(`${outgoing.name} is ready for your trusted phone.`);
    } catch (reason) { setReceiverError(typeof reason === "string" ? reason : "The file could not be armed"); }
  };

  const receiverLabel = receiver.state === "failed" ? "Needs attention" : receiver.running ? receiver.offer ? "Transfer waiting" : "Available" : "Offline";
  const receiverTone = receiver.state === "failed" ? "error" : receiver.running ? "ready" : "offline";

  return <div className="app-frame">
    <aside className="sidebar" aria-label="Primary navigation">
      <button className="brand" type="button" onClick={() => setActiveView("overview")} aria-label="ABOT overview"><img src={abotMark} alt="" /><span>abot.</span></button>
      <nav>{(["overview", "activity", "settings"] as const).map(view => <button type="button" key={view} className={`nav-item${activeView === view ? " active" : ""}`} onClick={() => setActiveView(view)}><span aria-hidden="true">{view === "overview" ? "01" : view === "activity" ? "02" : "03"}</span>{view[0].toUpperCase() + view.slice(1)}</button>)}</nav>
      <div className="sidebar-note"><span className="privacy-mark" aria-hidden="true" />Local by design<small>Files and camera frames stay on your devices.</small></div>
    </aside>

    <main className="workspace">
      <header className="topbar"><button className="mobile-brand" type="button" onClick={() => setActiveView("overview")} aria-label="ABOT overview"><img src={abotMark} alt="" /><span>abot.</span></button><p>{activeView}</p><span className={`connection-pill ${receiverTone}`}><i aria-hidden="true" />{receiverLabel}</span></header>

      {activeView === "overview" && <>
        <section className="page-heading"><p className="eyebrow">PRIVATE. LOCAL. IMMEDIATE.</p><h1>Move files.<br /><em>Not friction.</em></h1><p className="lede">Your trusted phone reconnects automatically. Drop a file to send, or leave ABOT ready to catch one.</p></section>
        <section className="dashboard-grid">
          <article className="panel receiver-panel" aria-labelledby="receive-heading" aria-live="polite">
            <div className="panel-heading"><div><p className="eyebrow">RECEIVE</p><h2 id="receive-heading">From phone</h2></div><span className="panel-index">A</span></div>
            <div className="panel-body receiver-state">
              {receiver.state === "stopped" && <button type="button" onClick={startReceiver} disabled={!nativeShell || isStartingReceiver}>{isStartingReceiver ? "Starting..." : "Start receiver"}</button>}
              {receiver.state === "waiting" && receiver.pairingUri && <><div className="signal-orbit" aria-hidden="true"><span /></div><strong>Listening on this PC</strong><p className="muted">Trusted phones can send now. Files save to Downloads.</p><button className="text-action" type="button" onClick={() => setShowPairing(true)}>Pair another phone <span>↗</span></button></>}
              {receiver.state === "offerPending" && receiver.offer && <><span className="blue-tag">INCOMING FILE</span><strong>Ready to catch</strong><p className="incoming-name">{receiver.offer.name}</p><small>{formatBytes(receiver.offer.size)}</small><div className="decision-actions"><button type="button" onClick={() => void decideTransfer(true)} disabled={!receiver.pairingApproved}>Receive</button><button className="button-secondary" type="button" onClick={() => void decideTransfer(false)}>Decline</button></div>{!receiver.pairingApproved && <button className="button-secondary pairing-approval" type="button" onClick={() => void approvePairing()}>Confirm matching code</button>}{receiver.pairingApproved && <GestureAccept key={receiver.offer.transferId} onAccept={() => void decideTransfer(true, receiver.offer?.transferId)} />}</>}
              {receiver.state === "receiving" && <><div className="progress-ring" /><strong>Receiving securely...</strong><p className="muted">Verifying size and SHA-256 before saving.</p></>}
              {receiver.state === "rejected" && <strong>Declined. Getting ready again...</strong>}
              {receiver.state === "completed" && <><div className="success-mark" aria-hidden="true">✓</div><strong>Received</strong><p className="muted">Verified and saved. Re-arming automatically...</p></>}
              {(receiver.state === "starting" || receiver.state === "restarting") && <><div className="progress-ring" /><strong>Preparing receiver...</strong></>}
              {receiver.state === "failed" && <><strong>Receiver needs attention</strong><p className="inline-error">{receiver.error}</p><button type="button" onClick={startReceiver}>Try again</button></>}
              {!nativeShell && <p className="notice">Open this page in the native Windows app.</p>}{receiverError && <p className="inline-error" role="alert">{receiverError}</p>}
            </div>
          </article>

          <article className={`panel send-panel${dragActive ? " drag-active" : ""}`} aria-labelledby="send-heading">
            <div className="panel-heading"><div><p className="eyebrow">SEND</p><h2 id="send-heading">To phone</h2></div><span className="panel-index">B</span></div>
            <div className="panel-body send-state">
              {!outgoing && <><div className="drop-glyph" aria-hidden="true">↓</div><strong>Drop one file here</strong><p className="muted">Or choose a file from this PC. Nothing leaves until your phone accepts.</p><button type="button" className="button-secondary compact" onClick={() => void chooseOutgoing()}>Browse files</button></>}
              {outgoing && !outgoingArmed && <><span className="blue-tag">FILE SELECTED</span><strong className="file-name">{outgoing.name}</strong><small>{formatBytes(outgoing.size)}</small><GestureAccept key={outgoing.id} onAccept={() => void armOutgoing()} intro="File ready. Show an open palm, then close it into a fist to grab the file." armed="PALM LOCKED — close your hand to send this file." action="send this file" firstPose="Open_Palm" secondPose="Closed_Fist" /><button type="button" onClick={() => void armOutgoing()}>Send without gesture</button><button type="button" className="button-secondary" onClick={() => void chooseOutgoing()}>Choose a different file</button></>}
              {outgoing && outgoingArmed && <><div className="success-mark blue" aria-hidden="true">✓</div><strong>Waiting for your phone</strong><p className="muted">Keep ABOT open on Android and accept {outgoing.name} when it appears.</p><button type="button" className="button-secondary compact" onClick={() => void chooseOutgoing()}>Change file</button></>}
            </div>
          </article>
        </section>
      </>}

      {activeView === "activity" && <section className="secondary-page"><div className="page-heading compact-heading"><p className="eyebrow">TRANSFER LOG</p><h1>Activity.</h1></div><div className="activity-list">{receiver.lastCompleted ? <div className="activity-row"><span className="activity-icon">↓</span><div><strong>{receiver.lastCompleted.name}</strong><p>Received and verified</p></div><div className="activity-meta"><span>Downloads</span><small>Latest</small></div></div> : <div className="empty-state"><span>00</span><strong>No completed transfers yet.</strong><p>Your latest verified transfer will appear here.</p></div>}{outgoing && <div className="activity-row"><span className="activity-icon blue">↑</span><div><strong>{outgoing.name}</strong><p>{outgoingArmed ? "Waiting for phone" : "Selected on this PC"}</p></div><div className="activity-meta"><span>{formatBytes(outgoing.size)}</span><small>Current</small></div></div>}</div></section>}

      {activeView === "settings" && <section className="secondary-page"><div className="page-heading compact-heading"><p className="eyebrow">DEVICE & PRIVACY</p><h1>Settings.</h1></div><div className="settings-list"><div className="setting-row"><div><strong>Receive folder</strong><p>Verified incoming files</p></div><span>Downloads</span></div><div className="setting-row"><div><strong>Connection</strong><p>Private local network only</p></div><span>{receiverLabel}</span></div><div className="setting-row"><div><strong>Camera processing</strong><p>Gesture frames never leave this PC</p></div><span>On device</span></div>{receiver.endpoint && <div className="setting-row"><div><strong>Local endpoint</strong><p>Current listening address</p></div><code>{receiver.endpoint}</code></div>}<button type="button" className="settings-action" onClick={() => setShowPairing(true)}>Pair another phone <span>↗</span></button></div></section>}
    </main>

    {showPairing && receiver.pairingUri && <div className="modal-backdrop" role="presentation" onMouseDown={() => setShowPairing(false)}><section className="pairing-modal" role="dialog" aria-modal="true" aria-labelledby="pairing-title" onMouseDown={event => event.stopPropagation()}><div className="modal-header"><div><p className="eyebrow">NEW PHONE</p><h2 id="pairing-title">Scan once.</h2></div><button type="button" className="icon-button" onClick={() => setShowPairing(false)} aria-label="Close pairing">×</button></div><div className="pairing-content">{receiver.pairingQrDataUrl && <img className="pairing-qr" src={receiver.pairingQrDataUrl} alt="Signed ABOT pairing QR code" />}<div><p className="pairing-code" aria-label={`Pairing code ${receiver.pairingCode}`}>{receiver.pairingCode}</p><p className="muted">Scan this QR in ABOT, then confirm the same six-digit code on both devices.</p></div></div><button type="button" onClick={() => void approvePairing()} disabled={receiver.pairingApproved}>{receiver.pairingApproved ? "This PC is approved" : "The codes match"}</button></section></div>}
    {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
  </div>;
}
