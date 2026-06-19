# 环境准备说明（prepare.md）

物联网平台学习项目的本地开发环境，由 Spring Boot 项目根目录的 **`compose.yaml`** 一键拉起五件套。

> 本项目用了 `spring-boot-docker-compose` 依赖：**运行 Spring Boot 应用时会自动启动 `compose.yaml` 里的服务**，应用停止时自动关闭。第 2 周还没写 Java，可先用下面的 `docker compose` 命令手动起环境。

| 组件 | 镜像 | 作用 | 关键端口 |
|---|---|---|---|
| EMQX | `emqx/emqx:5.10.4` | MQTT Broker，设备消息接入 | 1883(MQTT) / 18083(控制台) |
| TDengine | `tdengine/tsdb:3.3.7.0` | 时序数据库，存设备时序数据 | 6041(REST) / 6030(原生) / 6060(Web) |
| MySQL | `mysql:8.0` | 关系库，设备台账/元数据 | 3306 |
| Redis | `redis:7-alpine` | 缓存，实时值/限流 | 6379 |
| Grafana | `grafana/grafana:12.4.4` | 可视化大盘 | 3000 |

> **版本原则**：用主流稳定版，不追最新版（最新版常踩"文档/插件没跟上"的坑），不用 `:latest`（会漂移、不可复现）。

## 默认账号密码（仅本地开发用）

| 服务 | 账号 | 密码 |
|---|---|---|
| EMQX Dashboard | `admin` | `public`（首次登录要求改密） |
| TDengine | `root` | `taosdata` |
| MySQL | `root` | `example123`（库 `iot` 已自动创建） |
| Redis | — | `redis123` |
| Grafana | `admin` | `admin123` |

---

## 启动环境

方式一：手动起（第 2 周用这个，纯环境、不依赖 Java）

```bash
cd ~/Workshop/iot-platform
docker compose up -d        # 自动识别 compose.yaml
docker compose ps           # 等 5 个服务都是 healthy
```

方式二：跑 Spring Boot 应用（第 3 周起）——`spring-boot-docker-compose` 会自动拉起以上服务。

> Mac（含 Apple 芯片）均可，镜像都是多架构。首次拉镜像较慢，耐心等。
> 重置环境（清空数据卷重来）：`docker compose down -v`

---

## 环境就绪验收

### 1. 五个组件逐个验证

| 组件 | 验证方式 | 期望 |
|---|---|---|
| EMQX | 浏览器开 http://localhost:18083 ，登录 `admin / public` | 进入 Dashboard |
| TDengine | `docker exec -it iot-tdengine taos -s "show databases;"` | 列出系统库，无报错 |
| MySQL | `docker exec -it iot-mysql mysql -uroot -pexample123 -e "show databases;"` | 看到 `iot` 库 |
| Redis | `docker exec -it iot-redis redis-cli -a redis123 ping` | 返回 `PONG` |
| Grafana | 浏览器开 http://localhost:3000 ，登录 `admin / admin123` | 进得去，数据源里能找到 TDengine |

### 2. MQTT 收发测试（第 1 周核心）

1. 下载 **MQTTX** 桌面客户端（emqx.com/zh/products/mqttx）。
2. 新建连接：Host `127.0.0.1`，Port `1883`，其余默认，连接。
3. 开两个会话：
   - A 订阅 Topic：`device/+/data`
   - B 向 `device/001/data` 发：`{"temp":25.6,"humi":60}`
   - ✅ A 应能收到。
4. EMQX Dashboard → Connections / Subscriptions，应看到连接和订阅。
5. 加分：测一次 **retained（保留消息）** 和 **LWT（遗嘱消息）**。

### ✅ 第 1 周达成标准

- `docker compose ps` 五个服务全部 healthy。
- MQTTX 能收发消息。
- 能讲清 QoS 0/1/2、retained、LWT 各是什么。

---

## 常见坑

- **镜像拉不下来 / 慢**：配置 Docker 镜像加速器，或多重试。
- **端口被占**：`lsof -i :3306` 等查占用，改 compose 里的宿主机端口映射。
- **TDengine 起不来**：多因 FQDN，确认 `TAOS_FQDN=tdengine` 且 `hostname: tdengine` 都在。
- **Grafana 装 TDengine 插件失败**：新版 Grafana 可能不兼容，退回 `grafana/grafana:11.6.15`。

---

## 第 2 周：TDengine 建模 + SQL

```bash
# 建库 / 超级表 / 子表（可一次性导入）
docker exec -i iot-tdengine taos < sql/01_schema.sql

# 灌 ~1500 条模拟数据（需 pip install requests）
python3 scripts/gen_data.py

# 练习查询：把 sql/02_queries.sql 里的语句逐条贴进 taos shell 跑
docker exec -it iot-tdengine taos
```

## 下一步

- 第 3 周：Spring Boot 用 `spring-boot-starter-integration` 订阅 MQTT，把数据自动写进 TDengine + MySQL + Redis。
