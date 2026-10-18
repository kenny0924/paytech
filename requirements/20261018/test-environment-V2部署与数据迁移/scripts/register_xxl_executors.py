#!/usr/bin/env python3
"""mor-test XXL-Job：为持有补偿事件目录的服务登记执行器分组与扫描任务（任务卡 03）。

背景：阶段 2 只登记了 platform-support-service 与 acquiring-service；其余 8 个服务的
t_compensation_event 进入 ERROR 后没有扫描唤醒，永不重试。

用法（$REPO 为主仓库根目录）：
  python3 register_xxl_executors.py plan    # 只读：列出缺失的分组 / 任务与启动状态
  python3 register_xxl_executors.py apply   # 经 Admin API 幂等创建并启动；再次执行全部跳过

口令：Admin 登录口令取自 $MOR_TEST_ENVIRONMENT_DIR/.env.test（默认主仓库 test-environment）的
MOR_XXL_JOB_ADMIN_PASSWORD，只在内存中使用，不回显。Admin 地址默认 http://127.0.0.1:28090。

任务形状与 acquiring / platform-support 的 compensationEventScan 相同：BEAN、CRON */30 * * * * ?、
分片广播、单机串行、超时 60 秒、失败不重试、misfire DO_NOTHING。card 另登记 cardOrphanInstrumentPurge
（每小时、FIRST 路由、单机串行、超时 300 秒；Handler 不感知分片，多实例时只需一个执行）。
account 的 accountBalanceReconciliation 不在本任务范围，不登记。

回滚（Admin 页面或 API）：停止并删除下列分组中的任务（/jobinfo/stop、/jobinfo/remove），再删除分组
（/jobgroup/remove）；platform-support-service、acquiring-service、commerce-service 不受影响。
"""
from __future__ import annotations

import http.cookiejar
import json
import os
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ADMIN = os.environ.get("XXL_ADMIN_URL", "http://127.0.0.1:28090").rstrip("/")
ENV_DIR = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", "/Users/kenny/Desktop/Workspace/Code-MoR/test-environment"))

COMPENSATION_SCAN = {
    "executorHandler": "compensationEventScan",
    "scheduleType": "CRON",
    "scheduleConf": "*/30 * * * * ?",
    "misfireStrategy": "DO_NOTHING",
    "executorRouteStrategy": "SHARDING_BROADCAST",
    "executorBlockStrategy": "SERIAL_EXECUTION",
    "executorTimeout": "60",
    "executorFailRetryCount": "0",
}
CARD_PURGE = {
    "executorHandler": "cardOrphanInstrumentPurge",
    "scheduleType": "CRON",
    "scheduleConf": "0 0 * * * ?",
    "misfireStrategy": "DO_NOTHING",
    "executorRouteStrategy": "FIRST",
    "executorBlockStrategy": "SERIAL_EXECUTION",
    "executorTimeout": "300",
    "executorFailRetryCount": "0",
}

# (app_name, title, [(job_desc, job_shape)])
EXECUTORS = [
    ("client-master-service", "Client Master", [("Client Master 本地补偿扫描（compensation_event）", COMPENSATION_SCAN)]),
    ("compliance-service", "Compliance", [("Compliance 本地补偿扫描（compensation_event）", COMPENSATION_SCAN)]),
    ("risk-decision-service", "Risk Decision", [("Risk Decision 本地补偿扫描（compensation_event）", COMPENSATION_SCAN)]),
    ("consumer-service", "Consumer", [("Consumer 本地补偿扫描（compensation_event）", COMPENSATION_SCAN)]),
    ("payment-service", "Payment", [("Payment 本地补偿扫描（compensation_event）", COMPENSATION_SCAN)]),
    ("card-service", "Card", [("Card 本地补偿扫描（compensation_event）", COMPENSATION_SCAN),
                              ("Card 孤立交易工具回收（过期超过宽限期、从未签票、非已存卡来源）", CARD_PURGE)]),
    ("billing-service", "Billing", [("Billing 本地补偿扫描（compensation_event）", COMPENSATION_SCAN)]),
    ("account-service", "Account", [("Account 本地补偿扫描（compensation_event）", COMPENSATION_SCAN)]),
]


