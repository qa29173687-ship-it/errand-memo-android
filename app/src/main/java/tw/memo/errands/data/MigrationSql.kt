package tw.memo.errands.data

/** 資料庫升級 SQL（純字串，JVM 單元測試會實際在 SQLite 上執行驗證）。 */
object MigrationSql {
    /** v1.0.1：採購提醒預設改成「前一天晚上」。 */
    val V1_TO_V2 = listOf("UPDATE purchases SET remindDays = 1 WHERE remindDays = 0")

    /** v1.0.2：單一日期 → 特價期間（開始=結束=原日期）；沒有日期 → 不設期限。
     *  remindDays 0（當天）→ 當天早上；其他（前一晚/前3天/前7天）→ 前一晚。其餘欄位原樣保留。 */
    val V2_TO_V3 = listOf(
        "CREATE TABLE IF NOT EXISTS `purchases_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
            "`note` TEXT NOT NULL, `kind` TEXT NOT NULL, `startDate` TEXT, `endDate` TEXT, `timing` TEXT NOT NULL, " +
            "`mode` TEXT NOT NULL, `boughtOn` TEXT, `createdAt` INTEGER NOT NULL)",
        "INSERT INTO `purchases_new` (id, title, note, kind, startDate, endDate, timing, mode, boughtOn, createdAt) " +
            "SELECT id, title, note, " +
            "CASE WHEN date IS NULL OR trim(date) = '' THEN 'none' ELSE 'period' END, " +
            "CASE WHEN date IS NULL OR trim(date) = '' THEN NULL ELSE trim(date) END, " +
            "CASE WHEN date IS NULL OR trim(date) = '' THEN NULL ELSE trim(date) END, " +
            "CASE WHEN remindDays = 0 THEN 'morning' ELSE 'night' END, " +
            "mode, boughtOn, createdAt FROM `purchases`",
        "DROP TABLE `purchases`",
        "ALTER TABLE `purchases_new` RENAME TO `purchases`",
    )
}
