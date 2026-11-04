# Lab 04：使用者、權限與遠端連線

## 目標

- 了解 DB2 的身分驗證：使用者來自作業系統，權限記錄在資料庫中
- 用 `GRANT` / `REVOKE` 與 `ROLE` 管理權限
- 從 SYSCAT 查詢誰有什麼權限
- 用 `CATALOG TCPIP NODE` 透過 TCP/IP 連線

前置條件：Lab 03 已建立 `SHOP` schema 的物件。

## 1. 建立 OS 使用者

DB2 預設 (`AUTHENTICATION = SERVER`) 會交給作業系統驗證帳號密碼，所以使用者要先在 OS 上建立。
這一步需要 root，從主機下：

```bash
docker exec db2lab bash -c "useradd -m appuser && echo 'appuser:App12345' | chpasswd"
docker exec db2lab bash -c "useradd -m reporter && echo 'reporter:Rpt12345' | chpasswd"
```

| 使用者 | 角色 |
|---|---|
| `appuser` | 應用程式帳號：可讀寫訂單 |
| `reporter` | 報表帳號：只能讀客戶與統計檢視 |

確認驗證方式：

```text
$ db2 get dbm cfg | grep -E '\(AUTHENTICATION\)|SRVCON_AUTH'
 Server Connection Authentication          (SRVCON_AUTH) = NOT_SPECIFIED
 Database manager authentication        (AUTHENTICATION) = SERVER
```

## 2. 授權

以下回到 `db2inst1` shell，並先 `db2 connect to LABDB`。

```bash
# 角色：把權限集合起來，再授予使用者
db2 "create role R_READONLY"
db2 "grant select on SHOP.CUSTOMER to role R_READONLY"
db2 "grant select on SHOP.V_CUST_TOTAL to role R_READONLY"

db2 "grant connect on database to user reporter"
db2 "grant role R_READONLY to user reporter"

# 直接授權給使用者
db2 "grant connect on database to user appuser"
db2 "grant select, insert, update on SHOP.ORDERS to user appuser"
db2 "grant select on SHOP.CUSTOMER to user appuser"
```

| 語法 | 說明 |
|---|---|
| `create role <r>` | 建立角色 |
| `grant <權限> on <物件> to role <r>` | 權限給角色 |
| `grant role <r> to user <u>` | 角色給使用者 |
| `grant connect on database to user <u>` | 資料庫層級權限 (`CONNECT`、`DBADM`、`LOAD`、`CREATETAB` …) |
| `grant select, insert, update, delete on <表> to user <u>` | 表格層級權限 |
| `to user` / `to group` / `to role` / `to public` | 授權對象類型 |

> **Db2 12 的預設行為**：`PUBLIC` 預設**沒有** `CONNECT` 權限，新使用者必須明確授權才能連線：
>
> ```text
> $ db2 connect to LABDB user nobodyx using Nob12345
> SQL1060N  User "NOBODYX " does not have the CONNECT privilege.  SQLSTATE=08004
> ```

## 3. 驗證權限

```text
$ db2 connect to LABDB user reporter using Rpt12345
 SQL authorization ID   = REPORTER

$ db2 "select count(*) from SHOP.CUSTOMER"
          4

$ db2 "select count(*) from SHOP.ORDERS"
SQL0551N  The statement failed because the authorization ID does not have the
required authorization or privilege to perform the operation.  Authorization
ID: "REPORTER".  Operation: "SELECT". Object: "SHOP.ORDERS".  SQLSTATE=42501

$ db2 connect reset
```

`SQL0551N` 是最常見的權限錯誤，訊息會寫出是哪個使用者、做什麼操作、哪個物件。

## 4. 查詢權限

### 資料庫層級：`SYSCAT.DBAUTH`

```text
$ db2 "select substr(grantee,1,10) grantee, granteetype, connectauth, dbadmauth from syscat.dbauth"
GRANTEE    GRANTEETYPE CONNECTAUTH DBADMAUTH
---------- ----------- ----------- ---------
DB2INST1   U           N           Y
REPORTER   U           Y           N
APPUSER    U           Y           N
```

建庫者 (`DB2INST1`) 自動擁有 `DBADM`。

### 表格層級：`SYSCAT.TABAUTH`

