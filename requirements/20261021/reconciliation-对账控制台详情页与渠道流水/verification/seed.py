"""对账控制台详情页与渠道流水 阶段 5：本机种子数据（经私有栈运营入口真实接口 + 正式发布器发布 BillingFactRecorded）。

产出（写 scratchpad/recon6/run-<ts>.json 供 browser_detail.py / api_checks.py / cleanup.py 使用）：
  人员：MAKER（对账读 + 操作 + 财务读）、CHECKER（对账读 + 审批 + 财务读）
  银行账户 1 个（ACTIVE）；清算账户 CA1、CA2（两者都结算到该银行账户；不挂 MID，不建渠道）
  平台流水：CA1 PAY-A / RFD-A / PAY-C / PAY-E / PAY-X（无账单）；CA2 PAY-B / PAY-D
  渠道账单：A（CA1，S1 形态，调整行经 Maker/Checker 归类 → RECONCILED，净额 67.55）
           B（CA2，60.00 一行 → 自动 RECONCILED）
           C（CA1，MATCHED + UNMATCHED + 调整行异常 → MATCHING，留给浏览器做异常处置）
           D（CA2，含调整行 → MATCHING → 经接口作废 → VOIDED）
           E（CA1，含调整行 → MATCHING，留给浏览器作废）
  银行对账单：3 行（67.55 → A 建议，接口确认；60.00 → B 建议，留给浏览器确认；12.34 → 无匹配）
用法：python3 -I seed.py
"""
import json
import sys
import time
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402

RUN = uuid.uuid4().hex[:6].upper()
TODAY = date.today()
D = lambda n: (TODAY - timedelta(days=n)).isoformat()  # noqa: E731
RECORD = c.STATE / f"run-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
record = {"run": RUN, "startedAt": datetime.now(timezone.utc).isoformat()}
R = "/finance/reconciliation/v1"


def save():
    RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=2))


def must(ok, label, evidence):
    if not c.check("种子", label, ok, evidence):
        save()
        raise SystemExit(1)


def cash_account(maker, checker):
    number = "AE07" + str(uuid.uuid4().int)[:15] + "4321"
    status, created = c.raw(maker, "POST", R + "/cash-accounts", {
        "entityType": "OPERATING_ENTITY", "entityId": "AE001", "accountPurpose": "CHANNEL_SETTLEMENT", "bankCode": "ENBD",
        "accountName": f"AE001 settlement {RUN}", "currency": "USD", "accountNumber": number})
    must(status == 201, f"新建银行账户（{status}）", created)
    status, body = c.raw(checker, "POST", f"{R}/cash-accounts/{created['cashAccountId']}/approval",
                         {"decision": "APPROVED", "approvedContentHash": created["contentHash"]})
    must(status == 200 and body.get("status") == "ACTIVE", f"Checker 批准银行账户（{status}）", body)
    return created["cashAccountId"]


def clearing_account(maker, cash_id, suffix):
    body = {"clearingAccountCode": f"PMX-AE001-USD-{RUN}-{suffix}", "channelCode": "PAYERMAX_MOCK", "entityNo": "AE001", "currency": "USD",
            "settlementCashAccountId": cash_id, "unmatchedWaitDays": 3, "costToleranceAmount": "0.10", "cashGraceDays": 2}
    status, ca = c.raw(maker, "POST", R + "/clearing-accounts", body)
    must(status == 201, f"新建清算账户 {body['clearingAccountCode']}（{status}）", ca)
    return ca


def csv_statement(ca_code, batch, lines):
    gross = sum((1 if l[2] == "CREDIT" else -1) * float(l[3]) for l in lines)
    fee = sum(float(l[4]) for l in lines)
    head = ("#HEADER,clearing_account_code,channel_batch_ref,settlement_date,currency,gross_amount,fee_amount,net_amount,cash_direction\n"
            f"#HEADER,{ca_code},{batch},{TODAY.isoformat()},USD,{gross:.2f},{fee:.2f},{gross - fee:.2f},CREDIT\n"
            "line_no,line_type,direction,amount,fee_amount,reference_type,external_transaction_id,transaction_date,terminal_id,remark\n")
    return head + "".join(",".join(str(x) for x in l) + "\n" for l in lines)


