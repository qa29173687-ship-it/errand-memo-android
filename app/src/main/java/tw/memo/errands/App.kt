package tw.memo.errands

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import tw.memo.errands.alarm.Notifier
import tw.memo.errands.data.AppDb
import tw.memo.errands.widget.WidgetRefresher

class App : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
        // 下班順便做有任何變動（App 內新增/打勾/延到明天…）→ 更新桌面小工具
        scope.launch {
            AppDb.get(this@App).dao().errandsFlow().drop(1).debounce(300).collect { WidgetRefresher.refresh(this@App) }
        }
    }
}
