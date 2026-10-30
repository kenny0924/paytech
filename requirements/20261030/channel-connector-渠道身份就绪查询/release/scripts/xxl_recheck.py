#!/usr/bin/env python3
"""mor-test XXL-Job：手动触发一次 commercial 前置门禁全量重查（REQ-02 runbook「发布后重查」，technique 10.3 / R4）。

用法（Admin 默认 http://127.0.0.1:28090，口令取 test-environment/.env.test 的 MOR_XXL_JOB_ADMIN_PASSWORD，不回显）：
  python3 -I xxl_recheck.py plan       # 只读：commercial-service 执行器分组在线地址、两个 capabilityPrerequisiteRecheck 任务（会登录 Admin）
  python3 -I xxl_recheck.py trigger    # 触发一次 scope=ALL 的任务（含 ENABLED），打印最近 3 条日志
不新建、不改任务；只用现有任务（阶段 6 只读读数：id 25 scope=PENDING 每 5 分钟、id 26 scope=ALL 每日 03:00 UTC，均在 commercial-service 分组 14）。
参数 scope=ALL 与 CapabilityPrerequisiteRecheckJobHandler.SCOPE_ALL 一致；触发参数显式传入，不依赖任务保存的参数。
改写自 payout release/scripts/xxl_payout.py（只保留登录、查询与触发）。
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
APP = "commercial-service"
HANDLER = "capabilityPrerequisiteRecheck"
SCOPE_ALL = "scope=ALL"


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

    def group(self) -> dict:
        data = self.post("/jobgroup/pageList", {"offset": 0, "pagesize": 200, "appname": APP})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        matched = [row for row in rows if row["appname"] == APP]
        if len(matched) != 1:
            raise SystemExit(f"executor group {APP}: {len(matched)} rows, expected exactly 1; stop")
        return matched[0]

    def jobs(self, group_id: int) -> list:
        data = self.post("/jobinfo/pageList", {"jobGroup": group_id, "triggerStatus": -1, "jobDesc": "", "executorHandler": "",
                                                "author": "", "offset": 0, "pagesize": 200})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        return [row for row in rows if row["executorHandler"] == HANDLER]


def plan(admin: Admin) -> dict:
    group = admin.group()
    print(f"[group] {APP}: id={group['id']} addressList={group.get('addressList')} registry={group.get('registryList')}")
    jobs = admin.jobs(group["id"])
    for job in jobs:
        print(f"  [job] id={job['id']} param={job['executorParam']!r} cron={job['scheduleConf']} route={job['executorRouteStrategy']} "
              f"status={'RUNNING' if job['triggerStatus'] == 1 else 'STOPPED'}")
    full = [job for job in jobs if (job.get("executorParam") or "").strip() == SCOPE_ALL]
    if len(full) != 1:
        raise SystemExit(f"expected exactly one {HANDLER} job with {SCOPE_ALL}, found {len(full)}; stop")
    return {"group": group, "job": full[0]}


def trigger(admin: Admin) -> None:
    found = plan(admin)
    group, job = found["group"], found["job"]
    deadline = time.time() + 120
    while True:
        result = admin.post("/jobinfo/trigger", {"id": job["id"], "executorParam": SCOPE_ALL, "addressList": ""}, check=False)
        if result.get("code") == 200:
            break
        if time.time() > deadline:
            raise SystemExit(f"trigger failed: {result.get('msg')}")
        print(f"  trigger not ready ({result.get('msg')}), retry in 10 s")
        time.sleep(10)
    print(f"[trigger] {HANDLER} id={job['id']} param={SCOPE_ALL} accepted at {time.strftime('%H:%M:%SZ', time.gmtime())}")
    time.sleep(15)
    logs = admin.post("/joblog/pageList", {"jobGroup": group["id"], "jobId": job["id"], "logStatus": -1, "filterTime": "",
                                           "offset": 0, "pagesize": 3}, check=False)
    data = logs.get("data") or {}
    rows = data.get("data") if isinstance(data, dict) else data
    for row in rows or []:
        print(f"  log id={row.get('id')} trigger={row.get('triggerCode')} handle={row.get('handleCode')} msg={str(row.get('handleMsg'))[:300]}")


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in ("plan", "trigger"):
        raise SystemExit(__doc__)
    admin = Admin()
    plan(admin) if sys.argv[1] == "plan" else trigger(admin)


if __name__ == "__main__":
    main()
