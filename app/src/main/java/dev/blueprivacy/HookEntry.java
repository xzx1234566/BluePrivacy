package dev.blueprivacy;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * BluePrivacy 1.4 - local-only LSPosed/Xposed hooks.
 *
 * The module does not access network APIs, does not fake connectivity, and
 * does not send any data to a remote endpoint. All name writes stay inside
 * Android's local Bluetooth service/native stack. Bluetooth names are never
 * included in logs.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "BluePrivacy";
    private static final int MAX_NAME_BYTES = 248;

    private static final Set<String> SETTINGS_PACKAGES = new HashSet<>(Arrays.asList(
        "com.android.settings",
        "com.oplus.settings",
        "com.oneplus.settings",
        "com.coloros.settings"
    ));

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (SETTINGS_PACKAGES.contains(lpparam.packageName)) {
            hookSettingsRenameFlow(lpparam.classLoader);
        } else if ("com.android.bluetooth".equals(lpparam.packageName)) {
            hookBluetoothStack(lpparam.classLoader);
        }
    }

    private static void hookSettingsRenameFlow(ClassLoader classLoader) {
        // Observe the public API used by Android Settings. Never log the name value.
        try {
            Class<?> adapterClass = XposedHelpers.findClass(
                "android.bluetooth.BluetoothAdapter", null);
            XposedHelpers.findAndHookMethod(
                adapterClass,
                "setName",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object requested = param.args[0];
                            int bytes = requested instanceof String
                                ? ((String) requested).getBytes(StandardCharsets.UTF_8).length : -1;
                            int state = ((Number) XposedHelpers.callMethod(
                                param.thisObject, "getState")).intValue();
                            boolean ok = Boolean.TRUE.equals(param.getResult());
                            boolean readbackMatch = false;
                            if (ok && requested instanceof String) {
                                Object actual = XposedHelpers.callMethod(param.thisObject, "getName");
                                readbackMatch = requested.equals(actual);
                            }
                            XposedBridge.log(TAG + ": Settings setName"
                                + " state=" + state
                                + " result=" + ok
                                + " utf8Bytes=" + bytes
                                + " readbackMatch=" + readbackMatch);
                        } catch (Throwable ignored) {
                            XposedBridge.log(TAG + ": Settings API observer failed safely.");
                        }
                    }
                }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": BluetoothAdapter hook unavailable in Settings: "
                + t.getClass().getSimpleName());
        }

        // AOSP's built-in Bluetooth rename dialog calls this method on Save.
        // It performs the same local Android Bluetooth API call. The original
        // OEM wrapper is suppressed only after the OS confirms the new value.
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.settings.bluetooth.LocalDeviceNameDialogFragment",
                classLoader,
                "setDeviceName",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object arg = param.args[0];
                        if (!(arg instanceof String)) return;

                        String name = ((String) arg).trim();
                        if (!isValidName(name)) {
                            XposedBridge.log(TAG + ": invalid local Bluetooth name rejected");
                            return;
                        }

                        try {
                            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                            if (adapter == null || adapter.getState() != BluetoothAdapter.STATE_ON) {
                                XposedBridge.log(TAG + ": rename path left untouched; adapter is not STATE_ON");
                                return;
                            }

                            boolean accepted = adapter.setName(name);
                            String actual = adapter.getName();
                            boolean confirmed = accepted && name.equals(actual);
                            XposedBridge.log(TAG + ": native dialog save"
                                + " accepted=" + accepted + " readbackConfirmed=" + confirmed
                                + " utf8Bytes=" + name.getBytes(StandardCharsets.UTF_8).length);

                            // Return normally only after the native Bluetooth service confirms
                            // the name; on any failure let the ColorOS/AOSP implementation run.
                            if (confirmed) param.setResult(null);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": native dialog fallback failed: "
                                + t.getClass().getSimpleName());
                        }
                    }
                }
            );
            XposedBridge.log(TAG + ": AOSP native Bluetooth rename dialog hook installed.");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": AOSP rename dialog class not present; public API observer active.");
        }
    }

    private static void hookBluetoothStack(ClassLoader classLoader) {
        // Record only whether the final native property setter accepts a write.
        try {
            XposedHelpers.findAndHookMethod(
                "com.android.bluetooth.btservice.AdapterProperties",
                classLoader,
                "setName",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object value = param.args[0];
                        int bytes = value instanceof String
                            ? ((String) value).getBytes(StandardCharsets.UTF_8).length : -1;
                        XposedBridge.log(TAG + ": AdapterProperties.setName"
                            + " result=" + param.getResult() + " utf8Bytes=" + bytes);
                    }
                }
            );
            XposedBridge.log(TAG + ": Bluetooth AdapterProperties observer installed.");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": AdapterProperties observer unavailable: "
                + t.getClass().getSimpleName());
        }

        // Android 12+ Bluetooth service path:
        // AdapterService.setName(String, AttributionSource) validates the caller,
        // then calls AdapterProperties.setName(String). If the stock call returns
        // false, make one local retry ONLY for a verified installed Settings UID.
        // This does not alter permission checks for third-party apps.
        try {
            Class<?> attributionClass = XposedHelpers.findClass(
                "android.content.AttributionSource", classLoader);
            XposedHelpers.findAndHookMethod(
                "com.android.bluetooth.btservice.AdapterService",
                classLoader,
                "setName",
                String.class,
                attributionClass,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable() || Boolean.TRUE.equals(param.getResult())) return;

                        try {
                            Object rawName = param.args[0];
                            if (!(rawName instanceof String)) return;
                            String name = ((String) rawName).trim();
                            if (!isValidName(name)) return;

                            Object source = param.args[1];
                            if (source == null) return;
                            String sourcePackage = (String) XposedHelpers.callMethod(
                                source, "getPackageName");
                            int sourceUid = ((Number) XposedHelpers.callMethod(
                                source, "getUid")).intValue();

                            if (!isVerifiedSettingsAttribution(
                                    param.thisObject, sourcePackage, sourceUid)) {
                                return;
                            }

                            Object adapter = XposedHelpers.getObjectField(
                                param.thisObject, "mAdapter");
                            if (adapter == null) return;
                            int state = ((Number) XposedHelpers.callMethod(
                                adapter, "getState")).intValue();
                            if (state != BluetoothAdapter.STATE_ON) return;

                            Object properties = XposedHelpers.getObjectField(
                                param.thisObject, "mAdapterProperties");
                            if (properties == null) return;

                            Object fallback = XposedHelpers.callMethod(
                                properties, "setName", name);
                            if (Boolean.TRUE.equals(fallback)) {
                                // Only report success when the actual local native setter accepts it.
                                param.setResult(Boolean.TRUE);
                                XposedBridge.log(TAG + ": verified Settings local fallback accepted"
                                    + " state=" + state
                                    + " utf8Bytes=" + name.getBytes(StandardCharsets.UTF_8).length);
                            } else {
                                XposedBridge.log(TAG + ": verified Settings local fallback rejected"
                                    + " state=" + state);
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": service fallback unavailable: "
                                + t.getClass().getSimpleName());
                        }
                    }
                }
            );
            XposedBridge.log(TAG + ": AdapterService local fallback hook installed.");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": AdapterService fallback hook unavailable: "
                + t.getClass().getSimpleName());
        }

        // Observe vendor/AOSP Binder wrapper variants without changing their arguments.
        hookBinderSetName(
            "com.android.bluetooth.btservice.AdapterService$AdapterServiceBinder", classLoader);
        hookBinderSetName(
            "com.android.bluetooth.btservice.AdapterServiceBinder", classLoader);
    }

    private static boolean isVerifiedSettingsAttribution(
            Object serviceObject, String packageName, int uid) {
        if (packageName == null || uid < 0 || !SETTINGS_PACKAGES.contains(packageName)) return false;
        if (!(serviceObject instanceof Context)) return false;

        try {
            String[] packages = ((Context) serviceObject)
                .getPackageManager().getPackagesForUid(uid);
            if (packages == null) return false;
            for (String installed : packages) {
                if (packageName.equals(installed)) return true;
            }
        } catch (Throwable ignored) {
            // Fail closed if package-to-UID verification is unavailable.
        }
        return false;
    }

    private static void hookBinderSetName(String className, ClassLoader classLoader) {
        try {
            Class<?> target = XposedHelpers.findClassIfExists(className, classLoader);
            if (target == null) return;
            for (Method method : target.getDeclaredMethods()) {
                if (!"setName".equals(method.getName())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int bytes = (param.args != null && param.args.length > 0
                                && param.args[0] instanceof String)
                            ? ((String) param.args[0]).getBytes(StandardCharsets.UTF_8).length : -1;
                        XposedBridge.log(TAG + ": Binder setName result="
                            + param.getResult() + " utf8Bytes=" + bytes);
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Binder observer unavailable for "
                + className + ": " + t.getClass().getSimpleName());
        }
    }

    private static boolean isValidName(String name) {
        return name != null && !name.isEmpty()
            && name.getBytes(StandardCharsets.UTF_8).length <= MAX_NAME_BYTES;
    }
}
