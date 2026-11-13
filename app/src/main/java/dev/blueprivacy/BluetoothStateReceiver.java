package dev.blueprivacy;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

/** Reapplies only the saved friendly name after Bluetooth reaches STATE_ON. */
public final class BluetoothStateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) return;
        if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                != BluetoothAdapter.STATE_ON) return;
        if (Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.w("BluePrivacy", "BLUETOOTH_CONNECT not granted; not applying name");
            return;
        }
        String name = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(MainActivity.KEY_NAME, null);
        boolean enabled = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_AUTO, true);
        if (!enabled || name == null || name.trim().isEmpty()) return;
        try {
            BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter != null && adapter.isEnabled()) {
                boolean ok = adapter.setName(name);
                Log.i("BluePrivacy", "Auto-apply result=" + ok);
            }
        } catch (Throwable t) {
            Log.e("BluePrivacy", "Auto-apply failed", t);
        }
    }
}
