# AirBridge

**Move files naturally.**

AirBridge is an independently implemented, local-first file-transfer project for Android phones and Windows 10/11 PCs. Its target experience combines conventional, accessible transfer controls with optional camera-detected hand gestures. Gestures coordinate user intent; they do not move data or track a hand between devices.

## Current status

This repository now contains an initial Android-to-Windows transfer path in addition to its architecture and compiled-shell baseline. Windows creates a one-use 256-bit pairing link and owns the destination folder; Android sends an encrypted metadata offer, waits for explicit approval, then streams one file in authenticated 64 KiB chunks. Windows writes to a temporary file, verifies size and SHA-256, atomically finalizes it, and returns an encrypted completion acknowledgement. Real-device transfer, durable identity-bound pairing, reverse-direction transfer, and gesture recognition are **not yet verified or complete**.

Verified on the current development machine:

- Protocol/schema and synthetic loopback-transfer tests pass with Node.js; the Windows React UI typechecks and produces a Vite production bundle.
- Android unit tests and the debug APK build pass through the checked-in Gradle wrapper.
- Fifteen Rust/Tauri tests, including accepted and rejected encrypted TCP loopback transfers, and Clippy with warnings denied pass.
- The native Windows executable launches in a bounded smoke test, and debug NSIS/MSI bundles are generated.

See [the roadmap](docs/roadmap.md) and [environment report](docs/environment.md) for exact evidence.

## Repository layout

```text
apps/android/            Kotlin + Jetpack Compose client
apps/windows/            Tauri 2 + React desktop client
crates/transfer-core/    Streaming, integrity, and safe finalization
crates/discovery/        Untrusted LAN discovery records
crates/pairing/          Pairing state and consent contracts
crates/security/         Identity/session primitives
packages/protocol/       Versioned wire definitions and fixtures
docs/                    Architecture, research, setup, testing, releases
```

## Development

Prerequisites are documented in [Windows setup](docs/setup/windows.md) and [Android setup](docs/setup/android.md).

The checks currently available are:

```powershell
pnpm install
pnpm test
pnpm typecheck
cd apps/android
.\gradlew.bat testDebugUnitTest assembleDebug
```

When Rust is installed:

```powershell
cargo fmt --all --check
cargo clippy --workspace --all-targets --all-features -- -D warnings
cargo test --workspace
```

Run Rust commands from a Visual Studio Developer PowerShell/Command Prompt, or initialize that environment first. To build the native Windows application and debug bundles:

```powershell
cd apps/windows
pnpm exec tauri build --debug
```

These commands validate components, not the end-to-end acceptance criteria. Real Android-to-Windows and Windows-to-Android hardware tests remain pending.

## Try the Android → Windows development path

1. Launch the native Windows app and select **Choose folder and start receiver**.
2. Paste its one-time `airbridge://pair?...` link into the Android app on the same private LAN.
3. Select one Android document and offer it to Windows.
4. Review the name, size, and SHA-256 on Windows, then accept or reject it.

Windows Firewall may ask for private-network access. Do not enable public-network access. This is a development pairing flow: the high-entropy link must be handled like a one-time secret, and a fresh receiver session is required for each file.

## Security boundary

Discovery never grants trust. The implemented one-file development path authenticates and encrypts its offer, decision, chunks, and completion acknowledgement with a one-use 256-bit secret. A receiver must explicitly accept metadata before Android opens the document for upload. Incoming names and sizes are validated, data is streamed to a temporary destination, and finalization occurs only after size and SHA-256 verification. Binding this channel to persistent Ed25519 device identities, secure identity storage, revocation, and replay caches remain pending.

## License

[MIT](LICENSE)
