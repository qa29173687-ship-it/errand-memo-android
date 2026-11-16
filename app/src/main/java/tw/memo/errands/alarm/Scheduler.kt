package tw.memo.errands.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import tw.memo.errands.data.AppDb
import tw.memo.errands.data.Mode
import tw.memo.errands.data.Prefs
import tw.memo.errands.logic.Logic
import tw.memo.errands.ui.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId

object Scheduler {
    const val ACTION_FIRE = "tw.memo.errands.FIRE"
    const val ACTION_DISMISS = "tw.memo.errands.DISMISS"
    const val ACTION_SNOOZE = "tw.memo.errands.SNOOZE"
    const val ACTION_ADD_TODAY = "tw.memo.errands.ADD_TODAY"
    const val EXTRA_KIND = "kind"
    const val EXTRA_PID = "pid"
    const val EXTRA_FORCE_ALARM = "forceAlarm"
    const val EXTRA_TEST = "test"
    const val KIND_EVENING = "evening"
    const val KIND_NIGHT = "night"
    const val KIND_PURCHASE = "purchase"
    const val SNOOZE_MINUTES = 10L

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    fun requestCode(kind: String, pid: Long, snooze: Boolean): Int {
        val base = when (kind) { KIND_EVENING -> 1; KIND_NIGHT -> 2; else -> 100_000 + pid.toInt() }
        return if (snooze) base + 50_000_000 else base
    }

    fun firePi(ctx: Context, kind: String, pid: Long = 0, snooze: Boolean = false, forceAlarm: Boolean = false, test: Boolean = false): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_PID, pid)
            .putExtra(EXTRA_FORCE_ALARM, forceAlarm).putExtra(EXTRA_TEST, test)
        val rc = if (test) 9_000_000 + kind.hashCode() % 1000 else requestCode(kind, pid, snooze)
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAt(ctx: Context, millis: Long, pi: PendingIntent, alarmMode: Boolean) {
        val am = am(ctx)
        try {
            if (!canExact(ctx)) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else if (alarmMode) {
                val show = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            Log.w("Scheduler", "exact alarm denied, fallback", e)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun toMillis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun scheduleSlot(ctx: Context, kind: String) {
        val p = Prefs(ctx)
        val (time, days, mode) = if (kind == KIND_EVENING) Triple(p.eveningTime, p.eveningDays, p.eveningMode)
        else Triple(p.nightTime, p.nightDays, p.nightMode)
        val pi = firePi(ctx, kind)
        am(ctx).cancel(pi)
        val next = Logic.nextTrigger(LocalDateTime.now(), Logic.parseTime(time), days) ?: return
        setAt(ctx, toMillis(next), pi, mode == Mode.ALARM)
    }

    suspend fun schedulePurchases(ctx: Context) {
        val prefs = Prefs(ctx)
        prefs.scheduledPurchaseIds.forEach { am(ctx).cancel(firePi(ctx, KIND_PURCHASE, it)) }
        val now = LocalDateTime.now()
        val dayTime = Logic.parseTime(prefs.purchaseTime)
        val nightTime = Logic.parseTime(prefs.nightTime)
        val nightDays = prefs.nightDays
        val ids = mutableSetOf<Long>()
        AppDb.get(ctx).dao().purchases().forEach { p ->
            val t = Logic.purchaseTrigger(p, now, dayTime, nightTime) ?: return@forEach
            // 前一天晚上的提醒已合併在晚上確認裡（明天打折要買），不重複跳。
            if (p.date == t.toLocalDate().plusDays(1).toString() && Logic.coveredByNight(t, nightTime, nightDays)) return@forEach
            setAt(ctx, toMillis(t), firePi(ctx, KIND_PURCHASE, p.id), p.mode == Mode.ALARM)
            ids += p.id
        }
        prefs.scheduledPurchaseIds = ids
    }

    suspend fun rescheduleAll(ctx: Context) {
        scheduleSlot(ctx, KIND_EVENING)
        scheduleSlot(ctx, KIND_NIGHT)
        schedulePurchases(ctx)
    }

    fun snooze(ctx: Context, kind: String, pid: Long) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000
        setAt(ctx, at, firePi(ctx, kind, pid, snooze = true, forceAlarm = true), true)
    }

    /** 設定頁的測試：幾秒後用真的排程路徑觸發。 */
    fun test(ctx: Context, alarm: Boolean, seconds: Int = 10) {
        val at = System.currentTimeMillis() + seconds * 1000L
        setAt(ctx, at, firePi(ctx, if (alarm) "testAlarm" else "testNotify", test = true, forceAlarm = alarm), alarm)
    }
}
