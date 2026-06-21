package com.example.iotplatform.service;

import com.example.iotplatform.alarm.AlarmService;
import com.example.iotplatform.model.SensorData;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一落库核心：把一条设备数据写到三处存储。
 * 接入通道有多条（MQTT、Netty TCP……），都汇聚到 {@link #ingest(String, SensorData)}。
 * - TDengine：时序明细（自动建子表）
 * - MySQL   ：设备台账（upsert）
 * - Redis   ：实时最新值（hash）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorIngestService {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final AlarmService alarmService;
    @Qualifier("mysqlJdbcTemplate")
    private final JdbcTemplate mysql;
    @Qualifier("tdengineJdbcTemplate")
    private final JdbcTemplate tdengine;

    /** MQTT 接入通道：从 topic 取 deviceId、解析 payload，再交给统一 ingest。 */
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public void handle(Message<String> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String payload = message.getPayload();
        try {
            String deviceId = extractDeviceId(topic);          // device/{deviceId}/data
            SensorData data = objectMapper.readValue(payload, SensorData.class);
            ingest(deviceId, data);
            log.info("ingest ok (mqtt): device={} payload={}", deviceId, payload);
        } catch (Exception e) {
            log.error("ingest failed (mqtt): topic={} payload={} err={}", topic, payload, e.getMessage(), e);
        }
    }

    /** 统一落库入口：任何接入通道解析出 (deviceId, data) 后调用。 */
    public void ingest(String deviceId, SensorData data) {
        LocalDateTime now = LocalDateTime.now();
        writeTdengine(deviceId, data);
        upsertDevice(deviceId, now);
        cacheLatest(deviceId, data, now);
        alarmService.check(deviceId, data);   // W7：阈值告警
    }

    private String extractDeviceId(String topic) {
        String[] parts = topic.split("/");                     // ["device","{id}","data"]
        if (parts.length < 2 || parts[1].isEmpty()) {
            throw new IllegalArgumentException("非法 topic: " + topic);
        }
        return parts[1];
    }

    /** 写时序：子表不存在时用超级表自动建表（USING ... TAGS） */
    private void writeTdengine(String deviceId, SensorData d) {
        String subTable = "d_" + deviceId.replaceAll("[^a-zA-Z0-9]", "_");
        String location = d.getLocation() == null ? "" : d.getLocation();
        String devType = d.getDevType() == null ? "env" : d.getDevType();
        String sql = "INSERT INTO iot.`" + subTable + "` USING iot.env_sensor "
                + "TAGS ('" + deviceId + "','" + location + "','" + devType + "') "
                + "VALUES (NOW," + num(d.getTemp()) + "," + num(d.getHumi()) + ","
                + num(d.getLight()) + "," + num(d.getSoilPh()) + ")";
        tdengine.update(sql);
    }

    /** upsert 设备台账：首次插入，重复则刷新最近上报时间与累计条数 */
    private void upsertDevice(String deviceId, LocalDateTime now) {
        String sql = "INSERT INTO device(device_id, last_seen, msg_count, online) VALUES(?,?,1,1) "
                + "ON DUPLICATE KEY UPDATE last_seen=VALUES(last_seen), msg_count=msg_count+1, online=1";
        mysql.update(sql, deviceId, Timestamp.valueOf(now));
    }

    /** 写 Redis 实时值（hash） */
    private void cacheLatest(String deviceId, SensorData d, LocalDateTime now) {
        Map<String, String> m = new HashMap<>();
        m.put("temp", num(d.getTemp()));
        m.put("humi", num(d.getHumi()));
        m.put("light", num(d.getLight()));
        m.put("soilPh", num(d.getSoilPh()));
        m.put("ts", now.toString());
        redis.opsForHash().putAll("device:latest:" + deviceId, m);
    }

    private String num(Object v) {
        return v == null ? "NULL" : v.toString();
    }
}
