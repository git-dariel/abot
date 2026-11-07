# Test Results

## Mobile relaunch reconnect repair - 2026-11-07

- Root cause: the persistent Android foreground service and each recreated Activity independently started trusted NSD discovery. The Activity also restored an endpoint only in local Compose state, so the two owners could race and leave the UI using an expired Windows host/port after relaunch or receiver rotation.
- Fix: `TrustedReceiverService` is now the sole discovery/poll owner and publishes a process-local connection snapshot to the Activity. It coalesces scheduled work, prevents overlapping discovery, discards stale endpoints after recoverable failures, retries with a capped exponential backoff, and accepts an explicit reconnect action. The Windows receiver supervisor now starts during native Tauri setup and repeated start requests are idempotent.
- Passed: Android `testDebugUnitTest assembleDebug lintDebug` (13 unit tests); `cargo clippy --workspace --all-targets --all-features -- -D warnings`; `cargo test --workspace` (19 tests); Windows gesture tests (5), typecheck, and production build.
- Device validation: installed the 69,943,023-byte APK (SHA-256 `D843AE9F2299281F3236AC3CDC80351F02C3EDCBB012C592185DAEAA56429498`) with app data preserved on an Android 15/API 35 Infinix X6880. Three force-stop/relaunch cycles reconnected on the first observed UI poll. Background/foreground return retained the foreground service and connection. Wi-Fi off/on recovered to `PC connected`. Stopping Windows changed Android to its searching state; restarting Windows restored `PC connected`, with exactly one Android receiver service active.
- Boundary: these checks validated connection lifecycle and recovery. They did not send a file, exercise a locked-screen notification, or compare transfer hashes.

## Minimal lifecycle and bidirectional transfer slice - 2026-11-05

- Automatic receive lifecycle and white-first UI: Android trusted discovery retries without another QR scan; Windows re-arms after completed/rejected receives, hides pairing details until requested, and uses a completion toast. Android places QR scanning in the top-right icon action.
- Reverse direction: Windows selection is gesture-armed, Android authenticates through trusted reconnect, the phone must explicitly accept before bytes, and accepted encrypted chunks are written to a private temporary file, size/SHA-256 verified, then published to `Downloads/ABOT` using MediaStore pending finalization. Android returns an authenticated verified-completion frame.
- Passed: `cargo clippy --workspace --all-targets --all-features -- -D warnings`; `cargo test --workspace` (18 tests); four Windows gesture tests; TypeScript typecheck; Windows production UI build; Android unit tests, debug APK assembly, and lint.
- Device check: `adb install -r` succeeded on the authorized API 35 phone. The earlier automatic-receive APK screen rendered correctly; the final reverse APK installation succeeded.
- Not yet run: a physical Windows-to-Android file transfer, matching cross-device hashes, Windows camera gesture on the final reverse build, or Android background-process receiving. Computer Use native Windows capture was unavailable because its native pipe was absent.

## Gesture recognition reliability repair - 2026-11-05

- Phone log history showed `GestureCaptureActivity` remaining active for approximately 38 seconds without a crash; no application-level recognition telemetry existed. This supported a recognition/gating diagnosis rather than an Activity or CameraX crash diagnosis.
- Android now logs pose/confidence/armed state under `ABOT-Gesture`, tolerates slow inference frames, and provides visible/haptic state transitions. A new slow-frame unit test passes alongside the existing gesture tests.
- `apps/android/gradlew.bat testDebugUnitTest assembleDebug lintDebug`: passed. The installed debug APK is 69,942,615 bytes with SHA-256 `5BCFD17A724347758EE902C6D320E55BF8A91268B721E90084AF247D30886CAC`.
- Windows gesture tests: 4 passed, including a slow-frame release sequence. TypeScript typecheck and the Vite production build passed.
- The rebuilt Tauri development executable launched successfully. Not yet verified: physical palm/fist classification, offer arrival, Windows camera release, saved file, or matching cross-device hashes.

## ABOT Gallery gesture flow - 2026-11-05

