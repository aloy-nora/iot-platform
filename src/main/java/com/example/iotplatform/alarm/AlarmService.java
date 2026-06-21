package com.example.iotplatform.alarm;

import com.example.iotplatform.model.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 阈值告警：ingest 时检查温/湿是否越界，越界则落 MySQL alarm 表 + 写 Redis 最新告警。
 * W8 的企业微信推送将挂在这里（notify 方法预留）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmProps props;
    @Qualifier("mysqlJdbcTemplate")
    private final JdbcTemplate mysql;
    private final StringRedisTemplate redis;

    /** 检查一条数据，触发的告警逐条落库。 */
    public void check(String deviceId, SensorData d) {
        List<String[]> hits = new ArrayList<>();   // [metric, value, threshold, message]
        if (d.getTemp() != null) {
            if (d.getTemp() > props.tempMax())
                hits.add(new String[]{"temp", d.getTemp().toString(), ">" + props.tempMax(),
                        "温度过高 " + d.getTemp() + "℃"});
            else if (d.getTemp() < props.tempMin())
                hits.add(new String[]{"temp", d.getTemp().toString(), "<" + props.tempMin(),
                        "温度过低 " + d.getTemp() + "℃"});
        }
        if (d.getHumi() != null) {
            if (d.getHumi() > props.humiMax())
                hits.add(new String[]{"humi", d.getHumi().toString(), ">" + props.humiMax(),
                        "湿度过高 " + d.getHumi() + "%"});
            else if (d.getHumi() < props.humiMin())
                hits.add(new String[]{"humi", d.getHumi().toString(), "<" + props.humiMin(),
                        "湿度过低 " + d.getHumi() + "%"});
        }
        for (String[] h : hits) {
            save(deviceId, h[0], Double.parseDouble(h[1]), h[2], h[3]);
        }
    }

    private void save(String deviceId, String metric, double value, String threshold, String message) {
        mysql.update("INSERT INTO alarm(device_id, metric, value, threshold, level, message) VALUES(?,?,?,?,?,?)",
                deviceId, metric, value, threshold, "WARN", message);
        redis.opsForValue().set("device:alarm:" + deviceId, message);
        log.warn("ALARM device={} {}", deviceId, message);
        notify(deviceId, message);
    }

    /** W8 预留：企业微信/钉钉推送。当前仅占位。 */
    private void notify(String deviceId, String message) {
        // TODO W8: push to WeCom robot
    }
}
