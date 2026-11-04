# Windows Development Setup

Required: supported Node.js/pnpm, stable Rust, Visual Studio Build Tools with Desktop development with C++, and WebView2. Run repository checks from the root. Start the native app with `pnpm --filter @abot/windows exec tauri dev`, click **Start receiving to Downloads**, scan the generated QR in Android, and approve the matching six-digit code on both devices. The receiver uses Windows' Downloads directory automatically. The Windows Ed25519 private identity is persisted in Windows Credential Manager under `app.abot.desktop`.

Development and production UI builds copy the bundled gesture model and pinned MediaPipe WASM runtime into local assets automatically. To use gesture acceptance, allow camera access when enabling it for a reviewed incoming offer. Missing/denied cameras leave button acceptance available. See `docs/testing/gestures.md`.

Runtime LAN discovery/listening will require a Windows Firewall rule scoped to private networks. Do not open a public-network wildcard rule by default.
