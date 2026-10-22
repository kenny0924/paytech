"""3DS 全链路 + 卡 BIN 库 阶段 5（tasks/E-stage5.md）：本机 e2e 支付链路场景。

链路（用户 T1 决定）：脚本以 acquiring-service 身份直接调用 Payment 内部接口（建 Intent → 申请卡工具 → pay → 提交查询 / 刷新），
Card、risk-decision、channel-connector 与合成 PayerMax（harness/PayerMax3dsMock.java）均为真实进程；不经 acquiring（T2 不做）。
渠道通知：按 PayerMax 规则（SHA256withRSA，sign 头 + 原始 Body，合成 PayerMax 私钥）签名，直接 POST 本机 Gateway 18080，
带 Nginx 会写入的 X-Entry-Channel: CHANNEL_CALLBACK 与 X-Real-IP（不经 Nginx，见报告证据边界）。
退避查单：本机无 XXL Admin，按 XXL Admin 的方式向 Payment 执行器（127.0.0.1:19993）POST /run 触发 compensationEventScan。
过期 / 24 小时场景用 SQL 把本次测试 Action 的时间拨回（与自动测试“拨时”同义），只改本脚本新建的行。

用法：python3 -I stage5_3ds.py <场景…|all>；结果合并写入 stage5-3ds.json。卡号只在请求体中出现，不写入输出。
"""
import base64
import json
import os
import random
import subprocess
import sys
import tempfile
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

RUNTIME = MAIN / "local-environment/e2e/runtime"
LOGS = MAIN / "local-environment/logs"
JOURNAL = RUNTIME / "payermax/payermax-journal.jsonl"
KEYS = RUNTIME / "payermax/payermax-mock-keys.json"
XXL_TOKEN = (RUNTIME / "card3ds/xxl-token").read_text().strip()
OUT = HERE / "stage5-3ds.json"
CLIENT_ID = "880000000901"
ENTITY_NO = "AE001"
CHANNEL_CODE = "PAYERMAX_MOCK"
CHANNEL_ACCOUNT_ID = "238501216471023616"
GATEWAY = "http://127.0.0.1:18080"
CARDS = {  # 公开测试卡号段；BIN 库种子版本 1：40128888 → VISA DEBIT GB（8 位），411111 → VISA US，555555 → MASTERCARD US
    "GB_VISA": "4012888888881881", "US_VISA": "4111111111111111", "US_MC": "5555555555554444",
    "UNRECOGNIZED": "6011000990139424", "NG_MC": "5105105105105100"}
results = {}


# ---------- 基础 ----------

def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def pay_api(method, path, body=None, headers=None):
    status, text = h.private_call("payment", method, path, headers=headers, body=body)
    try:
        return status, json.loads(text) if text else {}
    except ValueError:
        return status, {"raw": text[:300]}


def psql(statement):
    return h.sql("payment", statement)


