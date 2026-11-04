# Lab 03：資料庫物件

## 目標

- 建立 bufferpool、tablespace、schema、table、index、view
- 查看 tablespace 使用量與容器 (container)
- 從系統目錄 (SYSCAT) 查詢物件資訊
- 用 `db2look` 匯出 DDL

前置條件：Lab 02 已建立 `LABDB`。

## 1. 執行建物件腳本

```bash
db2 -tvf /labs/03-objects/create_objects.sql
```

| 參數 | 說明 |
|---|---|
| `-t` | 以分號 `;` 作為敘述結尾 |
| `-v` | 執行前先印出敘述 (方便對照輸出) |
| `-f <file>` | 從檔案讀取 |
| (選用) `-s` | 遇到錯誤就停止 |
| (選用) `-z <log>` | 同時把輸出寫進 log 檔 |

[create_objects.sql](create_objects.sql) 內容說明：

| 敘述 | 說明 |
|---|---|
| `CREATE BUFFERPOOL BP8K SIZE 2000 PAGESIZE 8K` | 建立 8K 的 bufferpool (2000 pages ≈ 16 MB)。tablespace 的 page size 必須和 bufferpool 一致 |
| `CREATE TABLESPACE TS_DATA ... MANAGED BY AUTOMATIC STORAGE BUFFERPOOL BP8K` | 自動儲存 tablespace：DB2 自己決定容器檔案位置並自動擴充 |
| `CREATE SCHEMA SHOP` | 建立 schema (物件的命名空間) |
| `CREATE TABLE ... IN TS_DATA INDEX IN TS_IDX` | 資料放 `TS_DATA`、索引放 `TS_IDX`，分開放方便管理與調整 |
| `GENERATED ALWAYS AS IDENTITY` | 自動編號欄位 |
| `FOREIGN KEY ... REFERENCES` | 外鍵，Lab 05 的 LOAD 會因它進入 Set Integrity Pending |
| `CREATE INDEX` / `CREATE VIEW` | 建立索引與檢視 |
| `WITH GEN(N) AS (... UNION ALL ...)` | 遞迴 CTE，產生 20000 筆訂單測試資料 |

結果：`SHOP.CUSTOMER` 4 筆、`SHOP.ORDERS` 20000 筆。

```text
$ db2 "select * from SHOP.V_CUST_TOTAL"
CUST_ID     NAME    ORDER_CNT   TOTAL
----------- ------- ----------- -----------
          1 Alice          5000  2492500.00
          2 Bob            5000  2497500.00
          3 Carol          5000  2502500.00
          4 Dave           5000  2507500.00
```

## 2. Tablespace

### `list tablespaces show detail`

```text
$ db2 connect to LABDB
$ db2 list tablespaces show detail
 Tablespace ID                        = 0
 Name                                 = SYSCATSPACE
 Type                                 = Database managed space
 State                                = 0x0000
   Detailed explanation:
     Normal
 Total pages                          = 20480
 Used pages                           = 17652
 Free pages                           = 2824
 High water mark (pages)              = 17652
 Page size (bytes)                    = 8192
 ...
```

| 欄位 | 說明 |
|---|---|
| `Type` | DMS (Database managed space) 或 SMS (System managed space)。自動儲存的 regular tablespace 為 DMS |
| `State` | `0x0000` = Normal。非 0 時要注意，例如 `0x0020` = Backup pending、`0x0080` = Rollforward pending |
| `High water mark` | 曾用到的最高 page，決定 tablespace 能縮小到多少 |

> 此指令在新版 DB2 已被標為 deprecated，正式環境建議改用下面的 `MON_GET_TABLESPACE` 或 `SYSIBMADM.TBSP_UTILIZATION`。

### 用 SQL 看使用率

```text
$ db2 "select substr(tbsp_name,1,12) tbsp, tbsp_type, tbsp_page_size, tbsp_used_pages, tbsp_utilization_percent util_pct from sysibmadm.tbsp_utilization order by 1"
TBSP         TBSP_TYPE  TBSP_PAGE_SIZE TBSP_USED_PAGES      UTIL_PCT
------------ ---------- -------------- -------------------- --------
SYSCATSPACE  DMS                  8192                17652    86.20
TEMPSPACE1   SMS                  8192                    0    -1.00
TS_DATA      DMS                  8192                  288     7.08
TS_IDX       DMS                  8192                  288     7.08
USERSPACE1   DMS                  8192                   96     2.36
```

> 注意：`tbsp_utilization_percent` 是 `SYSIBMADM.TBSP_UTILIZATION` 管理視圖的欄位，
> 表函數 `MON_GET_TABLESPACE` 沒有這個欄位 (會回 `SQL0206N`)。

`MON_GET_TABLESPACE(<名稱|null>, -2)` 的第二個參數 `-2` 代表「所有 member」，單機環境也這樣寫：

```text
$ db2 "alter tablespace TS_DATA reduce max"
DB20000I  The SQL command completed successfully.
$ db2 "select tbsp_auto_resize_enabled, tbsp_increase_size_percent from table(mon_get_tablespace('TS_DATA',-2))"
TBSP_AUTO_RESIZE_ENABLED TBSP_INCREASE_SIZE_PERCENT
------------------------ --------------------------
                       1                          -
```

| 指令 | 說明 |
|---|---|
| `alter tablespace <ts> reduce max` | 把 tablespace 縮到最小 (釋放 high water mark 以上的空間) |
| `tbsp_auto_resize_enabled = 1` | 空間不足時自動擴充 |

### 容器位置

