"""阶段 5 本机全链路（任务卡 14）：PayerMax 卡直付合成渠道冒烟（支付成功、查询回传、退款一笔），全量环境、真实 Platform 与 Vault。

由 requirements/20261014/payment-PayerMax卡直付对接/assets/harness/verify_card.py 与 PayerMax 退款 harness 改写，原文件不改：
  1. 渠道沙箱验证（运营入口 /external-api-admin/v1 verify-accounts / verify-cases / verify-runs）：CARD_SUCCESS、CARD_DECLINED，
     以及合成渠道专有的参数错误 / 系统繁忙 / 错签名；证据中的卡号只允许前 6 后 4（444433******1111），全部证据没有卡号与 CVV 原值。
     验证账号 MOCK_CARD 的参数与凭据取合成渠道密钥文件；测试卡为 PayerMax 公布的 UAT 测试卡（合成数据）。
  2. 查询回传：对一笔真实支付成功的 Attempt（参数传入，或取 mor_payment_v2e2e 最近一笔走本渠道的 SUCCEEDED Attempt）
     直连 Platform 执行 PAY_QUERY，channelAccount 为真实账号（Vault Token 取自 mor_channel_connector_v2e2e）。
  3. 退款一笔：同一 Attempt 直连 Platform 执行 REFUND（退款号末位 2 → 合成渠道同步 REFUND_SUCCESS）。
     说明：Payment 尚无退款接口（PAY-I04 未实现），此处验证的是渠道侧退款能力，不是 Payment 退款链路。
  4. 合成渠道流水：本次请求签名、外层、requestTime、金额全部有效；卡直付请求卡字段格式有效。
用法：python3 card_smoke.py [attemptId]；回执写 local-environment/e2e/runtime/payermax/card-smoke.json，只含遮蔽卡号与结论。
"""
import json
import random
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import channel_setup as cs  # noqa: E402
import e2e_http as h  # noqa: E402

RESULTS = []
ACCOUNT_CODE = "MOCK_CARD"
REASON = "阶段 5 本机全链路卡直付冒烟"


def record(step, ok, evidence):
    RESULTS.append((step, "PASS" if ok else "FAIL", evidence))
    print(f"{'PASS' if ok else 'FAIL'} {step} | {evidence}")
    return ok


