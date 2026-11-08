# Progress

## Implemented

- Reopen/reconnect lifecycle repair: the Android foreground service is the single owner of trusted NSD discovery, polling, retry scheduling, and the current restorable endpoint. Recreated Activities observe a connection snapshot instead of launching duplicate discovery, clear stale service-sourced endpoints, and can request a clean reconnect. Recoverable network/protocol failures rediscover with a 1-to-15-second capped exponential backoff. Windows now starts its persistent receiver supervisor during native application setup rather than relying on a React effect.
- Windows-to-Android trusted transfer first slice: a Windows-selected file is armed by the local fist-to-palm gesture or fallback action; Android polls through the existing trusted reconnect session, requires explicit phone acceptance before bytes, streams encrypted chunks to an app-private temporary file, verifies size and SHA-256, and publishes the verified result to `Downloads/ABOT` through MediaStore pending finalization. Windows waits for the authenticated Android completion and shows a toast.
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

- Reconnect lifecycle validation on 2026-11-07: Android unit tests (13), debug APK assembly, and lint passed; workspace strict Clippy and all 19 Rust tests passed; five Windows gesture tests, TypeScript typecheck, and production build passed. The updated APK installed with existing data preserved on the authorized Android 15/API 35 Infinix X6880. Three force-stop/relaunch cycles, background/foreground return, Wi-Fi off/on, and Windows receiver restart each returned to `PC connected`; the service dump showed exactly one foreground `TrustedReceiverService`. This validates connection recovery, not a file transfer.
- Reverse-direction component validation: strict Rust workspace Clippy and all 18 Rust tests passed; four Windows gesture tests, TypeScript typecheck, and production UI build passed; Android unit/APK/lint passed. The updated APK installed on the authorized API 35 device. A physical Windows-to-Android transfer is not yet verified.
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

- Run and record the complete reverse hardware path: choose a Windows file, palm-to-fist release, accept on Android, confirm `Downloads/ABOT`, and compare hashes. Android background readiness now uses a foreground service; true Windows close-to-tray lifecycle and locked-phone notification acceptance still require hardware acceptance.
- Real-camera hardware validation of the Downloads/gesture slice; see `docs/testing/gestures.md`.

- Record hashes/environment details for the user-reported successful transfer and reconnect. Windows-to-Android hardware acceptance, Android asymmetric identity, replay cache, batch cancellation/resume, gesture hardware acceptance, and signed release artifacts remain pending.

## Cross-platform frontend redesign - 2026-11-07

### Implemented

- Windows and Android now share the exact paper-white (`#FFFFFF`), ink-black (`#050505`), and cornflower-blue (`#548CFF`) visual system, typography hierarchy, borders, status treatments, and action language. Windows provides responsive Overview, Activity, and Settings navigation plus redesigned receiver, sender, pairing, progress, error, empty, and notification states. Android provides responsive Home, Activity, Settings, Gallery, outgoing gesture, and incoming gesture screens with real connection/transfer state and Android-appropriate navigation. No transfer protocol or consent boundary changed.

### Verified

- Five Windows gesture tests, TypeScript typecheck, Vite production build, native Tauri debug build, and a three-second native process smoke passed. Thirteen Android unit tests, debug APK assembly, and lint passed after configuring the installed SDK through `ANDROID_HOME`. Browser renders were inspected at desktop/tablet widths; Android Home, Activity, Settings, and Gallery were inspected on the authorized API 35 device.

### Pending

- Run one complete phone-to-Windows and one complete Windows-to-phone transfer on the redesigned screens and record hashes. Native Windows visual capture remains unavailable because the Computer Use native pipe is absent; connected-device UI checks are not substitutes for hardware transfer acceptance.

## Multi-selection, discovery control, and logo concepts - 2026-11-08

### Implemented

- Android native picker and ABOT Gallery mixed multi-selection, preserved URI/name/MIME/extension/size metadata, deduplication, automatic Grab navigation, selected-item summaries, aggregate batch progress, and per-file success/failure handling over consecutive existing encrypted offers.
- Session-only Windows Receiver Discovery toggle. Fresh launch is OFF; ON starts the non-sticky connected-device service; OFF cancels timers, retries, NSD, multicast resources, and the foreground notification without aborting a pending transfer.
- Three approval-stage editable SVG logo concepts and a comparison board. Production icons were not changed.

### Verified

- Android 15 unit tests, debug APK assembly, and lint; five Windows gesture tests, TypeScript typecheck, and production build; all 19 Rust tests and strict Clippy. On the authorized Android 15/API 35 Infinix X6880, fresh launch showed discovery OFF with no service, ON created one receiver service and found the trusted PC, OFF removed it, both Gallery and native picker selected two items, and both opened a two-file Grab page with names and sizes.

### Pending

- Perform a physical palm-to-fist two-file send, accept both offers on Windows, and compare both hashes. Logo integration waits for explicit concept approval.

## Bridge Pulse production identity - 2026-11-08

### Implemented

- The owner approved Concept A, Bridge Pulse. Its opposing-device mark and protected connection point now replace the previous Android adaptive launcher, themed launcher, splash, and notification symbols plus the Windows bundle, taskbar, favicon, sidebar, and responsive header marks. Final full-color, square, and monochrome SVG masters and usage guidance live under `docs/branding/final/`.

### Verified

- Android unit tests, debug APK assembly, and lint passed; the APK installed on the authorized Android 15 Infinix X6880, and the real OEM launcher mask preserved a clear black/blue silhouette. Five Windows gesture tests, TypeScript typecheck, Vite production build, native debug executable build, NSIS packaging, and MSI packaging passed. Final SVG sources parsed successfully, and the 256 px Windows icon was visually inspected.

### Pending

- Inspect the packaged Windows taskbar and installer icon on the target laptop after installation.

## Premium relay frontend redesign - 2026-11-08

### Implemented

- Android Home, Activity, Settings, Gallery, outgoing Grab, and incoming receive-camera surfaces now use a shared deep-navy, sky-blue, pale-gray, and paper-white visual system with locally bundled Manrope typography, rounded feature cards, pill controls, compact icon navigation, and original transfer artwork. All existing pairing, discovery, multi-selection, gesture, consent, and transfer callbacks remain wired to their prior logic.
- Windows Overview, Activity, Settings, pairing, send/receive, empty, progress, completion, failure, toast, and gesture states use the equivalent responsive desktop design. The 760 px native minimum remains supported, while the desktop sidebar becomes compact bottom navigation below 720 px for browser-level resilience.

### Verified

- Android unit tests, debug APK assembly, and lint passed; the rebuilt APK installed with existing data preserved. Home, Activity, Settings, and the real MediaStore Gallery were inspected on the authorized Android 15 device after explicitly mapping Manrope's variable weight axis. Five Windows gesture tests, TypeScript typecheck, Vite production build, and native Tauri debug build passed. Windows Overview was visually inspected at 1440 px and the 768 px native minimum; the latter stacked feature cards without clipping.

### Pending

- Inspect the outgoing Grab and incoming receive-camera surfaces during the established physical bidirectional-transfer and hash acceptance flows; this presentation-only change does not itself prove cross-device transfer behavior.
