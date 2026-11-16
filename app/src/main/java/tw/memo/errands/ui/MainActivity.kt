package tw.memo.errands.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tw.memo.errands.alarm.Notifier
import tw.memo.errands.alarm.Scheduler
import tw.memo.errands.data.AppDb
import tw.memo.errands.data.Prefs
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val target = mutableStateOf<String?>(null)
    private val resumeTick = mutableIntStateOf(0)
    val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target.value = intent?.getStringExtra(Notifier.EXTRA_TARGET)
        val prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.askedNotifPermission && !Notifier.canPost(this)) {
            prefs.askedNotifPermission = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MemoTheme {
                AppRoot(target.value, { target.value = null }, resumeTick.intValue,
                    requestNotif = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_TARGET)?.let { target.value = it }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        lifecycleScope.launch(Dispatchers.IO) {
            AppDb.get(this@MainActivity).dao().purgeOldDone(LocalDate.now().minusDays(30).toString())
            Scheduler.rescheduleAll(this@MainActivity)
        }
    }
}

@Composable
fun AppRoot(target: String?, consumeTarget: () -> Unit, resumeTick: Int, requestNotif: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showNight by rememberSaveable { mutableStateOf(false) }
    val today = remember(resumeTick) { LocalDate.now() }
    LaunchedEffect(target) {
        when (target) {
            Notifier.TARGET_NIGHT -> { tab = 0; showNight = true }
            Notifier.TARGET_PURCHASE -> { showNight = false; tab = 1 }
            Notifier.TARGET_TODAY -> { showNight = false; tab = 0 }
        }
        if (target != null) consumeTarget()
    }
    if (showNight) {
        BackHandler { showNight = false }
        NightScreen(today) { showNight = false }
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Checklist, null) }, label = { Text("下班順便做") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.ShoppingCart, null) }, label = { Text("採購清單") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, null) }, label = { Text("設定") })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(today, resumeTick, onOpenNight = { showNight = true }, onGoSettings = { tab = 2 })
                1 -> PurchaseScreen(today)
                else -> SettingsScreen(resumeTick, requestNotif)
            }
        }
    }
}
