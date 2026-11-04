# Lab 02：Instance 與資料庫管理

## 目標

- 啟動、停止 instance
- 建立資料庫並查看目錄 (directory)
- 啟用 (activate) 資料庫、連線
- 查看與修改 instance 設定 (DBM CFG)、資料庫設定 (DB CFG)、登錄變數 (db2set)

以下指令都在 `docker exec -it db2lab su - db2inst1` 的 shell 內執行。

## 1. 停止 / 啟動 instance

```text
$ db2stop
SQL1064N  DB2STOP processing was successful.
$ db2start
SQL1063N  DB2START processing was successful.
```

| 指令 | 說明 |
|---|---|
| `db2stop` | 停止 instance。若還有應用程式連線，會回 `SQL1025N`，要先斷線或改用 `db2stop force` |
| `db2stop force` | 強制中斷所有連線後停止 (未 commit 的交易會 rollback) |
| `db2start` | 啟動 instance |

修改部分 DBM CFG 參數後，需要 `db2stop` + `db2start` 才會生效。

## 2. 建立資料庫

```text
$ db2 create database LABDB using codeset UTF-8 territory TW pagesize 8 K
DB20000I  The CREATE DATABASE command completed successfully.
```

| 參數 | 說明 |
|---|---|
| `LABDB` | 資料庫名稱，最多 8 個字元 |
| `using codeset UTF-8 territory TW` | 字元集與地區。字元集建庫後**無法修改**，要存中文請用 UTF-8 |
| `pagesize 8 K` | 預設 page size (4/8/16/32 K)，影響系統 tablespace 與預設 bufferpool |

建庫需要一些時間 (會建立系統目錄表與預設 tablespace)。未指定路徑時，資料放在 dbm cfg `DFTDBPATH` 指定的位置 (此容器為 `/database/data`)。

## 3. 查看目錄

### 系統資料庫目錄：`list db directory`

```text
$ db2 list db directory
 System Database Directory
 Number of entries in the directory = 1
Database 1 entry:
 Database alias                       = LABDB
 Database name                        = LABDB
 Local database directory             = /database/data
 Directory entry type                 = Indirect
```

- 記錄這個 instance「知道」哪些資料庫 (alias → 實際資料庫)
- `Indirect` 表示是本機的資料庫；Lab 04 會看到遠端資料庫是 `Remote`

### 節點目錄：`list node directory`

```text
$ db2 list node directory
SQL1027N  The node directory cannot be found.
```

節點目錄記錄遠端 DB2 server 的位址。還沒 catalog 過任何遠端節點時，會出現 `SQL1027N`，這是正常的 (Lab 04 會建立)。

## 4. 啟用資料庫與連線

```text
$ db2 activate db LABDB
DB20000I  The ACTIVATE DATABASE command completed successfully.

$ db2 list active databases
Database name                              = LABDB
Applications connected currently           = 0
Database path                              = /database/data/db2inst1/NODE0000/SQL00001/MEMBER0000/
```

| 指令 | 說明 |
|---|---|
| `db2 activate db <db>` | 預先啟用資料庫 (配置 bufferpool、記憶體)。沒 activate 的話，第一個連線會觸發啟用，最後一個連線斷開時又會關閉，反覆啟停很耗資源 |
| `db2 deactivate db <db>` | 關閉資料庫 (需沒有連線)，部分 DB CFG 修改後要靠它重新生效 |
| `db2 list active databases` | 列出已啟用的資料庫與連線數 |

```text
$ db2 connect to LABDB
   Database Connection Information
 Database server        = DB2/LINUXX8664 12.1.5.0
 SQL authorization ID   = DB2INST1
 Local database alias   = LABDB

$ db2 "values current server, current user, current schema"
LABDB
DB2INST1
DB2INST1

$ db2 connect reset
DB20000I  The SQL command completed successfully.
```

| 指令 | 說明 |
|---|---|
| `db2 connect to <db>` | 用目前 OS 使用者連線 (本機連線免密碼) |
| `db2 connect to <db> user <u> using <pw>` | 以指定使用者連線 |
| `db2 connect reset` | 斷開連線 |
| `db2 terminate` | 斷開連線並結束 CLP 背景程序 (修改目錄後要用它刷新快取) |

> **CLP 小技巧**：SQL 一定要用雙引號包起來，否則 shell 會把 `*`、`(` 等字元當成自己的語法。
> 執行 SQL 檔用 `db2 -tvf file.sql` (`-t` 以分號結尾，`-v` 顯示指令，`-f` 讀檔)。

## 5. Instance 設定：DBM CFG

