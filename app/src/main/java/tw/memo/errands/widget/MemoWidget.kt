package tw.memo.errands.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.flow.MutableStateFlow
import tw.memo.errands.R
import tw.memo.errands.alarm.Notifier
import tw.memo.errands.data.AppDb
import tw.memo.errands.data.Errand
import tw.memo.errands.data.Prefs
import tw.memo.errands.data.Repo
import tw.memo.errands.logic.Logic
import tw.memo.errands.ui.MainActivity
import java.time.LocalDate

/** 設定改了 → 版本 +1，小工具重新讀底色/透明度。 */
object WidgetPrefsBus {
    val version = MutableStateFlow(0)
    fun bump() { version.value++ }
}

/** 一組跟著底色走的顏色：淺/深固定，跟隨系統就用 day/night。 */
class WColors(day: WidgetStyle.Palette, night: WidgetStyle.Palette) {
    private fun cp(f: (WidgetStyle.Palette) -> Int, d: WidgetStyle.Palette, n: WidgetStyle.Palette): ColorProvider =
        ColorProvider(day = Color(f(d)), night = Color(f(n)))
    val bg = cp({ it.bg }, day, night); val ink = cp({ it.ink }, day, night); val soft = cp({ it.soft }, day, night)
    val accent = cp({ it.accent }, day, night); val tile = cp({ it.tile }, day, night); val outline = cp({ it.outline }, day, night)
    val divider = cp({ it.divider }, day, night); val track = cp({ it.track }, day, night)
    val plus = cp({ it.plus }, day, night); val onPlus = cp({ it.onPlus }, day, night)
    val takeFg = cp({ it.takeFg }, day, night); val takeBg = cp({ it.takeBg }, day, night)
    val buyFg = cp({ it.buyFg }, day, night); val buyBg = cp({ it.buyBg }, day, night); val ok = cp({ it.ok }, day, night)
}

