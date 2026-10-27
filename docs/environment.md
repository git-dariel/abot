# Environment and REA Report

Observed on 2026-10-27 in `C:\Users\Dariel\Documents\own-projects\airbridge-workspace`.

## Host tools

| Capability | Observation | Status |
|---|---|---|
| Git | 2.45.1.windows.1 | Available |
| Node.js | 24.22.1 | Available; compatible with REA 6.8.0 |
| npm | 11.19.1 | Available |
| pnpm | 10.22.0 | Available |
| Rust/Cargo | 1.99.1 stable MSVC toolchain | Available in the user Cargo directory |
| Java/Javac | Oracle Java 22.0.2 | Available, but Android Gradle compatibility not yet verified |
| Gradle | Command absent; Gradle 8.10.2 was available in the user wrapper cache and a repository wrapper was generated | Available through `apps/android/gradlew.bat` |
| Android SDK | User SDK present; platforms 31, 33, 34, 35, 36; build-tools 33.0.1, 34.0.0, 35.0.0, 36.0.0, 36.1.0 | Available off PATH |
| ADB | Present in SDK `platform-tools`, command absent from PATH | Available off PATH |
| Android Studio | Installation directory present; bundled JDK is OpenJDK 21.0.9 | Available |
| Visual Studio Build Tools | 17.14.42 with the C++ workload and MSVC 14.44 | Available through the Developer Command Prompt |

The approved machine-wide installers upgraded Node and installed Rustup plus Visual Studio Build Tools. Rust commands that link Windows binaries must run inside a Visual Studio developer environment.

The Android Gradle build installed the accepted/licensed Android 35 platform revision 2 into the existing user SDK. No global PATH or machine configuration was changed.

## REA availability

- **Registration:** REA 6.8.0 is registered in the Codex configuration and shared workflow skill version 49 is installed; doctor reports the selected Codex/skill scope healthy.
- **Catalog:** 175 CLI commands, 229 MCP tools, and 6 prompts; progress and cancellation are supported.
- **Current session:** a Codex restart is still required before the newly registered MCP tools are exposed here.
- **Working provider:** the safe artifact-graph provider inspected the owned pre-rebrand APK and Windows PE without executing them.
- **Unavailable provider:** `inspect-android-package` failed closed because `REA_JADX_MCP_JAR` is not configured. Hopper, Ghidra, IDA, and Kuna are also not configured.
- **Schema check:** `inspect-android-package` requires one string `path` and rejects additional properties at the MCP schema level; the CLI exposes that path positionally.
- **Runtime permissions:** static artifact inspection reads caller-selected local files and may create bounded local evidence; provider capability output reports operation-specific side effects. No proprietary third-party artifact was inspected.

Official setup documentation: <https://github.com/morluto/rea/blob/main/docs/installation.md>

## Remediation

1. Restart Codex to load the registered REA MCP server.
2. If deep APK declaration/class inspection is required, configure a trusted caller-supplied `jadx-headless-mcp` 0.7.1 JAR and rerun doctor.
3. Install a native analysis engine only for a specific authorized investigation that needs it.

REA evidence is limited to the project's own generated artifacts and the explicit availability checks above.
