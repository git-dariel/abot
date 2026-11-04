# Roadmap

## Milestone 0 — Environment and REA

- Implemented: host/tool inventory and REA report.
- Tested: Node/Rust/MSVC versions, healthy REA Codex registration, CLI catalog/schema, and owned-artifact static inspection.
- Pending: restart Codex for MCP exposure; optional analysis providers only when a scoped investigation requires them.

## Milestone 1 — Feasibility and architecture

- Implemented: public-source competitor notes, system design, protocol v1 schema, threat model, test plan.
- Tested: protocol fixtures, portable filename checks, and a 512 KiB accepted/verified loopback transfer passed; protocol and Windows TypeScript checks passed; Windows UI production bundle built.
- Pending: architecture validation through compiled peers.

## Milestone 2 — Compiled shells

- Implemented: initial React/Tauri and Android Compose source shells with real local file selection UI and explicit disabled transport state.
- Tested: Windows React production bundle and Android unit/debug APK build.
- Tested: native Tauri build, three-second Windows launch smoke, and Android debug build.
- Pending: interactive visual inspection and Android emulator/device launch.

## Milestone 3 — Discovery and pairing

- Implemented: mDNS advertisement/browse primitives, manual local endpoint fallback, a Windows Ed25519 identity stored in Windows Credential Manager, signed five-minute QR invitations, on-device Android signature verification/scanning, matching comparison codes, explicit approval on both screens, Android public-identity trust/revocation, and receiver-key binding in the encrypted offer.
- Tested: Rust tests cover invalid/public endpoints, invitation round trip, tampering, expiry, signatures, and consent ordering; Android unit tests verify a signed cross-language-format invitation and reject tampering; platform builds and lint pass.
- Implemented UI fix: the Android pairing flow is now a compact black-and-white, vertically scrollable two-step screen. The scanned payload is hidden after verification, while manual entry remains an opt-in fallback.
- Implemented lifecycle fix: verified QR and trust UI state survives Activity recreation when returning from the orientation-locked camera scanner.
- Tested UI fix: Android unit tests, debug APK assembly, and lint pass.
- User-verified: on a real Android device, QR scanning returns to the scrollable high-contrast screen with the comparison code and trust action still visible.
- Implemented trusted reconnect: successful QR transfer provisions a Keystore/Credential-Manager protected per-phone credential; Windows publishes signed invitation chunks through mDNS; Android verifies the stored receiver identity, proves credential possession, and derives a fresh encrypted session without rescanning.
- Tested trusted reconnect: fixed Kotlin/Rust HMAC vectors match; invitation TXT chunk round trip, Rust workspace tests/Clippy, Android unit/APK/lint, and Windows TypeScript/build pass.
- Implemented provisioning-state fix: Android enables trusted discovery only after an accepted QR transfer and an authenticated Windows completion confirms credential persistence; rejected discovery candidates now produce a diagnostic status instead of a generic timeout.
- Device observation: the connected phone's ABOT trusted store was empty, so its reported search could not authenticate any laptop. The updated APK visibly explains and enforces the missing setup step.
- Pending: real-device trusted reconnect and recorded file-transfer validation with matching hashes/environment details, Android asymmetric identity, and replay cache.

## Milestone 4 — Android to Windows transfer

- Implemented: one-use manual pairing link, AES-256-GCM encrypted/authenticated offer-decision-data-completion frames, explicit Windows acceptance, Android 64 KiB streaming, Windows temporary-file receive, byte-count/SHA-256 verification, atomic finalization, and completion acknowledgement.
- Tested: Rust encrypted-frame authentication; 130 KB multi-frame decrypt/finalize test; TCP integration tests proving rejection creates no file and the destination remains empty before consent but contains verified bytes after acceptance; Rust/Clippy, React typecheck/build, protocol tests, and Android unit/lint/APK checks.
- Observed: the project owner reported the earlier manual-link Android-to-Windows flow working on a real phone and laptop; device/OS, file size, matching hashes, duration, and firewall behavior were not recorded, so this is not yet a formal acceptance record.
- Pending: a recorded signed-QR hardware run, UI visual inspection, Android mutual identity, replay cache, cancellation/resume, multi-file offers, and progress reporting on Windows.

## Gesture and Downloads slice

- Implemented: automatic Windows Downloads destination; Android palm-to-fist offer and Windows fist-to-palm acceptance using local MediaPipe, single-hand confidence/hold checks, one-shot sequences, and offer-bound decisions. ABOT Gallery now shows user-granted phone photos, automatically searches for a trusted PC, previews the opened photo with an automatically started gesture camera, and feeds the result into the existing encrypted offer path. Windows now starts its receiver on app launch and starts the acceptance camera for an approved pending offer.
- Reliability update: both gesture gates tolerate slow on-device inference, use a 55% supported-pose threshold with a 350 ms stable hold, and expose live pose/confidence state. Android adds haptic palm/grab confirmation plus explicit offer-ready and connection-failure messages; the ordered single-hand sequence and receiver consent boundary remain enforced.
- Tested: see `docs/testing/test-results.md` for current build and sequence-test results.
- Device-checked: the final APK was installed on an authorized API 35 phone; the updated home screen, permission-aware gallery, discovery state, insets, and real MediaStore thumbnail grid rendered correctly.
- User-reported: conventional file transfer and trusted-PC reconnect work on the user's devices.
- Pending: real-camera ABOT Gallery transfer, Windows minimized-camera behavior, camera permission/lifecycle scenarios, and recorded file hashes/environment details from `docs/testing/gestures.md`. Windows-to-Android transfer and close-to-tray receiver lifecycle remain later milestones. Another vendor's Gallery cannot supply its current photo to ABOT under normal Android application privileges.

## Milestones 5–10

Reverse-direction transfer, mutual durable pairing, gestures, polished UI, reliability testing, and signed releases remain pending. The reported manual-link hardware success still needs the test evidence required by the acceptance plan.

## Branding milestone — ABOT

- Implemented: ABOT/`abot.` product identity, “Abot mo, salo ko.” tagline, `app.abot.android` Android identity and Kotlin namespace, `app.abot.desktop` Tauri identity, ABOT Rust/npm package names, `abot://` generated pairing links, desktop and Android icon/splash assets, and shared dark/light design tokens.
- Tested: full Rust/Clippy suite, protocol tests, TypeScript checks, Windows UI production build, Android forced unit/APK build and lint, Tauri EXE/NSIS/MSI packaging, three-second native launch smoke, manifest inspection confirming `app.abot.android`, icon rendering, and local browser visual inspection.
- Compatibility: v1 `AIRBRIDGE-*` wire domains, `_airbridge._tcp.local.` discovery, `airbridge.local` schema IDs, and legacy `airbridge://` input remain deliberately supported. Old platform application IDs do not upgrade in place.
- Pending: native UI capture was unavailable because the Computer Use Windows pipe was not present; Android emulator/device visual inspection and cross-device hardware validation remain pending.
