# Learnings

- The initial workspace was empty and was not a Git repository.
- Node 22.13.1, npm 11.3.0, pnpm 10.22.0, and Java 22.0.2 are available.
- The Android SDK exists under the user profile with API 31, 33, 34, and 36 platforms; ADB exists off PATH.
- Rust/Cargo and command-line Gradle were not available during initialization. Existing cached Gradle 8.10.2 plus Android Studio's bundled JDK 21 allowed a wrapper to be generated and the Android build to pass.
- Initially, REA 6.8.0 required a newer Node runtime than the installed 22.13.1 and exposed no MCP tools to the active session.
- Node was upgraded to 24.22.1, Rust 1.99.1 and Visual Studio Build Tools 17.14 were installed, and REA 6.8.0 was registered for Codex with shared skill version 49. A Codex restart is required before its MCP tools appear in-session.
- REA's generic artifact provider inspected the owned APK and PE; deeper APK declaration inspection remains unavailable without the caller-supplied JADX MCP JAR.
