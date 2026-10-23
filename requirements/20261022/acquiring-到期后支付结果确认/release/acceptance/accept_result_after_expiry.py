"""阶段 7 mor-test 验收（release.md 第 9 节）：订单到期后的支付结果确认，由 verification/stage5_result_after_expiry.py 改写。

E2E_TARGET=test；私网调用经接入 mor-test-backend 的一次性探针容器（e2e_http.private_call，E2E_PROBE_IMAGE 缺省为现网 payment 镜像，只用其 JDK）。
只读 SELECT（服务自有账号，口令经 MYSQL_PWD 传入，不打印）；不回拨 expires_at，不写任何库、XXL、RabbitMQ、Nacos、Vault。
Payment 是否收到请求：读 payment-service 管理端口 Prometheus 的 http_server_requests_seconds_count（按 uri 模板 / status），前后求差；
另以 acquiring 出站 http_client_requests_seconds_count 佐证。payment 的访问日志是 metadata-only（uri 记为 "/**"），只能按
requestId（本脚本发出的 req-f1s7-*，acquiring 透传）对应，不能按路径区分，所以以指标为主、日志为辅。

子命令：
  pa            P-A1（EXPIRED 无支付记录：查询提交 1、查看收银台，期望 410，Payment 零请求）
                P-A2（PAID 夹具：查询提交 = 投影 last_attempt_id / successful_attempt_id，期望 410，Payment 刷新 1 次且 404）
  pb            P-B：Client API（私网直连 acquiring，带 Gateway 可信 Header，见偏差）建 600.00 USD、5 分钟期限结账 →
                Consumer 保存资料、确认合同、提交卡 → FAILED THREE_DS_CHANNEL_UNAVAILABLE、无渠道请求 → 到期前查询 →
                到期后扫描前查询 / 查看收银台 / 再提交 / Client GET → 等任务 23 收尾 EXPIRED → 再查询
  leak <since>  acquiring、payment docker logs 泄漏检索（只计数）：测试卡号、cvv 键、Luhn 卡号形串、Checkout Token 原值与 chk_ 形
偏差：mor-test 没有可用的 Client 凭证；新建凭证要入驻新 Client，入驻的邮箱验证要写 Platform 库（mint_signup_link），不在授权内。
  因此 Client 侧与阶段 5 相同，私网直连 acquiring Client API（client-gateway-api/v1），带 Gateway 注入的可信 Header，不经 Gateway / Nginx。
结果合并写入同目录 accept-result-after-expiry.json，逐行过程写 accept-result-after-expiry.txt；Checkout Token、卡号只在内存中。
用法：python3 -I accept_result_after_expiry.py pa|pb|leak <sinceUTC>；-h 看本说明。
"""
import json
import os
import re
import subprocess
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as h  # noqa: E402

# 现网 payment 镜像（只用其 JDK 运行 PrivateHttpProbe）
h.PROBE_IMAGE = os.environ.get("E2E_PROBE_IMAGE", "sha256:cf9097ea8685fbb2231fd97f49a1993b1aa4b6f30ba628e76e929dfa155a11c7")

