package tw.memo.errands

import org.junit.Assert.*
import org.junit.Test
import tw.memo.errands.widget.ClockApps
import tw.memo.errands.widget.ClockApps.Choice

class ClockAppsTest {
    @Test fun knownLaunchableClockWinsOppoFirst() {
        assertEquals(Choice("com.coloros.alarmclock"),
            ClockApps.pickAuto(listOf("com.android.deskclock", "com.coloros.alarmclock"), listOf("com.android.deskclock", "com.coloros.alarmclock")))
        assertEquals(Choice("com.oplus.alarmclock"), ClockApps.pickAuto(emptyList(), listOf("com.android.deskclock", "com.oplus.alarmclock")))
    }

    @Test fun unknownAlarmHandlerUsedOnlyIfLaunchable() {
        assertEquals(Choice("com.weird.clock"), ClockApps.pickAuto(listOf("com.weird.clock"), listOf("com.weird.clock")))
        assertEquals(ClockApps.SYSTEM, ClockApps.pickAuto(listOf("com.weird.clock"), emptyList()))
        assertEquals(ClockApps.SYSTEM, ClockApps.pickAuto(emptyList(), emptyList()))
    }

    @Test fun oppoPackagesAreFirst() {
        assertEquals(listOf("com.coloros.alarmclock", "com.oplus.alarmclock", "com.oppo.alarmclock"), ClockApps.KNOWN.take(3))
    }

    @Test fun encodeDecodeAndMigrateLegacyComponent() {
        for (c in listOf(Choice("a.b"), ClockApps.SYSTEM)) assertEquals(c, ClockApps.decode(ClockApps.encode(c)))
        // v1.0.7 存的是 HandleApiActivity → 只留套件
        val legacy = "a|com.coloros.alarmclock|com.oplus.alarmclock.cts.HandleApiActivity"
        assertEquals(Choice("com.coloros.alarmclock"), ClockApps.decode(legacy))
        assertEquals("p|com.coloros.alarmclock", ClockApps.migrate(legacy))
        assertEquals("p|x.y", ClockApps.migrate("l|x.y|x.y.Main"))
        assertEquals("p|x.y", ClockApps.migrate("p|x.y"))
        assertEquals("s||", ClockApps.migrate("s||"))
        assertEquals("", ClockApps.migrate(""))
        assertEquals("", ClockApps.migrate("z|a|b"))
        assertNull(ClockApps.decode(null))
    }

    @Test fun clockLabelsSortAndFilter() {
        assertTrue(ClockApps.looksLikeClock("時鐘", "com.x"))
        assertTrue(ClockApps.looksLikeClock("Alarm", "com.x"))
        assertTrue(ClockApps.looksLikeClock("工具", "com.oplus.clock"))
        assertFalse(ClockApps.looksLikeClock("LINE", "jp.naver.line"))
        val opts = listOf(ClockApps.Option(Choice("b"), "Zebra", 1), ClockApps.Option(Choice("c"), "時鐘", 0), ClockApps.Option(Choice("a"), "apple", 1))
        assertEquals(listOf("c", "a", "b"), ClockApps.sortOptions(opts).map { it.choice.pkg })
        assertEquals(listOf("c"), ClockApps.filter(opts, "時鐘").map { it.choice.pkg })
    }
}
