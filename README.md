# BluePrivacy Xposed

BluePrivacy is an LSPosed/Xposed module focused on the native Android Bluetooth friendly-name save flow.

## Current hook scope

- `com.android.settings`: observes `BluetoothAdapter.setName(String)` and intercepts the AOSP `LocalDeviceNameDialogFragment.setDeviceName(String)` path. It uses the official Android API and suppresses the ROM wrapper only after the requested name is read back successfully.
- `com.android.bluetooth`: observes the Bluetooth stack's `AdapterProperties.setName(String)` and available Binder `setName` entrypoints to help identify a failure below Settings.
- No Wi-Fi or mobile-data APIs are used by this module.
- No Bluetooth MAC, IMEI, OAID, serial number, or other device identifiers are accessed or changed.

## Build

GitHub Actions workflow **Build BluePrivacy APK** builds `app-debug.apk` and uploads it as artifact `BluePrivacy-debug-apk`.

## Important compatibility note

AOSP Settings uses `LocalDeviceNameDialogFragment.setDeviceName()` and then `BluetoothAdapter.setName()`. ColorOS may replace this implementation or its Bluetooth service. The module includes an AOSP-compatible interception and service-layer diagnostics, but an APK build alone cannot prove that the ColorOS 16 vendor path is fixed on a specific handset. If the framework API or native Bluetooth setter itself returns false, the failure is below the Settings dialog and cannot honestly be called fixed by this hook.

The module scope must include **Settings** (`com.android.settings`) and **Bluetooth** (`com.android.bluetooth`) in LSPosed for both hook layers to load.
