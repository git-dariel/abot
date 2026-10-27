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
- Pending: Android private identity and mutual authenticated key agreement, tamper-resistant Android trust metadata, replay cache, discovery UI integration, and signed-QR real-hardware validation.

## Milestone 4 — Android to Windows transfer

- Implemented: one-use manual pairing link, AES-256-GCM encrypted/authenticated offer-decision-data-completion frames, explicit Windows acceptance, Android 64 KiB streaming, Windows temporary-file receive, byte-count/SHA-256 verification, atomic finalization, and completion acknowledgement.
- Tested: Rust encrypted-frame authentication; 130 KB multi-frame decrypt/finalize test; TCP integration tests proving rejection creates no file and the destination remains empty before consent but contains verified bytes after acceptance; Rust/Clippy, React typecheck/build, protocol tests, and Android unit/lint/APK checks.
- Observed: the project owner reported the earlier manual-link Android-to-Windows flow working on a real phone and laptop; device/OS, file size, matching hashes, duration, and firewall behavior were not recorded, so this is not yet a formal acceptance record.
- Pending: a recorded signed-QR hardware run, UI visual inspection, Android mutual identity, replay cache, cancellation/resume, multi-file offers, and progress reporting on Windows.

## Milestones 5–10

Reverse-direction transfer, mutual durable pairing, gestures, polished UI, reliability testing, and signed releases remain pending. The reported manual-link hardware success still needs the test evidence required by the acceptance plan.

## Branding milestone — ABOT

- Implemented: ABOT/`abot.` product identity, “Abot mo, salo ko.” tagline, `app.abot.android` Android identity and Kotlin namespace, `app.abot.desktop` Tauri identity, ABOT Rust/npm package names, `abot://` generated pairing links, desktop and Android icon/splash assets, and shared dark/light design tokens.
- Tested: full Rust/Clippy suite, protocol tests, TypeScript checks, Windows UI production build, Android forced unit/APK build and lint, Tauri EXE/NSIS/MSI packaging, three-second native launch smoke, manifest inspection confirming `app.abot.android`, icon rendering, and local browser visual inspection.
- Compatibility: v1 `AIRBRIDGE-*` wire domains, `_airbridge._tcp.local.` discovery, `airbridge.local` schema IDs, and legacy `airbridge://` input remain deliberately supported. Old platform application IDs do not upgrade in place.
- Pending: native UI capture was unavailable because the Computer Use Windows pipe was not present; Android emulator/device visual inspection and cross-device hardware validation remain pending.
