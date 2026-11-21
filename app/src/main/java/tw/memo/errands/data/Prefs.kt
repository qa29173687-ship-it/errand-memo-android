package tw.memo.errands.data

import android.content.Context
import tw.memo.errands.logic.Logic

/** 提醒設定（SharedPreferences）。 */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var eveningTime: String
        get() = sp.getString("eveningTime", "17:50")!!
        set(v) = sp.edit().putString("eveningTime", v).apply()
    var eveningDays: Set<Int>
        get() = Logic.parseDays(sp.getString("eveningDays", "1,2,3,4,5")!!)
        set(v) = sp.edit().putString("eveningDays", Logic.daysStr(v)).apply()
    var eveningMode: String
        get() = sp.getString("eveningMode", Mode.ALARM)!!
        set(v) = sp.edit().putString("eveningMode", v).apply()

    var nightTime: String
        get() = sp.getString("nightTime", "22:00")!!
        set(v) = sp.edit().putString("nightTime", v).apply()
    var nightDays: Set<Int>
        get() = Logic.parseDays(sp.getString("nightDays", "0,1,2,3,4,5,6")!!)
        set(v) = sp.edit().putString("nightDays", Logic.daysStr(v)).apply()
    var nightMode: String
        get() = sp.getString("nightMode", Mode.NOTIFY)!!
        set(v) = sp.edit().putString("nightMode", v).apply()

    var purchaseTime: String
        get() = sp.getString("purchaseTime", "09:00")!!
        set(v) = sp.edit().putString("purchaseTime", v).apply()

    /** 目前已排程的採購提醒 id（重排時先全部取消）。 */
    var scheduledPurchaseIds: Set<Long>
        get() = sp.getString("scheduledPurchaseIds", "")!!.split(',').mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = sp.edit().putString("scheduledPurchaseIds", v.joinToString(",")).apply()

    /** 桌面小工具底色（light / dark / system）與透明度（high / mid / low）。預設深色＋中，最好讀。 */
    var widgetTint: String
        get() = tw.memo.errands.widget.WidgetStyle.tintOf(sp.getString("widgetTint", null))
        set(v) = sp.edit().putString("widgetTint", v).apply()
    var widgetAlpha: String
        get() = tw.memo.errands.widget.WidgetStyle.alphaOf(sp.getString("widgetAlpha", null))
        set(v) = sp.edit().putString("widgetAlpha", v).apply()

    /** 點小工具時間要開的 App（空字串＝自動偵測），格式見 ClockApps.encode。 */
    var clockApp: String
        get() = sp.getString("clockApp", "")!!
        set(v) = sp.edit().putString("clockApp", v).apply()

    var askedNotifPermission: Boolean
        get() = sp.getBoolean("askedNotif", false)
        set(v) = sp.edit().putBoolean("askedNotif", v).apply()
}
