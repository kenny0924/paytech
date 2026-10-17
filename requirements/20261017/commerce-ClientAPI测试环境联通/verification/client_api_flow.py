"""Client API 在 mor-test 联通验收（requirements/20261017/commerce-ClientAPI测试环境联通，阶段 7）。

链路：入驻批准的验收 Client → Client Console 自助凭证（ck-）与 Webhook 端点（https://webhook-receiver:8443/hook）
→ https://localhost:28444/api/v1/*（Nginx CLIENT_API → Gateway 凭证 / Scope → Commerce）→ Commerce 补偿任务 → Platform 受理
→ 签名投递 → test 内验收接收端（落盘到 test-environment/private/webhook-receiver/inbox）。
  AC-01/02  order.paid：PaymentSucceeded 夹具（test 无 Payment，Intent 投影由夹具行代替，已标注）
  AC-03     order.cancelled（含重复取消）
  AC-04     refund.created（含同一 clientRefundId 重放）及退款查询
  AC-05     --outage：发布 PaymentSucceeded 前暂停 Platform 容器，确认任务 ERROR 后恢复，等 XXL-Job 扫描补交
  AC-01 拒绝 无凭证 / 错凭证 / 缺 Scope
不打印凭证、签名密钥或口令。用法：E2E_TARGET=test python3 client_api_flow.py [--outage]
"""
import base64
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

if os.environ.get("E2E_TARGET") != "test":
    raise SystemExit("仅用于 mor-test：E2E_TARGET=test")

HERE = Path(__file__).resolve().parent
LOCAL_E2E = HERE.parents[2] / "20260914/client-console-注册与客户入驻/verification/local-e2e"
sys.path.insert(0, str(LOCAL_E2E))

import commerce_webhook_flow as cwf  # noqa: E402
import developer_flow as dev  # noqa: E402
import workspace_flow as ws_flow  # noqa: E402
from acceptance_flow import RESULTS, Stop, problem  # noqa: E402
from e2e_http import ROOT, Failure, read_env  # noqa: E402

API = "https://localhost:28444/api/v1"
TLS = ssl.create_default_context(cafile=str(ROOT / "test-environment/private/test-ca.crt"))
INBOX = ROOT / "test-environment/private/webhook-receiver/inbox"
TEST_ENV = read_env(ROOT / "test-environment/.env.test")
ENDPOINT_URL = "https://webhook-receiver:8443/hook"
SCOPES = ["checkouts.write", "orders.read", "orders.cancel", "refunds.read", "refunds.write"]
PLATFORM_CONTAINER = "mor-test-platform-support-service-1"
record = cwf.record
STATE = {"credential": None}


# ---------- test 适配：替换 commerce_webhook_flow 的本机直连 ----------

def client_api(method, path, client_id=None, body=None, credential="default", idempotency=True):
    """经 Client API 正式入口调用；client_id 仅为与 commerce_webhook_flow 签名兼容，身份来自凭证。"""
    headers = {"Accept": "application/json", "X-Request-Id": "req-" + uuid.uuid4().hex[:16]}
    secret = STATE["credential"] if credential == "default" else credential
    if secret:
        headers["Authorization"] = "Bearer " + secret
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if method == "POST":
        if idempotency:
            headers["Idempotency-Key"] = "idem-" + uuid.uuid4().hex
        data = data if data is not None else b""
    request = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30, context=TLS) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as failure:
        text = failure.read().decode()
        try:
            return failure.code, json.loads(text)
        except ValueError:
            return failure.code, {"raw": text[:300]}


def commerce_sql(statement):
    result = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-ucommerce_app", "-N", "-B",
                             "--default-character-set=utf8mb4", "mor_commerce", "-e", statement],
                            env={**os.environ, "MYSQL_PWD": TEST_ENV["MOR_COMMERCE_DB_PASSWORD"]},
                            capture_output=True, text=True, check=True)
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def rabbit_publish(routing_key, body, message_id):
    """经 backend 网络调用 test RabbitMQ 管理接口发布到 /mor-test 的 topic_payment_result；口令经 stdin 传入。"""
    request = {"user": TEST_ENV["MOR_RABBITMQ_USER"], "password": TEST_ENV["MOR_RABBITMQ_PASSWORD"],
               "body": {"properties": {"message_id": message_id, "content_type": "application/json", "delivery_mode": 2},
                        "routing_key": routing_key, "payload": body, "payload_encoding": "string"}}
    script = ("import sys,json,base64,urllib.request;r=json.load(sys.stdin);"
              "a=base64.b64encode((r['user']+':'+r['password']).encode()).decode();"
              "q=urllib.request.Request('http://rabbitmq:15672/api/exchanges/%2Fmor-test/topic_payment_result/publish',"
              "data=json.dumps(r['body']).encode(),headers={'Authorization':'Basic '+a,'Content-Type':'application/json'},method='POST');"
              "print(json.loads(urllib.request.urlopen(q,timeout=10).read()).get('routed'))")
    result = subprocess.run(["docker", "run", "--rm", "-i", "--network", "mor-test-backend", "python:3.13-alpine",
                             "python", "-I", "-c", script], input=json.dumps(request), capture_output=True, text=True,
                            timeout=60, check=True)
    return result.stdout.strip() == "True"


