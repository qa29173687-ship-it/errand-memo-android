package tw.memo.errands.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object Repeat { const val ONCE = "once"; const val DAILY = "daily"; const val WEEKLY = "weekly" }
object ItemType { const val TAKE = "take"; const val BUY = "buy" }
object Mode { const val NOTIFY = "notify"; const val ALARM = "alarm" }

/** 下班順便做的一項。repeat=once 時 dueDate 為 yyyy-MM-dd；重複項目 dueDate=null。
 *  doneOn：一次性項目只要非 null 就算完成；重複項目 doneOn==當天 才算當天完成。 */
@Entity(tableName = "errands")
data class Errand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String = ItemType.TAKE,
    val note: String = "",
    val repeat: String = Repeat.ONCE,
    val dueDate: String? = null,
    val weekdays: String = "",
    val doneOn: String? = null,
    val createdAt: Long = 0,
    /** 從採購清單加入時連結的採購項目 id；任一邊打勾都會讓採購變成已買。 */
    val purchaseId: Long? = null,
)

object PKind { const val PERIOD = "period"; const val NONE = "none" }
object Timing { const val NIGHT = "night"; const val MORNING = "morning" }

/** 採購清單（v3）。kind=period：特價期間 startDate..endDate（yyyy-MM-dd）；kind=none：不設期限，不提醒。
 *  timing：night = 開始/結束前一晚（晚上確認時間）提醒；morning = 開始/結束當天早上（採購提醒時間）。 */
@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val kind: String = PKind.PERIOD,
    val startDate: String? = null,
    val endDate: String? = null,
    val timing: String = Timing.NIGHT,
    val mode: String = Mode.NOTIFY,
    val boughtOn: String? = null,
    val createdAt: Long = 0,
)

/** 常用項目（一鍵加入）。 */
@Entity(tableName = "frequent")
data class Frequent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val type: String = ItemType.TAKE,
    val sort: Int = 0,
)
