# Implementation Plan

1. Establish reproducible environment evidence and REA availability.
2. Define the protocol, transfer state machine, threat model, and platform boundaries.
3. Compile the Windows and Android application shells.
4. Implement authenticated pairing and untrusted LAN discovery with manual fallback.
5. Connect both applications to streamed, explicitly accepted, verified transfer in both directions.
6. Add independent on-device gesture classification and deterministic gesture state transitions.
7. Harden retries, resume checkpoints, cancellation, permissions, accessibility, and failure UX.
8. Run synthetic, emulator, desktop, and real-device acceptance tests; produce installable artifacts only when verified.

The data plane precedes gestures. Each milestone updates `docs/roadmap.md` with implemented, tested, and pending behavior.
