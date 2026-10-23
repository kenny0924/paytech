"""acquiring 订单到期后的支付结果确认 · 阶段 5 本机 e2e 验收（真实进程、真实链路）。

链路：Client 侧经私网直连 acquiring Client API（带 Gateway 可信 Header，webhook_flow.commerce）建结账；Consumer 侧经 Customer API
提交卡支付（acquiring → Payment → card / risk-decision / channel-connector → Platform 标准 API → 合成 PayerMax 3DS 扩展版）。
3DS：333.00 USD 由 PAYMENT_ATTEMPT_RISK 发布要求 3DS，合成 PayerMax 返回挑战；挑战结果经 POST /__control/query 设定查单结果。
到期：脚本把本次创建订单的 t_commerce_order.expires_at 回拨到 now − 2 分钟（只按 order_no 改本次订单，唯一的直接写库）。
扫描：本机无 XXL Admin，按 Admin 协议向 acquiring 执行器 127.0.0.1:19992 POST /run 触发 orderExpiryScan。
结果确认：只经 Consumer 查询提交结果接口 GET /checkouts/{token}/payment-submissions/{submissionId}，不再以 acquiring-service 身份直连 Payment。
查单计数：读合成 PayerMax 流水 runtime/payermax/payermax-journal.jsonl 中该尝试的 orderQuery 条数。

场景：s1 挑战中到期 → 成功（AC-01、AC-05、AC-06）；s2 挑战中到期 → 失败 → 扫描 EXPIRED（AC-02、B4）；
     s3 到期后 5 秒内两次查询（AC-03）；s4 未提交支付到期（AC-04）；s5 日志泄漏检索。
用法：python3 -I stage5_result_after_expiry.py [s1|s2|s3|s4|s5|all]...（缺省 all）；结果合并写入 stage5-result.json。
不打印口令、Token、卡号；Checkout Public Token 只在内存中用于请求与泄漏检索。
"""
import glob
import json
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
MAIN = HERE.parents[3]
sys.path.insert(0, str(MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as h  # noqa: E402
import webhook_flow as w  # noqa: E402

RUNTIME = MAIN / "local-environment/e2e/runtime/expiry"
LOGS = MAIN / "local-environment/logs"
JOURNAL = MAIN / "local-environment/e2e/runtime/payermax/payermax-journal.jsonl"
XXL_TOKEN = (RUNTIME / "xxl-token").read_text().strip()
XXL_LOG_DIR = RUNTIME / "xxl-acquiring"
OUT = HERE / "stage5-result.json"
CLIENT_ID = "237161687952666625"  # mor_platform_v2e2e.t_access_tenant：CLIENT、owner_status=ACTIVE
MOCK = "http://127.0.0.1:18190"
CHALLENGE_AMOUNT = "333.00"
PANS = [w.TEST_CARD["pan"], "4111111111111111", "4012888888881881"]
results = {}
tokens = set()


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def record(name, data):
    results[name] = data
    print(name, json.dumps(data, ensure_ascii=False)[:2000])


def asql(statement):
    return h.sql("acquiring", statement)


# ---------- 事实 ----------

def order_facts(order_no):
    rows = asql("SELECT order_id, order_status, IFNULL(status_reason_code,''), version, DATE_FORMAT(expires_at,'%Y-%m-%dT%H:%i:%s.%fZ'), "
                "IFNULL(DATE_FORMAT(termination_requested_at,'%Y-%m-%dT%H:%i:%s.%fZ'),''), IFNULL(paid_at,''), "
                f"DATE_FORMAT(update_time,'%Y-%m-%dT%H:%i:%s.%fZ') FROM t_commerce_order WHERE order_no='{order_no}'")
    keys = ("orderId", "status", "reasonCode", "version", "expiresAt", "terminationRequestedAt", "paidAt", "updateTime")
    facts = dict(zip(keys, rows[0]))
    session = asql(f"SELECT session_status, version FROM t_checkout_session WHERE order_id={facts['orderId']}")
    facts["session"] = dict(zip(("status", "version"), session[0])) if session else None
    projection = asql("SELECT payment_intent_id, intent_status, IFNULL(successful_attempt_id,''), version "
                      f"FROM t_order_payment_projection WHERE order_id={facts['orderId']}")
    facts["projection"] = dict(zip(("intentId", "intentStatus", "successfulAttemptId", "version"), projection[0])) if projection else None
    intent = h.sql("payment", "SELECT payment_intent_id, intent_status, IFNULL(active_attempt_id,''), IFNULL(successful_attempt_id,''), "
                   f"version FROM t_payment_intent WHERE source_type='COMMERCE_ORDER' AND source_id='{facts['orderId']}'")
    facts["paymentIntent"] = dict(zip(("intentId", "status", "activeAttemptId", "successfulAttemptId", "version"), intent[0])) if intent else None
    return facts


def attempts(intent_id):
    return [dict(zip(("attemptId", "status"), r)) for r in h.sql(
        "payment", f"SELECT payment_attempt_id, attempt_status FROM t_payment_attempt WHERE payment_intent_id={intent_id} ORDER BY create_time")]


def compensation_rows(order_id):
    return [dict(zip(("eventType", "subEventType", "executeStatus", "executeCount"), r)) for r in asql(
        "SELECT event_type, IFNULL(sub_event_type,''), execute_status, execute_count FROM t_compensation_event "
        f"WHERE event_key LIKE '%{order_id}%' OR payload_json LIKE '%{order_id}%'")]


def platform_events(order_no):
    return [dict(zip(("eventType", "sourceEventKey", "sourceVersion"), r)) for r in h.platform_sql(
        f"SELECT event_type, source_event_key, source_version FROM t_webhook_event WHERE source_event_key LIKE 'evt_{order_no}_%'")]


def journal_queries(attempt_id):
    if not JOURNAL.exists():
        return None
    count = 0
    for line in JOURNAL.read_text(errors="replace").splitlines():
        try:
            entry = json.loads(line)
        except ValueError:
            continue
        if entry.get("operation") == "orderQuery" and entry.get("order") == str(attempt_id):
            count += 1
    return count


# ---------- 动作 ----------

def create_checkout(label, unit_amount=CHALLENGE_AMOUNT):
    client_order_id = f"f1-{label}-{time.strftime('%H%M%S')}-{uuid.uuid4().hex[:6]}"
    status, body = w.commerce("POST", "/checkouts", CLIENT_ID, {
        "clientOrderId": client_order_id, "currency": "USD", "consumerCountryCode": "US",
        "items": [{"clientItemId": "line-1", "name": "Stage five item", "productTypeCode": "SERVICE", "taxCategoryCode": "STANDARD",
                   "quantity": "1", "unitAmount": unit_amount}]})
    assert status == 201, (status, (body or {}).get("code"))
    tokens.add(w.checkout_token(body.get("checkoutUrl")))
    return body["orderNo"], body.get("checkoutUrl"), {"http": status, "orderStatus": body.get("orderStatus"),
                                                       "total": (body.get("amounts") or {}).get("totalAmount"), "expiresAt": body.get("expiresAt")}


def backdate(order_no, minutes=2):
    """唯一的直接写库：把本次订单的 expires_at 回拨到 now - minutes（按 order_no 定位）。"""
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
            "checkoutStatus": checkout.get("status"), "checkoutUrlPresent": bool(checkout.get("url") or body.get("checkoutUrl"))}


def brief(status, body, started=None):
    body = body or {}
    out = {"http": status, "code": body.get("code"), "status": body.get("status")}
    if status == 200:
        out.update({"failureCode": (body.get("failure") or {}).get("code"), "pollAfterMs": body.get("pollAfterMs"),
                    "actionType": ((body.get("nextAction") or body.get("action") or {}).get("type")),
                    "last4Present": bool((body.get("card") or {}).get("last4")), "keys": sorted(body)})
    if started is not None:
        out["elapsedMs"] = int((time.time() - started) * 1000)
    return out


def poll(url, submission_id):
    started = time.time()
    status, body = w.customer("GET", f"/{w.checkout_token(url)}/payment-submissions/{submission_id}")
    return brief(status, body, started) | {"at": now()}


def customer_view(url):
    status, body = w.customer("GET", "/" + w.checkout_token(url))
    return {"http": status, "code": (body or {}).get("code")}


def customer_submit(url, contract_id="1"):
    status, body = w.customer("POST", f"/{w.checkout_token(url)}/payments",
                              {"legalConfirmation": {"consumerContractId": contract_id, "confirmed": True}, "card": w.TEST_CARD})
    return {"http": status, "code": (body or {}).get("code")}


def customer_save(url):
    status, body = w.customer("PUT", f"/{w.checkout_token(url)}/consumer", {
        "fullName": "Stage Five Consumer", "email": f"consumer-{uuid.uuid4().hex[:8]}@example.invalid",
        "billingAddress": {"countryCode": "US", "addressLine1": "1 Test Street", "city": "New York", "postalCode": "10001"}})
    return {"http": status, "code": (body or {}).get("code")}


def mock_control_available():
    try:
        request = urllib.request.Request(MOCK + "/__control/query", method="POST", data=json.dumps({"outTradeNo": "-", "status": "-"}).encode())
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status == 200
    except (urllib.error.HTTPError, OSError):
        return False


def mock_query(attempt, status):
    request = urllib.request.Request(MOCK + "/__control/query", method="POST",
                                     data=json.dumps({"outTradeNo": str(attempt), "status": status}).encode())
    with urllib.request.urlopen(request, timeout=5) as response:
        assert response.status == 200


def challenged(label):
    """建 333.00 USD 结账并提交卡支付进入 3DS 挑战；返回 (out, url, submissionId, attemptId, intentId) 或 None（已记录原因）。"""
    out = {}
    order_no, url, created = create_checkout(label)
    out["orderNo"], out["created"] = order_no, created
    submission, note = w.pay_checkout(url)
    submission = submission or {}
    out["submission"] = {"status": submission.get("status"), "note": re.sub(r"last4=\S+", "last4=*", note),
                         "actionType": ((submission.get("nextAction") or submission.get("action") or {}).get("type"))}
    facts = order_facts(order_no)
    out["beforeExpiry"] = facts
    if submission.get("status") != "REQUIRES_ACTION":
        out["executed"] = False
        out["reason"] = "提交未进入 REQUIRES_ACTION（3DS 挑战），见 submission"
        return out, None
    attempt_id = str(submission.get("attemptId") or "")
    return out, (order_no, url, submission.get("submissionId"), attempt_id, (facts["projection"] or {}).get("intentId"))


def poll_until(url, submission_id, wanted, tries=5, gap=6):
    """越过 Payment 5 秒限频后重查，直到状态为 wanted 或用完次数。"""
    seen = []
    for _ in range(tries):
        time.sleep(gap)
        seen.append(poll(url, submission_id))
        if seen[-1]["status"] == wanted:
            break
    return seen


# ---------- 场景 ----------

def s1():
    """S1 挑战中到期 → 挑战成功（AC-01）；到期后写操作与查看收银台仍 410（AC-05）；Client GET 无 checkoutUrl（AC-06）。"""
    if not mock_control_available():
        record("S1", {"executed": False, "reason": "18190 合成 PayerMax 无 /__control/query"})
        return
    out, ids = challenged("s1")
    if not ids:
        record("S1", out)
        return
    order_no, url, submission_id, attempt_id, intent_id = ids
    out["ids"] = {"intentId": intent_id, "attemptId": attempt_id, "submissionId": submission_id}
    mock_query(attempt_id, "PENDING")
    out["backdatedExpiresAt"] = backdate(order_no)
    time.sleep(6)
    out["pollAfterExpiry"] = poll(url, submission_id)
    out["customerViewAfterExpiry"] = customer_view(url)
    out["customerSaveAfterExpiry"] = customer_save(url)
    out["customerSubmitAfterExpiry"] = customer_submit(url)
    out["clientGetAfterExpiry"] = get_order(order_no)
    mock_query(attempt_id, "SUCCESS")
    out["pollsAfterSuccess"] = poll_until(url, submission_id, "SUCCEEDED")
    paid = w.wait_for(lambda: (lambda f: f if f["status"] == "PAID" else None)(order_facts(order_no)), 40, 1)
    out["paid"] = paid or order_facts(order_no)
    out["attempts"] = attempts(intent_id)
    out["scan"] = xxl_scan()
    time.sleep(2)
    after = order_facts(order_no)
    out["afterScan"] = {"facts": after, "get": get_order(order_no), "platformEvents": platform_events(order_no),
                        "compensation": compensation_rows(after["orderId"])}
    out["pollAfterPaid"] = poll(url, submission_id)
    out["orderQueries"] = journal_queries(attempt_id)
    p, a, first = out["paid"], after, out["pollAfterExpiry"]
    out["verdict"] = {
        "AC01_pollAfterExpiryNot410": first["http"] == 200 and first["status"] in ("REQUIRES_ACTION", "PROCESSING"),
        "AC01_pollSucceeded": out["pollsAfterSuccess"][-1]["http"] == 200 and out["pollsAfterSuccess"][-1]["status"] == "SUCCEEDED",
        "AC01_orderPaid": p["status"] == "PAID" and (p["paymentIntent"] or {}).get("status") == "SUCCEEDED",
        "AC01_scanKeepsPaid": a["status"] == "PAID" and a["version"] == p["version"] and a["updateTime"] == p["updateTime"]
                              and not any(e["eventType"] == "order.expired" for e in out["afterScan"]["platformEvents"]),
        "AC01_pollAfterPaid": out["pollAfterPaid"]["http"] == 200 and out["pollAfterPaid"]["status"] == "SUCCEEDED",
        "AC05_view410": out["customerViewAfterExpiry"] == {"http": 410, "code": "CHECKOUT_EXPIRED"},
        "AC05_save410": out["customerSaveAfterExpiry"] == {"http": 410, "code": "CHECKOUT_EXPIRED"},
        "AC05_submit410": out["customerSubmitAfterExpiry"] == {"http": 410, "code": "CHECKOUT_EXPIRED"},
        "AC06_noCheckoutUrl": out["clientGetAfterExpiry"]["http"] == 200 and not out["clientGetAfterExpiry"]["checkoutUrlPresent"]}
    record("S1", out)


def s2():
    """S2 挑战中到期 → 挑战失败：消费者查询 FAILED → 扫描 EXPIRED → 再查仍 FAILED（AC-02、B4）。"""
    if not mock_control_available():
        record("S2", {"executed": False, "reason": "18190 合成 PayerMax 无 /__control/query"})
        return
    out, ids = challenged("s2")
    if not ids:
        record("S2", out)
        return
    order_no, url, submission_id, attempt_id, intent_id = ids
    out["ids"] = {"intentId": intent_id, "attemptId": attempt_id, "submissionId": submission_id}
    mock_query(attempt_id, "PENDING")
    out["backdatedExpiresAt"] = backdate(order_no)
    time.sleep(6)
    out["pollAfterExpiry"] = poll(url, submission_id)
    mock_query(attempt_id, "FAILED")
    out["pollsAfterFailure"] = poll_until(url, submission_id, "FAILED")
    out["afterFailure"] = {"facts": order_facts(order_no), "attempts": attempts(intent_id)}
    out["scan"] = xxl_scan()
    expired = w.wait_for(lambda: (lambda f: f if f["status"] == "EXPIRED" else None)(order_facts(order_no)), 30, 1)
    out["afterScan"] = {"facts": expired or order_facts(order_no), "get": get_order(order_no), "platformEvents": platform_events(order_no)}
    time.sleep(6)
    out["pollAfterOrderExpired"] = poll(url, submission_id)
    out["orderQueries"] = journal_queries(attempt_id)
    f, first, last = out["afterScan"]["facts"], out["pollAfterExpiry"], out["pollsAfterFailure"][-1]
    out["verdict"] = {
        "AC02_pollAfterExpiryNot410": first["http"] == 200 and first["status"] in ("REQUIRES_ACTION", "PROCESSING"),
        "AC02_pollFailed": last["http"] == 200 and last["status"] == "FAILED",
        "attemptFailed": any(a["attemptId"] == attempt_id and a["status"] == "FAILED" for a in out["afterFailure"]["attempts"]),
        "AC02_orderSessionExpired": f["status"] == "EXPIRED" and (f["session"] or {}).get("status") == "EXPIRED"
                                    and (f["paymentIntent"] or {}).get("status") == "EXPIRED",
        "AC02_B4_pollAfterOrderExpired": out["pollAfterOrderExpired"]["http"] == 200 and out["pollAfterOrderExpired"]["status"] == "FAILED",
        "orderExpiredEventOnce": sum(e["eventType"] == "order.expired" for e in out["afterScan"]["platformEvents"]) == 1}
    record("S2", out)


def s3():
    """S3 到期后挑战仍处理中，5 秒内两次查询：都 200 处理中 / 需要验证，PayerMax 查单最多一次（AC-03）。"""
    if not mock_control_available():
        record("S3", {"executed": False, "reason": "18190 合成 PayerMax 无 /__control/query"})
        return
    out, ids = challenged("s3")
    if not ids:
        record("S3", out)
        return
    order_no, url, submission_id, attempt_id, intent_id = ids
    out["ids"] = {"intentId": intent_id, "attemptId": attempt_id, "submissionId": submission_id}
    mock_query(attempt_id, "PENDING")
    out["backdatedExpiresAt"] = backdate(order_no)
    time.sleep(6)  # 越过 pay_checkout 轮询留下的限频窗口
    before = journal_queries(attempt_id)
    started = time.time()
    out["pollA"] = poll(url, submission_id)
    out["pollB"] = poll(url, submission_id)
    out["windowMs"] = int((time.time() - started) * 1000)
    time.sleep(1)
    after_pair = journal_queries(attempt_id)
    time.sleep(6)
    out["pollC"] = poll(url, submission_id)
    time.sleep(1)
    after_c = journal_queries(attempt_id)
    out["orderQueries"] = {"beforePair": before, "afterPair": after_pair, "afterThirdBeyond5s": after_c,
                           "pairDelta": None if before is None else after_pair - before,
                           "thirdDelta": None if before is None else after_c - after_pair}
    out["facts"] = order_facts(order_no)
    out["attempts"] = attempts(intent_id)
    pending = ("REQUIRES_ACTION", "PROCESSING")
    delta = out["orderQueries"]["pairDelta"]
    out["verdict"] = {
        "AC03_bothPending": all(out[k]["http"] == 200 and out[k]["status"] in pending for k in ("pollA", "pollB")),
        "AC03_withinWindow": out["windowMs"] < 5000,
        "AC03_atMostOneQueryInWindow": delta is not None and delta <= 1,
        "orderStillOpen": out["facts"]["status"] == "OPEN"}
    record("S3", out)


def s4():
    """S4 未提交支付的订单到期：查询提交结果 410 CHECKOUT_EXPIRED（AC-04）。"""
    out = {}
    order_no, url, created = create_checkout("s4", "10.00")
    out["orderNo"], out["created"] = order_no, created
    out["beforeExpiry"] = {"poll": poll(url, 1)}
    out["backdatedExpiresAt"] = backdate(order_no)
    out["pollAfterExpiry"] = {"submission1": poll(url, 1), "submissionLarge": poll(url, 239999999999999999)}
    facts = order_facts(order_no)
    out["facts"] = facts
    out["verdict"] = {
        "beforeExpiry404": out["beforeExpiry"]["poll"]["http"] == 404 and out["beforeExpiry"]["poll"]["code"] == "PAYMENT_SUBMISSION_NOT_FOUND",
        "AC04_expired410": all(v["http"] == 410 and v["code"] == "CHECKOUT_EXPIRED" for v in out["pollAfterExpiry"].values()),
        "noPayment": facts["projection"] is None and facts["paymentIntent"] is None}
    record("S4", out)


def s5():
    """S5 日志泄漏检索：本次运行窗口内 acquiring.log / payment.log 不含卡号、CVV、acsSecret、Checkout Public Token、3DS 跳转地址。"""
    runs = previous_runs() + results.get("_runs", [])
    start = min(r["startedAt"] for r in runs) if runs else now()
    out = {"windowStart": start}
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
        out[name] = {"linesInWindow": len(lines), "hits": {
            "pan": sum(text.count(p) for p in PANS), "cvvKey": len(re.findall(r'(?i)"cvv"|cvv=', text)),
            "acsSecret": text.count("acsSecret"), "checkoutPublicToken": sum(text.count(t) for t in known_tokens if t),
            "tokenShape": len(re.findall(r"chk_[A-Za-z0-9]{20,}", text)),
            "redirectUrl": len(re.findall(r"https?://[^\s]*(3ds|acs|challenge)[^\s]*", text, re.I))},
            "warnCount": sum(" WARN " in l for l in lines), "errorCount": sum(" ERROR " in l for l in lines),
            "warnSamples": sorted({re.sub(r"\d{6,}", "<n>", l[l.find(" : ") + 3:][:200]) for l in lines
                                   if (" WARN " in l or " ERROR " in l) and " : " in l})[:15]}
    out["checkedTokens"] = len(known_tokens)
    out["verdict"] = {"noLeak": all(sum(out[n]["hits"].values()) == 0 for n in ("acquiring", "payment"))}
    record("S5", out)


def previous():
    return json.loads(OUT.read_text()) if OUT.exists() else {}


def previous_runs():
    return previous().get("_runs", [])


SCENARIOS = {"s1": s1, "s2": s2, "s3": s3, "s4": s4, "s5": s5}


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
        except (AssertionError, KeyError, IndexError, TypeError, w.Stop, RuntimeError, OSError) as failure:
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
