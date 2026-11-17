package tw.memo.errands

import org.junit.Assert.*
import org.junit.Test
import tw.memo.errands.data.*
import tw.memo.errands.logic.Logic
import tw.memo.errands.logic.Sale
import tw.memo.errands.logic.Sale.Event
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class SaleTest {
    private val mon = LocalDate.of(2026, 11, 16) // 週一
    private val nine = LocalTime.of(9, 0)
    private val ten = LocalTime.of(22, 0)
    private fun period(s: String, e: String, timing: String = Timing.NIGHT, id: Long = 1) =
        Purchase(id = id, title = "耳機", kind = PKind.PERIOD, startDate = s, endDate = e, timing = timing)
    private val noDeadline = Purchase(id = 2, title = "收納盒", kind = PKind.NONE)

    @Test fun defaultsAreNightBeforePeriod() {
        val p = Purchase(title = "x")
        assertEquals(PKind.PERIOD, p.kind); assertEquals(Timing.NIGHT, p.timing)
    }
    @Test fun rangeRemindersNightBeforeStartAndEnd() {
        val r = Sale.reminders(period("2026-11-20", "2026-11-25"), nine, ten)
        assertEquals(2, r.size)
        assertEquals(LocalDateTime.of(2026, 11, 19, 22, 0), r[0].at); assertEquals(Event.START, r[0].event); assertTrue(r[0].night)
        assertEquals(LocalDate.of(2026, 11, 20), r[0].eventDate)
        assertEquals(LocalDateTime.of(2026, 11, 24, 22, 0), r[1].at); assertEquals(Event.END, r[1].event)
        assertEquals(LocalDate.of(2026, 11, 25), r[1].eventDate)
    }
    @Test fun startEqualsEndIsSingleReminder() {
        val r = Sale.reminders(period("2026-11-20", "2026-11-20"), nine, ten)
        assertEquals(1, r.size); assertEquals(Event.SINGLE, r[0].event)
        assertEquals(LocalDateTime.of(2026, 11, 19, 22, 0), r[0].at)
        assertEquals("明天特價（只有一天）", Sale.tomorrowLabel(r[0].event))
    }
    @Test fun morningTimingOnTheDay() {
        val r = Sale.reminders(period("2026-11-20", "2026-11-25", Timing.MORNING), nine, ten)
        assertEquals(listOf(LocalDateTime.of(2026, 11, 20, 9, 0), LocalDateTime.of(2026, 11, 25, 9, 0)), r.map { it.at })
        assertFalse(r[0].night)
        assertEquals("今天開始特價：耳機", Sale.alertTitle(period("2026-11-20", "2026-11-25"), r[0]))
    }
    @Test fun noDeadlineAndBoughtProduceNoReminders() {
        assertTrue(Sale.reminders(noDeadline, nine, ten).isEmpty())
        assertNull(Sale.next(noDeadline, mon.atTime(8, 0), nine, ten))
        assertTrue(Sale.reminders(period("2026-11-20", "2026-11-25").copy(boughtOn = "2026-11-16"), nine, ten).isEmpty())
        assertTrue(Sale.eventsOn(listOf(noDeadline), mon.plusDays(1)).isEmpty())
    }
    @Test fun nextPicksUpcomingThenEnd() {
        val p = period("2026-11-20", "2026-11-25")
        assertEquals(Event.START, Sale.next(p, mon.atTime(8, 0), nine, ten)!!.event)
        val n = Sale.next(p, LocalDateTime.of(2026, 11, 21, 12, 0), nine, ten)!!
        assertEquals(Event.END, n.event); assertEquals(LocalDateTime.of(2026, 11, 24, 22, 0), n.at)
        assertNull(Sale.next(p, LocalDateTime.of(2026, 11, 25, 10, 0), nine, ten))
    }
    @Test fun missedNightReminderCatchesUpMorning() {
        val p = period("2026-11-20", "2026-11-25")
        val n = Sale.next(p, LocalDateTime.of(2026, 11, 19, 23, 0), nine, ten)!!
        assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0), n.at); assertEquals(Event.START, n.event); assertFalse(n.night)
    }
    @Test fun skipCoveredByNightGoesToNextUncovered() {
        val p = period("2026-11-20", "2026-11-25")
        val all = setOf(0, 1, 2, 3, 4, 5, 6)
        // 晚上確認每天都響 → 前一晚提醒都合併，不排單獨的鬧鐘
        assertNull(Sale.next(p, mon.atTime(8, 0), nine, ten) { it.night && Logic.coveredByNight(it.at, ten, all) })
        // 週四晚上沒有晚上確認 → 11/19（週四）的開始提醒要單獨跳
        val n = Sale.next(p, mon.atTime(8, 0), nine, ten) { it.night && Logic.coveredByNight(it.at, ten, all - 4) }!!
        assertEquals(LocalDateTime.of(2026, 11, 19, 22, 0), n.at)
    }
    @Test fun endBeforeStartTreatedAsSingleDay() {
        assertEquals(LocalDate.of(2026, 11, 20) to LocalDate.of(2026, 11, 20), Sale.range(period("2026-11-20", "2026-11-18")))
    }
    @Test fun statusAndLabels() {
        val p = period("2026-11-17", "2026-11-19")
        assertEquals(Sale.Status.UPCOMING, Sale.status(p, mon)); assertEquals("即將開始 · 明天", Sale.statusLabel(p, mon))
        assertEquals(Sale.Status.ACTIVE, Sale.status(p, mon.plusDays(1))); assertEquals("特價中 · 還剩 3 天", Sale.statusLabel(p, mon.plusDays(1)))
        assertEquals("特價中 · 今天最後一天", Sale.statusLabel(p, mon.plusDays(3)))
        assertEquals(Sale.Status.ENDED, Sale.status(p, mon.plusDays(4)))
        assertEquals(Sale.Status.NONE, Sale.status(noDeadline, mon))
        assertEquals(Sale.Status.BOUGHT, Sale.status(p.copy(boughtOn = "x"), mon))
        assertEquals("11/17 週二 – 11/19 週四", Sale.rangeLabel(p))
        assertEquals("11/17 週二（一天）", Sale.rangeLabel(period("2026-11-17", "2026-11-17")))
    }
    @Test fun eventsTomorrowForNightScreen() {
        val list = listOf(period("2026-11-17", "2026-11-20", id = 1), period("2026-11-10", "2026-11-17", id = 2),
            period("2026-11-17", "2026-11-17", id = 3), period("2026-11-18", "2026-11-19", id = 4), noDeadline,
            period("2026-11-17", "2026-11-17", id = 5).copy(boughtOn = "x"))
        val ev = Sale.eventsOn(list, mon.plusDays(1))
        assertEquals(listOf(1L to Event.START, 2L to Event.END, 3L to Event.SINGLE), ev.map { it.first.id to it.second })
    }
    @Test fun nightMessageWithSales() {
        val p = period("2026-11-17", "2026-11-20")
        assertNull(Logic.nightMessage(emptyList(), emptyList(), emptyList()))
        val (t, x) = Logic.nightMessage(emptyList(), emptyList(), listOf(p to Event.START, p.copy(id = 7, title = "拖鞋") to Event.END))!!
        assertEquals("明天：特價 2 項", t)
        assertTrue(x.contains("耳機（明天開始特價）") && x.contains("拖鞋（明天特價就結束了）"))
        val e = Errand(id = 3, title = "買牛奶", type = "buy", dueDate = mon.plusDays(1).toString())
        assertEquals("明天：下班要做 1 項・特價 1 項", Logic.nightMessage(emptyList(), listOf(e), listOf(p to Event.START))!!.first)
    }
    @Test fun addLabelAndEventDayForButton() {
        assertEquals("加入明天下班要做", Sale.addLabel(mon.plusDays(1), mon))
        assertEquals("加入今天下班要做", Sale.addLabel(mon, mon))
        val r = Sale.reminders(period("2026-11-17", "2026-11-20"), nine, ten)
        assertEquals(mon.plusDays(1), r[0].eventDate) // 11/16 晚上跳 → 加入 11/17 下班要做
        assertEquals("明天開始特價：耳機", Sale.alertTitle(period("2026-11-17", "2026-11-20"), r[0]))
        assertEquals("明天特價就結束了：耳機", Sale.alertTitle(period("2026-11-17", "2026-11-20"), r[1]))
    }
}
