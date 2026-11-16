package tw.memo.errands.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.memo.errands.alarm.Notifier
import tw.memo.errands.alarm.Scheduler
import tw.memo.errands.data.*
import tw.memo.errands.logic.Logic
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(today: LocalDate, resumeTick: Int, onOpenNight: () -> Unit, onGoSettings: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val all by remember { dao.errandsFlow() }.collectAsState(initial = emptyList())
    val frequent by remember { dao.frequentFlow() }.collectAsState(initial = emptyList())
    var viewTomorrow by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Errand?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editFrequent by remember { mutableStateOf(false) }
    val permsOk = remember(resumeTick) { Notifier.canPost(ctx) && Scheduler.canExact(ctx) }
    val day = if (viewTomorrow) today.plusDays(1) else today
    val shown = Logic.visibleOn(all, day, today).sortedBy { Logic.isDone(it, day) }
    val pendingCount = Logic.pendingToday(all, today).size
    fun io(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) { block() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("下班順便做", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(Logic.dateLabel(day) + if (viewTomorrow) "（明天）" else "（今天）", color = Muted)
                Spacer(Modifier.height(8.dp))
                Segmented(listOf("看今天", "看明天"), if (viewTomorrow) 1 else 0, { viewTomorrow = it == 1 }, Modifier.fillMaxWidth())
            }
            if (!permsOk) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().clickable { onGoSettings() }) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⚠️ 提醒可能不會準時跳出", fontWeight = FontWeight.Bold)
                        Text("請到「設定」允許通知和精確鬧鐘（點這裡前往）", fontSize = 14.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("常用項目（點一下加到${if (viewTomorrow || Logic.defaultTomorrow(LocalTime.now())) "明天" else "今天"}）",
                        Modifier.weight(1f))
                    TextButton(onClick = { editFrequent = true }) { Text("編輯") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    frequent.forEach { f ->
                        AssistChip(onClick = {
                            val toTomorrow = viewTomorrow || Logic.defaultTomorrow(LocalTime.now())
                            val d = if (toTomorrow) today.plusDays(1) else today
                            io { dao.insert(Errand(title = f.title, type = f.type, dueDate = d.toString(), createdAt = System.currentTimeMillis())) }
                            Toast.makeText(ctx, "已加到${if (toTomorrow) "明天" else "今天"}：${f.title}", Toast.LENGTH_SHORT).show()
                        }, label = { Text("＋ ${f.title}") })
                    }
                }
            }
            if (!viewTomorrow) item {
                OutlinedButton(onClick = onOpenNight, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.NightsStay, null); Spacer(Modifier.width(6.dp))
                    Text(if (pendingCount > 0) "晚上確認：$pendingCount 項還沒做，要延到明天嗎？" else "晚上確認／看明天要做什麼")
                }
            }
            item { SectionTitle(if (viewTomorrow) "明天下班要做" else "今天要做") }
            if (shown.isEmpty()) item {
                Text(if (viewTomorrow) "明天還沒有要做的事。按右下角 ＋ 新增。" else "今天沒有要順便做的事 🎉", color = Muted,
                    modifier = Modifier.padding(vertical = 24.dp))
            }
            items(shown, key = { it.id }) { e ->
                ErrandRow(e, day, today, checkable = !viewTomorrow || e.repeat == Repeat.ONCE,
                    onToggle = { io { Repo.setErrandDone(ctx, e, day, !Logic.isDone(e, day)) } },
                    onClick = { editing = e },
                    onMoveToday = { io { dao.update(e.copy(dueDate = today.toString())) } })
            }
        }
        ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("新增") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    if (adding || editing != null) {
        ErrandDialog(editing, today, defaultTomorrow = viewTomorrow || Logic.defaultTomorrow(LocalTime.now()),
            onDismiss = { adding = false; editing = null },
            onSave = { e -> io { if (e.id == 0L) dao.insert(e) else dao.update(e) }; adding = false; editing = null },
            onDelete = editing?.let { ed -> { io { dao.delete(ed) }; editing = null } })
    }
    if (editFrequent) FrequentDialog(frequent, onDismiss = { editFrequent = false },
        onAdd = { t, ty -> io { dao.insert(Frequent(title = t, type = ty, sort = (frequent.maxOfOrNull { it.sort } ?: 0) + 1)) } },
        onDelete = { f -> io { dao.delete(f) } })
}

