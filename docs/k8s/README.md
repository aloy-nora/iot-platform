# K8s 运维学习与排查文档

本目录汇总 iot-platform 迁移到 Kubernetes 过程中的安装、命令、排查、维护与规范。
配套清单文件在 `../../deploy/k8s/`。

## 文档规则（新增文档请遵守）

- **位置**：所有运维文档放在 `iot-platform/docs/<主题>/`，一个主题一个子目录（当前：`k8s/`）。
- **固定分类**（每个主题目录下）：
  | 文件 | 内容 |
  |---|---|
  | `01-安装与环境.md` | 装工具、建集群、环境相关的坑 |
  | `02-常用命令.md` | 命令速查（cheatsheet） |
  | `03-问题排查.md` | 遇到的问题：**现象 / 原因 / 解决 / 原理(面试点)** 四段式 |
  | `04-维护操作.md` | 重启、更新、扩缩容、持久化验证等日常维护 |
  | `05-清单规范与模板.md` | 可复制模板 + 命名/字段顺序规范 |
  | `06-概念与原理.md` | 底层概念/原理（exec/PID1/tini、ENTRYPOINT vs CMD、command/args 等） |
  | `07-Helm.md` | Helm 打包（Chart/values/模板、.Files/initdb、postStart、常用命令、服务开关） |
  | `08-CI-CD.md` | GitHub Actions CI→GHCR、CD 闭环（sha/write-back/detached HEAD/防递归、单仓 vs 双仓） |
  | `09-ArgoCD-GitOps.md` | ArgoCD 安装/Application/交接/同步机制/坑 |
  | `10-监控-Prometheus-Grafana.md` | actuator+micrometer 暴露指标、Prometheus 抓取、Grafana 数据源 provisioning、看板坑 |
  | `11-日志-Loki-Promtail.md` | Loki 单体部署、Promtail DaemonSet+RBAC、HOSTNAME 节点坑、relabel 拼路径、LogQL |
  | `12-追踪-Tempo-OTel.md` | Tempo 部署、OTel Java Agent 自动埋点（Dockerfile+env）、OTLP 4317/4318 坑、TraceQL |
  | `13-告警-Alertmanager.md` | Prometheus 告警规则(rule_files)、指向 AM、Alertmanager 部署/路由/分组、{{}}冲突坑 |
- **命名**：数字前缀 `01- 02-…` 控制顺序（与清单文件 `00- 10- 20-` 前缀同思路）。
- **排查条目**：一律按「现象 → 原因 → 解决 → 原理」写，方便日后检索，也直接是面试素材。

## 当前环境速览

| 项 | 值 |
|---|---|
| 本地集群工具 | kind v0.32（集群名 `iot`，context `kind-iot`） |
| K8s 版本 | v1.36.1（单节点 `iot-control-plane`） |
| 命名空间 | `iot` |
| 已部署 | MySQL 8.0、Redis 7-alpine（均 Deployment + Service + PVC + Secret） |
| 清单目录 | `deploy/k8s/`（`00-namespace` `10-mysql` `20-redis`） |
| 关键约束 | 节点走不通宿主机代理，镜像一律 `kind load` 送进节点（见 `03`） |