def read_admin_password() -> str:
    for line in (ENV_DIR / ".env.test").read_text().splitlines():
        if line.startswith("MOR_XXL_JOB_ADMIN_PASSWORD="):
            return line.split("=", 1)[1]
    raise SystemExit("MOR_XXL_JOB_ADMIN_PASSWORD missing in .env.test")


class Admin:
    def __init__(self) -> None:
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def post(self, path: str, fields) -> dict:
        body = urllib.parse.urlencode(fields).encode()
        with self.opener.open(urllib.request.Request(ADMIN + path, data=body, method="POST"), timeout=10) as response:
            result = json.loads(response.read().decode())
        if result.get("code") != 200:
            raise SystemExit(f"{path} failed: code={result.get('code')} msg={result.get('msg')}")
        return result

    def login(self) -> None:
        self.post("/auth/doLogin", {"userName": "admin", "password": read_admin_password()})

    def groups(self) -> dict:
        data = self.post("/jobgroup/pageList", {"offset": 0, "pagesize": 200})["data"]
        rows = data["data"] if isinstance(data, dict) else data
        return {row["appname"]: row for row in rows}

    def jobs(self, group_id: int) -> list:
        data = self.post("/jobinfo/pageList", {"jobGroup": group_id, "triggerStatus": -1, "jobDesc": "",
                                                "executorHandler": "", "author": "", "offset": 0, "pagesize": 200})["data"]
        return data["data"] if isinstance(data, dict) else data


def job_fields(group_id: int, job_desc: str, shape: dict) -> dict:
    fields = {"jobGroup": group_id, "jobDesc": job_desc, "author": "code-mor", "alarmEmail": "",
              "executorParam": "", "glueType": "BEAN", "glueRemark": "BEAN", "glueSource": "", "childJobId": ""}
    fields.update(shape)
    return fields


def main(mode: str) -> int:
    admin = Admin()
    admin.login()
    groups = admin.groups()
    pending = 0
    for app_name, title, jobs in EXECUTORS:
        group = groups.get(app_name)
        if group is None:
            pending += 1
            print(f"[group] {app_name}: MISSING")
            if mode == "plan":
                for _, shape in jobs:
                    print(f"  [job] {shape['executorHandler']}: MISSING (group absent)")
                continue
            admin.post("/jobgroup/insert", {"appname": app_name, "title": title, "addressType": 0, "addressList": ""})
            group = admin.groups()[app_name]
            print(f"[group] {app_name}: CREATED id={group['id']}")
        else:
            print(f"[group] {app_name}: EXISTS id={group['id']} registry={group.get('registryList') or []}")
        for job_desc, shape in jobs:
            handler = shape["executorHandler"]
            created = False
            existing = [job for job in admin.jobs(group["id"]) if job["executorHandler"] == handler]
            if len(existing) > 1:
                raise SystemExit(f"{app_name}/{handler}: {len(existing)} jobs, expected at most 1; resolve manually")
            if not existing:
                pending += 1
                print(f"  [job] {handler}: MISSING")
                if mode == "plan":
                    continue
                admin.post("/jobinfo/insert", job_fields(group["id"], job_desc, shape))
                created = True
                existing = [job for job in admin.jobs(group["id"]) if job["executorHandler"] == handler]
                print(f"  [job] {handler}: CREATED id={existing[0]['id']}")
            job = existing[0]
            if job["triggerStatus"] == 1:
                print(f"  [job] {handler}: RUNNING id={job['id']} cron={job['scheduleConf']}")
                continue
            if not created:
                pending += 1
            print(f"  [job] {handler}: STOPPED id={job['id']}")
            if mode == "apply":
                admin.post("/jobinfo/start", [("ids[]", job["id"])])
                print(f"  [job] {handler}: STARTED id={job['id']}")
    # 计数口径：缺失的分组、缺失的任务、已存在但停止的任务各计一项；新建任务随后的启动不重复计数。
    print(f"{mode}: {pending} pending item(s)" if mode == "plan" else f"apply: {pending} item(s) applied")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] not in ("plan", "apply"):
        raise SystemExit("usage: register_xxl_executors.py plan|apply")
    sys.exit(main(sys.argv[1]))
