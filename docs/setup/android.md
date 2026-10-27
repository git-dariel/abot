# Android Development Setup

Required: Android Studio, an Android SDK matching the checked-in Gradle configuration, a supported JDK, and either an emulator or USB-debuggable device. Set `ANDROID_SDK_ROOT` locally or use an untracked `local.properties`; never commit a machine-specific SDK path.

From `apps/android`, run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

The current module uses Android's document picker and requests only `INTERNET` for direct local TCP transfer. It does not request camera, broad file-storage, nearby-device, notification, or foreground-service permissions. Start the Windows receiver, paste its one-time pairing link, select one document, and keep the app in the foreground while sending.
