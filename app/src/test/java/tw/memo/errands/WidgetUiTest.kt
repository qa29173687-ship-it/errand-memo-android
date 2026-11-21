package tw.memo.errands

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.assertHasRunCallbackClickAction
import androidx.glance.action.actionParametersOf
import tw.memo.errands.widget.CheckCallback
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasClickAction
import androidx.glance.testing.unit.assertHasStartActivityClickAction
import androidx.glance.testing.unit.hasAnyDescendant
import androidx.glance.testing.unit.hasClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import tw.memo.errands.data.Errand
import tw.memo.errands.widget.MemoWidget
import tw.memo.errands.widget.QuickAddActivity
import java.time.LocalDate

/** 用 Glance 的測試工具實際組出小工具，確認畫面內容和點擊動作都有接上。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetUiTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.now().toString()
    private val items = listOf(
        Errand(id = 1, title = "買牛奶", type = "buy", note = "全聯", dueDate = today, createdAt = 1),
        Errand(id = 2, title = "拿包裹", type = "take", note = "7-11", dueDate = today, createdAt = 2),
    )

    private fun run(size: DpSize, list: List<Errand>, tint: String = "dark", block: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(size)
            setContext(ctx)
            provideComposable { MemoWidget().Content(list, tint, "mid") }
            block()
        }

    @Test fun medium4x2HasListHeaderPlusAndActions() = run(DpSize(360.dp, 180.dp), items) {
        onNode(hasText("今天下班順便做")).assertExists()
        onNode(hasClickAction() and hasAnyDescendant(hasText("今天下班順便做"))).assertHasStartActivityClickAction(MemoWidget.appIntent(ctx))
        onNode(hasText("買牛奶")).assertExists()
        onNode(hasText("拿包裹")).assertExists()
        onNode(hasClickAction() and hasAnyDescendant(hasText("＋"))).assertHasStartActivityClickAction<QuickAddActivity>()
        onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("完成：買牛奶")))
            .assertHasRunCallbackClickAction<CheckCallback>(actionParametersOf(MemoWidget.KEY_ID to 1L))
        onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("完成：拿包裹"))).assertHasClickAction()
        onNode(hasText("全聯")).assertDoesNotExist()
        onNode(hasText("今天完成")).assertDoesNotExist()
    }

    @Test fun large4x4ShowsNotesAndProgress() = run(DpSize(360.dp, 400.dp), items, tint = "light") {
        onNode(hasText("全聯")).assertExists()
        onNode(hasText("今天完成")).assertExists()
        onNode(hasText(" / 2")).assertExists()
    }

    @Test fun emptyState() = run(DpSize(360.dp, 180.dp), emptyList(), tint = "system") {
        onNode(hasText("今天沒有要順便做的 🎉")).assertExists()
    }
}
