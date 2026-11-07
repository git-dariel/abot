# Decisions

## ADR-013: One reconnect owner per platform process

Keep Android trusted discovery, reverse polling, retry scheduling, and the current endpoint under the unexported `connectedDevice` foreground service. Activities observe a service connection snapshot and may request a reconnect, but never create a competing NSD discovery session. Invalidate service-sourced endpoints after recoverable connection failures and rediscover with capped backoff. Start the Windows listener supervisor during native Tauri setup so receiver availability does not depend on React mounting; make repeated start commands idempotent.

## ADR-012: Foreground ABOT Gallery gesture flow

Approximate Huawei's documented open-palm-to-fist transfer UX within normal Android application privileges by owning the photo-viewing surface. ABOT Gallery reads only user-granted MediaStore images, reconnects to an already trusted Windows receiver, and opens the local gesture camera automatically when the user opens a photo. The current photo becomes the offer source; palm-to-fist creates the offer, while Windows independently detects fist-to-palm as the receiving user's acceptance before file bytes move. Keep the conventional picker and buttons as fallback. Do not claim integration with another vendor's Gallery, background Android camera access, or cross-camera hand tracking. This supersedes ADR-011's explicit camera-enable step only for the ABOT Gallery and approved-offer fast path.

## ADR-011: Local opt-in gesture intent

Bundle the pinned MediaPipe Gesture Recognizer model/runtime. Android palm-to-fist offers the already selected document; Windows fist-to-palm accepts the reviewed pending offer only after pairing approval. Require explicit camera activation, one detected hand, confidence >= 0.8, stable 600 ms poses, an 8-second sequence window, and one trigger per camera session. Bind desktop decisions to the displayed transfer UUID. Keep button controls and stop cameras on screen exit. Resolve Windows Downloads through the OS path API by default.

## ADR-001: Transfer before gestures

Build and verify the conventional transfer path first. Gesture events may only drive the same explicit state machine.

## ADR-002: Discovery is untrusted

An mDNS/UDP advertisement is a hint, not identity. Trust is established only by identity-bound pairing and explicit consent.

## ADR-003: Safe receiving

Validate leaf filenames and declared sizes, stream into a temporary file, verify byte count and SHA-256, then atomically persist without silent overwrite.

## ADR-004: Honest capability reporting

Scaffolded, compiled, synthetically tested, emulator-tested, and real-device-tested are distinct statuses.

## ADR-005: Backend-owned Windows file selection

The Tauri backend opens the native file dialog and retains selected paths in Rust state. The WebView receives IDs, safe names, sizes, and hashes but cannot submit arbitrary filesystem paths for hashing.

## ADR-006: Pairing invitation format

QR payloads are signed with Ed25519 and expire. The signature covers a deterministic length-prefixed transcript containing the protocol version, device ID, display name, endpoint, expiry, nonce, and identity key. The six-digit code is for human comparison, not encryption.

## ADR-007: First encrypted transfer slice

Connect Android sender to Windows receiver first. Use a Windows-generated one-use 256-bit secret and AES-256-GCM domain-separated frames so the offer, decision, chunks, and verification acknowledgement are confidential and authenticated. Keep chunks at 64 KiB and require a fresh receiver session per file. Treat this as development pairing, not durable device identity; do not silently promote the secret into long-lived trust.

## ADR-008: ABOT rebrand compatibility

Rebrand product, package, bundle, UI, and artifact names to ABOT. The Android application ID becomes `app.abot.android` and the Windows bundle identifier becomes `app.abot.desktop`, so previous installations do not upgrade in place. Preserve v1 `AIRBRIDGE-*` authenticated-data bytes, the `_airbridge._tcp.local.` service type, and canonical `airbridge.local` schema IDs. Generate `abot://` pairing links while accepting both `abot://` and legacy `airbridge://` links.

## ADR-009: Receiver-authenticated QR pairing

Persist the Windows Ed25519 private identity in Windows Credential Manager and derive its stable device UUID from the public key. Put a fresh one-use transport secret inside a signed, five-minute QR invitation. Android verifies the signature locally, compares the same six-digit transcript code, and stores only the explicitly approved Windows public identity with a revoke action. Bind each encrypted offer to the invitation's receiver public key and require Windows pairing approval before transfer acceptance. This is receiver-authenticated pairing; Android private identity and mutual key agreement remain future work.

## ADR-010: Possession-authenticated trusted reconnect

After a successful QR-approved transfer, provision a random per-phone reconnect credential only inside the encrypted offer. Protect it at rest with Android Keystore and Windows Credential Manager. Generate separate signed QR and discovery invitations so the QR session secret is never advertised. For repeat sessions, advertise only the discovery invitation as untrusted input, use its nonce as a challenge, and require an HMAC-SHA-256 proof over the challenge, receiver identity key, and Android installation UUID. Derive the reconnect AES key from a separate HMAC domain. This removes repeat QR scanning without treating discovery as identity; Android asymmetric identity and replay caching remain separate hardening work.

## ADR-014: Shared editorial design, platform-native structure

Use one cross-platform visual language built from paper white, ink black, cornflower blue, hard one-pixel borders, generous whitespace, and bold editorial headings. Share tokens and component principles, but keep navigation platform-appropriate: a responsive desktop sidebar that becomes a bottom bar on narrow Windows layouts, and Android top/bottom app bars with scrollable Compose content. Show only real receiver, transfer, pairing, and session state; do not invent dashboard metrics or persistent history. Keep the redesign inside the presentation layer and preserve all protocol, consent, streaming, verification, and local-first boundaries.

## ADR-015: Session-only discovery and sequential batches

Require an explicit Android Settings toggle before trusted Windows discovery starts. Keep the permission in process memory so a fresh launch is always OFF; use a non-sticky foreground service only while the user-enabled session is active or an already accepted transfer is finishing. For Android-to-Windows multi-selection, do not introduce a batch wire format: deduplicate content URIs and send each item as a consecutive existing authenticated offer, preserving independent Windows consent, 64 KiB streaming, size/SHA-256 verification, atomic finalization, and per-file failure reporting.
