package dev.blueprivacy;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
    static final String PREFS = "blueprivacy";
    static final String KEY_NAME = "friendly_name";
    static final String KEY_AUTO = "auto_apply";
    private static final int REQ_BT = 44;

    private EditText nameInput;
    private TextView status;
    private android.widget.CheckBox autoApply;
    private BluetoothAdapter adapter;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        TextView heading = new TextView(this);
        heading.setText("BluePrivacy 1.1 — 本機藍牙名稱");
        heading.setTextSize(22);
        layout.addView(heading);

        nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("BluePrivacy");
        nameInput.setText(prefs.getString(KEY_NAME, "BluePrivacy"));
        layout.addView(nameInput);

        autoApply = new android.widget.CheckBox(this);
        autoApply.setText("藍牙開啟時自動套用已儲存名稱");
        autoApply.setChecked(prefs.getBoolean(KEY_AUTO, true));
        layout.addView(autoApply);

        Button save = new Button(this);
        save.setText("儲存設定");
        layout.addView(save);
        Button apply = new Button(this);
        apply.setText("立即套用");
        layout.addView(apply);
        Button read = new Button(this);
        read.setText("讀取目前名稱");
        layout.addView(read);
        Button diagnose = new Button(this);
        diagnose.setText("檢查藍牙狀態");
        layout.addView(diagnose);
        Button restore = new Button(this);
        restore.setText("清除自訂名稱（不改硬體識別）");
        layout.addView(restore);

        status = new TextView(this);
        status.setText("完全離線可用；不需要 Wi-Fi 或行動數據。只管理藍牙友善名稱。");
        layout.addView(status);
        setContentView(layout);

        save.setOnClickListener(v -> saveSettings());
        apply.setOnClickListener(v -> { saveSettings(); applyName(); });
        read.setOnClickListener(v -> readName());
        diagnose.setOnClickListener(v -> diagnoseBluetooth());
        restore.setOnClickListener(v -> {
            prefs.edit().remove(KEY_NAME).putBoolean(KEY_AUTO, false).apply();
            nameInput.setText("");
            autoApply.setChecked(false);
            status.setText("已清除自訂設定。請到系統藍牙設定恢復原廠名稱（若系統允許）。");
        });
    }

    private String validatedName() {
        String n = nameInput.getText().toString().trim();
        if (n.isEmpty() || n.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 248)
            return null;
        return n;
    }

    private void saveSettings() {
        String n = validatedName();
        if (n == null) { status.setText("名稱不可為空，UTF-8 長度不可超過 248 bytes。"); return; }
        prefs.edit().putString(KEY_NAME, n).putBoolean(KEY_AUTO, autoApply.isChecked()).apply();
        status.setText("已儲存到本機：" + n);
    }

    private boolean hasPermission() {
        return Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
    }

    private void diagnoseBluetooth() {
        if (adapter == null) { status.setText("診斷：此裝置沒有可用的 BluetoothAdapter。"); return; }
        boolean permission = hasPermission();
        int state = adapter.getState();
        String stateText;
        switch (state) {
            case BluetoothAdapter.STATE_ON: stateText = "STATE_ON（已完全開啟）"; break;
            case BluetoothAdapter.STATE_TURNING_ON: stateText = "STATE_TURNING_ON（正在啟動，請稍候）"; break;
            case BluetoothAdapter.STATE_TURNING_OFF: stateText = "STATE_TURNING_OFF（正在關閉）"; break;
            case BluetoothAdapter.STATE_OFF: stateText = "STATE_OFF（藍牙關閉）"; break;
            default: stateText = "UNKNOWN（" + state + "）";
        }
        status.setText("診斷結果：\n藍牙狀態：" + stateText + "\nBLUETOOTH_CONNECT：" + (permission ? "已授權" : "未授權") + "\n網路連線：不參與改名流程。\n請在 STATE_ON 且已授權時按「立即套用」。");
    }

    private void applyName() {
        String n = validatedName();
        if (n == null) { status.setText("名稱不可為空，UTF-8 長度不可超過 248 bytes。"); return; }
        if (adapter == null) { status.setText("找不到 BluetoothAdapter。"); return; }
        if (!hasPermission()) { requestBtPermission(); return; }
        try {
            if (adapter.getState() != BluetoothAdapter.STATE_ON) { status.setText("藍牙尚未完全啟動（目前狀態：" + adapter.getState() + "）。請等候 STATE_ON 後再套用；網路狀態不影響此流程。"); return; }
            boolean ok = adapter.setName(n);
            String actual = adapter.getName();
            status.setText(ok && n.equals(actual) ? "修改成功，已讀回確認：" + actual
                : ok ? "API 回傳成功，但讀回名稱不一致。實際名稱：" + actual
                : "setName 回傳 false。藍牙狀態：" + adapter.getState() + "。可能是廠商藍牙服務或系統政策拒絕；網路不是 Android API 的必要條件。");
        } catch (SecurityException e) { status.setText("缺少 BLUETOOTH_CONNECT 權限：" + e.getMessage()); }
          catch (Throwable t) { status.setText("操作失敗：" + t.getClass().getSimpleName() + ": " + t.getMessage()); }
    }

    private void readName() {
        if (adapter == null) { status.setText("找不到 BluetoothAdapter。"); return; }
        if (!hasPermission()) { requestBtPermission(); return; }
        try { status.setText("目前名稱：" + adapter.getName()); }
        catch (Throwable t) { status.setText("讀取失敗：" + t.getMessage()); }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_BT && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
            applyName();
        else if (requestCode == REQ_BT) status.setText("未取得附近裝置權限。");
    }
}
