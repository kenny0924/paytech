"""acquiring 订单与会话过期收尾 · 阶段 5 本机 e2e 验收（真实进程、真实链路）。

链路：Client 侧经私网直连 acquiring Client API（带 Gateway 可信 Header，webhook_flow.commerce）建结账；Consumer 侧经 Customer API
提交卡支付（acquiring → Payment → card / risk-decision / channel-connector → Platform 标准 API → 合成 PayerMax）。
到期：脚本把本次创建订单的 t_commerce_order.expires_at 回拨到过去（用户授权，只按 order_no 改本次订单）。
扫描：本机无 XXL Admin，按 XXL Admin 协议向 acquiring 执行器 127.0.0.1:19992 POST /run 触发 orderExpiryScan（分片 0/1），
     从执行器日志目录读取本轮汇总。
Webhook：本机无接收端与端点订阅，order.expired 只核对到 acquiring 补偿任务完成 + Platform t_webhook_event 受理与冻结载荷。
S2 收敛：订单到期后 acquiring 消费者轮询先抛 CHECKOUT_EXPIRED、不再调用 Payment 刷新，payment 本机 XXL 未启用，
     因此以 acquiring-service 身份直接调用 Payment 刷新接口（与 acquiring 轮询调用的是同一接口）。

用法：python3 -I stage5_expiry.py [s1|s2|s3|s4|all]...（缺省 all）；结果合并写入 stage5-expiry.json。
不打印口令、Token、卡号；Checkout Public Token 只在内存中用于泄漏检索。
"""
import glob
import json
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAIN = HERE.parents[3]
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as h  # noqa: E402
import webhook_flow as w  # noqa: E402

RUNTIME = MAIN / "local-environment/e2e/runtime/expiry"
LOGS = MAIN / "local-environment/logs"
XXL_TOKEN = (RUNTIME / "xxl-token").read_text().strip()
XXL_LOG_DIR = RUNTIME / "xxl-acquiring"
OUT = HERE / "stage5-expiry.json"
CLIENT_ID = "237161687952666625"  # mor_platform_v2e2e.t_access_tenant：CLIENT、owner_status=ACTIVE
MOCK = "http://127.0.0.1:18190"
PANS = [w.TEST_CARD["pan"], "4111111111111111", "4012888888881881"]
results = {}
tokens = set()  # 本次 Checkout Public Token，只用于泄漏检索


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def record(name, data):
    results[name] = data
    print(name, json.dumps(data, ensure_ascii=False)[:1800])


def asql(statement):
    return h.sql("acquiring", statement)


# ---------- acquiring / Payment / Platform 事实 ----------

def order_facts(order_no):
    rows = asql("SELECT order_id, order_status, IFNULL(status_reason_code,''), version, DATE_FORMAT(expires_at,'%Y-%m-%dT%H:%i:%s.%fZ'), "
                "IFNULL(DATE_FORMAT(termination_requested_at,'%Y-%m-%dT%H:%i:%s.%fZ'),''), IFNULL(paid_at,''), IFNULL(cancelled_at,''), "
                f"DATE_FORMAT(update_time,'%Y-%m-%dT%H:%i:%s.%fZ') FROM t_commerce_order WHERE order_no='{order_no}'")
    keys = ("orderId", "status", "reasonCode", "version", "expiresAt", "terminationRequestedAt", "paidAt", "cancelledAt", "updateTime")
    facts = dict(zip(keys, rows[0]))
    session = asql(f"SELECT session_status, version FROM t_checkout_session WHERE order_id={facts['orderId']}")
    facts["session"] = dict(zip(("status", "version"), session[0])) if session else None
    projection = asql("SELECT payment_intent_id, intent_status, IFNULL(last_attempt_id,''), IFNULL(last_attempt_status,''), "
                      f"IFNULL(successful_attempt_id,''), version FROM t_order_payment_projection WHERE order_id={facts['orderId']}")
    facts["projection"] = dict(zip(("intentId", "intentStatus", "lastAttemptId", "lastAttemptStatus", "successfulAttemptId", "version"),
                                   projection[0])) if projection else None
    intent = h.sql("payment", "SELECT payment_intent_id, intent_status, IFNULL(active_attempt_id,''), IFNULL(successful_attempt_id,''), "
                   f"version FROM t_payment_intent WHERE source_type='COMMERCE_ORDER' AND source_id='{facts['orderId']}'")
    facts["paymentIntent"] = dict(zip(("intentId", "status", "activeAttemptId", "successfulAttemptId", "version"), intent[0])) if intent else None
    return facts


