package tw.memo.errands

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tw.memo.errands.data.AppDb
import tw.memo.errands.data.Errand
import tw.memo.errands.widget.MemoWidget
import java.io.File
import java.time.LocalDate

/** 整條管線：Room → Glance → RemoteViews → 真的 inflate 成 View，確認不會當掉、時鐘和清單都在、可點的地方都有接。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rTW-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@org.robolectric.annotation.SQLiteMode(org.robolectric.annotation.SQLiteMode.Mode.LEGACY)
class WidgetRenderTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun all(v: View): List<View> = if (v is ViewGroup) listOf(v) + (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else listOf(v)

    @OptIn(ExperimentalGlanceApi::class)
    private fun render(w: Int, h: Int, name: String, widget: MemoWidget = MemoWidget()): View = runBlocking {
        val rv = widget.compose(ctx, size = DpSize(w.dp, h.dp))
        val act = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val parent = FrameLayout(act)
        act.setContentView(parent)
        val v = rv.apply(act, parent)
        parent.addView(v)
        val d = ctx.resources.displayMetrics.density
        val pw = (w * d).toInt(); val ph = (h * d).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, pw, ph)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        v.measure(View.MeasureSpec.makeMeasureSpec(pw, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(ph, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, pw, ph)
        System.getenv("WIDGET_SHOT_DIR")?.let { dir ->
            val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(Color.rgb(90, 110, 160)); v.draw(c)
            File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        if (System.getenv("WIDGET_DUMP") != null) dump(v, 0)
        v
    }

    private fun dump(v: View, depth: Int) {
        val t = (v as? TextView)?.text?.toString()?.let { " '$it'" } ?: ""
        println("  ".repeat(depth) + v.javaClass.simpleName + t + " [${v.left},${v.top} ${v.width}x${v.height}] vis=${v.visibility} click=${v.hasOnClickListeners()}")
        if (v is ViewGroup) for (i in 0 until v.childCount) dump(v.getChildAt(i), depth + 1)
    }

    @Test fun inflatesWithClockListAndClicks() {
        val dao = AppDb.get(ctx).dao()
        val today = LocalDate.now().toString()
        runBlocking {
            dao.insert(Errand(title = "買牛奶", type = "buy", note = "全聯", dueDate = today, createdAt = 1))
            dao.insert(Errand(title = "拿包裹", type = "take", note = "7-11", dueDate = today, createdAt = 2))
        }
        for ((w, h, n) in listOf(Triple(360, 180, "w-4x2"), Triple(360, 400, "w-4x4"), Triple(360, 260, "w-4x4-provider"))) {
            val v = render(w, h, n, if (n == "w-4x4-provider") tw.memo.errands.widget.MemoWidgetLarge() else MemoWidget())
            val views = all(v)
            val clocks = views.filterIsInstance<android.widget.TextClock>()
            assertEquals("$n: 時、分、日期三個 TextClock", 3, clocks.size)
            assertTrue("$n: 時鐘字夠大 ${clocks[0].textSize}", clocks[0].textSize > 40 * ctx.resources.displayMetrics.density)
            val texts = views.filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("$n texts=$texts", texts.contains("今天下班順便做"))
            assertTrue(texts.contains("＋"))
            // 時間區、日期都有自己的點擊
            val timeBox = clocks[0].parent as View
            assertTrue("$n: 點時間有接", timeBox.hasOnClickListeners())
            assertTrue("$n: 時、分本身也可點", clocks[0].hasOnClickListeners() && clocks[1].hasOnClickListeners())
            // 往上找：整個左欄的容器也要可點
            var p: View? = timeBox; var parentsClickable = 0
            while (p != null && p !== v) { if (p !== timeBox && p.hasOnClickListeners()) parentsClickable++; p = p.parent as? View }
            assertTrue("$n: 左欄容器可點 ($parentsClickable)", parentsClickable >= 2)
            assertTrue("$n: 點日期有接", clocks[2].hasOnClickListeners())
            // 日期不能被擠掉、每個文字都完整在小工具裡面（沒有被裁掉）
            val d = ctx.resources.displayMetrics.density
            assertTrue("$n: 日期高度 ${clocks[2].height}", clocks[2].height >= 11 * d)
            assertTrue("$n: 時鐘有字 '${clocks[0].text}'", clocks[0].text.isNotEmpty())
            val loc = IntArray(2); val root = IntArray(2); v.getLocationInWindow(root)
            for (t in views.filterIsInstance<TextView>().filter { it.text.isNotEmpty() && it.visibility == View.VISIBLE }) {
                t.getLocationInWindow(loc)
                val top = loc[1] - root[1]; val bottom = top + t.height
                assertTrue("$n: '${t.text}' 超出小工具 top=$top bottom=$bottom h=${v.height}", top >= 0 && bottom <= v.height)
                // 每一層父 view 都要完整容納它
                var y = t.top; var p = t.parent as View
                while (true) {
                    assertTrue("$n: '${t.text}' 被 ${p.javaClass.simpleName} 裁掉 (y=$y h=${t.height} parent=${p.height})", y >= -1 && y + t.height <= p.height + 1)
                    if (p === v) break
                    y += p.top; p = p.parent as View
                }
            }
            if (n != "w-4x2") assertTrue("今天完成要看得到", texts.contains("今天完成"))
            val clickable = views.count { it.hasOnClickListeners() }
            assertTrue("$n: 可點的 view 數 $clickable", clickable >= 4)
        }
    }
}
