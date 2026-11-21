package tw.memo.errands

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tw.memo.errands.data.Prefs
import tw.memo.errands.widget.ClockApps
import tw.memo.errands.widget.ClockLaunchActivity
import tw.memo.errands.widget.MemoWidget

/** 模擬 OPPO：SHOW_ALARMS 只指到 HandleApiActivity，桌面圖示是另一個 activity。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClockResolveTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val oppoApi = ComponentName("com.coloros.alarmclock", "com.oplus.alarmclock.cts.HandleApiActivity")
    private val oppoMain = ComponentName("com.coloros.alarmclock", "com.oplus.alarmclock.AlarmClock")

    private fun installOppo() {
        val spm = shadowOf(ctx.packageManager)
        spm.addActivityIfNotPresent(oppoApi)
        for (a in ClockApps.ACTIONS) spm.addIntentFilterForActivity(oppoApi, IntentFilter(a).apply { addCategory(Intent.CATEGORY_DEFAULT) })
        spm.addActivityIfNotPresent(oppoMain)
        spm.addIntentFilterForActivity(oppoMain, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }

    @Test fun oppoUsesLauncherActivityNeverHandleApi() {
        installOppo()
        val legacy = "a|${oppoApi.packageName}|${oppoApi.className}"
        for (pref in listOf("", legacy, "s||")) {
            val first = ClockApps.plan(ctx, pref).first()
            assertEquals("啟動器 com.coloros.alarmclock", first.name)
            assertEquals(oppoMain, first.intent.component)
            assertTrue(first.intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
        assertTrue(ClockApps.plan(ctx, "").none { it.intent.component == oppoApi })
        assertEquals("com.coloros.alarmclock", ClockApps.options(ctx).first().choice.pkg)
    }

    @Test fun launchStartsLauncherMigratesPrefAndLogs() {
        installOppo()
        val prefs = Prefs(ctx)
        prefs.clockApp = "a|${oppoApi.packageName}|${oppoApi.className}"
        assertEquals("啟動器 com.coloros.alarmclock", ClockApps.launch(ctx))
        assertEquals("p|com.coloros.alarmclock", prefs.clockApp)
        assertEquals(oppoMain, shadowOf(ctx as Application).nextStartedActivity.component)
        assertTrue(prefs.clockLastLaunch.contains("1.啟動器 com.coloros.alarmclock→成功"))
        assertTrue(ClockApps.diagnostics(ctx, prefs.clockApp).contains("上次="))
    }

    @Test fun failuresAreRecordedAndFallBack() {
        // 沒有任何時鐘、系統也找不到 SHOW_ALARMS → 每步失敗都記下來
        shadowOf(ctx as Application).checkActivities(true)
        assertNull(ClockApps.launch(ctx, ""))
        val log = Prefs(ctx).clockLastLaunch
        assertTrue(log, log.contains("1.系統SHOW_ALARMS→失敗(ActivityNotFoundException"))
        assertTrue(log, log.contains("2.日期時間設定→失敗"))
    }

    @Test fun widgetAndTestButtonUseTrampoline() {
        installOppo()
        val i = MemoWidget.clockIntent(ctx)
        assertEquals(ComponentName(ctx, ClockLaunchActivity::class.java), i.component)
        val act = Robolectric.buildActivity(ClockLaunchActivity::class.java, i).create().get()
        assertTrue(act.isFinishing)
        assertEquals(oppoMain, shadowOf(act).nextStartedActivity.component)
    }

    @Test fun systemLauncherClockListedFirst() {
        val spm = shadowOf(ctx.packageManager)
        for (c in listOf(ComponentName("com.example.app", "com.example.app.Main"), ComponentName("com.oplus.newclock", "com.oplus.newclock.Main"))) {
            spm.addActivityIfNotPresent(c)
            spm.addIntentFilterForActivity(c, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
        }
        val opts = ClockApps.options(ctx)
        assertTrue(opts.any { it.choice.pkg == "com.example.app" })
        assertEquals("com.oplus.newclock", opts.first().choice.pkg)
        assertTrue(ClockApps.diagnostics(ctx, "").contains("SHOW_ALARMS=無"))
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS, ClockApps.plan(ctx, "").first().intent.action)
    }
}
