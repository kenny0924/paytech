#!/usr/bin/env python3
"""工作流体验优化 mor-test 冒烟（只读；改编自 20261021/platform-support-工作流/release/acceptance/wf7_smoke.py，
加「我发起的」菜单，路径深度按本目录调整；不调用已删除的重新验证路由）：经运营入口 http://localhost:28080 → Gateway → ops-bff → platform-support。

用法：python3 -I requirements/20261022/platform-support-工作流体验优化/release/acceptance/wfux_smoke.py [--before]
  --before：替换之前跑：断言 4 个旧菜单存在、「我发起的」尚不存在，并做同期回归。
管理员账号取 test-environment/private/access.md，口令不打印。全部串行（Gateway 每人并发 2）；429 按 Retry-After 重试。
不发任何写请求（登录除外）。结果打印到 stdout，退出码 0 = 全部通过。
"""
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
ACCESS = ROOT / "test-environment/private/access.md"
ORIGIN = "http://localhost:28080"
PREFIX = "/api/operations"
WORKFLOW_COMPONENTS = ("OPERATIONS_WORKFLOW_MY_TASKS", "OPERATIONS_WORKFLOW_STARTED", "OPERATIONS_WORKFLOW_INSTANCES",
                       "OPERATIONS_WORKFLOW_DEFINITIONS", "OPERATIONS_WORKFLOW_ACTION_TEMPLATES")
WORKFLOW_READS = (
    "/workflow-admin/v1/action-templates?page=1&size=20",
    "/workflow-admin/v1/definitions?page=1&size=20",
    "/workflow/v1/launchable-definitions?page=1&size=20",
    "/workflow/v1/instances?scope=MINE&page=1&size=20",
    "/workflow/v1/instances?scope=ALL&page=1&size=20",
    "/workflow/v1/my-tasks?page=1&size=20",
    "/workflow/v1/my-tasks/handled?page=1&size=20",
    "/workflow/v1/assignee-candidates?keyword=a",
)
# 同期服务回归（同一组 Platform / ops-bff / ops-web）：数据查询、文件存储、渠道。
REGRESSION_READS = (
    "/data-query-admin/v1/data-sources?page=1&size=20",
    "/storage-admin/v1/instances",
    "/storage-admin/v1/scan-policies",
    "/channel-admin/v1/channels?page=1&size=20",
)
results = []


def call(token, method, path, body=None):
    headers = {"Origin": ORIGIN, "Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    for attempt in range(10):
        request = urllib.request.Request(ORIGIN + PREFIX + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                text = response.read().decode()
                return response.status, text
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 9:
                time.sleep(int(failure.headers.get("Retry-After") or 3))
                continue
            return failure.code, failure.read().decode()[:300]
    return 0, ""


def check(name, ok, evidence=""):
    results.append((name, bool(ok)))
    print(("PASS " if ok else "FAIL ") + name + (f"  [{evidence}]" if evidence else ""))


def main():
    before = "--before" in sys.argv
    text = ACCESS.read_text()
    identifier = re.search(r"账号：(\S+)", text).group(1)
    password = re.search(r"密码：(\S+)", text).group(1)
    status, body = call(None, "POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password})
    check("login", status == 200, f"HTTP {status}")
    if status != 200:
        return 1
    token = json.loads(body)["accessToken"]
    status, nav = call(token, "GET", "/authorization/v1/navigation")
    check("navigation 200", status == 200, f"HTTP {status}")
    present = [key for key in WORKFLOW_COMPONENTS if key in nav]
    if before:
        check("navigation has 4 old workflow menus, no 我发起的", len(present) == 4 and "OPERATIONS_WORKFLOW_STARTED" not in present,
              ",".join(present))
    else:
        check("navigation has 工作流 + 5 menus (incl. 我发起的)", len(present) == 5 and "工作流" in nav, ",".join(present))
    if True:
        for path in WORKFLOW_READS:
            status, text = call(token, "GET", path)
            check("GET " + path, status == 200, f"HTTP {status} {'' if status == 200 else text[:120]}")
    for path in REGRESSION_READS:
        status, text = call(token, "GET", path)
        check("regression GET " + path, status == 200, f"HTTP {status} {'' if status == 200 else text[:120]}")
    failed = [name for name, ok in results if not ok]
    print(f"\n{len(results) - len(failed)}/{len(results)} passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
