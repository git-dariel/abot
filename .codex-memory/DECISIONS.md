# Decisions

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