@Composable
fun ErrandRow(e: Errand, day: LocalDate, today: LocalDate, checkable: Boolean, onToggle: () -> Unit, onClick: () -> Unit, onMoveToday: () -> Unit) {
    val done = Logic.isDone(e, day)
    val overdue = Logic.isOverdue(e, today) && day == today
    Card(Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (checkable) Checkbox(checked = done, onCheckedChange = { onToggle() }) else Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeTag(e.type); Spacer(Modifier.width(8.dp))
                    Text(e.title, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                        textDecoration = if (done) TextDecoration.LineThrough else null, color = if (done) Muted else MaterialTheme.colorScheme.onSurface)
                }
                val sub = listOf(Logic.whenLabel(e, today), if (e.purchaseId != null) "📦 採購清單" else "", e.note).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = if (overdue) Danger else Muted)
            }
            if (overdue) TextButton(onClick = onMoveToday) { Text("移到今天") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrandDialog(initial: Errand?, today: LocalDate, defaultTomorrow: Boolean, onDismiss: () -> Unit, onSave: (Errand) -> Unit, onDelete: (() -> Unit)?) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: ItemType.TAKE) }
    var typeTouched by remember { mutableStateOf(initial != null) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    val initDate = initial?.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    // 0 今天 1 明天 2 指定日期 3 重複
    var whenSel by remember {
        mutableIntStateOf(when {
            initial == null -> if (defaultTomorrow) 1 else 0
            initial.repeat != Repeat.ONCE -> 3
            initDate == today -> 0
            initDate == today.plusDays(1) -> 1
            else -> 2
        })
    }
    var date by remember { mutableStateOf(initDate ?: today.plusDays(2)) }
    var daily by remember { mutableStateOf(initial?.repeat == Repeat.DAILY) }
    var days by remember { mutableStateOf(initial?.let { Logic.parseDays(it.weekdays) }?.takeIf { it.isNotEmpty() } ?: setOf(1, 2, 3, 4, 5)) }
    var pickDate by remember { mutableStateOf(false) }
    val valid = title.isNotBlank() && (whenSel != 3 || daily || days.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增順便做" else "編輯") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it; if (!typeTouched) type = Logic.inferType(it) },
                    label = { Text("要做什麼（例如：拿包裹、買牛奶）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Segmented(listOf("拿", "買"), if (type == ItemType.BUY) 1 else 0,
                    { type = if (it == 1) ItemType.BUY else ItemType.TAKE; typeTouched = true }, Modifier.fillMaxWidth())
                OutlinedTextField(note, { note = it }, label = { Text("備註（選填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("什麼時候", color = Muted, fontSize = 14.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("今天", "明天", "指定日期", "重複").forEachIndexed { i, l ->
                        FilterChip(selected = whenSel == i, onClick = { whenSel = i; if (i == 2) pickDate = true }, label = { Text(l) })
                    }
                }
                if (whenSel == 2) OutlinedButton(onClick = { pickDate = true }) { Text("📅 " + Logic.dateLabel(date)) }
                if (whenSel == 3) {
                    Segmented(listOf("每天", "選星期"), if (daily) 0 else 1, { daily = it == 0 }, Modifier.fillMaxWidth())
                    if (!daily) WeekdayPicker(days) { days = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val base = initial ?: Errand(title = "", createdAt = System.currentTimeMillis())
                val e = when (whenSel) {
                    3 -> base.copy(repeat = if (daily) Repeat.DAILY else Repeat.WEEKLY, dueDate = null,
                        weekdays = if (daily) "" else Logic.daysStr(days),
                        doneOn = if (initial?.repeat == Repeat.ONCE) null else base.doneOn)
                    else -> {
                        val d = when (whenSel) { 0 -> today; 1 -> today.plusDays(1); else -> date }
                        base.copy(repeat = Repeat.ONCE, dueDate = d.toString(), weekdays = "",
                            doneOn = if (initial != null && initial.repeat != Repeat.ONCE) null else base.doneOn)
                    }
                }
                onSave(e.copy(title = title.trim(), type = type, note = note.trim()))
            }) { Text("儲存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("刪除", color = Danger) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
    if (pickDate) DateDialog(date, { pickDate = false }) { date = it; whenSel = 2 }
}

@Composable
fun FrequentDialog(list: List<Frequent>, onDismiss: () -> Unit, onAdd: (String, String) -> Unit, onDelete: (Frequent) -> Unit) {
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ItemType.TAKE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("編輯常用項目") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypeTag(f.type); Spacer(Modifier.width(8.dp))
                        Text(f.title, Modifier.weight(1f))
                        IconButton(onClick = { onDelete(f) }) { Icon(Icons.Filled.Close, "刪除") }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(title, { title = it; type = Logic.inferType(it) }, label = { Text("新增常用項目") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Segmented(listOf("拿", "買"), if (type == ItemType.BUY) 1 else 0,
                        { type = if (it == 1) ItemType.BUY else ItemType.TAKE }, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = title.isNotBlank(), onClick = { onAdd(title.trim(), type); title = "" }) { Text("加入") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
