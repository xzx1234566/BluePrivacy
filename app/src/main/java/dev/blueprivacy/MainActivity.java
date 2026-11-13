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
        heading.setText("BluePrivacy — 本機藍牙名稱");
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
        Button restore = new Button(this);
        restore.setText("清除自訂名稱（不改硬體識別）");
        layout.addView(restore);

        status = new TextView(this);
        status.setText("離線運作；只管理藍牙友善名稱。");
        layout.addView(status);
        setContentView(layout);

        save.setOnClickListener(v -> saveSettings());
        apply.setOnClickListener(v -> { saveSettings(); applyName(); });
        read.setOnClickListener(v -> readName());
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

    private void applyName() {
        String n = validatedName();
        if (n == null) { status.setText("名稱不可為空，UTF-8 長度不可超過 248 bytes。"); return; }
        if (adapter == null) { status.setText("找不到 BluetoothAdapter。"); return; }
        if (!hasPermission()) { requestBtPermission(); return; }
        try {
            if (!adapter.isEnabled()) { status.setText("請先開啟藍牙，再按立即套用。"); return; }
            boolean ok = adapter.setName(n);
            status.setText(ok ? "系統已接受名稱：" + adapter.getName()
                : "系統拒絕修改。請確認藍牙已完全啟動；不會繞過系統政策。");
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