def import_statement(maker, csv_text, name):
    header, lines = c.parse_channel_csv(csv_text)
    status, started = c.raw(maker, "POST", R + "/channel-statements", {
        "clearingAccountCode": header["clearing_account_code"], "channelBatchRef": header["channel_batch_ref"],
        "settlementDate": header["settlement_date"], "currency": header["currency"], "grossAmount": header["gross_amount"],
        "feeAmount": header["fee_amount"], "netAmount": header["net_amount"], "cashDirection": header["cash_direction"],
        "fileName": name, "fileContentHash": c.sha256_text(csv_text), "declaredLineCount": len(lines)})
    must(status == 201, f"开始导入账单 {header['channel_batch_ref']}（{status}）", started)
    body = [{"lineNo": int(l["line_no"]), "lineType": l["line_type"], "direction": l["direction"], "amount": l["amount"],
             "feeAmount": l["fee_amount"] or "0", "referenceType": l["reference_type"] or None,
             "externalTransactionId": l["external_transaction_id"] or None, "transactionDate": l["transaction_date"],
             **({"rawAttributes": {"remark": l["remark"]}} if l.get("remark") else {})} for l in lines]
    status, appended = c.raw(maker, "POST", f"{R}/channel-statements/{started['statementId']}/lines", {"lines": body})
    must(status == 200, f"追加账单行（{status}）", appended)
    status, done = c.raw(maker, "POST", f"{R}/channel-statements/{started['statementId']}/completion")
    must(status == 200 and done.get("status") == "MATCHING", f"完成导入 {header['channel_batch_ref']} → MATCHING（{done.get('status')}）", done)
    return started["statementId"]


def statement_status(maker, sid):
    return c.raw(maker, "GET", f"{R}/channel-statements/{sid}")[1].get("statement", {}).get("status")


def wait_status(maker, sid, expected, timeout=60):
    return c.wait_until(lambda: statement_status(maker, sid) == expected, timeout)


def exceptions_of(maker, sid):
    status, page = c.raw(maker, "GET", f"{R}/recon-exceptions?statementId={sid}&page=1&size=50")
    return page.get("items", [])


