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
import tw.memo.errands.logic.Sale
import tw.memo.errands.widget.WidgetRefresher
import android.os.Bundle
import java.time.LocalDate
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
    const val KIND_MIDNIGHT = "midnight"
    const val SNOOZE_MINUTES = 10L
    const val EXTRA_EV = "ev"
    const val EXTRA_EV_DATE = "evDate"
    const val EXTRA_EV_NIGHT = "evNight"

    /** 採購提醒事件（開始/結束/單日、事件日期、是否前一晚）放進 Intent extras。 */
    fun evBundle(r: Sale.Reminder): Bundle = Bundle().apply {
        putString(EXTRA_EV, r.event.name); putString(EXTRA_EV_DATE, r.eventDate.toString()); putBoolean(EXTRA_EV_NIGHT, r.night)
    }
    fun evFrom(i: Intent?): Bundle? = i?.getStringExtra(EXTRA_EV)?.let { ev ->
        Bundle().apply {
            putString(EXTRA_EV, ev); putString(EXTRA_EV_DATE, i.getStringExtra(EXTRA_EV_DATE))
            putBoolean(EXTRA_EV_NIGHT, i.getBooleanExtra(EXTRA_EV_NIGHT, false))
        }
    }
    fun evDay(b: Bundle?): LocalDate? = b?.getString(EXTRA_EV_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    fun requestCode(kind: String, pid: Long, snooze: Boolean): Int {
        val base = when (kind) { KIND_EVENING -> 1; KIND_NIGHT -> 2; KIND_MIDNIGHT -> 3; else -> 100_000 + pid.toInt() }
        return if (snooze) base + 50_000_000 else base
    }

    fun firePi(ctx: Context, kind: String, pid: Long = 0, snooze: Boolean = false, forceAlarm: Boolean = false, test: Boolean = false, ev: Bundle? = null): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_FIRE)
            .putExtra(EXTRA_KIND, kind).putExtra(EXTRA_PID, pid)
            .putExtra(EXTRA_FORCE_ALARM, forceAlarm).putExtra(EXTRA_TEST, test)
        if (ev != null) i.putExtras(ev)
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
            // 一個項目同時只排下一個提醒；觸發後再排下一個。前一晚的提醒若晚上確認會響，就合併在晚上確認（明天特價），不重複跳。
            val r = Sale.next(p, now, dayTime, nightTime) { it.night && Logic.coveredByNight(it.at, nightTime, nightDays) }
                ?: return@forEach
            setAt(ctx, toMillis(r.at), firePi(ctx, KIND_PURCHASE, p.id, ev = evBundle(r)), p.mode == Mode.ALARM)
            ids += p.id
        }
        prefs.scheduledPurchaseIds = ids
    }

    /** 每天 00:00 過後更新小工具（換成新的一天的清單）。不需精確，允許系統延後幾分鐘。 */
    fun scheduleMidnight(ctx: Context) {
        val next = LocalDate.now().plusDays(1).atTime(0, 0, 30)
        val pi = firePi(ctx, KIND_MIDNIGHT)
        am(ctx).cancel(pi)
        am(ctx).setWindow(AlarmManager.RTC_WAKEUP, toMillis(next), 10 * 60_000L, pi)
    }

    suspend fun rescheduleAll(ctx: Context) {
        scheduleMidnight(ctx)
        WidgetRefresher.refresh(ctx)
        scheduleSlot(ctx, KIND_EVENING)
        scheduleSlot(ctx, KIND_NIGHT)
        schedulePurchases(ctx)
    }

    fun snooze(ctx: Context, kind: String, pid: Long, ev: Bundle? = null) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000
        setAt(ctx, at, firePi(ctx, kind, pid, snooze = true, forceAlarm = true, ev = ev), true)
    }

    /** 設定頁的測試：幾秒後用真的排程路徑觸發。 */
    fun test(ctx: Context, alarm: Boolean, seconds: Int = 10) {
        val at = System.currentTimeMillis() + seconds * 1000L
        setAt(ctx, at, firePi(ctx, if (alarm) "testAlarm" else "testNotify", test = true, forceAlarm = alarm), alarm)
    }
}
