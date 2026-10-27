# Architecture

- `packages/protocol` owns wire-level names, states, schemas, and compatibility fixtures.
- `crates/transfer-core` owns streamed I/O, filename validation, hashing, temporary writes, and atomic finalization.
- `crates/discovery` implements mDNS advertisement/browse plus validated manual local endpoints; observations cannot confer trust.
- `crates/pairing` owns explicit-consent state transitions and signed, expiring QR invitations.
- `crates/security` owns Ed25519 identities, randomness, constant-time tokens, fingerprints, and comparison-code primitives; secure persistence and transport integration remain pending.
- Platform apps own permissions, secure persistence, lifecycle, file selection, camera input, and user consent UI.

The control plane offers and accepts metadata before the data plane may stream bytes.

The first connected data plane is Android to Windows over private-LAN TCP. A Windows-generated one-use 32-byte secret encrypts/authenticates offer, decision, 64 KiB chunks, and completion with AES-256-GCM. The receiver selects the destination and accepts before Android opens the document for upload. This development channel is intentionally separate from durable identity trust until the Ed25519 binding and secure storage work is complete.
