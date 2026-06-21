-- 04_alarm_schema.sql —— 第 7 周：告警记录表
-- 导入：docker exec -i iot-mysql mysql -uroot -pexample123 iot < sql/04_alarm_schema.sql

CREATE TABLE IF NOT EXISTS alarm (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  device_id   VARCHAR(64)  NOT NULL          COMMENT '设备号',
  metric      VARCHAR(32)  NOT NULL          COMMENT '指标(temp/humi/...)',
  value       DOUBLE       DEFAULT NULL       COMMENT '触发值',
  threshold   VARCHAR(64)  DEFAULT NULL       COMMENT '越界描述(如 >35)',
  level       VARCHAR(16)  DEFAULT 'WARN'     COMMENT '级别',
  message     VARCHAR(255) DEFAULT NULL       COMMENT '告警内容',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_device_time (device_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备告警记录';
