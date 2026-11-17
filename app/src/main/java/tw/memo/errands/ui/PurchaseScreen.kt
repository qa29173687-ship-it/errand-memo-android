package tw.memo.errands.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.memo.errands.alarm.Scheduler
import tw.memo.errands.data.*
import tw.memo.errands.logic.Logic
import tw.memo.errands.logic.Sale
import tw.memo.errands.logic.Sale.Status
import java.time.LocalDate

@Composable
fun PurchaseScreen(today: LocalDate) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val all by remember { dao.purchasesFlow() }.collectAsState(initial = emptyList())
    val errands by remember { dao.errandsFlow() }.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<Purchase?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    fun st(p: Purchase) = Sale.status(p, today)
    val active = all.filter { st(it) == Status.ACTIVE }.sortedBy { Sale.range(it)!!.second }
    val upcoming = all.filter { st(it) == Status.UPCOMING }.sortedBy { Sale.range(it)!!.first }
    val ended = all.filter { st(it) == Status.ENDED }.sortedByDescending { Sale.range(it)!!.second }
    val noDeadline = all.filter { st(it) == Status.NONE }.sortedBy { it.createdAt }
    val bought = all.filter { it.boughtOn != null }.sortedByDescending { it.boughtOn }
    fun io(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) { block() }
    fun addToday(p: Purchase) = scope.launch {
        val added = withContext(Dispatchers.IO) { Repo.addPurchaseToToday(ctx, p.id, today) }
        Toast.makeText(ctx, if (added) "已加入今天下班要做：${p.title}" else "下班要做裡已經有了", Toast.LENGTH_SHORT).show()
    }

    @Composable
    fun PRow(p: Purchase) {
        val linked = errands.any { it.purchaseId == p.id && it.doneOn == null }
        PurchaseRow(p, today, linked,
            onToggle = { io { Repo.setBought(ctx, p, p.boughtOn == null, today) } },
            onClick = { editing = p }, onAddToday = { addToday(p) })
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("採購清單", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("特價期間：開始和結束前一晚提醒你；不設期限：想到就記，不提醒。", color = Muted, fontSize = 14.sp)
            }
            if (all.isEmpty()) item { Text("目前沒有要買的東西。按右下角 ＋ 新增。", color = Muted, modifier = Modifier.padding(vertical = 16.dp)) }
            if (active.isNotEmpty()) { item { SectionTitle("🔥 特價中（${active.size}）") }; items(active, key = { it.id }) { PRow(it) } }
            if (upcoming.isNotEmpty()) { item { SectionTitle("⏳ 即將開始（${upcoming.size}）") }; items(upcoming, key = { it.id }) { PRow(it) } }
            if (ended.isNotEmpty()) { item { SectionTitle("⌛ 已結束・還沒買（${ended.size}）") }; items(ended, key = { it.id }) { PRow(it) } }
            if (noDeadline.isNotEmpty()) { item { SectionTitle("📝 不設期限（${noDeadline.size}）") }; items(noDeadline, key = { it.id }) { PRow(it) } }
            if (bought.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("✅ 已買（${bought.size}）", Modifier.weight(1f))
                        TextButton(onClick = { confirmClear = true }) { Text("清除已買") }
                    }
                }
                items(bought, key = { it.id }) { PRow(it) }
            }
        }
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("新增") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (adding || editing != null) PurchaseDialog(editing, today, onDismiss = { adding = false; editing = null },
        onSave = { p -> io { if (p.id == 0L) dao.insert(p) else dao.update(p); Scheduler.schedulePurchases(ctx) }; adding = false; editing = null },
        onDelete = editing?.let { ed -> { io { dao.delete(ed); Scheduler.schedulePurchases(ctx) }; editing = null } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("清除已買？") }, text = { Text("會把 ${bought.size} 項已買的東西從清單刪除。") },
        confirmButton = { TextButton(onClick = { io { dao.clearBought(); Scheduler.schedulePurchases(ctx) }; confirmClear = false }) { Text("清除", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun PurchaseRow(p: Purchase, today: LocalDate, linked: Boolean, onToggle: () -> Unit, onClick: () -> Unit, onAddToday: () -> Unit) {
    val status = Sale.status(p, today)
    val isBought = status == Status.BOUGHT
    Card(Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isBought, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f)) {
                    Text(p.title, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                        textDecoration = if (isBought) TextDecoration.LineThrough else null,
                        color = if (isBought) Muted else MaterialTheme.colorScheme.onSurface)
                    if (isBought) Text("已買 · ${p.boughtOn}", fontSize = 13.sp, color = Muted)
                    else {
                        if (p.kind == PKind.PERIOD) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val c = when (status) { Status.ACTIVE -> Accent; Status.UPCOMING -> TakeColor; else -> Danger }
                                StatusChip(Sale.statusLabel(p, today), c)
                                Spacer(Modifier.width(6.dp))
                                Text(Sale.rangeLabel(p), fontSize = 13.sp, color = Muted)
                            }
                            Text("${Sale.timingLabel(p.timing)}${if (p.mode == Mode.ALARM) "鬧鐘" else "通知"}", fontSize = 12.sp, color = Muted)
                        }
                        if (p.note.isNotBlank()) Text(p.note, fontSize = 13.sp, color = Muted)
                    }
                }
            }
            if (!isBought) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (p.kind == PKind.PERIOD) TextButton(onClick = onClick) { Text(if (status == Status.ENDED) "重新選日期" else "改日期") }
                TextButton(onClick = onAddToday, enabled = !linked) { Text(if (linked) "已在下班要做" else "加入今天下班要做") }
            }
        }
    }
}

