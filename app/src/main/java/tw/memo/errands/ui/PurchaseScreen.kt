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
import tw.memo.errands.logic.Logic.PStatus
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
    var redate by remember { mutableStateOf<Purchase?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val pending = all.filter { it.boughtOn == null }.sortedBy { it.date }
    val bought = all.filter { it.boughtOn != null }.sortedByDescending { it.boughtOn }
    fun io(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) { block() }
    fun addToday(p: Purchase) = scope.launch {
        val added = withContext(Dispatchers.IO) { Repo.addPurchaseToToday(ctx, p.id, today) }
        Toast.makeText(ctx, if (added) "已加入今天下班要做：${p.title}" else "今天下班要做裡已經有了", Toast.LENGTH_SHORT).show()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("採購清單", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("看到打折日自己選日期，到時提醒你；沒買到可以改日期。", color = Muted, fontSize = 14.sp)
            }
            item { SectionTitle("待買（${pending.size}）") }
            if (pending.isEmpty()) item { Text("目前沒有要買的東西。按右下角 ＋ 新增。", color = Muted, modifier = Modifier.padding(vertical = 16.dp)) }
            items(pending, key = { it.id }) { p ->
                val linked = errands.any { it.purchaseId == p.id && it.doneOn == null }
                PurchaseRow(p, today, linked,
                    onToggle = { io { Repo.setBought(ctx, p, true, today) } },
                    onClick = { editing = p }, onRedate = { redate = p }, onAddToday = { addToday(p) })
            }
            if (bought.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("已買（${bought.size}）", Modifier.weight(1f))
                        TextButton(onClick = { confirmClear = true }) { Text("清除已買") }
                    }
                }
                items(bought, key = { it.id }) { p ->
                    PurchaseRow(p, today, false, onToggle = { io { Repo.setBought(ctx, p, false, today) } },
                        onClick = { editing = p }, onRedate = {}, onAddToday = {})
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("新增") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (adding || editing != null) PurchaseDialog(editing, today, onDismiss = { adding = false; editing = null },
        onSave = { p -> io { if (p.id == 0L) dao.insert(p) else dao.update(p); Scheduler.schedulePurchases(ctx) }; adding = false; editing = null },
        onDelete = editing?.let { ed -> { io { dao.delete(ed); Scheduler.schedulePurchases(ctx) }; editing = null } })
    redate?.let { p ->
        val init = LocalDate.parse(p.date).let { if (it.isBefore(today)) today else it }
        DateDialog(init, { redate = null }) { d -> io { dao.update(p.copy(date = d.toString())); Scheduler.schedulePurchases(ctx) } }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false },
        title = { Text("清除已買？") }, text = { Text("會把 ${bought.size} 項已買的東西從清單刪除。") },
        confirmButton = { TextButton(onClick = { io { dao.clearBought(); Scheduler.schedulePurchases(ctx) }; confirmClear = false }) { Text("清除", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}

@Composable
fun PurchaseRow(p: Purchase, today: LocalDate, linked: Boolean, onToggle: () -> Unit, onClick: () -> Unit, onRedate: () -> Unit, onAddToday: () -> Unit) {
    val status = Logic.purchaseStatus(p, today)
    val isBought = status == PStatus.BOUGHT
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
                        val color = when (status) { PStatus.OVERDUE -> Danger; PStatus.TODAY -> Accent; else -> Muted }
                        Text((if (status == PStatus.OVERDUE) "已過期 · " else "") + Logic.purchaseDateLabel(p, today),
                            fontSize = 13.sp, color = color, fontWeight = if (status != PStatus.UPCOMING) FontWeight.Bold else null)
                        Text("${Logic.remindLabel(p.remindDays)}${if (p.mode == Mode.ALARM) "鬧鐘" else "通知"}" +
                            if (p.note.isNotBlank()) " · ${p.note}" else "", fontSize = 13.sp, color = Muted)
                    }
                }
            }
            if (!isBought) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onRedate) { Text(if (status == PStatus.OVERDUE) "重新選日期" else "改日期") }
                TextButton(onClick = onAddToday, enabled = !linked) { Text(if (linked) "已在今天下班要做" else "加入下班要做") }
            }
        }
    }
}

@Composable
fun PurchaseDialog(initial: Purchase?, today: LocalDate, onDismiss: () -> Unit, onSave: (Purchase) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember { mutableStateOf(initial?.date?.let { LocalDate.parse(it) } ?: today.plusDays(7)) }
    var remind by remember { mutableIntStateOf(initial?.remindDays ?: 1) }
    var mode by remember { mutableStateOf(initial?.mode ?: Mode.NOTIFY) }
    var pick by remember { mutableStateOf(false) }
    val opts = listOf(1, 0, 3, 7)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增採購" else "編輯採購") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("要買什麼") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("備註（例如：雙12、哪家賣場）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("日期（打折日）", color = Muted, fontSize = 14.sp)
                OutlinedButton(onClick = { pick = true }) { Text("📅 " + Logic.dateLabel(date)) }
                Text("提醒", color = Muted, fontSize = 14.sp)
                Segmented(listOf("前一晚", "當天", "前3天", "前7天"), opts.indexOf(remind).coerceAtLeast(0), { remind = opts[it] }, Modifier.fillMaxWidth())
                Text(if (remind == 0) "當天早上（設定裡的採購提醒時間）提醒" else "在晚上確認時間提醒" + if (remind == 1) "，會一起列在晚上確認的「明天打折要買」" else "",
                    fontSize = 13.sp, color = Muted)
                Segmented(listOf("通知", "鬧鐘"), if (mode == Mode.ALARM) 1 else 0, { mode = if (it == 1) Mode.ALARM else Mode.NOTIFY }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                val base = initial ?: Purchase(title = "", date = "", createdAt = System.currentTimeMillis())
                onSave(base.copy(title = title.trim(), note = note.trim(), date = date.toString(), remindDays = remind, mode = mode))
            }) { Text("儲存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("刪除", color = Danger) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
    if (pick) DateDialog(date, { pick = false }) { date = it }
}
