# Android Development Setup

Required: Android Studio, an Android SDK matching the checked-in Gradle configuration, a supported JDK, and either an emulator or USB-debuggable device. Set `ANDROID_SDK_ROOT` locally or use an untracked `local.properties`; never commit a machine-specific SDK path.

From `apps/android`, run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The module uses Android's document picker and requests `INTERNET` plus `CAMERA`; the camera is used only by the on-device QR scanner. It does not request broad file-storage, nearby-device, notification, or foreground-service permissions. Start the Windows receiver, scan its QR, compare and approve the six-digit code on both devices, select one document, and keep the app in the foreground while sending. A signed manual link is available as a fallback.
