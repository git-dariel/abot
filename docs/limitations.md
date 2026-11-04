# Known Limitations

- The Windows native app builds and passes a process-level launch smoke test. The project owner confirmed Android pairing UI, conventional file transfer, and trusted-PC reconnect. A recorded acceptance run with file hashes and environment details remains pending.
- The Android application ID changed from `dev.airbridge.android` to `app.abot.android`, and the Windows bundle identifier changed from `dev.airbridge.desktop` to `app.abot.desktop`. Existing AirBridge installations are separate applications and do not upgrade in place.
- Android-to-Windows supports one file per signed receiver session. The owner reports trusted-PC reconnect working on real devices. Windows-to-Android, multi-file offers, cancellation, and resume are not connected.
- The channel is bound to a persistent Windows Ed25519 identity, whose private key is stored in Windows Credential Manager. Repeat sessions authenticate possession of a random per-phone credential protected by Android Keystore and Credential Manager. Android still has no asymmetric device identity.
- Resume, cancellation plumbing, rate limits, and replay cache are pending.
- Optional gesture recognition is implemented on Android and Windows using locally bundled MediaPipe assets. Physical-camera accuracy, permission behavior in native WebView2, and a gesture-triggered transfer still require hardware validation. Frames and landmarks are not transmitted by ABOT.
- The project owner reported the earlier manual-link flow working on a real phone and laptop, but the required device/OS, matching hashes, duration, and firewall evidence was not recorded. The signed-QR flow has only passed unit/integration tests and platform builds so far.
- Development debug APK, MSI, and NSIS artifacts exist; no signed release APK/AAB or Windows artifact exists.
- REA is configured and works through the CLI, but the current Codex process must restart before MCP tools appear; deep APK inspection also needs a separately supplied JADX MCP provider.
