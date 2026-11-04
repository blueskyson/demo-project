# Lab 07：日常維護與執行計畫

## 目標

- `RUNSTATS` 收集統計資訊
- `REORGCHK` 判斷是否需要重組
- `REORG` 重組表格與索引 (離線 / 線上)，並監控進度
- `EXPLAIN` + `db2exfmt` 看執行計畫
- `db2rbind` 重新綁定 package

前置條件：Lab 06 完成。此時 `SHOP.ORDERS` 有 40000 筆 (Lab 05 用 LOAD 多載入了一份)。

```bash
db2 connect to LABDB
```

## 1. 製造碎片

刪掉 3/4 的資料，讓表格與索引出現大量空洞：

```text
$ db2 "delete from SHOP.ORDERS where mod(order_id, 4) <> 0"
$ db2 commit

$ db2 "select substr(tabname,1,10) tab, card, npages, fpages, stats_time from syscat.tables where tabschema='SHOP' and tabname='ORDERS'"
TAB        CARD                 NPAGES               FPAGES               STATS_TIME
---------- -------------------- -------------------- -------------------- ----------
ORDERS                       -1                   -1                   -1 -
```

統計資訊仍是 `-1`：`AUTO_RUNSTATS` 是背景排程執行，不保證馬上收集。

## 2. RUNSTATS

```text
$ db2 "runstats on table SHOP.ORDERS with distribution and detailed indexes all"
DB20000I  The RUNSTATS command completed successfully.

$ db2 "select substr(tabname,1,10) tab, card, npages, fpages, stats_time from syscat.tables where tabschema='SHOP' and tabname='ORDERS'"
TAB        CARD                 NPAGES               FPAGES               STATS_TIME
---------- -------------------- -------------------- -------------------- --------------------------
ORDERS                    10000                  139                  139 2026-11-04-14.44.32.332414
```

| 參數 | 說明 |
|---|---|
| `runstats on table <schema.table>` | 必須寫完整的 schema.table |
| `with distribution` | 收集欄位值分佈 (頻率值、分位數)，資料分佈不均時對 optimizer 很重要 |
| `and detailed indexes all` | 收集所有索引的詳細統計 (cluster ratio 等) |
| `and indexes all` | 收集索引統計 (較不詳細) |
| `allow read access` | 收集期間其他人只能讀 (預設 `allow write access`) |
| `tablesample bernoulli(10)` | 只抽樣 10% 的資料，大表用來縮短時間 |

| 統計欄位 | 意義 |
|---|---|
| `CARD` | 列數 |
| `NPAGES` | 有資料的 page 數 |
| `FPAGES` | 表格佔用的總 page 數 |
| `STATS_TIME` | 最後收集時間 |

**原則：大量異動 (LOAD、大量 DELETE/INSERT) 之後要 RUNSTATS**，否則 optimizer 會依舊的統計選計畫。

## 3. REORGCHK

```text
$ db2 "reorgchk current statistics on table SHOP.ORDERS"

Table statistics:
F1: 100 * OVERFLOW / CARD < 5
F2: 100 * (Effective Space Utilization of Data Pages) > 70
F3: 100 * (Required Pages / Total Pages) > 80

SCHEMA.NAME                     CARD     OV     NP     FP ACTBLK    TSIZE  F1  F2  F3 REORG
----------------------------------------------------------------------------------------
Table: SHOP.ORDERS
                               10000      0    139    139      -   280000   0  25 100 -*-

Index statistics:
F4: CLUSTERRATIO or normalized CLUSTERFACTOR > 80
F5: 100 * (Space used on leaf pages / Space available on non-empty leaf pages) > MIN(50, (100 - PCTFREE))
F6: (100 - PCTFREE) * (Amount of space available in an index with one less level / Amount of space required for all keys) < 100
F7: 100 * (Number of pseudo-deleted RIDs / Total number of RIDs) < 20
F8: 100 * (Number of pseudo-empty leaf pages / Total number of leaf pages) < 20

SCHEMA.NAME                 INDCARD  LEAF ELEAF LVLS  NDEL    KEYS ...  F4  F5  F6  F7  F8 REORG
------------------------------------------------------------------------------------------------
Table: SHOP.ORDERS
Index: SHOP.IX_ORDERS_CUST
                              10000    45    20    2 11981       4 ... 100  42   -  54  44 -*-**
Index: SYSIBM.SQL261104144021970
                              10000    83     0    2 30000   10000 ... 100  24   -  75   0 -*-*-
```

| 參數 | 說明 |
|---|---|
| `current statistics` | 用目前 catalog 中的統計判斷 (所以要先 RUNSTATS) |
| `update statistics` | 先對表格做 RUNSTATS 再判斷 |
| `on table <t>` / `on schema <s>` / `on table all` | 範圍 |

如何讀結果：`REORG` 欄每個字元對應一個公式 (表格是 F1~F3，索引是 F4~F8)，**`*` 代表該項超出門檻**。

