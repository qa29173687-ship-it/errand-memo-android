package tw.memo.errands.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.memo.errands.data.AppDb
import tw.memo.errands.data.Errand
import tw.memo.errands.data.Mode
import tw.memo.errands.data.Prefs
import tw.memo.errands.data.Repo
import tw.memo.errands.data.PKind
import tw.memo.errands.data.Timing
import tw.memo.errands.logic.Sale
import android.os.Bundle
import java.time.LocalDateTime
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import tw.memo.errands.logic.Logic
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ctx = context.applicationContext
        val kind = intent.getStringExtra(Scheduler.EXTRA_KIND) ?: return
        val pid = intent.getLongExtra(Scheduler.EXTRA_PID, 0)
        when (intent.action) {
            Scheduler.ACTION_DISMISS -> { Notifier.cancel(ctx, Notifier.notificationId(kind, pid)); return }
            Scheduler.ACTION_SNOOZE -> {
                Notifier.cancel(ctx, Notifier.notificationId(kind, pid))
                Scheduler.snooze(ctx, kind, pid, Scheduler.evFrom(intent)); return
            }
            Scheduler.ACTION_ADD_TODAY -> {
                Notifier.cancel(ctx, Notifier.notificationId(kind, pid))
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val today = LocalDate.now()
                        val day = Scheduler.evDay(Scheduler.evFrom(intent))?.takeIf { !it.isBefore(today) } ?: today
                        val added = Repo.addPurchaseToDay(ctx, pid, day)
                        val which = if (day == today) "今天" else if (day == today.plusDays(1)) "明天" else Logic.shortDate(day)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(ctx, if (added) "已加入${which}下班要做" else "下班要做裡已經有了", Toast.LENGTH_SHORT).show()
                        }
                    } finally { pending.finish() }
                }
                return
            }
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { fire(ctx, kind, pid, intent.getBooleanExtra(Scheduler.EXTRA_FORCE_ALARM, false), Scheduler.evFrom(intent)) }
            finally { pending.finish() }
        }
    }

    companion object {
        private fun lines(list: List<Errand>): String =
            list.joinToString("\n") { "【${Logic.typeLabel(it.type)}】${it.title}" + if (it.note.isNotBlank()) "（${it.note}）" else "" }

        suspend fun fire(ctx: Context, kind: String, pid: Long, forceAlarm: Boolean, ev: Bundle? = null) {
            val prefs = Prefs(ctx)
            val dao = AppDb.get(ctx).dao()
            val today = LocalDate.now()
            when (kind) {
                Scheduler.KIND_EVENING -> {
                    if (!forceAlarm) Scheduler.scheduleSlot(ctx, kind) // 排下一次
                    val items = Logic.pendingToday(dao.errands(), today)
                    if (items.isEmpty()) return
                    Notifier.show(ctx, kind, 0, "下班順便做（${items.size} 項）", lines(items),
                        Notifier.TARGET_TODAY, forceAlarm || prefs.eveningMode == Mode.ALARM)
                }
                Scheduler.KIND_NIGHT -> {
                    if (!forceAlarm) Scheduler.scheduleSlot(ctx, kind)
                    val all = dao.errands()
                    val sales = Sale.eventsOn(dao.purchases(), today.plusDays(1))
                    val (title, text) = Logic.nightMessage(Logic.pendingToday(all, today), Logic.dueTomorrow(all, today), sales) ?: return
                    // 明天特價的項目若設成「前一晚」且選鬧鐘，晚上確認也用鬧鐘響。
                    Notifier.show(ctx, kind, 0, title, text, Notifier.TARGET_NIGHT,
                        forceAlarm || prefs.nightMode == Mode.ALARM ||
                            sales.any { (p, _) -> p.mode == Mode.ALARM && p.timing == Timing.NIGHT })
                }
                Scheduler.KIND_PURCHASE -> {
                    if (!forceAlarm) Scheduler.schedulePurchases(ctx) // 排這個項目的下一個提醒（例如結束前一晚）
                    val p = dao.purchase(pid) ?: return
                    if (p.boughtOn != null || p.kind != PKind.PERIOD) return
                    val r = ev?.let { b ->
                        val day = Scheduler.evDay(b) ?: return@let null
                        val e = runCatching { Sale.Event.valueOf(b.getString(Scheduler.EXTRA_EV)!!) }.getOrNull() ?: return@let null
                        Sale.Reminder(LocalDateTime.now(), day, e, b.getBoolean(Scheduler.EXTRA_EV_NIGHT))
                    } ?: return
                    // 項目日期被改過、這個事件已不存在或已過 → 不跳
                    if (r.eventDate.isBefore(today) || Sale.events(p).none { it.first == r.eventDate && it.second == r.event }) return
                    val text = Sale.rangeLabel(p) + " · " + Sale.statusLabel(p, today) + if (p.note.isNotBlank()) "\n${p.note}" else ""
                    Notifier.show(ctx, kind, pid, Sale.alertTitle(p, r), text, Notifier.TARGET_PURCHASE,
                        forceAlarm || p.mode == Mode.ALARM, Scheduler.evBundle(r))
                }
                "testNotify", "testAlarm" -> {
                    Notifier.show(ctx, kind, 0, if (forceAlarm) "測試鬧鐘" else "測試通知",
                        "看到這則代表提醒可以正常跳出。", Notifier.TARGET_TODAY, forceAlarm)
                }
            }
        }
    }
}
