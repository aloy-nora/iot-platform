-- 03_mysql_schema.sql —— 第 3 周：MySQL 设备台账表
-- 导入：docker exec -i iot-mysql mysql -uroot -pexample123 iot < sql/03_mysql_schema.sql

CREATE TABLE IF NOT EXISTS device (
  device_id   VARCHAR(64)  NOT NULL          COMMENT '设备号',
  location    VARCHAR(128) DEFAULT NULL       COMMENT '安装位置',
  dev_type    VARCHAR(32)  DEFAULT NULL       COMMENT '设备类型',
  online      TINYINT(1)   DEFAULT 1          COMMENT '在线状态',
  last_seen   DATETIME     DEFAULT NULL       COMMENT '最近上报时间',
  msg_count   BIGINT       DEFAULT 0          COMMENT '累计上报条数',
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备台账';
