package tw.memo.errands.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import tw.memo.errands.data.*
import tw.memo.errands.logic.Logic
import java.time.LocalDate

/** 晚上確認：今天沒做完的要不要延到明天 + 明天下班要做。 */
@Composable
fun NightScreen(today: LocalDate, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val all by remember { dao.errandsFlow() }.collectAsState(initial = emptyList())
    val pending = Logic.pendingToday(all, today)
    val tomorrow = Logic.dueTomorrow(all, today)
    fun io(block: suspend () -> Unit) = scope.launch(Dispatchers.IO) { block() }
    suspend fun deferOne(e: Errand) {
        Logic.defer(e, today, System.currentTimeMillis()).forEach { if (it.id == 0L) dao.insert(it) else dao.update(it) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Text("🌙 晚上確認", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (pending.isNotEmpty()) {
                item {
                    Text("這些還沒做完，要延到明天嗎？", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { io { pending.forEach { deferOne(it) } } }, modifier = Modifier.fillMaxWidth()) {
                        Text("全部延到明天（${pending.size} 項）")
                    }
                }
                items(pending, key = { "p" + it.id }) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TypeTag(e.type); Spacer(Modifier.width(8.dp))
                                Text(e.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            }
                            val sub = listOf(Logic.whenLabel(e, today), e.note).filter { it.isNotBlank() }.joinToString(" · ")
                            if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Muted)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(onClick = { io { deferOne(e) } }) { Text("延到明天") }
                                OutlinedButton(onClick = { io { Repo.setErrandDone(ctx, e, today, true) } }) { Text("已完成") }
                                if (e.repeat == Repeat.ONCE) TextButton(onClick = { io { dao.delete(e) } }) { Text("刪除", color = Danger) }
                                else TextButton(onClick = { io { dao.update(e.copy(doneOn = today.toString())) } }) { Text("今天略過") }
                            }
                        }
                    }
                }
            } else item {
                Text("今天的都做完了 👍", fontSize = 17.sp, color = Muted, modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("明天下班要做（${Logic.shortDate(today.plusDays(1))}）", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            if (tomorrow.isEmpty()) item { Text("明天還沒有要做的事。", color = Muted) }
            items(tomorrow, key = { "t" + it.id }) { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeTag(e.type); Spacer(Modifier.width(8.dp))
                    Text(e.title, fontSize = 16.sp)
                    if (e.note.isNotBlank()) Text("  ${e.note}", fontSize = 13.sp, color = Muted)
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("好了") }
            }
        }
    }
}
