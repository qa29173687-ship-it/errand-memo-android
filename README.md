# 順便備忘（Errand Memo）

下班順便做 + 晚上確認 + 採購清單的 Android App。Kotlin + Jetpack Compose + Room，資料只存在手機，不需帳號。

- 下班順便做：今天要拿/買的事，可設今天、明天、指定日期或重複（每天/指定星期），晚上 6 點後新增預設明天；常用項目一鍵加入（可編輯）。
- 下班提醒（預設 17:50、週一至週五、鬧鐘）：列出今天還沒做的事；通知或鬧鐘（響到按「關閉」，可「稍後 10 分鐘」）。
- 晚上確認（預設 22:00）：沒做完的問要不要延到明天（延到明天/已完成/刪除、全部延到明天），並列出明天下班要做。
- 採購清單：自選日期、當天/前 1/3/7 天提醒（預設 09:00），通知或鬧鐘；過期可重新選日期；「加入下班要做」會建立連結的「買」項目，任一邊打勾都會變已買。

Build: `JAVA_HOME=... ANDROID_HOME=... ./gradlew test assembleRelease`。
Release signing reads `/home/box/keys/errand-memo.properties` (outside the repo, override with `ERRAND_SIGNING`); the keystore is never committed.
