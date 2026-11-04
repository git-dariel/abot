# ABOT Repository Instructions

Follow the user-provided Codex general instructions. In addition:

- Keep ABOT local-first; do not introduce a required cloud service or account.
- A gesture only arms or confirms a transfer. Never imply that a hand is tracked between cameras.
- Build the conventional, explicitly accepted file-transfer path before gesture controls.
- Never transmit file bytes until the receiving user accepts the offer.
- Stream files; do not buffer entire files in memory.
- Write incoming data to a temporary file, verify size and SHA-256, then atomically finalize it.
- Treat discovery advertisements as untrusted. Pairing, not discovery, establishes identity.
- Keep camera frames and landmarks on-device by default.
- Record implemented, tested, and pending behavior separately in `docs/roadmap.md` and `.codex-memory/PROGRESS.md`.
- Never describe a scaffold, synthetic test, or animation as a successful cross-device transfer.
- Use Rust workspace conventions for `crates/`, Kotlin/Compose conventions for `apps/android/`, and React/Tauri conventions for `apps/windows/`.

Before completing a change, run the narrowest relevant checks that the installed toolchains permit and report unavailable checks exactly.


## Token-Efficient Memory Workflow

- Treat Obsidian as persistent project memory.
- Never load the entire Obsidian vault.
- Search for relevant memory files before reading.
- Read at most 3 relevant notes initially.
- Prefer summaries over full session histories.
- Inspect only repository files related to the task.
- Avoid repeating completed research.
- Do not reread unchanged files unnecessarily.
- Update memory with concise implementation summaries.
- Never skip required tests or security checks to save tokens.
