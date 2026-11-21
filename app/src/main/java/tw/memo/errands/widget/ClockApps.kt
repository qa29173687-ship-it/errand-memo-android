package tw.memo.errands.widget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.provider.Settings
import tw.memo.errands.data.Prefs
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 點小工具時間要打開的時鐘 App。只記「套件名」，一律用該 App 的啟動器 intent（跟點桌面圖示一樣）打開，
 * 不再直接指定 HandleApiActivity 這類「只處理 API、不顯示畫面」的 activity（OPPO 上會一閃就結束）。
 */
object ClockApps {
    /** 已知的內建時鐘（順序＝優先順序）。OPPO/ColorOS 放最前面。 */
    val KNOWN = listOf(
        "com.coloros.alarmclock", "com.oplus.alarmclock", "com.oppo.alarmclock", "com.oplus.clock", "com.coloros.clock",
        "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
        "com.oneplus.deskclock", "com.android.alarmclock", "com.miui.clock", "com.vivo.alarmclock",
        "com.android.BBKClock", "com.huawei.deskclock", "com.hihonor.deskclock", "com.asus.deskclock",
        "com.sonymobile.alarm", "com.htc.android.worldclock", "com.motorola.timeweatherwidget",
    )

    /** pkg＝要開的 App；system=true：不指定 App，交給系統（隱含的 SHOW_ALARMS）。 */
    data class Choice(val pkg: String, val system: Boolean = false)

    val SYSTEM = Choice("", system = true)

