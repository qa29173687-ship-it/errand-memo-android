package tw.memo.errands.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** 從 App 裡直接把小工具加到桌面（Android 8+ 的系統「加入桌面」視窗）。 */
object WidgetPin {
    private fun provider(ctx: Context, large: Boolean) =
        ComponentName(ctx, if (large) MemoWidgetLargeReceiver::class.java else MemoWidgetReceiver::class.java)

    fun count(ctx: Context, large: Boolean): Int =
        runCatching { AppWidgetManager.getInstance(ctx).getAppWidgetIds(provider(ctx, large)).size }.getOrDefault(0)

    fun count(ctx: Context): Int = count(ctx, false) + count(ctx, true)

    /** 回傳 false 代表這個桌面不支援，請使用者手動加。 */
    fun request(ctx: Context, large: Boolean): Boolean = runCatching {
        val m = AppWidgetManager.getInstance(ctx)
        m.isRequestPinAppWidgetSupported && m.requestPinAppWidget(provider(ctx, large), null, null)
    }.getOrDefault(false)
}
