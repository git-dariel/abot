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

- Implemented reconnect lifecycle fix: Android trusted-device discovery and endpoint ownership now live only in the `connectedDevice` foreground service. Activity relaunches consume the service's current connection snapshot instead of starting a competing NSD session, stale endpoints are discarded after network/protocol failures, retries use a capped exponential backoff, and the UI can request a clean reconnect without clearing app data. Windows starts its receiver supervisor during native application setup, independently of React startup, and repeated start requests are idempotent.
- Tested reconnect lifecycle fix: 13 Android unit tests, debug APK assembly, lint, strict workspace Clippy, all 19 Rust tests, five Windows gesture tests, Windows typecheck, and the production UI build pass. With the existing trusted credential preserved on an Android 15/API 35 Infinix X6880, three force-stop/relaunch cycles, background/foreground return, Wi-Fi off/on, and a Windows receiver restart all recovered to `PC connected`; one Android receiver service remained active. A file was not transferred during these lifecycle checks.
- Implemented lifecycle/UI update: after the first accepted QR transfer, Android keeps using the provisioned trusted credential and retries discovery automatically; Windows re-arms the Downloads receiver after each completion or rejection. The QR is hidden behind an explicit pair-new-phone action, the obsolete receive-another-file action is removed, and successful receives use a transient toast. Both primary screens now use a reduced white-first layout, with QR scanning in the Android top-right action.
- Tested lifecycle/UI update: Windows gesture tests, TypeScript typecheck, and production UI build pass; Android unit tests, debug APK assembly, and lint pass. The updated APK installed on the authorized API 35 device and its white home screen/top-right scanner rendered correctly.
- Implemented: automatic Windows Downloads destination; Android palm-to-fist offer and Windows fist-to-palm acceptance using local MediaPipe, single-hand confidence/hold checks, one-shot sequences, and offer-bound decisions. ABOT Gallery now shows user-granted phone photos, automatically searches for a trusted PC, previews the opened photo with an automatically started gesture camera, and feeds the result into the existing encrypted offer path. Windows now starts its receiver on app launch and starts the acceptance camera for an approved pending offer.
- Reliability update: both gesture gates tolerate slow on-device inference, use a 55% supported-pose threshold with a 350 ms stable hold, and expose live pose/confidence state. Android adds haptic palm/grab confirmation plus explicit offer-ready and connection-failure messages; the ordered single-hand sequence and receiver consent boundary remain enforced.
- Tested: see `docs/testing/test-results.md` for current build and sequence-test results.
- Device-checked: the final APK was installed on an authorized API 35 phone; the updated home screen, permission-aware gallery, discovery state, insets, and real MediaStore thumbnail grid rendered correctly.
- User-reported: conventional file transfer and trusted-PC reconnect work on the user's devices.
- User-verified: Android-to-Windows gallery gesture transfer works on the user's phone and laptop.
- Implemented reverse-direction first slice: Windows can select one file and arm it with the local fist-to-palm gesture (or an explicit fallback button). A provisioned Android 10+ phone polls the authenticated trusted session, displays the incoming offer, and sends its explicit decision before any file bytes. Accepted bytes are streamed into an app-private temporary file, checked against the declared size and SHA-256, then published through Android's pending MediaStore flow to `Downloads/ABOT`. Windows reports Android's authenticated verified-completion acknowledgement.
- Tested reverse-direction slice: Rust workspace tests and strict Clippy, Windows gesture tests/typecheck/production build, and Android unit/APK/lint pass. The APK installed successfully on the authorized API 35 phone. Native Windows visual capture was unavailable because the Computer Use native pipe was absent; a real laptop-to-phone transfer and matching hardware hashes are still pending.
- Pending: physical laptop-to-phone acceptance, Windows minimized-camera behavior, Android background/foreground-service receiving, camera permission/lifecycle scenarios, and recorded file hashes/environment details from `docs/testing/gestures.md`. Another vendor's Gallery cannot supply its current photo to ABOT under normal Android application privileges.

## Milestones 5–10

Reverse-direction hardware acceptance, mutual durable pairing, close-to-tray/background lifecycle, reliability testing, and signed releases remain pending. The reported hardware success still needs the test evidence required by the acceptance plan.

## Branding milestone — ABOT

- Implemented: ABOT/`abot.` product identity, “Abot mo, salo ko.” tagline, `app.abot.android` Android identity and Kotlin namespace, `app.abot.desktop` Tauri identity, ABOT Rust/npm package names, `abot://` generated pairing links, desktop and Android icon/splash assets, and shared dark/light design tokens.
- Tested: full Rust/Clippy suite, protocol tests, TypeScript checks, Windows UI production build, Android forced unit/APK build and lint, Tauri EXE/NSIS/MSI packaging, three-second native launch smoke, manifest inspection confirming `app.abot.android`, icon rendering, and local browser visual inspection.
- Compatibility: v1 `AIRBRIDGE-*` wire domains, `_airbridge._tcp.local.` discovery, `airbridge.local` schema IDs, and legacy `airbridge://` input remain deliberately supported. Old platform application IDs do not upgrade in place.
- Pending: native UI capture was unavailable because the Computer Use Windows pipe was not present; Android emulator/device visual inspection and cross-device hardware validation remain pending.
