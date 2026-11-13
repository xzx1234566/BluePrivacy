package dev.blueprivacy;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Informational module launcher only. The actual operation remains in the
 * Android/ColorOS native Bluetooth Settings page.
 */
public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("BluePrivacy LSPosed 1.4");
        title.setTextSize(23);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(
            "\n本模組修正的是系統原生「設定 → 藍牙」名稱保存路徑，不提供 App 內改名功能。\n\n" +
            "隱私設計：沒有 INTERNET 權限、不含廣告、分析、遙測、雲端同步或第三方 SDK。\n" +
            "不偽造網路連線，也不呼叫任何網路 API。\n\n" +
            "只處理本機藍牙友善名稱，不讀取或修改藍牙 MAC、IMEI、OAID、序號等識別資訊。\n\n" +
            "在 LSPosed 啟用 BluePrivacy，並確認「設定」及「藍牙」作用域已勾選。"
        );
        body.setTextSize(16);
        root.addView(body);
        setContentView(root);
    }
}
