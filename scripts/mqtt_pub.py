#!/usr/bin/env python3
"""mqtt_pub.py —— 第 3 周：向 EMQX 发布模拟设备数据，测试接入链路
依赖： pip install paho-mqtt
运行： python3 scripts/mqtt_pub.py
"""
import json, random, time
import paho.mqtt.publish as publish

for i in range(6):
    dev = f"00{i % 3 + 1}"                      # 轮流 001/002/003
    payload = json.dumps({
        "temp": round(random.uniform(18, 35), 1),
        "humi": round(random.uniform(40, 90), 1),
        "light": random.randint(0, 60000),
        "soilPh": round(random.uniform(5.5, 7.5), 2),
    })
    publish.single(f"device/{dev}/data", payload,
                   hostname="localhost", port=1883, qos=1)
    print("pub", dev, payload)
    time.sleep(0.3)

print("done")
