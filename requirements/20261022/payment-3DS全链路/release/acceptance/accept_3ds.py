"""阶段 7 mor-test 3DS 验收（acceptance-plan 第 2 节），由 verification/stage5_3ds.py 改写。

链路：一次性探针容器（接入 mor-test-backend）以 acquiring-service 身份直连 Payment 内部接口（建 Intent → 申请卡工具 → pay →
提交查询 / 刷新）；Card、risk-decision、channel-connector 为 mor-test 真实容器。只读 SELECT 核对；不写库、不触发 XXL。

子命令：
  ac02                 DEMO_NI（两个 MID 均未声明 SECURITY.3DS_MODE）下 600.00 USD 卡支付 → FAILED THREE_DS_CHANNEL_UNAVAILABLE，0 渠道请求
  ac07 <accountId>     向 /api/channel-callbacks/v1/PAYERMAX/{accountId} 发随意签名的 PayerMax 形通知（ingress 网络探针直连 api-gateway:18080）
  start <card> <amount>  AC-12：建单并 pay，输出挑战跳转（只打印到终端，不写文件）与交易标识
  status <intentId> <submissionId> <attemptId>   AC-12 完成 ACS 后：提交查询与事实核对
  refresh <intentId> <submissionId>              AC-06（可选）
  instrument <label> <card>                      BIN AC-02 / AC-04 / AC-06：只建卡工具并读快照
结果合并写入 accept-3ds.json；卡号与跳转地址不写入输出。
"""
import json
import random
import sys
import time
from datetime import datetime, timedelta, timezone

import mt

h = mt.h
OUT = "accept-3ds.json"
CLIENT_ID = "236087245667409921"  # mor-test ACTIVE Client（只作 Payment 侧标识）
ENTITY_NO = "AE001"
CALLER = {"X-Caller-Service": "acquiring-service"}
CARDS = {  # 公开测试卡号段；BIN 库种子 v1：40128888 → VISA DEBIT GB（8 位）；PayerMax UAT 公开测试卡
    "GB_VISA": "4012888888881881", "PMX_UAT": "4444333322221111", "UNRECOGNIZED": "6011000990139424"}


def pay_api(method, path, body=None, headers=None):
    status, text = h.private_call("payment", method, path, headers={**CALLER, **(headers or {})}, body=body)
    try:
        return status, json.loads(text) if text else {}
    except ValueError:
        return status, {"raw": text[:300]}


def psql(statement):
    return mt.sql("payment", statement)