OUT = HERE / "accept-result-after-expiry.json"
LOG = HERE / "accept-result-after-expiry.txt"
CLIENT_ID = "239260386824658945"  # 订单过期收尾阶段 7 入驻批准的验收 Client（mor_acquiring 已有其结账）
CLIENT_API = "/client-gateway-api/v1"
CUSTOMER_API = "/customer-gateway-api/v1/checkouts"
PAYMENT_METRICS = "http://payment-service:19103/actuator/prometheus"
ACQUIRING_METRICS = "http://acquiring-service:19102/actuator/prometheus"
CARD = {"pan": "4012888888881881", "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}
PANS = [CARD["pan"], "4444333322221111", "4111111111111111"]
AMOUNT = "600.00"
EXPIRY_LEAD = timedelta(minutes=5, seconds=40)  # 最短 5 分钟按受理时刻计算，加余量
SECRETS = set()  # 本次接触到的 Checkout Token，只用于脱敏与泄漏检索


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")[:-4] + "Z"


def scrub(text):
    text = str(text)
    for secret in SECRETS | set(PANS):
        if secret:
            text = text.replace(secret, "<redacted>")
    return re.sub(r"chk_[A-Za-z0-9]{16,}", "chk_<redacted>", text)


def log(line):
    line = scrub(f"{now()} {line}")
    print(line)
    with LOG.open("a") as handle:
        handle.write(line + "\n")


def save(name, data):
    results = json.loads(OUT.read_text()) if OUT.exists() else {}
    results[name] = data
    text = json.dumps(results, ensure_ascii=False, indent=1)
    for secret in SECRETS | set(PANS):
        assert secret not in text, "输出中出现 Checkout Token 或卡号"
    OUT.write_text(text + "\n")
    log(f"{name} saved verdict={json.dumps(data.get('verdict'), ensure_ascii=False)}")


def sql(service, statement):
    if not statement.lstrip().upper().startswith("SELECT"):
        raise SystemExit("阶段 7 只允许只读 SELECT")
    return h.sql(service, statement)


# ---------- HTTP（私网探针） ----------

def call(service, method, path, headers=None, body=None):
    try:
        status, text = h.private_call(service, method, path, headers=headers, body=body)
    except RuntimeError as failure:  # 探针错误信息含 URL（可能含 Token），脱敏后抛出
        raise RuntimeError(scrub(failure)) from None
    try:
        return status, (json.loads(text) if text else {})
    except ValueError:
        return status, {"raw": scrub(text[:300])}


def client(method, path, body=None):
    headers = {"Accept": "application/json", "X-Request-Id": "req-f1s7-" + uuid.uuid4().hex[:12],
               "X-Gateway-Client-Id": CLIENT_ID, "X-Gateway-Credential-Id": "1"}
    return call("acquiring", method, CLIENT_API + path, headers, body)


def customer(method, token, suffix="", body=None):
    headers = {"Accept": "application/json", "X-Request-Id": "req-f1s7-" + uuid.uuid4().hex[:12],
               "User-Agent": "Mozilla/5.0 (Code-MoR F1 stage7 acceptance)"}
    return call("acquiring", method, f"{CUSTOMER_API}/{token}{suffix}", headers, body)


def brief(status, body):
    body = body or {}
    out = {"http": status, "code": body.get("code"), "status": body.get("status")}
    if status == 200:
        out.update({"failureCode": (body.get("failure") or {}).get("code"), "pollAfterMs": body.get("pollAfterMs"),
                    "last4Present": bool((body.get("card") or {}).get("last4")), "keys": sorted(body)})
    return out


def poll(token, submission_id):
    return brief(*customer("GET", token, f"/payment-submissions/{submission_id}")) | {"at": now()}


def view(token):
    status, body = customer("GET", token)
    return {"http": status, "code": (body or {}).get("code"), "at": now()}


def metrics(url, family):
    """Prometheus 文本中 family 的 {标签串: 值}；探针失败返回 None。"""
    try:
        status, text = h._probe_call("GET", url, {}, None, 30)
    except RuntimeError as failure:
        log(f"metrics 读取失败 {url}: {scrub(failure)[:200]}")
        return None
    if status != 200:
        return None
    values = {}
    for line in text.splitlines():
        if line.startswith(family + "{"):
            labels, _, value = line.rpartition(" ")
            values[labels[len(family):]] = float(value)
    return values


def delta(before, after, needle=""):
    if before is None or after is None:
        return None
    return {k: v - before.get(k, 0.0) for k, v in after.items() if needle in k and v - before.get(k, 0.0)}


def payment_requests():
    return metrics(PAYMENT_METRICS, "http_server_requests_seconds_count")


def acquiring_client_requests():
    return metrics(ACQUIRING_METRICS, "http_client_requests_seconds_count")


# ---------- 事实（只读） ----------

def order_facts(order_no):
    rows = sql("acquiring", "SELECT order_id, order_status, IFNULL(status_reason_code,''), version, "
               "DATE_FORMAT(expires_at,'%Y-%m-%dT%H:%i:%s.%fZ'), DATE_FORMAT(update_time,'%Y-%m-%dT%H:%i:%s.%fZ') "
               f"FROM t_commerce_order WHERE order_no='{order_no}'")
    facts = dict(zip(("orderId", "status", "reasonCode", "version", "expiresAt", "updateTime"), rows[0]))
    session = sql("acquiring", f"SELECT session_status, version FROM t_checkout_session WHERE order_id={int(facts['orderId'])}")
    facts["session"] = dict(zip(("status", "version"), session[0])) if session else None
    projection = sql("acquiring", "SELECT payment_intent_id, intent_status, IFNULL(last_attempt_id,''), IFNULL(last_attempt_status,''), "
                     f"version FROM t_order_payment_projection WHERE order_id={int(facts['orderId'])}")
    facts["projection"] = dict(zip(("intentId", "intentStatus", "lastAttemptId", "lastAttemptStatus", "version"), projection[0])) \
        if projection else None
    intent = sql("payment", "SELECT payment_intent_id, intent_status, IFNULL(active_attempt_id,''), version FROM t_payment_intent "
                 f"WHERE source_type='COMMERCE_ORDER' AND source_id='{int(facts['orderId'])}'")
    facts["paymentIntent"] = dict(zip(("intentId", "status", "activeAttemptId", "version"), intent[0])) if intent else None
    if intent:
        facts["attempts"] = [dict(zip(("attemptId", "status", "midId"), r)) for r in sql(
            "payment", "SELECT payment_attempt_id, attempt_status, IFNULL(mid_id,'') FROM t_payment_attempt "
                       f"WHERE payment_intent_id={int(intent[0][0])} ORDER BY attempt_no")]
    return facts


def interactions():
    return int(sql("channel-connector", "SELECT COUNT(*) FROM t_channel_interaction")[0][0])


def platform_events(order_no):
    return [dict(zip(("eventType", "sourceVersion"), r)) for r in h.platform_sql(
        f"SELECT event_type, source_version FROM t_webhook_event WHERE source_event_key LIKE 'evt_{order_no}_%'")]


def job23_since(since):
    """XXL 库只读（root 口令经 MYSQL_PWD，与 release/scripts/snapshot.sh 相同）。"""
    env = (ROOT / "test-environment/.env.test").read_text().splitlines()
    password = next(line.split("=", 1)[1] for line in env if line.startswith("MOR_MYSQL_ROOT_PASSWORD="))
    statement = ("SELECT DATE_FORMAT(trigger_time,'%Y-%m-%dT%H:%i:%s'), trigger_code, handle_code, "
                 "LEFT(REPLACE(IFNULL(handle_msg,''),'\\n',' '),160) FROM xxl_job_log WHERE job_id=23 "
                 f"AND trigger_time >= CONVERT_TZ('{since[:19].replace('T', ' ')}','+00:00',@@session.time_zone) ORDER BY id")
    result = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD=" + password, "mor-test-mysql-1", "mysql", "-uroot", "-N", "-B",
                             "--default-character-set=utf8mb4", "xxl_job", "-e", statement], capture_output=True, text=True)
    if result.returncode != 0:
        return {"error": result.stderr.strip()[:200]}
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def wait_for(predicate, seconds, step):
    deadline = time.time() + seconds
    while time.time() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(step)
    return predicate()


