package tw.memo.errands.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.memo.errands.alarm.Notifier
import tw.memo.errands.alarm.Scheduler
import tw.memo.errands.data.Mode
import tw.memo.errands.data.Prefs
import tw.memo.errands.logic.Logic
import tw.memo.errands.widget.ClockApps
import tw.memo.errands.widget.WidgetPin
import tw.memo.errands.widget.WidgetPrefsBus
import tw.memo.errands.widget.WidgetRefresher
import tw.memo.errands.widget.WidgetStyle
import java.time.LocalTime

@Composable
fun SettingsScreen(resumeTick: Int, requestNotif: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    fun changed() { tick++; scope.launch(Dispatchers.IO) { Scheduler.rescheduleAll(ctx) } }
    var pickTime by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("設定", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        WidgetCard(prefs, resumeTick) { scope.launch(Dispatchers.IO) { WidgetRefresher.refresh(ctx) } }
        key(tick) {
            SlotCard("🏃 下班提醒", "到點列出今天要拿、要買的東西（沒有項目就不提醒）",
                prefs.eveningTime, prefs.eveningDays, prefs.eveningMode,
                onTime = { pickTime = "evening" }, onDays = { prefs.eveningDays = it; changed() }, onMode = { prefs.eveningMode = it; changed() })
            SlotCard("🌙 晚上確認", "還沒做完的問你要不要延到明天，並列出「明天下班要做」和「明天特價」（開始/結束），可以前一晚先確認（全部都空就不提醒）",
                prefs.nightTime, prefs.nightDays, prefs.nightMode,
                onTime = { pickTime = "night" }, onDays = { prefs.nightDays = it; changed() }, onMode = { prefs.nightMode = it; changed() })
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("📦 採購提醒", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("特價期間：開始前一晚提醒「明天開始特價」、結束前一晚提醒「明天特價就結束了」（只有一天的只提醒一次），在晚上確認時間（${prefs.nightTime}）合併在晚上確認裡。不設期限的項目不提醒。", fontSize = 13.sp, color = Muted)
                    Text("項目選「當天早上」的話，改在開始/結束當天這個時間提醒：", fontSize = 13.sp, color = Muted)
                    OutlinedButton(onClick = { pickTime = "purchase" }) { Text("⏰ 當天早上 " + prefs.purchaseTime) }
                }
            }
        }
        PermissionCard(resumeTick, requestNotif)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🔔 測試", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("按下後 10 秒會跳出，可以先鎖螢幕試試看。", fontSize = 13.sp, color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { Scheduler.test(ctx, false); Toast.makeText(ctx, "10 秒後跳出測試通知", Toast.LENGTH_SHORT).show() }) { Text("測試通知") }
                    OutlinedButton(onClick = { Scheduler.test(ctx, true); Toast.makeText(ctx, "10 秒後響測試鬧鐘", Toast.LENGTH_SHORT).show() }) { Text("測試鬧鐘") }
                }
            }
        }
        Text("版本 ${appVersion(ctx)} · 資料只存在這支手機", fontSize = 13.sp, color = Muted)
    }

    pickTime?.let { which ->
        val cur = when (which) { "evening" -> prefs.eveningTime; "night" -> prefs.nightTime; else -> prefs.purchaseTime }
        TimeDialog(Logic.parseTime(cur), { pickTime = null }) { t ->
            val s = Logic.timeStr(t)
            when (which) { "evening" -> prefs.eveningTime = s; "night" -> prefs.nightTime = s; else -> prefs.purchaseTime = s }
            changed()
        }
    }
}

@Composable
private fun SlotCard(title: String, desc: String, time: String, days: Set<Int>, mode: String,
                     onTime: () -> Unit, onDays: (Set<Int>) -> Unit, onMode: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(desc, fontSize = 13.sp, color = Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onTime) { Text("⏰ $time") }
                Text(Logic.daysLabel(days), color = Muted, fontSize = 14.sp)
            }
            WeekdayPicker(days, onDays)
            Segmented(listOf("通知", "鬧鐘（響到按掉）"), if (mode == Mode.ALARM) 1 else 0,
                { onMode(if (it == 1) Mode.ALARM else Mode.NOTIFY) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PermissionCard(resumeTick: Int, requestNotif: () -> Unit) {
    val ctx = LocalContext.current
    val notifOk = remember(resumeTick) { Notifier.canPost(ctx) }
    val exactOk = remember(resumeTick) { Scheduler.canExact(ctx) }
    val fullOk = remember(resumeTick) { Notifier.canFullScreen(ctx) }
    val batteryOk = remember(resumeTick) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    fun open(i: Intent) = try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🔐 讓提醒準時跳出", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            PermRow("通知", notifOk) {
                if (Build.VERSION.SDK_INT >= 33 && !notifOk) {
                    val act = ctx as? android.app.Activity
                    if (act != null && !act.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) && Prefs(ctx).askedNotifPermission)
                        open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    else requestNotif()
                } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            if (Build.VERSION.SDK_INT >= 31) PermRow("精確鬧鐘（準時響）", exactOk) {
                open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
            }
            if (Build.VERSION.SDK_INT >= 34) PermRow("鎖定畫面顯示鬧鐘（全螢幕）", fullOk) {
                open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)))
            }
            PermRow("不限制電池用量（建議）", batteryOk) {
                open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            Text("小米、OPPO、vivo、華碩等手機另外有「自啟動／背景執行」設定，請在系統設定把「順便備忘」設成允許，重開機後提醒才不會消失。",
                fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun PermRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((if (ok) "✅ " else "⚠️ ") + label, Modifier.weight(1f), fontSize = 15.sp)
        if (ok) Text("已允許", color = Muted, fontSize = 13.sp) else TextButton(onClick = onFix) { Text("去開啟") }
    }
}

