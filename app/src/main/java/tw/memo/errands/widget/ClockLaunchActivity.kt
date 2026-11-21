package tw.memo.errands.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

/**
 * 透明的跳板：小工具點時間、設定裡的「測試」都先開這個（自己 App 的 activity，Android 12+ 允許），
 * 它再依序嘗試打開時鐘，然後馬上結束。
 */
class ClockLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ClockApps.launch(this) == null) Toast.makeText(this, "打不開時鐘，請到設定按「顯示診斷資訊」", Toast.LENGTH_LONG).show()
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
