"""对账切片 1 阶段 5：本机全链路 S1 接口验收（Operations 入口 → Gateway 权限点 → BFF → reconciliation；MQ 夹具与观察）。

准备：stack.sh start（私有栈）；临时 vhost /mor-recon5 已建；mor_reconciliation_v2e2e 为空。
人员：IAM 新建 MAKER（对账读 + 操作 + 财务读）与 CHECKER（对账读 + 审批 + 财务读）两名合成人员；渠道、技术账号、MID 由运营管理员经
channel-admin 接口建（AE001 主 MID 激活并带 USD 能力；AE002 账号 + MID 作 OE 不一致负例）。
运行记录（ID、合成人员口令）写 scratchpad/recon5/run-<ts>.json，供浏览器验收与清理使用。
用法：python3 -I s1_flow.py
"""
import json
import sys
import time
import uuid
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recon5_common as c  # noqa: E402

RUN = uuid.uuid4().hex[:6].upper()
TODAY = date.today().isoformat()
RECORD = c.STATE / f"run-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
record = {"run": RUN, "startedAt": datetime.now(timezone.utc).isoformat()}
R = "/finance/reconciliation/v1"
CH = "/channel-admin/v1"


def save():
    RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=2))


def remember(kind, value):
    record.setdefault(kind, []).append(str(value))
    save()


def channel_setup(admin):
    """合成渠道 PAYERMAX_MOCK（Platform 已有同编码接入方）、AE001 已配置并激活的技术账号、ACTIVE MID + USD 能力；AE002 DRAFT 账号与 MID。"""
    keys = json.loads((c.LOCAL_ENV / "e2e/runtime/payermax/payermax-mock-keys.json").read_text())
    status, ch = c.raw(admin, "POST", CH + "/channels", {"channelCode": "PAYERMAX_MOCK", "channelName": f"对账验收合成渠道 {RUN}"})
    c.check("准备", f"新建渠道 PAYERMAX_MOCK（{status}）", status == 201, ch)
    remember("channels", ch["channelId"])
    status, body = c.raw(admin, "POST", f"{CH}/channels/{ch['channelId']}/status", {"targetStatus": "ACTIVE", "reason": "对账验收"})
    c.check("准备", f"渠道激活（{status}）", status == 200, body)
    ids = {"channelId": ch["channelId"]}
    for entity in ("AE001", "AE002"):
        status, acct = c.raw(admin, "POST", CH + "/channel-accounts", {"channelId": ch["channelId"], "entityNo": entity,
                                                                      "accountName": f"对账验收 {entity} {RUN}"})
        c.check("准备", f"新建技术账号 {entity}（{status}）", status == 201, acct)
        ids[f"account{entity}"] = acct["channelAccountId"]
    configuration = {"parameters": {k: keys[k] for k in ("appId", "merchantNo", "keyVersion", "payermaxPublicKey")},
                     "credentials": {"merchantPrivateKey": keys["merchantPrivateKey"]}}
    status, body = c.raw(admin, "PUT", f"{CH}/channel-accounts/{ids['accountAE001']}/configuration", configuration)
    c.check("准备", f"AE001 账号配置（Vault Protect，{status}）", status == 200, {k: body.get(k) for k in ("status", "configured", "code", "detail")})
    status, body = c.raw(admin, "POST", f"{CH}/channel-accounts/{ids['accountAE001']}/status", {"targetStatus": "ACTIVE", "reason": "对账验收"})
    c.check("准备", f"AE001 账号激活（{status}）", status == 200, body)
    for entity in ("AE001", "AE002"):
        status, mid = c.raw(admin, "POST", CH + "/mids", {"channelId": ch["channelId"], "channelAccountId": ids[f"account{entity}"],
                                                         "externalMidCode": f"RECON-{entity}-{RUN}", "entityNo": entity})
        c.check("准备", f"新建 MID {entity}（{status}）", status == 201, mid)
        ids[f"mid{entity}"] = mid["midId"]
    status, body = c.raw(admin, "POST", f"{CH}/mids/{ids['midAE001']}/status", {"targetStatus": "ACTIVE", "reason": "对账验收"})
    c.check("准备", f"AE001 MID 激活（{status}）", status == 200, body)
    group = {"midId": ids["midAE001"], "paymentMethodCode": "CARD", "schemeCode": "*", "countryCode": "*", "currency": "USD",
             "mccCode": "5999", "details": [{"capabilityCode": "ACTION.PAY", "booleanValue": True},
                                            {"capabilityCode": "ACTION.REFUND", "booleanValue": True}]}
    status, body = c.raw(admin, "POST", CH + "/mid-capabilities", group)
    c.check("准备", f"AE001 MID USD 能力组（{status}）", status == 201, body)
    record["channel"] = ids
    save()
    return ids


