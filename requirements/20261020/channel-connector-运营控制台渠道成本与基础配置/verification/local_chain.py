"""阶段 5 本机全链路（AC-B05）：以 local_acceptance.py 经运营控制台登记的渠道 / 账号 / MID / 能力替代种子 SQL 与 channel_setup.py。

链路：channel-connector 候选查询（Payment 选路调用的同一私网合同 POST /channel-connector/v1/mid-candidates）
     → channel-connector 渠道执行（Payment 执行调用的同一私网合同 POST /channel-connector/v1/operations，冻结 Channel / 账号 / MID）
     → Platform 标准 API（integrationCode = channel_code = PAYERMAX_MOCK，按 CHANNEL_EXECUTION Reveal 控制台写入的账号 Token）
     → 本机合成 PayerMax（127.0.0.1:18190，流水 local-environment/e2e/runtime/payermax/payermax-journal.jsonl）。
与 20261017 local-e2e 的差异：
  - 不执行 sql/50-channel-connector-payermax-mock.sql、不运行 channel_setup.py；渠道 ID / 账号 ID / MID 读最新的 channel-acceptance-*.json，
    不是种子固定的 9500000000000001 / 101 / 201（card_smoke.py 等既有脚本硬编码这些 ID：最小改法是把 channel_setup.CHANNEL_ID /
    ACCOUNT_ID / MID_ID 改为读运行记录，或像本脚本一样直接传入，原脚本不改）。
  - 本机没有 mor_payment_v2e2e / mor_acquiring_v2e2e / mor_card_v2e2e（库不存在，创建与建表不在本需求 SQL 授权内），
    因此不经 Payment 服务进程，直接调用 Payment 所调的两个 channel-connector 合同；请求体按 Payment PaymentChannelClientImpl / PayInput 组装。
  - PAY 需要 card-service 读卡（Platform PAYMENT_CARD 初始化器），card-service 未运行，只记录是否到达 Platform；以 REFUND 证明执行到达合成 PayerMax。
用法：python3 -I local_chain.py；结果追加写入该运行记录的 chain 字段。不打印 Token、密钥或口令。
"""
import json
import random
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import local_acceptance as acc  # noqa: E402
from local_acceptance import e2e_http  # noqa: E402

PLATFORM_CODES = {"STANDARD_API_NOT_FOUND", "INTEGRATION_IMPLEMENTATION_NOT_FOUND", "INTEGRATION_CONFIG_INVALID",
                  "INTEGRATION_INITIALIZATION_FAILED", "INTEGRATION_BUDGET_EXHAUSTED", "VALIDATION_ERROR"}
JOURNAL = acc.RUNTIME / "payermax/payermax-journal.jsonl"
results = []


