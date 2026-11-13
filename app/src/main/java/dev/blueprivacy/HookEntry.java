package dev.blueprivacy;

import android.bluetooth.BluetoothAdapter;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * LSPosed bridge for the native Bluetooth rename flow.
 *
 * Settings package: intercepts the AOSP local-device-name save method and
 * performs the same official BluetoothAdapter.setName() operation directly.
 * Bluetooth package: records whether the stack's native-name setter accepted it.
 *
 * Only the local friendly name is handled. No address or device identifiers
 * are read or changed.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String SETTINGS = "com.android.settings";
    private static final String BLUETOOTH = "com.android.bluetooth";
    private static final int MAX_NAME_BYTES = 248;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (SETTINGS.equals(lpparam.packageName)) {
            hookSettingsRenameFlow(lpparam.classLoader);
        } else if (BLUETOOTH.equals(lpparam.packageName)) {
            hookBluetoothStack(lpparam.classLoader);
        }
    }

    private void hookSettingsRenameFlow(ClassLoader classLoader) {
        // Observe the framework API regardless of which Settings implementation calls it.
        try {
            Class<?> adapterClass = XposedHelpers.findClass(
                    "android.bluetooth.BluetoothAdapter", null);
            XposedHelpers.findAndHookMethod(adapterClass, "setName", String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            Object value = param.args[0];
                            XposedBridge.log("BluePrivacy: Settings BluetoothAdapter.setName("
                                    + safeName(value) + ")");
                        }

                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Object adapter = param.thisObject;
                            Object state = callQuietly(adapter, "getState");
                            Object actual = callQuietly(adapter, "getName");
                            XposedBridge.log("BluePrivacy: setName result=" + param.getResult()
                                    + ", state=" + state + ", readback=" + safeName(actual));
                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("BluePrivacy: cannot hook BluetoothAdapter in Settings: " + t);
        }

        // AOSP's native Bluetooth rename dialog calls this method on Save.
        // Intercept only when the normal public API succeeds and confirms the
        // value; otherwise allow the ROM's original implementation to continue.
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.settings.bluetooth.LocalDeviceNameDialogFragment",
                    classLoader,
                    "setDeviceName",
                    String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            Object arg = param.args[0];
                            if (!(arg instanceof String)) return;
                            String name = ((String) arg).trim();
                            if (!isValidName(name)) {
                                XposedBridge.log("BluePrivacy: rejected invalid Bluetooth name");
                                return;
                            }

                            try {
                                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                                if (adapter == null || adapter.getState() != BluetoothAdapter.STATE_ON) {
                                    XposedBridge.log("BluePrivacy: native rename bypass not used; "
                                            + "Bluetooth adapter is not STATE_ON");
                                    return;
                                }

                                boolean accepted = adapter.setName(name);
                                String actual = adapter.getName();
                                XposedBridge.log("BluePrivacy: native Settings save intercepted; "
                                        + "setName=" + accepted + ", readback=" + safeName(actual));

                                // Suppress only a duplicate/vendor wrapper call after a
                                // confirmed success. If it failed, let ColorOS original code run.
                                if (accepted && name.equals(actual)) {
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log("BluePrivacy: native rename interception failed: " + t);
                            }
                        }
                    });
            XposedBridge.log("BluePrivacy: hooked AOSP LocalDeviceNameDialogFragment");
        } catch (Throwable t) {
            XposedBridge.log("BluePrivacy: AOSP rename dialog class not present; "
                    + "framework API observer remains active (" + t.getClass().getSimpleName() + ")");
        }
    }

    private void hookBluetoothStack(ClassLoader classLoader) {
        // Observe the final Android Bluetooth-stack name setter. If this reports
        // false, the failure is below Settings (native stack/HAL/vendor service).
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.bluetooth.btservice.AdapterProperties",
                    classLoader,
                    "setName",
                    String.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log("BluePrivacy: Bluetooth stack AdapterProperties.setName("
                                    + safeName(param.args[0]) + ")");
                        }

                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log("BluePrivacy: native Bluetooth name setter result="
                                    + param.getResult());
                        }
                    });
            XposedBridge.log("BluePrivacy: hooked AdapterProperties.setName");
        } catch (Throwable t) {
            XposedBridge.log("BluePrivacy: AdapterProperties hook unavailable: " + t);
        }

        // Some Android 16/vendor builds place the Binder implementation in a
        // nested class. Hook matching setName methods when that class exists.
        hookBinderSetName("com.android.bluetooth.btservice.AdapterService$AdapterServiceBinder",
                classLoader);
        hookBinderSetName("com.android.bluetooth.btservice.AdapterServiceBinder",
                classLoader);
    }

    private void hookBinderSetName(String className, ClassLoader classLoader) {
        try {
            Class<?> target = XposedHelpers.findClassIfExists(className, classLoader);
            if (target == null) return;
            for (Method method : target.getDeclaredMethods()) {
                if (!"setName".equals(method.getName())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        if (param.args != null && param.args.length > 0) {
                            XposedBridge.log("BluePrivacy: Bluetooth Binder setName("
                                    + safeName(param.args[0]) + ")");
                        }
                    }

                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("BluePrivacy: Bluetooth Binder setName result="
                                + param.getResult());
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("BluePrivacy: Binder hook unavailable for "
                    + className + ": " + t);
        }
    }

    private static boolean isValidName(String name) {
        return name != null && !name.isEmpty()
                && name.getBytes(StandardCharsets.UTF_8).length <= MAX_NAME_BYTES;
    }

    private static String safeName(Object value) {
        if (value == null) return "<null>";
        String text = String.valueOf(value);
        return text.length() > 64 ? text.substring(0, 64) + "…" : text;
    }

    private static Object callQuietly(Object target, String method) {
        try {
            return XposedHelpers.callMethod(target, method);
        } catch (Throwable ignored) {
            return "<unavailable>";
        }
    }
}
