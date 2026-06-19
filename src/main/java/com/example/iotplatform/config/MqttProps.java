package com.example.iotplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 配置（类型安全绑定 mqtt.* 前缀）。
 * 本身不是 bean，需在 @Configuration 上用 @EnableConfigurationProperties(MqttProps.class) 注册。
 */
@ConfigurationProperties(prefix = "mqtt")
public record MqttProps(
        String url,
        String clientId,
        String topics,
        String username,
        String password
) {}
