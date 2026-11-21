package tw.memo.errands

import org.junit.Assert.*
import org.junit.Test
import tw.memo.errands.widget.WidgetStyle
import tw.memo.errands.widget.WidgetStyle.ClockTarget

class WidgetStyleTest {
    private fun alphaByte(argb: Int) = (argb ushr 24) and 0xFF

    @Test fun defaultsAreDarkMid() {
        assertEquals("dark", WidgetStyle.tintOf(null))
        assertEquals("mid", WidgetStyle.alphaOf(null))
        assertEquals("dark", WidgetStyle.tintOf("weird"))
        assertEquals("system", WidgetStyle.tintOf("system"))
        assertEquals("low", WidgetStyle.alphaOf("low"))
    }

    @Test fun opacityMatchesDemoAndOrdersHighMidLow() {
        assertEquals(.20f, WidgetStyle.bgOpacity(false, "high"), 1e-6f)
        assertEquals(.42f, WidgetStyle.bgOpacity(false, "mid"), 1e-6f)
        assertEquals(.68f, WidgetStyle.bgOpacity(false, "low"), 1e-6f)
        assertEquals(.48f, WidgetStyle.bgOpacity(true, "mid"), 1e-6f)
        for (dark in listOf(true, false)) {
            val h = alphaByte(WidgetStyle.palette(dark, "high").bg)
            val m = alphaByte(WidgetStyle.palette(dark, "mid").bg)
            val l = alphaByte(WidgetStyle.palette(dark, "low").bg)
            assertTrue("$h < $m < $l", h < m && m < l && l < 255 && h > 0)
        }
    }

    @Test fun backgroundIsTranslucentButTextIsOpaque() {
        val p = WidgetStyle.palette(true, "mid")
        assertEquals(122, alphaByte(p.bg))
        assertEquals(0x100C16, p.bg and 0xFFFFFF)
        assertEquals(255, alphaByte(p.ink))
        assertEquals(255, alphaByte(WidgetStyle.palette(false, "high").ink))
    }

    @Test fun metricsFor4x2And4x4() {
        val m = WidgetStyle.metrics(360f, 180f)
        assertFalse(m.large)
        assertEquals(129.6f, m.clockCol, .01f)
        assertTrue(m.clockSp in 50f..90f)
        // 時＋分兩行＋日期要塞得進高度（扣掉上下 padding）
        assertTrue(m.clockBlock + WidgetStyle.PAD_V_MEDIUM <= 180f + .01f)
        val l = WidgetStyle.metrics(360f, 400f)
        assertTrue(l.large)
        assertTrue(l.clockSp > m.clockSp)
        assertTrue(l.clockSp <= (l.clockCol - 20f) * .76f + .01f) // 不超出左欄寬
        assertTrue(l.dateSp in 11f..15f)
        assertTrue(l.clockBlock + WidgetStyle.PAD_V_LARGE + WidgetStyle.PROGRESS_BLOCK <= 400f + .01f)
    }

    @Test fun smallWidgetsStillGetReadableClock() {
        val m = WidgetStyle.metrics(250f, 110f)
        assertTrue(m.clockSp >= 28f)
        assertTrue(m.clockBlock + WidgetStyle.PAD_V_MEDIUM <= 110f + .01f)
    }

    @Test fun clockAppChoice() {
        assertEquals(ClockTarget.ShowAlarms, WidgetStyle.chooseClock(true, setOf("com.android.deskclock")))
        assertEquals(ClockTarget.Package("com.sec.android.app.clockpackage"),
            WidgetStyle.chooseClock(false, setOf("com.android.deskclock", "com.sec.android.app.clockpackage")))
        assertEquals(ClockTarget.Package("com.google.android.deskclock"),
            WidgetStyle.chooseClock(false, setOf("com.google.android.deskclock", "com.android.deskclock")))
        assertEquals(ClockTarget.None, WidgetStyle.chooseClock(false, emptySet()))
    }
}

class WidgetFitTest {
    @Test fun clockBlockAlwaysFitsCommonLauncherSizes() {
        for (t in listOf(0f, WidgetStyle.TIGHTEN)) for (w in listOf(250f, 300f, 330f, 360f, 380f, 411f)) for (h in listOf(110f, 140f, 160f, 180f, 200f, 220f, 280f, 340f, 400f, 460f)) {
            val m = WidgetStyle.metrics(w, h, t)
            val room = h - (if (m.large) WidgetStyle.PAD_V_LARGE + WidgetStyle.PROGRESS_BLOCK else WidgetStyle.PAD_V_MEDIUM)
            if (m.clockSp > 20f) assertTrue("${w}x$h block=${m.clockBlock} room=$room", m.clockBlock <= room + .01f)
            assertTrue("${w}x$h 時鐘寬度", m.clockSp * 1.1f <= m.clockCol - 19f)
        }
    }
}

class WidgetTightenTest {
    @Test fun tighteningOnAndroid12AllowsSameOrBiggerClock() {
        val a = WidgetStyle.metrics(360f, 170f, 0f); val b = WidgetStyle.metrics(360f, 170f, WidgetStyle.TIGHTEN)
        assertTrue(b.clockSp >= a.clockSp)
        assertTrue(b.clockBlock + WidgetStyle.PAD_V_MEDIUM <= 170.01f)
    }
}
