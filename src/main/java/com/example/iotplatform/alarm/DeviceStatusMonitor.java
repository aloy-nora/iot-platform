package com.example.iotplatform.alarm;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 设备在线状态监控：定时把"超过 offlineSeconds 未上报"的设备标记为离线。
 * 这是平台"设备掉线感知"的基础能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusMonitor {

    private final AlarmProps props;
    @Qualifier("mysqlJdbcTemplate")
    private final JdbcTemplate mysql;

    @Scheduled(fixedDelay = 10000)   // 每 10s 扫一次
    public void detectOffline() {
        int n = mysql.update(
                "UPDATE device SET online = 0 "
                        + "WHERE online = 1 AND last_seen < (NOW() - INTERVAL ? SECOND)",
                props.offlineSeconds());
        if (n > 0) log.info("标记 {} 个设备离线（>{}s 未上报）", n, props.offlineSeconds());
    }
}
