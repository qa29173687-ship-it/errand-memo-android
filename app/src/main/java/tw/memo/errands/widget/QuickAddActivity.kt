package tw.memo.errands.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.memo.errands.data.AppDb
import tw.memo.errands.data.Errand
import tw.memo.errands.data.ItemType
import tw.memo.errands.logic.Logic
import tw.memo.errands.ui.*
import java.time.LocalDate

/** 小工具 ＋：半透明底部面板，新增「今天下班順便做」。 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MemoTheme { Sheet(onClose = { finish() }, onAdd = ::add) } }
    }

    private fun add(title: String, type: String, note: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDb.get(this@QuickAddActivity).dao().insert(Errand(title = title, type = type, note = note,
                    dueDate = LocalDate.now().toString(), createdAt = System.currentTimeMillis()))
                WidgetRefresher.refresh(this@QuickAddActivity)
            }
            Toast.makeText(this@QuickAddActivity, "已加到今天：$title", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sheet(onClose: () -> Unit, onAdd: (String, String, String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val frequent by remember { AppDb.get(ctx).dao().frequentFlow() }.collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(ItemType.TAKE) }
    var touched by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val none = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color(0x73140A1E)).clickable(none, null) { onClose() }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clickable(none, null) {}
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding().imePadding().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).background(Color(0xFFEAD9CC), RoundedCornerShape(99.dp)))
            Text("新增今天下班順便做", fontSize = 19.sp, fontWeight = FontWeight.Black)
            OutlinedTextField(title, { title = it; if (!touched) type = Logic.inferType(it) },
                placeholder = { Text("要做什麼？例如：買衛生紙") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (title.isNotBlank()) onAdd(title.trim(), type, note.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
            Segmented(listOf("📦 拿", "🛒 買"), if (type == ItemType.BUY) 1 else 0,
                { type = if (it == 1) ItemType.BUY else ItemType.TAKE; touched = true }, Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, placeholder = { Text("備註（選填）：在哪裡買、取件碼…") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            if (frequent.isNotEmpty()) {
                Text("常用項目・點一下直接加入", fontSize = 12.sp, color = Muted, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    frequent.forEach { f ->
                        AssistChip(onClick = { onAdd(f.title, f.type, "") }, label = { Text(f.title) },
                            leadingIcon = { TypeTag(f.type) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                FilledTonalButton(onClick = onClose, modifier = Modifier.height(50.dp)) { Text("取消") }
                Button(onClick = { onAdd(title.trim(), type, note.trim()) }, enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f).height(50.dp)) { Text("加到今天", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
