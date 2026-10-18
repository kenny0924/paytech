"""阶段 5 本机全链路（任务卡 14）：Client Webhook 业务事件 + 真实卡支付（由 20260914 local-e2e/commerce_webhook_flow.py 改名适配）。

链路：acquiring（acquiring-service）→ t_compensation_event CLIENT_WEBHOOK_REQUESTED → ClientWebhookSubmissionHandler
→ Platform POST /platform-support/webhooks/v1/events（调用方 acquiring-service）→ 签名投递 → 本机接收端（127.0.0.1 线程）。
  order.cancelled：建结账 → 取消（重复取消不产生第二个事件）
  order.paid：真实支付，不再用夹具或旧 Topic 发布——Consumer 经 Hosted Checkout Customer API 提交卡（acquiring createIntent →
              Payment card-tokens → card-service 建工具 → Payment 风控 → channel-connector 候选与执行 → Platform 标准 API PAY
              → 合成 PayerMax），Payment 发布 PaymentSucceeded v2（payment.events）→ acquiring 订单 PAID、card 消费登记
  refund.created：对已支付订单建退款（同一 clientRefundId 重放不产生第二个事件）
已知缺口（GAP，单列、不计入通过数）：acquiring 的 PaymentRefundClient 仍是失败关闭的 UnavailablePaymentRefundClient，Payment 没有退款
  接口（PAY-I04 未实现），全仓没有 RefundSucceeded 发布方；退款停在 PENDING/PROCESSING，提交任务 ERROR。
偏差（已标注）：Gateway 没有 CHECKOUT 通道运行时（无 CHECKOUT_SESSION 安全配置实现、Platform 无 CHECKOUT 路由），Customer API 私网直连
  acquiring 127.0.0.1:18102；网络证据取连接地址 127.0.0.1。
本文件可单独运行（Client 侧经私网直连 acquiring，带 Gateway 注入的可信 Header）；client_api_flow.py 复用本文件步骤并替换为 Client API 正式入口。
输出每步 PASS/FAIL；不打印密码、签名密钥、令牌或卡号。
"""
import datetime
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import developer_flow as dev  # noqa: E402
import workspace_flow as ws_flow  # noqa: E402
from acceptance_flow import RESULTS, Stop, problem  # noqa: E402
from acceptance_flow import record as _record  # noqa: E402
from e2e_http import PRIVATE, Failure, platform_sql, private_call, sql  # noqa: E402

