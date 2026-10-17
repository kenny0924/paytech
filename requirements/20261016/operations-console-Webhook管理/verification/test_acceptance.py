"""阶段 7 mor-test 接口验收（D2=A）：经运营入口（Gateway → BFF → Platform）走端点管理与查询。

test 只允许可解析到公网的 https 端点：端点用 IANA 保留域名 example.com，不发送测试事件、不提交业务事件，
不验证送达、签名与 Replay（已在 Webhook 投递 test 验收与本机阶段 5 验证）。
前置：mor_platform 已执行注册块与 release/test-activate.sql，并插入与本机相同的合成租户
（db/platform-support-service/acceptance-fixture-local.sql）。不打印 Token、密钥与凭据。
用法：E2E_TARGET=test python3 -I test_acceptance.py
"""
import json
import re
import subprocess
import sys
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "requirements/20260914/client-console-注册与客户入驻/verification/local-e2e"))
import e2e_http  # noqa: E402

BASE = "/webhook-admin/v1"
CLIENT, PAUSED = "880000000000079101", "880000000000079102"
URL = "https://example.com/paytech-webhook-acceptance"
SECRET = re.compile(r"whsec_[A-Za-z0-9_-]{43}")
START = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
results = []


def check(ac, name, ok):
    results.append(bool(ok))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def status_of(fn):
    try:
        return 200, fn()
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {}


def raw_post(token, path, body):
    """读取响应头（Cache-Control）；返回 (状态, 头, 响应体)。"""
    headers = {"Origin": e2e_http.OPERATIONS, "Accept": "application/json", "Content-Type": "application/json",
               "Authorization": "Bearer " + token, "Idempotency-Key": str(uuid.uuid4())}
    request = urllib.request.Request(e2e_http.OPERATIONS + "/api/operations" + BASE + path,
                                     data=json.dumps(body).encode(), headers=headers, method="POST")
    with urllib.request.urlopen(request, context=e2e_http.TLS, timeout=20) as response:
        return response.status, response.headers, json.loads(response.read().decode())


def wait_until(probe, seconds=90):
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if probe():
            return True
        time.sleep(3)
    return False


