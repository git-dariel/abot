# Architecture

- `packages/protocol` owns wire-level names, states, schemas, and compatibility fixtures.
- `crates/transfer-core` owns streamed I/O, filename validation, hashing, temporary writes, and atomic finalization.
- `crates/discovery` implements mDNS advertisement/browse plus validated manual local endpoints; observations cannot confer trust.
- `crates/pairing` owns explicit-consent state transitions and signed, expiring QR invitations.
- `crates/security` owns Ed25519 identities, randomness, constant-time tokens, fingerprints, and comparison-code primitives; secure persistence and transport integration remain pending.
- Platform apps own permissions, secure persistence, lifecycle, file selection, camera input, and user consent UI.

The control plane offers and accepts metadata before the data plane may stream bytes.

The first connected data plane is Android to Windows over private-LAN TCP. Windows keeps a persistent Ed25519 private identity in Windows Credential Manager and signs separate five-minute QR and discovery invitations for the same endpoint. First pairing uses the private QR nonce as its one-use session secret and provisions a random per-phone reconnect credential inside the encrypted offer. Android encrypts that credential with Android Keystore; Windows stores it in Credential Manager. Later Windows sessions advertise only the separate signed discovery invitation through untrusted mDNS. Android verifies the signature and stored receiver key, proves possession of the reconnect credential, and both peers derive a fresh AES key from the discovery nonce. AES-256-GCM protects the offer, decision, 64 KiB chunks, and completion, and Windows must still accept the offer before Android opens the document. Android asymmetric identity remains pending.
