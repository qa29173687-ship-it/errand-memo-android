package tw.memo.errands.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.memo.errands.ui.MainActivity
import tw.memo.errands.data.Repo
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalTime

/** 鬧鐘全螢幕畫面。鈴聲來自持續響鈴（FLAG_INSISTENT）的鬧鐘通知，按關閉/稍後即取消通知停止響鈴。 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); render(intent) }

    private fun render(i: Intent) {
        val title = i.getStringExtra(Notifier.EXTRA_TITLE) ?: "提醒"
        val text = i.getStringExtra(Notifier.EXTRA_TEXT) ?: ""
        val nid = i.getIntExtra(Notifier.EXTRA_NID, 0)
        val kind = i.getStringExtra(Scheduler.EXTRA_KIND) ?: ""
        val pid = i.getLongExtra(Scheduler.EXTRA_PID, 0)
        val target = i.getStringExtra(Notifier.EXTRA_TARGET) ?: Notifier.TARGET_TODAY
        val now = LocalTime.now()
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF2B2A33)).padding(28.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(40.dp))
                    Text("⏰", fontSize = 48.sp)
                    Text("%02d:%02d".format(now.hour, now.minute), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    Text(text, color = Color(0xFFE6E1F0), fontSize = 18.sp, lineHeight = 28.sp)
                    Spacer(Modifier.height(36.dp))
                    Button(onClick = { Notifier.cancel(this@AlarmActivity, nid); finish() },
                        modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("關閉", fontSize = 20.sp) }
                    if (kind == Scheduler.KIND_PURCHASE) {
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = {
                            Notifier.cancel(this@AlarmActivity, nid)
                            val app = applicationContext
                            CoroutineScope(Dispatchers.IO).launch {
                                val added = Repo.addPurchaseToToday(app, pid)
                                launch(Dispatchers.Main) {
                                    Toast.makeText(app, if (added) "已加入今天下班要做" else "今天下班要做裡已經有了", Toast.LENGTH_SHORT).show()
                                }
                            }
                            finish()
                        }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("加入今天下班要做", fontSize = 18.sp) }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        Notifier.cancel(this@AlarmActivity, nid)
                        if (kind.isNotEmpty() && !kind.startsWith("test")) Scheduler.snooze(this@AlarmActivity, kind, pid)
                        finish()
                    }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("稍後 ${Scheduler.SNOOZE_MINUTES} 分鐘", color = Color.White, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        Notifier.cancel(this@AlarmActivity, nid)
                        val km = getSystemService(KeyguardManager::class.java)
                        val open = Intent(this@AlarmActivity, MainActivity::class.java)
                            .putExtra(Notifier.EXTRA_TARGET, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        if (km.isKeyguardLocked) km.requestDismissKeyguard(this@AlarmActivity, null)
                        startActivity(open); finish()
                    }) { Text("關閉並打開清單", color = Color(0xFFBFB6FF)) }
                }
            }
        }
    }
}
