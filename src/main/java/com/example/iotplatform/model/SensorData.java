package com.example.iotplatform.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 设备上报的传感数据。对应 MQTT payload JSON，例如：
 * {"temp":25.6,"humi":60.2,"light":32000,"soilPh":6.5}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SensorData {
    private Double temp;      // 空气温度
    private Double humi;      // 空气湿度
    private Integer light;    // 光照度
    private Double soilPh;    // 土壤 PH（JSON key: soilPh）
    private String location;  // 可选：安装位置
    private String devType;   // 可选：设备类型
}
