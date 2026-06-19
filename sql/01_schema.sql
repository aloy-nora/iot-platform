# 01_schema.sql —— 第 2 周：建库 / 超级表 / 子表
# 导入方式（taos 脚本模式）：
#   docker cp sql/01_schema.sql iot-tdengine:/tmp/01_schema.sql
#   docker exec iot-tdengine taos -f /tmp/01_schema.sql
# 注意：taos 脚本用 # 注释（不认 --）；每条语句写在一行、勿用行内注释，
#       否则会被误判为 "Incomplete SQL statement"。

# 建库
CREATE DATABASE IF NOT EXISTS iot;
USE iot;

# 超级表 env_sensor：列 = 测点(随时间变)；TAGS = 设备静态属性(按 tag 过滤/聚合极快)
CREATE STABLE IF NOT EXISTS env_sensor (ts TIMESTAMP, temp FLOAT, humi FLOAT, light INT, soil_ph FLOAT) TAGS (device_id NCHAR(32), location NCHAR(64), dev_type NCHAR(16));

# 子表：一台真实设备 = 一张子表
CREATE TABLE IF NOT EXISTS d001 USING env_sensor TAGS ('dev-001', 'A区-1号田', 'env');
CREATE TABLE IF NOT EXISTS d002 USING env_sensor TAGS ('dev-002', 'A区-2号田', 'env');
CREATE TABLE IF NOT EXISTS d003 USING env_sensor TAGS ('dev-003', 'B区-1号田', 'env');

# 手动插一条，理解"插入即写入对应子表"
INSERT INTO d001 VALUES (NOW, 25.6, 60.2, 32000, 6.5);

# 子表不存在时，可在 INSERT 里用超级表"自动建表 + 写入"（第 3 周 Spring Boot 落库靠这招）
INSERT INTO d004 USING env_sensor TAGS ('dev-004', 'B区-2号田', 'env') VALUES (NOW, 24.1, 58.0, 28000, 6.8);

# 查看结构与数据
SHOW STABLES;
SHOW TABLES;
DESCRIBE env_sensor;
SELECT * FROM env_sensor;
