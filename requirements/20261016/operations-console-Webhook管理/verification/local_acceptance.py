"""阶段 5 接口验收：经本机 Nginx 运营入口（Gateway → BFF → Platform）走 AC-01～AC-09。

前置：本机 mor_platform_e2e 已执行 Webhook 权限块、Operations Webhook 管理注册块、activation-local.sql 与
acceptance-fixture-local.sql；本机 Platform 开启 webhook.enabled 且放行 http 与 127.0.0.1（run-service.sh）。
脚本在 127.0.0.1:18990 起临时接收端：/hook 返回 204，/fail 返回 503。只连接本机；不打印 Token、密钥与凭据。
结束后执行 acceptance-cleanup-local.sql。用法：python3 -I local_acceptance.py
"""
import hashlib
import json
import re
import sys
import threading
import time
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[4]
HELPER = ROOT / "requirements/20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(HELPER))
import e2e_http  # noqa: E402

BASE = "/webhook-admin/v1"
CLIENT, PAUSED = "880000000000079101", "880000000000079102"
RECEIVER = "http://127.0.0.1:18990"
SECRET = re.compile(r"whsec_[A-Za-z0-9_-]{43}")
RUN = uuid.uuid4().hex[:8]
received = []
results = []


class Receiver(BaseHTTPRequestHandler):
    """临时接收端：记录签名头与 UA，按路径返回 204 或 503。"""

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length") or 0))
        received.append((self.path, self.headers.get("Code-MoR-Signature", ""), self.headers.get("User-Agent", "")))
        self.send_response(204 if self.path.startswith("/hook") else 503)
        self.end_headers()

    def log_message(self, *args):
        pass


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def status_of(fn):
    """执行调用，返回 (HTTP 状态, 响应体)；成功统一记 200。"""
    try:
        return 200, fn()
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {}


def raw_headers(token, method, path, body):
    """直接请求以读取响应头（Cache-Control）；返回 (状态, 头, 响应体)。"""
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Origin": e2e_http.OPERATIONS, "Accept": "application/json", "Content-Type": "application/json",
               "Authorization": "Bearer " + token, "Idempotency-Key": str(uuid.uuid4())}
    request = urllib.request.Request(e2e_http.OPERATIONS + "/api/operations" + BASE + path, data=data,
                                     headers=headers, method=method)
    with urllib.request.urlopen(request, context=e2e_http.TLS, timeout=20) as response:
        return response.status, response.headers, json.loads(response.read().decode())


def wait_until(probe, seconds=60):
    """轮询直到 probe 返回真；超时返回假。"""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if probe():
            return True
        time.sleep(2)
    return False


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()


def submit_event(event_id):
    """以 commerce-service 身份经 Platform 私网受理接口提交 order.paid。"""
    envelope = {"eventId": event_id, "eventType": "order.paid", "schemaVersion": "1",
                "createdAt": "2026-10-17T08:00:00Z",
                "data": {"object": {"orderNo": "ord_" + event_id, "orderStatus": "PAID", "version": 3}}}
    body = {"clientId": CLIENT, "sourceVersion": 3,
            "sourcePayloadHash": hashlib.sha256(canonical(envelope["data"]["object"])).hexdigest(),
            "payloadHash": hashlib.sha256(canonical(envelope)).hexdigest(), "envelope": envelope}
    return e2e_http.platform_call("POST", "/webhooks/v1/events",
                                  {"X-Caller-Service": "commerce-service", "Content-Type": "application/json"},
                                  json.dumps(body))[0]


def window(hours_back=24):
    now = datetime.now(timezone.utc)
    z = lambda t: quote(t.isoformat(timespec="seconds").replace("+00:00", "Z"))
    return f"createdFrom={z(now - timedelta(hours=hours_back))}&createdTo={z(now + timedelta(minutes=5))}"


