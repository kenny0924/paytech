#!/usr/bin/env python3
"""mor-test XXL-Job：reconciliation-service 执行器分组与两个任务（对账切片 1，release/xxl-job-reconciliation.md）。

用法（Admin 默认 http://127.0.0.1:28090，口令取 test-environment/.env.test 的 MOR_XXL_JOB_ADMIN_PASSWORD，不回显）：
  python3 -I xxl_reconciliation.py plan                 # 只读：分组 / 任务是否存在、注册地址、启动状态
  python3 -I xxl_reconciliation.py apply                # 幂等：建分组、建两个任务并启动；再次执行全部跳过
  python3 -I xxl_reconciliation.py trigger <handler>    # 手动触发一次（compensationEventScan | channelLineWaitExpiry），打印最近日志结果
  python3 -I xxl_reconciliation.py remove               # 回滚：停止并删除两个任务，再删除分组
照 requirements/20261018/test-environment-V2部署与数据迁移/scripts/register_xxl_executors.py 的 Admin API 用法。
trigger 在执行器注册前会报 "registry address is empty"（执行器心跳约 30 秒），脚本重试至 120 秒。
"""
from __future__ import annotations

import http.cookiejar
import json
import os
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ADMIN = os.environ.get("XXL_ADMIN_URL", "http://127.0.0.1:28090").rstrip("/")
ENV_DIR = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", Path(__file__).resolve().parents[5] / "test-environment"))
APP = "reconciliation-service"
TITLE = "Reconciliation"
EXECUTOR_ADDRESS = "http://172.30.73.41:9999/"
JOBS = [
    ("Reconciliation 本地补偿扫描（compensation_event）", {
        "executorHandler": "compensationEventScan", "scheduleType": "CRON", "scheduleConf": "*/30 * * * * ?",
        "misfireStrategy": "DO_NOTHING", "executorRouteStrategy": "SHARDING_BROADCAST", "executorBlockStrategy": "SERIAL_EXECUTION",
        "executorTimeout": "60", "executorFailRetryCount": "0"}),
    ("Reconciliation 账单行等待期满转异常（UNMATCHED → CHANNEL_LINE_UNMATCHED）", {
        "executorHandler": "channelLineWaitExpiry", "scheduleType": "CRON", "scheduleConf": "0 */10 * * * ?",
        "misfireStrategy": "DO_NOTHING", "executorRouteStrategy": "FIRST", "executorBlockStrategy": "SERIAL_EXECUTION",
        "executorTimeout": "300", "executorFailRetryCount": "0"}),
]


def admin_password() -> str:
    for line in (ENV_DIR / ".env.test").read_text().splitlines():
        if line.startswith("MOR_XXL_JOB_ADMIN_PASSWORD="):
            return line.split("=", 1)[1]
    raise SystemExit("MOR_XXL_JOB_ADMIN_PASSWORD missing in .env.test")


class Admin:
    def __init__(self) -> None:
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.post("/auth/doLogin", {"userName": "admin", "password": admin_password()})

    def post(self, path: str, fields, check: bool = True) -> dict:
        body = urllib.parse.urlencode(fields).encode()
        with self.opener.open(urllib.request.Request(ADMIN + path, data=body, method="POST"), timeout=15) as response:
            result = json.loads(response.read().decode())
        if check and result.get("code") != 200:
            raise SystemExit(f"{path} failed: code={result.get('code')} msg={result.get('msg')}")
        return result

    def group(self):
        data = self.post("/jobgroup/pageList", {"offset": 0, "pagesize": 200, "appname": APP})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        return next((row for row in rows if row["appname"] == APP), None)

    def jobs(self, group_id: int) -> list:
        data = self.post("/jobinfo/pageList", {"jobGroup": group_id, "triggerStatus": -1, "jobDesc": "", "executorHandler": "",
                                                "author": "", "offset": 0, "pagesize": 200})["data"]
        return data["data"] if isinstance(data, dict) else data


