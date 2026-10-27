# REA Findings

## Availability and setup

- **Source:** local command and MCP inventory on 2026-10-27; official REA installation guide.
- **Observation:** after upgrading to Node 24.22.1, REA 6.8.0 configured the Codex MCP registration and shared skill version 49. Doctor reports that selected scope healthy. The catalog reports 175 CLI commands, 229 MCP tools, and 6 prompts. The current Codex process still requires a restart to expose the new MCP tools.
- **Evidence/reproduction:** `npx rea-agents@6.8.0 doctor --client codex --json`; `npx rea-agents@6.8.0 capabilities --format json`.
- **Confidence:** high for CLI/configuration status; MCP invocation in this already-running client is not verified.
- **Architecture impact:** REA can provide evidence for owned build artifacts without changing ABOT's runtime architecture.

## Owned artifact inspection

- **Source:** pre-rebrand debug APK and Windows debug executable generated locally.
- **Observation:** the safe artifact provider identified the APK root hash `bad5f5a...b9a17f`, with 92 nodes, 143 occurrences, and 142 edges in its container inventory. It identified the Windows executable as PE. Both operations were static and did not execute the artifacts.
- **Evidence/reproduction:** `rea inspect-artifact <local-path> --format json`.
- **Confidence:** observed for bounded artifact/container identity only.
- **Impact:** confirms the artifact formats and integrity identities, not runtime behavior or acceptance criteria.

## Failed deeper APK probe

- **Source:** `rea inspect-android-package` against the owned debug APK.
- **Observation:** REA failed closed with `capability_unavailable` because `REA_JADX_MCP_JAR` is not configured.
- **Evidence/reproduction:** configure the documented caller-supplied jadx-headless-mcp 0.7.1 JAR, then rerun the positional APK command.
- **Confidence:** high.
- **Impact:** manifest semantics are still validated by Android build/lint, not by REA's Android provider.

No proprietary application was inspected, and no undocumented behavior is represented as verified.
