# Lab 05：資料搬移

## 目標

- `EXPORT` 匯出資料 (DEL / IXF 格式)
- `IMPORT` 匯入資料 (走 SQL INSERT)
- `LOAD` 大量載入 (直接寫 page，速度快但有副作用)
- 處理 LOAD 後的 Set Integrity Pending
- `LOAD FROM CURSOR` 表對表搬資料
- `db2move` 整個 schema 一次匯出

前置條件：Lab 03 的 `SHOP` 物件。工作目錄用 instance home 底下的 `~/work`
(容器的 `/database` 屬於 root，`db2inst1` 無法在那裡建目錄)。

```bash
mkdir -p ~/work/exp && cd ~/work/exp
db2 connect to LABDB
```

## 1. EXPORT

```text
$ db2 "export to customer.del of del modified by coldel| select * from SHOP.CUSTOMER"
SQL3104N  The Export utility is beginning to export data to file "customer.del".
SQL3105N  The Export utility has finished exporting "4" rows.
Number of rows exported: 4

$ head -3 customer.del
1|"Alice"|"Taipei"|"2026-11-04-14.40.22.294817"
2|"Bob"|"Taichung"|"2026-11-04-14.40.22.294817"
3|"Carol"|"Kaohsiung"|"2026-11-04-14.40.22.294817"

$ db2 "export to orders.ixf of ixf messages exp_orders.msg select * from SHOP.ORDERS"
Number of rows exported: 20000
```

| 參數 | 說明 |
|---|---|
| `to <file>` | 輸出檔 |
| `of del` | 分隔文字檔 (delimited)，可給其他系統讀 |
| `of ixf` | DB2 專用二進位格式，含表格定義，DB2 之間搬資料最可靠 |
| `modified by coldel\|` | 欄位分隔符改成 `\|` (預設是逗號)。其他常用：`chardel''` 字串定界符、`nochardel` 不加引號 |
| `messages <file>` | 訊息寫到檔案而非螢幕，大量資料時方便事後檢查 |
| `select ...` | 匯出的內容可以是任意查詢 |

## 2. IMPORT

```text
$ db2 "create table SHOP.ORDERS_BAK like SHOP.ORDERS in TS_DATA"
$ db2 "import from orders.ixf of ixf commitcount 5000 messages imp.msg insert into SHOP.ORDERS_BAK"
Number of rows read         = 20000
Number of rows skipped      = 0
Number of rows inserted     = 20000
Number of rows updated      = 0
Number of rows rejected     = 0
Number of rows committed    = 20000
```

| 參數 | 說明 |
|---|---|
| `create table ... like ...` | 複製欄位結構 (不含索引、外鍵、資料) |
| `commitcount 5000` | 每 5000 筆 commit 一次，避免交易日誌塞滿 (`SQL0964C`)；也可用 `automatic` |
| `insert into` | 附加資料 |
| `insert_update into` | 主鍵存在則更新，否則新增 (需有主鍵) |
| `replace into` | 先清空再匯入 |
| `create into` | 表格不存在時依 IXF 內的定義建表 (僅 IXF) |

### 匯入 identity 欄位

`CUST_ID` 是 `GENERATED ALWAYS`，不能直接塞值進去，要讓 DB2 忽略檔案內的值、重新產生：

```text
$ db2 "import from customer.del of del modified by coldel| identityignore messages imp2.msg insert into SHOP.CUSTOMER"
Number of rows read         = 4
Number of rows inserted     = 4
```

| modifier | 說明 |
|---|---|
| `identityignore` | 忽略檔案中的 identity 值，由 DB2 產生新值 |
| `identityoverride` | 強制使用檔案中的值 (僅 LOAD 支援，搬遷資料時用) |

執行後 `SHOP.CUSTOMER` 變成 8 筆 (名字重複、ID 不同)。

## 3. LOAD

```text
$ db2 "create table SHOP.ORDERS_LOAD like SHOP.ORDERS in TS_DATA"
$ db2 "load from orders.ixf of ixf messages load.msg replace into SHOP.ORDERS_LOAD nonrecoverable"
Number of rows read         = 20000
Number of rows skipped      = 0
Number of rows loaded       = 20000
Number of rows rejected     = 0
Number of rows deleted      = 0
Number of rows committed    = 20000

$ db2 "load query table SHOP.ORDERS_LOAD"
Tablestate:
  Normal
```

IMPORT 與 LOAD 的差異：

| | IMPORT | LOAD |
|---|---|---|
| 寫入方式 | 一筆一筆 SQL INSERT | 直接格式化 page 寫入 |
| 速度 | 慢 | 快很多 |
| 交易日誌 | 每筆都寫 | 幾乎不寫 |
| 觸發器、約束檢查 | 會執行 | 不執行 trigger；外鍵與 check 約束要事後 `SET INTEGRITY` |
| 載入期間其他人能否存取 | 可以 | 預設鎖表 (`allow no access`)，可選 `allow read access` |