| 公式 | 這次的值 | 解讀 |
|---|---|---|
| F2 | 25 (門檻 > 70) | data page 只用了 25%，刪除留下大量空洞 → 表格需要 REORG |
| F5 | 42 / 24 | 索引 leaf page 空間使用率低 |
| F7 | 54 / 75 | pseudo-deleted RID 太多 (刪除的 key 只做了標記，還沒真正移除) |
| F8 | 44 | 有大量空的 leaf page |

## 4. REORG

```text
$ db2 "reorg table SHOP.ORDERS"
DB20000I  The REORG command completed successfully.

$ db2 "reorg indexes all for table SHOP.ORDERS allow write access"
DB20000I  The REORG command completed successfully.

$ db2 "runstats on table SHOP.ORDERS with distribution and detailed indexes all"
$ db2 "reorgchk current statistics on table SHOP.ORDERS"
Table: SHOP.ORDERS
                               10000      0     35     35      -   280000   0 100 100 ---
Index: SHOP.IX_ORDERS_CUST
                              10000    12     0    2     0 ...   100  92   -   0   0 -----
Index: SYSIBM.SQL261104144021970
                              10000    21     0    2     0 ...   100 102   -   0   0 -----
```

NPAGES 從 139 降到 35，所有旗標都變成 `-`。

| 語法 | 說明 |
|---|---|
| `reorg table <t>` | 離線 (classic) 重組：重建整張表，期間預設只能讀 (`allow read access`)，完成後會一併重建索引 |
| `reorg table <t> index <idx>` | 依指定索引的順序重排資料，提升該索引的 cluster ratio |
| `reorg table <t> inplace allow write access` | 線上 (inplace) 重組：逐步搬移資料列，期間可讀寫，非同步執行 |
| `reorg table <t> inplace pause / resume / stop` | 控制進行中的 inplace reorg |
| `reorg indexes all for table <t> allow write access` | 只重建索引 |
| `reorg table <t> ... use <temp_ts>` | 指定暫存 tablespace (離線 reorg 需要額外空間放表格副本) |

**REORG 之後要再 RUNSTATS**，因為資料的實體配置改變了。

### 監控 REORG

```text
$ db2 "reorg table SHOP.ORDERS_BAK inplace allow write access"
DB20000I  The REORG command completed successfully.
DB21024I  This command is asynchronous and may not be effective immediately.

$ db2pd -db LABDB -reorgs
Table Reorg Information:
TbspaceID TableID TableName   Type    IndexID TempSpaceID
3         5       ORDERS      Offline 0       3
3         6       ORDERS_BAK  Online  0       3

Table Reorg Stats:
TableName   Start               End                 MaxPhase Phase      Status Completion
ORDERS      11/04/2026 14:44:42 11/04/2026 14:44:42 3        IdxRecreat Done   0
ORDERS_BAK  11/04/2026 14:44:44 11/04/2026 14:44:44 n/a      n/a        Done   0
```

```text
$ db2 "select substr(tabname,1,12) tab, reorg_status, reorg_completion, reorg_start from sysibmadm.snaptab_reorg"
TAB          REORG_STATUS REORG_COMPLETION REORG_START
------------ ------------ ---------------- --------------------------
ORDERS       COMPLETED    SUCCESS          2026-11-04-14.44.42.359775
ORDERS_BAK   COMPLETED    SUCCESS          2026-11-04-14.44.44.659747
```

| 指令 | 說明 |
|---|---|
| `db2pd -db <db> -reorgs` | 從記憶體讀 reorg 狀態，`Phase` 顯示目前階段 (Sort、Build、Replace、IdxRecreat) |
| `SYSIBMADM.SNAPTAB_REORG` | 用 SQL 查 reorg 狀態 |

注意：`inplace` 回 `DB20000I` 只代表「已開始」(`DB21024I` 說明它是非同步的)，要用上面的方法確認是否完成。

> 小提醒：這裡用 `docker exec ... su - db2inst1 -c` 逐條執行時，`db2pd` 顯示資料庫只 `Up 0 days 00:00:03`。
> 因為資料庫在 Lab 06 還原後沒有 `activate`，每次最後一個連線斷開就會關閉。實務上應保持 `db2 activate db LABDB`。

## 5. EXPLAIN：看執行計畫

### 建立 explain 表格 (只需做一次)

```text
$ db2 "call sysproc.sysinstallobjects('EXPLAIN','C',cast(null as varchar(128)),'DB2INST1')"
  Return Status = 0
```

| 參數 | 說明 |
|---|---|
| `'EXPLAIN'` | 要安裝的工具物件 |
| `'C'` | Create (`'D'` = 刪除、`'V'` = 驗證、`'M'` = 遷移) |
| 第 3 個參數 | tablespace，null 用預設 |
| `'DB2INST1'` | 建在哪個 schema |

### 擷取並格式化執行計畫

```bash
cd ~/work
db2 "set current explain mode explain"
db2 "select cust_id, sum(amount) from SHOP.ORDERS where order_date >= current date - 7 days group by cust_id"
db2 "set current explain mode no"
db2exfmt -d LABDB -1 -o plan_before.txt
```

