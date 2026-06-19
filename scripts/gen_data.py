#!/usr/bin/env python3
"""gen_data.py —— 第 2 周：往 TDengine 灌 ~1500 条模拟数据（走 REST，最快）

依赖： pip install requests
运行： python3 scripts/gen_data.py
"""
import requests, random
from datetime import datetime, timedelta

URL  = "http://localhost:6041/rest/sql"   # TDengine REST 端口
AUTH = ("root", "taosdata")

devices = [
    ("d001", "dev-001", "A区-1号田"),
    ("d002", "dev-002", "A区-2号田"),
    ("d003", "dev-003", "B区-1号田"),
]


def run(sql: str):
    r = requests.post(URL, data=sql.encode("utf-8"), auth=AUTH)
    j = r.json()
    if j.get("code", 0) != 0:
        raise RuntimeError(j)
    return j


def main():
    now = datetime.now()
    for tbl, dev, loc in devices:
        rows = []
        for i in range(500):                       # 每设备 500 条，每分钟一条往前推
            ts    = now - timedelta(minutes=500 - i)
            temp  = round(random.uniform(18, 35), 1)
            humi  = round(random.uniform(40, 90), 1)
            light = random.randint(0, 60000)
            ph    = round(random.uniform(5.5, 7.5), 2)
            rows.append(f"('{ts:%Y-%m-%d %H:%M:%S}', {temp}, {humi}, {light}, {ph})")
        sql = (f"INSERT INTO iot.{tbl} USING iot.env_sensor "
               f"TAGS ('{dev}','{loc}','env') VALUES " + " ".join(rows))  # 批量一次写 500 行
        run(sql)
        print(f"{tbl}: inserted {len(rows)} rows")
    print("done. total ~1500 rows")


if __name__ == "__main__":
    main()
