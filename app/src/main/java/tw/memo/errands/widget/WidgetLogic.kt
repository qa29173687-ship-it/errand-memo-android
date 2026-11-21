package tw.memo.errands.widget

import tw.memo.errands.data.Errand
import tw.memo.errands.logic.Logic
import java.time.LocalDate

/** 桌面小工具要顯示的資料（純函式，有單元測試）。只用下班順便做，不含採購清單。 */
object WidgetLogic {
    data class State(
        val items: List<Errand>,   // 要顯示的未完成項目（已依 limit 截斷）
        val remaining: Int,        // 今天還沒做的總數
        val done: Int,             // 今天已完成數
        val total: Int,            // 今天總數（含已完成）
        val more: Int,             // 截斷後沒顯示的數量（小尺寸「還有 N 項…」）
        val dateLabel: String,
    ) {
        val allDone get() = total > 0 && remaining == 0
        val progress get() = if (total == 0) 0f else done.toFloat() / total
    }

    /** limit = null 代表全部顯示（中、大尺寸可捲動）；小尺寸傳 3。過期未做的排最前面。 */
    fun state(errands: List<Errand>, today: LocalDate, limit: Int? = null): State {
        val visible = Logic.visibleOn(errands, today, today)
        val pending = visible.filter { !Logic.isDone(it, today) }
            .sortedWith(compareByDescending<Errand> { Logic.isOverdue(it, today) }.thenBy { it.createdAt }.thenBy { it.id })
        val done = visible.size - pending.size
        val shown = if (limit != null) pending.take(limit) else pending
        return State(shown, pending.size, done, visible.size, pending.size - shown.size, Logic.shortDate(today))
    }

    fun statusLabel(s: State): String = when {
        s.total == 0 -> "今天沒有要做的"
        s.remaining == 0 -> "全部完成"
        else -> "還有 ${s.remaining} 項"
    }
}
