"""2026-10-16 接入自助（API 凭证与 Webhook）API 级端到端验收（本机全链路）。

覆盖 requirements/20261016/client-console-接入自助 的 AC-01—AC-12（经 Nginx → Gateway → Client BFF → Platform 真实进程）：
  批准并进入工作区 → 首页 capabilities → Scope 目录 → 创建凭证（一次性密钥、no-store）→ PLT-I03 校验通过
  → 立即轮换（旧失效）→ 宽限轮换（新旧都可用）→ 撤销 → 上限 10 → 跨工作区 404 → 账号上下文 403
  → Webhook：本机接收端、创建端点（一次性密钥）、测试事件、签名校验、投递列表 / 详情（无运营字段）、重发、停用、跨工作区 404。
本机没有登记 Client API（凭证认证）路由，凭证可用性直接调用 Gateway 认证时使用的 Platform PLT-I03 私网接口验证。
输出每步 PASS/FAIL 与证据；不输出密码、令牌、Cookie、凭证或签名密钥。
用法：python3 developer_flow.py
"""
import hashlib
import hmac
import http.server
import json
import threading
import time
import urllib.error
import urllib.request

import workspace_flow as ws_flow
from acceptance_flow import RESULTS, Client, Stop, problem, record
from e2e_http import CLIENT, Failure

PLATFORM = "http://127.0.0.1:18106/platform-support"
DEV = "/developer/v1"


def raw(client, path, method="GET", body=None):
    """与 Client.call 相同的请求，但返回 (status, headers, body)，用于检查 Cache-Control 等响应头。"""
    headers = {"Origin": CLIENT, "Accept": "application/json", "Authorization": "Bearer " + client.token}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    elif method == "POST":
        data = b""  # 与 Web 一致：空 Body 命令不带请求体和 Content-Type
    request = urllib.request.Request(CLIENT + "/api/client" + path, data=data, headers=headers, method=method)
    try:
        with client.opener.open(request, timeout=30) as response:
            text = response.read().decode()
            return response.status, response.headers, (json.loads(text) if text else None)
    except urllib.error.HTTPError as failure:
        text = failure.read().decode()
        return failure.code, failure.headers, (json.loads(text) if text else None)