# ---------- P-A ----------

def pa():
    p1 = sql("acquiring", "SELECT o.order_no, s.public_token FROM t_commerce_order o JOIN t_checkout_session s ON s.order_id=o.order_id "
                          "LEFT JOIN t_order_payment_projection p ON p.order_id=o.order_id WHERE o.order_status='EXPIRED' "
                          "AND p.order_id IS NULL ORDER BY o.create_time DESC LIMIT 1")[0]
    p2 = sql("acquiring", "SELECT o.order_no, s.public_token, p.payment_intent_id, IFNULL(p.last_attempt_id,''), "
                          "IFNULL(p.successful_attempt_id,''), DATE_FORMAT(o.expires_at,'%Y-%m-%dT%H:%i:%sZ') FROM t_commerce_order o "
                          "JOIN t_checkout_session s ON s.order_id=o.order_id JOIN t_order_payment_projection p ON p.order_id=o.order_id "
                          "WHERE o.order_status='PAID' ORDER BY o.create_time DESC LIMIT 1")[0]
    SECRETS.update({p1[1], p2[1]})
    out = {"at": now(), "probeImage": h.PROBE_IMAGE[:19]}

    m0 = payment_requests()
    out["P-A1"] = {"orderNo": p1[0], "poll": poll(p1[1], 1), "view": view(p1[1])}
    time.sleep(1)
    m1 = payment_requests()
    out["P-A1"]["paymentRequestDelta"] = delta(m0, m1, "/payment-execution/")
    log(f"P-A1 order={p1[0]} poll={out['P-A1']['poll']['http']}/{out['P-A1']['poll']['code']} "
        f"view={out['P-A1']['view']['http']}/{out['P-A1']['view']['code']} paymentDelta={out['P-A1']['paymentRequestDelta']}")

    submission = p2[3] or p2[4]
    intent_in_payment = sql("payment", f"SELECT COUNT(*) FROM t_payment_intent WHERE payment_intent_id={int(p2[2])}")[0][0]
    c0 = acquiring_client_requests()
    out["P-A2"] = {"orderNo": p2[0], "paymentIntentId": p2[2], "submissionId": submission, "expiresAt": p2[5],
                   "intentInPayment": int(intent_in_payment), "poll": poll(p2[1], submission)}
    time.sleep(1)
    m2, c1 = payment_requests(), acquiring_client_requests()
    out["P-A2"]["paymentRequestDelta"] = delta(m1, m2, "/payment-execution/")
    out["P-A2"]["acquiringClientDelta"] = delta(c0, c1, "payment")
    log(f"P-A2 order={p2[0]} intent={p2[2]} (in payment: {intent_in_payment}) submission={submission} "
        f"poll={out['P-A2']['poll']['http']}/{out['P-A2']['poll']['code']} paymentDelta={out['P-A2']['paymentRequestDelta']} "
        f"acquiringClientDelta={out['P-A2']['acquiringClientDelta']}")

    d1, d2 = out["P-A1"]["paymentRequestDelta"], out["P-A2"]["paymentRequestDelta"]
    refresh = {k: v for k, v in (d2 or {}).items() if "/refresh" in k}
    out["verdict"] = {
        "PA1_poll410": out["P-A1"]["poll"]["http"] == 410 and out["P-A1"]["poll"]["code"] == "CHECKOUT_EXPIRED",
        "PA1_view410": out["P-A1"]["view"]["http"] == 410 and out["P-A1"]["view"]["code"] == "CHECKOUT_EXPIRED",
        "PA1_noPaymentRequest": d1 == {},
        "PA2_poll410": out["P-A2"]["poll"]["http"] == 410 and out["P-A2"]["poll"]["code"] == "CHECKOUT_EXPIRED",
        "PA2_intentAbsentInPayment": int(intent_in_payment) == 0,
        "PA2_oneRefresh404": d2 is not None and sum(refresh.values()) == 1 and all('status="404"' in k for k in refresh)
                             and sum(d2.values()) == 1}
    save("P-A-" + now(), out)


