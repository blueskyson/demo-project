# Lab 06：備份與還原

## 目標

- 離線備份 (circular logging 下唯一的選擇)
- 改成封存日誌 (archive logging)，處理 Backup Pending
- 線上備份
- 模擬誤刪表格，用 RESTORE + ROLLFORWARD 做時間點還原 (PIT recovery)
- 把備份還原成另一個資料庫 (複製環境)

前置條件：Lab 05 完成。備份與封存日誌放在 instance home 底下：

```bash
mkdir -p ~/backup ~/archlog
```

> 下面輸出中的時間戳 (`20261104144217` 等) 每次執行都不同，請換成自己的值。

## 1. 離線備份

```text
$ db2 force applications all
DB20000I  The FORCE APPLICATION command completed successfully.
DB21024I  This command is asynchronous and may not be effective immediately.

$ db2 deactivate db LABDB
DB20000I  The DEACTIVATE DATABASE command completed successfully.

$ db2 backup db LABDB to ~/backup compress
Backup successful. The timestamp for this backup image is : 20261104144217
```

| 指令 / 參數 | 說明 |
|---|---|
| `force applications all` | 中斷所有連線 (非同步，可能要等一下)。也可 `force application (<handle>)` 只踢單一連線 |
| `deactivate db` | 關閉資料庫，離線備份要求沒有任何連線 |
| `backup db <db> to <dir>` | 備份到目錄，檔名格式：`<DB>.0.<instance>.DBPART000.<時間戳>.001` |
| `compress` | 壓縮備份 |
| (選用) `parallelism <n>` / `buffer <size>` | 調整備份效能 |

**時間戳就是備份的 ID**，還原時要用它指定哪一份。

## 2. 改成封存日誌 (Archive Logging)

預設 `LOGARCHMETH1 = OFF` 是循環日誌：日誌會被覆蓋重用，只能還原到備份當下，且不能線上備份。
改成封存日誌後，填滿的日誌檔會被搬到封存位置，才能做線上備份與 rollforward。

```text
$ db2 update db cfg for LABDB using LOGARCHMETH1 DISK:/database/config/db2inst1/archlog
DB20000I  The UPDATE DATABASE CONFIGURATION command completed successfully.

$ db2 connect to LABDB
SQL1116N  A connection to or activation of database "LABDB" failed because the
database is in BACKUP PENDING state.  SQLSTATE=57019

$ db2 get db cfg for LABDB | grep -iE 'backup pending|LOGARCHMETH1'
 Backup pending                                          = YES
 First log archive method                 (LOGARCHMETH1) = DISK:/database/config/db2inst1/archlog/
```

| `LOGARCHMETH1` 值 | 說明 |
|---|---|
| `OFF` | 循環日誌 |
| `LOGRETAIN` | 保留日誌在原處，不自動搬走 |
| `DISK:<路徑>` | 封存到磁碟目錄 |
| `TSM` / `VENDOR:<lib>` | 封存到 TSM 或第三方備份軟體 |

從循環改成封存日誌後，資料庫會進入 **Backup Pending**，必須先做一次完整的離線備份，作為日後 rollforward 的起點：

```text
$ db2 backup db LABDB to ~/backup compress
Backup successful. The timestamp for this backup image is : 20261104144224

$ db2 connect to LABDB
(連線成功)
```

## 3. 線上備份

```text
$ db2 backup db LABDB online to ~/backup compress include logs
Backup successful. The timestamp for this backup image is : 20261104144227

$ ls -l ~/backup
-rw------- 1 db2inst1 db2iadm1 100720640 Nov  4 14:42 LABDB.0.db2inst1.DBPART000.20261104144217.001
-rw------- 1 db2inst1 db2iadm1 100720640 Nov  4 14:42 LABDB.0.db2inst1.DBPART000.20261104144224.001
-rw------- 1 db2inst1 db2iadm1  75296768 Nov  4 14:42 LABDB.0.db2inst1.DBPART000.20261104144227.001
```

| 參數 | 說明 |
|---|---|
| `online` | 備份時資料庫照常服務 (需封存日誌) |
| `include logs` | 把備份期間的日誌一起放進備份檔，讓這份備份能獨立還原到一致狀態 (線上備份預設就會包含) |
| `incremental` / `incremental delta` | 增量 / 差異備份 (需先設 `TRACKMOD ON`) |
| `tablespace (<ts>,...)` | 只備份指定 tablespace |

## 4. 檢查備份

### 備份歷史：`list history`

```text
$ db2 list history backup all for LABDB
 Op Obj Timestamp+Sequence Type Dev Earliest Log Current Log  Backup ID
 Start Time: 20261104144217
  EID: 8 Location: /database/config/db2inst1/backup
 ...
 Start Time: 20261104144227
  EID: 12 Location: /database/config/db2inst1/backup
```

`list history` 讀的是資料庫的 recovery history file，可用來查哪些備份可用、存在哪裡。
其他用法：`list history all for <db>`、`list history since <時間戳> for <db>`、
`prune history <時間戳> and delete` (清理舊紀錄並刪除封存日誌)。

### 驗證備份檔：`db2ckbkp`

```text
$ db2ckbkp ~/backup/LABDB.0.db2inst1.DBPART000.20261104144227.001
Image Verification Complete - successful.
```

備份檔損壞要在還原前發現，正式環境建議定期跑。`db2ckbkp -h <file>` 只看檔頭資訊。

## 5. 模擬事故：誤刪表格

