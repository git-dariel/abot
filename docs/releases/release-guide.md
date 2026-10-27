# Release Guide

A development-only ABOT Android debug APK is available at `apps/android/app/build/outputs/apk/debug/app-debug.apk`. It is not a signed production release. At build time its SHA-256 was `10ECA87D89B0D99CA9E4447B4EB14E6DAA8692DBD716BB814EC229569D2BB53B`.

Unsigned development Windows artifacts are also available:

- `target/debug/abot-windows.exe` — SHA-256 `7404CF3D74D375ECFFC9D184264CCF7ED07D5D5A318BDFF23CA3E4946876B7DB`
- `target/debug/bundle/nsis/ABOT_0.1.0_x64-setup.exe` — SHA-256 `FB21375FA63A6447095CD797FAFCADBF2C3C9BADE6A658A1DA4253960E91AC89`
- `target/debug/bundle/msi/ABOT_0.1.0_x64_en-US.msi` — SHA-256 `DC428E26163FA2DABE9EEBFE37E37EE3A76AE6D84AA6523E93A6B0B14D6402F9`

These debug artifacts demonstrate build packaging only. They are not signed releases and do not prove a cross-device transfer.

A release requires green protocol, Rust, Windows frontend/Tauri, and Android Gradle checks; real bidirectional transfer evidence; dependency/security review; versioned changelog; and platform signing configuration supplied outside the repository. Never commit signing keys or credentials.

Expected outputs after those gates:

- Windows Tauri NSIS/MSI installer and SHA-256 checksum.
- Android debug APK for development and signed APK/AAB through protected release configuration.
