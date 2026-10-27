# Technical Feasibility

The design is feasible with documented platform APIs: Android can obtain user-selected streams through the Storage Access Framework and run CameraX/MediaPipe locally; Tauri can bridge React UI to Rust networking/filesystem code while the WebView supplies webcam frames. LAN discovery needs a manual QR/IP fallback because multicast can be blocked by router isolation, VPN policy, Windows firewall, or Android multicast behavior.

The main risks are secure identity binding, Windows WebView camera variance, Android background/lifecycle restrictions, resumable integrity for very large files, and real-device interoperability. These need compiled prototypes and hardware evidence. No feasibility benchmark has been run.
