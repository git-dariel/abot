# System Design

AirBridge has separate platform applications joined by a versioned protocol. Each device independently handles selection, consent, camera inference, secure storage, and lifecycle.

## Control plane

1. Discovery yields untrusted nearby candidates.
2. Pairing binds a human-verified QR/short code to long-lived device identities.
3. An authenticated channel carries replay-protected offers and decisions.
4. A transfer offer contains metadata and hashes only.
5. The receiving user accepts or rejects before a file token is issued.

## Data plane

After acceptance, each file is streamed over an authenticated encrypted connection. The receiver enforces declared size and configured limits, uses a unique `.part` file, computes SHA-256 while writing, compares size and digest, and atomically renames only on success. Existing files are never silently overwritten.

Resume will use authenticated checkpoints containing transfer ID, file ID, byte offset, and a chunk-boundary digest. Resume is pending and must not trust a client-supplied offset without validating local partial state.

## Gesture plane

MediaPipe inference remains on each device. A stabilized `PINCH` or `GRAB` can arm an already selected transfer; a stabilized `OPEN_HAND` or `RELEASE` can confirm a visible incoming offer. Buttons invoke the same transitions and always remain available. Camera frames and landmarks are not transmitted by default.

## Ownership

- Android: CameraX, Storage Access Framework, foreground/lifecycle behavior, Android secure storage, Compose UI.
- Windows: WebView camera permission, native file dialogs/notifications, Windows secure storage, React UI.
- Rust libraries: portable transfer, discovery, pairing, and security rules.
- Protocol package: transport-independent message compatibility.
