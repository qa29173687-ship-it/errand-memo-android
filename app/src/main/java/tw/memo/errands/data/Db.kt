package tw.memo.errands.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM errands ORDER BY createdAt") fun errandsFlow(): Flow<List<Errand>>
    @Query("SELECT * FROM errands") suspend fun errands(): List<Errand>
    @Insert suspend fun insert(e: Errand): Long
    @Update suspend fun update(e: Errand)
    @Delete suspend fun delete(e: Errand)
    @Query("SELECT * FROM errands WHERE purchaseId = :pid") suspend fun errandsForPurchase(pid: Long): List<Errand>
    @Query("DELETE FROM errands WHERE repeat = 'once' AND doneOn IS NOT NULL AND dueDate < :before")
    suspend fun purgeOldDone(before: String)

    @Query("SELECT * FROM purchases ORDER BY date, createdAt") fun purchasesFlow(): Flow<List<Purchase>>
    @Query("SELECT * FROM purchases") suspend fun purchases(): List<Purchase>
    @Query("SELECT * FROM purchases WHERE id = :id") suspend fun purchase(id: Long): Purchase?
    @Insert suspend fun insert(p: Purchase): Long
    @Update suspend fun update(p: Purchase)
    @Delete suspend fun delete(p: Purchase)
    @Query("DELETE FROM purchases WHERE boughtOn IS NOT NULL") suspend fun clearBought()

    @Query("SELECT * FROM frequent ORDER BY sort, id") fun frequentFlow(): Flow<List<Frequent>>
    @Insert suspend fun insert(f: Frequent): Long
    @Delete suspend fun delete(f: Frequent)
}

@Database(entities = [Errand::class, Purchase::class, Frequent::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MemoDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "memo.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        listOf("拿包裹" to "take", "買牛奶" to "buy", "拿乾洗衣服" to "take", "買晚餐" to "buy")
                            .forEachIndexed { i, (t, ty) ->
                                db.execSQL("INSERT INTO frequent (title, type, sort) VALUES (?, ?, ?)", arrayOf<Any>(t, ty, i))
                            }
                    }
                })
                .build().also { inst = it }
        }
    }
}