def logs(container):
    out = subprocess.run(["docker", "logs", "--since", START, container], capture_output=True, text=True)
    return out.stdout + out.stderr


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    get = lambda path: ops.get(BASE + path)
    send = lambda method, path, body=None: ops.send(method, BASE + path, body, key=True)
    seen = []

    check("AC-01", "Gateway 已加载 Webhook 管理路由",
          wait_until(lambda: status_of(lambda: get("/clients?keyword=E2E%20Webhook"))[0] == 200))
    codes = json.dumps(ops.get("/authorization/v1/navigation"), ensure_ascii=False)
    check("AC-01", "导航含 Webhook 管理菜单与维护按钮", "OPERATIONS_WEBHOOKS" in codes and "OPERATIONS_WEBHOOK_MANAGE" in codes)

    found = get("/clients?keyword=E2E%20Webhook%20Acme")
    check("AC-02", "按名称搜到 2 个合成 Client", found["total"] == 2)
    check("AC-02", "已暂停的 Client 带安全状态",
          next((c for c in found["items"] if c["clientId"] == PAUSED), {}).get("securityStatus") == "SUSPENDED")
    check("AC-02", "按 Client ID 精确匹配", get(f"/clients?keyword={CLIENT}")["total"] == 1)
    check("AC-02", "未开通平台访问的 Client 返回 400", status_of(lambda: get("/clients/880000000000079199/endpoints"))[0] == 400)

    status, headers, created = raw_post(ops.token, f"/clients/{CLIENT}/endpoints",
                                        {"url": URL, "eventTypes": ["order.paid"], "expectedVersion": 0, "reason": "阶段 7 验收"})
    seen.append(created.get("signingSecret", ""))
    check("AC-03", "新建端点 201、返回密钥、no-store", status == 201 and SECRET.fullmatch(created["signingSecret"])
          and "no-store" in (headers.get("Cache-Control") or ""))
    endpoint = created["endpointId"]
    listed = get(f"/clients/{CLIENT}/endpoints")
    check("AC-03", "列表不含密钥明文", len(listed) == 1 and created["signingSecret"] not in json.dumps(listed))

    save = lambda url, types: {"url": url, "eventTypes": types, "expectedVersion": 0, "reason": "阶段 7 验收"}
    check("AC-04", "内网地址被拒 400", status_of(lambda: send("POST", f"/clients/{CLIENT}/endpoints", save("https://10.0.0.1/hook", ["order.paid"])))[0] == 400)
    check("AC-04", "test 不放行 http（400）", status_of(lambda: send("POST", f"/clients/{CLIENT}/endpoints", save("http://example.com/x", ["order.paid"])))[0] == 400)
    check("AC-04", "事件类型为空被拒 422", status_of(lambda: send("POST", f"/clients/{CLIENT}/endpoints", save(URL, [])))[0] == 422)

    updated = send("PUT", f"/endpoints/{endpoint}", {"url": URL, "eventTypes": ["order.paid", "refund.succeeded"],
                                                       "expectedVersion": 1, "reason": "阶段 7 验收"})
    check("AC-05", "修改成功（版本 2）", updated["version"] == 2)
    check("AC-05", "过期版本返回 409", status_of(lambda: send("PUT", f"/endpoints/{endpoint}", {
        "url": URL, "eventTypes": ["order.paid"], "expectedVersion": 1, "reason": "x"}))[0] == 409)
    rotated = send("POST", f"/endpoints/{endpoint}/signing-secret/rotations", {"immediate": False, "reason": "阶段 7 验收"})
    seen.append(rotated["signingSecret"])
    check("AC-06", "宽限轮换：第 2 号且有截止时间", rotated["rotationNo"] == 2 and rotated["previousValidUntil"])
    immediate = send("POST", f"/endpoints/{endpoint}/signing-secret/rotations", {"immediate": True, "reason": "阶段 7 验收"})
    seen.append(immediate["signingSecret"])
    check("AC-06", "立即作废：第 3 号且无宽限", immediate["rotationNo"] == 3 and immediate["previousValidUntil"] is None)
    disabled = send("PATCH", f"/endpoints/{endpoint}/status", {"status": "DISABLED", "expectedVersion": 2, "reason": "阶段 7 验收"})
    check("AC-05", "停用端点（版本 3）", disabled["version"] == 3)

    now = datetime.now(timezone.utc)
    z = lambda t: quote(t.isoformat(timespec="seconds").replace("+00:00", "Z"))
    page = get(f"/deliveries?clientId={CLIENT}&createdFrom={z(now - timedelta(hours=24))}&createdTo={z(now + timedelta(minutes=5))}")
    check("AC-08", "投递列表可查询（合成 Client 无投递）", page["total"] == 0)
    check("AC-08", "时间跨度超过 31 天返回 400", status_of(lambda: get(
        f"/deliveries?clientId={CLIENT}&createdFrom={z(now - timedelta(days=40))}&createdTo={z(now)}"))[0] == 400)
    check("AC-08", "不存在的投递返回 404", status_of(lambda: get("/deliveries/880000000000079999"))[0] == 404)

    text = "".join(logs(c) for c in ["mor-test-platform-support-service-1", "mor-test-operations-console-bff-1", "mor-test-api-gateway-1"])
    check("AC-09", "Platform / BFF / Gateway 日志不含本次密钥明文", all(x and x not in text for x in seen))
    check("AC-09", "Platform / BFF 日志无 ERROR", " ERROR " not in logs("mor-test-platform-support-service-1")
          and " ERROR " not in logs("mor-test-operations-console-bff-1"))
    actions = {row[0] for row in e2e_http.platform_sql(
        "SELECT DISTINCT action_code FROM t_audit_event WHERE action_code LIKE 'OPERATIONS_WEBHOOK%'"
        f" AND occurred_time >= '{START.replace('T', ' ').rstrip('Z')}'")}
    check("AC-09", f"写操作产生审计（{len(actions)} 种）", {"OPERATIONS_WEBHOOK_ENDPOINT_CREATE", "OPERATIONS_WEBHOOK_ENDPOINT_UPDATE",
          "OPERATIONS_WEBHOOK_SECRET_ROTATE", "OPERATIONS_WEBHOOK_ENDPOINT_STATUS_CHANGE"} <= actions)
    print(f"\n{sum(results)}/{len(results)} 通过")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
