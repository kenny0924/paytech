#!/usr/bin/env python3
"""mor-test XXL-Job：payout-service 执行器分组（新建）与补偿扫描任务 compensationEventScan（payout S1 runbook）。

用法（Admin 默认 http://127.0.0.1:28090，口令取 test-environment/.env.test 的 MOR_XXL_JOB_ADMIN_PASSWORD，不回显）：
  python3 -I xxl_payout.py plan       # 只读：分组是否存在 / 在线地址、任务是否存在 / 启动状态（会登录 Admin）
  python3 -I xxl_payout.py apply      # 幂等：缺分组则建（appname payout-service、自动注册），缺任务则登记并启动；再次执行全部跳过
  python3 -I xxl_payout.py trigger    # 手动触发一次（执行器注册前会重试 2 分钟），打印最近 3 条日志（handle 200）
  python3 -I xxl_payout.py remove     # 回滚：停止并删除本任务，再删除分组（分组下还有其他任务时只删任务）
时机：payout-service 起来后；address_list 在下一个注册周期（约 30 秒）出现 http://172.30.73.44:9999/。停 payout-service 之前先 remove。
参数照抄现有 compensationEventScan（如任务 28 Tax、13 Account）：CRON */30 * * * * ?（UTC）、SHARDING_BROADCAST、SERIAL_EXECUTION、
超时 60 秒、重试 0、DO_NOTHING、author code-mor。改写自 requirements/20261029/tax-结账算税与事件登记/release/scripts/xxl_tax.py（只改常量）。
阶段 6 只读读数（SQL，2026-10-30）：xxl_job_group 最大 id 18（tax-service）、无 payout-service；xxl_job_info 最大 id 28。
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
ENV_DIR = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", "/Users/kenny/Desktop/Workspace/Code-MoR/test-environment"))
APP = "payout-service"
GROUP = {"appname": APP, "title": "Payout", "addressType": "0", "addressList": ""}
HANDLER = "compensationEventScan"
JOB = {"jobDesc": "Payout 本地补偿扫描（compensation_event：收款账户状态通知重试）", "author": "code-mor", "alarmEmail": "",
       "scheduleType": "CRON", "scheduleConf": "*/30 * * * * ?", "misfireStrategy": "DO_NOTHING",
       "executorRouteStrategy": "SHARDING_BROADCAST", "executorHandler": HANDLER, "executorParam": "",
       "executorBlockStrategy": "SERIAL_EXECUTION", "executorTimeout": "60", "executorFailRetryCount": "0",
       "glueType": "BEAN", "glueRemark": "BEAN", "glueSource": "", "childJobId": ""}


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

    def group(self) -> dict | None:
        data = self.post("/jobgroup/pageList", {"offset": 0, "pagesize": 200, "appname": APP})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        matched = [row for row in rows if row["appname"] == APP]
        if len(matched) > 1:
            raise SystemExit(f"executor group {APP}: {len(matched)} rows, expected at most 1; stop")
        return matched[0] if matched else None

    def jobs(self, group_id: int) -> list:
        data = self.post("/jobinfo/pageList", {"jobGroup": group_id, "triggerStatus": -1, "jobDesc": "", "executorHandler": "",
                                                "author": "", "offset": 0, "pagesize": 200})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        return rows


def plan(admin: Admin, apply: bool) -> None:
    group = admin.group()
    if group is None:
        print(f"[group] {APP}: MISSING")
        if not apply:
            print("  would insert group:", json.dumps(GROUP, ensure_ascii=False))
            print("  would insert job:", json.dumps(JOB, ensure_ascii=False))
            return
        admin.post("/jobgroup/insert", GROUP)
        group = admin.group() or sys.exit("group insert did not take effect")
        print(f"[group] {APP}: CREATED id={group['id']}")
    else:
        print(f"[group] {APP}: id={group['id']} addressList={group.get('addressList')} registry={group.get('registryList')}")
    existing = [j for j in admin.jobs(group["id"]) if j["executorHandler"] == HANDLER]
    if len(existing) > 1:
        raise SystemExit(f"{HANDLER}: {len(existing)} jobs, expected at most 1")
    if not existing:
        print(f"  [job] {HANDLER}: MISSING")
        if not apply:
            print("  would insert:", json.dumps({"jobGroup": group["id"], **JOB}, ensure_ascii=False))
            return
        admin.post("/jobinfo/insert", {"jobGroup": group["id"], **JOB})
        existing = [j for j in admin.jobs(group["id"]) if j["executorHandler"] == HANDLER]
        print(f"  [job] {HANDLER}: CREATED id={existing[0]['id']}")
    job = existing[0]
    if job["triggerStatus"] == 1:
        print(f"  [job] {HANDLER}: RUNNING id={job['id']} cron={job['scheduleConf']} route={job['executorRouteStrategy']}")
    elif apply:
        admin.post("/jobinfo/start", [("ids[]", job["id"])])
        print(f"  [job] {HANDLER}: STARTED id={job['id']}")
    else:
        print(f"  [job] {HANDLER}: STOPPED id={job['id']}")


def trigger(admin: Admin) -> None:
    group = admin.group() or sys.exit(f"group {APP} missing")
    job = next((j for j in admin.jobs(group["id"]) if j["executorHandler"] == HANDLER), None) or sys.exit(f"{HANDLER} missing")
    deadline = time.time() + 120
    while True:
        result = admin.post("/jobinfo/trigger", {"id": job["id"], "executorParam": "", "addressList": ""}, check=False)
        if result.get("code") == 200:
            break
        if time.time() > deadline:
            raise SystemExit(f"trigger failed: {result.get('msg')}")
        print(f"  trigger not ready ({result.get('msg')}), retry in 10 s")
        time.sleep(10)
    print(f"[trigger] {HANDLER} id={job['id']} accepted at {time.strftime('%H:%M:%SZ', time.gmtime())}")
    time.sleep(10)
    logs = admin.post("/joblog/pageList", {"jobGroup": group["id"], "jobId": job["id"], "logStatus": -1, "filterTime": "",
                                           "offset": 0, "pagesize": 3}, check=False)
    data = logs.get("data") or {}
    rows = data.get("data") if isinstance(data, dict) else data
    for row in rows or []:
        print(f"  log id={row.get('id')} trigger={row.get('triggerCode')} handle={row.get('handleCode')} msg={str(row.get('handleMsg'))[:200]}")


def remove(admin: Admin) -> None:
    group = admin.group()
    if group is None:
        print(f"[group] {APP}: absent, nothing to remove")
        return
    for job in [j for j in admin.jobs(group["id"]) if j["executorHandler"] == HANDLER]:
        admin.post("/jobinfo/stop", [("ids[]", job["id"])], check=False)
        removed = admin.post("/jobinfo/remove", [("ids[]", job["id"])], check=False)
        if removed.get("code") != 200:  # 旧版 Admin 用单个 id
            admin.post("/jobinfo/remove", {"id": job["id"]})
        print(f"  [job] {HANDLER}: REMOVED id={job['id']}")
    if admin.jobs(group["id"]):
        print(f"[group] {APP}: other jobs remain, group kept")
        return
    removed = admin.post("/jobgroup/remove", {"id": group["id"]}, check=False)
    print(f"[group] {APP}: remove id={group['id']} code={removed.get('code')} msg={removed.get('msg')}")


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in ("plan", "apply", "trigger", "remove"):
        raise SystemExit(__doc__)
    admin = Admin()
    {"plan": lambda: plan(admin, False), "apply": lambda: plan(admin, True), "trigger": lambda: trigger(admin),
     "remove": lambda: remove(admin)}[sys.argv[1]]()


if __name__ == "__main__":
    main()
