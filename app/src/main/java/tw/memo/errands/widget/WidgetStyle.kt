package tw.memo.errands.widget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 小工具外觀：底色（淺/深/跟隨系統）＋透明度（高/中/低），全部是純資料，方便測試。 */
object WidgetStyle {
    const val TINT_LIGHT = "light"
    const val TINT_DARK = "dark"
    const val TINT_SYSTEM = "system"
    const val ALPHA_HIGH = "high" // 最透明
    const val ALPHA_MID = "mid"
    const val ALPHA_LOW = "low"   // 最不透明
    const val DEFAULT_TINT = TINT_DARK
    const val DEFAULT_ALPHA = ALPHA_MID

    fun tintOf(s: String?) = if (s == TINT_LIGHT || s == TINT_DARK || s == TINT_SYSTEM) s else DEFAULT_TINT
    fun alphaOf(s: String?) = if (s == ALPHA_HIGH || s == ALPHA_MID || s == ALPHA_LOW) s else DEFAULT_ALPHA

    /** 背景不透明度（0~1），跟 DEMO 一樣：淺色 .20/.42/.68、深色 .26/.48/.72。 */
    fun bgOpacity(dark: Boolean, alpha: String): Float = when (alphaOf(alpha)) {
        ALPHA_HIGH -> if (dark) .26f else .20f
        ALPHA_LOW -> if (dark) .72f else .68f
        else -> if (dark) .48f else .42f
    }

    fun argb(a: Float, rgb: Int): Int = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    /** 一組顏色（ARGB Int）。 */
    data class Palette(
        val bg: Int, val ink: Int, val soft: Int, val accent: Int, val tile: Int, val tileLine: Int,
        val outline: Int, val divider: Int, val track: Int, val plus: Int, val onPlus: Int,
        val takeFg: Int, val takeBg: Int, val buyFg: Int, val buyBg: Int, val ok: Int,
    )

    fun palette(dark: Boolean, alpha: String): Palette = if (dark) Palette(
        bg = argb(bgOpacity(true, alpha), 0x100C16), ink = 0xFFFFFFFF.toInt(), soft = 0xE6FFFFFF.toInt(),
        accent = 0xFFFF9A62.toInt(), tile = argb(.30f, 0x0C0912), tileLine = argb(.10f, 0xFFFFFF),
        outline = argb(.80f, 0xFFFFFF), divider = argb(.16f, 0xFFFFFF), track = argb(.24f, 0xFFFFFF),
        plus = 0xFFFF9A62.toInt(), onPlus = 0xFFFFFFFF.toInt(),
        takeFg = 0xFF8FE6E3.toInt(), takeBg = argb(.85f, 0x144648), buyFg = 0xFFFFC08E.toInt(), buyBg = argb(.85f, 0x5A3216),
        ok = 0xFF6BD59C.toInt(),
    ) else Palette(
        bg = argb(bgOpacity(false, alpha), 0xFFFFFF), ink = 0xFF1D1714.toInt(), soft = 0xFF3F3530.toInt(),
        accent = 0xFFC9561B.toInt(), tile = argb(.55f, 0xFFFFFF), tileLine = argb(.10f, 0x28160E),
        outline = argb(.60f, 0x281C16), divider = argb(.14f, 0x281C16), track = argb(.60f, 0xFFFFFF),
        plus = 0xFFE8743B.toInt(), onPlus = 0xFFFFFFFF.toInt(),
        takeFg = 0xFF0F7F86.toInt(), takeBg = 0xFFD2F1EE.toInt(), buyFg = 0xFFC25410.toInt(), buyBg = 0xFFFFE3CC.toInt(),
        ok = 0xFF2E9E6B.toInt(),
    )

    /** 版面尺寸（dp）：左欄寬度、時鐘字級、日期字級。跟 DEMO 一樣 4×2 佔 36%、4×4 佔 40%。 */
    data class Metrics(val large: Boolean, val clockCol: Float, val clockSp: Float, val dateSp: Float)

    fun metrics(widthDp: Float, heightDp: Float): Metrics {
        val large = heightDp >= 220f
        val col = widthDp * (if (large) .40f else .36f)
        val inner = max(40f, col - 20f) // 左右 padding
        val clock = min(inner * .76f, heightDp * (if (large) .26f else .36f))
        val date = (inner * .115f).coerceIn(11f, 15f)
        return Metrics(large, col, max(28f, clock), date)
    }

    /** 點時鐘要打開哪個時鐘 App：優先系統「顯示鬧鐘」，否則找已安裝的常見時鐘。 */
    val CLOCK_PACKAGES = listOf(
        "com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
        "com.miui.clock", "com.oneplus.deskclock", "com.coloros.alarmclock", "com.htc.android.worldclock",
        "com.asus.deskclock", "com.sonymobile.alarm", "com.motorola.timeweatherwidget",
    )

    sealed interface ClockTarget {
        object ShowAlarms : ClockTarget
        data class Package(val pkg: String) : ClockTarget
        object None : ClockTarget
    }

    fun chooseClock(showAlarmsResolves: Boolean, launchable: Set<String>): ClockTarget = when {
        showAlarmsResolves -> ClockTarget.ShowAlarms
        else -> CLOCK_PACKAGES.firstOrNull { it in launchable }?.let { ClockTarget.Package(it) } ?: ClockTarget.None
    }
}
