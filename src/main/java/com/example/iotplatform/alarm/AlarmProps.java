package com.example.iotplatform.alarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 告警与设备状态配置（绑定 alarm.* 前缀）。
 * 阈值：温度 [tempMin, tempMax]，湿度 [humiMin, humiMax]，越界即告警。
 * offlineSeconds：超过该秒数未上报判离线。
 */
@ConfigurationProperties(prefix = "alarm")
public record AlarmProps(
        double tempMin,
        double tempMax,
        double humiMin,
        double humiMax,
        int offlineSeconds
) {}
