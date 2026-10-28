# Security Design

## Threats and controls

| Threat | Required control |
|---|---|
| Spoofed discovery | Treat advertisements as untrusted; display identity fingerprint after pairing |
| MITM during pairing | Bind the human-verified QR/short code to both ephemeral handshake keys and device identities |
| Unauthorized transfer | Mutual authenticated session plus explicit receiver acceptance and scoped token |
| Replay | Unique message IDs, bounded replay cache, nonces, and short session lifetime |
| Path traversal | Accept portable leaf names only and choose the destination directory locally |
| Overwrite | No silent overwrite; require a unique destination or explicit user choice |
| Resource exhaustion | Metadata count/size limits, streaming, timeouts, concurrency/rate limits |
| Corruption | Declared size plus SHA-256, temporary write, atomic finalization |
| Camera privacy | Local inference; no frame/landmark transmission or logging by default |

## Cryptographic direction

The first-pairing slice uses AES-256-GCM with a random, one-use 32-byte secret carried only in a signed, expiring QR invitation. A separate signed discovery invitation is safe to advertise because its independent nonce is used only as an HMAC challenge. Repeat sessions derive a new AES key through HMAC-SHA-256 with a per-phone reconnect credential. Distinct associated-data domains authenticate offers, decisions, chunks, and completion; chunk authentication also binds the transfer UUID and sequence. The encrypted offer names the receiver identity key.

The six-digit display code is derived from the authenticated transcript; it is not an encryption key. The Windows private identity and reconnect credentials are held in Windows Credential Manager. Android stores the explicitly approved public identity, encrypts reconnect credentials with a non-exportable Android Keystore AES key, and exposes revocation. This provides possession-authenticated reconnect but not an Android asymmetric identity. Replay caching and production cryptographic review remain pending.