    /** 會用來找時鐘的動作（依序）。 */
    val ACTIONS = listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM, AlarmClock.ACTION_SHOW_TIMERS, AlarmClock.ACTION_SET_TIMER)

    fun looksLikeClock(label: String, pkg: String): Boolean {
        val l = label.lowercase(); val p = pkg.lowercase()
        return listOf("時鐘", "时钟", "鬧鐘", "闹钟", "clock", "alarm").any { it in l } || "clock" in p || "alarm" in p
    }

    fun rank(pkg: String): Int = KNOWN.indexOf(pkg).let { if (it < 0) Int.MAX_VALUE else it }

    /** 自動：可啟動的已知時鐘（OPPO 優先）→ 處理鬧鐘動作的 App（要可啟動）→ 系統鬧鐘。 */
    fun pickAuto(alarmPkgs: List<String>, launchable: Collection<String>): Choice =
        (KNOWN.filter { it in launchable } + alarmPkgs.sortedBy { rank(it) }.filter { it in launchable })
            .firstOrNull()?.let { Choice(it) } ?: SYSTEM

    /** 新格式 p|套件；s|| 系統。舊版（v1.0.6/1.0.7）的 a|套件|類別、l|套件|類別 只取套件（遷移）。 */
    fun encode(c: Choice): String = if (c.system) "s||" else "p|${c.pkg}"
    fun decode(s: String?): Choice? {
        val p = s?.split('|') ?: return null
        if (p.firstOrNull() == "s") return SYSTEM
        if (p.size < 2 || p[1].isBlank() || p[0] !in setOf("p", "a", "l")) return null
        return Choice(p[1])
    }
    /** 把舊格式（含 HandleApiActivity 這種類別）改存成只有套件。空字串（自動）不變。 */
    fun migrate(pref: String?): String = decode(pref)?.let { encode(it) } ?: ""

    // ---- Android ----

    private fun handlers(ctx: Context, action: String): List<Pair<String, String>> = runCatching {
        ctx.packageManager.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName to it.activityInfo.name }
            .filter { it.first != ctx.packageName && it.first != "android" }
    }.getOrDefault(emptyList())

    private fun alarmPkgs(ctx: Context): List<String> = ACTIONS.flatMap { handlers(ctx, it) }.map { it.first }.distinct()

    fun launcherIntent(ctx: Context, pkg: String): Intent? =
        runCatching { ctx.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()

    private fun launchable(ctx: Context, pkg: String) = launcherIntent(ctx, pkg) != null

    fun auto(ctx: Context): Choice {
        val alarm = alarmPkgs(ctx)
        return pickAuto(alarm, (KNOWN + alarm).distinct().filter { launchable(ctx, it) })
    }

    /** 目前要用的：自選（還能啟動的話）→ 自動 → 系統鬧鐘。 */
    fun current(ctx: Context, pref: String?): Choice =
        decode(pref)?.takeIf { it.system || launchable(ctx, it.pkg) } ?: auto(ctx)

    /** 嘗試順序的一步：name 給診斷用。 */
    data class Step(val name: String, val intent: Intent)

    /** 依序嘗試：選的 App 啟動器 → 其他偵測到的時鐘啟動器 → 系統 SHOW_ALARMS（不指定元件）→ 日期時間設定。 */
    fun plan(ctx: Context, pref: String?): List<Step> {
        val c = current(ctx, pref)
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        val pkgs = ((if (c.system) emptyList() else listOf(c.pkg)) + KNOWN + alarmPkgs(ctx).sortedBy { rank(it) }).distinct()
        val out = ArrayList<Step>()
        for (p in pkgs) launcherIntent(ctx, p)?.let { out += Step("啟動器 $p", Intent(it).addFlags(flags)) }
        // 隱含 SHOW_ALARMS 排在啟動器後面：OPPO 上它會跑到 HandleApiActivity，「成功」但看不到畫面
        out += Step("系統SHOW_ALARMS", Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(flags))
        out += Step("日期時間設定", Intent(Settings.ACTION_DATE_SETTINGS).addFlags(flags))
        return out
    }

    /**
     * 小工具點時間、設定裡的「測試」都走這裡（經由 ClockLaunchActivity）。
     * 每一步都 try/catch，結果記到 Prefs.clockLastLaunch 給診斷資訊顯示。回傳成功的那一步名稱（全失敗 null）。
     */
    fun launch(ctx: Context, pref: String? = Prefs(ctx).clockApp): String? {
        val prefs = Prefs(ctx)
        val migrated = migrate(pref)
        if (pref != null && migrated != pref) prefs.clockApp = migrated
        val log = ArrayList<String>()
        var ok: String? = null
        for ((i, s) in plan(ctx, migrated).withIndex()) {
            try {
                ctx.startActivity(s.intent)
                log += "${i + 1}.${s.name}→成功"; ok = s.name; break
            } catch (e: Exception) {
                log += "${i + 1}.${s.name}→失敗(${e.javaClass.simpleName}: ${e.message?.take(80)})"
            }
        }
        prefs.clockLastLaunch = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + log.joinToString("；")
        return ok
    }

    fun label(ctx: Context, c: Choice?): String {
        if (c == null) return "找不到時鐘 App"
        if (c.system) return "系統鬧鐘（自動）"
        val pm = ctx.packageManager
        return runCatching { pm.getApplicationLabel(pm.getApplicationInfo(c.pkg, 0)).toString() }.getOrDefault(c.pkg)
    }

    data class Option(val choice: Choice, val label: String, val section: Int) // 0 時鐘/鬧鐘, 1 其他 App

    /** 設定裡可選的 App（都是可以從桌面圖示打開的；只記套件）。時鐘類排前面。 */
    fun options(ctx: Context): List<Option> {
        val pm = ctx.packageManager
        val out = LinkedHashMap<String, Option>()
        for (pkg in (KNOWN + alarmPkgs(ctx).sortedBy { rank(it) }).distinct()) if (pkg != ctx.packageName && launchable(ctx, pkg))
            out[pkg] = Option(Choice(pkg), "⏰ " + label(ctx, Choice(pkg)), 0)
        val launchers = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())
        val rest = launchers.map { it.activityInfo }.filter { it.packageName != ctx.packageName && it.packageName !in out }
            .map { val l = it.loadLabel(pm).toString(); Option(Choice(it.packageName), l, if (looksLikeClock(l, it.packageName)) 0 else 1) }
            .distinctBy { it.choice.pkg }
        return sortOptions(out.values.toList() + rest)
    }

    fun sortOptions(list: List<Option>): List<Option> {
        val (clock, other) = list.partition { it.section == 0 }
        return clock + other.sortedBy { it.label.lowercase() }
    }

    fun filter(list: List<Option>, q: String): List<Option> =
        if (q.isBlank()) list else list.filter { it.label.contains(q.trim(), true) || it.choice.pkg.contains(q.trim(), true) }

    /** 診斷：版本、各鬧鐘動作的處理者、目前選擇、嘗試順序、上次點擊結果。 */
    fun diagnostics(ctx: Context, pref: String?): String {
        val parts = ACTIONS.map { a -> a.substringAfterLast('.') + "=" + handlers(ctx, a).joinToString(",") { it.first }.ifBlank { "無" } }
        val known = KNOWN.filter { launchable(ctx, it) }.joinToString(",").ifBlank { "無" }
        val c = current(ctx, pref)
        val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        return "v$ver；" + parts.joinToString("；") + "；可啟動時鐘=" + known +
            "；目前=" + (if (c.system) "系統SHOW_ALARMS" else c.pkg) +
            "；順序=" + plan(ctx, pref).joinToString(" > ") { it.name } +
            "；上次=" + Prefs(ctx).clockLastLaunch.ifBlank { "還沒點過" }
    }
}
