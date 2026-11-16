package tw.memo.errands.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 開機、時間/時區變更、App 更新、精確鬧鐘權限變更後重新排程。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Scheduler.rescheduleAll(context.applicationContext) } finally { pending.finish() }
        }
    }
}
