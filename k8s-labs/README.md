# k8s-labs

四個由淺入深的小實驗，透過 [kind](https://kind.sigs.k8s.io/)（Kubernetes in Docker）在本機學習 Kubernetes 與 Helm 的基本觀念。每個 lab 都是獨立目錄，可以單獨操作。

## 事前準備

建立一個共用的 kind cluster（所有 lab 都可以重複使用同一個 cluster）：

```bash
kind create cluster --name k8s-labs
kubectl cluster-info --context kind-k8s-labs
```

做完全部實驗後，可以刪除 cluster 釋放資源：

```bash
kind delete cluster --name k8s-labs
```

> 這幾個 lab 都用 `kubectl port-forward` 對外連線，不需要額外設定 kind 的 `extraPortMappings`。

---

## Lab 1 — `lab1-hello-world/`：最基礎的 Deployment + Service

**觀念**：Kubernetes 最基本的兩個資源。
- **Deployment**：宣告要跑幾個 Pod（`replicas`）、用什麼 image、怎麼更新版本。Deployment 會建立並管理 ReplicaSet，確保永遠有指定數量的 Pod 在跑。
- **Service**：Pod 會不斷被重建、IP 會變，Service 提供一個固定的虛擬 IP / DNS 名稱，透過 `selector` 比對 Pod 的 `label` 來做流量轉發與負載平衡。

這裡部署兩個 replica 的 `nginx:alpine`，用預設頁面驗證流量真的有打到 Pod。

**操作**：

```bash
cd lab1-hello-world
kubectl apply -f .

# 觀察 Deployment 建立的 Pod
kubectl get pods -l app=hello-world
kubectl get deployment hello-world

# 透過 Service 存取
kubectl port-forward svc/hello-world 8080:80
# 另開一個 terminal
curl http://localhost:8080
```

試著把 `deployment.yaml` 的 `replicas` 改成 3，`kubectl apply -f .` 後觀察 Pod 數量變化，體會 Deployment 如何維持期望狀態（desired state）。

**清除**：

```bash
kubectl delete -f .
```

---

## Lab 2 — `lab2-configmap/`：ConfigMap 的意義與用法

**觀念**：ConfigMap 把設定值從 container image 中抽離出來，讓同一份 image 可以在不同環境（dev/staging/prod）套用不同設定，而不用重新 build image。

這個 lab 示範 ConfigMap 最常見的兩種用法：

1. **當作環境變數**：透過 `envFrom.configMapRef`，ConfigMap 裡每一個 key 都會變成 container 內的一個環境變數（範例中的 `GREETING`）。
2. **當作掛載檔案**：透過 `volumes.configMap` + `volumeMounts`，把 ConfigMap 裡的某個 key（`index.html`）掛成容器內的檔案，取代 nginx 預設首頁。

**操作**：

```bash
cd lab2-configmap
kubectl apply -f .
kubectl rollout status deployment/hello-config

# 驗證環境變數
kubectl exec deploy/hello-config -- printenv GREETING

# 驗證掛載檔案（會看到自訂的 HTML，而不是 nginx 預設頁）
kubectl port-forward svc/hello-config 8080:80
curl http://localhost:8080
```

**動手試試**：修改 `configmap.yaml` 裡的 `GREETING` 或 `index.html` 內容，`kubectl apply -f configmap.yaml` 後：
- 環境變數**不會**自動更新（需要重建 Pod，例如 `kubectl rollout restart deployment/hello-config`）。
- 掛載的檔案**會**在數十秒內（kubelet sync 週期）自動更新，不需要重建 Pod。

這個差異是理解 ConfigMap 運作方式的重點：環境變數是 Pod 啟動當下注入的一次性快照，掛載檔案則是持續同步的。

**清除**：

```bash
kubectl delete -f .
```

---

## Lab 3 — `lab3-helm-hello-world/`：把 Lab 1 抽換成 Helm Chart

**觀念**：Helm 是 Kubernetes 的套件管理工具。與其手寫、手動 apply 多份 YAML，Helm 把資源定義寫成**模板（template）**，搭配 `values.yaml` 集中管理可調參數，並用 `helm install` / `upgrade` / `uninstall` 管理整個應用程式的生命週期（稱為一個 *release*）。

這個 chart 是 Lab 1 的 Helm 版本：
- `Chart.yaml`：chart 的中繼資料（名稱、版本）。
- `values.yaml`：預設參數，例如 `replicaCount`、`image.repository`、`service.port`。
- `templates/deployment.yaml`、`templates/service.yaml`：用 `{{ .Values.xxx }}` 語法讀取參數的模板，取代寫死的值。
- `templates/NOTES.txt`：`helm install` 完成後印出的操作提示。

**操作**：

```bash
cd lab3-helm-hello-world

# 檢查 chart 語法
helm lint .

# 先渲染出實際的 YAML 看看（不會真的部署，方便理解模板如何展開）
helm template lab3 .

# 安裝（release 名稱為 lab3）
helm install lab3 .
kubectl port-forward svc/lab3-hello-world 8080:80
curl http://localhost:8080

# 修改參數後升級，例如把 replica 數改成 3
helm upgrade lab3 . --set replicaCount=3
kubectl get pods -l app=lab3-hello-world

# 查看目前所有 release
helm list

# 查看某個 release 的版本歷史
helm history lab3
```

**清除**：

```bash
helm uninstall lab3
```

---

## Lab 4 — `lab4-helm-configmap/`：把 Lab 2 抽換成 Helm Chart

**觀念**：延續 Lab 3，這裡把 Lab 2 的 ConfigMap 也模板化，並額外示範一個 Helm 常見的實務技巧：**ConfigMap 更新時自動觸發 Pod 重新部署**。

`templates/deployment.yaml` 裡的這段：

```yaml
annotations:
  checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") . | sha256sum }}
```

會對渲染後的 `configmap.yaml` 內容算出雜湊值，寫進 Pod template 的 annotation。只要 ConfigMap 內容改變，這個雜湊值就會跟著變，連帶讓 Pod template 的 spec 也不同，Deployment 因此會觸發 rolling update ——解決了 Lab 2 提到的「改 ConfigMap 但環境變數不會自動更新」的問題。

**操作**：

```bash
cd lab4-helm-configmap
helm lint .
helm install lab4 .

kubectl exec deploy/lab4-configmap-demo -- printenv GREETING
kubectl port-forward svc/lab4-configmap-demo 8080:80
curl http://localhost:8080
```

**動手試試**：修改 `values.yaml` 的 `config.pageMessage`，然後升級：

```bash
helm upgrade lab4 . --set config.pageMessage="Hello, Helm!"
kubectl rollout status deployment/lab4-configmap-demo
```

觀察會發生一次新的 rolling update（跟 Lab 2 純 YAML 版本需要手動 `rollout restart` 不同），再次 `curl` 驗證頁面內容已更新。

**清除**：

```bash
helm uninstall lab4
```

---

## 小結

| Lab | 資源型態 | 核心觀念 |
|---|---|---|
| 1 | 原生 YAML | Deployment（期望狀態、Pod 管理）+ Service（穩定的網路入口） |
| 2 | 原生 YAML | ConfigMap 兩種用法：環境變數（一次性快照）vs 掛載檔案（持續同步） |
| 3 | Helm Chart | 用 `values.yaml` 參數化 Lab 1，體驗 `helm install/upgrade/uninstall` |
| 4 | Helm Chart | 參數化 Lab 2 的 ConfigMap，並用 checksum annotation 讓設定變更自動觸發滾動更新 |

建議操作順序：Lab 1 → Lab 2 → Lab 3 → Lab 4，每個 lab 做完記得清除資源（`kubectl delete -f .` 或 `helm uninstall`）再進行下一個，避免 Service/Deployment 名稱衝突。
