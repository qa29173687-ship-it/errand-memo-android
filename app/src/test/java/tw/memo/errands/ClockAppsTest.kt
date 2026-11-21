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
}
