# Progress

## Implemented

- Repository structure and project governance.
- Versioned protocol schema, TypeScript definitions, and compatibility fixtures.
- Rust streamed-copy, integrity, temporary-file, and safe-name core.
- Initial discovery, pairing, and security domain primitives.
- mDNS advertise/browse and manual local-address fallback.
- Ed25519 identity signatures and signed, expiring pairing QR payloads.
- Backend-owned Windows file selection with streamed SHA-256 metadata preparation.
- Native Windows debug executable plus NSIS/MSI bundles.
- Environment, research, architecture, testing, and release documentation.
- One-file Android-to-Windows private-LAN transport with a one-use pairing link, encrypted/authenticated control and data frames, explicit Windows acceptance, Android chunk streaming, and Windows verified atomic finalization.
- Identity-bound pairing UI: Windows Credential Manager-backed Ed25519 identity, signed/expiring QR rendering, local Android QR signature verification, six-digit comparison on both devices, explicit approval on both devices, Android public-identity trust/revocation, and receiver-key-bound offers.
- Android pairing UI uses a compact black-and-white, vertically scrollable two-step layout; scanned QR payloads are hidden from the form, manual link entry is opt-in, and system/keyboard insets remain scrollable.
- Android preserves the verified pairing link, comparison-code card, trust state, and status text across Activity recreation; QR scanning is orientation-locked to prevent camera-return resets.
- Trusted-device reconnect source path: a successful QR transfer provisions a per-phone credential protected by Android Keystore and Windows Credential Manager; Windows advertises signed invitation chunks through mDNS; Android verifies the trusted receiver key and sends an HMAC proof before deriving a fresh encrypted session.
- Product rebranded to ABOT (`abot.` visual mark and “Abot mo, salo ko.” tagline), including Android/Windows package metadata, adaptive Android icon/splash resources, desktop assets, and dark/light design tokens while preserving v1 wire compatibility identifiers.

## Verified

- `pnpm test`: 3 protocol/synthetic-transfer tests passed.
- `pnpm typecheck`: protocol and Windows TypeScript checks passed.
- `pnpm build:windows-ui`: Vite production build passed.
- `apps/android/gradlew.bat testDebugUnitTest assembleDebug`: Android unit test and debug APK build passed.
- `apps/android/gradlew.bat lintDebug`: Android lint passed.
- `cargo check --workspace --all-targets`: passed, including Tauri.
- `cargo clippy --workspace --all-targets --all-features -- -D warnings`: passed.
- `cargo test --workspace`: 18 tests passed, including encrypted multi-frame receive, accepted/rejected TCP integration, reconnect derivation, and discovery chunking.
- Native Windows executable stayed running during a three-second smoke test.
- REA 6.8.0 inspected the owned APK and Windows PE artifacts through its safe artifact provider.
- ABOT rebrand checks passed: Android manifest resolves to `app.abot.android`; Android forced unit/APK build and lint pass; Tauri produces `abot-windows.exe` plus ABOT NSIS/MSI bundles; the native process launch smoke and local browser visual inspection pass.
- Signed-pairing checks pass: Rust workspace tests/Clippy, Windows TypeScript/build, Android signed-invitation unit test/debug APK/lint, and source diff checks.
- Android scroll/contrast fix passes `testDebugUnitTest`, `assembleDebug`, and `lintDebug`.
- Android pairing-state lifecycle fix passes `testDebugUnitTest`, `assembleDebug`, and `lintDebug`.
- User-verified on a real Android device: QR scanning returns to the scrollable black-and-white screen with the comparison code and trust action remaining visible.
- Trusted reconnect component checks pass: Rust and Android share a fixed HMAC key/proof vector; Rust tests cover mDNS invitation chunking; Rust workspace tests/Clippy, Android unit/APK/lint, and Windows TypeScript/build pass.
- Trusted-reconnect readiness fix verified on the connected Android device: its empty trusted store explained the failed search; the updated UI disables discovery and shows the required QR/trust/accepted-transfer setup until Windows confirms credential persistence. Discovery now reports candidate resolution or verification failures instead of silently discarding them.
- User-reported observation: the earlier manual-link flow worked on a real phone and laptop. Exact device/OS details, hashes, duration, and firewall observations were not captured, so this is not a formal hardware acceptance result.

## Pending

- Complete a fresh accepted QR transfer on the connected phone to provision its currently empty trusted store, then validate real-device trusted reconnect and record hashes/environment details. Windows-to-Android transfer, Android asymmetric identity, replay cache, multi-file/cancellation/resume, gestures, and signed release artifacts remain pending.
