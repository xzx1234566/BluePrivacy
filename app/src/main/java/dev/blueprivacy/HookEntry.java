package dev.blueprivacy;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // Deliberately limited to the companion app. This does not patch system_server,
        // Bluetooth HAL, MAC addresses, identifiers, or security checks.
        if (!"dev.blueprivacy".equals(lpparam.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(
                "android.bluetooth.BluetoothAdapter",
                lpparam.classLoader,
                "setName",
                String.class,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        Object requested = param.args[0];
                        if (requested instanceof String) {
                            String value = ((String) requested).trim();
                            if (value.isEmpty() || value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 248) {
                                param.setResult(false);
                            } else {
                                XposedBridge.log("BluePrivacy: setName requested (" + value.length() + " chars)");
                            }
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log("BluePrivacy hook unavailable: " + t);
        }
    }
}