class Submission:
    def __init__(self, amount, intent=None, submission=None, attempt=None):
        self.amount, self.intent, self.submission, self.attempt = amount, intent, submission, attempt
        if intent:
            return
        self.source = "c3ds" + str(random.randrange(10 ** 10, 10 ** 11))
        status, body = pay_api("POST", "/payment-execution/v1/intents", {
            "clientId": CLIENT_ID, "sourceType": "COMMERCE_ORDER", "sourceId": str(random.randrange(10 ** 14, 10 ** 15)),
            "entityNo": ENTITY_NO, "clientPricingVersionId": "1", "industryCode": "DIGITAL_GOODS", "mccCode": "5999",
            "consumerCountryCode": "AE", "amount": amount, "currency": "USD",
            "expiresAt": (datetime.now(timezone.utc) + timedelta(hours=2)).strftime("%Y-%m-%dT%H:%M:%SZ")})
        assert status in (200, 201), (status, body)
        self.intent = body["intentId"]

    def apply(self, card, expiry=("03", "2030"), name="James Smith", cvv="123"):
        self.submission = str(random.randrange(10 ** 14, 10 ** 15))
        status, body = pay_api("POST", "/payment-instrument/v1/card-tokens", {
            "clientId": CLIENT_ID, "intentId": self.intent, "sourceSubmissionId": self.submission, "paymentMethodCode": "CARD",
            "card": {"pan": CARDS[card], "expiryMonth": expiry[0], "expiryYear": expiry[1], "cardholderName": name, "cvv": cvv}})
        assert status in (200, 201), (status, body)
        self.token = body["paymentInstrumentToken"]

    def pay(self):
        status, body = pay_api("POST", f"/payment-execution/v1/intents/{self.intent}/pay", {
            "clientId": CLIENT_ID, "sourceSubmissionId": self.submission, "captureMode": "AUTOMATIC",
            "payMethod": {"paymentMethodCode": "CARD", "paymentInstrumentToken": self.token},
            "returnUrl": "https://checkout.mor-test.invalid/return", "consumerReference": "c3ds-payer-001",
            "consumerIpAddress": "203.0.113.10", "consumerUserAgent": "Mozilla/5.0 (c3ds stage7)"},
            {"Idempotency-Key": "submission:" + self.submission})
        self.attempt = body.get("attemptId")
        return status, body

    def view(self):
        return pay_api("GET", f"/payment-execution/v1/intents/{self.intent}/submissions/{self.submission}?clientId={CLIENT_ID}")

    def refresh(self):
        return pay_api("POST", f"/payment-execution/v1/intents/{self.intent}/submissions/{self.submission}/refresh",
                       {"clientId": CLIENT_ID})

    def facts(self):
        actions = [dict(zip(("no", "type", "status", "authReq", "interaction", "code"), r)) for r in psql(
            "SELECT action_no, action_type, action_status, IFNULL(authentication_requirement,''), IFNULL(interaction_type,''), "
            f"IFNULL(channel_response_code,'') FROM t_payment_action WHERE payment_attempt_id={self.attempt} ORDER BY action_no")]
        attempt = psql(f"SELECT attempt_status, IFNULL(mid_id,''), IFNULL(captured_amount,'') FROM t_payment_attempt "
                       f"WHERE payment_attempt_id={self.attempt}")[0]
        intent = psql(f"SELECT intent_status, IFNULL(active_attempt_id,''), IFNULL(successful_attempt_id,'') FROM t_payment_intent "
                      f"WHERE payment_intent_id={self.intent}")[0]
        events = psql(f"SELECT event_type, execute_status, execute_count FROM t_compensation_event WHERE event_key='{self.attempt}' "
                      "ORDER BY id")
        return {"attempt": attempt, "intent": intent, "actions": actions, "compensation": events,
                "routing": psql("SELECT IFNULL(selected_mid_id,''), decision_status, IFNULL(decision_reason_code,'') "
                                f"FROM t_routing_decision WHERE payment_attempt_id={self.attempt}"),
                "token": psql("SELECT IFNULL(card_brand,''), IFNULL(funding_type,''), IFNULL(card_level,''), IFNULL(issuer_country,''), "
                              "IFNULL(bin_library_version_no,''), IFNULL(bin_match_length,'') FROM t_payment_instrument_token "
                              f"WHERE payment_intent_id={self.intent} AND source_submission_id={self.submission}"),
                "risk": mt.sql("risk-decision", "SELECT IFNULL(issuer_country_code,''), IFNULL(card_brand,''), IFNULL(funding_type,''), "
                               "IFNULL(authentication_requirement,'') FROM t_transaction_risk_assessment "
                               f"WHERE payment_attempt_id={self.attempt}")}


def interactions():
    return int(mt.sql("channel-connector", "SELECT COUNT(*) FROM t_channel_interaction")[0][0])


def scrub(body):
    """去掉跳转地址等临时指令，只留类型与是否存在。"""
    text = json.loads(json.dumps(body))
    for key in ("interaction", "nextAction"):
        value = text.get(key) if isinstance(text, dict) else None
        if isinstance(value, dict):
            url = value.get("redirectUrl") or ""
            text[key] = {"type": value.get("type"), "expiresAt": value.get("expiresAt"), "keys": sorted(value),
                         "redirectUrlPresent": bool(url), "redirectHost": url.split("/")[2] if url else None}
    return text


def ac02():
    before = interactions()
    sub = Submission("600.00")
    sub.apply("GB_VISA")
    status, body = sub.pay()
    time.sleep(2)
    mt.save(OUT, "AC-02", {"at": mt.now(), "intentId": sub.intent, "submissionId": sub.submission, "attemptId": sub.attempt,
                           "payHttp": status, "pay": scrub(body), "facts": sub.facts() if sub.attempt else None,
                           "channelInteractionsBefore": before, "channelInteractionsAfter": interactions()})


def ingress_probe(method, url, headers, data, timeout):
    """Gateway 只绑定 mor-test-ingress（GATEWAY_BIND_ADDRESS）：探针接入 ingress 网络，模拟 Nginx 转发。"""
    import subprocess
    payload = "\n".join([method, url] + [f"{k}: {v}" for k, v in headers.items()]) + "\n\n" + data.decode()
    result = subprocess.run(["docker", "run", "--rm", "-i", "--network", "mor-test-ingress", "--entrypoint", "java",
                             "-v", f"{h._probe_classes()}:/probe:ro", h.PROBE_IMAGE, "-cp", "/probe", "PrivateHttpProbe"],
                            input=payload, capture_output=True, text=True, timeout=timeout + 30)
    if result.returncode != 0:
        raise RuntimeError(f"ingress 探针失败：{result.stderr.strip()[:300]}")
    status, _, text = result.stdout.partition("\n")
    return int(status), text


