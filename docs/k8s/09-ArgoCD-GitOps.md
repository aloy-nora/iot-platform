# 09 · ArgoCD（GitOps CD）

GitOps = **Git 是唯一事实源，部署是"拉"不是"推"**：ArgoCD 装在集群内，持续把集群拉齐成 Git 里描述的样子。

## 安装

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```
- 坑：`applicationsets.argoproj.io` CRD 因 `last-applied-configuration` 注解超 256KB 报 `Too long` 而没建——**只用 Application 无影响**（`applications` CRD 已建）；想要它就用 `kubectl apply --server-side`。
- 取 admin 密码 + 进 UI：
```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo
kubectl port-forward -n argocd svc/argocd-server 8080:443   # https://localhost:8080, 账号 admin
```

## Application（部署定义，进 git：`deploy/argocd/application.yaml`）

关键字段：
- `source.repoURL / path / targetRevision`：盯哪个仓、哪个目录（chart 路径）、哪个分支；
- `destination.namespace`：部署到哪个命名空间；
- `syncPolicy.automated`：`prune`（Git 删了的资源集群也删）+ `selfHeal`（手动改集群会被拉回 Git 的样子）；
- `syncOptions: [CreateNamespace=true]`：目标命名空间不存在则自动建。

## 从 helm 交接给 ArgoCD

```bash
helm uninstall iot -n iot                          # 卸掉手动 helm 部署
kubectl apply -f deploy/argocd/application.yaml     # ArgoCD 接管
```
接管后不再有 helm release —— ArgoCD 自己渲染 chart + apply + 跟踪资源。

### 交接后想本地快速调试新组件（别用 helm upgrade！）

正道是 **改文件 → git push → ArgoCD 同步**。但迭代新组件（如加 Loki）时 push 一次 CI 就全量重建后端、还得等 reconcile，太慢。此时：

```bash
# ① 暂停自动同步，防止 ArgoCD 把本地临时装的资源当垃圾 prune 掉
kubectl -n argocd patch application iot-platform --type merge -p '{"spec":{"syncPolicy":{"automated":null}}}'
# ② 用 ArgoCD 同款方式(template+apply)本地上，可只渲染指定模板；-n 保证 .Release.Namespace 正确
helm template iot deploy/helm/iot-platform -n iot -s templates/loki.yaml -s templates/promtail.yaml | kubectl apply -n iot -f -
# ③ 调通后：commit+push，再恢复自动同步，交还 ArgoCD
kubectl -n argocd patch application iot-platform --type merge -p '{"spec":{"syncPolicy":{"automated":{"prune":true,"selfHeal":true}}}}'
```

**为什么不是 `helm upgrade`**：ArgoCD 接管后集群里**没有 helm release 记录**，现有资源也不带 helm 所有权标注。`helm upgrade` 会报 `has no deployed releases`；加 `--install` 则撞 `invalid ownership metadata / already exists and is not managed by Helm`。而 `helm template | kubectl apply` 和 ArgoCD 做的**完全一样**（渲染 YAML → apply），幂等、无所有权冲突、恢复同步时无缝衔接。
**原则：谁在管这个 release，就用谁的方式改。** 纯 helm 阶段用 `helm upgrade`；交给 ArgoCD 后一律走 git / template+apply。

## 工作机制（何时同步，务必理解）

- 每 **~3 分钟** reconcile（或配 GitHub webhook 即时）：拉 master 最新 commit → **只渲染 `path` 目录**的 chart → 和集群实际对比 → **只应用 diff**。
- **同一个 tag / 渲染结果无变化 → 无 diff → 什么都不做**。**commit 本身不是触发器，渲染结果的差异才是**（所以闭环要用 `:<sha>`，见 `08`）。
- 想立即同步：UI 点 **Refresh/Sync**。

## 常用命令

```bash
kubectl get application -n argocd -o wide    # SYNC(Synced) / HEALTH(Healthy) / REVISION
```

## 坑

- **有状态服务交接**：helm uninstall 删 PVC 与 ArgoCD 重建过快 → DB 数据盘半初始化崩溃（见 `03` 第 14 条：`delete pvc --wait=false` 清空重来）。
- **镜像 tag 不变不触发同步**（见 `08` 闭环）。
- **资源**：ArgoCD 自身约 7 个组件，单节点 kind 上叠加平台易内存吃紧（见 `03` 第 13 条）。