def journal():
    path = cs.RUNTIME / "payermax-journal.jsonl"
    return [json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []


def sandbox_cases(verify_card):
    token = cs.login()
    base = f"{cs.ADMIN}/integrations/{cs.INTEGRATION}"
    keys = cs.mock_keys()
    verify_card.upsert(token, base, "verify-accounts", ACCOUNT_CODE, {
        "accountName": "本机合成 PayerMax（测试卡）", **cs.identity(),
        "parameters": {k: keys[k] for k in ("appId", "merchantNo", "keyVersion", "payermaxPublicKey")},
        "credentials": {"merchantPrivateKey": keys["merchantPrivateKey"],
                        **{"testCard." + k: v for k, v in verify_card.TEST_CARD.items()}},
        "reason": REASON})
    cases = {code: verify_card.CASES[code] for code in ("CARD_SUCCESS", "CARD_DECLINED")}
    cases.update(verify_card.MOCK_ONLY)
    receipt = {}
    for code, (name, steps) in cases.items():
        verify_card.upsert(token, base, "verify-cases", code, {"caseName": name, "description": None, "accountCode": ACCOUNT_CODE,
                                                               "steps": steps, "reason": REASON})
        try:
            detail = verify_card.run(token, base, code)
            card = verify_card.evidence(detail)
        except AssertionError as failure:
            record(f"沙箱 {code}（{name}）", False, f"{failure}")
            continue
        step = detail["steps"][0]
        masked = card.get("cardIdentifierNo")
        receipt[code] = {"runCode": detail["runCode"], "runStatus": detail["runStatus"],
                         "executeStatus": (step.get("result") or {}).get("executeStatus"), "evidenceCardNumber": masked}
        record(f"沙箱 {code}（{name}）", detail["runStatus"] == "PASSED" and masked in (verify_card.MASKED_PAN, None),
               f"run={detail['runCode']} status={detail['runStatus']} execute={receipt[code]['executeStatus']} "
               f"evidencePan={masked} failure={detail.get('failureSummary')}")
    return receipt


def real_attempt(argument):
    if argument:
        return argument
    rows = h.sql("payment", "SELECT payment_attempt_id FROM t_payment_attempt WHERE attempt_status='SUCCEEDED' "
                            f"AND channel_id={cs.CHANNEL_ID} ORDER BY create_time DESC LIMIT 1")
    return rows[0][0] if rows else None


def account_token():
    return h.sql("channel-connector", f"SELECT account_vault_token FROM t_channel_account WHERE channel_account_id={cs.ACCOUNT_ID}")[0][0]


def query_back(attempt_id, token):
    result = cs.execute("pay-query-real-attempt", "PAY_QUERY", cs.INTEGRATION,
                        {"channelAccount": cs.identity(token), "payment": {"attemptId": str(attempt_id)}})
    outputs = result.get("outputs") or {}
    return record("查询回传：真实成功 Attempt 的 PAY_QUERY", result.get("executeStatus") == "SUCCESS"
                  and outputs.get("channelTransactionRef") == f"T{attempt_id}",
                  f"attemptId={attempt_id} execute={result.get('executeStatus')} paymentStatus={outputs.get('paymentStatus')} "
                  f"channelTransactionRef={outputs.get('channelTransactionRef')}")


def refund_one(attempt_id, token):
    refund_id = f"RFD{random.randrange(10**11):011d}2"
    result = cs.execute("refund-real-attempt", "REFUND", cs.INTEGRATION, {
        "channelAccount": cs.identity(token),
        "refund": {"refundId": refund_id, "paymentAttemptId": str(attempt_id), "amount": {"value": "5.00", "currency": "USD"},
                   "reason": "Stage 5 local smoke"}})
    outputs = result.get("outputs") or {}
    return record("退款一笔：REFUND 同步成功", result.get("executeStatus") == "SUCCESS" and outputs.get("refundStatus") == "SUCCEEDED"
                  and outputs.get("channelRefundRef") == f"R{refund_id}",
                  f"refundId={refund_id} execute={result.get('executeStatus')} refundStatus={outputs.get('refundStatus')} "
                  f"channelStatus={outputs.get('channelStatus')}")


def main():
    cs.install_harness()
    import verify_card  # 卡直付 harness，从 install_harness 加入的路径导入
    before = len(journal())
    receipt = {"sandbox": sandbox_cases(verify_card)}
    attempt_id = real_attempt(sys.argv[1] if len(sys.argv) > 1 else None)
    if not attempt_id:
        record("真实支付成功的 Attempt", False, "mor_payment_v2e2e 没有走 PAYERMAX_MOCK 的 SUCCEEDED Attempt：先跑 client_api_flow.py")
    else:
        token = account_token()
        receipt["attemptId"] = str(attempt_id)
        receipt["query"] = query_back(attempt_id, token)
        receipt["refund"] = refund_one(attempt_id, token)
    seen = journal()[before:]
    valid = [t for t in seen if t["operation"] in ("orderQuery", "refund")]
    record("合成渠道流水：查询与退款请求签名、外层、时效、金额有效", valid and all(
        t["signatureValid"] and t["envelopeValid"] and t["requestTimeValid"] and t["amountValid"] for t in valid),
        f"requests={[(t['operation'], t['outcome'], t['code']) for t in seen]}")
    real = [t for t in journal() if t["operation"] == "orderAndPay" and t.get("direct") and str(t.get("outcome")) not in ("2", "3", "4")]
    record("合成渠道流水：真实支付的卡直付请求卡字段格式有效", real and all(t["cardValid"] and t["signatureValid"] for t in real),
           f"directPayments={len(real)}")
    passed = sum(1 for r in RESULTS if r[1] == "PASS")
    receipt.update(passed=passed, total=len(RESULTS))
    (cs.RUNTIME / "card-smoke.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=1) + "\n")
    print(f"\n合计 {passed}/{len(RESULTS)} 通过")
    raise SystemExit(0 if passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
