# 07 · Helm 打包（M2）

把 `deploy/k8s/*.yaml` 收敛成一个可复用、可版本化的 Chart：`deploy/helm/iot-platform/`。

## 核心概念

- **Chart**：一个应用的打包目录（模板 + 默认值）。
- **values.yaml**：所有可变参数集中处；模板里用 `{{ .Values.xxx }}` 引用。
- **Release**：一次 `helm install` 就是一个 release，可 `upgrade`/`rollback`/`uninstall`。

## 目录结构

```
deploy/helm/iot-platform/
├── Chart.yaml            # name/version/appVersion
├── values.yaml          # 镜像/密码/存储/副本数等参数
├── files/               # 供 .Files 读取的静态文件（MySQL schema sql）
└── templates/           # mysql/redis/tdengine/emqx/backend + mysql-initdb
```

## 模板化要点（由裸 manifest 改造）

- 去掉每个资源的 `namespace:` 字段 → 交给 `helm install -n iot`（Helm 惯例，命名空间由 Release 决定）。
- 写死的值抽到 values：`image: {{ .Values.mysql.image }}`、`storage: {{ .Values.mysql.storage }}`。
- 密码等字符串加 `| quote`：`{{ .Values.redis.password | quote }}`（防纯数字/特殊字符被 YAML 误解析）。

## schema 自动初始化（本 Chart 的关键）

- **MySQL**：`.Files.Glob "files/*.sql").AsConfig` 把 sql 转成 ConfigMap（`mysql-initdb`），挂到容器 `/docker-entrypoint-initdb.d/`。mysql 镜像**首次初始化空数据目录时**自动执行 → 建 `device`/`alarm` 表。
- **TDengine**：容器 `lifecycle.postStart` 里 `until taos -s 'show databases'…` 等就绪后 `CREATE STABLE IF NOT EXISTS`。幂等、跑在容器内连 localhost（绕开 FQDN 问题）。

## 常用命令

```bash
helm lint deploy/helm/iot-platform                     # 校验 Chart
helm template iot deploy/helm/iot-platform             # 只渲染不部署（调试模板）
helm install iot deploy/helm/iot-platform -n iot --create-namespace
helm upgrade iot deploy/helm/iot-platform -n iot       # 改模板/values 后更新
helm list -n iot ; helm status iot -n iot
helm rollback iot <REVISION> -n iot                    # 回滚
helm uninstall iot -n iot                              # 卸载整套
```

## 踩过的坑

- **注释里的 `{{ }}` 也会被解析**：Helm 把整个文件当模板处理，YAML 的 `#` 注释里若出现非法 `{{ .Values.x.* }}` 会 `parse error: bad character`。注释里别写非法模板串。
- **`[INFO] icon is recommended`**：INFO 级、可忽略；仅发布到 chart 仓库时才需加 `icon:`。
- **裸 manifest 与 Helm 不能并存同名资源**：先 `kubectl delete namespace iot` 清掉手动部署的，再 `helm install`。
- **一键复现验证**：`kubectl delete namespace iot` → `helm install …` → 整套服务 + 表结构全自动重建（backend 可能等依赖时重启一两次再稳定）。

## 服务开关（feature toggle，按需省资源）

把整个模板用 `{{- if .Values.X.enabled }} … {{- end }}` 包起来，values 里给 `enabled: true/false`，即可一键开关某服务：

```yaml
# templates/grafana.yaml
{{- if .Values.grafana.enabled }}
# ...整个 Secret/PVC/Deployment/Service...
{{- end }}
```
```yaml
# values.yaml
grafana:
  enabled: false     # 关掉 → 渲染为空 → ArgoCD prune 从集群删除（省资源），改回 true 即恢复
```
用途：资源紧张时临时关掉非核心服务（见 `03` 第 13 条）；也是"生产/测试环境装不同子集"的标准手法。
