# iot-platform

一个物联网数据平台学习项目：设备数据经 **MQTT / TCP(Netty) / Modbus** 多路接入，落 **TDengine（时序）+ MySQL（台账）+ Redis**，**Grafana** 可视化，**MediaMTX** 做 RTSP→HLS 视频接入。后端为 **Spring Boot 3 / Java 21**。

整套平台已从 Docker Compose **迁移到 Kubernetes**，并用 **Helm** 打包（含 schema 自动初始化）。

## 技术栈

- 后端：Spring Boot 3.4、Spring Integration MQTT、JDBC 双数据源、Netty、j2mod(Modbus)
- 存储：TDengine 3.3、MySQL 8、Redis 7
- 接入/流媒体：EMQX 5、MediaMTX
- 可视化：Grafana 12
- 部署：Docker Compose、Kubernetes、Helm、kind（本地）

## 快速开始

**Docker Compose：**
```bash
docker compose up -d
```

**Kubernetes（Helm，本地 kind）：**
```bash
helm install iot deploy/helm/iot-platform -n iot --create-namespace
kubectl get pods -n iot
```

## 文档

K8s 迁移与运维的完整笔记（安装 / 常用命令 / 问题排查 / 维护 / 清单规范 / 概念原理 / Helm）见 [`docs/k8s/`](docs/k8s/)。其中 `03-问题排查.md` 汇总了迁移过程真实踩过的 10+ 个坑。

## ⚠️ 安全说明

本仓库中所有数据库/后台密码（`example123`、`redis123`、`admin123`、`taosdata` 等）均为**本地演示用的默认值，并非真实密钥**，仅对本地 kind/compose 环境有效。

**生产环境请勿直接使用**，应通过 K8s Secret、外部密钥管理（如 Vault / 云厂商 KMS）或 `helm install --set` / gitignore 的 `values-secret.yaml` 注入覆盖。