ACQUIRING_CLIENT_API = PRIVATE["acquiring"] + "/client-gateway-api/v1"
CUSTOMER_API = "/customer-gateway-api/v1/checkouts"
EVENT_TYPES = ["order.paid", "order.cancelled", "refund.created"]
CALLER = "acquiring-service"
# PayerMax 公布的 UAT 测试卡（合成数据）；只出现在发给 acquiring 的请求体中，不进入输出。
TEST_CARD = {"pan": "4444333322221111", "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}
PAY_TRIES = 5
GAPS = []


# ---------- 基础设施 ----------

def record(step, ok, evidence):
    """与 acceptance_flow.record 相同的输出，但单步失败不中止后续独立步骤。"""
    try:
        _record(step, bool(ok), evidence)
    except Stop:
        pass
    return bool(ok)


def gap(step, evidence):
    """已知生产缺口：单列输出，不计入通过数，也不算失败。"""
    GAPS.append((step, evidence))
    print(f"GAP  {step} | {evidence}")


def acquiring_sql(statement):
    return sql("acquiring", statement)


def commerce(method, path, client_id, body=None):
    """单独运行时的 Client 侧调用：私网直连 acquiring，带 Gateway 注入的可信 Header。Idempotency-Key 由 Gateway 处理，不发送。"""
    headers = {"Accept": "application/json", "X-Request-Id": "req-" + uuid.uuid4().hex[:16],
               "X-Gateway-Client-Id": str(client_id), "X-Gateway-Credential-Id": "1"}
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    if method == "POST":
        data = data if data is not None else b""
    request = urllib.request.Request(ACQUIRING_CLIENT_API + path, data=data, headers=headers, method=method)
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


def customer(method, path, body=None):
    """Consumer 侧 Customer API（私网直连 acquiring，见文件头偏差说明）；返回 (status, dict)。"""
    headers = {"Accept": "application/json", "X-Request-Id": "req-" + uuid.uuid4().hex[:16],
               "User-Agent": "Mozilla/5.0 (Code-MoR stage5 local e2e)"}
    status, text = private_call("acquiring", method, CUSTOMER_API + path, headers, body)
    try:
        return status, (json.loads(text) if text else None)
    except ValueError:
        return status, {"raw": text[:300]}


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


# ---------- 查询 ----------

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
    return body["orderNo"], body.get("checkoutUrl")


def order_row(order_no):
    rows = acquiring_sql("SELECT order_id,client_id,order_status,version,entity_no,total_amount,currency,"
                         "IFNULL(cancelled_at,'NULL'),IFNULL(status_reason_code,'NULL'),IFNULL(paid_at,'NULL') "
                         f"FROM t_commerce_order WHERE order_no='{order_no}'")
    keys = ["orderId", "clientId", "status", "version", "entityNo", "total", "currency", "cancelledAt", "reasonCode", "paidAt"]
    return dict(zip(keys, rows[0])) if rows else None


def payment_projection(order_id):
    rows = acquiring_sql("SELECT payment_intent_id,intent_status,IFNULL(successful_attempt_id,''),captured_amount "
                         f"FROM t_order_payment_projection WHERE order_id={int(order_id)}")
    return dict(zip(["intentId", "intentStatus", "successfulAttemptId", "capturedAmount"], rows[0])) if rows else None


def tasks(event_key):
    return acquiring_sql("SELECT id,event_type,sub_event_type,execute_status,execute_count,IFNULL(last_error_code,'') "
                         f"FROM t_compensation_event WHERE event_key='{event_key}'")


def task_payload(event_key):
    rows = acquiring_sql(f"SELECT payload_json FROM t_compensation_event WHERE event_key='{event_key}'")
    return json.loads(rows[0][0]) if rows else None


def platform_rows(event_id):
    events = platform_sql("SELECT event_id,event_type,source_system,source_version FROM t_webhook_event "
                          f"WHERE source_event_key='{event_id}'")
    deliveries = []
    if events:
        deliveries = platform_sql("SELECT delivery_id,status,attempt_count FROM t_webhook_delivery "
                                  f"WHERE webhook_event_id={int(events[0][0])}")
    return events, deliveries


def consumed(service, attempt_id):
    """PaymentSucceeded v2 的消费登记（paytech-messaging t_consumed_event；aggregateType=PAYMENT_ATTEMPT、aggregateId=尝试 ID）。"""
    return sql(service, "SELECT event_id FROM t_consumed_event WHERE event_type='PaymentSucceeded' "
                        f"AND aggregate_type='PAYMENT_ATTEMPT' AND aggregate_id='{attempt_id}'")


def received(receiver, event_id):
    return [r for r in receiver.requests if json.loads(r["body"]).get("eventId") == event_id]


# ---------- 投递核对 ----------

def check_delivery(receiver, secret, event_id, event_type, client_id):
    """接收端一份、签名、eventId 格式、任务完成、Platform 事件与投递 SUCCEEDED。返回 envelope。"""
    hits = wait_for(lambda: received(receiver, event_id), 40)
    record(f"{event_type} 送达接收端", len(hits) == 1, f"eventId={event_id} received={len(hits)}")
    if not hits:
        record(f"{event_type} acquiring 任务状态", False, f"tasks={tasks(event_id)}")
        return None
    envelope = json.loads(hits[0]["body"])
    record(f"{event_type} 签名可用端点密钥校验", dev.signature_ok(hits[0], secret),
           f"header={'Code-MoR-Signature' in hits[0]['headers']}")
    record(f"{event_type} eventId/eventType/顶层键", envelope.get("eventId") == event_id and envelope.get("eventType") == event_type
           and event_id.startswith("evt_") and set(envelope) == {"eventId", "eventType", "schemaVersion", "createdAt", "data"},
           f"eventId={envelope.get('eventId')} type={envelope.get('eventType')} keys={sorted(envelope)} createdAt={envelope.get('createdAt')}")
    # Foundation 成功后删除任务行：行消失即 SUCCEEDED；ERROR/FAILED 会保留。
    left = wait_for(lambda: tasks(event_id) == [] and "gone", 15)
    record(f"{event_type} acquiring 任务成功完成（行已删除，无 ERROR/FAILED）", left == "gone", f"remaining={tasks(event_id)}")
    events, deliveries = wait_for(lambda: (lambda e: e if e[1] and all(d[1] == "SUCCEEDED" for d in e[1]) else None)(
        platform_rows(event_id)), 15) or platform_rows(event_id)
    record(f"{event_type} Platform 事件唯一（来源 {CALLER}）、投递 SUCCEEDED", len(events) == 1 and events[0][2] == CALLER
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
    record(f"{label} data.object 与 acquiring 查询一致", not diffs, f"diffs={diffs} formatOnlyDiffs={notes}")


# ---------- 真实卡支付（Consumer 经 Hosted Checkout Customer API） ----------

def checkout_token(checkout_url):
    return (checkout_url or "").rstrip("/").rsplit("/", 1)[-1]


def pay_checkout(checkout_url):
    """一次真实卡支付；返回 (submission dict | None, 说明)。不打印卡值。"""
    token = checkout_token(checkout_url)
    view = wait_for(lambda: (lambda r: r[1] if r[0] == 200 else None)(customer("GET", f"/{token}")), 30, 1)
    if not view:
        status, body = customer("GET", f"/{token}")
        return None, f"结账不可读 HTTP {status} code={(body or {}).get('code')}"
    if ((view.get("consumer") or {}).get("status")) != "COMPLETE":
        status, saved = customer("PUT", f"/{token}/consumer", {
            "fullName": "Stage Five Consumer", "email": f"consumer-{uuid.uuid4().hex[:8]}@example.invalid",
            "billingAddress": {"countryCode": "US", "addressLine1": "1 Test Street", "city": "New York", "postalCode": "10001"}})
        if status != 200:
            return None, f"保存消费者资料 HTTP {status} code={(saved or {}).get('code')}"
    view = wait_for(lambda: (lambda r: r[1] if r[0] == 200 and ((r[1] or {}).get("legal") or {}).get("consumerContractId")
                             else None)(customer("GET", f"/{token}")), 30, 1)
    if not view:
        body = customer("GET", f"/{token}")[1] or {}
        return None, (f"结账合同未就绪 preparation={body.get('preparationStatus')} allowed={body.get('allowedActions')} "
                      f"payment={(body.get('payment') or {}).get('availability')}")
    contract_id = (view.get("legal") or {}).get("consumerContractId")
    status, submitted = customer("POST", f"/{token}/payments",
                                 {"legalConfirmation": {"consumerContractId": contract_id, "confirmed": True}, "card": TEST_CARD})
    if status not in (200, 202) or not (submitted or {}).get("submissionId"):
        return None, f"提交支付 HTTP {status} code={(submitted or {}).get('code')}"
    submission_id = submitted["submissionId"]
    final = wait_for(lambda: (lambda r: r[1] if r[0] == 200 and (r[1] or {}).get("status") not in
                              ("PROCESSING", "AWAITING_CARD", None) else None)(
        customer("GET", f"/{token}/payment-submissions/{submission_id}")), 60, 1)
    final = final or customer("GET", f"/{token}/payment-submissions/{submission_id}")[1] or submitted
    return final, (f"submissionId={submission_id} attemptId={final.get('attemptId')} status={final.get('status')} "
                   f"last4={(final.get('card') or {}).get('last4')} failure={(final.get('failure') or {}).get('code')}")


def paid_order(client_id, label="paid"):
    """建结账并真实支付；合成 PayerMax 按订单号（尝试 ID）末位 2/3/4 返回参数错误 / 系统繁忙 / 错签名，失败时换新订单重试。"""
    notes = []
    for attempt in range(1, PAY_TRIES + 1):
        order_no, checkout_url = create_checkout(client_id, f"{label}-{attempt}")
        submission, note = pay_checkout(checkout_url)
        notes.append(f"#{attempt} {note}")
        if submission and submission.get("status") == "SUCCEEDED":
            record("真实卡支付成功（Hosted Checkout → Payment → channel-connector → Platform PAY → 合成 PayerMax）", True,
                   f"orderNo={order_no} tries={attempt} {note}")
            return order_no, submission
    record("真实卡支付成功（Hosted Checkout → Payment → channel-connector → Platform PAY → 合成 PayerMax）", False,
           " ; ".join(notes))
    raise Stop("真实支付未成功")


def flow_cancel(receiver, secret, client_id):
    order_no, _ = create_checkout(client_id, "cancel")
    status, body = commerce("POST", f"/orders/{order_no}/cancel", client_id)
    record("AC-03 取消未支付订单", status == 200 and (body or {}).get("orderStatus") == "CANCELLED", f"HTTP {status} body={body}")
    event_id = f"evt_{order_no}_cancelled"
    envelope = check_delivery(receiver, secret, event_id, "order.cancelled", client_id)
    again, again_body = commerce("POST", f"/orders/{order_no}/cancel", client_id)
    time.sleep(3)
    record("AC-03 重复取消不产生第二个事件", len(tasks(event_id)) == 0 and len(platform_rows(event_id)[0]) == 1
           and len(received(receiver, event_id)) == 1,
           f"HTTP {again} status={(again_body or {}).get('orderStatus')} tasks={len(tasks(event_id))} received={len(received(receiver, event_id))}")
    if not envelope:
        return
    _, order = commerce("GET", f"/orders/{order_no}", client_id)
    row = order_row(order_no)
    obj = envelope["data"]["object"]
    compare("AC-03 order.cancelled", obj, [
        ("orderNo", order["orderNo"], "str"), ("clientOrderId", order["clientOrderId"], "str"),
        ("orderStatus", order["orderStatus"], "str"), ("checkoutStatus", (order.get("checkout") or {}).get("status"), "str"),
        ("cancelledAt", body.get("cancelledAt"), "instant"), ("version", row["version"], "int")])
    record("AC-03 结构化原因码", obj.get("reasonCode") == "CLIENT_REQUESTED" and row["reasonCode"] == "CLIENT_REQUESTED",
           f"reasonCode={obj.get('reasonCode')} db={row['reasonCode']}")


def flow_paid(receiver, secret, client_id):
    order_no, submission = paid_order(client_id)
    attempt_id = submission.get("attemptId")
    paid = wait_for(lambda: order_row(order_no)["status"] == "PAID", 30)
    projection = payment_projection(order_row(order_no)["orderId"]) or {}
    record("AC-01 PaymentSucceeded v2 经 payment.events 落地为 acquiring 订单 PAID",
           paid and projection.get("successfulAttemptId") == str(attempt_id),
           f"status={order_row(order_no)['status']} projection={projection} attemptId={attempt_id}")
    record("PaymentSucceeded v2 由 acquiring 与 card 各消费登记一次",
           len(wait_for(lambda: consumed("acquiring", attempt_id), 20)) == 1 and len(wait_for(lambda: consumed("card", attempt_id), 20)) == 1,
           f"acquiring={len(consumed('acquiring', attempt_id))} card={len(consumed('card', attempt_id))}")
    event_id = f"evt_{order_no}_paid"
    envelope = check_delivery(receiver, secret, event_id, "order.paid", client_id)
    status, again = pay_checkout_again(order_no, client_id)
    time.sleep(3)
    record("AC-02 已支付订单不能再次支付，且不产生第二个 order.paid",
           status != 201 and len(tasks(event_id)) == 0 and len(received(receiver, event_id)) == 1 and len(platform_rows(event_id)[0]) == 1,
           f"repay={again} tasks={len(tasks(event_id))} received={len(received(receiver, event_id))} "
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


def pay_checkout_again(order_no, client_id):
    """已支付订单再次提交：Checkout 应不再允许支付（返回非 200/202 或无新提交）。"""
    _, order = commerce("GET", f"/orders/{order_no}", client_id)
    url = ((order or {}).get("checkout") or {}).get("url")
    if not url:
        return 0, "checkoutUrl 已不再返回"
    token = checkout_token(url)
    view = customer("GET", f"/{token}")[1] or {}
    contract_id = (view.get("legal") or {}).get("consumerContractId") or "1"
    status, body = customer("POST", f"/{token}/payments",
                            {"legalConfirmation": {"consumerContractId": contract_id, "confirmed": True}, "card": TEST_CARD})
    return status, f"HTTP {status} status={(body or {}).get('status')} code={(body or {}).get('code')}"


def flow_refund(receiver, secret, client_id, order_no):
    request = {"clientRefundId": "refund-" + uuid.uuid4().hex[:8], "amount": "10.00", "reasonCode": "REQUESTED_BY_CUSTOMER"}
    status, created = commerce("POST", f"/orders/{order_no}/refunds", client_id, request)
    refund_no = (created or {}).get("refundRequestNo")
    record("AC-04 创建退款", status == 201 and refund_no, f"HTTP {status} refundRequestNo={refund_no} code={(created or {}).get('code')}")
    if not refund_no:
        return
    _, refund_now = commerce("GET", f"/refunds/{refund_no}", client_id)
    event_id = f"evt_{refund_no}_created"
    envelope = check_delivery(receiver, secret, event_id, "refund.created", client_id)
    again, replay = commerce("POST", f"/orders/{order_no}/refunds", client_id, request)
    time.sleep(3)
    record("AC-04 同一 clientRefundId 重放不产生第二个事件",
           again in (200, 201) and (replay or {}).get("refundRequestNo") == refund_no and len(tasks(event_id)) == 0
           and len(platform_rows(event_id)[0]) == 1 and len(received(receiver, event_id)) == 1,
           f"HTTP {again} sameRefund={(replay or {}).get('refundRequestNo') == refund_no} tasks={len(tasks(event_id))} "
           f"received={len(received(receiver, event_id))}")
    if envelope:
        obj = envelope["data"]["object"]
        fields = ["refundRequestNo", "clientRefundId", "requestOrigin", "orderNo", "amount", "currency", "refundStatus",
                  "reasonCode", "statusReasonCode"]
        expected = [(f, refund_now.get(f), "str") for f in fields]
        expected += [(f, refund_now.get(f), "instant") for f in ("createdAt", "completedAt", "updatedAt")]
        expected.append(("version", refund_now.get("version"), "int"))
        compare("AC-04 refund.created（对比创建后立即 GET）", obj, expected)
        record("AC-04 refund.created 键集合与 GET Refund 相同", set(obj) == set(refund_now), f"event={sorted(obj)} get={sorted(refund_now)}")
    refund_outcome(client_id, refund_no)
    return refund_no


def refund_outcome(client_id, refund_no):
    """任务卡要求退款经 PAY-I04 真实链路到 RefundSucceeded；本仓未实现，记 GAP 并给出证据。"""
    time.sleep(5)
    _, later = commerce("GET", f"/refunds/{refund_no}", client_id)
    submissions = acquiring_sql("SELECT event_type,execute_status,execute_count,IFNULL(last_error_code,''),"
                                "IFNULL(LEFT(last_error_summary,80),'') FROM t_compensation_event "
                                f"WHERE event_type<>'CLIENT_WEBHOOK_REQUESTED' AND payload_json LIKE '%{refund_no}%'")
    status = (later or {}).get("refundStatus")
    if status == "SUCCEEDED":
        record("退款经 Payment 真实链路 SUCCEEDED（RefundSucceeded）", True, f"refundStatus={status}")
        return
    gap("退款经 PAY-I04 真实链路到 RefundSucceeded",
        f"refundStatus={status} version={(later or {}).get('version')} submissionTasks={submissions}；"
        "acquiring PaymentRefundClient = UnavailablePaymentRefundClient（'Payment 退款接口尚未接入'），Payment 无退款接口，无 RefundSucceeded 发布方")


def summary(data):
    print("DATA", json.dumps(data))
    passed = sum(1 for r in RESULTS if r[1] == "PASS")
    print(f"\n合计 {passed}/{len(RESULTS)} 通过；GAP {len(GAPS)} 项（已知生产缺口，不计入）")
    return passed == len(RESULTS)


def main():
    stamp = time.strftime("%Y%m%d%H%M%S")
    data = {}
    try:
        client, ops, app = ws_flow.approved_client(stamp)
        client_id = app["clientId"]
        context = dev.enter(client, client_id)
        record("进入工作区", context.get("contextType") == "TENANT", f"tenantId={context.get('tenantId')} clientId={client_id}")
        receiver = dev.Receiver()
        status, _, created = dev.raw(client, dev.DEV + "/webhook-endpoints", "POST", {"url": receiver.url, "eventTypes": EVENT_TYPES})
        secret = (created or {}).get("signingSecret") or ""
        record("端点：本机接收端、订阅 3 种事件", status == 201 and secret.startswith("whsec_"),
               f"HTTP {status} endpointId={(created or {}).get('endpointId')} url={receiver.url} code={(created or {}).get('code')}")
        if status != 201:
            raise Stop("端点未创建")
        data.update(clientId=client_id, endpointId=created.get("endpointId"), tenantId=context.get("tenantId"))
        flow_cancel(receiver, secret, client_id)
        order_no = flow_paid(receiver, secret, client_id)
        data["orderNoPaid"] = order_no
        data["refundRequestNo"] = flow_refund(receiver, secret, client_id, order_no)
        others = [json.loads(r["body"]).get("eventType") for r in receiver.requests]
        record("接收端总数 = 3（无多余投递）", len(receiver.requests) == 3, f"eventTypes={others}")
        receiver.server.shutdown()
    except Stop as stop:
        print(f"中止于：{stop}")
        RESULTS.append(("中止", "FAIL", str(stop)))
    except Failure as failure:
        RESULTS.append(("中止（HTTP）", "FAIL", problem(failure)))
        print(f"FAIL 中止（HTTP） | {problem(failure)}")
    raise SystemExit(0 if summary(data) else 1)


if __name__ == "__main__":
    main()
