package tw.memo.errands.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.memo.errands.logic.Logic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

val Accent = Color(0xFFE8743B)
val Muted = Color(0xFF7A7570)
val Danger = Color(0xFFC62828)
val TakeColor = Color(0xFF3B7DD8)
val BuyColor = Color(0xFF2E9E5B)

private val scheme = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D3), onPrimaryContainer = Color(0xFF5A2408),
    secondary = Color(0xFF6C5B4E), secondaryContainer = Color(0xFFF5E6DA),
    background = Color(0xFFFFF7EE), surface = Color(0xFFFFFBF7), surfaceVariant = Color(0xFFF4ECE4),
)

@Composable
fun MemoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

@Composable
fun TypeTag(type: String) {
    val c = if (type == "buy") BuyColor else TakeColor
    Surface(color = c.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(Logic.typeLabel(type), color = c, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = Muted, modifier = modifier.padding(vertical = 6.dp))

fun LocalDate.toUtcMillis(): Long = toEpochDay() * 86_400_000L
fun millisToDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.toUtcMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(millisToDate(it)) }; onDismiss() }) { Text("確定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("確定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        text = { TimePicker(state = state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, label ->
            SegmentedButton(selected = i == selected, onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(label, maxLines = 1) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekdayPicker(days: Set<Int>, onChange: (Set<Int>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(1, 2, 3, 4, 5, 6, 0).forEach { d ->
            FilterChip(selected = d in days, onClick = { onChange(if (d in days) days - d else days + d) },
                label = { Text(Logic.WEEKDAYS[d]) })
        }
    }
}