class Submission:
    def __init__(self, amount="10.00"):
        self.amount = amount
        self.source = str(random.randrange(10 ** 14, 10 ** 15))
        status, body = pay_api("POST", "/payment-execution/v1/intents", {
            "clientId": CLIENT_ID, "sourceType": "COMMERCE_ORDER", "sourceId": self.source, "entityNo": ENTITY_NO,
            "clientPricingVersionId": "1", "industryCode": "DIGITAL_GOODS", "mccCode": "5999", "consumerCountryCode": "AE",
            "amount": amount, "currency": "USD",
            "expiresAt": (datetime.now(timezone.utc) + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%SZ")})
        assert status in (200, 201), (status, body)
        self.intent = body["intentId"]
        self.submission = None
        self.attempt = None

    def apply(self, card, check=True):
        self.submission = str(random.randrange(10 ** 14, 10 ** 15))
        status, body = pay_api("POST", "/payment-instrument/v1/card-tokens", {
            "clientId": CLIENT_ID, "intentId": self.intent, "sourceSubmissionId": self.submission, "paymentMethodCode": "CARD",
            "card": {"pan": CARDS[card], "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}})
        if not check:
            self.token = body.get("paymentInstrumentToken")
            return status, body
        assert status in (200, 201), (status, body)
        self.token = body["paymentInstrumentToken"]
        return status

    def pay(self):
        status, body = pay_api("POST", f"/payment-execution/v1/intents/{self.intent}/pay", {
            "clientId": CLIENT_ID, "sourceSubmissionId": self.submission, "captureMode": "AUTOMATIC",
            "payMethod": {"paymentMethodCode": "CARD", "paymentInstrumentToken": self.token},
            "returnUrl": "https://checkout.local.invalid/return", "consumerReference": "s5-payer-001",
            "consumerIpAddress": "203.0.113.10", "consumerUserAgent": "Mozilla/5.0 (stage5)"},
            {"Idempotency-Key": "submission:" + self.submission})
        self.attempt = body.get("attemptId")
        return status, body

    def submission_view(self):
        return pay_api("GET", f"/payment-execution/v1/intents/{self.intent}/submissions/{self.submission}?clientId={CLIENT_ID}")

    def refresh(self):
        return pay_api("POST", f"/payment-execution/v1/intents/{self.intent}/submissions/{self.submission}/refresh",
                       {"clientId": CLIENT_ID})

    def actions(self):
        return [dict(zip(("no", "type", "status", "authReq", "interaction", "expires", "code"), r)) for r in psql(
            "SELECT action_no, action_type, action_status, IFNULL(authentication_requirement,''), IFNULL(interaction_type,''), "
            "IFNULL(interaction_expires_at,''), IFNULL(channel_response_code,'') FROM t_payment_action "
            f"WHERE payment_attempt_id={self.attempt} ORDER BY action_no")]

    def facts(self):
        attempt = psql(f"SELECT attempt_status, mid_id, captured_amount FROM t_payment_attempt WHERE payment_attempt_id={self.attempt}")[0]
        intent = psql(f"SELECT intent_status, IFNULL(active_attempt_id,''), IFNULL(successful_attempt_id,'') FROM t_payment_intent "
                      f"WHERE payment_intent_id={self.intent}")[0]
        events = psql(f"SELECT event_type, execute_status, execute_count FROM t_compensation_event WHERE event_key='{self.attempt}' "
                      "ORDER BY id")
        refs = psql("SELECT COUNT(*) FROM t_channel_transaction_ref r JOIN t_payment_action a ON a.payment_action_id=r.payment_action_id "
                    f"WHERE a.payment_attempt_id={self.attempt}")[0][0]
        return {"attemptStatus": attempt[0], "midId": attempt[1], "capturedAmount": attempt[2], "intentStatus": intent[0],
                "activeAttemptId": intent[1], "successfulAttemptId": intent[2], "compensation": events, "channelTxnRefs": int(refs),
                "actions": self.actions()}


def mock_query(attempt, status):
    request = urllib.request.Request("http://127.0.0.1:18190/__control/query", method="POST",
                                     data=json.dumps({"outTradeNo": attempt, "status": status}).encode())
    with urllib.request.urlopen(request, timeout=5) as response:
        assert response.status == 200


def journal_for(attempt):
    rows = []
    for line in JOURNAL.read_text().splitlines():
        try:
            item = json.loads(line)
        except ValueError:
            continue
        if item.get("order") == attempt:
            rows.append({k: item.get(k) for k in ("operation", "code", "status", "dynamic3DS", "notifyUrlPresent", "queryControl",
                                                  "signatureValid", "cardValid")})
    return rows


def xxl_scan(event_types=""):
    body = {"jobId": 1, "executorHandler": "compensationEventScan", "executorParams": event_types,
            "executorBlockStrategy": "SERIAL_EXECUTION", "executorTimeout": 0, "logId": int(time.time() * 1000),
            "logDateTime": int(time.time() * 1000), "glueType": "BEAN", "glueSource": "", "glueUpdatetime": 0,
            "broadcastIndex": 0, "broadcastTotal": 1}
    request = urllib.request.Request("http://127.0.0.1:19993/run", method="POST", data=json.dumps(body).encode(),
                                     headers={"XXL-JOB-ACCESS-TOKEN": XXL_TOKEN, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read())


def wait_due(attempt, timeout=150):
    """等到本 Attempt 的 PAY_RESULT_QUERY 到期（next_execute_at <= 当前 UTC），返回等待秒数。"""
    start = time.time()
    while time.time() - start < timeout:
        rows = psql(f"SELECT TIMESTAMPDIFF(MICROSECOND, UTC_TIMESTAMP(6), next_execute_at) FROM t_compensation_event "
                    f"WHERE event_key='{attempt}' AND event_type='PAY_RESULT_QUERY'")
        if not rows or int(rows[0][0]) <= 0:
            return round(time.time() - start, 1)
        time.sleep(min(5, int(rows[0][0]) / 1e6 + 0.5))
    raise SystemExit(f"PAY_RESULT_QUERY 未到期：{attempt}")


def wait_status(sub, statuses, timeout=30):
    start = time.time()
    while time.time() - start < timeout:
        status = psql(f"SELECT attempt_status FROM t_payment_attempt WHERE payment_attempt_id={sub.attempt}")[0][0]
        if status in statuses:
            return status, round(time.time() - start, 2)
        time.sleep(0.5)
    return status, None


def risk_assessment(attempt):
    rows = h.sql("risk-decision", "SELECT IFNULL(issuer_country_code,''), IFNULL(card_brand,''), IFNULL(funding_type,''), "
                 f"IFNULL(authentication_requirement,'') FROM t_transaction_risk_assessment WHERE payment_attempt_id={attempt}")
    return dict(zip(("issuerCountryCode", "cardBrand", "fundingType", "authenticationRequirement"), rows[0])) if rows else None


def callback(attempt, status="SUCCESS", tamper=False, complete_time=None, notify_time=None):
    keys = json.loads(KEYS.read_text())
    body = json.dumps({"code": "APPLY_SUCCESS", "msg": "", "keyVersion": "1", "appId": keys["appId"], "merchantNo": keys["merchantNo"],
                       "notifyTime": notify_time or now(), "notifyType": "PAYMENT",
                       "data": {"outTradeNo": attempt, "tradeToken": "T" + attempt, "status": status, "totalAmount": 10,
                                "currency": "USD", "completeTime": complete_time or now()}}, separators=(",", ":")).encode()
    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as pem:
        os.chmod(pem.name, 0o600)
        pem.write("-----BEGIN PRIVATE KEY-----\n" + keys["payermaxPrivateKey"] + "\n-----END PRIVATE KEY-----\n")
    try:
        signature = base64.b64encode(subprocess.run(["openssl", "dgst", "-sha256", "-sign", pem.name], input=body,
                                                    capture_output=True, check=True).stdout).decode()
    finally:
        os.unlink(pem.name)
    if tamper:
        body = body.replace(b'"status":"' + status.encode(), b'"status":"FAILED"')
    request = urllib.request.Request(f"{GATEWAY}/api/channel-callbacks/v1/{CHANNEL_CODE}/{CHANNEL_ACCOUNT_ID}", method="POST",
                                     data=body, headers={"Content-Type": "application/json", "sign": signature,
                                                         "X-Entry-Channel": "CHANNEL_CALLBACK", "X-Real-IP": "203.0.113.50"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read().decode()[:200]
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode()[:300]


def callbacks(attempt):
    return [dict(zip(("callbackId", "signature", "status", "mapping", "target"), r)) for r in h.sql(
        "channel-connector", "SELECT c.channel_callback_id, c.signature_status, c.callback_status, IFNULL(i.mapping_status,''), "
        "IFNULL(i.target_id,'') FROM t_channel_callback c LEFT JOIN t_channel_callback_item i ON i.channel_callback_id=c.channel_callback_id "
        f"WHERE i.target_id='{attempt}' OR c.received_at >= UTC_TIMESTAMP() - INTERVAL 0 SECOND ORDER BY c.received_at")]


def challenge(card="GB_VISA", amount="10.00"):
    sub = Submission(amount)
    sub.apply(card)
    status, body = sub.pay()
    return sub, status, body


def record(name, data):
    results[name] = data
    print(name, json.dumps(data, ensure_ascii=False)[:1500])


# ---------- 场景 ----------

def s_challenge_success_by_handler():
    """AC-01（风控要求 3DS → PAY 带 do3DS 并返回挑战，无 AUTHENTICATE）+ AC-04（不依赖通知，退避查单推进成功）+ BIN AC-08 发卡国命中。"""
    sub, status, body = challenge("GB_VISA")
    after_pay = sub.facts()
    view_status, view = sub.submission_view()
    mock_query(sub.attempt, "SUCCESS")
    waited = wait_due(sub.attempt)
    scan = xxl_scan("PAY_RESULT_QUERY")
    final_status, latency = wait_status(sub, {"SUCCEEDED", "FAILED"})
    record("AC01_AC04_handler", {
        "payHttp": status, "payStatus": body.get("status"), "nextActionType": (body.get("nextAction") or {}).get("type"),
        "redirectUrlPresent": bool((body.get("nextAction") or {}).get("redirectUrl")),
        "riskAssessment": risk_assessment(sub.attempt), "afterPay": after_pay,
        "submissionView": {"http": view_status, "status": view.get("status"), "interaction": view.get("interaction")},
        "waitedForBackoffSeconds": waited, "xxlTrigger": scan, "finalStatus": final_status, "after": sub.facts(),
        "mock": journal_for(sub.attempt)})


def s_challenge_failed_refresh():
    """AC-05：挑战失败 → 刷新查单得到 FAILED，Attempt 失败、Intent 可重新提交（标记金额 333 触发 3DS）。"""
    sub, status, body = challenge("US_VISA", "333.00")
    mock_query(sub.attempt, "FAILED")
    first_attempt = sub.attempt
    r_status, r_body = sub.refresh()
    facts = sub.facts()
    sub.apply("US_VISA")
    mock_query("-", "-")
    retry_status, retry_body = sub.pay()
    record("AC05_failed", {"payHttp": status, "nextActionType": (body.get("nextAction") or {}).get("type"),
                           "riskAssessment": risk_assessment(first_attempt),
                           "refresh": {"http": r_status, "status": r_body.get("status"), "failureCode": r_body.get("failureCode")},
                           "afterRefresh": facts, "mock": journal_for(first_attempt),
                           "resubmit": {"http": retry_status, "status": retry_body.get("status"), "attemptId": retry_body.get("attemptId"),
                                        "newAttempt": retry_body.get("attemptId") != first_attempt}})


def s_refresh_rate_limit():
    """AC-06：轮询触发立即查单；5 秒限频窗口内重复刷新不再查单；窗口后再刷新得到成功。"""
    sub, status, body = challenge("GB_VISA")
    time.sleep(6)  # 越过提交后立即执行的首轮查单可能占用的限频窗口
    before = len([r for r in journal_for(sub.attempt) if r["operation"] == "orderQuery"])
    first = sub.refresh()
    second = sub.refresh()
    third = sub.refresh()
    mid = len([r for r in journal_for(sub.attempt) if r["operation"] == "orderQuery"])
    mock_query(sub.attempt, "SUCCESS")
    time.sleep(5.5)
    final = sub.refresh()
    after = len([r for r in journal_for(sub.attempt) if r["operation"] == "orderQuery"])
    record("AC06_refresh", {"payHttp": status, "queriesBefore": before, "threeRefreshesWithinWindow": [first[1].get("status"), second[1].get("status"), third[1].get("status")],
                            "queriesAfterThreeRefreshes": mid, "afterWindowRefresh": {"http": final[0], "status": final[1].get("status")},
                            "queriesAtEnd": after, "after": sub.facts()})


def s_callback():
    """AC-07 / AC-08：签名通知经 Gateway → channel-connector 验签 → MQ → Payment 查单确认；重复通知、通知与刷新并发只写一次；验签失败 401 并记录。"""
    sub, status, body = challenge("GB_VISA")
    mock_query(sub.attempt, "SUCCESS")
    t0 = time.time()
    first = callback(sub.attempt)
    final_status, latency = wait_status(sub, {"SUCCEEDED", "FAILED"}, 20)
    duplicate = callback(sub.attempt, complete_time=None)
    same_body_status = None
    refresh_after = sub.refresh()
    time.sleep(3)
    tampered_sub, _, _ = challenge("GB_VISA")
    mock_query(tampered_sub.attempt, "SUCCESS")
    tampered = callback(tampered_sub.attempt, tamper=True)
    time.sleep(3)
    record("AC07_AC08_callback", {
        "payHttp": status, "callback": first, "attemptStatusAfterCallback": final_status, "secondsToTerminal": latency,
        "duplicateCallback": duplicate, "refreshAfterTerminal": {"http": refresh_after[0], "status": refresh_after[1].get("status")},
        "after": sub.facts(), "callbackRows": callbacks(sub.attempt), "mock": journal_for(sub.attempt),
        "tampered": {"http": tampered[0], "body": tampered[1], "attemptStatus": tampered_sub.facts()["attemptStatus"],
                     "rejectedRows": h.sql("channel-connector", "SELECT COUNT(*) FROM t_channel_callback WHERE signature_status='REJECTED' "
                                           "AND received_at >= UTC_TIMESTAMP(6) - INTERVAL 30 SECOND")[0][0]}})
    tampered_sub_cleanup = mock_query(tampered_sub.attempt, "FAILED")
    tampered_sub.refresh()
    del same_body_status, tampered_sub_cleanup, t0


def s_concurrent():
    """AC-08：同一结果的通知两次 + 刷新 + 退避 Handler 并发到达，终态只写一次，PaymentSucceeded 只登记一次。"""
    import threading
    sub, status, body = challenge("GB_VISA")
    mock_query(sub.attempt, "SUCCESS")
    psql(f"UPDATE t_compensation_event SET next_execute_at=UTC_TIMESTAMP(6) WHERE event_key='{sub.attempt}' AND event_type='PAY_RESULT_QUERY'")
    outcomes = []
    fixed = now()
    jobs = [lambda: outcomes.append(("callback", callback(sub.attempt, complete_time=fixed, notify_time=fixed)[0])),
            lambda: outcomes.append(("callback-same-body", callback(sub.attempt, complete_time=fixed, notify_time=fixed)[0])),
            lambda: outcomes.append(("refresh", sub.refresh()[1].get("status"))),
            lambda: outcomes.append(("xxl", xxl_scan("PAY_RESULT_QUERY").get("code")))]
    threads = [threading.Thread(target=job) for job in jobs]
    [t.start() for t in threads]
    [t.join() for t in threads]
    final_status, _ = wait_status(sub, {"SUCCEEDED", "FAILED"}, 20)
    time.sleep(3)
    record("AC08_concurrent", {"payHttp": status, "outcomes": outcomes, "final": final_status, "after": sub.facts(),
                               "paymentSucceededEvents": psql(f"SELECT COUNT(*) FROM t_compensation_event WHERE event_key='{sub.attempt}' "
                                                              "AND event_type='PAYMENT_SUCCEEDED'")[0][0],
                               "callbackRows": callbacks(sub.attempt)})


def backdate(sub, minutes_expiry=None, minutes_created=None):
    sets = []
    if minutes_expiry is not None:
        sets.append(f"interaction_expires_at = UTC_TIMESTAMP(6) - INTERVAL {minutes_expiry} MINUTE")
    if minutes_created is not None:
        sets.append(f"create_time = UTC_TIMESTAMP(6) - INTERVAL {minutes_created} MINUTE")
    psql(f"UPDATE t_payment_action SET {', '.join(sets)} WHERE payment_attempt_id={sub.attempt} AND action_type='PAY'")


def s_expiry():
    """AC-09 / AC-10：交互过期后：仍处理中 → 确认中、不允许重新提交；CLOSED → 失败可重提；无此交易（>10 分钟）→ 失败；>24 小时仍无终态 → 转人工。"""
    out = {}
    sub, _, _ = challenge("GB_VISA")
    mock_query(sub.attempt, "PENDING")
    backdate(sub, minutes_expiry=1)
    time.sleep(5.5)
    refreshed = sub.refresh()
    view = sub.submission_view()
    resubmit = Submission.__new__(Submission)
    resubmit.__dict__.update(sub.__dict__)
    blocked = resubmit.apply("GB_VISA", check=False)
    out["pendingAfterExpiry"] = {"refresh": {"status": refreshed[1].get("status"), "interaction": refreshed[1].get("interaction")},
                                 "view": {"status": view[1].get("status"), "interaction": view[1].get("interaction")},
                                 "resubmitApplyInstrument": {"http": blocked[0], "code": blocked[1].get("code"), "detail": str(blocked[1].get("detail"))[:120]},
                                 "facts": sub.facts()}
    mock_query(sub.attempt, "CLOSED")
    time.sleep(5.5)
    closed = sub.refresh()
    out["closed"] = {"refresh": {"status": closed[1].get("status"), "failureCode": closed[1].get("failureCode")}, "facts": sub.facts()}
    retry = Submission.__new__(Submission)
    retry.__dict__.update(sub.__dict__)
    retry.apply("US_VISA")
    retry_status, retry_body = retry.pay()
    out["closedResubmit"] = {"http": retry_status, "status": retry_body.get("status")}

    nf, _, _ = challenge("GB_VISA")
    mock_query(nf.attempt, "NOT_EXIST")
    time.sleep(5.5)
    early = nf.refresh()
    backdate(nf, minutes_expiry=1, minutes_created=11)
    time.sleep(5.5)
    late = nf.refresh()
    out["notFound"] = {"within10Min": early[1].get("status"), "after10Min": {"status": late[1].get("status"),
                       "failureCode": late[1].get("failureCode")}, "facts": nf.facts()}

    stuck, _, _ = challenge("GB_VISA")
    mock_query(stuck.attempt, "PENDING")
    backdate(stuck, minutes_expiry=600, minutes_created=24 * 60 + 5)
    psql(f"UPDATE t_compensation_event SET next_execute_at=UTC_TIMESTAMP(6) WHERE event_key='{stuck.attempt}' AND event_type='PAY_RESULT_QUERY'")
    out["xxl"] = xxl_scan("PAY_RESULT_QUERY")
    time.sleep(4)
    out["over24h"] = {"facts": stuck.facts(), "compensation": psql(
        "SELECT event_type, execute_status, execute_count, IFNULL(last_error_code,''), LEFT(IFNULL(last_error_summary,''),120) "
        f"FROM t_compensation_event WHERE event_key='{stuck.attempt}'")}
    record("AC09_AC10_expiry", out)


def s_unknown_recovery():
    """R3：渠道 PAY 结果未知（合成 PayerMax 末位 3 返回 SYSTEM_ERROR 不可控），此处以已处理中的 Attempt 观察 UNKNOWN→查单推进由自动测试覆盖。"""
    record("R3_unknown", {"executed": False, "reason": "合成 PayerMax 下单结果按 Attempt ID 末位决定，脚本无法指定；由 payment 自动测试覆盖"})


SCENARIOS = {"handler": s_challenge_success_by_handler, "failed": s_challenge_failed_refresh, "refresh": s_refresh_rate_limit,
             "callback": s_callback, "concurrent": s_concurrent, "expiry": s_expiry}


def main():
    names = sys.argv[1:] or ["all"]
    if names == ["all"]:
        names = list(SCENARIOS)
    previous = json.loads(OUT.read_text()) if OUT.exists() else {}
    for name in names:
        started = now()
        try:
            SCENARIOS[name]()
        except (AssertionError, KeyError, IndexError) as failure:
            record(name + "_error", {"error": repr(failure)[:600]})
        results.setdefault("_runs", []).append({"scenario": name, "startedAt": started, "finishedAt": now()})
    previous.update({k: v for k, v in results.items() if k != "_runs"})
    previous.setdefault("_runs", []).extend(results.get("_runs", []))
    text = json.dumps(previous, ensure_ascii=False, indent=2)
    for pan in CARDS.values():
        assert pan not in text, "输出中出现卡号"
    OUT.write_text(text + "\n")


if __name__ == "__main__":
    main()
