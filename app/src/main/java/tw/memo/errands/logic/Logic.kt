package tw.memo.errands.logic

import tw.memo.errands.data.Errand
import tw.memo.errands.data.ItemType
import tw.memo.errands.data.Purchase
import tw.memo.errands.data.Repeat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 純函式：日期、重複、延期與提醒時間計算（有單元測試）。週日=0 … 週六=6。 */
object Logic {
    val WEEKDAYS = listOf("日", "一", "二", "三", "四", "五", "六")

    fun dow(d: LocalDate): Int = d.dayOfWeek.value % 7
    fun parseDays(s: String): Set<Int> = s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..6 }.toSet()
    fun daysStr(s: Set<Int>): String = s.sorted().joinToString(",")
    fun parseTime(s: String): LocalTime = runCatching { LocalTime.parse(s) }.getOrDefault(LocalTime.of(9, 0))
    fun timeStr(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

    /** 晚上 6 點（含）以後新增，預設排到明天。 */
    fun defaultTomorrow(now: LocalTime): Boolean = now.hour >= 18

    fun isDueOn(e: Errand, d: LocalDate): Boolean = when (e.repeat) {
        Repeat.ONCE -> e.dueDate == d.toString()
        Repeat.DAILY -> true
        Repeat.WEEKLY -> dow(d) in parseDays(e.weekdays)
        else -> false
    }

    fun isDone(e: Errand, d: LocalDate): Boolean =
        if (e.repeat == Repeat.ONCE) e.doneOn != null else e.doneOn == d.toString()

    /** 一次性、未完成、日期已過。 */
    fun isOverdue(e: Errand, today: LocalDate): Boolean =
        e.repeat == Repeat.ONCE && e.doneOn == null && e.dueDate != null && e.dueDate < today.toString()

    /** 某天清單要顯示的項目（今天會連同過期未做的一起顯示）。 */
    fun visibleOn(list: List<Errand>, d: LocalDate, today: LocalDate): List<Errand> =
        list.filter { isDueOn(it, d) || (d == today && isOverdue(it, today)) }

    /** 今天還沒做完的。 */
    fun pendingToday(list: List<Errand>, today: LocalDate): List<Errand> =
        visibleOn(list, today, today).filter { !isDone(it, today) }

    /** 明天下班要做的（未完成）。 */
    fun dueTomorrow(list: List<Errand>, today: LocalDate): List<Errand> {
        val t = today.plusDays(1)
        return list.filter { isDueOn(it, t) && !isDone(it, t) }
    }

    /** 打勾完成；過期項目完成時日期改成今天，避免隔天又顯示。 */
    fun complete(e: Errand, today: LocalDate): Errand {
        val due = if (e.repeat == Repeat.ONCE && e.dueDate != null && e.dueDate < today.toString()) today.toString() else e.dueDate
        return e.copy(doneOn = today.toString(), dueDate = due)
    }

    fun uncomplete(e: Errand): Errand = e.copy(doneOn = null)

    /** 延到明天：一次性 → 改日期；重複 → 今天標記略過，並新增一筆明天的一次性項目（id=0 代表要 insert）。 */
    fun defer(e: Errand, today: LocalDate, nowMillis: Long = 0): List<Errand> {
        val tomorrow = today.plusDays(1).toString()
        return if (e.repeat == Repeat.ONCE) listOf(e.copy(dueDate = tomorrow, doneOn = null))
        else listOf(
            e.copy(doneOn = today.toString()),
            Errand(title = e.title, type = e.type, note = e.note, repeat = Repeat.ONCE, dueDate = tomorrow, createdAt = nowMillis),
        )
    }

    /** 下一次（嚴格晚於 now）符合星期的觸發時間；沒選星期回傳 null。 */
    fun nextTrigger(now: LocalDateTime, time: LocalTime, days: Set<Int>): LocalDateTime? {
        if (days.isEmpty()) return null
        for (i in 0..7) {
            val c = now.toLocalDate().plusDays(i.toLong()).atTime(time)
            if (c.isAfter(now) && dow(c.toLocalDate()) in days) return c
        }
        return null
    }

    /** 這個採購提醒時間剛好是晚上確認會響的時候 → 合併到晚上確認，不另外跳。 */
    fun coveredByNight(trigger: LocalDateTime, nightTime: LocalTime, nightDays: Set<Int>): Boolean =
        trigger.toLocalTime() == nightTime && dow(trigger.toLocalDate()) in nightDays

    /** 晚上確認的標題與內容；全部都空回傳 null（不提醒）。sales = 明天有特價事件的採購項目。 */
    fun nightMessage(pending: List<Errand>, tomorrow: List<Errand>, sales: List<Pair<Purchase, Sale.Event>>): Pair<String, String>? {
        if (pending.isEmpty() && tomorrow.isEmpty() && sales.isEmpty()) return null
        fun el(l: List<Errand>) = l.joinToString("\n") { "【${typeLabel(it.type)}】${it.title}" }
        val parts = mutableListOf<String>()
        if (pending.isNotEmpty()) parts += "還沒做完（${pending.size}）：\n" + el(pending)
        if (tomorrow.isNotEmpty()) parts += "明天下班要做（${tomorrow.size}）：\n" + el(tomorrow)
        if (sales.isNotEmpty()) parts += "明天特價（${sales.size}）：\n" +
            sales.joinToString("\n") { (p, ev) -> "🛒 ${p.title}（${Sale.tomorrowLabel(ev)}）" }
        val title = if (pending.isNotEmpty()) "這些還沒做完，要延到明天嗎？"
        else listOfNotNull(
            if (tomorrow.isNotEmpty()) "下班要做 ${tomorrow.size} 項" else null,
            if (sales.isNotEmpty()) "特價 ${sales.size} 項" else null,
        ).joinToString("・", prefix = "明天：")
        return title to parts.joinToString("\n\n")
    }

    fun daysBetween(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b)

    fun dateLabel(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日 週${WEEKDAYS[dow(d)]}"
    fun shortDate(d: LocalDate): String = "${d.monthValue}/${d.dayOfMonth} 週${WEEKDAYS[dow(d)]}"

    fun repeatLabel(e: Errand): String = when (e.repeat) {
        Repeat.DAILY -> "每天"
        Repeat.WEEKLY -> daysLabel(parseDays(e.weekdays))
        else -> ""
    }

    fun daysLabel(days: Set<Int>): String {
        val d = days.sorted()
        return when {
            d.isEmpty() -> "還沒選星期"
            d.size == 7 -> "每天"
            d == listOf(1, 2, 3, 4, 5) -> "週一至週五"
            d == listOf(0, 6) -> "週六、週日"
            else -> d.joinToString("、") { "週" + WEEKDAYS[it] }
        }
    }

    fun whenLabel(e: Errand, today: LocalDate): String {
        if (e.repeat != Repeat.ONCE) return repeatLabel(e)
        val d = e.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return ""
        return when {
            d == today -> "今天"
            d == today.plusDays(1) -> "明天"
            d.isBefore(today) -> if (e.doneOn == null) "已過期 · ${shortDate(d)}" else shortDate(d)
            else -> shortDate(d)
        }
    }

    fun typeLabel(type: String): String = if (type == "buy") "買" else "拿"

    /** 依標題猜類型：「買…」或含「加油」→ 買，其他 → 拿。 */
    fun inferType(title: String): String = if (title.trim().startsWith("買") || title.contains("加油")) "buy" else "take"

    // ---- 採購清單 ↔ 下班順便做 連結 ----

    /** 從採購項目建立今天的「買」項目（連結 purchaseId）。 */
    fun errandFromPurchase(p: Purchase, day: LocalDate, nowMillis: Long = 0): Errand =
        Errand(title = p.title, type = ItemType.BUY, note = p.note, repeat = Repeat.ONCE,
            dueDate = day.toString(), createdAt = nowMillis, purchaseId = p.id)

    /** 已經有一筆連結且未完成的項目就不重複加入（不論是今天或明天）。 */
    fun shouldAddLinked(existing: List<Errand>, purchaseId: Long): Boolean =
        existing.none { it.purchaseId == purchaseId && it.doneOn == null }

    /** 連結的順便做項目打勾/取消後，採購項目的新狀態。 */
    fun purchaseAfterErrandToggle(p: Purchase, errandDone: Boolean, today: LocalDate): Purchase =
        if (errandDone) (if (p.boughtOn == null) p.copy(boughtOn = today.toString()) else p) else p.copy(boughtOn = null)

    /** 採購項目勾成已買/取消後，連結的順便做項目要更新成什麼（只回傳有變的）。 */
    fun linkedAfterPurchaseToggle(errands: List<Errand>, purchaseId: Long, bought: Boolean, today: LocalDate): List<Errand> =
        errands.filter { it.purchaseId == purchaseId }.mapNotNull { e ->
            when {
                bought && e.doneOn == null -> complete(e, today)
                !bought && e.doneOn != null -> uncomplete(e)
                else -> null
            }
        }

}
