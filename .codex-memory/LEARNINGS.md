# Learnings

- The initial workspace was empty and was not a Git repository.
- Node 22.13.1, npm 11.3.0, pnpm 10.22.0, and Java 22.0.2 are available.
- The Android SDK exists under the user profile with API 31, 33, 34, and 36 platforms; ADB exists off PATH.
- Rust/Cargo and command-line Gradle were not available during initialization. Existing cached Gradle 8.10.2 plus Android Studio's bundled JDK 21 allowed a wrapper to be generated and the Android build to pass.
- Initially, REA 6.8.0 required a newer Node runtime than the installed 22.13.1 and exposed no MCP tools to the active session.
- Node was upgraded to 24.22.1, Rust 1.99.1 and Visual Studio Build Tools 17.14 were installed, and REA 6.8.0 was registered for Codex with shared skill version 49. A Codex restart is required before its MCP tools appear in-session.
- REA's generic artifact provider inspected the owned APK and PE; deeper APK declaration inspection remains unavailable without the caller-supplied JADX MCP JAR.
- Cross-platform rebranding must classify identifiers before replacement: product/package/bundle names can move to ABOT, while v1 authenticated-data domains, the mDNS service type, and canonical schema IDs must remain byte-for-byte stable. Generate the new `abot://` scheme while accepting the legacy scheme during migration.
- Changing Android `applicationId` or the Tauri bundle identifier creates a separate installed application, not an in-place brand update; release documentation must state that migration boundary explicitly.
- On this Windows host, a combined Gradle clean/lint run can fail when migrated lint-cache JARs remain locked. Stopping Gradle daemons and running the forced unit/APK build and lint as separate `--no-daemon` invocations produced clean verified results.
- A QR scanner launched as a separate Android Activity can recreate the calling Compose Activity, especially around orientation changes. Keep the verified serialized QR payload and user-facing approval state in `rememberSaveable`, reconstruct derived cryptographic data after recreation, and orientation-lock the scanner when rotation adds no value.
