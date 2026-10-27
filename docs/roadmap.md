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

- Implemented: mDNS advertisement/browse, manual local endpoint fallback, Ed25519 identities, signed expiring QR invitations, comparison codes, and explicit mutual-consent state machine.
- Tested: unit tests cover invalid/public endpoints, invitation round trip, tampering, expiry, signatures, and consent ordering.
- Pending: platform UI integration, secure identity storage, trust revocation, and an encrypted transport-bound handshake.

## Milestone 4 — Android to Windows transfer

- Implemented: one-use manual pairing link, AES-256-GCM encrypted/authenticated offer-decision-data-completion frames, explicit Windows acceptance, Android 64 KiB streaming, Windows temporary-file receive, byte-count/SHA-256 verification, atomic finalization, and completion acknowledgement.
- Tested: Rust encrypted-frame authentication; 130 KB multi-frame decrypt/finalize test; TCP integration tests proving rejection creates no file and the destination remains empty before consent but contains verified bytes after acceptance; Rust/Clippy, React typecheck/build, protocol tests, and Android unit/lint/APK checks.
- Pending: real Android-to-Windows hardware run, UI visual inspection, Windows Firewall observation, identity-bound handshake, secure identity persistence, replay cache, cancellation/resume, multi-file offers, and progress reporting on Windows.

## Milestones 5–10

Reverse-direction transfer, durable pairing, gestures, polished UI, reliability testing, and signed releases remain pending. No real cross-device acceptance criterion has passed yet.
