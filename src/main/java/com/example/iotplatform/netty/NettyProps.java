package com.example.iotplatform.netty;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Netty TCP 接入网关配置（绑定 netty.* 前缀）。 */
@ConfigurationProperties(prefix = "netty")
public record NettyProps(
        boolean enabled,
        int port,
        String deviceId,
        long sendIntervalMs
) {}
