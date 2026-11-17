package tw.memo.errands.logic

import tw.memo.errands.data.PKind
import tw.memo.errands.data.Purchase
import tw.memo.errands.data.Timing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 採購清單「特價期間／不設期限」的純函式（有單元測試）。 */
object Sale {
    enum class Event { START, END, SINGLE }
    enum class Status { NONE, UPCOMING, ACTIVE, ENDED, BOUGHT }

    /** 一次提醒：at = 跳出時間；eventDate = 特價開始/結束那天；night = 前一晚提醒（eventDate 是隔天）。 */
    data class Reminder(val at: LocalDateTime, val eventDate: LocalDate, val event: Event, val night: Boolean)

    private fun parse(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    /** 特價期間（結束早於開始時視為同一天）；不設期限或日期壞掉 → null。 */
    fun range(p: Purchase): Pair<LocalDate, LocalDate>? {
        if (p.kind != PKind.PERIOD) return null
        val s = parse(p.startDate) ?: parse(p.endDate) ?: return null
        val e = parse(p.endDate)?.takeIf { !it.isBefore(s) } ?: s
        return s to e
    }

    /** 特價事件：開始、結束；開始=結束時只有一個 SINGLE。已買/不設期限 → 空。 */
    fun events(p: Purchase): List<Pair<LocalDate, Event>> {
        if (p.boughtOn != null) return emptyList()
        val (s, e) = range(p) ?: return emptyList()
        return if (s == e) listOf(s to Event.SINGLE) else listOf(s to Event.START, e to Event.END)
    }

    /** 所有提醒時間：night → 事件前一天 nightTime；morning → 事件當天 dayTime。 */
    fun reminders(p: Purchase, dayTime: LocalTime, nightTime: LocalTime): List<Reminder> =
        events(p).map { (d, ev) ->
            if (p.timing == Timing.MORNING) Reminder(d.atTime(dayTime), d, ev, false)
            else Reminder(d.minusDays(1).atTime(nightTime), d, ev, true)
        }

    /** 下一個要排程的提醒（嚴格晚於 now）。skip 為 true 的（例如已合併在晚上確認）略過。
     *  前一晚提醒錯過（例如太晚新增、手機關機）時，補在事件當天 dayTime。 */
    fun next(p: Purchase, now: LocalDateTime, dayTime: LocalTime, nightTime: LocalTime,
             skip: (Reminder) -> Boolean = { false }): Reminder? {
        val pool = mutableListOf<Reminder>()
        reminders(p, dayTime, nightTime).forEach { r ->
            if (r.at.isAfter(now)) pool += r
            else if (r.night) {
                val catchUp = Reminder(r.eventDate.atTime(dayTime), r.eventDate, r.event, false)
                if (catchUp.at.isAfter(now)) pool += catchUp
            }
        }
        return pool.filter { !skip(it) }.minByOrNull { it.at }
    }

    /** 某天有特價事件（未買）的項目。 */
    fun eventsOn(list: List<Purchase>, date: LocalDate): List<Pair<Purchase, Event>> =
        list.flatMap { p -> events(p).filter { it.first == date }.map { p to it.second } }

    fun status(p: Purchase, today: LocalDate): Status {
        if (p.boughtOn != null) return Status.BOUGHT
        val (s, e) = range(p) ?: return Status.NONE
        return when {
            today.isBefore(s) -> Status.UPCOMING
            today.isAfter(e) -> Status.ENDED
            else -> Status.ACTIVE
        }
    }

    fun tomorrowLabel(ev: Event): String = when (ev) {
        Event.START -> "明天開始特價"
        Event.END -> "明天特價就結束了"
        Event.SINGLE -> "明天特價（只有一天）"
    }

    fun todayLabel(ev: Event): String = when (ev) {
        Event.START -> "今天開始特價"
        Event.END -> "今天特價最後一天"
        Event.SINGLE -> "今天特價（只有一天）"
    }

    fun alertTitle(p: Purchase, r: Reminder): String =
        (if (r.night) tomorrowLabel(r.event) else todayLabel(r.event)) + "：" + p.title

    fun rangeLabel(p: Purchase): String {
        val (s, e) = range(p) ?: return "不設期限"
        return if (s == e) "${Logic.shortDate(s)}（一天）" else "${Logic.shortDate(s)} – ${Logic.shortDate(e)}"
    }

    fun statusLabel(p: Purchase, today: LocalDate): String {
        val r = range(p)
        return when (status(p, today)) {
            Status.BOUGHT -> "已買"
            Status.NONE -> "不設期限"
            Status.UPCOMING -> Logic.daysBetween(today, r!!.first).let { if (it == 1L) "即將開始 · 明天" else "即將開始 · 還有 $it 天" }
            Status.ACTIVE -> Logic.daysBetween(today, r!!.second).let { if (it == 0L) "特價中 · 今天最後一天" else "特價中 · 還剩 ${it + 1} 天" }
            Status.ENDED -> "已結束"
        }
    }

    fun timingLabel(timing: String): String = if (timing == Timing.MORNING) "當天早上" else "前一天晚上"

    fun addLabel(day: LocalDate, today: LocalDate): String =
        if (day == today) "加入今天下班要做" else if (day == today.plusDays(1)) "加入明天下班要做" else "加入 ${Logic.shortDate(day)} 下班要做"
}
