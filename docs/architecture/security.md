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

The connected development slice uses AES-256-GCM with a random, one-use 32-byte secret transferred in the manual pairing link. Distinct associated-data domains authenticate offers, decisions, chunks, and completion; chunk authentication also binds the transfer UUID and sequence. This protects the initial file path without treating discovery as identity.

Ed25519 still signs the deterministic pairing invitation, and the six-digit display code is derived from that authenticated transcript; it is not an encryption key. Binding the encrypted channel to persistent identities, platform secure storage, trust revocation, and a replay cache remain pending and require focused review before production release.
