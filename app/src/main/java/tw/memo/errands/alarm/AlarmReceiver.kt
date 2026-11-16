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
                Scheduler.snooze(ctx, kind, pid); return
            }
            Scheduler.ACTION_ADD_TODAY -> {
                Notifier.cancel(ctx, Notifier.notificationId(kind, pid))
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val added = Repo.addPurchaseToToday(ctx, pid)
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(ctx, if (added) "已加入今天下班要做" else "今天下班要做裡已經有了", Toast.LENGTH_SHORT).show()
                        }
                    } finally { pending.finish() }
                }
                return
            }
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { fire(ctx, kind, pid, intent.getBooleanExtra(Scheduler.EXTRA_FORCE_ALARM, false)) }
            finally { pending.finish() }
        }
    }

    companion object {
        private fun lines(list: List<Errand>): String =
            list.joinToString("\n") { "【${Logic.typeLabel(it.type)}】${it.title}" + if (it.note.isNotBlank()) "（${it.note}）" else "" }

        suspend fun fire(ctx: Context, kind: String, pid: Long, forceAlarm: Boolean) {
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
                    val buys = Logic.purchasesTomorrow(dao.purchases(), today)
                    val (title, text) = Logic.nightMessage(Logic.pendingToday(all, today), Logic.dueTomorrow(all, today), buys) ?: return
                    // 明天要買的項目若設成鬧鐘，晚上確認也用鬧鐘響。
                    Notifier.show(ctx, kind, 0, title, text, Notifier.TARGET_NIGHT,
                        forceAlarm || prefs.nightMode == Mode.ALARM || buys.any { it.mode == Mode.ALARM })
                }
                Scheduler.KIND_PURCHASE -> {
                    val p = dao.purchase(pid) ?: return
                    if (p.boughtOn != null) return
                    val text = Logic.purchaseDateLabel(p, today) + if (p.note.isNotBlank()) " · ${p.note}" else ""
                    Notifier.show(ctx, kind, pid, Logic.purchaseAlertTitle(p, today), text, Notifier.TARGET_PURCHASE,
                        forceAlarm || p.mode == Mode.ALARM)
                }
                "testNotify", "testAlarm" -> {
                    Notifier.show(ctx, kind, 0, if (forceAlarm) "測試鬧鐘" else "測試通知",
                        "看到這則代表提醒可以正常跳出。", Notifier.TARGET_TODAY, forceAlarm)
                }
            }
        }
    }
}