```text
$ db2 connect to LABDB
$ db2 "insert into SHOP.CUSTOMER (name, city) values ('Eve','Tainan')"
$ db2 commit
$ db2 -x "values varchar_format(current timestamp, 'YYYY-MM-DD-HH24.MI.SS')" > ~/work/pit_time.txt
$ cat ~/work/pit_time.txt
2026-11-04-14.42.38

$ db2 "drop table SHOP.ORDERS_BAK"      # 事故發生
$ db2 connect reset
```

目標：還原到 `14.42.38`，要拿回 `ORDERS_BAK`，同時保留在那之前新增的 `Eve`。

`db2 -x` 表示不印欄位標題，方便把結果存進檔案或變數。

### 強制封存目前的日誌：`archive log`

```text
$ db2 archive log for db LABDB
DB20000I  The ARCHIVE LOG command completed successfully.

$ find ~/archlog -name '*.LOG'
.../archlog/db2inst1/LABDB/NODE0000/LOGSTREAM0000/C0000000/S0000000.LOG
.../archlog/db2inst1/LABDB/NODE0000/LOGSTREAM0000/C0000000/S0000001.LOG
...
```

把正在使用的日誌檔截斷並封存，確保最新的交易都在封存目錄中。
**必須在沒有連線的狀態下執行**，否則會出現：

```text
SQL1493N  The command failed because the application is already connected to an active database.
```

## 6. RESTORE + ROLLFORWARD

```text
$ db2 force applications all
$ db2 deactivate db LABDB
$ db2 restore db LABDB from ~/backup taken at 20261104144227 without prompting
SQL2540W  Restore is successful, however a warning "2539" was encountered
during Database Restore while processing in No Interrupt mode.
```

| 參數 | 說明 |
|---|---|
| `from <dir>` | 備份檔所在目錄 |
| `taken at <時間戳>` | 指定哪一份備份 |
| `without prompting` | 不互動詢問 |
| `into <新名稱>` | 還原成另一個資料庫 (見第 7 節) |
| `without rolling forward` | 還原後直接可用，不需 rollforward (只適用離線備份) |

`SQL2540W` 的 warning `2539` 是提醒「要覆蓋一個已存在的資料庫」，因為加了 `without prompting` 所以自動繼續。這是預期內的。

還原線上備份後，資料庫會進入 **Rollforward Pending**：

```text
$ db2 connect to LABDB
SQL1117N  A connection to or activation of database "LABDB" cannot be made
because of ROLL-FORWARD PENDING.  SQLSTATE=57019
```

套用日誌到指定時間點：

```text
$ db2 "rollforward db LABDB to 2026-11-04-14.42.38 using local time and stop"
                                 Rollforward Status
 Rollforward status                     = not pending
 Log files processed                    = S0000000.LOG - S0000003.LOG
 Last committed transaction             = 2026-11-04-14.42.38.000000 Local
DB20000I  The ROLLFORWARD command completed successfully.
```

| 參數 | 說明 |
|---|---|
| `to <時間戳>` | 還原到指定時間點，格式 `YYYY-MM-DD-HH.MM.SS` |
| `to end of logs` | 套用所有日誌 (還原到最新，例如硬碟壞掉時) |
| `to end of backup` | 只套用到備份結束時的一致狀態 |
| `using local time` | 時間戳以本地時間解讀 (預設是 UTC，容易搞錯) |
| `and stop` (= `and complete`) | 套完日誌後結束 rollforward，資料庫回到可用狀態 |
| `query status` | `db2 rollforward db <db> query status` 只查目前狀態 |

驗證：

```text
$ db2 connect to LABDB
$ db2 list tables for schema SHOP
CUSTOMER  ORDERS  ORDERS_BAK  ORDERS_LOAD  V_CUST_TOTAL     ← ORDERS_BAK 回來了

$ db2 "select name, city from SHOP.CUSTOMER where name='Eve'"
Eve     Tainan                                               ← 事故前的資料也保留
```

> 時間點還原會讓整個資料庫回到該時間，**之後的所有交易都會消失**。
> 正式環境只想救回單一表格時，通常改成：還原到另一個資料庫 (第 7 節) 再把資料搬回來。

## 7. 還原成另一個資料庫

```text
$ db2 restore db LABDB from ~/backup taken at 20261104144224 into LABCOPY without rolling forward without prompting
DB20000I  The RESTORE DATABASE command completed successfully.

$ db2 connect to LABCOPY
$ db2 -x "select count(*) from SHOP.ORDERS"
      40000
$ db2 connect reset
$ db2 drop db LABCOPY
DB20000I  The DROP DATABASE command completed successfully.
```

`20261104144224` 是離線備份，所以可以 `without rolling forward` 直接使用。
常見用途：建立測試環境、從備份中撈回誤刪的資料。

## 小結

| 指令 | 用途 |
|---|---|
| `force applications all` | 中斷所有連線 |
| `backup db <db> to <dir> [online] [compress]` | 備份 |
| `update db cfg using LOGARCHMETH1 DISK:<dir>` | 開啟封存日誌 |
| `list history backup all for <db>` | 查備份歷史 |
| `db2ckbkp <file>` | 驗證備份檔 |
| `archive log for db <db>` | 強制封存目前日誌 |
| `restore db <db> from <dir> taken at <ts> [into <new>]` | 還原 |
| `rollforward db <db> to <ts\|end of logs> using local time and stop` | 套用日誌 |

| 狀態 | 原因 | 解法 |
|---|---|---|
| Backup Pending (`SQL1116N`) | 從循環改成封存日誌 | 做一次離線完整備份 |
| Rollforward Pending (`SQL1117N`) | 還原了線上備份或沒加 `without rolling forward` | `rollforward ... and stop` |
