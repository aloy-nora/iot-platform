# 02_queries.sql —— 第 2 周：练习查询
# 用法：逐条贴进交互 shell（docker exec -it iot-tdengine taos），看懂每条结果
# 说明：taos 用 # 注释（不认 --）；语句尽量写一行，避免多行/行内注释出错

USE iot;

# 1) 每个设备的最新值（实时大盘就靠它）
SELECT tbname, LAST(ts) AS ts, LAST(temp) AS temp, LAST(humi) AS humi FROM env_sensor GROUP BY tbname;

# 2) 某设备每 10 分钟平均温度（时间窗口 INTERVAL）
SELECT _wstart, AVG(temp) AS avg_temp FROM d001 INTERVAL(10m);

# 3) 所有设备、按设备分组、每小时均值（PARTITION BY + INTERVAL）
SELECT tbname, _wstart, AVG(temp) AS avg_temp, AVG(humi) AS avg_humi FROM env_sensor PARTITION BY tbname INTERVAL(1h);

# 4) 最近 2 小时的整体极值（时间范围筛选 + 聚合）
SELECT AVG(temp) AS avg_t, MAX(temp) AS max_t, MIN(temp) AS min_t FROM env_sensor WHERE ts >= NOW - 2h;

# 5) 滑动窗口：每 30 分钟窗口、每 10 分钟滑动一次
SELECT _wstart, AVG(temp) AS avg_temp FROM d001 INTERVAL(30m) SLIDING(10m);

# 6) 按 tag 过滤（A 区所有田）—— 体会 tag 的威力
SELECT AVG(temp) AS a_zone_avg FROM env_sensor WHERE location LIKE 'A区%';

# 7) 每个设备数据条数
SELECT tbname, COUNT(*) FROM env_sensor GROUP BY tbname;
