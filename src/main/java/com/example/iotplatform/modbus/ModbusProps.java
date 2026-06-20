package com.example.iotplatform.modbus;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Modbus 网关配置（绑定 modbus.* 前缀）。 */
@ConfigurationProperties(prefix = "modbus")
public record ModbusProps(
        boolean enabled,
        int slavePort,
        int unitId,
        String deviceId,
        long pollIntervalMs
) {}
