package tw.memo.errands.data

import android.content.Context
import tw.memo.errands.alarm.Scheduler
import tw.memo.errands.logic.Logic
import java.time.LocalDate

/** 需要同時改兩張表（連結）或重排提醒的操作。都在 IO 執行緒呼叫。 */
object Repo {
    suspend fun setErrandDone(ctx: Context, e: Errand, day: LocalDate, done: Boolean) {
        val dao = AppDb.get(ctx).dao()
        dao.update(if (done) Logic.complete(e, day) else Logic.uncomplete(e))
        val pid = e.purchaseId ?: return
        val p = dao.purchase(pid) ?: return
        dao.update(Logic.purchaseAfterErrandToggle(p, done, day))
        Scheduler.schedulePurchases(ctx)
    }

    suspend fun setBought(ctx: Context, p: Purchase, bought: Boolean, today: LocalDate = LocalDate.now()) {
        val dao = AppDb.get(ctx).dao()
        dao.update(p.copy(boughtOn = if (bought) today.toString() else null))
        Logic.linkedAfterPurchaseToggle(dao.errandsForPurchase(p.id), p.id, bought, today).forEach { dao.update(it) }
        Scheduler.schedulePurchases(ctx)
    }

    /** 加入今天下班要做。回傳 false 代表已經加過了。 */
    suspend fun addPurchaseToToday(ctx: Context, pid: Long, today: LocalDate = LocalDate.now()): Boolean {
        val dao = AppDb.get(ctx).dao()
        val p = dao.purchase(pid) ?: return false
        if (!Logic.shouldAddLinked(dao.errandsForPurchase(pid), pid)) return false
        dao.insert(Logic.errandFromPurchase(p, today, System.currentTimeMillis()))
        return true
    }
}
