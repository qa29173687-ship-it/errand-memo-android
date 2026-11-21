package tw.memo.errands.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock

/** 點小工具時間要打開的時鐘 App：自動偵測（OPPO/ColorOS 優先）或使用者自選。 */
object ClockApps {
    /** 已知的內建時鐘（順序＝優先順序）。OPPO/ColorOS 放最前面。 */
    val KNOWN = listOf(
        "com.coloros.alarmclock", "com.oplus.alarmclock", "com.oppo.alarmclock",
        "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
        "com.oneplus.deskclock", "com.android.alarmclock", "com.miui.clock", "com.vivo.alarmclock",
        "com.android.BBKClock", "com.huawei.deskclock", "com.hihonor.deskclock", "com.asus.deskclock",
        "com.sonymobile.alarm", "com.htc.android.worldclock", "com.motorola.timeweatherwidget",
    )

    /** alarms=true：用 SHOW_ALARMS 打開那個 activity；false：直接啟動 App。 */
    data class Choice(val pkg: String, val cls: String?, val alarms: Boolean)

    fun rank(pkg: String): Int = KNOWN.indexOf(pkg).let { if (it < 0) Int.MAX_VALUE else it }

    /** 自動：有處理 SHOW_ALARMS 的就用（已知時鐘優先），否則找已安裝的已知時鐘。 */
    fun pickAuto(alarmHandlers: List<Pair<String, String>>, launchable: Collection<String>): Choice? {
        alarmHandlers.sortedBy { rank(it.first) }.firstOrNull()?.let { return Choice(it.first, it.second, true) }
        return KNOWN.firstOrNull { it in launchable }?.let { Choice(it, null, false) }
    }

    fun encode(c: Choice): String = listOf(if (c.alarms) "a" else "l", c.pkg, c.cls ?: "").joinToString("|")
    fun decode(s: String?): Choice? {
        val p = s?.split('|') ?: return null
        if (p.size != 3 || p[1].isBlank() || p[0] !in setOf("a", "l")) return null
        return Choice(p[1], p[2].ifBlank { null }, p[0] == "a")
    }

    // ---- Android ----

    private fun alarmHandlers(ctx: Context): List<Pair<String, String>> = runCatching {
        ctx.packageManager.queryIntentActivities(Intent(AlarmClock.ACTION_SHOW_ALARMS), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName to it.activityInfo.name }
            .filter { it.first != ctx.packageName && it.first != "android" }
    }.getOrDefault(emptyList())

    private fun launchable(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    fun auto(ctx: Context): Choice? = pickAuto(alarmHandlers(ctx), KNOWN.filter { launchable(ctx, it) })

    private fun exists(ctx: Context, c: Choice): Boolean = runCatching {
        if (c.cls != null) { ctx.packageManager.getActivityInfo(ComponentName(c.pkg, c.cls), 0); true } else launchable(ctx, c.pkg)
    }.getOrDefault(false)

    /** 目前要用的：自選（還裝著的話）→ 自動。 */
    fun current(ctx: Context, pref: String?): Choice? = decode(pref)?.takeIf { exists(ctx, it) } ?: auto(ctx)

    fun intentFor(ctx: Context, c: Choice): Intent? {
        val i = when {
            c.alarms && c.cls != null -> Intent(AlarmClock.ACTION_SHOW_ALARMS).setClassName(c.pkg, c.cls)
            c.cls != null -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(c.pkg, c.cls)
            else -> ctx.packageManager.getLaunchIntentForPackage(c.pkg)
        } ?: return null
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun intent(ctx: Context, pref: String?): Intent? = current(ctx, pref)?.let { intentFor(ctx, it) }

    fun label(ctx: Context, c: Choice?): String {
        if (c == null) return "找不到時鐘 App（會打開順便備忘）"
        val pm = ctx.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.pkg, 0)).toString() }.getOrDefault(c.pkg)
    }

    data class Option(val choice: Choice, val label: String, val section: Int) // 0 鬧鐘/時鐘, 1 其他 App

    /** 設定裡可選的：處理 SHOW_ALARMS 的、已知時鐘、其他所有桌面上的 App。 */
    fun options(ctx: Context): List<Option> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, Option>()
        for ((pkg, cls) in alarmHandlers(ctx).sortedBy { rank(it.first) })
            out.putIfAbsent(pkg, Option(Choice(pkg, cls, true), "⏰ " + label(ctx, Choice(pkg, cls, true)) + "（鬧鐘）", 0))
        for (pkg in KNOWN) if (pkg !in out && launchable(ctx, pkg))
            out[pkg] = Option(Choice(pkg, null, false), "🕒 " + label(ctx, Choice(pkg, null, false)), 0)
        val launchers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }.getOrDefault(emptyList())
        launchers.map { it.activityInfo }.filter { it.packageName != ctx.packageName && it.packageName !in out }
            .map { Option(Choice(it.packageName, it.name, false), it.loadLabel(pm).toString(), 1) }
            .sortedBy { it.label }.forEach { out.putIfAbsent(it.choice.pkg, it) }
        return out.values.toList()
    }
}
