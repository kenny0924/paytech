"""对账控制台详情页与渠道流水 阶段 5：3 个新接口经私有栈运营入口（Nginx → Gateway 权限点 → BFF → reconciliation）的接口核对。

用法：python3 -I api_checks.py <run-*.json>（seed.py 产出）
"""
import json
import sys
from datetime import date, timedelta
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402

R = "/finance/reconciliation/v1"
LINE_FIELDS = {"lineId", "statementId", "channelBatchRef", "revision", "statementStatus", "settlementDate", "clearingAccountId",
               "clearingAccountCode", "currency", "lineNo", "lineType", "direction", "amount", "feeAmount", "referenceType",
               "externalTransactionId", "transactionDate", "platformTxnId", "matchMethod", "matchedTime", "actualFee", "resolutionId",
               "matchStatus"}
HEAD_FIELDS = {"bankStatementId", "cashAccountId", "statementRef", "periodStart", "periodEnd", "currency", "openingBalance",
               "closingBalance", "declaredLineCount", "importedLineCount", "duplicateLineCount", "fileName", "fileContentHash",
               "rejectReason", "status", "makerId", "createTime", "updateTime"}


def main():
    record = json.loads(Path(sys.argv[1]).read_text())
    people = record["people"]
    token = c.login(people["MAKER"]["email"], people["MAKER"]["password"])
    st, refs = record["statements"], record["refs"]
    ca1, ca2 = record["ca1"]["clearingAccountId"], record["ca2"]["clearingAccountId"]
    get = lambda path: c.raw(token, "GET", R + path)  # noqa: E731
    lines = lambda q: get("/channel-statement-lines?" + q)  # noqa: E731

    s, body = lines("page=1&size=100")
    items = body.get("items", [])
    c.check("API-1", f"默认查询 200，共 {body.get('total')} 行（A3+B1+C3+E2=9，排除作废 D 的 2 行）",
            s == 200 and body.get("total") == 9 and all(i["statementId"] != st["D"] for i in items), body)
    c.check("API-1", f"返回字段齐全（缺 {sorted(LINE_FIELDS - set(items[0]) if items else LINE_FIELDS)}）", bool(items) and LINE_FIELDS <= set(items[0]))
    keys = [(i["transactionDate"] or "", int(i["lineId"])) for i in items]
    c.check("API-1", "排序：交易日倒序、同日 lineId 倒序", keys == sorted(keys, reverse=True), keys)
    a1 = next((i for i in items if i["statementId"] == st["A"] and i["lineNo"] == 1), {})
    c.check("API-1", f"行带账单批次号 / 状态 / 清算账户编码 / 平台流水（{a1.get('channelBatchRef')} {a1.get('statementStatus')} "
            f"{a1.get('clearingAccountCode')} {a1.get('platformTxnId')} 实际费 {a1.get('actualFee')}）",
            a1.get("channelBatchRef", "").startswith("A-") and a1.get("statementStatus") == "CASH_CONFIRMED"
            and a1.get("clearingAccountCode") == record["ca1"]["clearingAccountCode"] and a1.get("platformTxnId")
            and a1.get("matchStatus") == "MATCHED", a1)

    s, body = lines(f"clearingAccountId={ca2}")
    c.check("API-2", f"按清算账户 CA2：{body.get('total')} 行（B 1 行；D 作废排除）",
            s == 200 and body.get("total") == 1 and body["items"][0]["statementId"] == st["B"], body)
    s, body = lines("statementStatus=VOIDED")
    c.check("API-2", f"显式 statementStatus=VOIDED：{body.get('total')} 行（D 的 2 行）",
            s == 200 and body.get("total") == 2 and all(i["statementId"] == st["D"] for i in body["items"]), body)
    s, body = lines("matchStatus=UNMATCHED")
    c.check("API-2", f"matchStatus=UNMATCHED：{body.get('total')} 行，全部 UNMATCHED",
            s == 200 and body.get("total", 0) >= 1 and all(i["matchStatus"] == "UNMATCHED" for i in body["items"]), body)
    s, body = lines("lineType=ADJUSTMENT")
    c.check("API-2", f"lineType=ADJUSTMENT：{body.get('total')} 行（A、C、E 各 1）",
            s == 200 and body.get("total") == 3 and all(i["lineType"] == "ADJUSTMENT" for i in body["items"]), body)
    s, body = lines(f"externalTransactionId={refs['PAY-C']}")
    c.check("API-2", f"外部交易号精确：{body.get('total')} 行（C 行 1）",
            s == 200 and body.get("total") == 1 and body["items"][0]["statementId"] == st["C"] and body["items"][0]["lineNo"] == 1, body)
    s, body = lines(f"externalTransactionId={refs['PAY-C'][:-2]}")
    c.check("API-2", f"外部交易号前缀不命中（精确匹配）：{body.get('total')} 行", s == 200 and body.get("total") == 0, body)
    s, body = lines(f"referenceType=NI_REFUND_REF&externalTransactionId={refs['RFD-A']}")
    c.check("API-2", f"交易号类型 + 交易号：{body.get('total')} 行", s == 200 and body.get("total") == 1, body)
    s, body = lines("referenceType=NI_REFUND_REF")
    c.check("API-2", f"交易号类型单独使用：{body.get('total')} 行", s == 200 and body.get("total") == 1, body)
    d2, d1 = (date.today() - timedelta(days=2)).isoformat(), (date.today() - timedelta(days=1)).isoformat()
    s, body = lines(f"transactionDateFrom={d2}&transactionDateTo={d1}")
    c.check("API-2", f"交易日 {d2}..{d1}（两端含）：{body.get('total')} 行（C3 + E2）",
            s == 200 and body.get("total") == 5 and all(d2 <= i["transactionDate"] <= d1 for i in body["items"]), body)
    s, body = lines("page=2&size=10")
    c.check("API-3", f"page=2&size=10：{len(body.get('items', []))} 条（共 9）、回执 page {body.get('page')} size {body.get('size')}",
            s == 200 and body.get("items") == [] and body.get("page") == 2 and body.get("size") == 10, body)
    s, body = lines("page=1&size=10")
    c.check("API-3", f"size=10 返回 {len(body.get('items', []))} 条", s == 200 and len(body.get("items", [])) == 9)
    for size in (200, 30):
        s, body = lines(f"size={size}")
        c.check("API-3", f"size={size} → 400（{s} {body.get('code')}）", s == 400, body)
    s, body = lines("foo=1")
    c.check("API-3", f"未登记查询参数 → 400（{s} {body.get('code')}）", s == 400, body)
    s, body = c.raw(None, "GET", R + "/channel-statement-lines")
    c.check("API-3", f"未登录 → 401（{s}）", s == 401, body)

    bsid = record["bankStatementId"]
    s, body = get(f"/bank-statements/{bsid}")
    c.check("API-4", f"银行对账单头 200（{body.get('status')} 期初 {body.get('openingBalance')} 期末 {body.get('closingBalance')} 行 {body.get('importedLineCount')}）",
            s == 200 and HEAD_FIELDS <= set(body) and body.get("importedLineCount") == 3 and body.get("status") == "IMPORTED", body)
    s, body = get("/bank-statements/999999999999")
    c.check("API-4", f"银行对账单不存在 → 404 BANK_STATEMENT_NOT_FOUND（{s} {body.get('code')} {body.get('detail')}）",
            s == 404 and body.get("code") == "BANK_STATEMENT_NOT_FOUND", body)

    l1, l2, l3 = (record["bankLines"][k] for k in ("1", "2", "3"))
    s, body = get(f"/bank-statement-lines/{l1}")
    m = (body.get("matches") or [{}])[0]
    c.check("API-5", f"流水 1：{body.get('line', {}).get('matchStatus')}，匹配 {m.get('match', {}).get('status')}，预期流水 "
            f"{m.get('expectedMovement', {}).get('movementType')} sourceId=账单 A",
            s == 200 and set(body) >= {"line", "statement", "matches", "openExceptions"} and body["line"]["matchStatus"] == "MATCHED"
            and m.get("match", {}).get("status") == "CONFIRMED" and m.get("expectedMovement", {}).get("movementType") == "CHANNEL_SETTLEMENT"
            and str(m["expectedMovement"]["sourceId"]) == str(record["statements"]["A"]) and body["statement"]["bankStatementId"] == bsid, body)
    s, body = get(f"/bank-statement-lines/{l2}")
    c.check("API-5", f"流水 2：建议 {[x['match']['status'] for x in body.get('matches', [])]}",
            s == 200 and [x["match"]["status"] for x in body["matches"]] == ["PROPOSED"], body)
    s, body = get(f"/bank-statement-lines/{l3}")
    c.check("API-5", f"流水 3：无匹配（{len(body.get('matches', []))}）、openExceptions={len(body.get('openExceptions', []))}",
            s == 200 and body.get("matches") == [] and isinstance(body.get("openExceptions"), list), body)
    s, body = get("/bank-statement-lines/999999999999")
    c.check("API-5", f"银行流水不存在 → 404 BANK_STATEMENT_LINE_NOT_FOUND（{s} {body.get('code')}）",
            s == 404 and body.get("code") == "BANK_STATEMENT_LINE_NOT_FOUND", body)
    s, body = get("/bank-statement-lines/abc")
    c.check("API-5", f"非数字主键 → 400（{s} {body.get('code')}）", s == 400, body)
    s, body = get("/channel-statements/999999999999")
    c.check("API-6", f"既有渠道账单详情不存在仍为 400 BUSINESS_CHECK_FAILED（{s} {body.get('code')} {body.get('detail')}）",
            s == 400 and body.get("code") == "BUSINESS_CHECK_FAILED" and "不存在" in (body.get("detail") or ""), body)
    passed = sum(1 for r in c.results if r[2])
    print(f"\n接口核对 {passed}/{len(c.results)} 通过")
    out = c.STATE / "api-checks.json"
    out.write_text(json.dumps(c.results, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
