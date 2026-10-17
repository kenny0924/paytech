"""2026-10-17 Commerce Webhook 业务事件本机全链路验收（requirements/20261016/commerce-Webhook业务事件 阶段 5）。

链路：Commerce（本机进程 18102，直连并带 Gateway 注入的可信 Header）→ t_compensation_event CLIENT_WEBHOOK_REQUESTED
→ ClientWebhookSubmissionHandler → Platform POST /platform-support/webhooks/v1/events → 签名投递 → 本机接收端。
  order.cancelled：Client API 建结账 → 取消（重复取消不产生第二个事件）
  order.paid：向 topic_payment_result 发布合规 PaymentSucceeded（同一消息两次）
  refund.created：Client API 对已支付订单创建退款（同一 clientRefundId 重放不产生第二个事件）
夹具（已标注）：本机没有 Payment 进程，Intent 关联由 t_order_payment_projection 夹具行代替（真实链路由 Payment createIntent 产生）。
输出每步 PASS/FAIL；不打印密码、签名密钥、令牌。
用法：先按 requirements/20261016/commerce-Webhook业务事件/delivery.md 阶段 5 的参数在 18102 启动 Commerce，再运行 python3 commerce_webhook_flow.py
"""
import datetime
import hashlib
import json
import os
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

LOCAL_E2E = Path(__file__).resolve().parent
sys.path.insert(0, str(LOCAL_E2E))

import developer_flow as dev  # noqa: E402
import workspace_flow as ws_flow  # noqa: E402
from acceptance_flow import RESULTS, Stop, problem  # noqa: E402
from acceptance_flow import record as _record  # noqa: E402
from e2e_http import ROOT, Failure, platform_sql, read_env  # noqa: E402

COMMERCE = "http://127.0.0.1:18102/commerce/client-gateway-api/v1"
HERE = Path(__file__).resolve().parent
ENV = read_env(ROOT / "local-environment/.env.local")
EVENT_TYPES = ["order.paid", "order.cancelled", "refund.created"]


# ---------- 基础设施 ----------

def record(step, ok, evidence):
    """与 acceptance_flow.record 相同的输出，但单步失败不中止后续独立步骤。"""
    try:
        _record(step, bool(ok), evidence)
    except Stop:
        pass
    return bool(ok)