- `apps/android/gradlew.bat testDebugUnitTest assembleDebug lintDebug`: passed after setting `ANDROID_HOME` to the installed SDK. The final debug APK is 69,942,615 bytes with SHA-256 `CD94BDE6922298019C34B0CFFD65B8A2AF262DEC4F9230A965BE9AED23D11275`.
- `pnpm --filter @abot/windows test`: 3 gesture-sequence tests passed.
- `pnpm --filter @abot/windows typecheck` and `pnpm --filter @abot/windows build`: passed; Vite transformed 34 modules.
- `adb install -r` succeeded on an authorized Android 15/API 35 Infinix X6880 without clearing ABOT data. Screenshots verified the updated home UI, safe gallery insets, trusted-PC search state, permission handling, and a MediaStore-backed thumbnail grid.
- Not run: a real palm-to-fist recognition, Windows camera release, encrypted cross-device transfer, matching hash comparison, minimized-window camera validation, or Windows-to-Android transfer. Source/build/device UI evidence must not be described as a successful gesture transfer.

## Downloads and gestures - 2026-11-05

- Windows gesture gate: 3 Node tests pass (ordered sustained poses, duplicate suppression, low confidence, multiple hands, interruptions, fresh session).
- Android gesture gate: 2 JUnit tests pass alongside existing tests; debug APK assembly and lint pass.
- Windows TypeScript checks and production UI build pass, with the model and WASM served from bundled local assets.
- Rust workspace tests (18) and Clippy pass. `pnpm --filter @abot/windows exec tauri build --debug --no-bundle` passes and builds `target/debug/abot-windows.exe` with local gesture assets.
- Gesture APK: 69,942,615 bytes; SHA-256 `89F4A90695E229FC692FD5B84E3CFB265EDF74292F1E3EF248EFDF49DC6B6812`. Six Android unit tests passed, including two gesture tests.
- Hardware limitation: ADB reports no connected phone for this run. Physical camera accuracy, native WebView camera permission, and an end-to-end gesture transfer are pending; use `docs/testing/gestures.md`.

Earlier artifact hashes below belong to the earlier builds, not the gesture build.

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

## Cross-platform frontend redesign - 2026-11-07

- Windows: `pnpm --filter @abot/windows test` passed all 5 gesture-sequence tests; `pnpm --filter @abot/windows typecheck` passed; `pnpm --filter @abot/windows build` passed with 39 transformed modules; `pnpm --filter @abot/windows exec tauri build --debug --no-bundle` produced the updated native executable; the executable remained alive for a three-second smoke test.
- Android: with `ANDROID_HOME` set to the installed SDK, `gradlew.bat testDebugUnitTest assembleDebug lintDebug --no-daemon` passed. The six test suites contain 13 tests with zero failures, the debug APK assembled, and lint completed successfully.
- Visual checks: Windows Overview was inspected from local renders at 1440px and 768px. Android Home, Activity, Settings, and the real MediaStore-backed Gallery were inspected on the authorized Android 15/API 35 device. No camera frames or gallery media left the device during inspection.
- Boundary: native Windows capture was unavailable because the Computer Use native pipe was absent. No new physical cross-device transfer or hash comparison was performed in this redesign pass, so the established hardware acceptance tasks remain pending.

## Multi-selection and manual discovery - 2026-11-08

- Android: `testDebugUnitTest assembleDebug lintDebug --no-daemon` passed with 15 tests across 7 suites. The 69,943,075-byte APK has SHA-256 `78DC44EA7AB60700B704D242D1575230B7DC60081B5CF462D2107A8657F2DC8B` and installed successfully with existing data preserved on the Android 15/API 35 Infinix X6880.
- Windows/Rust compatibility: five gesture tests, TypeScript typecheck, and Vite production build passed; all 19 Rust workspace tests and strict Clippy passed. No Windows protocol change was required because trusted receiver sessions already accept consecutive authenticated uploads and re-arm consent between completions.
- Device lifecycle: after force-stop and cold launch, the UI showed discovery OFF, `dumpsys activity services app.abot.android` showed no service, and there was no active ABOT receiver notification. Turning ON created one `connectedDevice` foreground service and found the trusted PC. Turning OFF removed the service and active receiver notification. Force-stop left no service.
- Device selection: ABOT Gallery selected two permission-scoped media items, displayed `2 selected`, mixed-media guidance, and Continue; Continue opened `Grab 2 files` with both names and sizes. Android DocumentsUI multi-select also returned two files and opened the same Grab page automatically.
- Logo validation: the three SVG concepts and comparison board rendered successfully in headless Edge. Existing Android and Windows production icon resources were not modified.
- Boundary: no physical palm-to-fist batch was completed, no two Windows acceptance gestures were performed, and no cross-device hashes were compared. These remain hardware acceptance tasks rather than claimed transfer success.