class InboxReceiver:
    """读取 test 接收端落盘的请求；只看本次运行开始后的文件。"""

    def __init__(self):
        self.started_ns = time.time_ns()
        self.url = ENDPOINT_URL

    @property
    def requests(self):
        items = []
        for path in sorted(INBOX.glob("*.json")):
            if int(path.name.split("-", 1)[0]) < self.started_ns:
                continue
            raw = json.loads(path.read_text(encoding="utf-8"))
            items.append({"headers": raw["headers"], "body": base64.b64decode(raw["body"])})
        return items


cwf.commerce = client_api
cwf.commerce_sql = commerce_sql
cwf.rabbit_publish = rabbit_publish


# ---------- 验收步骤 ----------

def create_credentials(client):
    status, _, created = dev.raw(client, dev.DEV + "/credentials", "POST",
                                 {"name": "Client API acceptance", "scopeCodes": SCOPES, "validityDays": 30})
    secret = (created or {}).get("secret") or ""
    record("自助创建凭证（5 个 Scope）", status == 201 and secret.startswith("ck-"),
           f"HTTP {status} keyPrefix={((created or {}).get('credential') or {}).get('keyPrefix')} code={(created or {}).get('code')}")
    status, _, limited = dev.raw(client, dev.DEV + "/credentials", "POST",
                                 {"name": "Client API read-only", "scopeCodes": ["orders.read"], "validityDays": 30})
    record("自助创建只读凭证（orders.read）", status == 201, f"HTTP {status}")
    if not secret:
        raise Stop("凭证未创建")
    STATE["credential"] = secret
    return (limited or {}).get("secret") or ""


def rejections(read_only, order_no):
    status, body = client_api("GET", f"/orders/{order_no}", credential=None)
    record("AC-01 无凭证被 Gateway 拒绝", status == 401, f"HTTP {status} code={(body or {}).get('code')}")
    wrong = STATE["credential"][:-2] + ("AA" if not STATE["credential"].endswith("AA") else "BB")
    status, body = client_api("GET", f"/orders/{order_no}", credential=wrong)
    record("AC-01 错凭证被拒绝", status == 401, f"HTTP {status} code={(body or {}).get('code')}")
    status, body = client_api("GET", f"/orders/{order_no}", credential=read_only)
    record("AC-01 只读凭证可查单", status == 200, f"HTTP {status}")
    status, body = client_api("POST", f"/orders/{order_no}/cancel", credential=read_only)
    record("AC-01 缺 Scope（orders.cancel）被拒绝", status == 403, f"HTTP {status} code={(body or {}).get('code')}")
    status, body = client_api("POST", f"/orders/{order_no}/cancel", idempotency=False)
    record("D2 POST 缺 Idempotency-Key 被拒（Commerce 必填 Header）", status == 400, f"HTTP {status} code={(body or {}).get('code')}")


def queries(order_no, client_order_id):
    status, order = client_api("GET", f"/orders/{order_no}")
    record("AC-02 查单", status == 200 and order.get("orderNo") == order_no, f"HTTP {status} status={(order or {}).get('orderStatus')}")
    status, found = client_api("GET", f"/orders?clientOrderId={urllib.request.quote(client_order_id)}")
    record("AC-02 按 clientOrderId 查单", status == 200 and (found or {}).get("orderNo") == order_no, f"HTTP {status}")
    status, body = client_api("GET", "/orders/ord_doesnotexist0000000000000000")
    record("AC-02 不存在 / 他人订单 404", status == 404, f"HTTP {status} code={(body or {}).get('code')}")
    status, body = client_api("GET", f"/orders/{order_no}?unknown=1")
    record("Gateway Query 白名单：未知参数被拒", 400 <= status < 500, f"HTTP {status} code={(body or {}).get('code')}")


def task_state(event_key):
    rows = commerce_sql("SELECT execute_status,execute_count,IFNULL(last_error_code,'') FROM t_compensation_event "
                        f"WHERE event_key='{event_key}'")
    return rows[0] if rows else None