/** 桌面小工具外觀：底色、透明度；改了馬上更新桌面上的小工具。 */
@Composable
private fun WidgetCard(prefs: Prefs, resumeTick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val placed = remember(resumeTick) { WidgetPin.count(ctx) }
    var tint by remember { mutableStateOf(prefs.widgetTint) }
    var alpha by remember { mutableStateOf(prefs.widgetAlpha) }
    val tints = listOf(WidgetStyle.TINT_LIGHT, WidgetStyle.TINT_DARK, WidgetStyle.TINT_SYSTEM)
    val alphas = listOf(WidgetStyle.ALPHA_HIGH, WidgetStyle.ALPHA_MID, WidgetStyle.ALPHA_LOW)
    fun apply() { WidgetPrefsBus.bump(); onChanged() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🪟 桌面小工具", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("半透明底色，可以看到桌布（Android 小工具沒辦法做毛玻璃模糊）。桌布很花的話，選深色或透明度「低」比較好讀。", fontSize = 13.sp, color = Muted)
            Text("底色", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Segmented(listOf("淺色", "深色", "跟隨系統"), tints.indexOf(tint).coerceAtLeast(0),
                { tint = tints[it]; prefs.widgetTint = tint; apply() }, Modifier.fillMaxWidth())
            Text("透明度（高＝最透明）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Segmented(listOf("高", "中", "低"), alphas.indexOf(alpha).coerceAtLeast(0),
                { alpha = alphas[it]; prefs.widgetAlpha = alpha; apply() }, Modifier.fillMaxWidth())
            Text(if (placed > 0) "✅ 桌面上已經有 $placed 個順便備忘小工具" else "桌面上還沒有順便備忘小工具",
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((large, label) in listOf(false to "📌 加 4×2", true to "📌 加 4×4")) Button(onClick = {
                    if (!WidgetPin.request(ctx, large)) Toast.makeText(ctx, "這支手機不支援直接加入，請長按桌面 → 小工具 → 順便備忘", Toast.LENGTH_LONG).show()
                }, modifier = Modifier.weight(1f)) { Text(label) }
            }
            HorizontalDivider()
            ClockRow(prefs) { apply() }
            HorizontalDivider()
            Text("也可以：長按桌面空白處 → 小工具 → 順便備忘，選「4×2」或「4×4」。小工具是在「桌面」上，不在 App 裡面。點日期或標題會打開順便備忘。", fontSize = 12.sp, color = Muted)
        }
    }
}

fun appVersion(ctx: android.content.Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

/** 點小工具時間要打開哪個 App：顯示偵測結果、可自選、可測試。 */
@Composable
private fun ClockRow(prefs: Prefs, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var pref by remember { mutableStateOf(prefs.clockApp) }
    val current = remember(pref) { ClockApps.current(ctx, pref) }
    var picking by remember { mutableStateOf(false) }
    Text("⏰ 點時間打開", fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Text(ClockApps.label(ctx, current) + if (pref.isBlank()) "（自動偵測）" else "（自己選的）", fontSize = 15.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) { Text("選擇 App") }
        OutlinedButton(onClick = {
            val i = ClockApps.intent(ctx, pref)
            val ok = i != null && runCatching { ctx.startActivity(i) }.isSuccess
            if (!ok) Toast.makeText(ctx, "打不開，請按「選擇 App」換一個試試", Toast.LENGTH_LONG).show()
        }, modifier = Modifier.weight(1f)) { Text("測試") }
    }
    if (picking) {
        val opts = remember { ClockApps.options(ctx) }
        AlertDialog(onDismissRequest = { picking = false }, confirmButton = {}, dismissButton = { TextButton({ picking = false }) { Text("取消") } },
            title = { Text("點時間要打開") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    fun pick(v: String) { pref = v; prefs.clockApp = v; picking = false; onChanged() }
                    TextButton({ pick("") }, Modifier.fillMaxWidth()) { Text("自動偵測（建議）", Modifier.fillMaxWidth()) }
                    var lastSection = -1
                    for (o in opts) {
                        if (o.section != lastSection) {
                            lastSection = o.section
                            Text(if (o.section == 0) "時鐘／鬧鐘" else "其他 App", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
                        }
                        TextButton({ pick(ClockApps.encode(o.choice)) }, Modifier.fillMaxWidth()) { Text(o.label, Modifier.fillMaxWidth()) }
                    }
                }
            })
    }
}
