package tw.memo.errands.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import tw.memo.errands.R
import tw.memo.errands.alarm.Notifier
import tw.memo.errands.data.AppDb
import tw.memo.errands.data.Errand
import tw.memo.errands.data.Repo
import tw.memo.errands.logic.Logic
import java.time.LocalDate

/** 桌面小工具「今天下班順便做」（Jetpack Glance）。小 2×2 / 中 4×2 / 大 4×4。 */
class MemoWidget : GlanceAppWidget() {
    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
        val KEY_ID = ActionParameters.Key<Long>("errandId")
        val KEY_TARGET = ActionParameters.Key<String>(Notifier.EXTRA_TARGET)
        const val SMALL_LIMIT = 3

        /** Android 12 以下沒有動態取色 → 暖橘色。 */
        val Warm = ColorProviders(
            light = lightColorScheme(
                primary = Color(0xFFE8743B), onPrimary = Color.White,
                primaryContainer = Color(0xFFFFE2D1), onPrimaryContainer = Color(0xFF6A2A09),
                secondaryContainer = Color(0xFFFFE2D1), onSecondaryContainer = Color(0xFF6A2A09),
                surface = Color(0xFFFFF9F4), onSurface = Color(0xFF2A1F1A),
                surfaceVariant = Color(0xFFF6ECE4), onSurfaceVariant = Color(0xFF7D6F66),
                background = Color(0xFFFFF9F4), onBackground = Color(0xFF2A1F1A), outline = Color(0xFFCDB9AA),
            ),
            dark = darkColorScheme(
                primary = Color(0xFFFF9A62), onPrimary = Color(0xFF4A1C04),
                primaryContainer = Color(0xFF5A3423), onPrimaryContainer = Color(0xFFFFE2D1),
                secondaryContainer = Color(0xFF5A3423), onSecondaryContainer = Color(0xFFFFE2D1),
                surface = Color(0xFF26212F), onSurface = Color(0xFFF5ECE6),
                surfaceVariant = Color(0xFF332C3D), onSurfaceVariant = Color(0xFFB5A9A1),
                background = Color(0xFF26212F), onBackground = Color(0xFFF5ECE6), outline = Color(0xFF6D6077),
            ),
        )
        val TakeFg = ColorProvider(day = Color(0xFF0F7F86), night = Color(0xFF6FD8D6))
        val TakeBg = ColorProvider(day = Color(0xFFD2F1EE), night = Color(0xFF1E3F42))
        val BuyFg = ColorProvider(day = Color(0xFFC25410), night = Color(0xFFFFB37A))
        val BuyBg = ColorProvider(day = Color(0xFFFFE3CC), night = Color(0xFF47321F))
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDb.get(context).dao()
        val initial = dao.errands()
        provideContent {
            val flow = remember { dao.errandsFlow() }
            val list by flow.collectAsState(initial)
            GlanceTheme(colors = if (Build.VERSION.SDK_INT >= 31) GlanceTheme.colors else Warm) {
                Content(list)
            }
        }
    }

    @Composable
    private fun Content(all: List<Errand>) {
        val size = LocalSize.current
        val small = size.width < 200.dp
        val large = !small && size.height >= 220.dp
        val today = LocalDate.now()
        val s = WidgetLogic.state(all, today, if (small) SMALL_LIMIT else null)
        val c = GlanceTheme.colors
        val bg = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.background(c.surface).cornerRadius(24.dp)
        else GlanceModifier.background(ImageProvider(R.drawable.widget_bg))
        Column(bg.then(GlanceModifier.fillMaxSize().appWidgetBackground().padding(if (small) 10.dp else 12.dp))) {
            Header(s, small)
            if (!small && s.total > 0) {
                Spacer(GlanceModifier.height(6.dp))
                LinearProgressIndicator(progress = s.progress, modifier = GlanceModifier.fillMaxWidth().height(5.dp),
                    color = c.primary, backgroundColor = c.surfaceVariant)
            }
            Spacer(GlanceModifier.height(if (small) 6.dp else 8.dp))
            if (s.items.isEmpty()) Empty(small)
            else if (small) {
                Column(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    s.items.forEachIndexed { i, e ->
                        if (i > 0) Spacer(GlanceModifier.height(4.dp))
                        ItemRow(e, today, compact = true)
                    }
                }
                if (s.more > 0) Text("還有 ${s.more} 項…", modifier = GlanceModifier.fillMaxWidth(),
                    style = TextStyle(fontSize = 11.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center))
            } else {
                LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(s.items, itemId = { it.id }) { e ->
                        Column(GlanceModifier.fillMaxWidth()) {
                            ItemRow(e, today, compact = false)
                            Spacer(GlanceModifier.height(5.dp))
                        }
                    }
                }
            }
            if (large) {
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("今天完成 ${s.done} / ${s.total}", modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(fontSize = 12.sp, color = c.onSurfaceVariant, fontWeight = FontWeight.Medium))
                    Box(GlanceModifier.background(c.primaryContainer).cornerRadius(14.dp).padding(horizontal = 14.dp, vertical = 7.dp)
                        .clickable(actionStartActivity<QuickAddActivity>())) {
                        Text("＋ 新增順便做", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer))
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(s: WidgetLogic.State, small: Boolean) {
        val c = GlanceTheme.colors
        val open = actionStartActivity<tw.memo.errands.ui.MainActivity>(actionParametersOf(KEY_TARGET to Notifier.TARGET_TODAY))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open)) {
                Text(if (small) "下班順便做" else "今天下班順便做", maxLines = 1,
                    style = TextStyle(fontSize = if (small) 13.sp else 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface))
                Text(if (small) WidgetLogic.statusLabel(s) else "${s.dateLabel} · ${WidgetLogic.statusLabel(s)}", maxLines = 1,
                    style = TextStyle(fontSize = 11.sp, color = c.primary, fontWeight = FontWeight.Medium))
            }
            if (!small && s.total > 0) {
                Box(GlanceModifier.background(c.primaryContainer).cornerRadius(12.dp).padding(horizontal = 8.dp, vertical = 4.dp).clickable(open)) {
                    Text("${s.done}/${s.total}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer))
                }
                Spacer(GlanceModifier.width(8.dp))
            }
            Box(GlanceModifier.size(if (small) 32.dp else 38.dp).background(c.primary).cornerRadius(if (small) 11.dp else 13.dp)
                .clickable(actionStartActivity<QuickAddActivity>()), contentAlignment = Alignment.Center) {
                Text("＋", style = TextStyle(fontSize = if (small) 18.sp else 21.sp, color = c.onPrimary, fontWeight = FontWeight.Bold))
            }
        }
    }

    @Composable
    private fun ItemRow(e: Errand, today: LocalDate, compact: Boolean) {
        val c = GlanceTheme.colors
        val buy = e.type == "buy"
        val overdue = Logic.isOverdue(e, today)
        Row(GlanceModifier.fillMaxWidth().background(c.surfaceVariant).cornerRadius(14.dp)
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 3.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(if (compact) 28.dp else 34.dp).clickable(actionRunCallback<CheckCallback>(actionParametersOf(KEY_ID to e.id))),
                contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_circle), contentDescription = "完成：${e.title}",
                    modifier = GlanceModifier.size(if (compact) 20.dp else 24.dp), colorFilter = ColorFilter.tint(c.outline))
            }
            Spacer(GlanceModifier.width(4.dp))
            Box(GlanceModifier.background(if (buy) BuyBg else TakeBg).cornerRadius(10.dp).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(if (buy) "買" else "拿", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (buy) BuyFg else TakeFg))
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(e.title, maxLines = 1, style = TextStyle(fontSize = if (compact) 13.sp else 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface))
                val sub = listOf(if (overdue) "已過期" else "", if (compact) "" else e.note).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = c.onSurfaceVariant))
            }
        }
    }

    @Composable
    private fun Empty(small: Boolean) {
        val c = GlanceTheme.colors
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Text("✅", style = TextStyle(fontSize = if (small) 22.sp else 30.sp, textAlign = TextAlign.Center))
            Text("今天沒有要順便做的 🎉", style = TextStyle(fontSize = if (small) 12.sp else 14.sp, fontWeight = FontWeight.Bold, color = c.onSurface, textAlign = TextAlign.Center))
            if (!small) Text("想到什麼，按右上角 ＋ 新增", style = TextStyle(fontSize = 11.sp, color = c.onSurfaceVariant))
        }
    }
}

class MemoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MemoWidget()
}

/** 小工具上點圓圈：標記完成（連結的採購也會變已買），項目從小工具消失。 */
class CheckCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[MemoWidget.KEY_ID] ?: return
        val e = AppDb.get(context).dao().errand(id) ?: return
        Repo.setErrandDone(context, e, LocalDate.now(), true)
        MemoWidget().update(context, glanceId)
    }
}

object WidgetRefresher {
    suspend fun refresh(ctx: Context) {
        runCatching { MemoWidget().updateAll(ctx.applicationContext) }
    }
}
