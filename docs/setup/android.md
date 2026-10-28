# Android Development Setup

Required: Android Studio, an Android SDK matching the checked-in Gradle configuration, a supported JDK, and either an emulator or USB-debuggable device. Set `ANDROID_SDK_ROOT` locally or use an untracked `local.properties`; never commit a machine-specific SDK path.

From `apps/android`, run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The module uses Android's document picker and requests `INTERNET`, `CAMERA`, network-state access, and Wi-Fi multicast reception; the camera is used only by the on-device QR scanner. It does not request broad file-storage, notification, or foreground-service permissions. Start the Windows receiver, scan its QR, compare and approve the six-digit code on both devices, trust the PC on Android, then complete one accepted transfer. **Find trusted PC** stays disabled until Windows confirms that it stored the reconnect credential. For later receiver sessions, use that button instead of scanning. A signed manual link remains available as fallback. The app targets SDK 35, where local-network access remains covered by `INTERNET`; revisit Android's `ACCESS_LOCAL_NETWORK` flow before targeting SDK 37.