| 參數 | 說明 |
|---|---|
| `replace into` / `insert into` | 清空後載入 / 附加 |
| `nonrecoverable` | 不留可 rollforward 的副本。在封存日誌模式下若不加這個 (也不加 `copy yes`)，LOAD 後 tablespace 會進入 **Backup Pending** |
| `copy yes to <dir>` | 載入時另存一份副本，讓 rollforward 可以重做這次載入 |
| `load query table <t>` | 查看表格的 LOAD 狀態 |

## 4. LOAD 造成的 Set Integrity Pending

`SHOP.ORDERS` 有外鍵指向 `SHOP.CUSTOMER`，LOAD 不會檢查外鍵，所以載入後表格會被鎖成「待檢查」狀態：

```text
$ db2 "load from orders.ixf of ixf modified by identityignore messages load2.msg insert into SHOP.ORDERS nonrecoverable"
Number of rows committed    = 20000

$ db2 "select substr(tabname,1,10) tab, status, substr(const_checked,1,1) fk_chk from syscat.tables where tabschema='SHOP' and tabname='ORDERS'"
TAB        STATUS FK_CHK
---------- ------ ------
ORDERS     C      N

$ db2 "select count(*) from SHOP.ORDERS"
SQL0668N  Operation not allowed for reason code "1" on table "SHOP.ORDERS".
SQLSTATE=57007
```

| 欄位 / 訊息 | 意義 |
|---|---|
| `STATUS = C` | Check pending (Set Integrity Pending)；`N` = 正常 |
| `CONST_CHECKED` 第 1 碼 = `N` | 外鍵尚未檢查 |
| `SQL0668N` reason code 1 | 表格在 Set Integrity Pending 狀態，無法存取 |

解除：

```text
$ db2 "set integrity for SHOP.ORDERS immediate checked"
DB20000I  The SQL command completed successfully.

$ db2 "select substr(tabname,1,10) tab, status from syscat.tables where tabname='ORDERS'"
TAB        STATUS
---------- ------
ORDERS     N

$ db2 "select count(*) from SHOP.ORDERS"
      40000
```

| 語法 | 說明 |
|---|---|
| `set integrity for <t> immediate checked` | 檢查所有約束；有違反的資料時會失敗 |
| `... immediate checked for exception in <t> use <exc_t>` | 違反約束的資料搬到例外表，其餘正常放行 |
| `... immediate unchecked` | 不檢查直接放行 (確定資料沒問題才用) |

找出所有 pending 的表格：

```bash
db2 "select tabschema, tabname from syscat.tables where status = 'C'"
```

## 5. LOAD FROM CURSOR

不用落地檔案，直接把查詢結果載入另一張表：

```text
$ db2 "declare c1 cursor for select * from SHOP.ORDERS where order_date > current date - 30 days"
$ db2 "load from c1 of cursor replace into SHOP.ORDERS_LOAD nonrecoverable"
Number of rows committed    = 3298
```

`declare` 和 `load` 必須在同一個 CLP 連線中執行。

## 6. db2move：整批匯出

```text
$ mkdir -p ~/work/move && cd ~/work/move
$ db2move LABDB export -sn SHOP
EXPORT:  20000 rows from table "SHOP    "."ORDERS_BAK"
EXPORT:   3298 rows from table "SHOP    "."ORDERS_LOAD"
...
$ ls
EXPORT.out  db2move.lst  tab1.ixf  tab1.msg  tab2.ixf  tab2.msg  tab3.ixf  tab3.msg  tab4.ixf  tab4.msg

$ head db2move.lst
!"SHOP    "."CUSTOMER"!tab1.ixf!tab1.msg!
!"SHOP    "."ORDERS"!tab2.ixf!tab2.msg!
!"SHOP    "."ORDERS_BAK"!tab3.ixf!tab3.msg!
!"SHOP    "."ORDERS_LOAD"!tab4.ixf!tab4.msg!
```

| 用法 | 說明 |
|---|---|
| `db2move <db> export` | 匯出所有使用者表格 |
| `-sn <schema>` | 只匯出指定 schema |
| `-tn <t1,t2>` | 只匯出指定表格 |
| `db2move <db> import` / `load` | 在目的端依 `db2move.lst` 匯入 |
| `db2move <src> copy -sn <s> -co target_db <tgt> user <u> using <pw>` | 直接複製到另一個資料庫 (含 DDL) |

`db2move.lst` 記錄表格與檔案的對應，目的端匯入時要和 `.ixf` 放在同一目錄。
完整搬遷一般搭配 `db2look` 先建結構 (Lab 03)。

## 小結

| 指令 | 用途 |
|---|---|
| `export to <f> of del/ixf select ...` | 匯出 |
| `import from <f> of ... insert/replace/insert_update into <t>` | 小量、需觸發 trigger 時用 |
| `load from <f> of ... insert/replace into <t>` | 大量快速載入 |
| `load query table <t>` | 查 LOAD 狀態 |
| `set integrity for <t> immediate checked` | 解除 Set Integrity Pending |
| `declare ... cursor` + `load from <c> of cursor` | 表對表載入 |
| `db2move` | 整批匯出 / 匯入 |