def main():
    server = ThreadingHTTPServer(("127.0.0.1", 18990), Receiver)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    get = lambda path: ops.get(BASE + path)
    send = lambda method, path, body=None: ops.send(method, BASE + path, body, key=True)
    secrets_seen = []

    # AC-01 路由经 Gateway 快照生效、导航含菜单与按钮
    check("AC-01", "Gateway 已加载 Webhook 管理路由，Client 搜索可访问",
          wait_until(lambda: status_of(lambda: get("/clients?keyword=E2E%20Webhook"))[0] == 200))
    codes = json.dumps(ops.get("/authorization/v1/navigation"), ensure_ascii=False)
    check("AC-01", "导航含 Webhook 管理菜单与维护按钮", "OPERATIONS_WEBHOOKS" in codes and "OPERATIONS_WEBHOOK_MANAGE" in codes)

    # AC-02 Client 选择
    found = get("/clients?keyword=E2E%20Webhook%20Acme")
    check("AC-02", "按名称搜到 2 个合成 Client", found["total"] == 2)
    paused = next((c for c in found["items"] if c["clientId"] == PAUSED), {})
    check("AC-02", "已暂停的 Client 仍列出且带安全状态", paused.get("securityStatus") == "SUSPENDED")
    check("AC-02", "按 Client ID 精确匹配 1 个", get(f"/clients?keyword={CLIENT}")["total"] == 1)
    check("AC-02", "未开通平台访问的 Client 查端点返回 400",
          status_of(lambda: get("/clients/880000000000079199/endpoints"))[0] == 400)

    # AC-03 新建与一次性密钥
    status, headers, created = raw_headers(ops.token, "POST", f"/clients/{CLIENT}/endpoints",
                                           {"url": RECEIVER + "/hook", "eventTypes": ["order.paid"],
                                            "expectedVersion": 0, "reason": "阶段 5 联调"})
    secrets_seen.append(created.get("signingSecret", ""))
    check("AC-03", "新建端点 201、返回密钥、no-store", status == 201 and SECRET.fullmatch(created["signingSecret"])
          and "no-store" in (headers.get("Cache-Control") or ""))
    endpoint = created["endpointId"]
    listed = get(f"/clients/{CLIENT}/endpoints")
    check("AC-03", "列表不含密钥明文", len(listed) == 1 and created["signingSecret"] not in json.dumps(listed))

    # AC-04 地址与订阅校验
    save = lambda url, types: {"url": url, "eventTypes": types, "expectedVersion": 0, "reason": "阶段 5 联调"}
    code, body = status_of(lambda: send("POST", f"/clients/{CLIENT}/endpoints", save("http://10.0.0.1/hook", ["order.paid"])))
    check("AC-04", f"内网地址被拒 400（{body.get('detail')}）", code == 400)
    code, _ = status_of(lambda: send("POST", f"/clients/{CLIENT}/endpoints", save("ftp://hooks.example/x", ["order.paid"])))
    check("AC-04", f"非 http(s) 地址被拒（{code}）", code in (400, 422))
    code, _ = status_of(lambda: send("POST", f"/clients/{CLIENT}/endpoints", save(RECEIVER + "/hook", [])))
    check("AC-04", f"事件类型为空被拒（{code}）", code in (400, 422))
    for i in range(4):
        secrets_seen.append(send("POST", f"/clients/{CLIENT}/endpoints", save(f"{RECEIVER}/hook?n={i}", ["order.paid"]))["signingSecret"])
    code, body = status_of(lambda: send("POST", f"/clients/{CLIENT}/endpoints", save(RECEIVER + "/hook?n=9", ["order.paid"])))
    check("AC-04", f"第 6 个端点被拒 400（{body.get('detail')}）", code == 400)

    # AC-05 修改与并发冲突、启停
    updated = send("PUT", f"/endpoints/{endpoint}", {"url": RECEIVER + "/hook", "eventTypes": ["order.paid", "refund.succeeded"],
                                                       "expectedVersion": 1, "reason": "阶段 5 联调"})
    check("AC-05", "修改成功，版本 2", updated["version"] == 2)
    code, _ = status_of(lambda: send("PUT", f"/endpoints/{endpoint}", {"url": RECEIVER + "/hook", "eventTypes": ["order.paid"],
                                                                         "expectedVersion": 1, "reason": "x"}))
    check("AC-05", "过期版本修改返回 409", code == 409)

    # AC-06 轮换
    rotated = send("POST", f"/endpoints/{endpoint}/signing-secret/rotations", {"immediate": False, "reason": "阶段 5 联调"})
    secrets_seen.append(rotated["signingSecret"])
    view = next(e for e in get(f"/clients/{CLIENT}/endpoints") if e["endpointId"] == endpoint)
    check("AC-06", "宽限轮换：第 2 号，列表显示旧密钥截止时间",
          rotated["rotationNo"] == 2 and rotated["previousValidUntil"] and view["signingSecret"]["previousValidUntil"])

    # AC-07 测试事件：成功双签、失败显示状态码
    received.clear()
    tested = send("POST", f"/endpoints/{endpoint}/tests")
    check("AC-07", f"测试事件送达（{tested['result']} / {tested['httpStatus']}）", tested["result"] == "SUCCEEDED" and tested["httpStatus"] == 204)
    check("AC-07", "宽限期内测试事件带两个 v1 签名",
          received and re.fullmatch(r"t=\d+,v1=[0-9a-f]{64},v1=[0-9a-f]{64}", received[-1][1]) is not None)
    immediate = send("POST", f"/endpoints/{endpoint}/signing-secret/rotations", {"immediate": True, "reason": "阶段 5 联调"})
    secrets_seen.append(immediate["signingSecret"])
    check("AC-06", "立即作废：第 3 号且无宽限", immediate["rotationNo"] == 3 and immediate["previousValidUntil"] is None)
    other = next(e for e in get(f"/clients/{CLIENT}/endpoints") if e["endpointId"] != endpoint)
    moved = send("PUT", f"/endpoints/{other['endpointId']}", {"url": RECEIVER + "/fail", "eventTypes": ["order.paid"],
                                                               "expectedVersion": other["version"], "reason": "阶段 5 联调"})
    failed = send("POST", f"/endpoints/{other['endpointId']}/tests")
    check("AC-07", "测试失败返回 HTTP_STATUS 503", failed["result"] == "FAILED" and failed["httpStatus"] == 503
          and failed["failureCode"] == "HTTP_STATUS" and moved["version"] == other["version"] + 1)
    disabled = send("PATCH", f"/endpoints/{other['endpointId']}/status",
                    {"status": "DISABLED", "expectedVersion": moved["version"], "reason": "阶段 5 联调"})
    check("AC-05", "停用端点成功", disabled["version"] == moved["version"] + 1)

    # AC-08 投递记录与 Replay
    event_id = f"evt_e2e_ops_{RUN}"
    check("AC-08", "commerce-service 提交事件 204", submit_event(event_id) == 204)
    query = lambda extra="": get(f"/deliveries?clientId={CLIENT}&{window()}&page=1&size=20{extra}")
    check("AC-08", "投递送达（按 eventId 与状态筛选）",
          wait_until(lambda: query(f"&eventId={event_id}&status=SUCCEEDED")["total"] >= 1, 30))
    page = query(f"&eventId={event_id}")
    check("AC-08", "eventId 筛选只命中启用的订阅端点", page["total"] == 4)
    delivery = page["items"][0]["deliveryId"]
    detail = get(f"/deliveries/{delivery}")
    check("AC-08", "详情含 Envelope 与尝试", detail["event"]["envelope"]["eventId"] == event_id and len(detail["attempts"]) >= 1)
    replay = send("POST", f"/deliveries/{delivery}/replays", {"reason": "阶段 5 联调"})
    check("AC-08", "Replay 生成新投递并送达",
          wait_until(lambda: get(f"/deliveries/{replay['deliveryId']}")["status"] == "SUCCEEDED", 30))
    code, _ = status_of(lambda: get(f"/deliveries?clientId={CLIENT}&{window(24 * 40)}"))
    check("AC-08", "时间跨度超过 31 天返回 400", code == 400)

    # AC-09 日志不含密钥；审计
    logs = "".join((ROOT / "local-environment/e2e/logs" / f).read_text(errors="ignore")
                   for f in ["platform.log", "ops-bff.log", "gateway.log"])
    check("AC-09", "Platform / BFF / Gateway 日志不含任何本次密钥明文", all(x and x not in logs for x in secrets_seen))
    actions = e2e_http.platform_sql("SELECT DISTINCT action_code FROM t_audit_event WHERE action_code LIKE 'OPERATIONS_WEBHOOK%'"
                                    " AND occurred_time > UTC_TIMESTAMP(6) - INTERVAL 1 HOUR")
    names = {row[0] for row in actions}
    check("AC-09", f"写操作产生审计（{len(names)} 种动作）", {"OPERATIONS_WEBHOOK_ENDPOINT_CREATE", "OPERATIONS_WEBHOOK_ENDPOINT_UPDATE",
          "OPERATIONS_WEBHOOK_ENDPOINT_STATUS_CHANGE", "OPERATIONS_WEBHOOK_SECRET_ROTATE", "OPERATIONS_WEBHOOK_ENDPOINT_TEST",
          "OPERATIONS_WEBHOOK_DELIVERY_REPLAY"} <= names)

    server.shutdown()
    passed = sum(1 for r in results if r[2])
    print(f"\n{passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