@Composable
fun PurchaseDialog(initial: Purchase?, today: LocalDate, onDismiss: () -> Unit, onSave: (Purchase) -> Unit, onDelete: (() -> Unit)?) {
    val initRange = initial?.let { Sale.range(it) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: PKind.PERIOD) }
    var start by remember { mutableStateOf(initRange?.first ?: today.plusDays(1)) }
    var end by remember { mutableStateOf(initRange?.second ?: today.plusDays(7)) }
    var timing by remember { mutableStateOf(initial?.timing ?: Timing.NIGHT) }
    var mode by remember { mutableStateOf(initial?.mode ?: Mode.NOTIFY) }
    var pick by remember { mutableStateOf<String?>(null) }
    val valid = title.isNotBlank() && (kind == PKind.NONE || !end.isBefore(start))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增採購" else "編輯採購") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("要買什麼") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("備註（例如：雙12、哪家賣場）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Segmented(listOf("特價期間", "不設期限"), if (kind == PKind.NONE) 1 else 0,
                    { kind = if (it == 1) PKind.NONE else PKind.PERIOD }, Modifier.fillMaxWidth())
                if (kind == PKind.PERIOD) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("開始", color = Muted, fontSize = 13.sp)
                            OutlinedButton(onClick = { pick = "start" }, Modifier.fillMaxWidth()) { Text(Logic.shortDate(start), maxLines = 1) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text("結束", color = Muted, fontSize = 13.sp)
                            OutlinedButton(onClick = { pick = "end" }, Modifier.fillMaxWidth()) { Text(Logic.shortDate(end), maxLines = 1) }
                        }
                    }
                    if (end.isBefore(start)) Text("結束日不能早於開始日", color = Danger, fontSize = 13.sp)
                    else Text(if (start == end) "只有一天：前一次提醒「明天特價（只有一天）」" else "提醒兩次：「明天開始特價」和「明天特價就結束了」",
                        fontSize = 13.sp, color = Muted)
                    Text("什麼時候提醒", color = Muted, fontSize = 14.sp)
                    Segmented(listOf("前一天晚上", "當天早上"), if (timing == Timing.MORNING) 1 else 0,
                        { timing = if (it == 1) Timing.MORNING else Timing.NIGHT }, Modifier.fillMaxWidth())
                    Segmented(listOf("通知", "鬧鐘"), if (mode == Mode.ALARM) 1 else 0, { mode = if (it == 1) Mode.ALARM else Mode.NOTIFY }, Modifier.fillMaxWidth())
                } else Text("不設期限的項目不會提醒，會放在清單的「不設期限」區。", fontSize = 13.sp, color = Muted)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val base = initial ?: Purchase(title = "", createdAt = System.currentTimeMillis())
                onSave(base.copy(title = title.trim(), note = note.trim(), kind = kind,
                    startDate = if (kind == PKind.PERIOD) start.toString() else null,
                    endDate = if (kind == PKind.PERIOD) end.toString() else null,
                    timing = timing, mode = mode))
            }) { Text("儲存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("刪除", color = Danger) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
    when (pick) {
        "start" -> DateDialog(start, { pick = null }) { start = it; if (end.isBefore(it)) end = it }
        "end" -> DateDialog(end, { pick = null }) { end = it }
    }
}