def cash_accounts(maker, checker, maker_id, checker_id):
    """AC-03：Vault Protect、复核、本人不能复核、只存 Token + 末四位、第二个有效账户被拒；AC-14：权限点拦截。"""
    number = "AE07" + str(uuid.uuid4().int)[:15] + "4321"
    body = {"entityType": "OPERATING_ENTITY", "entityId": "AE001", "accountPurpose": "CHANNEL_SETTLEMENT", "bankCode": "ENBD",
            "accountName": f"AE001 settlement {RUN}", "currency": "USD", "accountNumber": number}
    status, denied = c.raw(checker, "POST", R + "/cash-accounts", body)
    c.check("AC-14", f"只有审批权限的人调用提交（新建银行账户）路由 → 403（{status}）", status == 403, denied)
    status, created = c.raw(maker, "POST", R + "/cash-accounts", body)
    ok = status == 201 and created.get("status") == "PENDING_VERIFICATION"
    c.check("AC-03", f"Maker 提交新建银行账户 → PENDING_VERIFICATION（{status}）", ok, created)
    cash_id = created["cashAccountId"]
    remember("cashAccounts", cash_id)
    c.check("AC-03", "响应不含明文账号", number not in json.dumps(created))
    review = {"decision": "APPROVED", "approvedContentHash": created["contentHash"]}
    status, body = c.raw(maker, "POST", f"{R}/cash-accounts/{cash_id}/approval", review)
    c.check("AC-14", f"只有操作权限的人调用复核路由 → 403（{status}）", status == 403, body)
    status, body = c.raw(checker, "POST", f"{R}/cash-accounts/{cash_id}/approval", review)
    c.check("AC-03", f"Checker（另一人）批准 → ACTIVE（{status} {body.get('status')}）", status == 200 and body.get("status") == "ACTIVE", body)
    row = c.recon_sql(f"SELECT account_vault_token, account_last4, maker_id, checker_id, status FROM t_cash_account WHERE id={int(cash_id)}")[0]
    full = " ".join(c.recon_sql(f"SELECT * FROM t_cash_account WHERE id={int(cash_id)}")[0])
    c.check("AC-03", f"库中只存 Vault Token（长度 {len(row[0])}）与末四位 {row[1]}，整行不含明文账号；maker≠checker",
            len(row[0]) >= 32 and row[1] == "4321" and number not in full and row[2] == str(maker_id) and row[3] == str(checker_id), row[1:])
    status, detail = c.raw(maker, "GET", f"{R}/cash-accounts/{cash_id}")
    c.check("AC-03", "详情 / 列表不返回明文（只返回末四位）", status == 200 and number not in json.dumps(detail) and "4321" in json.dumps(detail), detail)
    # 内部接口不经 Gateway：直连私网
    import urllib.request
    with urllib.request.urlopen("http://127.0.0.1:18115/reconciliation/v1/cash-accounts/current?entityType=OPERATING_ENTITY"
                                "&entityId=AE001&currency=USD&purpose=CHANNEL_SETTLEMENT", timeout=10) as response:
        current = json.loads(response.read())
    c.check("AC-03", f"内部当前账户查询只返回 ID 与末四位（{sorted(current)}）",
            current.get("cashAccountId") == cash_id and number not in json.dumps(current), current)
    # 第二个同主体 + 币种 + 用途的有效账户：提交可以，批准被拒
    status, second = c.raw(maker, "POST", R + "/cash-accounts", {**body, "accountNumber": "AE07" + str(uuid.uuid4().int)[:15] + "9999"})
    if not c.check("AC-03", f"第二个同用途账户可提交（{status}）", status == 201, second):
        raise SystemExit(1)
    remember("cashAccounts", second.get("cashAccountId"))
    status, body2 = c.raw(checker, "POST", f"{R}/cash-accounts/{second['cashAccountId']}/approval",
                          {"decision": "APPROVED", "approvedContentHash": second["contentHash"]})
    c.check("AC-03", f"同主体 + 币种 + 用途第二个有效账户批准被拒（{body2.get('detail')}）", c.rejected(status, body2, "已有有效银行账户"), body2)
    status, body3 = c.raw(checker, "POST", f"{R}/cash-accounts/{second['cashAccountId']}/approval",
                          {"decision": "REJECTED", "approvedContentHash": second["contentHash"]})
    c.check("AC-03", f"拒绝新建 → CLOSED（{body3.get('status')}）", status == 200 and body3.get("status") == "CLOSED", body3)
    return cash_id


