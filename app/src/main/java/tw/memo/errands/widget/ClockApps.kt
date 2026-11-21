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
        "com.coloros.alarmclock", "com.oplus.alarmclock", "com.oppo.alarmclock", "com.oplus.clock", "com.coloros.clock",
        "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
        "com.oneplus.deskclock", "com.android.alarmclock", "com.miui.clock", "com.vivo.alarmclock",
        "com.android.BBKClock", "com.huawei.deskclock", "com.hihonor.deskclock", "com.asus.deskclock",
        "com.sonymobile.alarm", "com.htc.android.worldclock", "com.motorola.timeweatherwidget",
    )

    /** alarms=true：用 SHOW_ALARMS 打開那個 activity；false：直接啟動 App；system=true：不指定 App，交給系統找鬧鐘。 */
    data class Choice(val pkg: String, val cls: String?, val alarms: Boolean, val system: Boolean = false)

    val SYSTEM = Choice("", null, true, system = true)

    /** 會用來找時鐘的動作（依序）。 */
    val ACTIONS = listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SHOW_TIMERS, AlarmClock.ACTION_SET_TIMER)

    /** 名字或套件名看起來像時鐘。 */
    fun looksLikeClock(label: String, pkg: String): Boolean {
        val l = label.lowercase(); val p = pkg.lowercase()
        return listOf("時鐘", "时钟", "鬧鐘", "闹钟", "clock", "alarm").any { it in l } || "clock" in p || "alarm" in p
    }

    fun rank(pkg: String): Int = KNOWN.indexOf(pkg).let { if (it < 0) Int.MAX_VALUE else it }

    /** 自動：有處理 SHOW_ALARMS 的就用（已知時鐘優先），否則找已安裝的已知時鐘。 */
    fun pickAuto(alarmHandlers: List<Pair<String, String>>, launchable: Collection<String>): Choice? {
        alarmHandlers.sortedBy { rank(it.first) }.firstOrNull()?.let { return Choice(it.first, it.second, true) }
        return KNOWN.firstOrNull { it in launchable }?.let { Choice(it, null, false) }
    }

    /** 都找不到的時候：交給系統（隱含的 SHOW_ALARMS），在 ColorOS 上常常還是能打開時鐘。 */
    fun pickAutoOrSystem(alarmHandlers: List<Pair<String, String>>, launchable: Collection<String>): Choice =
        pickAuto(alarmHandlers, launchable) ?: SYSTEM

    fun encode(c: Choice): String = if (c.system) "s||" else listOf(if (c.alarms) "a" else "l", c.pkg, c.cls ?: "").joinToString("|")
    fun decode(s: String?): Choice? {
        val p = s?.split('|') ?: return null
        if (p.size == 3 && p[0] == "s") return SYSTEM
        if (p.size != 3 || p[1].isBlank() || p[0] !in setOf("a", "l")) return null
        return Choice(p[1], p[2].ifBlank { null }, p[0] == "a")
    }

    // ---- Android ----

    private fun handlers(ctx: Context, action: String): List<Pair<String, String>> = runCatching {
        ctx.packageManager.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName to it.activityInfo.name }
            .filter { it.first != ctx.packageName && it.first != "android" }
    }.getOrDefault(emptyList())

    /** 處理任何鬧鐘/計時動作的 activity（SHOW_ALARMS 優先）。 */
    private fun alarmHandlers(ctx: Context): List<Pair<String, String>> {
        val show = handlers(ctx, AlarmClock.ACTION_SHOW_ALARMS)
        if (show.isNotEmpty()) return show
        // 沒有 SHOW_ALARMS 的話，用處理其他鬧鐘動作的 App，但改成直接啟動它
        return emptyList()
    }

    private fun otherAlarmPkgs(ctx: Context): List<String> =
        ACTIONS.drop(1).flatMap { handlers(ctx, it) }.map { it.first }.distinct()

    private fun launchable(ctx: Context, pkg: String) =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) != null }.getOrDefault(false)

    fun auto(ctx: Context): Choice {
        val known = KNOWN.filter { launchable(ctx, it) }
        val others = otherAlarmPkgs(ctx).filter { launchable(ctx, it) && it !in known }
        return pickAuto(alarmHandlers(ctx), known + others)
            ?: others.firstOrNull()?.let { Choice(it, null, false) } ?: SYSTEM
    }

    private fun exists(ctx: Context, c: Choice): Boolean = c.system || runCatching {
        if (c.cls != null) { ctx.packageManager.getActivityInfo(ComponentName(c.pkg, c.cls), 0); true } else launchable(ctx, c.pkg)
    }.getOrDefault(false)

    /** 目前要用的：自選（還裝著的話）→ 自動 → 系統鬧鐘。 */
    fun current(ctx: Context, pref: String?): Choice = decode(pref)?.takeIf { exists(ctx, it) } ?: auto(ctx)

    fun intentFor(ctx: Context, c: Choice): Intent? {
        val i = when {
            c.system -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            c.alarms && c.cls != null -> Intent(AlarmClock.ACTION_SHOW_ALARMS).setClassName(c.pkg, c.cls)
            c.cls != null -> Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(c.pkg, c.cls)
            else -> ctx.packageManager.getLaunchIntentForPackage(c.pkg)
        } ?: return null
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun intent(ctx: Context, pref: String?): Intent? = intentFor(ctx, current(ctx, pref))

    fun label(ctx: Context, c: Choice?): String {
        if (c == null) return "找不到時鐘 App（會打開順便備忘）"
        if (c.system) return "系統鬧鐘（自動）"
        val pm = ctx.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.pkg, 0)).toString() }.getOrDefault(c.pkg)
    }

    data class Option(val choice: Choice, val label: String, val section: Int) // 0 時鐘/鬧鐘, 1 其他 App

    /** 設定裡可選的：處理鬧鐘動作的、已知時鐘、名字像時鐘的，再來是其他所有 App（含系統 App）。 */
    fun options(ctx: Context): List<Option> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, Option>()
        for ((pkg, cls) in handlers(ctx, AlarmClock.ACTION_SHOW_ALARMS).sortedBy { rank(it.first) })
            out.putIfAbsent(pkg, Option(Choice(pkg, cls, true), "⏰ " + label(ctx, Choice(pkg, cls, true)) + "（鬧鐘）", 0))
        for (pkg in KNOWN + otherAlarmPkgs(ctx)) if (pkg !in out && launchable(ctx, pkg))
            out[pkg] = Option(Choice(pkg, null, false), "🕒 " + label(ctx, Choice(pkg, null, false)), 0)
        val launchers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
        val rest = launchers.map { it.activityInfo }.filter { it.packageName != ctx.packageName && it.packageName !in out }
            .map { val l = it.loadLabel(pm).toString(); Option(Choice(it.packageName, it.name, false), l, if (looksLikeClock(l, it.packageName)) 0 else 1) }
            .distinctBy { it.choice.pkg }
        sortOptions(out.values.toList() + rest).let { return it }
    }

    /** 時鐘類排前面（已知/鬧鐘處理者維持原順序），其他依名稱排序。 */
    fun sortOptions(list: List<Option>): List<Option> {
        val (clock, other) = list.partition { it.section == 0 }
        return clock + other.sortedBy { it.label.lowercase() }
    }

    fun filter(list: List<Option>, q: String): List<Option> =
        if (q.isBlank()) list else list.filter { it.label.contains(q.trim(), true) || it.choice.pkg.contains(q.trim(), true) }

    /** 診斷：各個鬧鐘動作找到哪些套件。 */
    fun diagnostics(ctx: Context, pref: String?): String {
        val parts = ACTIONS.map { a -> a.substringAfterLast('.') + "=" + handlers(ctx, a).joinToString(",") { it.first }.ifBlank { "無" } }
        val known = KNOWN.filter { launchable(ctx, it) }.joinToString(",").ifBlank { "無" }
        val c = current(ctx, pref)
        return parts.joinToString("；") + "；已知時鐘=" + known + "；目前=" + (if (c.system) "系統SHOW_ALARMS" else c.pkg + (c.cls?.let { "/$it" } ?: ""))
    }
}
