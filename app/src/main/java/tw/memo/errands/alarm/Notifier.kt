package tw.memo.errands.alarm

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import tw.memo.errands.R
import tw.memo.errands.logic.Sale
import android.os.Bundle
import java.time.LocalDate
import tw.memo.errands.ui.MainActivity

object Notifier {
    const val CH_REMIND = "remind"
    const val CH_ALARM = "alarm_ring"
    const val EXTRA_TARGET = "target"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"
    const val EXTRA_NID = "nid"
    const val TARGET_TODAY = "today"
    const val TARGET_NIGHT = "night"
    const val TARGET_PURCHASE = "purchase"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val remind = NotificationChannel(CH_REMIND, "提醒通知", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "下班提醒、晚上確認、採購提醒（通知模式）"
        }
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val alarm = NotificationChannel(CH_ALARM, "鬧鐘", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "鬧鐘模式：持續響到按關閉或稍後"
            setSound(alarmUri, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 600, 800, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(remind)
        nm.createNotificationChannel(alarm)
    }

    fun canPost(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun canFullScreen(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun notificationId(kind: String, pid: Long): Int = when (kind) {
        Scheduler.KIND_EVENING -> 1
        Scheduler.KIND_NIGHT -> 2
        Scheduler.KIND_PURCHASE -> 1000 + pid.toInt()
        else -> 9
    }

    private fun openApp(ctx: Context, target: String, nid: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).putExtra(EXTRA_TARGET, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(ctx, nid, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(ctx: Context, act: String, kind: String, pid: Long, nid: Int, ev: Bundle?): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(act)
            .putExtra(Scheduler.EXTRA_KIND, kind).putExtra(Scheduler.EXTRA_PID, pid).putExtra(EXTRA_NID, nid)
        if (ev != null) i.putExtras(ev)
        val rc = nid * 10 + when (act) { Scheduler.ACTION_DISMISS -> 1; Scheduler.ACTION_SNOOZE -> 2; else -> 3 }
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(ctx: Context, kind: String, pid: Long, title: String, text: String, target: String, alarm: Boolean, ev: Bundle? = null) {
        if (!canPost(ctx)) return
        ensureChannels(ctx)
        val nid = notificationId(kind, pid)
        val b = NotificationCompat.Builder(ctx, if (alarm) CH_ALARM else CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(ctx, target, nid))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (alarm) {
            val full = Intent(ctx, AlarmActivity::class.java)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_NID, nid)
                .putExtra(EXTRA_TARGET, target).putExtra(Scheduler.EXTRA_KIND, kind).putExtra(Scheduler.EXTRA_PID, pid)
                .apply { if (ev != null) putExtras(ev) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            val fullPi = PendingIntent.getActivity(ctx, nid + 500, full, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            b.setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullPi, true)
                .setOngoing(true)
                .setAutoCancel(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, "關閉", action(ctx, Scheduler.ACTION_DISMISS, kind, pid, nid, ev))
                .addAction(0, "稍後 ${Scheduler.SNOOZE_MINUTES} 分鐘", action(ctx, Scheduler.ACTION_SNOOZE, kind, pid, nid, ev))
        } else {
            b.setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true)
        }
        if (kind == Scheduler.KIND_PURCHASE) {
            val day = Scheduler.evDay(ev) ?: LocalDate.now()
            b.addAction(0, Sale.addLabel(day, LocalDate.now()), action(ctx, Scheduler.ACTION_ADD_TODAY, kind, pid, nid, ev))
        }
        val n = b.build()
        if (alarm) n.flags = n.flags or Notification.FLAG_INSISTENT
        try { NotificationManagerCompat.from(ctx).notify(nid, n) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context, nid: Int) = NotificationManagerCompat.from(ctx).cancel(nid)
}
