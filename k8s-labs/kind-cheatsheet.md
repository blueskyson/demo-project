# Kubernetes `kind` 速查表

YAML manifest 裡 `kind:` 欄位常用資源類型的分類整理，方便快速查找。

## 1. Workload（工作負載）— 跑你的程式

| kind | 用途 |
|---|---|
| `Pod` | 最小部署單位，通常不直接手寫，而是透過下方控制器建立 |
| `Deployment` | 無狀態應用，滾動更新、多副本 |
| `ReplicaSet` | 維持固定數量 Pod，通常由 Deployment 自動建立，很少手寫 |
| `StatefulSet` | 有狀態應用，Pod 有固定名稱/身份與獨立儲存（例如資料庫） |
| `DaemonSet` | 每個 Node 都跑一份（例如日誌收集、監控 agent） |
| `Job` | 跑到完成就結束的一次性任務 |
| `CronJob` | 定期排程執行的 Job（類似 crontab） |

## 2. 網路（Networking）

| kind | 用途 |
|---|---|
| `Service` | 給 Pod 群組一個穩定的虛擬 IP / DNS 名稱 |
| `Ingress` | 依 domain/path 把外部 HTTP(S) 流量路由到不同 Service |
| `NetworkPolicy` | 定義 Pod 之間、與外部的網路存取規則（類似防火牆） |

## 3. 設定與機密（Config）

| kind | 用途 |
|---|---|
| `ConfigMap` | 非機密設定值，可當環境變數或掛載檔案 |
| `Secret` | 機密資料（密碼、token、憑證），用法類似 ConfigMap，但會 base64 編碼並限制存取 |

## 4. 儲存（Storage）

| kind | 用途 |
|---|---|
| `PersistentVolume` (PV) | 叢集管理員提供的實體儲存資源 |
| `PersistentVolumeClaim` (PVC) | 應用程式對儲存的「請求」，會綁定到 PV |
| `StorageClass` | 定義動態建立 PV 的方式（例如用哪種雲端硬碟） |

## 5. 權限與帳號（RBAC / Security）

| kind | 用途 |
|---|---|
| `ServiceAccount` | Pod 用來跟 API Server 溝通的身份 |
| `Role` / `ClusterRole` | 定義可以執行哪些操作（可讀取哪些資源） |
| `RoleBinding` / `ClusterRoleBinding` | 把 Role 綁定給 User / ServiceAccount |

## 6. 命名空間與資源限制

| kind | 用途 |
|---|---|
| `Namespace` | 邏輯隔離資源的「分區」（例如 dev/staging/prod） |
| `ResourceQuota` | 限制一個 Namespace 能用多少 CPU/記憶體/資源數量 |
| `LimitRange` | 設定 Namespace 內 Pod/Container 的預設與上下限資源用量 |

---

**Namespace 有無限制**：Workload、Networking、Config、Storage（PV 除外）、RBAC 的 Role/RoleBinding 這些資源都是 **namespaced**（屬於特定 namespace）；`Namespace`、`PersistentVolume`、`ClusterRole`、`ClusterRoleBinding`、`StorageClass` 則是 **cluster-scoped**（整個叢集共用，不屬於任何 namespace）。

可以用以下指令查詢某個 kind 是否有 namespace：

```bash
kubectl api-resources --namespaced=true
kubectl api-resources --namespaced=false
```