def main():
    admin = c.admin_token()
    c.bind_probe()
    maker, maker_id, *_ = c.synthetic_person(admin, "MAKER", [c.PERM["RECON_READ"], c.PERM["RECON_OPERATE"], c.PERM["ACCOUNTING_READ"]], RUN, record)
    checker, checker_id, *_ = c.synthetic_person(admin, "CHECKER", [c.PERM["RECON_READ"], c.PERM["RECON_APPROVE"], c.PERM["ACCOUNTING_READ"]], RUN, record)
    save()
    cash_id = cash_account(maker, checker)
    ca1, ca2 = clearing_account(maker, cash_id, "1"), clearing_account(maker, cash_id, "2")
    record.update(cashAccountId=cash_id, ca1=ca1, ca2=ca2)
    save()

    refs = {k: f"NI-{k}-{RUN}" for k in ("PAY-A", "RFD-A", "PAY-C", "PAY-E", "PAY-X", "PAY-B", "PAY-D")}
    facts = [c.fact("PAYMENT", ca1["clearingAccountId"], "100.00", "NI_ORDER_REF", refs["PAY-A"], channel_cost="0.45"),
             c.fact("REFUND", ca1["clearingAccountId"], "30.00", "NI_REFUND_REF", refs["RFD-A"]),
             c.fact("PAYMENT", ca1["clearingAccountId"], "40.00", "NI_ORDER_REF", refs["PAY-C"], channel_cost="0.20"),
             c.fact("PAYMENT", ca1["clearingAccountId"], "25.00", "NI_ORDER_REF", refs["PAY-E"]),
             c.fact("PAYMENT", ca1["clearingAccountId"], "10.00", "NI_ORDER_REF", refs["PAY-X"]),
             c.fact("PAYMENT", ca2["clearingAccountId"], "60.00", "NI_ORDER_REF", refs["PAY-B"]),
             c.fact("PAYMENT", ca2["clearingAccountId"], "15.00", "NI_ORDER_REF", refs["PAY-D"])]
    c.publish_facts(facts)
    count = c.wait_until(lambda: (lambda n: n if n == 7 else None)(int(c.recon_sql("SELECT COUNT(*) FROM t_platform_txn")[0][0])), 90)
    must(count == 7, f"7 条平台流水已登记（{count}）", count)
    record["refs"] = refs
    save()

    a = import_statement(maker, csv_statement(ca1["clearingAccountCode"], f"A-{RUN}", [
        (1, "PAYMENT", "CREDIT", "100.00", "0.45", "NI_ORDER_REF", refs["PAY-A"], D(4), "T01", ""),
        (2, "REFUND", "DEBIT", "30.00", "0", "NI_REFUND_REF", refs["RFD-A"], D(4), "T01", "partial"),
        (3, "ADJUSTMENT", "DEBIT", "2.00", "0", "", "", D(4), "", "monthly fee")]), "A.csv")
    b = import_statement(maker, csv_statement(ca2["clearingAccountCode"], f"B-{RUN}", [
        (1, "PAYMENT", "CREDIT", "60.00", "0", "NI_ORDER_REF", refs["PAY-B"], D(3), "T02", "")]), "B.csv")
    cst = import_statement(maker, csv_statement(ca1["clearingAccountCode"], f"C-{RUN}", [
        (1, "PAYMENT", "CREDIT", "40.00", "0.20", "NI_ORDER_REF", refs["PAY-C"], D(2), "T01", ""),
        (2, "PAYMENT", "CREDIT", "12.00", "0", "NI_ORDER_REF", f"NI-NOPE-{RUN}", D(2), "T01", ""),
        (3, "ADJUSTMENT", "DEBIT", "1.50", "0", "", "", D(2), "", "reserve")]), "C.csv")
    d = import_statement(maker, csv_statement(ca2["clearingAccountCode"], f"D-{RUN}", [
        (1, "PAYMENT", "CREDIT", "15.00", "0", "NI_ORDER_REF", refs["PAY-D"], D(1), "T02", ""),
        (2, "ADJUSTMENT", "DEBIT", "0.50", "0", "", "", D(1), "", "fee")]), "D.csv")
    e = import_statement(maker, csv_statement(ca1["clearingAccountCode"], f"E-{RUN}", [
        (1, "PAYMENT", "CREDIT", "25.00", "0", "NI_ORDER_REF", refs["PAY-E"], D(1), "T01", ""),
        (2, "ADJUSTMENT", "DEBIT", "0.30", "0", "", "", D(1), "", "fee")]), "E.csv")
    record["statements"] = {"A": a, "B": b, "C": cst, "D": d, "E": e}
    save()

    # A：调整行异常经 Maker 提交、Checker 批准归类 → RECONCILED
    found = c.wait_until(lambda: [x for x in exceptions_of(maker, a) if x["exceptionType"] == "UNEXPLAINED_ADJUSTMENT"], 60)
    must(bool(found), "A 调整行开 UNEXPLAINED_ADJUSTMENT", found)
    status, sub = c.raw(maker, "POST", f"{R}/recon-exceptions/{found[0]['exceptionId']}/resolutions",
                        {"resolutionType": "CLASSIFY", "classificationCode": "CHANNEL_FEE_OTHER", "amount": "-2.00", "note": "月费归类"})
    must(status == 201, f"A 提交归类（{status}）", sub)
    status, ok = c.raw(checker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval",
                       {"decision": "APPROVED", "approvedContentHash": sub["contentHash"]})
    must(status == 200 and ok.get("status") == "APPLIED", f"A 批准归类（{status}）", ok)
    must(wait_status(maker, a, "RECONCILED"), "A → RECONCILED", statement_status(maker, a))
    must(wait_status(maker, b, "RECONCILED"), "B → RECONCILED", statement_status(maker, b))
    record["exceptionA"] = found[0]["exceptionId"]

    # C / E 等异常开立，D 作废
    c.wait_until(lambda: exceptions_of(maker, cst), 60)
    c.wait_until(lambda: exceptions_of(maker, d), 60)
    status, voided = c.raw(maker, "POST", f"{R}/channel-statements/{d}/void")
    must(status == 200 and statement_status(maker, d) == "VOIDED", f"D 作废 → VOIDED（{status}）", voided)
    record["exceptionsC"] = [x["exceptionId"] for x in exceptions_of(maker, cst)]
    must(statement_status(maker, cst) == "MATCHING" and statement_status(maker, e) == "MATCHING", "C、E 仍为 MATCHING", None)
    save()

    # 银行对账单：3 行
    lines = [{"lineNo": 1, "bankTransactionRef": f"ENBD-1-{RUN}", "valueDate": TODAY.isoformat(), "direction": "CREDIT", "amount": "67.55",
              "counterpartyName": "PAYERMAX MOCK", "referenceText": f"SETTLEMENT A-{RUN}"},
             {"lineNo": 2, "bankTransactionRef": f"ENBD-2-{RUN}", "valueDate": TODAY.isoformat(), "direction": "CREDIT", "amount": "60.00",
              "counterpartyName": "PAYERMAX MOCK", "referenceText": f"SETTLEMENT B-{RUN}"},
             {"lineNo": 3, "bankTransactionRef": f"ENBD-3-{RUN}", "valueDate": TODAY.isoformat(), "direction": "CREDIT", "amount": "12.34",
              "counterpartyName": "UNKNOWN PAYER", "referenceText": "UNIDENTIFIED"}]
    status, started = c.raw(maker, "POST", R + "/bank-statements", {
        "cashAccountId": cash_id, "bankAccountLast4": "4321", "statementRef": f"ENBD-{RUN}", "periodStart": TODAY.isoformat(),
        "periodEnd": TODAY.isoformat(), "currency": "USD", "openingBalance": "1000.00", "closingBalance": "1139.89",
        "fileName": "bank.csv", "fileContentHash": c.sha256_text(RUN + "bank"), "declaredLineCount": 3})
    must(status == 201, f"开始导入银行对账单（{status}）", started)
    bsid = started["bankStatementId"]
    status, _ = c.raw(maker, "POST", f"{R}/bank-statements/{bsid}/lines", {"lines": lines})
    status, done = c.raw(maker, "POST", f"{R}/bank-statements/{bsid}/completion")
    must(status == 200 and done.get("status") == "IMPORTED", f"银行对账单 IMPORTED（{done.get('status')}）", done)
    status, page = c.raw(maker, "GET", f"{R}/bank-statements/{bsid}/lines?page=1&size=10")
    by_no = {i["lineNo"]: i for i in page["items"]}
    record["bankStatementId"] = bsid
    record["bankLines"] = {str(k): v["bankStatementLineId"] for k, v in by_no.items()}
    save()

    def proposals(line_id):
        s, m = c.raw(maker, "GET", f"{R}/bank-matches?bankStatementLineId={line_id}")
        return [x for x in m.get("items", []) if x.get("status") == "PROPOSED"]
    p1 = c.wait_until(lambda: proposals(by_no[1]["bankStatementLineId"]), 60)
    p2 = c.wait_until(lambda: proposals(by_no[2]["bankStatementLineId"]), 60)
    must(bool(p1) and bool(p2), f"行 1、行 2 出一对一建议（{len(p1 or [])} / {len(p2 or [])}）", {"p1": p1, "p2": p2})
    status, conf = c.raw(maker, "POST", f"{R}/bank-matches/{p1[0]['bankMatchId']}/confirmation")
    must(status == 200, f"接口确认行 1 建议（{status}）", conf)
    record["bankMatchConfirmed"] = p1[0]["bankMatchId"]
    record["bankMatchProposed"] = p2[0]["bankMatchId"]
    save()
    passed = sum(1 for r in c.results if r[2])
    print(f"\n种子完成 {passed}/{len(c.results)}；记录 {RECORD}")


if __name__ == "__main__":
    main()
