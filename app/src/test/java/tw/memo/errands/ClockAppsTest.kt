package tw.memo.errands

import org.junit.Assert.*
import org.junit.Test
import tw.memo.errands.widget.ClockApps
import tw.memo.errands.widget.ClockApps.Choice

class ClockAppsTest {
    @Test fun showAlarmsHandlerWinsAndOppoRanksFirst() {
        val handlers = listOf("com.android.deskclock" to "A", "com.coloros.alarmclock" to "com.coloros.alarmclock.AlarmClock")
        assertEquals(Choice("com.coloros.alarmclock", "com.coloros.alarmclock.AlarmClock", true),
            ClockApps.pickAuto(handlers, listOf("com.google.android.deskclock")))
    }

    @Test fun unknownHandlerStillUsedBeforeLaunchFallback() {
        assertEquals(Choice("com.weird.clock", "X", true), ClockApps.pickAuto(listOf("com.weird.clock" to "X"), listOf("com.coloros.alarmclock")))
    }

    @Test fun fallbackToKnownLaunchableInPriorityOrder() {
        assertEquals(Choice("com.oplus.alarmclock", null, false),
            ClockApps.pickAuto(emptyList(), listOf("com.android.deskclock", "com.oplus.alarmclock")))
        assertEquals(Choice("com.sec.android.app.clockpackage", null, false),
            ClockApps.pickAuto(emptyList(), listOf("com.android.deskclock", "com.sec.android.app.clockpackage")))
        assertNull(ClockApps.pickAuto(emptyList(), emptyList()))
    }

    @Test fun oppoPackagesAreFirst() {
        assertEquals(listOf("com.coloros.alarmclock", "com.oplus.alarmclock", "com.oppo.alarmclock"), ClockApps.KNOWN.take(3))
    }

    @Test fun encodeDecodeRoundTrip() {
        for (c in listOf(Choice("a.b", "a.b.C", true), Choice("x.y", null, false), Choice("x.y", "x.y.Main", false)))
            assertEquals(c, ClockApps.decode(ClockApps.encode(c)))
        assertNull(ClockApps.decode(""))
        assertNull(ClockApps.decode(null))
        assertNull(ClockApps.decode("z|a|b"))
    }

    @Test fun systemChoiceAndClockLabels() {
        assertEquals(ClockApps.SYSTEM, ClockApps.decode(ClockApps.encode(ClockApps.SYSTEM)))
        assertEquals(ClockApps.SYSTEM, ClockApps.pickAutoOrSystem(emptyList(), emptyList()))
        assertTrue(ClockApps.looksLikeClock("時鐘", "com.x"))
        assertTrue(ClockApps.looksLikeClock("Alarm", "com.x"))
        assertTrue(ClockApps.looksLikeClock("工具", "com.oplus.clock"))
        assertFalse(ClockApps.looksLikeClock("LINE", "jp.naver.line"))
        val opts = listOf(ClockApps.Option(Choice("b", null, false), "Zebra", 1), ClockApps.Option(Choice("c", null, false), "時鐘", 0),
            ClockApps.Option(Choice("a", null, false), "apple", 1))
        assertEquals(listOf("c", "a", "b"), ClockApps.sortOptions(opts).map { it.choice.pkg })
        assertEquals(listOf("c"), ClockApps.filter(opts, "時鐘").map { it.choice.pkg })
    }
}
