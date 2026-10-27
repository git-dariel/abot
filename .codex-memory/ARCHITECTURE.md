# Architecture

- `packages/protocol` owns wire-level names, states, schemas, and compatibility fixtures.
- `crates/transfer-core` owns streamed I/O, filename validation, hashing, temporary writes, and atomic finalization.
- `crates/discovery` implements mDNS advertisement/browse plus validated manual local endpoints; observations cannot confer trust.
- `crates/pairing` owns explicit-consent state transitions and signed, expiring QR invitations.
- `crates/security` owns Ed25519 identities, randomness, constant-time tokens, fingerprints, and comparison-code primitives; secure persistence and transport integration remain pending.
- Platform apps own permissions, secure persistence, lifecycle, file selection, camera input, and user consent UI.

The control plane offers and accepts metadata before the data plane may stream bytes.

The first connected data plane is Android to Windows over private-LAN TCP. Windows keeps a persistent Ed25519 private identity in Windows Credential Manager and signs a five-minute QR invitation containing its endpoint and a one-use 32-byte secret. Android verifies the invitation locally, compares a six-digit code, and stores only the approved receiver public identity with a revoke control. The encrypted offer is bound to that receiver key. AES-256-GCM protects the offer, decision, 64 KiB chunks, and completion, and the receiver must approve pairing and then accept the offer before Android opens the document for upload. Android private identity and mutual key agreement remain pending.
