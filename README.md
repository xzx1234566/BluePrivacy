# BluePrivacy LSPosed

BluePrivacy 1.4 is a local-only LSPosed/Xposed module for the native Android/ColorOS Bluetooth friendly-name save path.

## Recommended LSPosed scope

- `com.android.settings`
- `com.oplus.settings`
- `com.oneplus.settings`
- `com.coloros.settings`
- `com.android.bluetooth`

The module:
- Observes the native Settings call to `BluetoothAdapter.setName(String)`.
- Hooks the AOSP `LocalDeviceNameDialogFragment.setDeviceName(String)` save method when that class exists.
- In the Bluetooth process, observes the final `AdapterProperties.setName(String)` native-property setter.
- Includes a narrowly constrained fallback for Android 12+ `AdapterService.setName(String, AttributionSource)`: only after the stock call fails, only when the attribution package is a real installed Settings package whose UID maps back to that package, and only while Bluetooth is `STATE_ON`. The fallback calls the same local `AdapterProperties.setName()` operation and reports success only if the native setter accepts it.
- Does not fake Wi-Fi/mobile connectivity, does not create a network route, and does not call any network API.

## Privacy

- No `android.permission.INTERNET` or other network permissions.
- No HTTP, HTTPS, sockets, analytics, advertising, telemetry, remote configuration, cloud sync, or crash-upload code.
- No third-party SDK. Xposed API is `compileOnly` and is not packaged into the APK.
- The requested Bluetooth name is not written to Xposed logs; logs contain only state, byte count, and success/failure.
- No Bluetooth MAC, IMEI, OAID, serial number, or other device identifiers are read or changed.

The APK itself does not upload data. GitHub Actions uploads only the generated APK artifact to this repository's GitHub Actions storage for download; that build step does not collect data from the phone.

## Compatibility note

The Bluetooth service fallback follows the AOSP Android Bluetooth implementation. ColorOS may replace private Settings or Bluetooth classes. If its save dialog blocks the action before calling `BluetoothAdapter.setName()`, or its native Bluetooth stack rejects the property, this generic hook cannot guarantee a fix without matching that ColorOS-specific implementation. A successful build is not proof of runtime compatibility on a physical OnePlus Ace 5 Pro.
