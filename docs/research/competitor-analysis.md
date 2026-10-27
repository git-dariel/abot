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

## Huawei Share OneHop

- **Source:** <https://consumer.huawei.com/en/support/content/en-us15957630/>
- **Observation:** the documented OneHop flow requires Huawei ecosystem capabilities and physical NFC-area interaction; it is not evidence of camera-based continuous hand tracking.
- **Evidence:** Huawei's public support workflow.
- **Confidence:** high for the documented flow; no proprietary implementation was examined.
- **Impact:** ABOT must be independently implemented and use explicit camera gesture states plus ordinary controls.

## LocalSend

- **Sources:** <https://github.com/localsend/protocol> and <https://github.com/localsend/localsend/security>
- **Observation:** metadata preparation before upload, receiver decision, streaming HTTP, per-file token, hash verification, and discovery fallbacks are useful public patterns. Published advisories also show why unauthenticated discovery and unsafe filenames cannot be trusted.
- **Evidence:** open protocol v2.2 and GitHub security advisories.
- **Confidence:** high for public protocol/advisory content.
- **Impact:** ABOT separates discovery from trust, requires identity-bound pairing, validates filenames, and sends no bytes before acceptance.