```text
$ db2 "select substr(grantee,1,12) grantee, granteetype, substr(tabname,1,12) tab, selectauth, insertauth, updateauth, deleteauth from syscat.tabauth where tabschema='SHOP'"
GRANTEE      GRANTEETYPE TAB          SELECTAUTH INSERTAUTH UPDATEAUTH DELETEAUTH
------------ ----------- ------------ ---------- ---------- ---------- ----------
DB2INST1     U           CUSTOMER     G          G          G          G
R_READONLY   R           CUSTOMER     Y          N          N          N
APPUSER      U           CUSTOMER     Y          N          N          N
DB2INST1     U           ORDERS       G          G          G          G
APPUSER      U           ORDERS       Y          Y          Y          N
DB2INST1     U           V_CUST_TOTAL G          N          N          N
R_READONLY   R           V_CUST_TOTAL Y          N          N          N
```

| 值 | 意義 |
|---|---|
| `GRANTEETYPE` | `U` = 使用者、`G` = 群組、`R` = 角色 |
| `Y` | 有權限 |
| `G` | 有權限，且可再授權給別人 (`WITH GRANT OPTION`)，物件擁有者預設如此 |
| `N` | 沒有權限 |

### 角色成員：`SYSCAT.ROLEAUTH`

```text
$ db2 "select substr(grantee,1,10) grantee, substr(rolename,1,12) role from syscat.roleauth where rolename='R_READONLY'"
GRANTEE    ROLE
---------- ------------
REPORTER   R_READONLY
```

## 5. 撤銷權限

```text
$ db2 "revoke update on SHOP.ORDERS from user appuser"
DB20000I  The SQL command completed successfully.

$ db2 "select substr(authid,1,10) id, privilege, substr(objectname,1,12) obj from sysibmadm.privileges where authid='APPUSER'"
ID         PRIVILEGE   OBJ
---------- ----------- ------------
APPUSER    INSERT      ORDERS
APPUSER    SELECT      ORDERS
APPUSER    SELECT      CUSTOMER
```

`SYSIBMADM.PRIVILEGES` 把各種物件的權限整理成一列一個權限，查單一使用者時比 TABAUTH 好讀。

## 6. 透過 TCP/IP 連線 (Catalog)

前面的連線都是本機連線 (IPC)。遠端用戶端要先在自己的目錄中「catalog」伺服器位址與資料庫。
這裡在容器內對 `127.0.0.1` 做 catalog，模擬遠端用戶端：

```text
$ db2 catalog tcpip node LABNODE remote 127.0.0.1 server 50000
DB20000I  The CATALOG TCPIP NODE command completed successfully.
DB21056W  Directory changes may not be effective until the directory cache is refreshed.

$ db2 catalog database LABDB as LABREM at node LABNODE
DB20000I  The CATALOG DATABASE command completed successfully.

$ db2 terminate
```

| 指令 | 說明 |
|---|---|
| `catalog tcpip node <節點名> remote <主機> server <埠號或service名>` | 在節點目錄登記一台 DB2 server |
| `catalog database <資料庫> as <別名> at node <節點名>` | 在資料庫目錄登記遠端資料庫，給它一個本地別名 |
| `terminate` | 結束 CLP 背景程序，讓目錄變更生效 (`DB21056W` 就是在提醒這件事) |
| `uncatalog node <節點名>` / `uncatalog database <別名>` | 移除登記 |

```text
$ db2 list node directory
Node 1 entry:
 Node name                      = LABNODE
 Protocol                       = TCPIP
 Hostname                       = 127.0.0.1
 Service name                   = 50000

$ db2 connect to LABREM user appuser using App12345
 SQL authorization ID   = APPUSER
 Local database alias   = LABREM

$ db2 "select current user, current server from sysibm.sysdummy1"
APPUSER    LABDB
```

透過別名 `LABREM` 走 TCP/IP 連到實際的 `LABDB`。從主機用 JDBC 連線的話，URL 為
`jdbc:db2://localhost:50000/LABDB`。

## 小結

| 指令 / 視圖 | 用途 |
|---|---|
| `useradd` (OS) | DB2 使用者來自 OS |
| `grant ... to user/group/role` | 授權 |
| `revoke ... from user/group/role` | 撤銷 |
| `create role` / `grant role` | 角色管理 |
| `SYSCAT.DBAUTH` / `TABAUTH` / `ROLEAUTH` | 查權限 |
| `SYSIBMADM.PRIVILEGES` | 依使用者查所有權限 |
| `catalog tcpip node` / `catalog database` | 設定 TCP/IP 連線 |
| `list node directory` / `list db directory` | 查看目錄 |