def attempts(intent_id):
    return [dict(zip(("attemptId", "status"), r)) for r in h.sql(
        "payment", f"SELECT payment_attempt_id, attempt_status FROM t_payment_attempt WHERE payment_intent_id={intent_id} ORDER BY create_time")]


def webhook_facts(order_no, kind="expired"):
    event_id = f"evt_{order_no}_{kind}"
    task_rows = w.tasks(event_id)
    events = h.platform_sql("SELECT event_id, event_type, source_system, source_event_key, source_version, payload FROM t_webhook_event "
                            f"WHERE source_event_key='{event_id}'")
    deliveries = h.platform_sql(f"SELECT COUNT(*) FROM t_webhook_delivery WHERE webhook_event_id={int(events[0][0])}")[0][0] if events else None
    return {"eventId": event_id, "acquiringTasks": task_rows,
            "platformEvents": [{"eventType": e[1], "sourceSystem": e[2], "sourceEventKey": e[3], "sourceVersion": e[4]} for e in events],
            "platformDeliveries": deliveries, "payload": json.loads(events[0][5]) if events else None}


def wait_webhook(order_no, timeout=40):
    w.wait_for(lambda: not webhook_facts(order_no)["acquiringTasks"] and webhook_facts(order_no)["platformEvents"], timeout, 1)
    return webhook_facts(order_no)


def compensation_rows(order_id):
    return asql("SELECT event_type, IFNULL(sub_event_type,''), event_key, execute_status, execute_count FROM t_compensation_event "
                f"WHERE event_key LIKE '%{order_id}%' OR payload_json LIKE '%{order_id}%'")


# ---------- 动作 ----------

def create_checkout(label, unit_amount="10.00"):
    client_order_id = f"exp-{label}-{time.strftime('%H%M%S')}-{uuid.uuid4().hex[:6]}"
    status, body = w.commerce("POST", "/checkouts", CLIENT_ID, {
        "clientOrderId": client_order_id, "currency": "USD", "consumerCountryCode": "US",
        "items": [{"clientItemId": "line-1", "name": "Stage five item", "productTypeCode": "SERVICE", "taxCategoryCode": "STANDARD",
                   "quantity": "1", "unitAmount": unit_amount}]})
    assert status == 201, (status, body)
    tokens.add(w.checkout_token(body.get("checkoutUrl")))
    return body["orderNo"], body.get("checkoutUrl"), {"http": status, "orderStatus": body.get("orderStatus"),
                                                       "checkoutStatus": (body.get("checkout") or {}).get("status") or body.get("checkoutStatus"),
                                                       "total": (body.get("amounts") or {}).get("totalAmount"), "expiresAt": body.get("expiresAt")}


def backdate(order_no, minutes=2):
    """用户授权的唯一直接写库：把本次订单的 expires_at 回拨到 now - minutes（按 order_no 定位）。"""
    asql(f"UPDATE t_commerce_order SET expires_at = DATE_SUB(UTC_TIMESTAMP(), INTERVAL {int(minutes)} MINUTE) WHERE order_no='{order_no}'")
    return asql(f"SELECT DATE_FORMAT(expires_at,'%Y-%m-%dT%H:%i:%s.%fZ') FROM t_commerce_order WHERE order_no='{order_no}'")[0][0]


