# BluePrivacy

本專案用於管理 Android 本機 Bluetooth friendly name。

## APK 編譯

此 Repository 已加入 GitHub Actions 工作流程：**Build BluePrivacy APK**。

上傳完整 Android Studio 專案（包含 `app/`、Gradle 設定及原始碼）到根目錄後，在 Actions 執行工作流程。成功後下載 artifact `BluePrivacy-debug-apk`，其中包含 `app-debug.apk`。

## 限制與安全

- 僅管理本機藍牙 friendly name。
- 不修改 Bluetooth MAC、IMEI、OAID、序號或韌體分區。
- Android 12+ 需要使用者授予 Nearby devices / BLUETOOTH_CONNECT 權限。
- ColorOS 可能限制名稱修改；建置成功不代表已在 OnePlus Ace 5 Pro 實機驗證。
- 此專案目前尚未提供已簽署的正式 release APK。
