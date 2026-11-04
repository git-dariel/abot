# Competitor Analysis

## Quick Share

- **Source:** <https://www.android.com/intl/en_nz/quick-share/with-windows-pc/>
- **Observation:** the published Windows experience relies on nearby Wi-Fi and Bluetooth and states that transfers use end-to-end encryption; Windows 10/11 64-bit support has hardware/implementation constraints.
- **Evidence:** public product requirements and FAQ.
- **Confidence:** high for documented product behavior; internal protocol details were not inspected.
- **Impact:** ABOT needs clear capability checks and must not promise universal support merely from OS version.

## Microsoft Phone Link

- **Source:** <https://support.microsoft.com/en-us/windows/apps/phonelink/seamlessly-transfer-content-between-your-devices>
- **Observation:** file transfer is surfaced through familiar share targets, notifications, progress, and stable download locations, but some drag/drop workflows are device-specific.
- **Evidence:** Microsoft's public setup and transfer instructions.
- **Confidence:** high for documented UX.
- **Impact:** integrate platform file pickers/share affordances and make completion destinations obvious, without inheriting account or manufacturer dependencies.

## Huawei air gesture transfer

- **Sources:** <https://consumer.huawei.com/cn/support/content/zh-cn16090787/> and <https://consumer.huawei.com/cn/support/content/zh-cn16046165/>
- **Observation:** Huawei documents a system setting available on supported HarmonyOS 5+ devices and integrated scenarios. The sender opens supported content, holds an open palm 20-40 cm from the screen, closes the fist to grab, moves that fist toward another supported unlocked device, and opens the palm to release. The HarmonyOS 6 documentation includes supported Huawei phones, tablets, and HarmonyOS computers.
- **Evidence:** Huawei's public support workflow and supported-product/version lists.
- **Confidence:** high for documented behavior and compatibility; implementation internals were not inspected.
- **Impact:** ABOT can reproduce the intent sequence inside its own foreground Android Gallery and Windows app, but it must not claim OS-wide integration, another Gallery app's active-photo access, continuous cross-camera hand tracking, or unrestricted background camera use.

## LocalSend

- **Sources:** <https://github.com/localsend/protocol> and <https://github.com/localsend/localsend/security>
- **Observation:** metadata preparation before upload, receiver decision, streaming HTTP, per-file token, hash verification, and discovery fallbacks are useful public patterns. Published advisories also show why unauthenticated discovery and unsafe filenames cannot be trusted.
- **Evidence:** open protocol v2.2 and GitHub security advisories.
- **Confidence:** high for public protocol/advisory content.
- **Impact:** ABOT separates discovery from trust, requires identity-bound pairing, validates filenames, and sends no bytes before acceptance.
