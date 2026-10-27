# Release Guide

A development-only Android debug APK is available at `apps/android/app/build/outputs/apk/debug/app-debug.apk`. It is not a signed production release. At build time its SHA-256 was `BAD5F5A4BCF2CC01FC8ABCC7D1BFEC31FB6735CBCED1ABD9131F542E12B9A17F`.

Unsigned development Windows artifacts are also available:

- `target/debug/airbridge-windows.exe` — SHA-256 `5A0B0D6C8F5DFCB16213AFB42CB4247CB1566AE81AAC9AB96703A57A3FB9B54D`
- `target/debug/bundle/nsis/AirBridge_0.1.0_x64-setup.exe` — SHA-256 `9F24FC0E556816060C77DAAF35E002066B5FEB08D707EECD89EF669E7244AA64`
- `target/debug/bundle/msi/AirBridge_0.1.0_x64_en-US.msi` — SHA-256 `9762AB109E9C1F0C4C00A755D6335AE3A7065D130B17D34EBC7DFBC5A11D06CD`

These debug artifacts demonstrate build packaging only. They are not signed releases and do not yet provide cross-device network transfer.

A release requires green protocol, Rust, Windows frontend/Tauri, and Android Gradle checks; real bidirectional transfer evidence; dependency/security review; versioned changelog; and platform signing configuration supplied outside the repository. Never commit signing keys or credentials.

Expected outputs after those gates:

- Windows Tauri NSIS/MSI installer and SHA-256 checksum.
- Android debug APK for development and signed APK/AAB through protected release configuration.
