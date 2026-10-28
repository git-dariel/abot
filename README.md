# ABOT

**Abot mo, salo ko.**

Gesture-powered, local-first file sharing between Android phones and Windows computers. ABOT combines conventional, accessible transfer controls with optional camera-detected hand gestures. Gestures coordinate user intent; they do not move data or track a hand between devices.

## Current status

This repository contains an identity-bound Android-to-Windows transfer path. Windows stores a persistent Ed25519 identity in Windows Credential Manager and displays a signed, five-minute QR invitation. Android verifies it locally, shows the same six-digit comparison code, and stores the approved public identity for recognition or revocation. A successful QR transfer also provisions a per-phone reconnect credential protected by Android Keystore and Windows Credential Manager. Later sessions can find that trusted PC through mDNS, verify its fresh signed invitation, and derive a new authenticated session without rescanning. Files still move only after explicit Windows acceptance. Full Android asymmetric identity, reverse-direction transfer, replay caching, and gestures are **not yet complete**.

Verified on the current development machine:

- Protocol/schema and synthetic loopback-transfer tests pass with Node.js; the Windows React UI typechecks and produces a Vite production bundle.
- Android unit tests and the debug APK build pass through the checked-in Gradle wrapper.
- Eighteen Rust/Tauri tests, including accepted/rejected encrypted TCP loopback transfers, receiver-identity mismatch rejection, discovery payload chunking, and cross-platform reconnect derivation, and Clippy with warnings denied pass.
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

These commands validate components, not the complete end-to-end acceptance criteria. The earlier manual-link flow was reported working by the project owner on a phone and laptop; the new signed-QR flow still needs a recorded hardware run with matching hashes.

## Try the Android → Windows development path

1. Launch the native Windows app and select **Choose folder and start receiver**.
2. Tap **Scan QR** in Android, scan the Windows QR, and verify that both screens show the same six-digit code. The manual signed link remains available under the Windows disclosure.
3. Approve the matching code on both devices, then select one Android document and offer it to Windows.
4. Review the name, size, and SHA-256 on Windows, then accept or reject it. File bytes do not leave Android until acceptance.

After one successful QR transfer, start a fresh Windows receiver and use **Find trusted PC** on Android. Discovery is only a transport hint: Android still verifies the signed invitation and matches the stored Windows identity before reconnecting.

Windows Firewall may ask for private-network access. Do not enable public-network access. The invitation expires after five minutes and a fresh receiver session is required for each file.

## Security boundary

Discovery never grants trust. For first pairing, the signed QR invitation binds the private-LAN endpoint and one-use AES-256-GCM secret to the persistent Windows Ed25519 public key. For repeat sessions, the signed discovery invitation supplies a fresh challenge; Android requires the previously approved Windows identity and proves possession of a per-phone reconnect credential before both peers derive the session key. Reconnect credentials are encrypted with Android Keystore and stored in Windows Credential Manager. A receiver must explicitly accept metadata before Android opens the document. Full Android asymmetric identity and replay caches remain pending.

## License

[MIT](LICENSE)
