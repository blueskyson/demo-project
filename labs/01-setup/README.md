# Lab 01：環境建置與確認

## 目標

- 用 Docker Compose 啟動 Db2 Community Edition
- 認識容器內的目錄結構與 instance 使用者
- 確認版本、授權、instance 狀態與連線埠

## 1. 啟動容器

```bash
cp .env.example .env
docker compose up -d
docker logs -f db2lab
```

| 指令 | 說明 |
|---|---|
| `docker compose up -d` | 依 `docker-compose.yml` 在背景建立並啟動 `db2lab` 容器 |
| `docker logs -f db2lab` | 持續追蹤容器 log；看到 `(*) Setup has completed.` 代表 instance 已建好並啟動 |

`docker-compose.yml` 的重點設定：

| 設定 | 原因 |
|---|---|
| `privileged: true` | DB2 啟動時要調整 kernel IPC 參數，官方映像要求 privileged |
| `db2data:/database` | instance home、資料庫、log 都放在這個 named volume，容器重建後資料仍在 |
| `./labs:/labs` | 讓容器內能直接讀到各 lab 的 SQL 腳本 |

`.env` 的重點變數：

| 變數 | 說明 |
|---|---|
| `LICENSE=accept` | 必須接受授權，否則容器不會啟動 DB2 |
| `DB2INSTANCE=db2inst1` | instance 名稱，同時也是容器內的 OS 使用者 |
| `DB2INST1_PASSWORD` | `db2inst1` 的 OS 密碼，也是連線資料庫時用的密碼 |
| `DBNAME=` | 留空表示不自動建庫，Lab 02 會手動建立 |

## 2. 進入 instance 使用者

```bash
docker exec -it db2lab su - db2inst1
```

DB2 的指令 (`db2`, `db2pd`, `db2look` 等) 需要先載入 `~/sqllib/db2profile` 環境，
`su -` (login shell) 會自動載入，所以一定要加 `-`。

```text
$ id; echo $HOME
uid=1000(db2inst1) gid=1000(db2iadm1) groups=1000(db2iadm1)
/database/config/db2inst1
```

- `db2iadm1` 是 SYSADM 群組，屬於這個群組的 OS 使用者擁有 instance 最高權限
- instance home 是 `/database/config/db2inst1`，底下的 `sqllib/` 是 instance 目錄 (設定檔、db2dump、執行檔連結)

> 如果只想跑一條指令，不進互動 shell：
> `docker exec db2lab su - db2inst1 -c "db2level"`

## 3. 確認版本：`db2level`

```text
$ db2level
DB21085I  This instance or install (instance name, where applicable:
"db2inst1") uses "64" bits and DB2 code release "SQL12015" with level
identifier "02060110".
Informational tokens are "DB2 v12.1.5.0", "special_85816",
"DYN2606252315AMD64_85816", and Fix Pack "0".
Product is installed at "/opt/ibm/db2/V12.1".
```

用途：查詢 DB2 的版本與 Fix Pack，回報問題或套用修補前必看。

## 4. 確認授權：`db2licm -l`

```text
$ db2licm -l
Product name:                     "DB2 Community Edition"
License type:                     "Community"
Expiry date:                      "Permanent"
Max amount of memory (GB):        "8"
Max number of cores:              "4"
Enforcement policy:               "Soft Stop"
```

用途：列出已安裝產品的授權資訊。Community 版免費、永久有效，但限制 4 cores / 8 GB。

## 5. 列出 instance：`db2ilist`

```text
$ db2ilist
db2inst1
```

用途：列出這套 DB2 安裝底下所有的 instance。一台主機可以有多個 instance，各自獨立設定與啟停。

## 6. 確認目前的 instance：`db2 get instance`

```text
$ db2 get instance
 The current database manager instance is:  db2inst1
```

用途：顯示目前 shell 所使用的 instance (由環境變數 `DB2INSTANCE` 決定)。

## 7. 確認 instance 是否在執行：`db2pd -`

```text
$ db2pd -
Database Member 0 -- Active -- Up 0 days 00:00:36 -- Date 2026-11-04-14.36.37.308306
```

用途：`db2pd` 是直接讀取 DB2 共享記憶體的診斷工具，不需要連線、不會拿鎖。
只下 `-` 會顯示 instance 是否 Active，以及已啟動多久。

## 8. 確認連線埠

```text
$ grep db2c_ /etc/services
db2c_db2inst1      50000/tcp
db2c_db2inst1_ssl  50001/tcp

$ db2 get dbm cfg | grep -i svcename
 TCP/IP Service name                          (SVCENAME) = db2c_db2inst1
```

- dbm cfg 的 `SVCENAME` 存的是 service 名稱 `db2c_db2inst1`，實際的埠號要到 `/etc/services` 查 (50000)
- `docker-compose.yml` 把容器的 50000 對應到主機的 50000，所以主機上的 DBeaver 等工具可以直接連 `localhost:50000`

## 小結

| 指令 | 用途 |
|---|---|
| `docker exec -it db2lab su - db2inst1` | 以 instance 使用者身分進入容器 |
| `db2level` | 版本與 Fix Pack |
| `db2licm -l` | 授權資訊 |
| `db2ilist` | 列出所有 instance |
| `db2 get instance` | 目前使用的 instance |
| `db2pd -` | instance 是否運作中 |
| `db2 get dbm cfg` | instance 層級設定 (Lab 02 詳述) |