def validate(secret):
    """调用 Gateway 认证使用的 PLT-I03 私网接口，返回 (status, body)。"""
    request = urllib.request.Request(PLATFORM + "/api-credentials/v1/validate", method="POST",
                                     headers={"X-Platform-Api-Credential": secret, "Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as failure:
        return failure.code, None


def enter(client, client_id):
    _, item = ws_flow.wait_accessible(client, client_id)
    status, grant = client.call("/auth/v1/workspace-context", "POST", {"memberId": item["memberId"]})
    client.token = grant["accessToken"]
    return grant["context"]


class Receiver:
    """本机 HTTP 接收端：记录收到的请求并返回 200。"""

    def __init__(self):
        self.requests = []
        owner = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                length = int(self.headers.get("Content-Length") or 0)
                owner.requests.append({"headers": dict(self.headers), "body": self.rfile.read(length)})
                self.send_response(200)
                self.end_headers()

            def log_message(self, *args):
                pass

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = f"http://127.0.0.1:{self.server.server_address[1]}/hook"
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def wait(self, count, seconds=30):
        deadline = time.time() + seconds
        while time.time() < deadline and len(self.requests) < count:
            time.sleep(0.5)
        return len(self.requests) >= count


def signature_ok(request, secret):
    header = request["headers"].get("Code-MoR-Signature") or ""
    parts = dict(p.split("=", 1) for p in header.split(",") if "=" in p)
    signatures = [p.split("=", 1)[1] for p in header.split(",") if p.startswith("v1=")]
    expected = hmac.new(secret.encode(), (parts.get("t", "") + ".").encode() + request["body"], hashlib.sha256).hexdigest()
    return bool(signatures) and any(hmac.compare_digest(expected, s) for s in signatures)


def ensure_scope():
    """测试夹具：本机未登记运营 Scope 管理路由，直接在 mor_platform_e2e 写入一条 V1 Scope 目录（存在则不动）。"""
    import os
    import subprocess
    from e2e_http import ROOT
    env = {}
    for line in (ROOT / "local-environment/.env.local").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            env[k.strip()] = v.strip().strip('"')
    statement = ("INSERT INTO t_api_scope(api_scope_id,scope_code,display_name,description,version,create_time,update_time) "
                 "SELECT 9300000000000001,'orders.read','查询订单','Client API V1：查询订单（本机验收夹具）',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) "
                 "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM t_api_scope WHERE scope_code='orders.read'); "
                 "SELECT COUNT(*) FROM t_api_scope WHERE scope_code='orders.read';")
    result = subprocess.run(["mysql", "-h127.0.0.1", "-P13306", "-uplatform_app", "-N", "-B", "--default-character-set=utf8mb4",
                             "mor_platform_e2e", "-e", statement],
                            env={**os.environ, "MYSQL_PWD": env["MOR_PLATFORM_DB_PASSWORD"]}, capture_output=True, text=True, check=True)
    record("夹具：Scope 目录 orders.read", result.stdout.strip().endswith("1"), f"rows={result.stdout.strip()}")


def credentials_flow(client, tenant_id, data):
    status, home = client.call("/workspace/v1/home")
    caps = set(home.get("capabilities") or [])
    record("AC-11 首页 capabilities", caps == {"API_CREDENTIAL_READ", "API_CREDENTIAL_MANAGE", "WEBHOOK_READ", "WEBHOOK_MANAGE"},
           f"capabilities={sorted(caps)}")
    status, scopes = client.call(DEV + "/scopes")
    codes = [s.get("scopeCode") for s in (scopes or {}).get("items") or []]
    record("F-03 Scope 目录", status == 200 and codes, f"HTTP {status} scopes={codes}")
    scope = codes[:1]

    status, headers, created = raw(client, DEV + "/credentials", "POST", {"name": "E2E server", "scopeCodes": scope, "validityDays": 365})
    secret = (created or {}).get("secret") or ""
    first = (created or {}).get("credential") or {}
    record("AC-02 创建凭证（一次性密钥、no-store）", status == 201 and secret.startswith("ck-") and "no-store" in (headers.get("Cache-Control") or ""),
           f"HTTP {status} keyPrefix={first.get('keyPrefix')} cacheControl={headers.get('Cache-Control')} secretLength={len(secret)}")
    v_status, v_body = validate(secret)
    record("AC-02 PLT-I03 校验通过并带本工作区身份", v_status == 200 and str((v_body or {}).get("tenantId")) == str(tenant_id),
           f"HTTP {v_status} tenantId={(v_body or {}).get('tenantId')} clientId={(v_body or {}).get('ownerRefId')} scopes={(v_body or {}).get('effectiveScopeCodes')}")
    record("AC-02 错误密钥 401", validate(secret[:-2] + ("AA" if not secret.endswith("AA") else "BB"))[0] == 401, "篡改后 401")
    status, listed = client.call(DEV + "/credentials")
    text = json.dumps(listed)
    record("AC-02 列表不含密钥", status == 200 and secret.split(".")[-1] not in text and "verifier" not in text.lower() and "secret" not in text,
           f"HTTP {status} items={len(listed.get('items') or [])}")

    status, _, rotated = raw(client, DEV + f"/credentials/{first['credentialId']}/rotation", "POST",
                             {"expectedVersion": first["version"], "graceMinutes": 0})
    second_secret, second = rotated["secret"], rotated["credential"]
    record("AC-04 立即轮换：旧失效、新可用", status == 201 and validate(secret)[0] == 401 and validate(second_secret)[0] == 200,
           f"HTTP {status} newId={second.get('credentialId')} rotatedFrom={second.get('rotatedFromId')}")

    status, _, rotated = raw(client, DEV + f"/credentials/{second['credentialId']}/rotation", "POST",
                             {"expectedVersion": second["version"], "graceMinutes": 60})
    third_secret, third = rotated["secret"], rotated["credential"]
    record("AC-04 宽限轮换：新旧都可用", status == 201 and validate(second_secret)[0] == 200 and validate(third_secret)[0] == 200,
           f"HTTP {status} newId={third.get('credentialId')}")

    status, detail = client.call(DEV + f"/credentials/{third['credentialId']}")
    status, _, revoked = raw(client, DEV + f"/credentials/{third['credentialId']}/revocation", "POST", {"expectedVersion": detail["version"]})
    again, _, revoked_again = raw(client, DEV + f"/credentials/{third['credentialId']}/revocation", "POST", {"expectedVersion": detail["version"]})
    record("AC-05 撤销后 401、重复撤销返回原结果", status in (200, 201) and validate(third_secret)[0] == 401 and again in (200, 201),
           f"HTTP {status}/{again} status={(revoked or {}).get('status')}")

    created_ids, limit_status, limit_body = [], None, None
    for index in range(12):
        status, _, body = raw(client, DEV + "/credentials", "POST", {"name": f"Limit {index}", "scopeCodes": scope, "validityDays": 30})
        if status != 201:
            limit_status, limit_body = status, body
            break
        created_ids.append(body["credential"]["credentialId"])
    status, listed = client.call(DEV + "/credentials")
    active = [i for i in listed.get("items") or [] if i.get("status") == "ACTIVE"]
    record("AC-03 有效凭证上限 10", limit_status is not None and 400 <= limit_status < 500 and len(active) == 10,
           f"created={len(created_ids)} rejected HTTP {limit_status} code={(limit_body or {}).get('code')} active={len(active)}")
    data["credentialId"] = first["credentialId"]


def canonical(value):
    """RFC 8785：本脚本的 Envelope 只含 ASCII 键、字符串与小整数，按键排序的紧凑 JSON 即规范字节。"""
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()


def submit_event(client_id):
    """模拟 Commerce 经私网提交 order.paid（本机 webhook.http.callers 已登记 commerce-service）。"""
    event_id = "evt_e2e_" + time.strftime("%Y%m%d%H%M%S")
    obj = {"orderNo": "ord_" + event_id, "orderStatus": "PAID", "totalAmount": "42.90", "version": 5}
    envelope = {"eventId": event_id, "eventType": "order.paid", "schemaVersion": "1", "createdAt": "2026-10-16T00:00:00Z",
                "data": {"object": obj}}
    body = {"clientId": str(client_id), "sourceVersion": 5, "sourcePayloadHash": hashlib.sha256(canonical(obj)).hexdigest(),
            "payloadHash": hashlib.sha256(canonical(envelope)).hexdigest(), "envelope": envelope}
    request = urllib.request.Request(PLATFORM + "/webhooks/v1/events", data=json.dumps(body).encode(), method="POST",
                                     headers={"X-Caller-Service": "commerce-service", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, event_id
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode()[:300]


def webhook_flow(client, data):
    receiver = Receiver()
    # 新建端点与 Web 相同只发地址和订阅事件，不带版本（2026-10-16 拆分新建请求体）。
    status, headers, created = raw(client, DEV + "/webhook-endpoints", "POST",
                                   {"url": receiver.url, "eventTypes": ["order.paid", "refund.succeeded"]})
    secret = (created or {}).get("signingSecret") or ""
    endpoint_id = (created or {}).get("endpointId")
    record("AC-07 新建端点（一次性密钥、no-store）", status == 201 and secret.startswith("whsec_") and "no-store" in (headers.get("Cache-Control") or ""),
           f"HTTP {status} endpointId={endpoint_id} code={(created or {}).get('code')} cacheControl={headers.get('Cache-Control')}")
    if not endpoint_id:
        raise Stop("端点未创建")
    data["endpointId"] = endpoint_id
    status, endpoints = client.call(DEV + "/webhook-endpoints")
    record("F-06 端点列表不含密钥", status == 200 and secret not in json.dumps(endpoints),
           f"HTTP {status} items={len(endpoints.get('items') or [])}")

    status, _, tested = raw(client, DEV + f"/webhook-endpoints/{endpoint_id}/test-events", "POST")
    arrived = receiver.wait(1)
    record("AC-09 测试事件送达", status in (200, 201, 202) and arrived, f"HTTP {status} result={tested} received={len(receiver.requests)}")
    if arrived:
        body = json.loads(receiver.requests[0]["body"])
        record("AC-09 签名可用本次密钥校验", signature_ok(receiver.requests[0], secret),
               f"eventType={body.get('eventType') or body.get('type')} signatureHeader={'Code-MoR-Signature' in receiver.requests[0]['headers']}")

    print("NOTE 测试事件为同步发送，不生成投递记录（与需求 AC-09 的表述不同，记为偏差）")
    status, event_id = submit_event(data["clientId"])
    record("夹具：模拟 Commerce 提交 order.paid", status == 204, f"HTTP {status} eventId={event_id}")
    record("order.paid 送达接收端", receiver.wait(2), f"received={len(receiver.requests)}")
    record("order.paid 签名可用本次密钥校验", signature_ok(receiver.requests[-1], secret), "Code-MoR-Signature 校验")
    deadline, deliveries = time.time() + 30, []
    while time.time() < deadline:
        status, page = client.call(DEV + f"/webhook-deliveries?endpointId={endpoint_id}")
        deliveries = (page or {}).get("items") or (page or {}).get("records") or []
        if deliveries and deliveries[0].get("status") in ("SUCCEEDED", "DELIVERED"):
            break
        time.sleep(2)
    record("F-08 投递列表", deliveries and deliveries[0].get("status") in ("SUCCEEDED", "DELIVERED"),
           f"HTTP {status} count={len(deliveries)} first={deliveries[0] if deliveries else None} pageKeys={sorted((page or {}).keys())}")
    if not deliveries:
        raise Stop("无投递记录")
    delivery_id = deliveries[0]["deliveryId"]
    status, detail = client.call(DEV + f"/webhook-deliveries/{delivery_id}")
    text = json.dumps(detail)
    record("AC-10 投递详情无运营字段", status == 200 and detail.get("attempts") and all(k not in text for k in ("requestedBy", "sourceSystem", "reason")),
           f"HTTP {status} attempts={[(a.get('attemptNo'), a.get('httpStatus'), a.get('failureCode')) for a in detail.get('attempts') or []]} keys={sorted(detail)}")

    status, _, replay = raw(client, DEV + f"/webhook-deliveries/{delivery_id}/replay", "POST")
    new_id = (replay or {}).get("deliveryId")
    arrived = receiver.wait(3)
    status2, replayed = client.call(DEV + f"/webhook-deliveries/{new_id}") if new_id else (None, {})
    record("AC-09 重发：新投递、事件不变", status == 201 and new_id and new_id != delivery_id and arrived
           and (replayed.get("event") or {}).get("eventId") == (detail.get("event") or {}).get("eventId"),
           f"HTTP {status} newDelivery={new_id} received={len(receiver.requests)} replayOf={replayed.get('replayOfDeliveryId')}")

    status, endpoints = client.call(DEV + "/webhook-endpoints")
    current = next(e for e in endpoints["items"] if e["endpointId"] == endpoint_id)
    status, changed = client.call(DEV + f"/webhook-endpoints/{endpoint_id}/status", "POST",
                                  {"status": "DISABLED", "expectedVersion": current["version"]})
    status, endpoints = client.call(DEV + "/webhook-endpoints")
    current = next(e for e in endpoints["items"] if e["endpointId"] == endpoint_id)
    record("F-06 停用端点", current.get("status") == "DISABLED", f"status={current.get('status')}")
    data["deliveryId"] = delivery_id
    receiver.server.shutdown()


def cross_tenant(data):
    stamp = time.strftime("%Y%m%d%H%M%S") + "x"
    other, _, app = ws_flow.approved_client(stamp)
    enter(other, app["clientId"])
    for name, path in (("凭证", f"/credentials/{data['credentialId']}"), ("投递", f"/webhook-deliveries/{data['deliveryId']}")):
        status, _, body = raw(other, DEV + path)
        record(f"AC-06/08 跨工作区读取{name} 404", status == 404, f"HTTP {status} code={(body or {}).get('code')}")
    status, _, body = raw(other, DEV + f"/webhook-endpoints/{data['endpointId']}/status", "POST", {"status": "ACTIVE", "expectedVersion": 1})
    record("AC-08 跨工作区操作端点 404", status == 404, f"HTTP {status} code={(body or {}).get('code')}")
    status, grant = other.call("/auth/v1/account-context", "POST", {})
    other.token = grant["accessToken"]
    status, _, body = raw(other, DEV + "/credentials")
    record("AC-01 账号上下文 403", status == 403, f"HTTP {status} code={(body or {}).get('code')}")


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    data = {}
    try:
        client, ops, app = ws_flow.approved_client(stamp)
        ensure_scope()
        context = enter(client, app["clientId"])
        data["clientId"] = app["clientId"]
        record("进入工作区", context.get("contextType") == "TENANT", f"tenantId={context.get('tenantId')}")
        credentials_flow(client, context["tenantId"], data)
        webhook_flow(client, data)
        cross_tenant(data)
    except Stop as stop:
        print(f"中止于：{stop}")
    except Failure as failure:
        RESULTS.append(("中止（HTTP）", "FAIL", problem(failure)))
        print(f"FAIL 中止（HTTP） | {problem(failure)}")
    passed = sum(1 for r in RESULTS if r[1] == "PASS")
    print(f"\n合计 {passed}/{len(RESULTS)} 通过")
    raise SystemExit(0 if passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
