# Test Results

Observed on 2026-10-28:

| Command | Result | Scope |
|---|---|---|
| `pnpm test` | Pass: 3 tests, 0 failures | Protocol schema fixture, filename validation, and 512 KiB loopback streamed transfer |
| `pnpm typecheck` | Pass | Protocol package and Windows React UI |
| `pnpm build:windows-ui` | Pass | Vite production bundle; 31 modules transformed |
| `apps/android/gradlew.bat testDebugUnitTest lintDebug assembleDebug` | Pass: 51 tasks | Android unit test, lint, and debug APK assembly |
| `apps/android/gradlew.bat lintDebug` | Pass | Android debug source/resources lint |
| `cargo check --workspace --all-targets` | Pass | Rust libraries and Tauri native crate |
| `cargo clippy --workspace --all-targets --all-features -- -D warnings` | Pass | Rust libraries and Tauri native crate |
| `cargo test --workspace` | Pass: 15 tests | Streaming/finalization, AES-GCM authentication, identities, pairing, discovery, Windows metadata, encrypted multi-frame receive, and accepted/rejected TCP integration |
| `pnpm exec tauri build --debug` | Pass | Native executable plus NSIS and MSI debug bundles |
| Windows launch smoke | Pass | Process remained alive for three seconds, then was stopped by the test |
| REA `inspect-artifact` | Pass | Static APK and PE identity/container evidence |
| REA `inspect-android-package` | Unavailable | Required caller-supplied JADX MCP JAR is not configured |

The first typecheck run failed because the JavaScript test harness was included without Node typings. The configuration was corrected to typecheck the public source contract; the same tests continue to run through Node's test runner. The rerun passed.

The protocol-package synthetic test sent random bytes over loopback and enforced acceptance. The Rust/Tauri TCP integration test exercised the connected Windows receiver: it submitted an authenticated encrypted offer, confirmed the destination was empty before consent, accepted, sent an encrypted data frame, received the verified completion acknowledgement, and compared the finalized bytes. A separate 130 KB test crossed three encrypted chunks before verified finalization. These are same-machine data-plane tests, not Android-to-Windows hardware evidence.

Not run: Android instrumentation tests, UI visual inspection, emulator tests, cross-device LAN transfer, or hardware E2E. The UIs were build/launch-smoke verified but not visually inspected.

Generated debug artifacts:

- APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk` (9,492,587 bytes), SHA-256 `844F51B69B6D49A7B429BA5A74D565AAE249D2FB4EF6364E0C67FA5ACE286F90`.
- Windows executable: `target/debug/airbridge-windows.exe` (13,597,696 bytes), SHA-256 `A12D685B351669357256C96E1455DFFD9368DCCE757F61C65B4A32429CCA217B`.
- NSIS installer: `target/debug/bundle/nsis/AirBridge_0.1.0_x64-setup.exe` (2,632,229 bytes), SHA-256 `C2B66C294262CC1C55A284F065797950E210507B76CE5F8CE686AF1F11C4E689`.
- MSI installer: `target/debug/bundle/msi/AirBridge_0.1.0_x64_en-US.msi` (4,386,816 bytes), SHA-256 `D35E83AD3B635D954DF46E76B2076355D1458DF28C20D8EA7A1D6720F2B37BD7`.