def ac07(account_id):
    body = json.dumps({"code": "APPLY_SUCCESS", "msg": "", "keyVersion": "1", "appId": "c3ds-reverse", "merchantNo": "c3ds-reverse",
                       "notifyTime": mt.now(), "notifyType": "PAYMENT",
                       "data": {"outTradeNo": "c3ds-reverse-" + str(random.randrange(10 ** 8)), "status": "SUCCESS",
                                "totalAmount": 10, "currency": "USD", "completeTime": mt.now()}}, separators=(",", ":"))
    before = mt.sql("channel-connector", "SELECT COUNT(*), IFNULL(MAX(channel_callback_id),0) FROM t_channel_callback")[0]
    status, text = ingress_probe("POST", f"http://api-gateway:18080/api/channel-callbacks/v1/PAYERMAX/{account_id}",
                                 {"Content-Type": "application/json", "sign": "c3dsInvalidSignature==",
                                  "X-Entry-Channel": "CHANNEL_CALLBACK", "X-Real-IP": "203.0.113.50"}, body.encode(), 30)
    time.sleep(1)
    rows = mt.sql("channel-connector", "SELECT channel_callback_id, channel_id, IFNULL(channel_account_id,''), signature_status, "
                  f"callback_status FROM t_channel_callback WHERE channel_callback_id>{before[1]} ORDER BY channel_callback_id")
    mt.save(OUT, f"AC-07-{account_id}", {"at": mt.now(), "http": status, "body": text[:400], "callbacksBefore": before[0],
                                          "newCallbacks": rows})


def start(card, amount):
    before = interactions()
    sub = Submission(amount)
    sub.apply(card)
    status, body = sub.pay()
    interaction = body.get("nextAction") or body.get("interaction") or {}
    print("CHALLENGE (不写文件):", json.dumps(interaction, ensure_ascii=False))
    mt.save(OUT, "AC-12-start", {"at": mt.now(), "intentId": sub.intent, "submissionId": sub.submission, "attemptId": sub.attempt,
                                 "amount": amount, "payHttp": status, "pay": scrub(body), "channelInteractionsBefore": before,
                                 "channelInteractionsAfter": interactions(), "facts": sub.facts() if sub.attempt else None})


def status_(intent, submission, attempt, label="AC-12-after"):
    sub = Submission(None, intent, submission, attempt)
    code, body = sub.view()
    mt.save(OUT, label + "-" + mt.now(), {"viewHttp": code, "view": scrub(body), "facts": sub.facts(),
                                           "interactions": mt.sql("channel-connector",
                                                                  "SELECT channel_interaction_id, IFNULL(payment_action_id,''), interaction_status, "
                                                                  "IFNULL(http_status,''), IFNULL(channel_result_code,'') FROM t_channel_interaction "
                                                                  "ORDER BY channel_interaction_id")})


def instrument(label, card):
    """BIN AC-02 / AC-04 / AC-06：只申请卡工具（不 pay），读 Token 冻结的卡属性快照。"""
    sub = Submission("10.00")
    sub.apply(card)
    rows = psql("SELECT IFNULL(card_brand,''), IFNULL(funding_type,''), IFNULL(card_level,''), IFNULL(card_product,''), "
                "IFNULL(issuer_country,''), IFNULL(issuer_region,''), IFNULL(bin_library_version_no,''), IFNULL(bin_match_length,'') "
                f"FROM t_payment_instrument_token WHERE payment_intent_id={sub.intent} AND source_submission_id={sub.submission}")
    mt.save(OUT, f"instrument-{label}", {"at": mt.now(), "card": card, "intentId": sub.intent, "submissionId": sub.submission,
                                         "token": dict(zip(("cardBrand", "fundingType", "cardLevel", "cardProduct", "issuerCountry",
                                                            "issuerRegion", "binLibraryVersionNo", "binMatchLength"), rows[0]))})


def refresh(intent, submission):
    sub = Submission(None, intent, submission)
    before = interactions()
    out = [sub.refresh()[0] for _ in range(3)]
    time.sleep(2)
    mt.save(OUT, "AC-06", {"at": mt.now(), "refreshHttp": out, "interactionsBefore": before, "interactionsAfter": interactions()})


if __name__ == "__main__":
    command, *args = sys.argv[1:]
    {"ac02": ac02, "ac07": ac07, "instrument": instrument, "start": start, "status": status_, "refresh": refresh}[command](*args)
