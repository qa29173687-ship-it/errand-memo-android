package tw.memo.errands.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoDao {
    @Query("SELECT * FROM errands ORDER BY createdAt") fun errandsFlow(): Flow<List<Errand>>
    @Query("SELECT * FROM errands") suspend fun errands(): List<Errand>
    @Query("SELECT * FROM errands WHERE id = :id") suspend fun errand(id: Long): Errand?
    @Query("SELECT * FROM frequent ORDER BY sort, id") suspend fun frequent(): List<Frequent>
    @Insert suspend fun insert(e: Errand): Long
    @Update suspend fun update(e: Errand)
    @Delete suspend fun delete(e: Errand)
    @Query("SELECT * FROM errands WHERE purchaseId = :pid") suspend fun errandsForPurchase(pid: Long): List<Errand>
    @Query("DELETE FROM errands WHERE repeat = 'once' AND doneOn IS NOT NULL AND dueDate < :before")
    suspend fun purgeOldDone(before: String)

    @Query("SELECT * FROM purchases ORDER BY startDate, createdAt") fun purchasesFlow(): Flow<List<Purchase>>
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

@Database(entities = [Errand::class, Purchase::class, Frequent::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): MemoDao

    companion object {
        /** v1.0.1：採購提醒預設改成「前一天晚上」。v1.0.0 的「當天」(0，當時的預設) 改成 1；3/7 天保留（改在晚上提醒）。資料全部保留。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V1_TO_V2.forEach { db.execSQL(it) }
            }
        }
        /** v1.0.2：採購清單改成「特價期間／不設期限」，舊資料轉換不遺失。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V2_TO_V3.forEach { db.execSQL(it) }
            }
        }
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { inst = it }
        }
    }
}