# ---------- P-B ----------

def create_checkout():
    # 到期时刻取某分钟的第 8 秒：任务 23 约在每分钟第 0—2 秒调度，第 4 步的“到期后、扫描前”窗口约 50 秒
    earliest = datetime.now(timezone.utc) + EXPIRY_LEAD
    expires = earliest.replace(second=8, microsecond=0)
    expires_at = (expires if expires >= earliest else expires + timedelta(minutes=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
    status, body = client("POST", "/checkouts", {
        "clientOrderId": f"f1-s7-{time.strftime('%H%M%S')}-{uuid.uuid4().hex[:6]}", "currency": "USD", "consumerCountryCode": "US",
        "expiresAt": expires_at,
        "items": [{"clientItemId": "line-1", "name": "Stage seven item", "productTypeCode": "SERVICE", "taxCategoryCode": "STANDARD",
                   "quantity": "1", "unitAmount": AMOUNT}]})
    body = body or {}
    token = (body.get("checkoutUrl") or "").rstrip("/").rsplit("/", 1)[-1]
    if token:
        SECRETS.add(token)
    return status, body, token


def prepare_and_submit(token, out):
    """保存资料、等合同就绪、提交卡；任一步异常返回 None 并记录（环境问题，不绕行）。"""
    ready = wait_for(lambda: (lambda r: r[1] if r[0] == 200 else None)(customer("GET", token)), 30, 2)
    if not ready:
        out["stall"] = {"step": "查看收银台", "result": view(token)}
        return None
    if ((ready.get("consumer") or {}).get("status")) != "COMPLETE":
        status, saved = customer("PUT", token, "/consumer", {
            "fullName": "Stage Seven Consumer", "email": f"consumer-{uuid.uuid4().hex[:8]}@example.invalid",
            "billingAddress": {"countryCode": "US", "addressLine1": "1 Test Street", "city": "New York", "postalCode": "10001"}})
        out["saveConsumer"] = {"http": status, "code": (saved or {}).get("code")}
        log(f"P-B 保存消费者资料 HTTP {status} code={(saved or {}).get('code')}")
        if status != 200:
            out["stall"] = {"step": "保存消费者资料", "result": out["saveConsumer"]}
            return None
    contract = wait_for(lambda: (lambda r: r[1] if r[0] == 200 and ((r[1] or {}).get("legal") or {}).get("consumerContractId")
                                 else None)(customer("GET", token)), 60, 3)
    if not contract:
        body = customer("GET", token)[1] or {}
        out["stall"] = {"step": "合同就绪", "preparationStatus": body.get("preparationStatus"), "allowedActions": body.get("allowedActions"),
                        "payment": (body.get("payment") or {}).get("availability")}
        return None
    contract_id = contract["legal"]["consumerContractId"]
    out["contractReady"] = {"allowedActions": contract.get("allowedActions"), "preparationStatus": contract.get("preparationStatus")}
    out["channelInteractionsBefore"] = interactions()
    status, submitted = customer("POST", token, "/payments",
                                 {"legalConfirmation": {"consumerContractId": contract_id, "confirmed": True}, "card": CARD})
    out["submit"] = brief(status, submitted) | {"submissionIdPresent": bool((submitted or {}).get("submissionId"))}
    log(f"P-B 提交卡支付 HTTP {status} status={(submitted or {}).get('status')} code={(submitted or {}).get('code')}")
    if status not in (200, 202) or not (submitted or {}).get("submissionId"):
        out["stall"] = {"step": "提交卡支付", "result": out["submit"]}
        return None
    submission_id = submitted["submissionId"]
    final = wait_for(lambda: (lambda p: p if p["http"] == 200 and p["status"] not in ("PROCESSING", "AWAITING_CARD", None) else None)(
        poll(token, submission_id)), 60, 3)
    out["submissionResult"] = final or poll(token, submission_id)
    time.sleep(2)
    out["channelInteractionsAfter"] = interactions()
    return submission_id


def pb():
    started = now()
    out = {"startedAt": started, "clientId": CLIENT_ID, "amount": AMOUNT, "card": "公开测试卡 401288…（GB VISA DEBIT）"}
    status, body, token = create_checkout()
    out["created"] = {"http": status, "code": body.get("code"), "orderNo": body.get("orderNo"), "orderStatus": body.get("orderStatus"),
                      "total": (body.get("amounts") or {}).get("totalAmount"), "expiresAt": body.get("expiresAt"),
                      "checkoutUrlPresent": bool(token)}
    log(f"P-B 建结账 HTTP {status} orderNo={body.get('orderNo')} expiresAt={body.get('expiresAt')} code={body.get('code')}")
    if status != 201 or not token:
        out["stall"] = {"step": "建结账", "result": out["created"]}
        save("P-B-" + started, out)
        return
    order_no = body["orderNo"]
    out["orderNo"] = order_no
    submission_id = prepare_and_submit(token, out)
    if submission_id is None:
        log(f"P-B 停在 {out['stall']['step']}：{json.dumps(out['stall'], ensure_ascii=False)}")
        out["facts"] = order_facts(order_no)
        save("P-B-" + started, out)
        return
    out["submissionId"] = submission_id
    out["factsAfterSubmit"] = order_facts(order_no)
    log(f"P-B 提交结果 {out['submissionResult']['http']} {out['submissionResult']['status']} "
        f"failure={out['submissionResult'].get('failureCode')} interactions {out['channelInteractionsBefore']}->{out['channelInteractionsAfter']}")

    # 3：到期前查询（回归）
    out["pollBeforeExpiry"] = poll(token, submission_id)
    log(f"P-B 到期前查询 {out['pollBeforeExpiry']['http']} {out['pollBeforeExpiry']['status']}")

    # 4：到期后、任务 23 扫描前
    expires_at = datetime.fromisoformat(out["factsAfterSubmit"]["expiresAt"].replace("Z", "+00:00"))
    wait = (expires_at - datetime.now(timezone.utc)).total_seconds() + 1
    log(f"P-B 等待到期 {int(wait)} 秒（expires_at={out['factsAfterSubmit']['expiresAt']}）")
    if wait > 0:
        time.sleep(wait)
    out["afterExpiryBeforeScan"] = {
        "orderStatusBefore": order_facts(order_no)["status"],
        "poll": poll(token, submission_id), "view": view(token),
        "submitAgain": (lambda r: {"http": r[0], "code": (r[1] or {}).get("code"), "at": now()})(customer(
            "POST", token, "/payments", {"legalConfirmation": {"consumerContractId": "1", "confirmed": True}, "card": CARD})),
        "clientGet": (lambda r: {"http": r[0], "orderStatus": (r[1] or {}).get("orderStatus"),
                                 "paymentStatus": (r[1] or {}).get("paymentStatus"),
                                 "checkoutStatus": ((r[1] or {}).get("checkout") or {}).get("status"),
                                 "checkoutUrlPresent": bool(((r[1] or {}).get("checkout") or {}).get("url") or (r[1] or {}).get("checkoutUrl")),
                                 "at": now()})(client("GET", f"/orders/{order_no}"))}
    out["afterExpiryBeforeScan"]["orderStatusAfter"] = order_facts(order_no)["status"]
    log(f"P-B 到期后扫描前 {json.dumps(out['afterExpiryBeforeScan'], ensure_ascii=False)}")

    # 5：等任务 23 收尾
    expired = wait_for(lambda: (lambda f: f if f["status"] == "EXPIRED" else None)(order_facts(order_no)), 240, 10)
    out["afterScan"] = {"facts": expired or order_facts(order_no), "platformEvents": platform_events(order_no),
                        "job23": job23_since(out["factsAfterSubmit"]["expiresAt"]),
                        "clientGet": (lambda r: {"http": r[0], "orderStatus": (r[1] or {}).get("orderStatus"),
                                                 "paymentStatus": (r[1] or {}).get("paymentStatus"),
                                                 "checkoutUrlPresent": bool(((r[1] or {}).get("checkout") or {}).get("url"))})(
                            client("GET", f"/orders/{order_no}"))}
    out["pollAfterOrderExpired"] = poll(token, submission_id)
    log(f"P-B 扫描后 status={out['afterScan']['facts']['status']} reason={out['afterScan']['facts']['reasonCode']} "
        f"poll={out['pollAfterOrderExpired']['http']} {out['pollAfterOrderExpired']['status']}")
    out["finishedAt"] = now()

    sub, before, after, f = out["submissionResult"], out["pollBeforeExpiry"], out["afterExpiryBeforeScan"], out["afterScan"]["facts"]
    out["verdict"] = {
        "submitFailed3dsUnavailable": sub["http"] == 200 and sub["status"] == "FAILED" and sub.get("failureCode") == "THREE_DS_CHANNEL_UNAVAILABLE",
        "noChannelInteraction": out["channelInteractionsAfter"] == out["channelInteractionsBefore"],
        "pollBeforeExpiryFailed": before["http"] == 200 and before["status"] == "FAILED",
        "AC02a_pollAfterExpiryFailed": after["poll"]["http"] == 200 and after["poll"]["status"] == "FAILED",
        "AC02a_windowBeforeScan": after["orderStatusAfter"] != "EXPIRED",
        "AC05_view410": after["view"]["http"] == 410 and after["view"]["code"] == "CHECKOUT_EXPIRED",
        "AC05_submit410": after["submitAgain"]["http"] == 410 and after["submitAgain"]["code"] == "CHECKOUT_EXPIRED",
        "AC06_noCheckoutUrl": after["clientGet"]["http"] == 200 and not after["clientGet"]["checkoutUrlPresent"],
        "AC02b_orderExpired": f["status"] == "EXPIRED" and f["reasonCode"] == "EXPIRY_REACHED"
                              and (f["session"] or {}).get("status") == "EXPIRED"
                              and (f["projection"] or {}).get("intentStatus") == "EXPIRED"
                              and (f["paymentIntent"] or {}).get("status") == "EXPIRED",
        "AC02b_orderExpiredEventOnce": sum(e["eventType"] == "order.expired" for e in out["afterScan"]["platformEvents"]) == 1,
        "AC02b_B4_pollAfterOrderExpired": out["pollAfterOrderExpired"]["http"] == 200 and out["pollAfterOrderExpired"]["status"] == "FAILED"}
    out["tokensChecked"] = len(SECRETS)
    _PB_TOKENS.append(token)
    save("P-B-" + started, out)
    leak(started, tokens=[token])


_PB_TOKENS = []


# ---------- 泄漏检索 ----------

def luhn_runs(text):
    found = 0
    for run in re.findall(r"(?<!\d)[3-6]\d{12,18}(?!\d)", text):
        digits = [int(c) for c in reversed(run)]
        found += sum(d if i % 2 == 0 else (d * 2 - 9 if d * 2 > 9 else d * 2) for i, d in enumerate(digits)) % 10 == 0
    return found


def leak(since, tokens=None):
    tokens = [t for t in (tokens or []) if t]
    if not tokens:  # 单独运行：窗口内新建的会话 Token（只读，只在内存中）
        tokens = [r[0] for r in sql("acquiring", "SELECT public_token FROM t_checkout_session WHERE create_time >= "
                                                 f"'{since[:19].replace('T', ' ')}'")]
    SECRETS.update(tokens)
    out = {"since": since, "tokensChecked": len(tokens)}
    for svc in ("acquiring-service", "payment-service"):
        result = subprocess.run(["docker", "logs", "--since", since, f"mor-test-{svc}-1"], capture_output=True, text=True)
        text = result.stdout + result.stderr
        lines = text.splitlines()
        out[svc] = {"lines": len(lines), "testPans": sum(text.count(p) for p in PANS),
                    "cvvKey": len(re.findall(r'(?i)"cvv"|cvv=', text)), "luhnCardLikeRuns": luhn_runs(text),
                    "checkoutToken": sum(text.count(t) for t in tokens), "chkTokenShape": len(re.findall(r"chk_[A-Za-z0-9]{20,}", text)),
                    "warnLines": sum(" WARN " in l for l in lines), "errorLines": sum(" ERROR " in l for l in lines),
                    "warnLoggers": sorted({m.group(1) for l in lines if " WARN " in l or " ERROR " in l
                                           for m in [re.search(r"\] ([\w.$]+)\s+:", l)] if m})}
    out["verdict"] = {"noLeak": all(out[s][k] == 0 for s in ("acquiring-service", "payment-service")
                                    for k in ("testPans", "cvvKey", "luhnCardLikeRuns", "checkoutToken", "chkTokenShape"))}
    save("leak-" + now(), out)


def main():
    args = sys.argv[1:]
    if not args or args[0] in ("-h", "--help"):
        print(__doc__)
        return
    log(f"== {' '.join(args)}")
    try:
        {"pa": pa, "pb": pb, "leak": lambda: leak(args[1])}[args[0]]()
    except (AssertionError, KeyError, IndexError, TypeError, ValueError, RuntimeError, OSError) as failure:
        log(f"{args[0]} 中止：{scrub(repr(failure))[:600]}")
        raise SystemExit(1)


if __name__ == "__main__":
    main()