def xxl_scan(timeout=60):
    """按 XXL Admin 协议触发 acquiring 执行器 orderExpiryScan，等执行器日志出现本轮汇总。"""
    log_id = int(time.time() * 1000)
    body = {"jobId": 9001, "executorHandler": "orderExpiryScan", "executorParams": "", "executorBlockStrategy": "SERIAL_EXECUTION",
            "executorTimeout": 0, "logId": log_id, "logDateTime": log_id, "glueType": "BEAN", "glueSource": "", "glueUpdatetime": 0,
            "broadcastIndex": 0, "broadcastTotal": 1}
    request = urllib.request.Request("http://127.0.0.1:19992/run", method="POST", data=json.dumps(body).encode(),
                                     headers={"XXL-JOB-ACCESS-TOKEN": XXL_TOKEN, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        accepted = json.loads(response.read())
    summary, handle_code = None, None
    deadline = time.time() + timeout
    while time.time() < deadline and handle_code is None:
        for path in glob.glob(str(XXL_LOG_DIR / "*" / f"{log_id}.log")):
            text = Path(path).read_text(errors="replace")
            match = re.search(r"order expiry scan[^\n]*", text)
            summary = match.group(0) if match else summary
            ended = re.search(r"execute end[^\n]*handleCode=(\d+)", text)
            handle_code = int(ended.group(1)) if ended else None
        if handle_code is None:
            time.sleep(1)
    return {"logId": log_id, "runAccepted": accepted, "handleCode": handle_code, "summary": summary}


def get_order(order_no):
    status, body = w.commerce("GET", f"/orders/{order_no}", CLIENT_ID)
    body = body or {}
    checkout = body.get("checkout") or {}
    return {"http": status, "orderStatus": body.get("orderStatus"), "paymentStatus": body.get("paymentStatus"),
            "checkoutStatus": checkout.get("status"), "checkoutUrlPresent": bool(checkout.get("url") or body.get("checkoutUrl")),
            "version": body.get("version"), "expiresAt": body.get("expiresAt"), "clientOrderId": body.get("clientOrderId"),
            "keys": sorted(body)}


def cancel(order_no):
    status, body = w.commerce("POST", f"/orders/{order_no}/cancel", CLIENT_ID)
    return {"http": status, "code": (body or {}).get("code"), "orderStatus": (body or {}).get("orderStatus")}


def customer_view(checkout_url):
    status, body = w.customer("GET", "/" + w.checkout_token(checkout_url))
    return {"http": status, "code": (body or {}).get("code"), "status": (body or {}).get("status")}


def customer_submit(checkout_url, contract_id="1"):
    token = w.checkout_token(checkout_url)
    status, body = w.customer("POST", f"/{token}/payments",
                              {"legalConfirmation": {"consumerContractId": contract_id, "confirmed": True}, "card": w.TEST_CARD})
    return {"http": status, "code": (body or {}).get("code"), "status": (body or {}).get("status")}


def check_payload(order_no, facts, webhook, order_view):
    payload = webhook["payload"] or {}
    obj = (payload.get("data") or {}).get("object") or {}
    expected = {"eventId": webhook["eventId"], "eventType": "order.expired", "orderNo": order_no, "orderStatus": "EXPIRED",
                "checkoutStatus": "EXPIRED", "reasonCode": "EXPIRY_REACHED", "clientOrderId": order_view.get("clientOrderId")}
    actual = {"eventId": payload.get("eventId"), "eventType": payload.get("eventType"), "orderNo": obj.get("orderNo"),
              "orderStatus": obj.get("orderStatus"), "checkoutStatus": obj.get("checkoutStatus"), "reasonCode": obj.get("reasonCode"),
              "clientOrderId": obj.get("clientOrderId")}
    diffs = {k: (actual[k], v) for k, v in expected.items() if actual[k] != v}
    expired_at = w.instant(obj.get("expiredAt")) if obj.get("expiredAt") else None
    db_expires = w.instant(facts["expiresAt"])
    if expired_at != db_expires:
        diffs["expiredAt"] = (obj.get("expiredAt"), facts["expiresAt"])
    if str(obj.get("version")) != str(facts["version"]):
        diffs["version"] = (obj.get("version"), facts["version"])
    if webhook["platformEvents"] and str(webhook["platformEvents"][0]["sourceVersion"]) != str(facts["version"]):
        diffs["sourceVersion"] = (webhook["platformEvents"][0]["sourceVersion"], facts["version"])
    return {"objectKeys": sorted(obj), "envelopeKeys": sorted(payload), "expiredAt": obj.get("expiredAt"), "version": obj.get("version"),
            "diffs": diffs, "ok": not diffs and set(obj) == {"orderNo", "clientOrderId", "orderStatus", "checkoutStatus", "reasonCode",
                                                              "expiredAt", "version"}}


# ---------- 场景 ----------

def s1():
    """S1 无 Intent 本地过期（AC-01、AC-06 受理部分、AC-08、AC-09）。"""
    out = {}
    order_no, url, created = create_checkout("s1")
    out["orderNo"] = order_no
    out["created"] = created
    out["beforeExpiry"] = {"facts": order_facts(order_no), "get": get_order(order_no)}
    out["backdatedExpiresAt"] = backdate(order_no)
    out["scan1"] = xxl_scan()
    webhook = wait_webhook(order_no)
    facts = order_facts(order_no)
    view = get_order(order_no)
    out["afterScan1"] = {"facts": facts, "get": view, "webhook": {k: v for k, v in webhook.items() if k != "payload"},
                         "payloadCheck": check_payload(order_no, facts, webhook, view)}
    out["cancelAfterExpiry"] = cancel(order_no)
    out["customerViewAfterExpiry"] = customer_view(url)
    out["customerSubmitAfterExpiry"] = customer_submit(url)
    out["scan2"] = xxl_scan()
    time.sleep(3)
    out["afterScan2"] = {"facts": order_facts(order_no), "webhook": {k: v for k, v in webhook_facts(order_no).items() if k != "payload"},
                         "cancelledEvent": webhook_facts(order_no, "cancelled")["platformEvents"]}
    a, b = out["afterScan1"]["facts"], out["afterScan2"]["facts"]
    out["verdict"] = {
        "AC01_orderAndSessionExpired": a["status"] == "EXPIRED" and a["reasonCode"] == "EXPIRY_REACHED" and (a["session"] or {}).get("status") == "EXPIRED",
        "AC01_get": view["orderStatus"] == "EXPIRED" and not view["checkoutUrlPresent"] and view["paymentStatus"] == "NOT_STARTED",
        "noIntent": a["paymentIntent"] is None and a["projection"] is None,
        "AC06_accepted": not webhook["acquiringTasks"] and len(webhook["platformEvents"]) == 1
                         and webhook["platformEvents"][0]["eventType"] == "order.expired"
                         and webhook["platformEvents"][0]["sourceSystem"] == "acquiring-service"
                         and out["afterScan1"]["payloadCheck"]["ok"],
        "AC08_cancel409": out["cancelAfterExpiry"]["http"] == 409 and out["cancelAfterExpiry"]["code"] == "ORDER_NOT_CANCELLABLE",
        "AC08_customerExpired": out["customerViewAfterExpiry"]["code"] == "CHECKOUT_EXPIRED",
        "AC08_submitRejected": out["customerSubmitAfterExpiry"]["http"] >= 400,
        "AC09_secondScanNoWrite": a["version"] == b["version"] and a["updateTime"] == b["updateTime"]
                                  and len(out["afterScan2"]["webhook"]["platformEvents"]) == 1 and not out["afterScan2"]["webhook"]["acquiringTasks"],
        "noCancelledEvent": not out["afterScan2"]["cancelledEvent"]}
    record("S1", out)


def mock_control_available():
    try:
        request = urllib.request.Request(MOCK + "/__control/query", method="POST", data=json.dumps({"outTradeNo": "-", "status": "-"}).encode())
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status == 200
    except urllib.error.HTTPError:
        return False
    except OSError:
        return False


def mock_query(attempt, status):
    request = urllib.request.Request(MOCK + "/__control/query", method="POST",
                                     data=json.dumps({"outTradeNo": attempt, "status": status}).encode())
    with urllib.request.urlopen(request, timeout=5) as response:
        assert response.status == 200


def payment_refresh(intent_id, submission_id):
    status, text = h.private_call("payment", "POST", f"/payment-execution/v1/intents/{intent_id}/submissions/{submission_id}/refresh",
                                  body={"clientId": CLIENT_ID})
    try:
        body = json.loads(text) if text else {}
    except ValueError:
        body = {}
    return {"http": status, "status": body.get("status"), "failureCode": body.get("failureCode"), "code": body.get("code")}


def s2():
    """S2 3DS 挑战中到期保持 OPEN，挑战失败后过期（AC-02、AC-03、AC-11、B1）。标记金额 333.00 USD 由 PAYMENT_ATTEMPT_RISK 发布要求 3DS。"""
    if not mock_control_available():
        record("S2", {"executed": False, "reason": "18190 合成 PayerMax 无 /__control/query（运行的不是 3DS 扩展版），无法制造挑战与设定查单结果"})
        return
    out = {}
    order_no, url, created = create_checkout("s2", "333.00")
    out["orderNo"], out["created"] = order_no, created
    submission, note = w.pay_checkout(url)
    out["submission"] = {"status": (submission or {}).get("status"), "note": re.sub(r"last4=\S+", "last4=*", note),
                         "actionType": ((submission or {}).get("nextAction") or (submission or {}).get("action") or {}).get("type")}
    facts = order_facts(order_no)
    out["beforeExpiry"] = facts
    intent_id = (facts["projection"] or {}).get("intentId")
    submission_id = (submission or {}).get("submissionId")
    attempt_id = str((submission or {}).get("attemptId") or (facts["projection"] or {}).get("lastAttemptId"))
    if (submission or {}).get("status") != "REQUIRES_ACTION":
        out["executed"] = False
        out["reason"] = "提交未进入 REQUIRES_ACTION（3DS 挑战），见 submission"
        record("S2", out)
        return
    mock_query(attempt_id, "PENDING")
    out["backdatedExpiresAt"] = backdate(order_no)
    out["scan1"] = xxl_scan()
    time.sleep(2)
    pending = order_facts(order_no)
    out["afterScan1"] = {"facts": pending, "attempts": attempts(intent_id), "webhook": {k: v for k, v in webhook_facts(order_no).items() if k != "payload"}}
    out["resubmitAfterExpiry"] = customer_submit(url)
    out["customerPollAfterExpiry"] = (lambda r: {"http": r[0], "code": (r[1] or {}).get("code")})(
        w.customer("GET", f"/{w.checkout_token(url)}/payment-submissions/{submission_id}"))
    mock_query(attempt_id, "FAILED")
    refreshes = []
    for _ in range(4):
        time.sleep(6)  # 越过 Payment 5 秒查单限频窗口
        refreshes.append(payment_refresh(intent_id, submission_id))
        if refreshes[-1]["status"] == "FAILED":
            break
    out["paymentRefresh"] = refreshes
    out["afterFailure"] = {"facts": order_facts(order_no), "attempts": attempts(intent_id)}
    out["scan2"] = xxl_scan()
    webhook = wait_webhook(order_no)
    final = order_facts(order_no)
    view = get_order(order_no)
    out["afterScan2"] = {"facts": final, "get": view, "webhook": {k: v for k, v in webhook.items() if k != "payload"},
                         "payloadCheck": check_payload(order_no, final, webhook, view)}
    out["scan3"] = xxl_scan()
    time.sleep(2)
    out["terminateAgain"] = (lambda r: {"http": r[0], "body": r[1][:300]})(h.private_call(
        "payment", "POST", f"/payment-execution/v1/intents/{intent_id}/terminate", body={"clientId": CLIENT_ID, "reason": "ORDER_EXPIRED"}))
    out["afterScan3"] = {"facts": order_facts(order_no), "webhook": {k: v for k, v in webhook_facts(order_no).items() if k != "payload"}}
    p, f = pending, final
    out["verdict"] = {
        "B1_AC03_pendingKeepsOpen": p["status"] == "OPEN" and bool(p["terminationRequestedAt"]) and (p["paymentIntent"] or {}).get("status") == "OPEN"
                                    and not out["afterScan1"]["webhook"]["platformEvents"] and not out["afterScan1"]["webhook"]["acquiringTasks"],
        "AC03_resubmitRejected": out["resubmitAfterExpiry"]["http"] >= 400,
        "attemptFailed": any(a["attemptId"] == attempt_id and a["status"] == "FAILED" for a in out["afterFailure"]["attempts"]),
        "AC02_intentExpired": (f["paymentIntent"] or {}).get("status") == "EXPIRED",
        "AC02_orderSessionProjectionExpired": f["status"] == "EXPIRED" and f["reasonCode"] == "EXPIRY_REACHED"
                                             and (f["session"] or {}).get("status") == "EXPIRED" and (f["projection"] or {}).get("intentStatus") == "EXPIRED",
        "AC02_get": view["orderStatus"] == "EXPIRED" and view["paymentStatus"] == "EXPIRED" and not view["checkoutUrlPresent"],
        "AC06_accepted": len(webhook["platformEvents"]) == 1 and not webhook["acquiringTasks"] and out["afterScan2"]["payloadCheck"]["ok"],
        "AC11_terminateAgainAlreadyTerminated": '"ALREADY_TERMINATED"' in out["terminateAgain"]["body"]
                                                and order_facts(order_no)["paymentIntent"]["version"] == f["paymentIntent"]["version"],
        "AC09_scan3NoWrite": out["afterScan3"]["facts"]["version"] == f["version"] and len(out["afterScan3"]["webhook"]["platformEvents"]) == 1}
    record("S2", out)


def s3():
    """S3 已支付订单不受影响（AC-04 的“只能收敛为 PAID”）。"""
    out = {}
    w.CLIENT_ID = CLIENT_ID
    order_no, submission = w.paid_order(CLIENT_ID, "exp-paid")
    out["orderNo"] = order_no
    out["submissionStatus"] = submission.get("status")
    paid = w.wait_for(lambda: order_facts(order_no)["status"] == "PAID" and order_facts(order_no), 40, 1)
    out["paid"] = paid or order_facts(order_no)
    out["backdatedExpiresAt"] = backdate(order_no)
    out["scan"] = xxl_scan()
    time.sleep(2)
    after = order_facts(order_no)
    out["afterScan"] = {"facts": after, "get": get_order(order_no), "webhook": {k: v for k, v in webhook_facts(order_no).items() if k != "payload"},
                        "paidEvent": webhook_facts(order_no, "paid")["platformEvents"]}
    b = out["paid"]
    out["verdict"] = {
        "stillPaid": after["status"] == "PAID" and after["version"] == b["version"] and after["updateTime"] == b["updateTime"],
        "noTerminationRequested": after["terminationRequestedAt"] == "",
        "intentSucceededUnchanged": (after["paymentIntent"] or {}).get("status") == "SUCCEEDED"
                                    and after["paymentIntent"]["version"] == (b["paymentIntent"] or {}).get("version"),
        "noOrderExpired": not out["afterScan"]["webhook"]["platformEvents"] and not out["afterScan"]["webhook"]["acquiringTasks"]}
    record("S3", out)


def s4():
    """S4 日志泄漏检索：本次时间窗口内 acquiring.log / payment.log 不含卡号、CVV、acsSecret、Checkout Public Token；过期相关 WARN 只含 orderId / intentId。"""
    start = min(r["startedAt"] for r in previous_runs() + results.get("_runs", [])) if (previous_runs() or results.get("_runs")) else now()
    out = {"windowStart": start}
    # 本窗口内创建的会话 Token（acquiring 库中为原值），只在内存中使用
    known_tokens = set(tokens) | {r[0] for r in asql("SELECT public_token FROM t_checkout_session "
                                                     f"WHERE create_time >= '{start[:10]} {start[11:26]}'")}
    for name in ("acquiring", "payment"):
        lines, inside = [], False
        for line in (LOGS / f"{name}.log").read_text(errors="replace").splitlines():
            if re.match(r"^\d{4}-\d\d-\d\dT", line):
                inside = line[:23] >= start[:23]
            if inside:
                lines.append(line)
        text = "\n".join(lines)
        hits = {"pan": sum(text.count(p) for p in PANS), "cvvKey": len(re.findall(r'(?i)"cvv"|cvv=', text)),
                "acsSecret": text.count("acsSecret"), "checkoutPublicToken": sum(text.count(t) for t in known_tokens if t),
                "tokenShape": len(re.findall(r"chk_[A-Za-z0-9]{20,}", text)),
                "redirectUrl": len(re.findall(r"https?://[^\s]*(3ds|acs|challenge)[^\s]*", text, re.I))}
        warns = [l for l in lines if " WARN " in l and re.search(r"过期|到期|终止|expir|terminat", l, re.I)]
        redacted = []
        for l in warns:
            for t in known_tokens:
                if t:
                    l = l.replace(t, "<token>")
            redacted.append(l[l.find(" : ") + 3:][:300] if " : " in l else l[:300])
        out[name] = {"linesInWindow": len(lines), "hits": hits, "expiryWarnCount": len(warns), "expiryWarns": redacted[:20],
                     "warnFieldNames": sorted({m for l in warns for m in re.findall(r"(\w+)=", l)})}
    out["checkedTokens"] = len(known_tokens)
    out["verdict"] = {"noLeak": all(sum(out[n]["hits"].values()) == 0 for n in ("acquiring", "payment")),
                      "warnsOnlyIds": all(set(out[n]["warnFieldNames"]) <= {"orderId", "intentId", "decision", "error", "clientId", "terminationRequestedAt",
                                                                             "pendingSince", "attemptId"} for n in ("acquiring", "payment"))}
    record("S4", out)


def previous():
    return json.loads(OUT.read_text()) if OUT.exists() else {}


def previous_runs():
    return previous().get("_runs", [])


SCENARIOS = {"s1": s1, "s2": s2, "s3": s3, "s4": s4}


def main():
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        print(__doc__)
        return
    names = sys.argv[1:] or ["all"]
    if names == ["all"]:
        names = list(SCENARIOS)
    for name in names:
        started = now()
        results.setdefault("_runs", []).append({"scenario": name, "startedAt": started})
        try:
            SCENARIOS[name]()
        except (AssertionError, KeyError, IndexError, TypeError, w.Stop, RuntimeError) as failure:
            record(name.upper() + "_error", {"error": re.sub(r"\d{13,19}", lambda m: m.group(0) if m.group(0) not in PANS else "<pan>", repr(failure))[:800]})
        results["_runs"][-1]["finishedAt"] = now()
    merged = previous()
    merged.update({k: v for k, v in results.items() if k != "_runs"})
    merged.setdefault("_runs", []).extend(results.get("_runs", []))
    text = json.dumps(merged, ensure_ascii=False, indent=2)
    for secret in PANS + [t for t in tokens if t] + [r[0] for r in asql("SELECT public_token FROM t_checkout_session")]:
        assert secret not in text, "输出中出现卡号或 Checkout Token"
    OUT.write_text(text + "\n")


if __name__ == "__main__":
    main()
