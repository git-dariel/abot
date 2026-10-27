# Known Limitations

- The Windows native app builds and passes a process-level launch smoke test; the Android debug app builds. Neither UI has been visually inspected or exercised on an Android emulator/device in this environment.
- The Android application ID changed from `dev.airbridge.android` to `app.abot.android`, and the Windows bundle identifier changed from `dev.airbridge.desktop` to `app.abot.desktop`. Existing AirBridge installations are separate applications and do not upgrade in place.
- Android-to-Windows supports one file per signed-QR receiver session. Windows-to-Android, multi-file offers, automatic discovery UI, cancellation, and resume are not connected.
- The channel is bound to a persistent Windows Ed25519 identity, whose private key is stored in Windows Credential Manager. Android persists and can revoke the approved public identity, but it has no private device identity or mutual authenticated key agreement yet; its trust metadata is not hardware-backed.
- Resume, cancellation plumbing, rate limits, and replay cache are pending.
- Gesture recognition is not implemented. Android camera access is limited to local QR scanning; frames are not transmitted by ABOT.
- The project owner reported the earlier manual-link flow working on a real phone and laptop, but the required device/OS, matching hashes, duration, and firewall evidence was not recorded. The signed-QR flow has only passed unit/integration tests and platform builds so far.
- Development debug APK, MSI, and NSIS artifacts exist; no signed release APK/AAB or Windows artifact exists.
- REA is configured and works through the CLI, but the current Codex process must restart before MCP tools appear; deep APK inspection also needs a separately supplied JADX MCP provider.
