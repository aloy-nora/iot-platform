package com.example.iotplatform.alarm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 告警推送：把告警发到企业微信/钉钉群机器人。
 * - 去重限流：同 设备+指标 在 dedupSeconds 内只推一次（Redis SETNX + TTL）
 * - 异步：@Async 不阻塞 ingest 主链路
 * - 容错：webhookUrl 为空走 dry-run 仅打印；推送异常只记日志
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotifier {

    private final PushProps props;
    private final StringRedisTemplate redis;
    private final RestClient restClient = RestClient.create();

    @Async
    public void push(String deviceId, String metric, String message) {
        if (!props.enabled()) return;

        // 去重：SETNX 占位，dedupSeconds 内同 设备+指标 再来直接跳过
        String key = "alarm:dedup:" + deviceId + ":" + metric;
        Boolean first = redis.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(props.dedupSeconds()));
        if (Boolean.FALSE.equals(first)) {
            log.debug("告警去重跳过 device={} metric={}", deviceId, metric);
            return;
        }

        String text = "【设备告警】" + deviceId + "\n" + message;
        if (props.webhookUrl() == null || props.webhookUrl().isBlank()) {
            log.info("[dry-run 推送] {}", text.replace("\n", " | "));
            return;
        }
        try {
            String body = buildBody(text);
            String resp = restClient.post()
                    .uri(props.webhookUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("告警已推送 device={} resp={}", deviceId, resp);
        } catch (Exception e) {
            log.error("告警推送失败 device={} err={}", deviceId, e.getMessage());
        }
    }

    /** 企业微信/钉钉的机器人都接受 {"msgtype":"text","text":{"content":"..."}} 格式 */
    private String buildBody(String text) {
        String content = text.replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + content + "\"}}";
    }
}