def clearing_accounts(maker, ids, cash_id):
    """AC-02：新建、挂 MID（channel-connector 核对）、重复归属、OE 不一致、未归属 MID、解析接口。"""
    code = f"PMX-AE001-USD-{RUN}"
    status, unassigned = c.raw(maker, "GET", R + "/clearing-accounts/unassigned-mids")
    hit = [i for i in unassigned.get("items", []) if i["midId"] == ids["midAE001"]]
    c.check("AC-02", f"未归属 MID 列表含新激活 MID（USD）（{status}）", status == 200 and hit and hit[0]["currency"] == "USD", unassigned)
    c.check("AC-02", "未归属 MID 不含 DRAFT MID（AE002）", all(i["midId"] != ids["midAE002"] for i in unassigned.get("items", [])))
    body = {"clearingAccountCode": code, "channelCode": "PAYERMAX_MOCK", "entityNo": "AE001", "currency": "USD",
            "settlementCashAccountId": cash_id, "unmatchedWaitDays": 3, "costToleranceAmount": "0.10", "cashGraceDays": 2}
    status, ca = c.raw(maker, "POST", R + "/clearing-accounts", body)
    c.check("AC-02", f"新建清算账户 {code}（{status} {ca.get('status')}）", status == 201, ca)
    remember("clearingAccounts", ca["clearingAccountId"])
    status, body2 = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca['clearingAccountId']}/mids", {"midId": ids["midAE001"]})
    c.check("AC-02", f"挂 MID（channel-connector 核对通过，{status}）", status in (200, 201), body2)
    status, ca2 = c.raw(maker, "POST", R + "/clearing-accounts", {**body, "clearingAccountCode": code + "-B"})
    remember("clearingAccounts", ca2["clearingAccountId"])
    status, body3 = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca2['clearingAccountId']}/mids", {"midId": ids["midAE001"]})
    c.check("AC-02", f"同一 MID + 币种重复归属被拒（{body3.get('detail')}）", c.rejected(status, body3), body3)
    status, ca3 = c.raw(maker, "POST", R + "/clearing-accounts", {**body, "clearingAccountCode": code + "-C", "entityNo": "AE002",
                                                                  "settlementCashAccountId": None})
    remember("clearingAccounts", ca3.get("clearingAccountId"))
    status, body4 = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca3['clearingAccountId']}/mids", {"midId": ids["midAE001"]})
    c.check("AC-02", f"MID 经营主体不一致被拒（{body4.get('detail')}）", c.rejected(status, body4), body4)
    status, body5 = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca3['clearingAccountId']}/mids", {"midId": ids["midAE002"]})
    c.check("AC-02", f"MID 未激活（DRAFT）被拒（{body5.get('detail')}）", c.rejected(status, body5), body5)
    status, body6 = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca3['clearingAccountId']}/mids", {"midId": "999999999999"})
    c.check("AC-02", f"MID 不存在被拒（{status} {body6.get('detail')}）", status == 400, body6)
    status, unassigned = c.raw(maker, "GET", R + "/clearing-accounts/unassigned-mids")
    c.check("AC-02", "挂上后未归属列表不再含该 MID", all(i["midId"] != ids["midAE001"] for i in unassigned.get("items", [])))
    import urllib.error
    import urllib.request

    def resolve(mid, currency):
        request = urllib.request.Request("http://127.0.0.1:18115/reconciliation/v1/clearing-accounts/resolutions",
                                         data=json.dumps({"midId": mid, "currency": currency}).encode(),
                                         headers={"Content-Type": "application/json"}, method="POST")
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as failure:
            return failure.code, json.loads(failure.read() or b"{}")
    status, hit = resolve(ids["midAE001"], "USD")
    c.check("AC-02", f"解析接口命中唯一账户（{status} {hit.get('clearingAccountCode')} {hit.get('entityNo')}）",
            status == 200 and hit.get("clearingAccountId") == ca["clearingAccountId"] and hit.get("entityNo") == "AE001", hit)
    status, miss = resolve(ids["midAE001"], "EUR")
    c.check("AC-02", f"解析接口命中不到返回业务错误（{status} {miss.get('code')}）", status == 400 and miss.get("code") == "CLEARING_ACCOUNT_NOT_RESOLVED", miss)
    return ca


def platform_txns(maker, ca):
    """AC-04：PAYMENT / REFUND 各一条、方向正确；重复投递幂等；其他类型忽略；缺清算账户引用进 DLQ。"""
    pay = c.fact("PAYMENT", ca["clearingAccountId"], "100.00", "NI_ORDER_REF", f"NI-ORD-{RUN}", channel_cost="0.45")
    ref = c.fact("REFUND", ca["clearingAccountId"], "30.00", "NI_REFUND_REF", f"NI-RFD-{RUN}")
    other = c.fact("MANUAL_ADJUSTMENT", ca["clearingAccountId"], "5.00")
    missing = c.fact("PAYMENT", None, "1.00", "NI_ORDER_REF", f"NI-ORD-MISS-{RUN}")
    record["facts"] = {"payment": pay["factId"], "refund": ref["factId"], "other": other["factId"], "missingRef": missing["factId"]}
    save()
    c.publish_facts([pay, ref, pay, other, missing])
    listed = c.wait_until(lambda: (lambda s, b: b if s == 200 and b.get("total", 0) >= 2 else None)(
        *c.raw(maker, "GET", f"{R}/platform-txns?clearingAccountId={ca['clearingAccountId']}&page=1&size=20")), 30)
    items = (listed or {}).get("items", [])
    by_fact = {i["billingFactId"]: i for i in items}
    p, r = by_fact.get(pay["factId"], {}), by_fact.get(ref["factId"], {})
    c.check("AC-04", f"PAYMENT → 应收（{p.get('direction')} {p.get('expectedAmount')} 预估费 {p.get('expectedFee')}）",
            p.get("direction") == "RECEIVABLE" and Decimal(p.get("expectedAmount", "0")) == Decimal("100") and Decimal(p.get("expectedFee", "0")) == Decimal("0.45"), p)
    c.check("AC-04", f"REFUND → 应付（{r.get('direction')} {r.get('expectedAmount')}）", r.get("direction") == "PAYABLE" and Decimal(r.get("expectedAmount", "0")) == Decimal("30"), r)
    time.sleep(3)
    count = c.recon_sql(f"SELECT COUNT(*) FROM t_platform_txn WHERE billing_fact_id IN ({pay['factId']},{ref['factId']},{other['factId']})")[0][0]
    c.check("AC-04", f"同一 PAYMENT 事实重复投递只生成一条、MANUAL_ADJUSTMENT 不生成（共 {count} 条）", count == "2")
    dlq = c.wait_until(lambda: [q for q in c.queues("reconciliation.billing.dlq") if q[1] >= 1], 120, 3)
    c.check("AC-04", f"缺清算账户引用 → 重投后进 reconciliation.billing.dlq 留存（{dlq}）", bool(dlq))
    record["platformTxns"] = [p.get("platformTxnId"), r.get("platformTxnId")]
    save()
    return pay, ref