```text
$ db2 get dbm cfg | grep -E 'INSTANCE_MEMORY|DIAGLEVEL|DIAGPATH|SVCENAME|NUMDB|SYSADM_GROUP'
 Max number of concurrently active databases     (NUMDB) = 32
 Diagnostic error capture level              (DIAGLEVEL) = 3
 Diagnostic data directory path               (DIAGPATH) = /database/config/db2inst1/sqllib/db2dump/ $m
 SYSADM group name                        (SYSADM_GROUP) = DB2IADM1
 Global instance memory (% or 4KB)     (INSTANCE_MEMORY) = AUTOMATIC(2097152)
 TCP/IP Service name                          (SVCENAME) = db2c_db2inst1
```

| 參數 | 說明 |
|---|---|
| `NUMDB` | 同時可啟用的資料庫數量上限 |
| `DIAGLEVEL` | db2diag.log 記錄等級 (0~4，預設 3 = 錯誤+警告) |
| `DIAGPATH` | db2diag.log 的位置，排錯時第一個要看的地方 |
| `SYSADM_GROUP` | 擁有 SYSADM 權限的 OS 群組 |
| `INSTANCE_MEMORY` | instance 可用記憶體上限 (單位 4KB page) |

修改設定：

```text
$ db2 update dbm cfg using DIAGLEVEL 3
DB20000I  The UPDATE DATABASE MANAGER CONFIGURATION command completed successfully.
```

語法：`db2 update dbm cfg using <參數> <值> [<參數> <值> ...]`

## 6. 資料庫設定：DB CFG

```text
$ db2 get db cfg for LABDB | grep -E 'Database page size|LOGARCHMETH1|LOGFILSIZ|LOGPRIMARY|LOGSECOND|Path to log files|AUTO_RUNSTATS|SELF_TUNING_MEM|code set|territory'
 Database territory                                      = TW
 Database code set                                       = UTF-8
 Database page size                                      = 8192
 Self tuning memory                    (SELF_TUNING_MEM) = ON
 Log file size (4KB)                         (LOGFILSIZ) = 1024
 Number of primary log files                (LOGPRIMARY) = 13
 Number of secondary log files               (LOGSECOND) = 12
 Path to log files                                       = /database/data/db2inst1/NODE0000/SQL00001/LOGSTREAM0000/
 First log archive method                 (LOGARCHMETH1) = OFF
     Automatic runstats                  (AUTO_RUNSTATS) = ON
```

| 參數 | 說明 |
|---|---|
| `LOGFILSIZ` | 每個交易日誌檔的大小 (單位 4KB)，1024 = 4 MB |
| `LOGPRIMARY` / `LOGSECOND` | 主要 / 次要日誌檔數量。大交易塞滿日誌會出現 `SQL0964C` (transaction log full) |
| `LOGARCHMETH1` | `OFF` = 循環日誌 (circular logging)，只能做離線備份、無法 rollforward。Lab 06 會改成封存日誌 |
| `SELF_TUNING_MEM` | STMM，自動調整 bufferpool、sort heap 等記憶體 |
| `AUTO_RUNSTATS` | 自動收集統計資訊 |

修改設定：

```text
$ db2 update db cfg for LABDB using LOGFILSIZ 4096 LOGPRIMARY 5
DB20000I  The UPDATE DATABASE CONFIGURATION command completed successfully.
SQL1363W  One or more of the parameters submitted for immediate modification
were not changed dynamically. For these configuration parameters, the database
must be shutdown and reactivated before the configuration parameter changes
become effective.
```

`SQL1363W` 表示這些參數無法線上生效，要 `deactivate` 再 `activate` 資料庫 (或所有連線斷開) 後才會套用。

想同時看「目前生效值」與「下次啟用後的值」，用 `show detail`，但必須先連線：

```bash
db2 connect to LABDB
db2 get db cfg for LABDB show detail | grep -E 'LOGFILSIZ|LOGPRIMARY'
```

## 7. 登錄變數：`db2set`

```text
$ db2set -all
[i] DB2COMM=TCPIP
[g] DB2SYSTEM=db2lab

$ db2set DB2COMM
TCPIP
```

| 指令 | 說明 |
|---|---|
| `db2set -all` | 列出所有已設定的登錄變數。`[i]` = instance 層級，`[g]` = 全域層級 |
| `db2set <變數>` | 查詢單一變數 |
| `db2set <變數>=<值>` | 設定變數，多數需要重啟 instance 才生效 |
| `db2set <變數>=` | 刪除設定 |

`DB2COMM=TCPIP` 讓 instance 監聽 TCP/IP 連線，沒有設定的話遠端無法連進來。

## 小結

| 層級 | 查詢 | 修改 | 生效方式 |
|---|---|---|---|
| 登錄變數 | `db2set -all` | `db2set X=Y` | 通常需 `db2stop/db2start` |
| Instance | `db2 get dbm cfg` | `db2 update dbm cfg using ...` | 部分線上生效，部分需重啟 instance |
| 資料庫 | `db2 get db cfg for <db>` | `db2 update db cfg for <db> using ...` | 部分線上生效，`SQL1363W` 時需重新啟用資料庫 |
