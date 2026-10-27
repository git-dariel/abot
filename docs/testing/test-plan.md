# Test Plan

## Component gates

- Protocol: schema fixtures, unknown-major rejection, malformed IDs, unsafe metadata.
- Transfer core: empty/large streamed inputs, checksum mismatch, size mismatch, existing destination, special characters, partial cleanup, cancellation.
- Pairing/security: mutual consent, expiry, rejection, replay, invalid transcript/code, revoked trust.
- Gesture classifier: normalized geometry, smoothing, cooldown, disappearance, and false-positive sequences.

## Integration gates

- Synthetic peers for both transfer directions, forced disconnect, corrupt stream, cancellation, resume, and duplicate offer.
- Windows app with local loopback peer and filesystem permission failures.
- Android emulator with content URIs, denied permissions, rotation, process recreation, and background limits.
- Real Android and Windows hardware on normal LAN, AP-isolated LAN, slow network, sleep/lock, and camera unavailable.

## Acceptance evidence

Record tool versions, device/OS identifiers without personal data, exact commands, input sizes, matching hashes, duration, and observed failures. Never infer hardware acceptance from component tests.
