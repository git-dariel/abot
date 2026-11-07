# Android Development Setup

Required: Android Studio, an Android SDK matching the checked-in Gradle configuration, a supported JDK, and either an emulator or USB-debuggable device. Set `ANDROID_SDK_ROOT` locally or use an untracked `local.properties`; never commit a machine-specific SDK path.

From `apps/android`, run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The module uses Android's document picker and permission-scoped MediaStore access. It requests `INTERNET`, `CAMERA`, network-state, Wi-Fi multicast, selected photo/video access, notification permission, and the `connectedDevice` foreground-service permission; it does not request broad all-files access. Start the Windows receiver, scan its QR, compare and approve the six-digit code on both devices, trust the PC on Android, then complete one accepted transfer. For later sessions, turn on **Windows Receiver Discovery** in Android Settings. It is OFF on every fresh launch and the foreground notification exists only while user-enabled discovery or an active transfer requires it. A signed manual link remains available as fallback. The app targets SDK 35, where local-network access remains covered by `INTERNET`; revisit Android's `ACCESS_LOCAL_NETWORK` flow before targeting SDK 37.
