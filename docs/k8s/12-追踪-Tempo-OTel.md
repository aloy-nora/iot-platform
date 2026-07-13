# 12 · 链路追踪（Tempo + OpenTelemetry）

可观测性第三条腿：**指标看"有没有问题"(Prometheus)，日志看"哪里错"(Loki)，追踪看"慢在哪一步、哪次调用卡了"(Tempo)**。三者同进一个 Grafana。

- **Trace**：一个请求从头到尾的旅程。**Span**：旅程里一个操作单元（一次 SQL / Redis / HTTP），有起止时间、父子关系。一 Trace = 一棵 Span 树。
- **OpenTelemetry(OTel)**：厂商中立标准 + SDK。**Java Agent 自动埋点**（`-javaagent`，零改代码，自动给 HTTP/JDBC/Redis/调度/MQTT 加 span），用 **OTLP** 协议发给后端。
- **Tempo**：Grafana 家的 trace 后端，只索引 trace ID、正文压存，轻量。

架构：`backend(挂 OTel Agent) ──OTLP──▶ Tempo ◀── Grafana(Tempo 数据源)`

## Step 1 · 部署 Tempo（进 chart）

`templates/tempo.yaml`：ConfigMap + Deployment + Service。要点：
- **单体模式** + **文件存储**（`storage.trace.backend: local`）。
- **OTLP 接收器**：`distributor.receivers.otlp` 开 grpc(4317) + http(4318)；查询端口 3200（Grafana 数据源连它）。
- **坑：`fsGroup: 10001`**——tempo 镜像 UID 10001，emptyDir 默认 root 写不进 `/var/tempo`（同 loki/grafana）。
- `usage_report.reporting_enabled: false` 关联网上报。

## Step 2 · 后端接入 OTel Java Agent（生产正规做法）

**① Dockerfile 构建期下载 agent 烧进镜像**（CI 有网）：
```dockerfile
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar /app/otel-javaagent.jar
```
**② 部署 env 里启用 + 配置**（不改镜像即可开关）：
```yaml
- name: JAVA_TOOL_OPTIONS
  value: "-javaagent:/app/otel-javaagent.jar"
- name: OTEL_SERVICE_NAME
  value: iot-backend
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: http://tempo:4318      # ⚠️ 见下"坑"
- name: OTEL_METRICS_EXPORTER   # 指标已用 Prometheus，别重复导出
  value: none
- name: OTEL_LOGS_EXPORTER      # 日志已用 Loki
  value: none
```
- agent/env 改动通过 CI/GitOps 生效：改 Dockerfile → push → CI 打新镜像写回 sha → ArgoCD 部署。

## Step 3 · Grafana 加 Tempo 数据源

`grafana-datasources` ConfigMap 追加（与 Prometheus/Loki 并列）：
```yaml
- name: Tempo
  type: tempo
  access: proxy
  url: http://tempo:3200
```
改 ConfigMap 后 `kubectl rollout restart deploy/grafana`。

## ⚠️ 头号坑：OTLP 4317 vs 4318（协议/端口不匹配）

- **现象**：backend 日志 `Failed to export spans ... unexpected end of stream on http://tempo:4317`，Tempo 收不到 trace。
- **原因**：**OTel Java Agent 2.x 默认协议是 `http/protobuf`（走 4318）**，而 4317 是 **gRPC** 端口。用 HTTP 打 gRPC 端口 → 流意外结束。
- **解决**：端点用 **4318**（`OTEL_EXPORTER_OTLP_ENDPOINT=http://tempo:4318`）；或保持 4317 但设 `OTEL_EXPORTER_OTLP_PROTOCOL=grpc`。Tempo 两个端口都开着，改端点即可，**纯 env 改动、不用重建镜像**。

## 验证 & 查看

```bash
# Tempo 收到哪些 service / 最近 trace（port-forward svc/tempo 3200）
curl -s http://localhost:3200/api/v2/search/tag/resource.service.name/values
curl -s -G http://localhost:3200/api/search --data-urlencode 'q={}' --data-urlencode 'limit=5'
```
- **Grafana Explore → Tempo → Search → Run** → 点 traceID 看 waterfall（span 树 + 每步耗时）。
- 实测 agent 自动埋出：`SELECT iot`(JDBC)、`ModbusDeviceSimulator.refresh`(@Scheduled)、`mqttInboundChannel process`(MQTT)——**零改代码**。追踪立刻暴露了一条 3.2s 的 MQTT 慢处理。
- **TraceQL**（Tempo 查询语言，类比 LogQL/PromQL）：`{ resource.service.name = "iot-backend" && duration > 1s }` 查慢 trace。

至此 **Prometheus(指标) + Loki(日志) + Tempo(追踪)** 三合一进 Grafana，可观测性闭环完成。
