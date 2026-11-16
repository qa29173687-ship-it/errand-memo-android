package tw.memo.errands

import android.app.Application
import tw.memo.errands.alarm.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannels(this)
    }
}
