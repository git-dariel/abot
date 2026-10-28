# Protocol v1

The normative machine-readable files are in `packages/protocol/schema`. Protocol `1.0` peers reject unknown major versions.

## Transfer sequence

1. Sender emits `transfer.offer` with safe metadata and per-file SHA-256 values.
2. Receiver shows the offer and emits `transfer.accept` or `transfer.reject` after explicit input.
3. Acceptance returns short-lived, transfer-scoped authorization; only then may bytes be sent.
4. Both peers emit local progress from actual byte counts.
5. Receiver enters `VERIFYING`, checks count and digest, atomically finalizes, and emits `transfer.complete`.
6. Either peer may emit `transfer.cancel`; errors emit `transfer.failed` with a safe code, not an internal stack trace.

Message IDs are single-use within a bounded replay window. Transfer and file IDs are UUIDs. Timestamps are advisory; replay protection cannot depend only on wall-clock synchronization.

## Encrypted single-file transport (implemented development slice)

The first connected path is Android sender to Windows receiver over TCP on a private LAN. Windows creates separate signed, five-minute QR and discovery invitations for the same private endpoint. First pairing transfers only the QR invitation out of band; its random nonce is the one-use AES secret, and Android verifies the Ed25519 signature before showing the comparison code. mDNS advertises only the separate discovery invitation, whose independent nonce is an HMAC challenge rather than a broadcast session key.

Every control or data frame is authenticated and encrypted with AES-256-GCM. Offer, decision, data, and completion frames use separate associated-data domains. Data frames also bind the transfer UUID and monotonically increasing sequence number. Plaintext chunks are limited to 64 KiB, so neither peer buffers the complete file.

The receiver decrypts and validates the offer before presenting it. It sends an encrypted accept/reject decision; Android does not reopen the document for upload until it authenticates an acceptance. A zero-length frame ends the encrypted stream. Windows then verifies the declared byte count and SHA-256, atomically finalizes the temporary file, and sends an encrypted verified acknowledgement.

The offer includes the invitation's receiver public key and Windows rejects a mismatch. During the first successful QR transfer it may also carry an Android installation UUID and random reconnect credential inside the encrypted offer. Android encrypts that credential at rest with Android Keystore; Windows stores it in Credential Manager. For reconnect, Android sends `ABR2` plus an HMAC proof over the fresh invitation nonce, receiver key, and installation UUID. Both peers independently derive the AES session key from the same inputs. The mDNS record is untrusted and cannot establish a session without the stored credential. Android asymmetric identity, replay caches beyond short invitation expiry, multiple files, cancellation, and resume remain pending.

## Pairing invitation

The JSON QR payload contains protocol version, device UUID, display name, socket endpoint, millisecond expiry, a 32-byte random nonce, an Ed25519 public identity key, and an Ed25519 signature. Binary fields are lowercase hexadecimal.

The signature covers the domain-separated `AIRBRIDGE-PAIRING\0` transcript followed by big-endian version, UUID bytes, length-prefixed UTF-8 display name and endpoint, big-endian expiry, nonce, and public key. That pre-rebrand byte string is deliberately preserved as a v1 compatibility identifier. Android rejects malformed, expired, public-endpoint, unsupported-version, or invalid-signature invitations before displaying the comparison code. The Windows identity is persisted in Windows Credential Manager; Android persists the approved public identity with an in-app revoke control.

## State machine

`IDLE → FILE_SELECTED → GESTURE_ARMED → TRANSFER_OFFERED → AWAITING_REMOTE_CONFIRMATION → ACCEPTED → TRANSFERRING → VERIFYING → COMPLETED`

The gesture states are optional: a button may move `FILE_SELECTED` directly to `TRANSFER_OFFERED`, and a button may accept an incoming offer. Active states may end in `FAILED` or `CANCELLED` according to explicit transition tests.