```text
$ db2 list tablespace containers for 3
 Container ID                         = 0
 Name                                 = /database/data/db2inst1/NODE0000/LABDB/T0000003/C0000000.LRG
 Type                                 = File
```

`for 3` 的 3 是 tablespace ID (由 `list tablespaces` 得知)。自動儲存的容器是一般檔案。

## 3. 查詢表格與索引

```text
$ db2 list tables for schema SHOP
Table/View                      Schema          Type  Creation time
------------------------------- --------------- ----- --------------------------
CUSTOMER                        SHOP            T     2026-11-04-14.40.21.151307
ORDERS                          SHOP            T     2026-11-04-14.40.21.754458
V_CUST_TOTAL                    SHOP            V     2026-11-04-14.40.22.254231
```

```text
$ db2 describe table SHOP.ORDERS
Column name     Data type schema  Data type name  Length  Scale Nulls
--------------- ----------------- --------------- ------- ----- -----
ORDER_ID        SYSIBM            INTEGER               4     0 No
CUST_ID         SYSIBM            INTEGER               4     0 No
AMOUNT          SYSIBM            DECIMAL              10     2 No
ORDER_DATE      SYSIBM            DATE                  4     0 No
```

```text
$ db2 describe indexes for table SHOP.ORDERS
Index schema  Index name          Unique rule  Number of columns
------------- ------------------- ------------ -----------------
SYSIBM        SQL261104144021970  P                            1
SHOP          IX_ORDERS_CUST      D                            1
```

| 指令 | 說明 |
|---|---|
| `list tables for schema <s>` | 列出 schema 下的表格 (`T`) 與檢視 (`V`)。`for all` 列出全部 |
| `describe table <s.t>` | 欄位定義 |
| `describe indexes for table <s.t>` | 索引清單。Unique rule：`P` = 主鍵、`U` = 唯一、`D` = 允許重複 |

主鍵自動產生的索引名稱是 `SYSIBM.SQL...` 這種系統名稱。

### 系統目錄 SYSCAT

```text
$ db2 "select substr(tabschema,1,8) sch, substr(tabname,1,12) tab, type, card, substr(tbspace,1,10) tbsp from syscat.tables where tabschema='SHOP'"
SCH      TAB          TYPE CARD                 TBSP
-------- ------------ ---- -------------------- ----------
SHOP     CUSTOMER     T                      -1 TS_DATA
SHOP     ORDERS       T                      -1 TS_DATA
SHOP     V_CUST_TOTAL V                      -1 -
```

`CARD = -1` 代表**還沒收集統計資訊**。optimizer 會用預設值估算，可能選錯執行計畫，Lab 07 會用 `RUNSTATS` 修正。

常用 SYSCAT 視圖：`SYSCAT.TABLES`、`SYSCAT.COLUMNS`、`SYSCAT.INDEXES`、`SYSCAT.TABLESPACES`、`SYSCAT.BUFFERPOOLS`、`SYSCAT.VIEWS`。

### 表格實際佔用空間

```text
$ db2 "select substr(tabname,1,12) tab, data_object_p_size, index_object_p_size from sysibmadm.admintabinfo where tabschema='SHOP'"
TAB          DATA_OBJECT_P_SIZE   INDEX_OBJECT_P_SIZE
------------ -------------------- --------------------
CUSTOMER                      512                  512
ORDERS                       1024                 1024
```

單位為 KB (實體配置大小)。

## 4. 匯出 DDL：`db2look`

```bash
db2look -d LABDB -z SHOP -e -o /tmp/shop_ddl.sql
```

| 參數 | 說明 |
|---|---|
| `-d <db>` | 資料庫名稱 |
| `-z <schema>` | 只匯出指定 schema |
| `-t <t1> <t2>` | 只匯出指定表格 |
| `-e` | 匯出物件 DDL (table、index、view、constraint …) |
| `-l` | 匯出 bufferpool、tablespace、storage group 的 DDL |
| `-x` | 一併匯出 GRANT 權限 |
| `-o <file>` | 輸出檔 |

輸出片段：

```sql
CREATE TABLE "SHOP    "."CUSTOMER"  (
		  "CUST_ID" INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY (
		    START WITH +1
		    INCREMENT BY +1
		    ...
		    CACHE 20
		    NO ORDER ) ,
		  "NAME" VARCHAR(50 OCTETS) NOT NULL ,
		  ...
		 IN "TS_DATA" INDEX IN "TS_IDX"
		 ORGANIZE BY ROW;
ALTER TABLE "SHOP    "."CUSTOMER" ALTER COLUMN "CUST_ID" RESTART WITH 21;
```

- `RESTART WITH 21`：identity 有 `CACHE 20`，DB2 一次預先配置 20 個號碼，所以下一個值是 21 而非 5
- 常見用途：搬遷前備份結構、在測試環境重建相同結構 (`db2look ... -e -l -x` 一次全拿)

## 小結

| 指令 | 用途 |
|---|---|
| `db2 -tvf <file>` | 執行 SQL 腳本 |
| `db2 list tablespaces show detail` | tablespace 狀態與空間 |
| `SYSIBMADM.TBSP_UTILIZATION` / `MON_GET_TABLESPACE` | 用 SQL 看 tablespace 使用率 |
| `db2 list tablespace containers for <id>` | tablespace 的容器位置 |
| `db2 list tables for schema <s>` | 列出表格 |
| `db2 describe table / describe indexes for table` | 欄位、索引定義 |
| `SYSCAT.*` | 系統目錄，查所有物件的中繼資料 |
| `db2look` | 匯出 DDL |
