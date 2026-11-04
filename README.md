# DB2 維運練習 (Docker)

用 Docker 跑 IBM Db2 Community Edition，依序練習 DB2 DBA 的日常維運指令。
每個 lab 的 README 都記錄了指令的用途、參數說明，以及實際執行時的輸出。

## 環境

| 項目 | 值 |
|---|---|
| 映像 | `icr.io/db2_community/db2:latest` (實測為 Db2 v12.1.5.0) |
| 容器名稱 | `db2lab` |
| Instance / 使用者 | `db2inst1` / `Passw0rd` (見 `.env`) |
| 連線埠 | `50000` |
| 練習資料庫 | `LABDB` (Lab 02 建立) |
| 授權限制 | Community 版：最多 4 cores、8 GB 記憶體 |

```bash
cp .env.example .env
docker compose up -d
docker logs -f db2lab          # 等到出現 "Setup has completed." (首次約 2~5 分鐘)
docker exec -it db2lab su - db2inst1    # 進入 instance 使用者的 shell，之後的 db2 指令都在這裡下
```

`./labs` 會掛載到容器內的 `/labs`，所以 SQL 腳本可直接用 `db2 -tvf /labs/...` 執行。

## Lab 清單

| Lab | 主題 | 重點指令 |
|---|---|---|
| [01-setup](labs/01-setup/README.md) | 環境建置與確認 | `docker compose`, `db2level`, `db2licm`, `db2ilist`, `db2pd -` |
| [02-instance-database](labs/02-instance-database/README.md) | Instance 與資料庫管理 | `db2start/db2stop`, `CREATE DATABASE`, `ACTIVATE`, `GET/UPDATE DBM/DB CFG`, `db2set` |
| [03-objects](labs/03-objects/README.md) | 資料庫物件 | Bufferpool、Tablespace、Schema、Table、Index、View、`db2look` |
| [04-security](labs/04-security/README.md) | 使用者、權限與遠端連線 | `GRANT/REVOKE`, `ROLE`, `SYSCAT.*AUTH`, `CATALOG TCPIP NODE` |
| [05-data-movement](labs/05-data-movement/README.md) | 資料搬移 | `EXPORT`, `IMPORT`, `LOAD`, `SET INTEGRITY`, `db2move` |
| [06-backup-recovery](labs/06-backup-recovery/README.md) | 備份與還原 | `BACKUP`, `LOGARCHMETH1`, `RESTORE`, `ROLLFORWARD`, `db2ckbkp` |
| [07-maintenance](labs/07-maintenance/README.md) | 日常維護與執行計畫 | `RUNSTATS`, `REORGCHK`, `REORG`, `EXPLAIN`, `db2exfmt`, `db2rbind` |

Lab 之間有資料相依 (例如 Lab 07 的筆數取決於 Lab 05、06 做過的事)，建議照順序做。

## 從頭重來

```bash
docker compose down -v    # -v 會一併刪除 db2data volume (資料庫、備份全部清掉)
docker compose up -d
```