| 指令 | 說明 |
|---|---|
| `set current explain mode explain` | 只產生執行計畫寫進 explain 表格，**不實際執行** SQL (所以會回 `SQL0217W`) |
| `set current explain mode yes` | 產生計畫且實際執行 |
| `set current explain mode no` | 關閉 |
| `db2exfmt -d <db> -1 -o <file>` | 把 explain 表格中**最新**的一筆計畫格式化成文字。`-1` 表示其他選項都用預設值 |

`plan_before.txt` 中的計畫 (由下往上讀，每個節點顯示：預估列數 / 運算子 / 累計成本 / I/O)：

```text
Access Plan:
-----------
	Total Cost: 		38.518
	Query Degree:		1

      Rows
     RETURN
     (   1)
      Cost
       I/O
       |
        4
     GRPBY
     (   2)
     38.5178
       35
       |
        4
     TBSCAN
     (   3)
       ...
     SORT
       ...
     TBSCAN
       |
      10000
 TABLE: SHOP
     ORDERS
```

常見運算子：

| 運算子 | 意義 |
|---|---|
| `TBSCAN` | 全表掃描 |
| `IXSCAN` | 索引掃描 |
| `FETCH` | 依索引找到的 RID 回表讀資料 |
| `SORT` | 排序 (可能溢出到 temp tablespace) |
| `GRPBY` | GROUP BY |
| `NLJOIN` / `HSJOIN` / `MSJOIN` | Nested loop / Hash / Merge join |

也可以直接用 SQL 查 explain 表格：

```text
$ db2 "select operator_type, count(*) n from explain_operator group by operator_type"
OPERATOR_TYPE N
------------- -----------
GRPBY                   2
RETURN                  3
SORT                    2
TBSCAN                  5
```

> 注意：`substr(operator_type,1,8)` 會出現 `SQL0138N` (參數超出範圍)，因為 `OPERATOR_TYPE` 是 `CHAR(6)`，直接選欄位即可。

### 加索引後比較：optimizer 不一定會用索引

```bash
db2 "create index SHOP.IX_ORDERS_DATE on SHOP.ORDERS (ORDER_DATE) collect statistics"
```

`collect statistics` 表示建索引的同時收集索引統計，省去另外做 RUNSTATS。

實測結果：建了索引後，以下三個查詢的計畫**仍然是 TBSCAN**，成本也不變：

| 查詢 | 表格大小 | 建索引後的計畫 |
|---|---|---|
| `SHOP.ORDERS where order_date >= current date - 7 days` | 10000 列 / 35 pages | TBSCAN，成本 38.5 |
| `SHOP.ORDERS where order_date = current date - 100 days` | 10000 列 / 35 pages | TBSCAN，成本 38.5 (預估 26 列) |
| `SHOP.ORDERS_BAK where amount = 500.50` (索引 `IX_BAK_AMOUNT`) | 20000 列 / 70 pages | TBSCAN，成本 70.3 (預估 20 列) |

可能的原因：表格只有 35~70 個 page，依序讀完整張表 (prefetch) 的成本很低；
而測試資料是用 `MOD(N, 365)` 產生的，同一天的資料平均散佈在每個 page，走索引也幾乎要讀遍所有 page。
這點**尚未驗證完**，待確認：索引的 `CLUSTERRATIO`、`SYSCAT.INDEXES` 的統計是否已收集，以及加大資料量後是否改走 `IXSCAN`。

## 6. 重新綁定 package：`db2rbind`

```text
$ db2rbind LABDB -l ~/work/rbind.log all
 Rebind done successfully for database 'LABDB'.
```

| 參數 | 說明 |
|---|---|
| `-l <file>` | 記錄檔 (必填) |
| `all` | 重新綁定所有 package (不加則只綁定無效的) |
| `-u <user> -p <pw>` | 指定使用者 |

靜態 SQL (程式預先編譯的 package) 的執行計畫是在 bind 時決定的。
RUNSTATS、新增索引之後，要 rebind 才會使用新統計重新選計畫；動態 SQL 則下次 prepare 就會用新的統計。
單一 package 可以用 `db2 "rebind package <schema.pkg>"`。

## 小結：例行維護流程

```text
RUNSTATS → REORGCHK → (有 *) REORG → RUNSTATS → db2rbind
```

| 指令 | 用途 |
|---|---|
| `runstats on table <t> with distribution and detailed indexes all` | 收集統計 |
| `reorgchk current statistics on table <t>` | 判斷是否需要重組 |
| `reorg table <t>` / `reorg table <t> inplace allow write access` | 離線 / 線上重組 |
| `reorg indexes all for table <t>` | 重建索引 |
| `db2pd -db <db> -reorgs` / `SYSIBMADM.SNAPTAB_REORG` | 監控重組進度 |
| `sysinstallobjects('EXPLAIN','C',...)` | 建立 explain 表格 |
| `set current explain mode explain` + `db2exfmt` | 看執行計畫 |
| `db2rbind <db> -l <log> all` | 重新綁定 package |
