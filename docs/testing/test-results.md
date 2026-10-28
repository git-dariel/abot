# Test Results

Observed on 2026-10-28:

| Command | Result | Scope |
|---|---|---|
| `pnpm test` | Pass: 3 tests, 0 failures | Protocol schema fixture, filename validation, and 512 KiB loopback streamed transfer |
| `pnpm typecheck` | Pass | Protocol package and Windows React UI |
| `pnpm build:windows-ui` | Pass | Vite production bundle; 31 modules transformed |
| `apps/android/gradlew.bat testDebugUnitTest assembleDebug --rerun-tasks --no-daemon` | Pass: 41 tasks | Forced Android unit test and debug APK assembly under `app.abot.android` |
| `apps/android/gradlew.bat lintDebug --rerun-tasks --no-daemon` | Pass: 26 tasks | Android debug source/resources lint |
| `cargo check --workspace --all-targets` | Pass | Rust libraries and Tauri native crate |
| `cargo clippy --workspace --all-targets --all-features -- -D warnings` | Pass | Rust libraries and Tauri native crate |
| `cargo test --workspace` | Pass: 18 tests | Streaming/finalization, AES-GCM authentication, identities, pairing, discovery, Windows metadata, reconnect derivation, encrypted multi-frame receive, and accepted/rejected TCP integration |
| `pnpm exec tauri build --debug` | Pass | Native executable plus NSIS and MSI debug bundles |
| Windows launch smoke | Pass | Process remained alive for three seconds, then was stopped by the test |
| REA `inspect-artifact` | Pass | Static APK and PE identity/container evidence |
| REA `inspect-android-package` | Unavailable | Required caller-supplied JADX MCP JAR is not configured |

The first typecheck run failed because the JavaScript test harness was included without Node typings. The configuration was corrected to typecheck the public source contract; the same tests continue to run through Node's test runner. The rerun passed.

The protocol-package synthetic test sent random bytes over loopback and enforced acceptance. The Rust/Tauri TCP integration test exercised the connected Windows receiver: it submitted an authenticated encrypted offer, confirmed the destination was empty before consent, accepted, sent an encrypted data frame, received the verified completion acknowledgement, and compared the finalized bytes. A separate 130 KB test crossed three encrypted chunks before verified finalization, and a receiver-key mismatch test was rejected. Android unit tests verify the signed invitation transcript/tamper behavior and legacy-link compatibility. These are component and same-machine data-plane tests, not signed-QR Android-to-Windows hardware evidence.

Not run for the signed-QR change: Android instrumentation tests, Android emulator/device visual inspection, native Windows UI capture, recorded cross-device LAN transfer, or hardware E2E. The project owner reported the earlier manual-link flow working on a phone and laptop, but exact device/OS, hashes, duration, and firewall observations were not captured. The Windows React UI was previously visually inspected from its local production-equivalent development rendering; native Computer Use capture was unavailable because its Windows pipe was not present.

## Trusted reconnect component validation - 2026-10-29

- `cargo test --workspace`: passed, including shared reconnect derivation and mDNS invitation chunk round trip.
- `cargo clippy --workspace --all-targets --all-features -- -D warnings`: passed.
- `pnpm typecheck` and `pnpm build:windows-ui`: passed.
- `apps/android/gradlew.bat testDebugUnitTest assembleDebug lintDebug`: passed, including the Kotlin side of the fixed cross-platform HMAC vector.
- Not run: Android-to-Windows mDNS discovery and `ABR2` reconnect on two physical devices. This remains a hardware acceptance task, not a claimed successful reconnect.
- Reconnect-readiness regression check: the connected phone had an empty ABOT trusted store. The updated APK was installed with `adb install -r`; UI automation confirmed **Find trusted PC** is disabled and the QR/trust/accepted-transfer prerequisite is visible. Full reconnect still requires the owner to select and approve a real file on both devices.

Generated debug artifacts:

- ABOT APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk` (13,843,303 bytes), SHA-256 `87C380A6BD8D76974C7EF8BEE209CAFD5956D2736E01A94D56A5AD911A96F0F0`.
- ABOT Windows executable: `target/debug/abot-windows.exe` (13,584,384 bytes), SHA-256 `7404CF3D74D375ECFFC9D184264CCF7ED07D5D5A318BDFF23CA3E4946876B7DB`.
- ABOT NSIS installer: `target/debug/bundle/nsis/ABOT_0.1.0_x64-setup.exe` (2,629,327 bytes), SHA-256 `FB21375FA63A6447095CD797FAFCADBF2C3C9BADE6A658A1DA4253960E91AC89`.
- ABOT MSI installer: `target/debug/bundle/msi/ABOT_0.1.0_x64_en-US.msi` (4,382,720 bytes), SHA-256 `DC428E26163FA2DABE9EEBFE37E37EE3A76AE6D84AA6523E93A6B0B14D6402F9`.
