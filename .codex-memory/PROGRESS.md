# Progress

## Implemented

- Minimal automatic receive lifecycle: Android automatically rediscovers its provisioned trusted PC after transfers and does not require another QR scan. Windows automatically re-arms its Downloads receiver after completion or rejection, hides new-device QR details by default, removes the receive-another-file action, and reports successful saves with a transient toast. Android and Windows primary screens now use a reduced white-first layout; Android scanning is a top-right icon action.
- Gesture reliability/feedback repair: Android and Windows now tolerate up to three seconds between slow inference frames, accept stable supported poses at 55% confidence after 350 ms, and retain the single-hand/ordered/one-shot safety gates. Android shows live hand/pose confidence, uses a larger framing preview and haptic stage confirmation, then shows explicit grabbed, offer-reached-Windows, and failure messages. Windows shows live fist confidence and a clear camera-ready/locked state.
- ABOT Gallery fast path: user-granted MediaStore photo grid, automatic trusted-PC discovery on gallery entry, current-photo preview with an automatically started local palm-to-fist recognizer, and the existing encrypted offer pipeline. Windows starts its Downloads receiver on app launch and starts the offer-bound fist-to-palm acceptance camera when a trusted offer appears. The conventional picker/button path remains available.
- Windows Downloads default and optional local MediaPipe gesture controls: Android palm-to-fist offers a selected file; Windows fist-to-palm accepts a reviewed, identity-approved offer. Models are bundled, and stable-pose gates prevent repeated or brief gestures.

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

- Automatic receive/minimal UI update: Windows gesture tests, TypeScript typecheck, and production build passed; Android unit tests, debug APK assembly, and lint passed. The APK installed successfully on the authorized API 35 phone, and a device screenshot confirmed the white home screen and top-right QR action. The user confirmed the Android-to-Windows gesture flow works on their phone and laptop before this lifecycle/UI refinement.
- Gesture reliability repair: Android unit tests (including slow-frame sequencing), debug APK assembly, and lint passed; Windows four-test gesture suite, TypeScript typecheck, and production build passed. The repaired APK (SHA-256 `5BCFD17A724347758EE902C6D320E55BF8A91268B721E90084AF247D30886CAC`) installed successfully on the authorized API 35 phone, and the rebuilt Tauri development executable launched. Physical hand recognition and cross-device completion remain unverified.
- On 2026-11-05, Android unit tests, debug APK assembly, and lint passed; Windows gesture tests, TypeScript typecheck, and production UI build passed. The final APK was installed with `adb install -r` on an authorized API 35 Infinix X6880, preserving app data. Real-device screenshots verified the updated home screen, permission-aware ABOT Gallery, trusted-PC discovery status, safe insets, and MediaStore thumbnail grid. No hand-camera or cross-device transfer was performed in this validation.
- Gesture sequence tests pass on Kotlin and JavaScript; Android unit/APK/lint and Windows TypeScript/UI build pass. Physical-camera recognition and gesture-triggered transfers remain unverified.
- User confirmed conventional file transfer and trusted-PC reconnect work on their devices.

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

- Run the complete ABOT Gallery gesture path on hardware: keep Windows ABOT open or minimized, open a phone photo, palm-to-fist offer, fist-to-palm accept at the PC, then record matching file hashes. Windows-to-Android transfer and true close-to-tray/background receiver lifecycle are not implemented.
- Real-camera hardware validation of the Downloads/gesture slice; see `docs/testing/gestures.md`.

- Record hashes/environment details for the user-reported successful transfer and reconnect. Windows-to-Android transfer, Android asymmetric identity, replay cache, multi-file/cancellation/resume, gesture hardware acceptance, and signed release artifacts remain pending.
