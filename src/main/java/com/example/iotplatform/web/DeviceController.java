package com.example.iotplatform.web;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备查询接口（W3 数据的读出口）：
 * - GET /api/devices              设备列表 + Redis 实时值
 * - GET /api/devices/{id}/history 某设备时序历史（查 TDengine）
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final StringRedisTemplate redis;
    @Qualifier("mysqlJdbcTemplate")
    private final JdbcTemplate mysql;
    @Qualifier("tdengineJdbcTemplate")
    private final JdbcTemplate tdengine;

    /** 设备列表（MySQL 台账）+ 每个设备的实时值（Redis hash） */
    @GetMapping
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> devices = mysql.queryForList(
                "SELECT device_id, online, last_seen, msg_count FROM device ORDER BY device_id");
        for (Map<String, Object> d : devices) {
            String id = String.valueOf(d.get("device_id"));
            d.put("latest", redis.opsForHash().entries("device:latest:" + id));
        }
        return devices;
    }

    /** 某设备最近 N 小时的时序明细（查 TDengine 子表） */
    @GetMapping("/{id}/history")
    public List<Map<String, Object>> history(@PathVariable String id,
                                             @RequestParam(defaultValue = "2") int hours) {
        String sub = "d_" + id.replaceAll("[^a-zA-Z0-9]", "_");
        String sql = "SELECT ts, temp, humi, light, soil_ph FROM iot.`" + sub + "` "
                + "WHERE ts >= NOW - " + hours + "h ORDER BY ts";
        return tdengine.queryForList(sql);
    }

    /** 设备影子：台账(MySQL) + 实时值(Redis) + 最近告警(Redis) 的合并视图。 */
    @GetMapping("/{id}/shadow")
    public Map<String, Object> shadow(@PathVariable String id) {
        List<Map<String, Object>> rows = mysql.queryForList(
                "SELECT device_id, online, last_seen, msg_count FROM device WHERE device_id = ?", id);
        Map<String, Object> shadow = rows.isEmpty() ? new HashMap<>() : rows.get(0);
        shadow.put("reported", redis.opsForHash().entries("device:latest:" + id));
        shadow.put("lastAlarm", redis.opsForValue().get("device:alarm:" + id));
        return shadow;
    }

    /** 最近告警列表（MySQL alarm 表）。 */
    @GetMapping("/alarms")
    public List<Map<String, Object>> alarms(@RequestParam(defaultValue = "50") int limit) {
        return mysql.queryForList(
                "SELECT id, device_id, metric, value, threshold, level, message, created_at "
                        + "FROM alarm ORDER BY id DESC LIMIT ?", limit);
    }
}
