#!/usr/bin/env python3
"""mor-test XXL-Job：REQ-05 工作流 DATA_QUERY 定时重查任务 workflowDataQueryRecheck（执行器分组 2 platform-support-service，已存在、不新建）。

用法（Admin 默认 http://127.0.0.1:28090，口令取 test-environment/.env.test 的 MOR_XXL_JOB_ADMIN_PASSWORD，不回显）：
  python3 -I xxl_workflow.py plan       # 只读：分组在线地址、任务是否存在 / 启动状态
  python3 -I xxl_workflow.py apply      # 幂等：登记任务并启动；再次执行全部跳过（同一 Handler 已有多条即停）
  python3 -I xxl_workflow.py trigger    # 手动触发一次，打印最近 3 条日志（handle 200 与摘要 "workflow data-query recheck: checked …"）
  python3 -I xxl_workflow.py remove     # 回滚：停止并删除本任务（不动分组与任务 2 compensationEventScan）
时机：Platform 新镜像起来后，xxl_job_registry 中 platform-support-service 心跳晚于新容器启动（约 30 秒）。旧 Platform 镜像没有该 Handler，
回滚 Platform 之前必须先 remove。改写自 requirements/20261020/reconciliation-渠道资金对账切片1/release/scripts/xxl_reconciliation.py。
参数依据 technique.md 10.4 与 WorkflowDataQueryRecheckJobHandler 类注释：CRON 每 5 分钟（UTC）、SHARDING_BROADCAST、DISCARD_LATER、超时 300 秒、参数空。
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
APP = "platform-support-service"
EXPECTED_GROUP_ID = 2
HANDLER = "workflowDataQueryRecheck"
JOB = {"jobDesc": "Workflow DATA_QUERY 系统检查定时重查（REQ-05）", "author": "code-mor", "alarmEmail": "",
       "scheduleType": "CRON", "scheduleConf": "0 */5 * * * ?", "misfireStrategy": "DO_NOTHING",
       "executorRouteStrategy": "SHARDING_BROADCAST", "executorHandler": HANDLER, "executorParam": "",
       "executorBlockStrategy": "DISCARD_LATER", "executorTimeout": "300", "executorFailRetryCount": "0",
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

    def group(self) -> dict:
        data = self.post("/jobgroup/pageList", {"offset": 0, "pagesize": 200, "appname": APP})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        group = next((row for row in rows if row["appname"] == APP), None)
        if group is None or group["id"] != EXPECTED_GROUP_ID:
            raise SystemExit(f"executor group {APP} expected id {EXPECTED_GROUP_ID}, got {group and group['id']}; stop")
        return group

    def jobs(self, group_id: int) -> list:
        data = self.post("/jobinfo/pageList", {"jobGroup": group_id, "triggerStatus": -1, "jobDesc": "", "executorHandler": "",
                                                "author": "", "offset": 0, "pagesize": 200})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        return [j for j in rows if j["executorHandler"] == HANDLER]


def plan(admin: Admin, apply: bool) -> None:
    group = admin.group()
    print(f"[group] {APP}: id={group['id']} addressList={group.get('addressList')} registry={group.get('registryList')}")
    existing = admin.jobs(group["id"])
    if len(existing) > 1:
        raise SystemExit(f"{HANDLER}: {len(existing)} jobs, expected at most 1")
    if not existing:
        print(f"  [job] {HANDLER}: MISSING")
        if not apply:
            print("  would insert:", json.dumps({"jobGroup": group["id"], **JOB}, ensure_ascii=False))
            return
        admin.post("/jobinfo/insert", {"jobGroup": group["id"], **JOB})
        existing = admin.jobs(group["id"])
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
    group = admin.group()
    job = next(iter(admin.jobs(group["id"])), None) or sys.exit(f"{HANDLER} missing")
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
    for job in admin.jobs(group["id"]):
        admin.post("/jobinfo/stop", [("ids[]", job["id"])], check=False)
        removed = admin.post("/jobinfo/remove", [("ids[]", job["id"])], check=False)
        if removed.get("code") != 200:  # 旧版 Admin 用单个 id
            admin.post("/jobinfo/remove", {"id": job["id"]})
        print(f"  [job] {HANDLER}: REMOVED id={job['id']}")


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in ("plan", "apply", "trigger", "remove"):
        raise SystemExit(__doc__)
    admin = Admin()
    {"plan": lambda: plan(admin, False), "apply": lambda: plan(admin, True), "trigger": lambda: trigger(admin),
     "remove": lambda: remove(admin)}[sys.argv[1]]()


if __name__ == "__main__":
    main()
