# 10 · 监控（Prometheus + Grafana）

架构：**应用暴露 `/actuator/prometheus` → Prometheus 抓取存时序 → Grafana 查询可视化**。

## Step 1 · 后端暴露指标（代码改动）

`build.gradle`：
```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```
`application.yaml`：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus     # 默认只暴露 health，要显式加 prometheus
  metrics:
    tags:
      application: ${spring.application.name}   # 给所有指标打 application 标签，社区看板按它筛选
```
验证：`curl localhost:8080/actuator/prometheus`（port-forward 后）出一堆 `jvm_*`/`http_*` 指标。

## Step 2 · 部署 Prometheus（进 chart，带 enabled 开关）

- **镜像用 `quay.io/prometheus/prometheus`**（`prom/prometheus` 在 Docker Hub，本网络被墙；quay 节点直连可拉）。
- ConfigMap 放 `prometheus.yml` 抓取配置，挂到 `/etc/prometheus/prometheus.yml`（subPath 单文件）：
  ```yaml
  scrape_configs:
    - job_name: iot-backend
      metrics_path: /actuator/prometheus
      static_configs:
        - targets: ['backend:8080']    # Service DNS 直指后端
  ```
- **不给 `/prometheus` 挂数据卷**——用镜像自带目录（nobody 可写、数据临时），避开 fsGroup 卷权限问题（见 `06`）。
- 探针用 `/-/ready`（就绪）、`/-/healthy`（存活）——`/-/` 是 Prometheus 约定的运维端点前缀。

## Step 3 · Grafana 接 Prometheus（自动 provisioning）

ConfigMap 放数据源定义，挂到 `/etc/grafana/provisioning/datasources/`，Grafana 启动即自动连上、无需手点：
```yaml
apiVersion: 1
datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090     # Service DNS
    isDefault: true
```

## 验证 & 排查

- **Explore**：选 Prometheus → 查 `jvm_memory_used_bytes` → 出曲线 = 全链路通（这才是要证明的）。
- **查真实标签**（不依赖 UI）：
  ```bash
  kubectl exec -n iot deploy/prometheus -- wget -qO- 'http://localhost:9090/api/v1/label/application/values'
  kubectl exec -n iot deploy/prometheus -- wget -qO- 'http://localhost:9090/api/v1/targets?state=active'
  ```
- **坑：老社区看板（4701/12900）在新 Grafana（12.x）上 No Data**：变量定义格式变了，导入后 `application`/`instance` 变量的 query 是空的 → 下拉空 → 面板无数据。**不是数据问题**（Explore 已证明数据在）。解法：手动给变量补 `label_values(application)`；或**自己建面板**（更省心、更懂）；或把看板 JSON provisioning 进 chart（datasource 写死，无导入映射问题）。

## 常用 PromQL（自建面板用）

```promql
sum by (area) (jvm_memory_used_bytes{application="iot-platform"})              # 堆/非堆内存
process_cpu_usage{application="iot-platform"}                                  # 进程 CPU
jvm_threads_live_threads{application="iot-platform"}                           # 活跃线程
rate(http_server_requests_seconds_count{application="iot-platform"}[1m])       # 每秒请求
```

## 自建看板 provisioning（as-code，替代社区看板导入）

`templates/grafana-dashboards.yaml`：两个 ConfigMap——**provider 配置**(`/etc/grafana/provisioning/dashboards/`，`options.path` 指向看板目录) + **看板 JSON**(挂到 `/var/lib/grafana/dashboards/`)。Grafana 启动即自动加载，进 git 可复现。
- **数据源固定 uid**：给 Prometheus 数据源加 `uid: prometheus`，看板 JSON 里 `"datasource":{"type":"prometheus","uid":"prometheus"}` 稳定引用（不靠导入映射，无 No Data）。
- **7 个面板 PromQL**（本平台真实指标）：运行时长 `process_uptime_seconds`；CPU `process_cpu_usage`/`system_cpu_usage`；堆内存 `sum(jvm_memory_used_bytes{area="heap"})`；线程 `jvm_threads_live_threads`；QPS `sum(rate(http_server_requests_seconds_count[1m]))`；**平均延迟 `sum(rate(..._sum[1m]))/sum(rate(..._count[1m]))`**（Micrometer Timer 经典）；DB 连接 `hikaricp_connections_active`。
- **坑①**：看板 JSON 里 `legendFormat` 的双花括号插值会和 Helm 冲突（`missing value for command`）——用静态 legend 或 `.Files.Get`。**注释里也别写字面双花括号。**
- **坑②**：改数据源 `uid` 后 Grafana 启动报 `Datasource provisioning error: data source not found`——旧 PVC 存了旧 uid 记录冲突。Grafana 全靠 provisioning、PVC 无需保留 → **删 grafana PVC 重建**即干净。
