# Windows Development Setup

Required: supported Node.js/pnpm, stable Rust, Visual Studio Build Tools with Desktop development with C++, and WebView2. Run repository checks from the root. Start the native app with `pnpm --filter @abot/windows exec tauri dev`, select a destination folder, scan the generated QR in Android, and approve the matching six-digit code on both devices. The Windows Ed25519 private identity is persisted in Windows Credential Manager under `app.abot.desktop`.

Runtime LAN discovery/listening will require a Windows Firewall rule scoped to private networks. Do not open a public-network wildcard rule by default.
