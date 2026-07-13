# 13 · 告警（Prometheus Rules + Alertmanager）

可观测性从"看得见"到"主动喊你"。分工：
- **Prometheus 评估告警规则(rule)**：PromQL 表达式持续满足 `for:` 时长 → 告警从 `pending` 转 `firing`，推给 Alertmanager。
- **Alertmanager**：**去重 / 分组(group) / 路由(route) / 静默(silence) / 抑制(inhibit)** → 发通知（邮件/Slack/企业微信/webhook）。
- 一句话：**Prometheus 决定"要不要告警"，Alertmanager 决定"怎么通知、发给谁"。**

## Step 1 · Prometheus 加告警规则

`prometheus.yml` 加 `rule_files`，规则文件挂进容器（subPath）：
```yaml
rule_files:
  - /etc/prometheus/rules.yml
```
`rules.yml`：
```yaml
groups:
  - name: demo-alerts
    rules:
      - alert: TargetDown
        expr: up == 0            # PromQL
        for: 1m                  # 持续 1m 才 firing（防抖动）
        labels: { severity: critical }
        annotations: { summary: "有抓取目标掉线" }
```
- `AlwaysFiringDemo: expr: vector(1)` 恒触发，用于验证链路。
- 状态：条件成立但没到 `for` 时长 = **pending**；到了 = **firing**。

## Step 2 · Prometheus 指向 Alertmanager

```yaml
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

## Step 3 · 部署 Alertmanager

`templates/alertmanager.yaml`：ConfigMap(alertmanager.yml) + Deployment + Service。
- 镜像 **`quay.io/prometheus/alertmanager`**（Docker Hub 被墙，quay 节点直连可拉，无需旁路）。
- `args: --storage.path=/tmp/alertmanager`（用 /tmp 可写，避开卷权限）。
- `alertmanager.yml` 核心：
  ```yaml
  route:
    receiver: default
    group_by: ['alertname']   # 同名告警合并成一条通知
    group_wait: 10s           # 首次攒一波
    repeat_interval: 1h       # 重复提醒间隔
  receivers:
    - name: default
      # 真实通知在此加：webhook_configs / email_configs / wechat_configs / slack_configs
  ```

## ⚠️ 踩坑

1. **`{{ }}` 出现在 Helm 模板里（连注释、连规则 annotations）会被 Helm 解析** → `parse error: missing value for command`。规则 annotations 想用 Prometheus 的 `{{ $labels.x }}`/`{{ $value }}` 插值时，会和 Helm 冲突。**解法**：① 本 demo 避开插值用静态文案；② 要插值就把规则放独立文件用 `.Files.Get` 引入（Helm 不模板化 Files 内容）。**注释里也别写字面双花括号。**
2. **Prometheus→Alertmanager 有发送延迟**：规则刚 firing 时 AM 可能还是 0 条，稍等即到（批量推送）。排查：`/api/v1/alertmanagers` 看 `activeAlertmanagers` 有没有你的 AM（空=连不上/配错）。

## 验证

```bash
# Prometheus 侧（port-forward svc/prometheus 9090）
curl -s localhost:9090/api/v1/rules          # 规则加载了吗
curl -s localhost:9090/api/v1/alerts         # 哪些在 firing/pending
curl -s localhost:9090/api/v1/alertmanagers  # 认得的 AM（active 应有你的）
# Alertmanager 侧（port-forward svc/alertmanager 9093）
curl -s localhost:9093/api/v2/alerts         # 收到的告警
```
- UI：Prometheus `:9090/alerts`；Alertmanager `:9093`（可点 Silence 静默）。
- 实测：`AlwaysFiringDemo` 在 Prometheus firing → 推送 → Alertmanager 收到（active）。
