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

The connected slice uses AES-256-GCM with a random, one-use 32-byte secret carried in a signed, expiring QR invitation. Distinct associated-data domains authenticate offers, decisions, chunks, and completion; chunk authentication also binds the transfer UUID and sequence. The invitation binds the secret and private endpoint to the persistent Windows Ed25519 identity, and the encrypted offer names that receiver key.

The six-digit display code is derived from the authenticated transcript; it is not an encryption key. The Windows private identity is held in Windows Credential Manager. Android stores only the explicitly approved public identity and exposes revocation. Android private identity, mutual authenticated key agreement, tamper-resistant trust metadata, and a replay cache remain pending and require focused review before production release.
