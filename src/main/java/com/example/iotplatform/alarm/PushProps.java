package com.example.iotplatform.alarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 告警推送配置（绑定 push.* 前缀）。
 * type: wecom（企业微信）/ dingtalk（钉钉）；webhookUrl 为空时走 dry-run 仅打印。
 * dedupSeconds: 同设备+同指标在该秒数内只推一次，防告警轰炸。
 */
@ConfigurationProperties(prefix = "push")
public record PushProps(
        boolean enabled,
        String type,
        String webhookUrl,
        int dedupSeconds
) {}