def flow_paid_with_outage(receiver, secret, client_id):
    """AC-05：Platform 暂停期间产生 order.paid，恢复后由 XXL-Job 补偿扫描补交。"""
    order_no = cwf.create_checkout(client_id, "outage")
    order = cwf.order_row(order_no)
    intent_id = cwf.payment_fixture(order)
    attempt_id = 9_420_000_000_000_000 + int(time.time() * 1000) % 99_999_999
    message = cwf.payment_succeeded_message(order, intent_id, attempt_id)
    event_id = f"evt_{order_no}_paid"
    subprocess.run(["docker", "pause", PLATFORM_CONTAINER], check=True, capture_output=True)
    try:
        routed = rabbit_publish("payment.succeeded.v1", message, f"msg-{attempt_id}")
        failed = cwf.wait_for(lambda: (lambda t: t if t and t[0] in ("ERROR", "FAILED") else None)(task_state(event_id)), 40)
    finally:
        subprocess.run(["docker", "unpause", PLATFORM_CONTAINER], check=True, capture_output=True)
    record("AC-05 Platform 不可用时业务照常（PAID），Webhook 任务可重试 ERROR",
           routed and cwf.order_row(order_no)["status"] == "PAID" and failed and failed[0] == "ERROR",
           f"routed={routed} status={cwf.order_row(order_no)['status']} task={failed}")
    frozen = cwf.task_payload(event_id)
    hits = cwf.wait_for(lambda: cwf.received(receiver, event_id), 300, 5)
    record("AC-05 恢复后 XXL-Job 扫描补交，接收端收到 1 次", len(hits) == 1, f"received={len(hits)} task={task_state(event_id)}")
    if hits and frozen:
        record("AC-05 补交内容与冻结 Envelope 一致", json.loads(hits[0]["body"]) == frozen.get("envelope"),
               f"eventId={event_id}")
    envelope = cwf.check_delivery(receiver, secret, event_id, "order.paid", client_id) if hits else None
    routed2 = rabbit_publish("payment.succeeded.v1", message, f"msg-{attempt_id}")
    time.sleep(5)
    record("AC-02 同一 PaymentSucceeded 重复投递不产生第二个事件",
           routed2 and not cwf.tasks(event_id) and len(cwf.received(receiver, event_id)) == 1,
           f"routed={routed2} tasks={len(cwf.tasks(event_id))} received={len(cwf.received(receiver, event_id))}")
    return order_no, envelope


def main():
    outage = "--outage" in sys.argv
    stamp = time.strftime("%Y%m%d%H%M%S")
    data = {}
    try:
        client, ops, app = ws_flow.approved_client(stamp)
        client_id = app["clientId"]
        context = dev.enter(client, client_id)
        record("进入工作区", context.get("contextType") == "TENANT", f"tenantId={context.get('tenantId')} clientId={client_id}")
        read_only = create_credentials(client)
        receiver = InboxReceiver()
        status, _, created = dev.raw(client, dev.DEV + "/webhook-endpoints", "POST",
                                     {"url": ENDPOINT_URL, "eventTypes": cwf.EVENT_TYPES})
        secret = (created or {}).get("signingSecret") or ""
        record("端点：test 验收接收端、订阅 3 种事件", status == 201 and secret.startswith("whsec_"),
               f"HTTP {status} endpointId={(created or {}).get('endpointId')} code={(created or {}).get('code')}")
        if status != 201:
            raise Stop("端点未创建")
        data.update(clientId=client_id, endpointId=created.get("endpointId"), tenantId=context.get("tenantId"))

        order_no = cwf.create_checkout(client_id, "query")
        client_order_id = client_api("GET", f"/orders/{order_no}")[1]["clientOrderId"]
        queries(order_no, client_order_id)
        rejections(read_only, order_no)

        cwf.flow_cancel(receiver, secret, client_id)
        if outage:
            paid_order, _ = flow_paid_with_outage(receiver, secret, client_id)
        else:
            paid_order = cwf.flow_paid(receiver, secret, client_id)
        cwf.flow_refund(receiver, secret, client_id, paid_order)
        for path in (f"/orders/{paid_order}/refundable-amount", f"/orders/{paid_order}/refunds?limit=10"):
            status, body = client_api("GET", path)
            record(f"退款查询 {path.split('/')[-1].split('?')[0]}", status == 200, f"HTTP {status} code={(body or {}).get('code')}")
        data["orderNoPaid"] = paid_order
        types = [json.loads(r["body"]).get("eventType") for r in receiver.requests]
        record("接收端总数 = 3（无多余投递）", len(types) == 3, f"eventTypes={types}")
    except Stop as stop:
        print(f"中止于：{stop}")
        RESULTS.append(("中止", "FAIL", str(stop)))
    except Failure as failure:
        RESULTS.append(("中止（HTTP）", "FAIL", problem(failure)))
        print(f"FAIL 中止（HTTP） | {problem(failure)}")
    print("DATA", json.dumps(data))
    passed = sum(1 for r in RESULTS if r[1] == "PASS")
    print(f"\n合计 {passed}/{len(RESULTS)} 通过")
    raise SystemExit(0 if passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