def statement_lines(lines):
    out = []
    for line in lines:
        item = {"lineNo": int(line["line_no"]), "lineType": line["line_type"], "direction": line["direction"], "amount": line["amount"],
                "feeAmount": line["fee_amount"] or "0", "referenceType": line["reference_type"] or None,
                "externalTransactionId": line["external_transaction_id"] or None, "transactionDate": line["transaction_date"]}
        extra = {k: v for k, v in line.items() if k not in ("line_no", "line_type", "direction", "amount", "fee_amount", "reference_type",
                                                             "external_transaction_id", "transaction_date") and v}
        if extra:
            item["rawAttributes"] = extra
        out.append(item)
    return out


def import_statement(maker, csv_text, file_name):
    header, lines = c.parse_channel_csv(csv_text)
    status, started = c.raw(maker, "POST", R + "/channel-statements", {
        "clearingAccountCode": header["clearing_account_code"], "channelBatchRef": header["channel_batch_ref"],
        "settlementDate": header["settlement_date"], "currency": header["currency"], "grossAmount": header["gross_amount"],
        "feeAmount": header["fee_amount"], "netAmount": header["net_amount"], "cashDirection": header["cash_direction"],
        "fileName": file_name, "fileContentHash": c.sha256_text(csv_text), "declaredLineCount": len(lines)})
    if status != 201:
        return status, started, None
    remember("statements", started["statementId"])
    body = statement_lines(lines)
    for i in range(0, len(body), 100):
        s, appended = c.raw(maker, "POST", f"{R}/channel-statements/{started['statementId']}/lines", {"lines": body[i:i + 100]})
        if s != 200:
            return s, appended, started["statementId"]
    status, done = c.raw(maker, "POST", f"{R}/channel-statements/{started['statementId']}/completion")
    return status, done, started["statementId"]


