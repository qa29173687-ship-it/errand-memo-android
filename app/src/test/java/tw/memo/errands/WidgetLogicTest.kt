package tw.memo.errands

import org.junit.Assert.*
import org.junit.Test
import tw.memo.errands.data.Errand
import tw.memo.errands.data.Repeat
import tw.memo.errands.widget.WidgetLogic
import java.time.LocalDate

class WidgetLogicTest {
    private val mon = LocalDate.of(2026, 11, 16) // 週一
    private fun once(id: Long, d: LocalDate, done: String? = null, created: Long = id, pid: Long? = null) =
        Errand(id = id, title = "項目$id", type = if (id % 2 == 0L) "buy" else "take", dueDate = d.toString(), doneOn = done, createdAt = created, purchaseId = pid)

    @Test fun onlyTodaysPendingErrands() {
        val list = listOf(
            once(1, mon), once(2, mon, done = mon.toString()), once(3, mon.plusDays(1)), once(4, mon.minusDays(3), done = "x"),
            Errand(id = 5, title = "倒垃圾", repeat = Repeat.WEEKLY, weekdays = "1", createdAt = 5),
            Errand(id = 6, title = "週二", repeat = Repeat.WEEKLY, weekdays = "2", createdAt = 6),
            Errand(id = 7, title = "每天", repeat = Repeat.DAILY, doneOn = mon.toString(), createdAt = 7),
        )
        val s = WidgetLogic.state(list, mon)
        assertEquals(listOf(1L, 5L), s.items.map { it.id })
        assertEquals(2, s.remaining); assertEquals(2, s.done); assertEquals(4, s.total); assertEquals(0, s.more)
        assertEquals(0.5f, s.progress, 0.001f)
        assertEquals("還有 2 項", WidgetLogic.statusLabel(s))
        assertEquals("11/16 週一", s.dateLabel)
    }
    @Test fun overdueFirstThenCreatedOrder() {
        val s = WidgetLogic.state(listOf(once(1, mon, created = 30), once(2, mon, created = 10), once(3, mon.minusDays(2), created = 50)), mon)
        assertEquals(listOf(3L, 2L, 1L), s.items.map { it.id })
    }
    @Test fun smallSizeTruncatesToThreeWithMoreCount() {
        val list = (1L..5L).map { once(it, mon) }
        val s = WidgetLogic.state(list, mon, limit = 3)
        assertEquals(listOf(1L, 2L, 3L), s.items.map { it.id })
        assertEquals(5, s.remaining); assertEquals(2, s.more)
        val two = WidgetLogic.state(list.take(2), mon, limit = 3)
        assertEquals(2, two.items.size); assertEquals(0, two.more)
    }
    @Test fun checkedItemDisappearsAndCountsUpdate() {
        val before = WidgetLogic.state(listOf(once(1, mon), once(2, mon)), mon)
        val after = WidgetLogic.state(listOf(once(1, mon, done = mon.toString()), once(2, mon)), mon)
        assertEquals(2, before.remaining)
        assertEquals(listOf(2L), after.items.map { it.id }); assertEquals(1, after.remaining); assertEquals(1, after.done)
    }
    @Test fun emptyAndAllDone() {
        val empty = WidgetLogic.state(emptyList(), mon)
        assertTrue(empty.items.isEmpty()); assertEquals(0, empty.total); assertFalse(empty.allDone)
        assertEquals("今天沒有要做的", WidgetLogic.statusLabel(empty)); assertEquals(0f, empty.progress, 0f)
        val all = WidgetLogic.state(listOf(once(1, mon, done = mon.toString())), mon)
        assertTrue(all.items.isEmpty()); assertTrue(all.allDone); assertEquals("全部完成", WidgetLogic.statusLabel(all))
    }
    @Test fun dayRolloverShowsNewDay() {
        val list = listOf(once(1, mon), once(2, mon.plusDays(1)))
        assertEquals(listOf(1L), WidgetLogic.state(list, mon).items.map { it.id })
        // 隔天：昨天沒做的變過期仍顯示（排前面），今天的也顯示
        assertEquals(listOf(1L, 2L), WidgetLogic.state(list, mon.plusDays(1)).items.map { it.id })
    }
    @Test fun deferredToTomorrowLeavesToday() {
        val e = once(1, mon)
        val deferred = tw.memo.errands.logic.Logic.defer(e, mon)[0]
        assertTrue(WidgetLogic.state(listOf(deferred), mon).items.isEmpty())
        assertEquals(1, WidgetLogic.state(listOf(deferred), mon.plusDays(1)).remaining)
    }
}