def commerce_sql(statement):
    result = subprocess.run(["mysql", "-h127.0.0.1", "-P13306", "-ucommerce_app", "-N", "-B",
                             "--default-character-set=utf8mb4", "mor_commerce", "-e", statement],
                            env={**os.environ, "MYSQL_PWD": ENV["MOR_COMMERCE_DB_PASSWORD"]},
                            capture_output=True, text=True, check=True)
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def commerce(method, path, client_id, body=None):
    headers = {"Accept": "application/json", "X-Request-Id": "req-" + uuid.uuid4().hex[:16],
               "X-Gateway-Client-Id": str(client_id), "X-Gateway-Credential-Id": "1"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if method == "POST":
        headers["Idempotency-Key"] = "idem-" + uuid.uuid4().hex
        data = data if data is not None else b""
    request = urllib.request.Request(COMMERCE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as failure:
        text = failure.read().decode()
        try:
            return failure.code, json.loads(text)
        except ValueError:
            return failure.code, {"raw": text[:300]}


def rabbit_publish(routing_key, body, message_id):
    import base64
    auth = base64.b64encode(f"{ENV['MOR_RABBITMQ_USER']}:{ENV['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    payload = {"properties": {"message_id": message_id, "content_type": "application/json", "delivery_mode": 2},
               "routing_key": routing_key, "payload": body, "payload_encoding": "string"}
    request = urllib.request.Request("http://127.0.0.1:15672/api/exchanges/%2Fmor-local/topic_payment_result/publish",
                                     data=json.dumps(payload).encode(),
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"},
                                     method="POST")
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read().decode()).get("routed")


def wait_for(predicate, seconds=30, step=0.5):
    deadline = time.time() + seconds
    while time.time() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(step)
    return predicate()


def instant(value):
    if value is None:
        return None
    return datetime.datetime.fromisoformat(value.replace("Z", "+00:00"))


def db_instant(value):
    return None if value in (None, "NULL") else datetime.datetime.fromisoformat(value).replace(tzinfo=datetime.timezone.utc)


# ---------- 业务步骤 ----------

def create_checkout(client_id, label):
    client_order_id = f"wh-{label}-{time.strftime('%H%M%S')}-{uuid.uuid4().hex[:6]}"
    status, body = commerce("POST", "/checkouts", client_id, {
        "clientOrderId": client_order_id, "currency": "USD", "consumerCountryCode": "US",
        "items": [{"clientItemId": "line-1", "name": "Premium subscription", "productTypeCode": "SERVICE",
                   "taxCategoryCode": "STANDARD", "quantity": "2", "unitAmount": "19.95", "discountAmount": "2.00"},
                  {"clientItemId": "line-2", "name": "Setup service", "productTypeCode": "SERVICE",
                   "taxCategoryCode": "STANDARD", "quantity": "1", "unitAmount": "5.00"}]})
    record(f"建结账（{label}）", status == 201 and (body or {}).get("orderNo"),
           f"HTTP {status} orderNo={(body or {}).get('orderNo')} total={((body or {}).get('amounts') or {}).get('totalAmount')} "
           f"checkoutStatus={(body or {}).get('checkoutStatus')} code={(body or {}).get('code')}")
    if status != 201:
        raise Stop(f"建结账失败 {label}: {body}")
    return body["orderNo"]


def order_row(order_no):
    rows = commerce_sql("SELECT order_id,client_id,order_status,version,entity_no,total_amount,currency,"
                        "IFNULL(cancelled_at,'NULL'),IFNULL(status_reason_code,'NULL'),IFNULL(paid_at,'NULL') "
                        f"FROM t_commerce_order WHERE order_no='{order_no}'")
    keys = ["orderId", "clientId", "status", "version", "entityNo", "total", "currency", "cancelledAt", "reasonCode", "paidAt"]
    return dict(zip(keys, rows[0])) if rows else None


def tasks(event_key):
    return commerce_sql("SELECT id,event_type,sub_event_type,execute_status,execute_count,IFNULL(last_error_code,'') "
                        f"FROM t_compensation_event WHERE event_key='{event_key}'")


def task_payload(event_key):
    rows = commerce_sql(f"SELECT payload_json FROM t_compensation_event WHERE event_key='{event_key}'")
    return json.loads(rows[0][0]) if rows else None


def platform_rows(event_id):
    events = platform_sql("SELECT event_id,event_type,source_system,source_version FROM t_webhook_event "
                          f"WHERE source_event_key='{event_id}'")
    deliveries = []
    if events:
        deliveries = platform_sql("SELECT delivery_id,status,attempt_count FROM t_webhook_delivery "
                                  f"WHERE webhook_event_id={int(events[0][0])}")
    return events, deliveries


def received(receiver, event_id):
    return [r for r in receiver.requests if json.loads(r["body"]).get("eventId") == event_id]


def check_delivery(receiver, secret, event_id, event_type, client_id):
    """接收端一份、签名、eventId 格式、任务 SUCCEEDED、Platform 事件与投递 SUCCEEDED。返回 envelope。"""
    hits = wait_for(lambda: received(receiver, event_id), 40)
    record(f"{event_type} 送达接收端", len(hits) == 1, f"eventId={event_id} received={len(hits)}")
    if not hits:
        record(f"{event_type} Commerce 任务状态", False, f"tasks={tasks(event_id)}")
        return None
    envelope = json.loads(hits[0]["body"])
    record(f"{event_type} 签名可用端点密钥校验", dev.signature_ok(hits[0], secret),
           f"header={'Code-MoR-Signature' in hits[0]['headers']}")
    record(f"{event_type} eventId/eventType/顶层键", envelope.get("eventId") == event_id and envelope.get("eventType") == event_type
           and event_id.startswith("evt_") and set(envelope) == {"eventId", "eventType", "schemaVersion", "createdAt", "data"},
           f"eventId={envelope.get('eventId')} type={envelope.get('eventType')} keys={sorted(envelope)} createdAt={envelope.get('createdAt')}")
    # Foundation 成功后删除任务行：行消失即 SUCCEEDED；ERROR/FAILED 会保留。
    left = wait_for(lambda: tasks(event_id) == [] and "gone", 15)
    record(f"{event_type} Commerce 任务成功完成（行已删除，无 ERROR/FAILED）", left == "gone", f"remaining={tasks(event_id)}")
    events, deliveries = wait_for(lambda: (lambda e: e if e[1] and all(d[1] == "SUCCEEDED" for d in e[1]) else None)(platform_rows(event_id)), 15) \
        or platform_rows(event_id)
    record(f"{event_type} Platform 事件唯一、投递 SUCCEEDED", len(events) == 1 and events[0][2] == "commerce-service"
           and len(deliveries) == 1 and deliveries[0][1] == "SUCCEEDED", f"events={events} deliveries={deliveries}")
    accepted = json.loads(platform_sql(f"SELECT payload FROM t_webhook_event WHERE source_event_key='{event_id}'")[0][0])
    record(f"{event_type} 接收内容 = Platform 受理的冻结 Envelope；sourceVersion = object.version",
           accepted == envelope and int(events[0][3]) == envelope["data"]["object"]["version"],
           f"equal={accepted == envelope} sourceVersion={events[0][3]} objectVersion={envelope['data']['object'].get('version')}")
    safety(event_type, envelope)
    return envelope


FORBIDDEN_KEYS = {"orderId", "clientId", "refundRequestId", "paymentIntentId", "paymentAttemptId", "channelRefundId",
                  "entityNo", "email", "phone", "consumer", "risk", "riskCheckoutAssessmentId"}


def safety(event_type, envelope):
    obj = envelope["data"]["object"]
    flat = json.dumps(obj)
    bad = [k for k in FORBIDDEN_KEYS if f'"{k}"' in flat]
    numeric_ids = [k for k, v in obj.items() if isinstance(v, str) and v.isdigit() and len(v) >= 12]
    record(f"AC-07 {event_type} 载荷无内部 ID / 个人信息 / 风控", not bad and not numeric_ids and set(envelope["data"]) == {"object"},
           f"objectKeys={list(obj)} forbidden={bad} numericIds={numeric_ids}")


def compare(label, obj, expected):
    """expected: [(field, actual_value, kind)]，kind=str|instant|int。"""
    diffs, notes = [], []
    for field, actual, kind in expected:
        value = obj.get(field) if "." not in field else obj.get(field.split(".")[0], {}).get(field.split(".")[1])
        if kind == "instant":
            if instant(value) != (actual if isinstance(actual, datetime.datetime) else instant(actual)):
                diffs.append((field, value, str(actual)))
            elif isinstance(actual, str) and value != actual:
                notes.append((field, value, actual))
        elif kind == "int":
            if str(value) != str(actual):
                diffs.append((field, value, actual))
        elif value != actual:
            diffs.append((field, value, actual))
    record(f"{label} data.object 与 Commerce 查询一致", not diffs, f"diffs={diffs} formatOnlyDiffs={notes}")


def flow_cancel(receiver, secret, client_id):
    order_no = create_checkout(client_id, "cancel")
    status, body = commerce("POST", f"/orders/{order_no}/cancel", client_id)
    record("AC-03 Client API 取消未支付订单", status == 200 and (body or {}).get("orderStatus") == "CANCELLED",
           f"HTTP {status} body={body}")
    event_id = f"evt_{order_no}_cancelled"
    envelope = check_delivery(receiver, secret, event_id, "order.cancelled", client_id)
    again, again_body = commerce("POST", f"/orders/{order_no}/cancel", client_id)
    time.sleep(3)
    record("AC-03 重复取消不产生第二个事件", len(tasks(event_id)) == 0 and len(platform_rows(event_id)[0]) == 1 and len(received(receiver, event_id)) == 1,
           f"HTTP {again} status={(again_body or {}).get('orderStatus')} tasks={len(tasks(event_id))} received={len(received(receiver, event_id))}")
    if not envelope:
        return
    _, order = commerce("GET", f"/orders/{order_no}", client_id)
    row = order_row(order_no)
    obj = envelope["data"]["object"]
    compare("AC-03 order.cancelled", obj, [
        ("orderNo", order["orderNo"], "str"), ("clientOrderId", order["clientOrderId"], "str"),
        ("orderStatus", order["orderStatus"], "str"), ("checkoutStatus", (order.get("checkout") or {}).get("status"), "str"),
        ("cancelledAt", body.get("cancelledAt"), "instant"),
        ("version", row["version"], "int")])
    record("AC-03 结构化原因码", obj.get("reasonCode") == "CLIENT_REQUESTED" and row["reasonCode"] == "CLIENT_REQUESTED",
           f"reasonCode={obj.get('reasonCode')} db={row['reasonCode']}")


def amount_text(value):
    text = value.rstrip("0").rstrip(".") if "." in value else value
    return text or "0"


def payment_fixture(order):
    """夹具：本机无 Payment，插入 Intent 关联投影（真实链路由 SubmitCustomerPayment → Payment createIntent 产生）。"""
    projection_id = 9_400_000_000_000_000 + random.randint(1, 99_999_999)
    intent_id = 9_410_000_000_000_000 + random.randint(1, 99_999_999)
    rows = commerce_sql(
        "INSERT INTO t_order_payment_projection(order_payment_projection_id,order_id,payment_intent_id,intent_status,"
        "intent_source_version,captured_amount,currency,last_event_time,version,create_time,update_time) VALUES "
        f"({projection_id},{int(order['orderId'])},{intent_id},'REQUIRES_PAYMENT',1,0,'{order['currency']}',"
        "UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)); SELECT ROW_COUNT();")
    record("夹具：订单 Intent 关联投影（替代 Payment createIntent）", rows and rows[-1][0] == "1",
           f"orderId={order['orderId']} paymentIntentId={intent_id} projectionId={projection_id}")
    return intent_id


def payment_succeeded_message(order, intent_id, attempt_id):
    now = datetime.datetime.now(datetime.timezone.utc).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")
    total = amount_text(order["total"])
    payload = {"sourceType": "COMMERCE_ORDER", "clientId": order["clientId"], "orderId": order["orderId"],
               "paymentIntentId": str(intent_id), "paymentAttemptId": str(attempt_id), "entityNo": order["entityNo"],
               "amount": total, "currency": order["currency"], "capturedAmount": total, "paymentSucceededTime": now}
    payload_bytes = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode()
    envelope = {"eventKey": f"PAYMENT:PAYMENT_ATTEMPT:{attempt_id}:1:PaymentSucceeded", "eventType": "PaymentSucceeded",
                "sourceSystem": "payment", "sourceType": "PAYMENT_ATTEMPT", "sourceId": str(attempt_id), "sourceVersion": 1,
                "schemaVersion": 1, "occurredAt": now, "payloadHash": "sha256:" + hashlib.sha256(payload_bytes).hexdigest(),
                "payload": payload}
    return json.dumps(envelope, separators=(",", ":"), ensure_ascii=False)


def flow_paid(receiver, secret, client_id):
    order_no = create_checkout(client_id, "paid")
    order = order_row(order_no)
    intent_id = payment_fixture(order)
    attempt_id = 9_420_000_000_000_000 + random.randint(1, 99_999_999)
    message = payment_succeeded_message(order, intent_id, attempt_id)
    routed = rabbit_publish("payment.succeeded.v1", message, f"msg-{attempt_id}")
    paid = wait_for(lambda: order_row(order_no)["status"] == "PAID", 20)
    record("AC-01 PaymentSucceeded 经 MQ 落地为 PAID", routed and paid, f"routed={routed} status={order_row(order_no)['status']}")
    event_id = f"evt_{order_no}_paid"
    envelope = check_delivery(receiver, secret, event_id, "order.paid", client_id)
    routed2 = rabbit_publish("payment.succeeded.v1", message, f"msg-{attempt_id}")
    time.sleep(5)
    record("AC-02 同一 PaymentSucceeded 重复投递不产生第二个事件",
           routed2 and len(tasks(event_id)) == 0 and len(received(receiver, event_id)) == 1
           and len(platform_rows(event_id)[0]) == 1,
           f"routed={routed2} tasks={len(tasks(event_id))} received={len(received(receiver, event_id))} "
           f"platformEvents={len(platform_rows(event_id)[0])} orderVersion={order_row(order_no)['version']}")
    if envelope:
        _, current = commerce("GET", f"/orders/{order_no}", client_id)
        row = order_row(order_no)
        compare("AC-01 order.paid", envelope["data"]["object"], [
            ("orderNo", current["orderNo"], "str"), ("clientOrderId", current["clientOrderId"], "str"),
            ("orderStatus", current["orderStatus"], "str"), ("paymentStatus", current["paymentStatus"], "str"),
            ("currency", current["currency"], "str"), ("amounts.totalAmount", current["amounts"]["totalAmount"], "str"),
            ("amounts.paidAmount", current["amounts"]["paidAmount"], "str"), ("paidAt", current["paidAt"], "instant"),
            ("version", row["version"], "int")])
    return order_no


def flow_refund(receiver, secret, client_id, order_no):
    request = {"clientRefundId": "refund-" + uuid.uuid4().hex[:8], "amount": "10.00", "reasonCode": "REQUESTED_BY_CUSTOMER"}
    status, created = commerce("POST", f"/orders/{order_no}/refunds", client_id, request)
    refund_no = (created or {}).get("refundRequestNo")
    record("AC-04 Client API 创建退款", status == 201 and refund_no, f"HTTP {status} refundRequestNo={refund_no} code={(created or {}).get('code')}")
    if not refund_no:
        return
    _, refund_now = commerce("GET", f"/refunds/{refund_no}", client_id)
    event_id = f"evt_{refund_no}_created"
    envelope = check_delivery(receiver, secret, event_id, "refund.created", client_id)
    again, replay = commerce("POST", f"/orders/{order_no}/refunds", client_id, request)
    time.sleep(3)
    record("AC-04 同一 clientRefundId 重放不产生第二个事件",
           again in (200, 201) and (replay or {}).get("refundRequestNo") == refund_no and len(tasks(event_id)) == 0 and len(platform_rows(event_id)[0]) == 1
           and len(received(receiver, event_id)) == 1,
           f"HTTP {again} sameRefund={(replay or {}).get('refundRequestNo') == refund_no} tasks={len(tasks(event_id))} "
           f"received={len(received(receiver, event_id))}")
    if not envelope:
        return
    obj = envelope["data"]["object"]
    fields = ["refundRequestNo", "clientRefundId", "requestOrigin", "orderNo", "amount", "currency", "refundStatus",
              "reasonCode", "statusReasonCode"]
    expected = [(f, refund_now.get(f), "str") for f in fields]
    expected += [(f, refund_now.get(f), "instant") for f in ("createdAt", "completedAt", "updatedAt")]
    expected.append(("version", refund_now.get("version"), "int"))
    compare("AC-04 refund.created（对比创建后立即 GET）", obj, expected)
    record("AC-04 refund.created 键集合与 GET Refund 相同", set(obj) == set(refund_now), f"event={sorted(obj)} get={sorted(refund_now)}")
    _, refund_later = commerce("GET", f"/refunds/{refund_no}", client_id)
    print(f"NOTE 退款当前 GET：status={refund_later.get('refundStatus')} version={refund_later.get('version')} "
          f"statusReasonCode={refund_later.get('statusReasonCode')}（Commerce 尚未接入 Payment 退款接口，提交任务预期失败）")
    print("NOTE refund submission tasks:", commerce_sql(
        "SELECT event_type,execute_status,execute_count,IFNULL(last_error_code,'') FROM t_compensation_event "
        f"WHERE event_key LIKE '%{refund_no}%' OR (event_type<>'CLIENT_WEBHOOK_REQUESTED' AND create_time>=UTC_TIMESTAMP()-INTERVAL 5 MINUTE)"))


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    data = {}
    try:
        client, ops, app = ws_flow.approved_client(stamp)
        client_id = app["clientId"]
        context = dev.enter(client, client_id)
        record("进入工作区", context.get("contextType") == "TENANT", f"tenantId={context.get('tenantId')} clientId={client_id}")
        receiver = dev.Receiver()
        status, headers, created = dev.raw(client, dev.DEV + "/webhook-endpoints", "POST", {"url": receiver.url, "eventTypes": EVENT_TYPES})
        secret = (created or {}).get("signingSecret") or ""
        record("端点：本机接收端、订阅 3 种事件", status == 201 and secret.startswith("whsec_"),
               f"HTTP {status} endpointId={(created or {}).get('endpointId')} url={receiver.url} code={(created or {}).get('code')}")
        if status != 201:
            raise Stop("端点未创建")
        data.update(clientId=client_id, endpointId=created.get("endpointId"), tenantId=context.get("tenantId"))
        flow_cancel(receiver, secret, client_id)
        order_no = flow_paid(receiver, secret, client_id)
        flow_refund(receiver, secret, client_id, order_no)
        data["orderNoPaid"] = order_no
        others = [json.loads(r["body"]).get("eventType") for r in receiver.requests]
        record("接收端总数 = 3（无多余投递）", len(receiver.requests) == 3, f"eventTypes={others}")
        receiver.server.shutdown()
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