/** 桌面小工具「今天下班順便做」：左邊大時鐘（系統 TextClock）＋右邊今天的清單。4×2 / 4×4。 */
class MemoWidget : GlanceAppWidget() {
    companion object {
        val KEY_ID = ActionParameters.Key<Long>("errandId")

        fun appIntent(ctx: Context): Intent = Intent(ctx, MainActivity::class.java)
            .putExtra(Notifier.EXTRA_TARGET, Notifier.TARGET_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** 手機內建的時鐘 App；都找不到就回 null。 */
        fun clockIntent(ctx: Context): Intent? {
            val pm = ctx.packageManager
            val show = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolves = runCatching { show.resolveActivity(pm) != null }.getOrDefault(false)
            val launchable = if (resolves) emptySet() else WidgetStyle.CLOCK_PACKAGES
                .filter { runCatching { pm.getLaunchIntentForPackage(it) != null }.getOrDefault(false) }.toSet()
            return when (val t = WidgetStyle.chooseClock(resolves, launchable)) {
                WidgetStyle.ClockTarget.ShowAlarms -> show
                is WidgetStyle.ClockTarget.Package -> pm.getLaunchIntentForPackage(t.pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                WidgetStyle.ClockTarget.None -> null
            }
        }
    }

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDb.get(context).dao()
        val initial = dao.errands()
        provideContent {
            val flow = remember { dao.errandsFlow() }
            val list by flow.collectAsState(initial)
            val v by WidgetPrefsBus.version.collectAsState()
            val style = remember(v) { Prefs(context).let { it.widgetTint to it.widgetAlpha } }
            Content(list, style.first, style.second)
        }
    }

    @Composable
    private fun Content(all: List<Errand>, tint: String, alpha: String) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val m = WidgetStyle.metrics(size.width.value, size.height.value)
        val today = LocalDate.now()
        val s = WidgetLogic.state(all, today, null)
        val light = WidgetStyle.palette(false, alpha)
        val dark = WidgetStyle.palette(true, alpha)
        val c = when (tint) {
            WidgetStyle.TINT_LIGHT -> WColors(light, light)
            WidgetStyle.TINT_DARK -> WColors(dark, dark)
            else -> WColors(light, dark)
        }
        val bg = if (Build.VERSION.SDK_INT >= 31) GlanceModifier.background(c.bg).cornerRadius(24.dp)
        else GlanceModifier.background(ImageProvider(bgRes(tint, alpha)))
        Row(bg.then(GlanceModifier.fillMaxSize().appWidgetBackground())) {
            Column(GlanceModifier.width(m.clockCol.dp).fillMaxHeight()
                .padding(start = 15.dp, end = 4.dp, top = if (m.large) 16.dp else 10.dp, bottom = if (m.large) 14.dp else 10.dp),
                verticalAlignment = if (m.large) Alignment.Top else Alignment.CenterVertically) {
                AndroidRemoteViews(clockViews(ctx, tint, m), GlanceModifier.fillMaxWidth())
                if (m.large) {
                    Spacer(GlanceModifier.defaultWeight())
                    Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(appIntent(ctx)))) {
                        Text("今天完成", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.soft))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${s.done}", style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold,
                                color = if (s.allDone) c.ok else c.ink))
                            Text(" / ${s.total}", style = TextStyle(fontSize = 15.sp, color = c.soft))
                        }
                        Spacer(GlanceModifier.height(6.dp))
                        LinearProgressIndicator(progress = s.progress, modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                            color = c.accent, backgroundColor = c.track)
                    }
                }
            }
            Column(GlanceModifier.width(1.dp).fillMaxHeight().padding(vertical = 14.dp)) {
                Spacer(GlanceModifier.fillMaxSize().background(c.divider))
            }
            Column(GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp)) {
                Header(ctx, s, c)
                Spacer(GlanceModifier.height(6.dp))
                if (s.items.isEmpty()) Empty(c, m.large)
                else LazyColumn(GlanceModifier.defaultWeight().fillMaxWidth()) {
                    items(s.items, itemId = { it.id }) { e ->
                        Column(GlanceModifier.fillMaxWidth()) {
                            ItemRow(e, today, m.large, c)
                            Spacer(GlanceModifier.height(5.dp))
                        }
                    }
                }
            }
        }
    }

    private fun bgRes(tint: String, alpha: String): Int = when (tint) {
        WidgetStyle.TINT_LIGHT -> when (alpha) { WidgetStyle.ALPHA_HIGH -> R.drawable.widget_bg_light_high; WidgetStyle.ALPHA_LOW -> R.drawable.widget_bg_light_low; else -> R.drawable.widget_bg_light_mid }
        WidgetStyle.TINT_DARK -> when (alpha) { WidgetStyle.ALPHA_HIGH -> R.drawable.widget_bg_dark_high; WidgetStyle.ALPHA_LOW -> R.drawable.widget_bg_dark_low; else -> R.drawable.widget_bg_dark_mid }
        else -> when (alpha) { WidgetStyle.ALPHA_HIGH -> R.drawable.widget_bg_system_high; WidgetStyle.ALPHA_LOW -> R.drawable.widget_bg_system_low; else -> R.drawable.widget_bg_system_mid }
    }

    /** 左欄時鐘：點時間開時鐘 App，點日期開順便備忘。 */
    private fun clockViews(ctx: Context, tint: String, m: WidgetStyle.Metrics): RemoteViews {
        val layout = when (tint) {
            WidgetStyle.TINT_LIGHT -> R.layout.widget_clock_light
            WidgetStyle.TINT_DARK -> R.layout.widget_clock_dark
            else -> R.layout.widget_clock_system
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val app = PendingIntent.getActivity(ctx, 41, appIntent(ctx), flags)
        val clock = clockIntent(ctx)?.let { PendingIntent.getActivity(ctx, 42, it, flags) } ?: app
        return RemoteViews(ctx.packageName, layout).apply {
            setTextViewTextSize(R.id.clock_h, TypedValue.COMPLEX_UNIT_DIP, m.clockSp)
            setTextViewTextSize(R.id.clock_m, TypedValue.COMPLEX_UNIT_DIP, m.clockSp)
            setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_DIP, m.dateSp)
            setOnClickPendingIntent(R.id.clock_time, clock)
            setOnClickPendingIntent(R.id.clock_date, app)
        }
    }

    @Composable
    private fun Header(ctx: Context, s: WidgetLogic.State, c: WColors) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(appIntent(ctx)))) {
                Text("今天下班順便做", maxLines = 1, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink))
                if (s.remaining > 0) Row {
                    Text("還有 ", style = TextStyle(fontSize = 11.5.sp, color = c.soft))
                    Text("${s.remaining}", style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = c.accent))
                    Text(" 項", style = TextStyle(fontSize = 11.5.sp, color = c.soft))
                } else Text(WidgetLogic.statusLabel(s), maxLines = 1,
                    style = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = if (s.allDone) c.ok else c.soft))
            }
            Box(GlanceModifier.size(36.dp).background(c.plus).cornerRadius(12.dp)
                .clickable(actionStartActivity<QuickAddActivity>()), contentAlignment = Alignment.Center) {
                Text("＋", style = TextStyle(fontSize = 20.sp, color = c.onPlus, fontWeight = FontWeight.Bold))
            }
        }
    }

    @Composable
    private fun ItemRow(e: Errand, today: LocalDate, large: Boolean, c: WColors) {
        val buy = e.type == "buy"
        val overdue = Logic.isOverdue(e, today)
        Row(GlanceModifier.fillMaxWidth().background(c.tile).cornerRadius(13.dp)
            .padding(horizontal = 6.dp, vertical = if (large) 5.dp else 3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(30.dp).clickable(actionRunCallback<CheckCallback>(actionParametersOf(KEY_ID to e.id))),
                contentAlignment = Alignment.Center) {
                Image(ImageProvider(R.drawable.ic_circle), contentDescription = "完成：${e.title}",
                    modifier = GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(c.outline))
            }
            Spacer(GlanceModifier.width(3.dp))
            Box(GlanceModifier.background(if (buy) c.buyBg else c.takeBg).cornerRadius(10.dp).padding(horizontal = 6.dp, vertical = 1.dp)) {
                Text(if (buy) "買" else "拿", style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (buy) c.buyFg else c.takeFg))
            }
            Spacer(GlanceModifier.width(7.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(e.title, maxLines = 1, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink))
                val sub = listOf(if (overdue) "已過期" else "", if (large) e.note else "").filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = c.soft))
            }
        }
    }

    @Composable
    private fun Empty(c: WColors, large: Boolean) {
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
            Text("✅", style = TextStyle(fontSize = if (large) 30.sp else 22.sp, textAlign = TextAlign.Center))
            Text("今天沒有要順便做的 🎉", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.ink, textAlign = TextAlign.Center))
            if (large) Text("想到什麼，按右上角 ＋ 新增", style = TextStyle(fontSize = 11.sp, color = c.soft, textAlign = TextAlign.Center))
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