def plan(admin: Admin, apply: bool) -> None:
    group = admin.group()
    if group is None:
        print(f"[group] {APP}: MISSING")
        if not apply:
            return
        admin.post("/jobgroup/insert", {"appname": APP, "title": TITLE, "addressType": 0, "addressList": ""})
        group = admin.group()
        print(f"[group] {APP}: CREATED id={group['id']}")
    else:
        print(f"[group] {APP}: EXISTS id={group['id']} addressList={group.get('addressList')} registry={group.get('registryList')}")
    for desc, shape in JOBS:
        handler = shape["executorHandler"]
        existing = [j for j in admin.jobs(group["id"]) if j["executorHandler"] == handler]
        if len(existing) > 1:
            raise SystemExit(f"{handler}: {len(existing)} jobs, expected at most 1")
        if not existing:
            print(f"  [job] {handler}: MISSING")
            if not apply:
                continue
            fields = {"jobGroup": group["id"], "jobDesc": desc, "author": "code-mor", "alarmEmail": "", "executorParam": "",
                      "glueType": "BEAN", "glueRemark": "BEAN", "glueSource": "", "childJobId": "", **shape}
            admin.post("/jobinfo/insert", fields)
            existing = [j for j in admin.jobs(group["id"]) if j["executorHandler"] == handler]
            print(f"  [job] {handler}: CREATED id={existing[0]['id']}")
        job = existing[0]
        if job["triggerStatus"] == 1:
            print(f"  [job] {handler}: RUNNING id={job['id']} cron={job['scheduleConf']} route={job['executorRouteStrategy']}")
        elif apply:
            admin.post("/jobinfo/start", [("ids[]", job["id"])])
            print(f"  [job] {handler}: STARTED id={job['id']}")
        else:
            print(f"  [job] {handler}: STOPPED id={job['id']}")


def trigger(admin: Admin, handler: str) -> None:
    group = admin.group() or sys.exit("group missing")
    job = next((j for j in admin.jobs(group["id"]) if j["executorHandler"] == handler), None) or sys.exit(f"{handler} missing")
    deadline = time.time() + 120
    while True:
        result = admin.post("/jobinfo/trigger", {"id": job["id"], "executorParam": "", "addressList": ""}, check=False)
        if result.get("code") == 200:
            break
        if time.time() > deadline:
            raise SystemExit(f"trigger failed: {result.get('msg')}")
        print(f"  trigger not ready ({result.get('msg')}), retry in 10 s")
        time.sleep(10)
    print(f"[trigger] {handler} id={job['id']} accepted at {time.strftime('%H:%M:%SZ', time.gmtime())}")
    time.sleep(8)
    logs = admin.post("/joblog/pageList", {"jobGroup": group["id"], "jobId": job["id"], "logStatus": -1, "filterTime": "",
                                           "offset": 0, "pagesize": 3}, check=False)
    data = logs.get("data") or {}
    rows = data.get("data") if isinstance(data, dict) else data
    for row in rows or []:
        print(f"  log id={row.get('id')} trigger={row.get('triggerCode')} handle={row.get('handleCode')} msg={str(row.get('handleMsg'))[:200]}")


def remove(admin: Admin) -> None:
    group = admin.group()
    if group is None:
        print("[group] missing; nothing to remove")
        return
    for job in admin.jobs(group["id"]):
        admin.post("/jobinfo/stop", [("ids[]", job["id"])], check=False)
        removed = admin.post("/jobinfo/remove", [("ids[]", job["id"])], check=False)
        if removed.get("code") != 200:  # 旧版 Admin 用单个 id
            admin.post("/jobinfo/remove", {"id": job["id"]})
        print(f"  [job] {job['executorHandler']}: REMOVED id={job['id']}")
    removed = admin.post("/jobgroup/remove", [("ids[]", group["id"])], check=False)
    if removed.get("code") != 200:
        admin.post("/jobgroup/remove", {"id": group["id"]})
    print(f"[group] {APP}: REMOVED id={group['id']}")


def main() -> None:
    if len(sys.argv) < 2 or sys.argv[1] not in ("plan", "apply", "trigger", "remove"):
        raise SystemExit(__doc__)
    admin = Admin()
    mode = sys.argv[1]
    if mode in ("plan", "apply"):
        plan(admin, mode == "apply")
    elif mode == "trigger":
        trigger(admin, sys.argv[2])
    else:
        remove(admin)


if __name__ == "__main__":
    main()
