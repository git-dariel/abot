# Known Limitations

- The Windows native app builds and passes a process-level launch smoke test; the Android debug app builds. Neither UI has been visually inspected or exercised on an Android emulator/device in this environment.
- Android-to-Windows supports one file per manually paired receiver session. Windows-to-Android, multi-file offers, automatic discovery UI, cancellation, and resume are not connected.
- The connected AES-256-GCM channel uses a one-use high-entropy pairing secret, but it is not yet bound to the Ed25519 device identities. Secure identity persistence, replay caches, and trust revocation are pending.
- Resume, cancellation plumbing, rate limits, and replay cache are pending.
- Gesture recognition and camera permissions are not implemented.
- No emulator or real-device transfer has been run; the connected path has only passed same-machine encrypted TCP integration tests and platform builds.
- Development debug APK, MSI, and NSIS artifacts exist; no signed release APK/AAB or Windows artifact exists.
- REA is configured and works through the CLI, but the current Codex process must restart before MCP tools appear; deep APK inspection also needs a separately supplied JADX MCP provider.