def check(name, ok, evidence):
    results.append((name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] AC-B05 {name} | {json.dumps(evidence, ensure_ascii=False, default=str)[:500]}", flush=True)


def journal():
    return [json.loads(line) for line in JOURNAL.read_text().splitlines()] if JOURNAL.exists() else []


def execute(rec, operation, purpose, amount, payload):
    body = {"entityNo": acc.ENTITY_NO, "channelId": rec["channelIds"]["main"], "channelAccountId": rec["accountIds"]["main"],
            "midId": rec["midIds"]["main"], "operation": operation, "purpose": purpose,
            "criteria": {"paymentMethodCode": "CARD", "schemeCode": None, "countryCode": "US", "currency": "USD", "mccCode": "5999"},
            "amount": amount, "input": payload}
    status, text = e2e_http.private_call("channel-connector", "POST", "/channel-connector/v1/operations", body=body, timeout=90)
    return status, (json.loads(text) if text.startswith("{") else {"raw": text[:300]})


def main():
    path, rec = acc.latest_record()
    mid = rec["midIds"]["main"]
    status, items = acc.candidates("USD")
    mine = [c for c in items if str(c.get("midId")) == mid]
    check("候选查询（Payment 选路合同）返回控制台登记的 MID、账号与 PAY", status == 200 and mine
          and mine[0].get("channelAccountId") == rec["accountIds"]["main"] and "PAY" in (mine[0].get("supportedOperations") or []),
          {"status": status, "candidates": [(c.get("midId"), c.get("channelAccountId"), c.get("supportedOperations")) for c in items]})
    seed = acc.cc_sql("SELECT COUNT(*) FROM t_payment_channel WHERE channel_id=9500000000000001")[0][0]
    check("未使用种子：库中不存在种子渠道 9500000000000001", seed == "0", {"seedRows": seed})

    if acc.TEST:
        endpoints = e2e_http.sql("platform", f"SELECT endpoints FROM t_integration WHERE integration_code='{acc.MAIN_CODE}'")
        external = bool(endpoints) and "127.0.0.1" not in endpoints[0][0] and "mock" not in endpoints[0][0].lower()
        print(f"[环境限制] mor-test 接入方 {acc.MAIN_CODE} 出站地址为外部 PayerMax UAT（{'外部' if external else '内部'}）；"
              "账号凭据为合成值，不向第三方发送渠道执行请求，执行段不运行（按主会话：不改接入方配置）")
        rec["chain"] = {"passed": sum(1 for r in results if r[1]), "total": len(results), "execution": "SKIPPED_EXTERNAL_ENDPOINT"}
        acc.record, acc.RECORD_PATH = rec, path
        acc.save()
        print(f"\n全链路（候选段）：{rec['chain']['passed']} / {rec['chain']['total']} 通过")
        return 0 if rec["chain"]["passed"] == rec["chain"]["total"] else 1

    before = len(journal())
    refund_id = f"RFD{random.randrange(10**11):011d}2"
    refund_input = {"refund": {"refundId": refund_id, "paymentAttemptId": str(random.randrange(10**15, 10**16)),
                               "amount": {"value": "5.00", "currency": "USD"}, "reason": "Stage 5 channel console chain"}}
    status, refund = execute(rec, "REFUND", "ORIGINAL_TRANSACTION", "5.00", refund_input)
    time.sleep(1)
    seen = journal()[before:]
    evidence = {"status": status, "outcome": refund.get("outcome"), "channelStatus": refund.get("channelStatus"),
                "failureCode": refund.get("failureCode"), "rawOutputKeys": refund.get("rawOutputKeys"), "mockRequests": len(seen)}
    if acc.MAIN_CODE == "PAYERMAX_MOCK":
        check("渠道执行 REFUND：channel-connector → Platform 标准 API → 合成 PayerMax 同步成功",
              status == 200 and refund.get("outcome") == "SUCCESS", evidence)
        check("合成 PayerMax 收到退款请求且签名有效（Platform 用控制台 Protect 的账号凭据签名）",
              any(t.get("operation") == "refund" and t.get("signatureValid") for t in seen),
              [(t.get("operation"), t.get("outcome"), t.get("signatureValid"), t.get("envelopeValid")) for t in seen])
    else:
        # channel-connector 只在 Platform 以 Problem code 拒绝时返回这些 NOT_SENT 码；本地坐标或 Token 不可用另有本地码，Platform 不可达为 UNKNOWN。
        check(f"渠道执行 REFUND 到达 Platform：冻结坐标与账号 Token 校验通过，Platform 以 {refund.get('failureCode')} 应答"
              f"（接入方 {acc.MAIN_CODE} 在本机 Platform 无 REFUND 配置）",
              status == 200 and refund.get("failureCode") in PLATFORM_CODES, evidence)
        direct_status, direct = e2e_http.private_call("platform", "POST", "/standard-apis/v1/REFUND/execute",
                                                      body={"integrationCode": acc.MAIN_CODE, "input": refund_input})
        direct_code = (json.loads(direct) if direct.startswith("{") else {}).get("code")
        check("直连 Platform 同一标准 API 得到相同 Problem code（交叉核对）", direct_code == refund.get("failureCode"),
              {"directStatus": direct_status, "directCode": direct_code})

    status, pay = execute(rec, "PAY", "NEW_PAYMENT", "10.00", {
        "card": {"token": "tok_stage5_unavailable"},
        "payment": {"attemptId": str(random.randrange(10**15, 10**16)), "amount": {"value": "10.00", "currency": "USD"},
                    "country": "US", "returnUrl": "https://127.0.0.1:18446/return", "payerReference": "stage5-chain"}})
    check("渠道执行 PAY 到达 Platform（Platform 应答 SUCCESS / FAIL 或 Platform Problem code；card-service 未运行时读卡失败属预期）",
          status == 200 and (pay.get("outcome") in ("SUCCESS", "FAIL") or pay.get("failureCode") in PLATFORM_CODES),
          {"status": status, "outcome": pay.get("outcome"), "failureCode": pay.get("failureCode"),
           "failureMessage": (pay.get("failureMessage") or "")[:160]})
    rec["chain"] = {"passed": sum(1 for r in results if r[1]), "total": len(results),
                    "refund": {"outcome": refund.get("outcome"), "channelStatus": refund.get("channelStatus")},
                    "pay": {"outcome": pay.get("outcome"), "failureCode": pay.get("failureCode")}}
    acc.record, acc.RECORD_PATH = rec, path
    acc.save()
    print(f"\n全链路：{rec['chain']['passed']} / {rec['chain']['total']} 通过；记录 {path}")
    return 0 if rec["chain"]["passed"] == rec["chain"]["total"] else 1


if __name__ == "__main__":
    sys.exit(main())
