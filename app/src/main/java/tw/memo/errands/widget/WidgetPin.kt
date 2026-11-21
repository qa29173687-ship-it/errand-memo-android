package tw.memo.errands.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** 從 App 裡直接把小工具加到桌面（Android 8+ 的系統「加入桌面」視窗）。 */
object WidgetPin {
    private fun provider(ctx: Context) = ComponentName(ctx, MemoWidgetReceiver::class.java)

    fun count(ctx: Context): Int =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(provider(ctx)).size }.getOrDefault(0)

    /** 回傳 false 代表這個桌面不支援，請使用者手動加。 */
    fun request(ctx: Context): Boolean = runCatching {
        val m = AppWidgetManager.getInstance(ctx)
        m.isRequestPinAppWidgetSupported && m.requestPinAppWidget(provider(ctx), null, null)
    }.getOrDefault(false)
}
