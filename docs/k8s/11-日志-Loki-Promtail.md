# 11 · 日志（Loki + Promtail）

可观测性第二条腿：**指标看"有没有问题"（Prometheus，见 `10`），日志看"具体哪错了"（Loki）**。两者同进一个 Grafana。

架构：**Promtail(每节点 DaemonSet) tail /var/log/pods → 推给 Loki → Grafana 加 Loki 数据源用 LogQL 查**。

```
每节点: Promtail(DaemonSet) ──tail /var/log/pods──▶ Loki(存储+检索) ◀──LogQL── Grafana
```

**Loki vs ELK**：ELK 对日志正文全文索引，强但吃资源；Loki **只索引标签(label)**，正文压缩存文件，按标签缩范围再 grep——轻、省、便宜，是"给日志做的 Prometheus"。

## Step 1 · 部署 Loki（单体模式，进 chart）

`templates/loki.yaml`：ConfigMap(配置) + Deployment + Service(3100)。要点：
- **单体(monolithic)模式**：一个进程扛收/存/查，`ring.kvstore: inmemory`，无需外部一致性存储。
- **文件存储**：`storage.filesystem` + `schema: v13` + `store: tsdb`（3.x 现代格式；老教程的 `boltdb-shipper` 已过时，别抄）。
- **`args: ["-config.file=/etc/loki/loki.yaml"]`** 覆盖镜像自带默认配置。
- **坑：`fsGroup: 10001`**——loki 镜像以 UID 10001 跑，emptyDir 默认 root 属主写不进 `/loki`（同 grafana `fsGroup: 472`，见 `06`）。
- **`analytics.reporting_enabled: false`**——关联网上报，离线 kind 里不然启动重试卡顿。
- 探针打 `/ready`（起来前返回 503）。

## Step 2 · 部署 Promtail（DaemonSet + RBAC）

`templates/promtail.yaml`：ServiceAccount + ClusterRole + ClusterRoleBinding + ConfigMap + DaemonSet。

**为什么要 RBAC**：Promtail 靠 K8s 服务发现(`kubernetes_sd_configs: role: pod`)调 API Server 列 pod，拿 namespace/pod/container 元数据给日志打标签。调 API 要过两关：**认证**(pod 自带 SA token) + **授权**(RBAC 决定能干啥)。默认 SA 零权限 → List pods 403 → 采不动。故建：
- ClusterRole：`get/list/watch` `pods/nodes/...`。**集群级**，因要跨所有 namespace 采集。
- 记忆：**SA=谁，ClusterRole=能干啥，Binding=接起来**。

**DaemonSet 三个必踩点**：
1. **`tolerations: NoSchedule/Exists`**——日志采集器要覆盖每个节点，含真实集群里带污点的 control-plane。toleration 与「是不是 DaemonSet」无关，只与目标节点是否带污点有关；本地单节点 kind 节点无污点（kind 会去掉 control-plane 污点），此处不加也能调度，保留是为可移植到有污点的真集群。
2. **`hostPath: /var/log` + `runAsUser: 0`**——日志在宿主机节点上（root 属主），挂 hostPath 进来，需 root 读。
3. **`HOSTNAME` 注入（最易漏，见 `03` 第 15 条）**：
   ```yaml
   env:
     - name: HOSTNAME
       valueFrom: { fieldRef: { fieldPath: spec.nodeName } }
   ```
   Promtail 作 DaemonSet 只发现自己节点的 pod，靠 `HOSTNAME` 判断节点。容器里 `HOSTNAME` 默认=Pod 名 → 选择器 `spec.nodeName=<Pod名>` 匹配不到 → `promtail_targets_active_total 0`。注入真实节点名才通。

**关键 relabel（拼日志文件路径）**：
```yaml
- source_labels: [__meta_kubernetes_pod_uid, __meta_kubernetes_pod_container_name]
  target_label: __path__
  separator: /                                  # 把 uid、container 拼成 "uid/container"
  replacement: /var/log/pods/*$1/*.log          # 命中 <ns>_<pod>_<uid>/<container>/0.log
- action: labelmap
  regex: __meta_kubernetes_pod_label_(.+)        # 把 pod 自身 label(app=xxx)也带成 Loki label
```
`pipeline_stages: [{cri: {}}]`——解析 containerd/CRI 日志行前缀。

## Step 3 · Grafana 接 Loki（自动 provisioning）

在 `grafana-datasources` ConfigMap 里追加（与 Prometheus 并列）：
```yaml
- name: Loki
  type: loki
  access: proxy
  url: http://loki:3100        # Service DNS
```
改 ConfigMap 后 Grafana 不会自动重载 → `kubectl rollout restart deploy/grafana`。

## 验证 & 排查

- **看链路是否真通**（不靠 UI）：
  ```bash
  # Promtail 侧：目标数 / 读取 / 发送
  kubectl -n iot port-forward ds/promtail 9080:9080
  curl -s localhost:9080/metrics | grep -E 'promtail_targets_active_total|promtail_sent_entries_total'
  curl -s localhost:9080/config    # 看实际加载的 scrape_configs / selector（排 HOSTNAME 坑用这个）
  # Loki 侧：收到哪些 namespace
  kubectl -n iot port-forward svc/loki 3100:3100
  curl -s "http://localhost:3100/loki/api/v1/label/namespace/values?start=$(( $(date +%s)-3600 ))000000000"
  ```
- **Grafana Explore** → 选 Loki → 查询：

## 常用 LogQL

```logql
{namespace="iot"}                    # 某命名空间全部日志
{app="backend"}                      # 按 pod label(app) 筛（labelmap 带过来的）
{namespace="iot"} |= "ERROR"         # 行过滤：含 ERROR。|= 含 / != 不含 / |~ 正则
{app="backend"} |= "Netty"           # 后端里带 Netty 的行
count_over_time({app="backend"}[5m]) # 指标化：5 分钟内日志行数（可画曲线/告警）
```
> Loki 3.x 还会自动给日志加 `detected_level`（info/warn/error）、`service_name` 等标签，可直接 `{detected_level="error"}`。

## 部署方式说明（GitOps 场景）

Loki/Promtail 折进现有 Helm chart（`loki.enabled`/`promtail.enabled` 开关），随 ArgoCD 一起管。本地迭代调试**别用 `helm upgrade`**（ArgoCD 接管后无 helm release，会撞所有权冲突）——暂停 ArgoCD 自动同步 + `helm template | kubectl apply`，调通再 commit+push 交还。详见 `09`。
