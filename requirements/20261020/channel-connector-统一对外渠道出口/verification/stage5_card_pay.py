"""任务卡 P1-stage5 补充（偏差 1）：新版本上真实读卡 → PAY 成功。

链路：card 建手输卡工具（POST /card/v1/instruments，调用方 payment-service，Payment 现行合同）
     → card 签发 CHANNEL_EXECUTION 读卡票据（POST /card/v1/material-tickets，调用方 payment-service）
     → channel-connector POST /channel-connector/v1/operations（PAY，NEW_PAYMENT，card.token = 票据，按 PaymentChannelClientImpl 组装）
     → protocol 进程内执行：Vault Reveal 账号配置 + 以 channel-connector-service 身份向 card 读卡 → 合成 PayerMax 卡直付。
核对：合成 PayerMax 流水 +1 且验签 / 卡字段有效；票据 used_at 已写；card 侧 Vault 事件；再次用同一票据读卡被拒；
     卡号与 CVV 不出现在本输出、合成 PayerMax 流水与 card / channel-connector / ops 日志中（只输出是否出现）。
测试卡为 PayerMax 公布的 UAT 测试卡（合成数据），与沙箱用例相同。用法：python3 -I stage5_card_pay.py <out.json>
"""
import json
import random
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import stage5_e2e as s5  # noqa: E402
from stage5_e2e import h  # noqa: E402

CARD = {"pan": s5.TEST_CARD["pan"], "expiryMonth": s5.TEST_CARD["expiryMonth"], "expiryYear": s5.TEST_CARD["expiryYear"],
        "cardholderName": s5.TEST_CARD["cardholderName"], "cvv": s5.TEST_CARD["cvv"]}


def card_call(path, caller, body):
    status, text = h.private_call("card", "POST", "/card/v1" + path, headers={"X-Caller-Service": caller}, body=body)
    return status, (json.loads(text) if text.startswith("{") else {"raw": text[:200]})


def digits(n):
    return str(random.randrange(10 ** (n - 1), 10 ** n))


def vault_card_events(since):
    rows = h.sql("vault", "SELECT caller_service, action, IFNULL(data_type,''), IFNULL(purpose,''), outcome, result_code "
                          f"FROM t_vault_access_event WHERE create_time >= '{since}' AND caller_service IN "
                          "('card-service','channel-connector-service','platform-support-service') ORDER BY create_time")
    return [dict(zip(("callerService", "action", "dataType", "purpose", "outcome", "resultCode"), r)) for r in rows]


def main(out):
    state = json.loads(s5.STATE.read_text())
    started = datetime.now(timezone.utc)
    since = started.strftime("%Y-%m-%d %H:%M:%S.%f")
    marks = {n: s5.log_offset(n) for n in ("card", "channel-connector", "platform", "ops-bff")}
    journal_before = len(s5.journal())
    result = {"startedAt": started.isoformat(), "channel": {k: state[k] for k in ("channelId", "channelAccountId", "midId")}}

    client_id, submission = digits(15), digits(16)
    status, instrument = card_call("/instruments", "payment-service", {
        "clientId": client_id, "sourceSubmissionId": submission, "card": CARD, "consentToSave": False,
        "expiresAt": (started + timedelta(minutes=30)).strftime("%Y-%m-%dT%H:%M:%SZ")})
    result["instrument"] = {"httpStatus": status, "instrumentRef": instrument.get("instrumentRef"),
                            "maskedPan": instrument.get("maskedPan"), "code": instrument.get("code")}
    if status != 200:
        return finish(out, result, state)

    attempt_id, action_id = digits(15) + "1", digits(16)  # 末位 1：合成 PayerMax 按 subject（缺省）同步 SUCCESS
    status, issued = card_call("/material-tickets", "payment-service", {
        "instrumentRef": instrument["instrumentRef"], "paymentAttemptId": attempt_id, "paymentActionId": action_id,
        "purpose": "CHANNEL_EXECUTION", "ttlSeconds": 90})
    ticket = issued.get("ticket")
    result["ticket"] = {"httpStatus": status, "issued": bool(ticket), "code": issued.get("code")}
    if not ticket:
        return finish(out, result, state)

    pay = s5.execute(state, "PAY", "NEW_PAYMENT", "10.00", {
        "card": {"token": ticket},
        "payment": {"attemptId": attempt_id, "amount": {"value": "10.00", "currency": "USD"}, "country": "US",
                    "returnUrl": "https://127.0.0.1:18446/return", "payerReference": "stage5-card-pay",
                    "clientIp": "203.0.113.10", "userAgent": "Mozilla/5.0 (stage5 card pay)"}})
    pay.pop("vaultChannelAccountEvents", None)
    result["pay"] = pay

    row = h.sql("card", "SELECT purpose, caller, used_at IS NOT NULL, attempt_id = " + attempt_id + ", action_id = " + action_id
                        + " FROM t_card_material_ticket WHERE instrument_ref = '" + instrument["instrumentRef"] + "'")
    result["ticketRow"] = [dict(zip(("purpose", "issuedByCaller", "used", "attemptMatches", "actionMatches"), r)) for r in row]
    # 同一票据再次读卡：已用 → 拒绝（channel-connector 身份），且不返回卡数据
    status, again = card_call("/channel-card-materials/reveal", "channel-connector-service", {"ticket": ticket})
    result["reuseTicket"] = {"httpStatus": status, "code": again.get("code"), "returnedCardFields": bool({"pan", "cvv"} & set(again))}
    ticket = None
    time.sleep(1)
    result["vaultEvents"] = vault_card_events(since)
    logs = {n: s5.log_since(n, marks[n]) for n in marks}
    result["cardAccessLog"] = s5.card_log_facts(logs["card"])
    journal_text = "\n".join(json.dumps(t) for t in s5.journal()[journal_before:])
    cvv_marker = '"cvv":"' + CARD["cvv"] + '"'
    result["panOrCvvPresent"] = {
        **{f"log:{n}": (CARD["pan"] in text or cvv_marker in text.replace(" ", "")) for n, text in logs.items()},
        "payermaxJournal": CARD["pan"] in journal_text or cvv_marker in journal_text}
    result["channelRequestCarriesFullPan"] = bool(result["pay"]["channelJournal"]["entries"]) and all(
        e.get("cardValid") for e in s5.journal()[journal_before:] if e.get("operation") == "orderAndPay")
    return finish(out, result, state)


def finish(out, result, state):
    text = json.dumps(result, ensure_ascii=False)
    if CARD["pan"] in text or '"cvv"' in text:
        raise SystemExit("输出含卡号或 CVV，未写入")
    s5.write(out, result, state)
    print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main(sys.argv[1])
