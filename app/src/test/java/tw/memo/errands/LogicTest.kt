package tw.memo.errands

import org.junit.Assert.*
import org.junit.Test
import tw.memo.errands.data.*
import tw.memo.errands.logic.Logic
import tw.memo.errands.logic.Sale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LogicTest {
    private val mon = LocalDate.of(2026, 11, 16) // 週一
    private fun once(id: Long, d: LocalDate, done: String? = null, pid: Long? = null) =
        Errand(id = id, title = "買牛奶$id", type = "buy", dueDate = d.toString(), doneOn = done, purchaseId = pid)
    private fun weekly(id: Long, days: String, done: String? = null) =
        Errand(id = id, title = "倒垃圾", repeat = Repeat.WEEKLY, weekdays = days, doneOn = done)

    @Test fun dowSundayZero() {
        assertEquals(1, Logic.dow(mon)); assertEquals(0, Logic.dow(LocalDate.of(2026, 11, 22)))
    }
    @Test fun defaultTomorrowAfter18() {
        assertFalse(Logic.defaultTomorrow(LocalTime.of(17, 59)))
        assertTrue(Logic.defaultTomorrow(LocalTime.of(18, 0)))
        assertTrue(Logic.defaultTomorrow(LocalTime.of(23, 30)))
    }
    @Test fun dueOnOnceDailyWeekly() {
        assertTrue(Logic.isDueOn(once(1, mon), mon))
        assertFalse(Logic.isDueOn(once(1, mon), mon.plusDays(1)))
        assertTrue(Logic.isDueOn(Errand(title = "x", repeat = Repeat.DAILY), mon))
        assertTrue(Logic.isDueOn(weekly(2, "1,3"), mon))
        assertFalse(Logic.isDueOn(weekly(2, "3"), mon))
    }
    @Test fun repeatDoneOnlyForThatDay() {
        val w = weekly(1, "1,2", done = mon.toString())
        assertTrue(Logic.isDone(w, mon)); assertFalse(Logic.isDone(w, mon.plusDays(1)))
        assertTrue(Logic.isDone(once(2, mon, done = mon.minusDays(3).toString()), mon))
    }
    @Test fun overdueShownTodayAndInPending() {
        val old = once(1, mon.minusDays(2))
        assertTrue(Logic.isOverdue(old, mon))
        assertEquals(listOf(old), Logic.pendingToday(listOf(old), mon))
        assertFalse(Logic.isOverdue(once(2, mon.minusDays(2), done = "x"), mon))
    }
    @Test fun completingOverdueMovesToToday() {
        val c = Logic.complete(once(1, mon.minusDays(2)), mon)
        assertEquals(mon.toString(), c.dueDate); assertEquals(mon.toString(), c.doneOn)
        assertTrue(Logic.pendingToday(listOf(c), mon).isEmpty())
    }
    @Test fun pendingAndTomorrow() {
        val list = listOf(once(1, mon), once(2, mon, done = mon.toString()), once(3, mon.plusDays(1)), weekly(4, "2"), weekly(5, "1"))
        assertEquals(listOf(1L, 5L), Logic.pendingToday(list, mon).map { it.id })
        assertEquals(listOf(3L, 4L), Logic.dueTomorrow(list, mon).map { it.id })
    }
    @Test fun deferOnce() {
        val r = Logic.defer(once(1, mon), mon)
        assertEquals(1, r.size); assertEquals(mon.plusDays(1).toString(), r[0].dueDate); assertEquals(1L, r[0].id)
    }
    @Test fun deferOverdueGoesToTomorrowNotDayAfterOldDate() {
        assertEquals(mon.plusDays(1).toString(), Logic.defer(once(1, mon.minusDays(5)), mon)[0].dueDate)
    }
    @Test fun deferRepeatSkipsTodayAndAddsOneOff() {
        val r = Logic.defer(weekly(7, "1"), mon, 99)
        assertEquals(2, r.size)
        assertEquals(mon.toString(), r[0].doneOn); assertEquals(7L, r[0].id)
        assertEquals(0L, r[1].id); assertEquals(Repeat.ONCE, r[1].repeat); assertEquals(mon.plusDays(1).toString(), r[1].dueDate)
        val after = listOf(r[0], r[1].copy(id = 8))
        assertTrue(Logic.pendingToday(after, mon).isEmpty())
        assertEquals(listOf(8L), Logic.dueTomorrow(after, mon).map { it.id })
    }
    @Test fun nextTriggerSameDayLaterAndWeekdays() {
        val t = LocalTime.of(17, 50); val wk = setOf(1, 2, 3, 4, 5)
        assertEquals(mon.atTime(t), Logic.nextTrigger(mon.atTime(9, 0), t, wk))
        assertEquals(mon.plusDays(1).atTime(t), Logic.nextTrigger(mon.atTime(t), t, wk)) // 剛好到點 → 下一次
        val fri = LocalDate.of(2026, 11, 20)
        assertEquals(LocalDate.of(2026, 11, 23).atTime(t), Logic.nextTrigger(fri.atTime(18, 0), t, wk))
        assertNull(Logic.nextTrigger(mon.atTime(9, 0), t, emptySet()))
    }
    private val nine = LocalTime.of(9, 0)
    private val ten = LocalTime.of(22, 0)

    @Test fun coveredByNight() {
        val t = LocalDateTime.of(2026, 11, 19, 22, 0) // 週四
        assertTrue(Logic.coveredByNight(t, ten, setOf(0, 1, 2, 3, 4, 5, 6)))
        assertFalse(Logic.coveredByNight(t, ten, setOf(1)))
        assertFalse(Logic.coveredByNight(t, LocalTime.of(21, 30), setOf(4)))
    }
    @Test fun addPurchaseToTomorrowLinkedNoDup() {
        val buy = Purchase(id = 9, title = "藍牙耳機", startDate = "2026-11-17", endDate = "2026-11-17")
        val e = Logic.errandFromPurchase(buy, mon.plusDays(1))
        assertEquals(mon.plusDays(1).toString(), e.dueDate); assertEquals(ItemType.BUY, e.type); assertEquals(9L, e.purchaseId)
        assertEquals(listOf(e), Logic.dueTomorrow(listOf(e), mon))
        assertFalse(Logic.shouldAddLinked(listOf(e.copy(id = 1)), 9))
    }
    @Test fun labels() {
        assertEquals("週一至週五", Logic.daysLabel(setOf(1, 2, 3, 4, 5)))
        assertEquals("明天", Logic.whenLabel(once(1, mon.plusDays(1)), mon))
        assertTrue(Logic.whenLabel(once(1, mon.minusDays(1)), mon).startsWith("已過期"))
        assertEquals("buy", Logic.inferType("買牛奶")); assertEquals("take", Logic.inferType("拿包裹"))
    }

    // ---- 採購 ↔ 下班順便做 連結 ----
    private val sale = Purchase(id = 5, title = "藍牙耳機", note = "雙11", startDate = "2026-11-16", endDate = "2026-11-20")

    @Test fun errandFromPurchaseIsTodayBuyLinked() {
        val e = Logic.errandFromPurchase(sale, mon, 1)
        assertEquals("藍牙耳機", e.title); assertEquals(ItemType.BUY, e.type); assertEquals(5L, e.purchaseId)
        assertEquals(mon.toString(), e.dueDate); assertEquals(Repeat.ONCE, e.repeat); assertEquals(0L, e.id)
        assertEquals(listOf(e), Logic.pendingToday(listOf(e), mon))
    }
    @Test fun noDuplicateLinkedWhileOpen() {
        assertTrue(Logic.shouldAddLinked(emptyList(), 5))
        assertFalse(Logic.shouldAddLinked(listOf(once(1, mon, pid = 5)), 5))
        assertTrue(Logic.shouldAddLinked(listOf(once(1, mon, done = mon.toString(), pid = 5)), 5))
        assertTrue(Logic.shouldAddLinked(listOf(once(1, mon, pid = 6)), 5))
    }
    @Test fun checkingErrandMarksPurchaseBought() {
        val b = Logic.purchaseAfterErrandToggle(sale, true, mon)
        assertEquals(mon.toString(), b.boughtOn)
        assertEquals(Sale.Status.BOUGHT, Sale.status(b, mon))
        assertNull(Logic.purchaseAfterErrandToggle(b, false, mon).boughtOn)
        // 已買的不改日期
        assertEquals("2026-11-10", Logic.purchaseAfterErrandToggle(sale.copy(boughtOn = "2026-11-10"), true, mon).boughtOn)
    }
    @Test fun checkingPurchaseCompletesLinkedErrand() {
        val list = listOf(once(1, mon, pid = 5), once(2, mon, pid = 6), once(3, mon.minusDays(1), pid = 5))
        val up = Logic.linkedAfterPurchaseToggle(list, 5, true, mon)
        assertEquals(listOf(1L, 3L), up.map { it.id })
        assertTrue(up.all { it.doneOn == mon.toString() })
        assertEquals(mon.toString(), up[1].dueDate) // 過期的也移到今天完成
        val undo = Logic.linkedAfterPurchaseToggle(up + list[1], 5, false, mon)
        assertEquals(listOf(1L, 3L), undo.map { it.id }); assertTrue(undo.all { it.doneOn == null })
        assertTrue(Logic.linkedAfterPurchaseToggle(list, 5, false, mon).isEmpty())
    }
    @Test fun deferKeepsLink() {
        assertEquals(5L, Logic.defer(once(1, mon, pid = 5), mon)[0].purchaseId)
    }
}
