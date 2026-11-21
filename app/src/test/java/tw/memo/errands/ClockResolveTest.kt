package tw.memo.errands

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tw.memo.errands.widget.ClockApps

/** 在模擬的 PackageManager 上確認：OPPO 時鐘有 SHOW_ALARMS 時，產生指向它的明確 intent。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockResolveTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun oppoShowAlarmsBecomesExplicitIntent() {
        val spm = shadowOf(ctx.packageManager)
        val oppo = ComponentName("com.coloros.alarmclock", "com.coloros.alarmclock.AlarmClock")
        val other = ComponentName("com.android.deskclock", "com.android.deskclock.DeskClock")
        for (c in listOf(other, oppo)) {
            spm.addActivityIfNotPresent(c)
            spm.addIntentFilterForActivity(c, IntentFilter(AlarmClock.ACTION_SHOW_ALARMS).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        }
        val i = ClockApps.intent(ctx, "")!!
        assertEquals(oppo, i.component)
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, i.action)
        assertTrue(i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        // 使用者自選的優先
        val pick = ClockApps.encode(ClockApps.Choice(other.packageName, other.className, true))
        assertEquals(other, ClockApps.intent(ctx, pick)!!.component)
        // 自選的被移除 → 回到自動
        assertEquals(oppo, ClockApps.intent(ctx, ClockApps.encode(ClockApps.Choice("gone.app", "gone.app.X", true)))!!.component)
        // 設定裡的選項：時鐘排前面、OPPO 第一
        assertEquals("com.coloros.alarmclock", ClockApps.options(ctx).first().choice.pkg)
    }

    @Test fun noClockAppFallsBackToSystemShowAlarms() {
        val i = ClockApps.intent(ctx, "")!!
        assertNull(i.component)
        assertEquals(android.provider.AlarmClock.ACTION_SHOW_ALARMS, i.action)
        assertEquals("系統鬧鐘（自動）", ClockApps.label(ctx, ClockApps.current(ctx, "")))
    }

    @Test fun systemLauncherClockWithoutShowAlarmsIsListedFirst() {
        val spm = shadowOf(ctx.packageManager)
        val clock = ComponentName("com.oplus.newclock", "com.oplus.newclock.Main")
        val other = ComponentName("com.example.app", "com.example.app.Main")
        for (c in listOf(other, clock)) {
            spm.addActivityIfNotPresent(c)
            spm.addIntentFilterForActivity(c, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        val opts = ClockApps.options(ctx)
        assertTrue(opts.any { it.choice.pkg == "com.example.app" })
        assertEquals("com.oplus.newclock", opts.first().choice.pkg) // 套件名含 clock → 排前面
        assertEquals(1, ClockApps.filter(opts, "newclock").size)
        assertTrue(ClockApps.diagnostics(ctx, "").contains("SHOW_ALARMS=無"))
    }
}
