# Windows Development Setup

Required: supported Node.js/pnpm, stable Rust, Visual Studio Build Tools with Desktop development with C++, and WebView2. Run repository checks from the root. Start the native app with `pnpm --filter @airbridge/windows exec tauri dev`, select a destination folder, and use the generated one-time pairing link on Android.

Runtime LAN discovery/listening will require a Windows Firewall rule scoped to private networks. Do not open a public-network wildcard rule by default.
