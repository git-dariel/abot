import { invoke, isTauri } from "@tauri-apps/api/core";
import { getCurrentWebview } from "@tauri-apps/api/webview";
import { useEffect, useRef, useState } from "react";
import { GestureAccept } from "./GestureAccept";
import { TransferArtwork } from "./TransferArtwork";
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

function NavIcon({ view }: { view: AppView }) {
  if (view === "overview") return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 11 9-8 9 8v10H3Z" /><path d="M9 21v-6h6v6" /></svg>;
  if (view === "activity") return <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="17" rx="4" /><path d="M7 9h10M7 14h7" /></svg>;
  return <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="7" /><circle cx="12" cy="12" r="2.5" /><path d="M12 2v3M12 19v3M2 12h3M19 12h3" /></svg>;
}

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
      <button className="brand" type="button" onClick={() => setActiveView("overview")} aria-label="ABOT overview"><img src={abotMark} alt="" /><span>abot</span></button>
      <nav>{(["overview", "activity", "settings"] as const).map(view => <button type="button" key={view} className={`nav-item${activeView === view ? " active" : ""}`} onClick={() => setActiveView(view)}><NavIcon view={view} /><span>{view[0].toUpperCase() + view.slice(1)}</span></button>)}</nav>
      <div className="privacy-note"><span className="privacy-dot" aria-hidden="true" /><div><strong>Local by design</strong><small>Files and camera frames stay on your devices.</small></div></div>
    </aside>

    <main className="workspace">
      <header className="topbar">
        <button className="mobile-brand" type="button" onClick={() => setActiveView("overview")} aria-label="ABOT overview"><img src={abotMark} alt="" /><span>abot</span></button>
        <div className="topbar-title"><span>{activeView}</span><strong>{activeView === "overview" ? "Your private relay" : activeView === "activity" ? "Transfer history" : "Device controls"}</strong></div>
        <span className={`connection-pill ${receiverTone}`}><i aria-hidden="true" />{receiverLabel}</span>
      </header>

      {activeView === "overview" && <div className="page overview-page">
        <section className="feature-grid">
          <article className={`feature-card send-card${dragActive ? " drag-active" : ""}`} aria-labelledby="send-heading">
            <div className="feature-heading"><div><span className="card-kicker">SEND</span><h2 id="send-heading">To phone</h2></div><TransferArtwork className="card-art" /></div>
            <div className="feature-content">
              {!outgoing && <div className="center-state"><span className="drop-icon" aria-hidden="true">↓</span><strong>Drop one file here</strong><p>Or choose a file from this PC. Your phone still approves it.</p><button type="button" className="pill-button navy" onClick={() => void chooseOutgoing()}>Browse files</button></div>}
              {outgoing && !outgoingArmed && <div className="selected-state"><span className="soft-pill">FILE SELECTED</span><strong className="file-name">{outgoing.name}</strong><small>{formatBytes(outgoing.size)}</small><GestureAccept key={outgoing.id} onAccept={() => void armOutgoing()} intro="File ready. Show an open palm, then close it into a fist to grab the file." armed="PALM LOCKED — close your hand to send this file." action="send this file" firstPose="Open_Palm" secondPose="Closed_Fist" /><div className="button-row"><button type="button" className="pill-button navy" onClick={() => void armOutgoing()}>Send without gesture</button><button type="button" className="pill-button ghost" onClick={() => void chooseOutgoing()}>Change file</button></div></div>}
              {outgoing && outgoingArmed && <div className="center-state"><span className="success-icon" aria-hidden="true">✓</span><strong>Waiting for your phone</strong><p>Open ABOT on Android and approve {outgoing.name}.</p><button type="button" className="pill-button ghost" onClick={() => void chooseOutgoing()}>Change file</button></div>}
            </div>
          </article>

          <article className="feature-card receive-card" aria-labelledby="receive-heading" aria-live="polite">
            <div className="feature-heading"><div><span className="card-kicker">RECEIVE</span><h2 id="receive-heading">From phone</h2></div><TransferArtwork className="card-art" reversed /></div>
            <div className="feature-content">
              {receiver.state === "stopped" && <div className="center-state"><strong>Receiver is offline</strong><p>Start the local receiver when you are ready.</p><button type="button" className="pill-button sky" onClick={startReceiver} disabled={!nativeShell || isStartingReceiver}>{isStartingReceiver ? "Starting…" : "Start receiver"}</button></div>}
              {receiver.state === "waiting" && receiver.pairingUri && <div className="center-state"><span className="signal-mark"><i /></span><strong>Ready to receive</strong><p>Trusted phones can send now. Verified files save to Downloads.</p><button className="text-action light" type="button" onClick={() => setShowPairing(true)}>Pair another phone <span>↗</span></button></div>}
              {receiver.state === "offerPending" && receiver.offer && <div className="selected-state"><span className="sky-pill">INCOMING FILE</span><strong className="file-name">{receiver.offer.name}</strong><small>{formatBytes(receiver.offer.size)}</small><div className="button-row"><button type="button" className="pill-button sky" onClick={() => void decideTransfer(true)} disabled={!receiver.pairingApproved}>Receive</button><button className="pill-button dark-ghost" type="button" onClick={() => void decideTransfer(false)}>Decline</button></div>{!receiver.pairingApproved && <button className="pill-button dark-ghost" type="button" onClick={() => void approvePairing()}>Confirm matching code</button>}{receiver.pairingApproved && <GestureAccept key={receiver.offer.transferId} onAccept={() => void decideTransfer(true, receiver.offer?.transferId)} />}</div>}
              {receiver.state === "receiving" && <div className="center-state"><span className="spinner" /><strong>Receiving securely…</strong><p>Verifying size and SHA-256 before saving.</p></div>}
              {receiver.state === "rejected" && <div className="center-state"><strong>Transfer declined</strong><p>Preparing the receiver for another offer.</p></div>}
              {receiver.state === "completed" && <div className="center-state"><span className="success-icon sky" aria-hidden="true">✓</span><strong>Received and verified</strong><p>Saved to Downloads. The receiver is re-arming.</p></div>}
              {(receiver.state === "starting" || receiver.state === "restarting") && <div className="center-state"><span className="spinner" /><strong>Preparing receiver…</strong></div>}
              {receiver.state === "failed" && <div className="center-state"><strong>Receiver needs attention</strong><p className="error-copy">{receiver.error}</p><button type="button" className="pill-button sky" onClick={startReceiver}>Try again</button></div>}
              {!nativeShell && <p className="notice">Open this page in the native Windows app.</p>}{receiverError && <p className="error-copy" role="alert">{receiverError}</p>}
            </div>
          </article>
        </section>

        <section className="recent-card">
          <div className="section-heading"><div><span className="eyebrow">RECENT ITEM</span><h2>Latest transfer</h2></div><span className="session-label">THIS SESSION</span></div>
          {receiver.lastCompleted ? <div className="recent-row"><span className="file-tile">✓</span><div><strong>{receiver.lastCompleted.name}</strong><p>Received and verified · Downloads</p></div><span>Latest</span></div> : <div className="empty-recent"><strong>No completed transfers yet</strong><p>Your latest verified file will appear here.</p></div>}
        </section>
      </div>}

      {activeView === "activity" && <div className="page secondary-page"><section className="page-heading compact"><h1>Activity</h1></section><section className="recent-card">{receiver.lastCompleted ? <div className="recent-row"><span className="file-tile">↓</span><div><strong>{receiver.lastCompleted.name}</strong><p>Received and verified</p></div><span>Downloads</span></div> : <div className="empty-recent"><strong>No completed transfers yet</strong><p>Your latest verified file will appear here.</p></div>}{outgoing && <div className="recent-row"><span className="file-tile sky">↑</span><div><strong>{outgoing.name}</strong><p>{outgoingArmed ? "Waiting for phone" : "Selected on this PC"}</p></div><span>{formatBytes(outgoing.size)}</span></div>}</section></div>}

      {activeView === "settings" && <div className="page secondary-page"><section className="page-heading compact"><h1>Settings</h1></section><section className="settings-card"><div className="setting-row"><div><strong>Receive folder</strong><p>Verified incoming files</p></div><span>Downloads</span></div><div className="setting-row"><div><strong>Connection</strong><p>Private local network only</p></div><span>{receiverLabel}</span></div><div className="setting-row"><div><strong>Camera processing</strong><p>Gesture frames never leave this PC</p></div><span>On device</span></div>{receiver.endpoint && <div className="setting-row"><div><strong>Local endpoint</strong><p>Current listening address</p></div><code>{receiver.endpoint}</code></div>}</section><button type="button" className="settings-action" onClick={() => setShowPairing(true)}>Pair another phone <span>↗</span></button></div>}
    </main>

    {showPairing && receiver.pairingUri && <div className="modal-backdrop" role="presentation" onMouseDown={() => setShowPairing(false)}><section className="pairing-modal" role="dialog" aria-modal="true" aria-labelledby="pairing-title" onMouseDown={event => event.stopPropagation()}><div className="modal-header"><div><p className="eyebrow">NEW PHONE</p><h2 id="pairing-title">Scan once</h2></div><button type="button" className="close-button" onClick={() => setShowPairing(false)} aria-label="Close pairing">×</button></div><div className="pairing-content">{receiver.pairingQrDataUrl && <img className="pairing-qr" src={receiver.pairingQrDataUrl} alt="Signed ABOT pairing QR code" />}<div><p className="pairing-code" aria-label={`Pairing code ${receiver.pairingCode}`}>{receiver.pairingCode}</p><p>Scan this QR in ABOT, then confirm the same six-digit code on both devices.</p></div></div><button type="button" className="pill-button navy full" onClick={() => void approvePairing()} disabled={receiver.pairingApproved}>{receiver.pairingApproved ? "This PC is approved" : "The codes match"}</button></section></div>}
    {toast && <div className="toast" role="status"><span>✓</span>{toast}</div>}
  </div>;
}