def channel_statement(maker, checker, ca, collected):
    """AC-05 / AC-06 / AC-08 / AC-11 / AC-12：上传 S1 账单（分段）→ 调整行 UNEXPLAINED_ADJUSTMENT → CLASSIFY CHANNEL_FEE_OTHER（Maker+Checker）→ RECONCILED。"""
    template = (c.ROOT / "paytech/reconciliation/docs/formats/examples/channel-statement-S1.csv").read_text()
    csv_text = (template.replace("NI-AE001-USD", ca["clearingAccountCode"]).replace("NI-20261019-001", f"PMX-{RUN}-001")
                .replace("2026-10-19", TODAY).replace("NI-ORD-0001", f"NI-ORD-{RUN}").replace("NI-RFD-0001", f"NI-RFD-{RUN}"))
    record["channelCsv"] = csv_text
    # 负例：恒等式不成立（明细合计 ≠ 账单头）整单拒绝并逐行 / 整单给出原因
    bad = csv_text.replace(",68.00,0.45,67.55,", ",69.00,0.45,68.55,").replace(f"PMX-{RUN}-001", f"PMX-{RUN}-BAD")
    status, done, bad_id = import_statement(maker, bad, "bad.csv")
    c.check("AC-05", f"账单头与明细合计不一致 → 完成导入整单 REJECTED 并给原因（{done.get('status')} {done.get('rejectReason') or done.get('rejections')}）",
            status == 200 and done.get("status") == "REJECTED" and (done.get("rejectReason") or done.get("rejections")), done)
    status, done, sid = import_statement(maker, csv_text, "channel-statement-S1.csv")
    c.check("AC-05", f"S1 账单分段导入完成 → MATCHING（{status} {done.get('status')} 行数 {done.get('importedLineCount')}）",
            status == 200 and done.get("status") == "MATCHING" and done.get("importedLineCount") == 3, done)
    record["statementId"] = sid
    save()
    status, dup = c.raw(maker, "POST", R + "/channel-statements", {
        "clearingAccountCode": ca["clearingAccountCode"], "channelBatchRef": f"PMX-{RUN}-001", "settlementDate": TODAY, "currency": "USD",
        "grossAmount": "68.00", "feeAmount": "0.45", "netAmount": "67.55", "cashDirection": "CREDIT", "fileName": "dup.csv",
        "fileContentHash": "0" * 64, "declaredLineCount": 3})
    c.check("AC-05", f"同账单号已有 MATCHING 版本时再次导入被拒（{dup.get('detail')}）", c.rejected(status, dup), dup)
    if status == 201:
        remember("statements", dup.get("statementId"))

    def exception_open():
        s, page = c.raw(maker, "GET", f"{R}/recon-exceptions?clearingAccountId={ca['clearingAccountId']}&page=1&size=20")
        return [e for e in page.get("items", []) if e["exceptionType"] == "UNEXPLAINED_ADJUSTMENT" and e["statementId"] == sid]
    found = c.wait_until(exception_open, 60)
    c.check("AC-06", f"调整行开 UNEXPLAINED_ADJUSTMENT（阻塞，差异 {found[0]['differenceAmount'] if found else None}）",
            bool(found) and found[0]["blocking"] and Decimal(found[0]["differenceAmount"]) == Decimal("-2.00"), found)
    status, detail = c.raw(maker, "GET", f"{R}/channel-statements/{sid}")
    gate = detail.get("gate") or {}
    c.check("AC-08", f"对平前详情显示门禁未满足项（{gate}）", status == 200 and gate.get("adjustmentLinesClassified") is False
            and gate.get("noBlockingException") is False and gate.get("transactionLinesMatched") is True, detail)
    status, lines = c.raw(maker, "GET", f"{R}/channel-statements/{sid}/lines?page=1&size=50")
    by_no = {l["lineNo"]: l for l in lines.get("items", [])}
    c.check("AC-06", f"带交易号的行完全匹配（行 1 {by_no.get(1, {}).get('matchStatus')} 实际费 {by_no.get(1, {}).get('actualFee')}；行 2 {by_no.get(2, {}).get('matchStatus')}）",
            by_no.get(1, {}).get("matchStatus") == "MATCHED" and by_no.get(2, {}).get("matchStatus") == "MATCHED"
            and Decimal(by_no.get(1, {}).get("actualFee") or "-1") == Decimal("0.45"), lines)
    exception_id = found[0]["exceptionId"]
    status, ex = c.raw(maker, "GET", f"{R}/recon-exceptions/{exception_id}")
    codes = ex.get("availableClassificationCodes")
    c.check("AC-11", f"账单行异常只列允许的归类码（{codes}）", codes == ["CHANNEL_FEE_OTHER", "CHANNEL_RESERVE_HOLD", "CHANNEL_RESERVE_RELEASE", "RECON_DIFFERENCE"], ex)
    status, badcode = c.raw(maker, "POST", f"{R}/recon-exceptions/{exception_id}/resolutions",
                            {"resolutionType": "CLASSIFY", "classificationCode": "BANK_FEE", "amount": "-2.00", "note": "错误归类码"})
    c.check("AC-11", f"来源对象不允许的归类码被拒（{badcode.get('detail')}）", c.rejected(status, badcode), badcode)
    status, badamt = c.raw(maker, "POST", f"{R}/recon-exceptions/{exception_id}/resolutions",
                           {"resolutionType": "CLASSIFY", "classificationCode": "CHANNEL_FEE_OTHER", "amount": "-1.00", "note": "金额不足"})
    c.check("AC-11", f"归类金额未覆盖差异被拒（{badamt.get('detail')}）", c.rejected(status, badamt), badamt)
    status, denied = c.raw(checker, "POST", f"{R}/recon-exceptions/{exception_id}/resolutions",
                           {"resolutionType": "CLASSIFY", "classificationCode": "CHANNEL_FEE_OTHER", "amount": "-2.00", "note": "x"})
    c.check("AC-14", f"只有审批权限的人提交处置 → 403（{status}）", status == 403, denied)
    status, sub = c.raw(maker, "POST", f"{R}/recon-exceptions/{exception_id}/resolutions",
                        {"resolutionType": "CLASSIFY", "classificationCode": "CHANNEL_FEE_OTHER", "amount": "-2.00", "note": "月费 2.00 归渠道其他费用"})
    c.check("AC-11", f"Maker 提交 CLASSIFY CHANNEL_FEE_OTHER → PENDING_CHECK（{status} {sub.get('status')}）", status == 201 and sub.get("status") == "PENDING_CHECK", sub)
    review = {"decision": "APPROVED", "approvedContentHash": sub.get("contentHash")}
    status, denied = c.raw(maker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval", review)
    c.check("AC-14", f"只有操作权限的人复核处置 → 403（{status}）", status == 403, denied)
    status, approved = c.raw(checker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval", review)
    c.check("AC-11", f"Checker 批准 → APPLIED（{status} {approved.get('status')}）", status == 200 and approved.get("status") == "APPLIED", approved)
    record["lineResolutionId"] = sub["resolutionId"]
    save()

    def reconciled():
        s, d = c.raw(maker, "GET", f"{R}/channel-statements/{sid}")
        return d if d.get("statement", {}).get("status") == "RECONCILED" else None
    detail = c.wait_until(reconciled, 60)
    c.check("AC-08", f"四项门禁满足后账单 RECONCILED，生成预期银行流水（{(detail or {}).get('expectedMovementId')}）",
            bool(detail) and detail.get("expectedMovementId"), detail)
    status, ex = c.raw(maker, "GET", f"{R}/recon-exceptions/{exception_id}")
    c.check("AC-11", f"批准同一事务内异常已解决（{ex.get('exception', {}).get('status')}）", ex.get("exception", {}).get("status") == "RESOLVED", ex.get("exception"))
    batches = c.await_events(collected, "ChannelBatchReconciled", 1)
    payload = batches[0]["payload"] if batches else {}
    classified = (payload.get("classifiedLines") or [{}])[0]
    c.check("AC-08/AC-12", f"ChannelBatchReconciled：净额 {payload.get('netAmount')} = 应收 {payload.get('receivableTotal')} − 应付 {payload.get('payableTotal')} − 费 {payload.get('actualFeeTotal')} + 调整 {classified.get('amount')}",
            bool(batches) and Decimal(payload["netAmount"]) == Decimal(payload["receivableTotal"]) - Decimal(payload["payableTotal"])
            - Decimal(payload["actualFeeTotal"]) + Decimal(classified["amount"]) == Decimal("67.55"), payload)
    c.check("AC-08", f"已归类调整行带归类码 / 带符号金额 / 处置 ID（{classified}）",
            classified.get("classificationCode") == "CHANNEL_FEE_OTHER" and Decimal(classified.get("amount", "0")) == Decimal("-2.00")
            and str(classified.get("resolutionId")) == str(sub["resolutionId"]) and str(classified.get("lineNo")) == "3", classified)
    c.check("AC-12", f"信封：聚合 CHANNEL_STATEMENT / 版本 1 / sourceService reconciliation（{batches[0].get('aggregateType') if batches else None}）",
            bool(batches) and batches[0]["aggregateType"] == "CHANNEL_STATEMENT" and str(batches[0]["aggregateVersion"]) == "1"
            and batches[0]["sourceService"] == "reconciliation" and str(batches[0]["aggregateId"]) == str(sid), batches[0] if batches else None)
    time.sleep(3)
    collected.extend(c.drain_probe())
    cost = [e for e in collected if e["eventType"] == "ActualChannelCostConfirmed"]
    c.check("AC-07", f"实际费 0.45 = 预估 0.45 → 不发 ActualChannelCostConfirmed（收到 {len(cost)}）", not cost)
    adj = [e for e in collected if e["eventType"] == "ReconAdjustmentApproved"]
    c.check("AC-11", f"账单行归类不单独发 ReconAdjustmentApproved（收到 {len(adj)}）", not adj)
    status, again = c.raw(maker, "POST", f"{R}/channel-statements/{sid}/void")
    c.check("AC-05", f"已对平账单不能作废（{again.get('detail')}）", c.rejected(status, again), again)
    return sid, detail["expectedMovementId"]


def bank(maker, checker, cash_id, sid, movement_id, collected):
    """AC-09 / AC-10 / AC-11：银行对账单 S1 → 无建议（金额不等）→ 手工匹配 67.40 → ChannelSettlementConfirmed → BANK_AMOUNT_MISMATCH 0.15 → BANK_FEE。"""
    template = (c.ROOT / "paytech/reconciliation/docs/formats/examples/bank-statement-S1.csv").read_text()
    csv_text = (template.replace("ENBD-20261019", f"ENBD-{RUN}").replace("2026-10-19", TODAY).replace("ENBD-TXN-0001", f"ENBD-TXN-{RUN}")
                .replace("NI-20261019-001", f"PMX-{RUN}-001"))
    record["bankCsv"] = csv_text
    import csv as csvmod
    import io
    rows = list(csvmod.reader(io.StringIO(csv_text)))
    header = dict(zip(rows[0][1:], rows[1][1:]))
    lines = [dict(zip(rows[2], r)) for r in rows[3:] if r]

    def run_import(head, body_lines, name):
        status, started = c.raw(maker, "POST", R + "/bank-statements", {
            "cashAccountId": cash_id, "bankAccountLast4": head["bank_account_last4"], "statementRef": head["statement_ref"], "periodStart": head["period_start"], "periodEnd": head["period_end"],
            "currency": head["currency"], "openingBalance": head["opening_balance"], "closingBalance": head["closing_balance"],
            "fileName": name, "fileContentHash": c.sha256_text(name + json.dumps(body_lines)), "declaredLineCount": len(body_lines)})
        if status != 201:
            return status, started
        remember("bankStatements", started["bankStatementId"])
        s, _ = c.raw(maker, "POST", f"{R}/bank-statements/{started['bankStatementId']}/lines", {"lines": [
            {"lineNo": int(l["line_no"]), "bankTransactionRef": l["bank_transaction_ref"], "valueDate": l["value_date"], "direction": l["direction"],
             "amount": l["amount"], "counterpartyName": l["counterparty_name"], "referenceText": l["reference_text"]} for l in body_lines]})
        return c.raw(maker, "POST", f"{R}/bank-statements/{started['bankStatementId']}/completion")

    status, done = run_import({**header, "statement_ref": f"ENBD-{RUN}-BAD", "closing_balance": "1067.41"}, lines, "bad-bank.csv")
    c.check("AC-09", f"期初 + 流水 ≠ 期末 → 整单拒绝（{done.get('status')} {done.get('rejectReason')}）", status == 200 and done.get("status") == "REJECTED", done)
    status, done = run_import(header, lines, "bank-statement-S1.csv")
    c.check("AC-09", f"银行对账单导入 → IMPORTED（{status} {done.get('status')} 行 {done.get('importedLineCount')}，首张无缺口异常）",
            status == 200 and done.get("status") == "IMPORTED" and done.get("importedLineCount") == 1 and not done.get("gapExceptionId"), done)
    bank_statement_id = done["bankStatementId"]
    status, page = c.raw(maker, "GET", f"{R}/bank-statements/{bank_statement_id}/lines?page=1&size=10")
    line = page["items"][0]
    record["bankLineId"] = line["bankStatementLineId"]
    save()
    time.sleep(3)
    status, matches = c.raw(maker, "GET", f"{R}/bank-matches?bankStatementLineId={line['bankStatementLineId']}")
    c.check("AC-10", f"金额 67.40 ≠ 预期 67.55 → 没有一对一建议（{len(matches.get('items', []))} 条）", status == 200 and not matches.get("items"), matches)
    # 重复流水：同银行账户 + 交易号在新对账单中出现 → 整单拒绝（I-15），不重复入库
    status, done2 = run_import({**header, "statement_ref": f"ENBD-{RUN}-DUP", "opening_balance": "1067.40", "closing_balance": "1134.80"},
                               lines, "dup-bank.csv")
    c.check("AC-09", f"同银行账户 + 交易号重复的流水整单拒绝、不重复入库（{done2.get('status')} {done2.get('rejections')}）",
            status == 200 and done2.get("status") == "REJECTED"
            and c.recon_sql(f"SELECT COUNT(*) FROM t_bank_statement_line WHERE bank_transaction_ref='ENBD-TXN-{RUN}'")[0][0] == "1", done2)
    over = {"bankStatementLineId": line["bankStatementLineId"], "allocations": [{"expectedMovementId": movement_id, "amount": "67.41"}]}
    status, body = c.raw(maker, "POST", R + "/bank-matches", over)
    c.check("AC-10", f"手工匹配合计超出流水金额被拒（{body.get('detail')}）", c.rejected(status, body), body)
    status, body = c.raw(maker, "POST", R + "/bank-matches", {**over, "allocations": [{"expectedMovementId": movement_id, "amount": "67.40"}]})
    c.check("AC-10", f"手工匹配 67.40 → CONFIRMED（{status}）", status == 201, body)
    settled = c.await_events(collected, "ChannelSettlementConfirmed", 1)
    p = settled[0]["payload"] if settled else {}
    c.check("AC-10/AC-12", f"ChannelSettlementConfirmed 一条（matchedAmount {p.get('matchedAmount')}、银行流水 {p.get('bankStatementLineId')}、聚合 {settled[0]['aggregateType'] if settled else None}）",
            len(settled) == 1 and Decimal(p.get("matchedAmount", "0")) == Decimal("67.40") and str(p.get("bankStatementLineId")) == str(line["bankStatementLineId"])
            and settled[0]["aggregateType"] == "BANK_MATCH", p)
    status, opened = c.raw(maker, "POST", R + "/recon-exceptions", {"sourceType": "EXPECTED_BANK_MOVEMENT", "sourceId": movement_id})
    c.check("AC-11", f"人工开 BANK_AMOUNT_MISMATCH，差异由系统算 0.15（{status} {opened.get('exceptionType')} {opened.get('differenceAmount')}）",
            status == 201 and opened.get("exceptionType") == "BANK_AMOUNT_MISMATCH" and Decimal(opened.get("differenceAmount", "0")) == Decimal("0.15"), opened)
    status, ex = c.raw(maker, "GET", f"{R}/recon-exceptions/{opened['exceptionId']}")
    c.check("AC-11", f"预期银行流水异常可用归类码（{ex.get('availableClassificationCodes')}）含 BANK_FEE、不含 CHANNEL_FEE_OTHER",
            "BANK_FEE" in (ex.get("availableClassificationCodes") or []) and "CHANNEL_FEE_OTHER" not in (ex.get("availableClassificationCodes") or []), ex)
    status, sub = c.raw(maker, "POST", f"{R}/recon-exceptions/{opened['exceptionId']}/resolutions",
                        {"resolutionType": "CLASSIFY", "classificationCode": "BANK_FEE", "amount": "0.15", "note": "银行扣费 0.15"})
    c.check("AC-11", f"提交 CLASSIFY BANK_FEE 0.15（{status} {sub.get('status')}）", status == 201, sub)
    review = {"decision": "APPROVED", "approvedContentHash": sub.get("contentHash")}
    status, self_review = c.raw(maker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval", review)
    c.check("AC-14", f"Maker 复核自己的处置 → Gateway 403（无审批权限）（{status}）", status == 403, self_review)
    status, approved = c.raw(checker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval", review)
    c.check("AC-11", f"Checker 批准 → APPLIED（{status} {approved.get('status')}）", status == 200 and approved.get("status") == "APPLIED", approved)
    adj = c.await_events(collected, "ReconAdjustmentApproved", 1)
    p = adj[0]["payload"] if adj else {}
    c.check("AC-11/AC-12", f"ReconAdjustmentApproved 一条（{p.get('sourceType')} {p.get('classificationCode')} {p.get('amount')} 银行账户 {p.get('cashAccountId')}；maker≠checker）",
            len(adj) == 1 and p.get("sourceType") == "EXPECTED_BANK_MOVEMENT" and p.get("classificationCode") == "BANK_FEE"
            and Decimal(p.get("amount", "0")) == Decimal("0.15") and str(p.get("cashAccountId")) == str(cash_id)
            and p.get("makerId") != p.get("checkerId") and str(p.get("resolutionId")) == str(sub["resolutionId"]), p)
    status, st = c.raw(maker, "GET", f"{R}/channel-statements/{sid}")
    s = st.get("statement", {})
    c.check("AC-10", f"账单 CASH_CONFIRMED，已到账 {s.get('cashConfirmedAmount')}", s.get("status") == "CASH_CONFIRMED" and Decimal(s.get("cashConfirmedAmount") or "0") == Decimal("67.55"), s)
    status, mv = c.raw(maker, "GET", f"{R}/expected-bank-movements?cashAccountId={cash_id}&page=1&size=10")
    m = next((i for i in mv.get("items", []) if i["expectedMovementId"] == movement_id), {})
    c.check("AC-11", f"预期银行流水 CONFIRMED（确认 {m.get('confirmedAmount')} + 归类 {m.get('classifiedAmount')}）",
            m.get("status") == "CONFIRMED" and Decimal(m.get("confirmedAmount", "0")) == Decimal("67.40") and Decimal(m.get("classifiedAmount", "0")) == Decimal("0.15"), m)
    return bank_statement_id


def summary(maker, expect):
    status, body = c.raw(maker, "GET", f"{R}/summary?entityNo=AE001&currency=USD")
    t, u = body.get("inTransitStatements", {}), body.get("unmatchedBankLines", {})
    got = (t.get("reconciledCount"), t.get("reconciledUnconfirmedAmount"), u.get("count"), u.get("creditAmount"))
    return status == 200 and got == expect, got


def main():
    global RECORD, record
    if len(sys.argv) > 1:
        RECORD = Path(sys.argv[1])
        record.update(json.loads(RECORD.read_text()))
    admin = c.admin_token()
    c.bind_probe()
    collected = []
    if "people" not in record or "CHECKER" not in record["people"]:
        maker, maker_id, *_ = c.synthetic_person(admin, "MAKER", [c.PERM["RECON_READ"], c.PERM["RECON_OPERATE"], c.PERM["ACCOUNTING_READ"]], RUN, record)
        checker, checker_id, *_ = c.synthetic_person(admin, "CHECKER", [c.PERM["RECON_READ"], c.PERM["RECON_APPROVE"], c.PERM["ACCOUNTING_READ"]], RUN, record)
        save()
    else:
        people = record["people"]
        maker, maker_id = c.login(people["MAKER"]["email"], people["MAKER"]["password"]), people["MAKER"]["accountId"]
        checker, checker_id = c.login(people["CHECKER"]["email"], people["CHECKER"]["password"]), people["CHECKER"]["accountId"]
    ids = record.get("channel") or channel_setup(admin)
    if "cashAccountId" not in record:
        record["cashAccountId"] = cash_accounts(maker, checker, maker_id, checker_id)
        save()
    cash_id = record["cashAccountId"]
    if "clearingAccount" not in record:
        record["clearingAccount"] = clearing_accounts(maker, ids, cash_id)
        save()
    ca = record["clearingAccount"]
    if "platformTxns" not in record:
        platform_txns(maker, ca)
        ok, got = summary(maker, (0, "0.00", 0, "0.00"))
        c.check("F5", f"汇总（导入前）{got}", ok)
    if "expectedMovementId" not in record:
        sid, movement_id = channel_statement(maker, checker, ca, collected)
        record["expectedMovementId"] = movement_id
        save()
        ok, got = summary(maker, (1, "67.55", 0, "0.00"))
        c.check("F5", f"汇总（账单对平待到账）：已对平 1 / 未到账 67.55 / 未匹配流水 0（{got}）", ok)
    sid, movement_id = record["statementId"], record["expectedMovementId"]
    if "bankStatementId" not in record:
        record["bankStatementId"] = bank(maker, checker, cash_id, sid, movement_id, collected)
        save()
        ok, got = summary(maker, (0, "0.00", 0, "0.00"))
        c.check("F5", f"汇总（CASH_CONFIRMED 后）回零（{got}）", ok)
    record.setdefault("events", []).extend({"eventType": e["eventType"], "aggregateType": e["aggregateType"], "aggregateId": e["aggregateId"],
                                            "payload": e["payload"]} for e in collected)
    record.setdefault("results", []).extend(c.results)
    save()
    passed = sum(1 for r in c.results if r[2])
    print(f"\n本次合计 {passed}/{len(c.results)} 通过；记录 {RECORD}")


if __name__ == "__main__":
    main()
