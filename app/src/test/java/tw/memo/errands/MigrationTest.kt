package tw.memo.errands

import org.junit.Assert.*
import org.junit.Test
import tw.memo.errands.data.MigrationSql
import java.sql.DriverManager

/** 在真的 SQLite 上跑 v1→v2→v3 的升級 SQL，確認舊採購資料轉換正確、不遺失。 */
class MigrationTest {
    private val v1Purchases = "CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
        "`note` TEXT NOT NULL, `date` TEXT NOT NULL, `remindDays` INTEGER NOT NULL, `mode` TEXT NOT NULL, `boughtOn` TEXT, `createdAt` INTEGER NOT NULL)"

    private fun db() = DriverManager.getConnection("jdbc:sqlite::memory:")

    @Test fun v2ToV3MapsDatesAndKeepsAllRows() {
        db().use { c ->
            val st = c.createStatement()
            st.execute(v1Purchases)
            st.execute("INSERT INTO purchases VALUES (1,'藍牙耳機','雙11','2026-11-20',1,'alarm',NULL,100)")
            st.execute("INSERT INTO purchases VALUES (2,'拖鞋','','2026-11-11',0,'notify','2026-11-12',200)")
            st.execute("INSERT INTO purchases VALUES (3,'收納盒','隨時','',3,'notify',NULL,300)")
            st.execute("INSERT INTO purchases VALUES (4,'充電線','',' 2026-12-12 ',7,'notify',NULL,400)")
            MigrationSql.V2_TO_V3.forEach { st.execute(it) }
            val rs = st.executeQuery("SELECT id,title,note,kind,startDate,endDate,timing,mode,boughtOn,createdAt FROM purchases ORDER BY id")
            val rows = mutableListOf<List<Any?>>()
            while (rs.next()) rows += (1..10).map { rs.getObject(it) }
            assertEquals(4, rows.size)
            assertEquals(listOf(1, "藍牙耳機", "雙11", "period", "2026-11-20", "2026-11-20", "night", "alarm", null, 100), rows[0])
            assertEquals(listOf(2, "拖鞋", "", "period", "2026-11-11", "2026-11-11", "morning", "notify", "2026-11-12", 200), rows[1])
            assertEquals(listOf(3, "收納盒", "隨時", "none", null, null, "night", "notify", null, 300), rows[2])
            assertEquals(listOf(4, "充電線", "", "period", "2026-12-12", "2026-12-12", "night", "notify", null, 400), rows[3])
            // 舊欄位已移除、id 自動遞增仍可用
            val cols = st.executeQuery("PRAGMA table_info(purchases)").let { r -> buildList { while (r.next()) add(r.getString("name")) } }
            assertFalse("date" in cols); assertFalse("remindDays" in cols)
            st.execute("INSERT INTO purchases (title,note,kind,timing,mode,createdAt) VALUES ('新','', 'none','night','notify',1)")
            assertEquals(5, st.executeQuery("SELECT max(id) FROM purchases").getInt(1))
        }
    }

    @Test fun fullChainV1ToV3() {
        db().use { c ->
            val st = c.createStatement()
            st.execute(v1Purchases)
            st.execute("INSERT INTO purchases VALUES (1,'耳機','','2026-11-20',0,'notify',NULL,1)")
            MigrationSql.V1_TO_V2.forEach { st.execute(it) }  // 0 → 1（前一晚）
            MigrationSql.V2_TO_V3.forEach { st.execute(it) }
            val rs = st.executeQuery("SELECT kind,startDate,endDate,timing FROM purchases")
            assertTrue(rs.next())
            assertEquals(listOf("period", "2026-11-20", "2026-11-20", "night"), (1..4).map { rs.getString(it) })
        }
    }
}
