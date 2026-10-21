"""对账切片 1 阶段 7：mor-test 全链路验收（AC-16 + AC-13 + AC-14 + 主要业务规则；由阶段 5 verification/s1_flow.py 改写）。

前置（release.md 第 5 节第 1–9 步完成）：块 123 已激活；reconciliation / billing / ops-bff / ops-web / platform 为候选镜像且 healthy；
XXL reconciliation 分组与两个任务已登记且执行器已注册。mor_reconciliation 15 张表为空。
人员：IAM 新建 MAKER（对账读 + 操作 + 财务读）与 CHECKER（对账读 + 审批 + 财务读）两名合成人员（I-10：财务读用于看到「财务」父菜单）。
渠道：管理员经 channel-admin 建合成渠道（编码默认 PAYERMAX = mor-test Platform 已登记接入方；RECON7_CHANNEL_CODE 可改）、
AE001 技术账号（随机合成参数，mor-test Vault 真实 Protect）+ ACTIVE MID + USD 能力组。
事实：ReconBillingFactPublisher 只投 reconciliation.billing.hash：PAYMENT 100.00（预估渠道成本 0.40，账单实际 0.45 → 触发
ActualChannelCostConfirmed）、REFUND 30.00、重复 PAYMENT、MANUAL_ADJUSTMENT、缺清算账户引用的 PAYMENT（→ DLQ）。
四类事件在临时观察队列 recon7.probe.reconciliation-events 上核对。AC-13：另建等待 0 天的清算账户，导入 1 行无对应流水的账单，
经 XXL 手动触发 channelLineWaitExpiry 两次，异常只开 1 条。
运行记录写 RECON7_STATE/run-<ts>.json（含合成人员口令，0600），供 stage7_cleanup.py 使用。
用法：python3 -I stage7_acceptance.py [run-*.json 续跑]
"""
import json
import os
import sys
import time
import uuid
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import recon7_common as c  # noqa: E402
import xxl_reconciliation as xxl  # noqa: E402

RUN = uuid.uuid4().hex[:6].upper()
TODAY = date.today().isoformat()
CHANNEL_CODE = os.environ.get("RECON7_CHANNEL_CODE", "PAYERMAX")
RECORD = c.STATE / f"run-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
record = {"run": RUN, "startedAt": datetime.now(timezone.utc).isoformat(), "channelCode": CHANNEL_CODE}
R = "/finance/reconciliation/v1"
CH = "/channel-admin/v1"


def save():
    RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    os.chmod(RECORD, 0o600)


def remember(kind, value):
    record.setdefault(kind, []).append(str(value))
    save()


def channel_setup(admin):
    """合成渠道（Platform 已有同编码接入方）、AE001 已配置并激活的技术账号、ACTIVE MID + USD 能力组。"""
    import secrets as pysecrets
    status, ch = c.raw(admin, "POST", CH + "/channels", {"channelCode": CHANNEL_CODE, "channelName": f"对账 mor-test 验收合成渠道 {RUN}"})
    if not c.check("准备", f"新建渠道 {CHANNEL_CODE}（{status}）", status == 201, ch):
        raise SystemExit("渠道编码已被占用或 Platform 无同编码接入方；换 RECON7_CHANNEL_CODE 后重跑")
    record["channel"] = {"channelId": ch["channelId"]}
    save()
    status, body = c.raw(admin, "POST", f"{CH}/channels/{ch['channelId']}/status", {"targetStatus": "ACTIVE", "reason": "对账验收"})
    c.check("准备", f"渠道激活（{status}）", status == 200, body)
    status, acct = c.raw(admin, "POST", CH + "/channel-accounts", {"channelId": ch["channelId"], "entityNo": "AE001",
                                                                  "accountName": f"对账验收 AE001 {RUN}"})
    c.check("准备", f"新建技术账号 AE001（{status}）", status == 201, acct)
    configuration = {"parameters": {"appId": "synthetic-app-" + pysecrets.token_hex(6), "merchantNo": "SYN" + pysecrets.token_hex(5).upper(),
                                    "keyVersion": "1", "payermaxPublicKey": "synthetic-public-" + pysecrets.token_urlsafe(24)},
                     "credentials": {"merchantPrivateKey": "synthetic-private-" + pysecrets.token_urlsafe(32)}}
    status, body = c.raw(admin, "PUT", f"{CH}/channel-accounts/{acct['channelAccountId']}/configuration", configuration)
    c.check("准备", f"AE001 账号配置（Vault Protect，{status}）", status == 200, {k: body.get(k) for k in ("status", "configured", "code", "detail")})
    status, body = c.raw(admin, "POST", f"{CH}/channel-accounts/{acct['channelAccountId']}/status", {"targetStatus": "ACTIVE", "reason": "对账验收"})
    c.check("准备", f"AE001 账号激活（{status}）", status == 200, body)
    status, mid = c.raw(admin, "POST", CH + "/mids", {"channelId": ch["channelId"], "channelAccountId": acct["channelAccountId"],
                                                     "externalMidCode": f"RECON7-AE001-{RUN}", "entityNo": "AE001"})
    c.check("准备", f"新建 MID AE001（{status}）", status == 201, mid)
    status, body = c.raw(admin, "POST", f"{CH}/mids/{mid['midId']}/status", {"targetStatus": "ACTIVE", "reason": "对账验收"})
    c.check("准备", f"MID 激活（{status}）", status == 200, body)
    group = {"midId": mid["midId"], "paymentMethodCode": "CARD", "schemeCode": "*", "countryCode": "*", "currency": "USD",
             "mccCode": "5999", "details": [{"capabilityCode": "ACTION.PAY", "booleanValue": True},
                                            {"capabilityCode": "ACTION.REFUND", "booleanValue": True}]}
    status, body = c.raw(admin, "POST", CH + "/mid-capabilities", group)
    c.check("准备", f"MID USD 能力组（{status}）", status == 201, body)
    record["channel"] = {"channelId": ch["channelId"], "accountAE001": acct["channelAccountId"], "midAE001": mid["midId"]}
    save()
    return record["channel"]


def cash_accounts(maker, checker, maker_id, checker_id):
    """AC-03 / AC-14：复核、Vault Protect（只存 Token + 末四位）、本人不能复核、第二个有效账户被拒、权限点拦截。"""
    number = "AE07" + str(uuid.uuid4().int)[:15] + "4321"
    body = {"entityType": "OPERATING_ENTITY", "entityId": "AE001", "accountPurpose": "CHANNEL_SETTLEMENT", "bankCode": "ENBD",
            "accountName": f"AE001 settlement {RUN}", "currency": "USD", "accountNumber": number}
    status, denied = c.raw(checker, "POST", R + "/cash-accounts", body)
    c.check("AC-14", f"只有审批权限的人调用提交（新建银行账户）路由 → 403（{status}）", status == 403, denied)
    status, created = c.raw(maker, "POST", R + "/cash-accounts", body)
    if not c.check("AC-03", f"Maker 提交新建银行账户 → PENDING_VERIFICATION（{status}）",
                   status == 201 and created.get("status") == "PENDING_VERIFICATION", created):
        raise SystemExit(1)
    cash_id = created["cashAccountId"]
    remember("cashAccounts", cash_id)
    c.check("AC-03", "响应不含明文账号", number not in json.dumps(created))
    review = {"decision": "APPROVED", "approvedContentHash": created["contentHash"]}
    status, body2 = c.raw(maker, "POST", f"{R}/cash-accounts/{cash_id}/approval", review)
    c.check("AC-14", f"只有操作权限的人调用复核路由 → 403（{status}）", status == 403, body2)
    status, body2 = c.raw(checker, "POST", f"{R}/cash-accounts/{cash_id}/approval", review)
    c.check("AC-03", f"Checker 批准 → ACTIVE（{status} {body2.get('status')}）", status == 200 and body2.get("status") == "ACTIVE", body2)
    row = c.recon_sql(f"SELECT account_vault_token, account_last4, maker_id, checker_id FROM t_cash_account WHERE id={int(cash_id)}")[0]
    full = " ".join(c.recon_sql(f"SELECT * FROM t_cash_account WHERE id={int(cash_id)}")[0])
    c.check("AC-03", f"库中只存 Vault Token（长度 {len(row[0])}）与末四位 {row[1]}，整行不含明文；maker≠checker",
            len(row[0]) >= 32 and row[1] == "4321" and number not in full and row[2] == str(maker_id) and row[3] == str(checker_id), row[1:])
    vault = c.sql("mor_vault", "SELECT COUNT(*) FROM t_vault_record WHERE data_type='CASH_ACCOUNT_NUMBER_V1'")[0][0]
    c.check("AC-03", f"mor-test Vault 已有 CASH_ACCOUNT_NUMBER_V1 记录（{vault}）", int(vault) >= 1)
    status, detail = c.raw(maker, "GET", f"{R}/cash-accounts/{cash_id}")
    c.check("AC-03", "详情只返回末四位", status == 200 and number not in json.dumps(detail) and "4321" in json.dumps(detail), detail)
    status, second = c.raw(maker, "POST", R + "/cash-accounts", {**body, "accountNumber": "AE07" + str(uuid.uuid4().int)[:15] + "9999"})
    if c.check("AC-03", f"第二个同用途账户可提交（{status}）", status == 201, second):
        remember("cashAccounts", second["cashAccountId"])
        status, b2 = c.raw(checker, "POST", f"{R}/cash-accounts/{second['cashAccountId']}/approval",
                           {"decision": "APPROVED", "approvedContentHash": second["contentHash"]})
        c.check("AC-03", f"同主体 + 币种 + 用途第二个有效账户批准被拒（{b2.get('detail')}）", c.rejected(status, b2, "已有有效银行账户"), b2)
        status, b3 = c.raw(checker, "POST", f"{R}/cash-accounts/{second['cashAccountId']}/approval",
                           {"decision": "REJECTED", "approvedContentHash": second["contentHash"]})
        c.check("AC-03", f"拒绝新建 → CLOSED（{b3.get('status')}）", status == 200 and b3.get("status") == "CLOSED", b3)
    return cash_id


def clearing_accounts(maker, ids, cash_id):
    """AC-02：未归属 MID、新建、挂 MID（channel-connector 核对）、重复归属被拒、挂上后不再未归属；另建等待 0 天的 AC-13 账户。"""
    code = f"PMX-AE001-USD-{RUN}"
    status, unassigned = c.raw(maker, "GET", R + "/clearing-accounts/unassigned-mids")
    hit = [i for i in unassigned.get("items", []) if str(i["midId"]) == str(ids["midAE001"])]
    c.check("AC-02", f"未归属 MID 列表含新激活 MID（USD）（{status}）", status == 200 and hit and hit[0]["currency"] == "USD", unassigned)
    body = {"clearingAccountCode": code, "channelCode": CHANNEL_CODE, "entityNo": "AE001", "currency": "USD",
            "settlementCashAccountId": cash_id, "unmatchedWaitDays": 3, "costToleranceAmount": "0.10", "cashGraceDays": 2}
    status, ca = c.raw(maker, "POST", R + "/clearing-accounts", body)
    if not c.check("AC-02", f"新建清算账户 {code}（{status}）", status == 201, ca):
        raise SystemExit(1)
    remember("clearingAccounts", ca["clearingAccountId"])
    status, b2 = c.raw(maker, "POST", f"{R}/clearing-accounts/{ca['clearingAccountId']}/mids", {"midId": ids["midAE001"]})
    c.check("AC-02", f"挂 MID（channel-connector 实时核对，{status}）", status in (200, 201), b2)
    status, wait_ca = c.raw(maker, "POST", R + "/clearing-accounts", {**body, "clearingAccountCode": code + "-W",
                                                                      "settlementCashAccountId": None, "unmatchedWaitDays": 0})
    c.check("AC-13", f"新建等待 0 天的清算账户 {code}-W（{status}）", status == 201, wait_ca)
    remember("clearingAccounts", wait_ca.get("clearingAccountId"))
    status, b3 = c.raw(maker, "POST", f"{R}/clearing-accounts/{wait_ca['clearingAccountId']}/mids", {"midId": ids["midAE001"]})
    c.check("AC-02", f"同一 MID + 币种重复归属被拒（{b3.get('detail')}）", c.rejected(status, b3), b3)
    status, b4 = c.raw(maker, "POST", f"{R}/clearing-accounts/{wait_ca['clearingAccountId']}/mids", {"midId": "999999999999"})
    c.check("AC-02", f"MID 不存在被拒（{status} {b4.get('detail')}）", status == 400, b4)
    status, unassigned = c.raw(maker, "GET", R + "/clearing-accounts/unassigned-mids")
    c.check("AC-02", "挂上后未归属列表不再含该 MID", all(str(i["midId"]) != str(ids["midAE001"]) for i in unassigned.get("items", [])))
    record["waitClearingAccount"] = wait_ca
    save()
    return ca


def platform_txns(maker, ca):
    """AC-04：PAYMENT / REFUND 方向；重复投递只一条；其他类型忽略；缺清算账户引用 → 重投后进 DLQ 留存。"""
    pay = c.fact("PAYMENT", ca["clearingAccountId"], "100.00", "NI_ORDER_REF", f"NI-ORD-{RUN}", channel_cost="0.40", payee_id=CHANNEL_CODE)
    ref = c.fact("REFUND", ca["clearingAccountId"], "30.00", "NI_REFUND_REF", f"NI-RFD-{RUN}")
    other = c.fact("MANUAL_ADJUSTMENT", ca["clearingAccountId"], "5.00")
    missing = c.fact("PAYMENT", None, "1.00", "NI_ORDER_REF", f"NI-ORD-MISS-{RUN}")
    record["facts"] = {"payment": pay["factId"], "refund": ref["factId"], "other": other["factId"], "missingRef": missing["factId"]}
    save()
    dlq_before = (c.queue("reconciliation.billing.dlq") or {}).get("messages", 0)
    record["dlqBefore"] = dlq_before
    c.publish_facts([pay, ref, pay, other, missing])
    listed = c.wait_until(lambda: (lambda s, b: b if s == 200 and b.get("total", 0) >= 2 else None)(
        *c.raw(maker, "GET", f"{R}/platform-txns?clearingAccountId={ca['clearingAccountId']}&page=1&size=20")), 60)
    by_fact = {i["billingFactId"]: i for i in (listed or {}).get("items", [])}
    p, r = by_fact.get(pay["factId"], {}), by_fact.get(ref["factId"], {})
    c.check("AC-04", f"PAYMENT → 应收（{p.get('direction')} {p.get('expectedAmount')} 预估费 {p.get('expectedFee')}）",
            p.get("direction") == "RECEIVABLE" and Decimal(p.get("expectedAmount", "0")) == Decimal("100")
            and Decimal(p.get("expectedFee", "0")) == Decimal("0.40"), p)
    c.check("AC-04", f"REFUND → 应付（{r.get('direction')} {r.get('expectedAmount')}）",
            r.get("direction") == "PAYABLE" and Decimal(r.get("expectedAmount", "0")) == Decimal("30"), r)
    time.sleep(3)
    count = c.recon_sql(f"SELECT COUNT(*) FROM t_platform_txn WHERE billing_fact_id IN ({pay['factId']},{ref['factId']},{other['factId']})")[0][0]
    c.check("AC-04", f"重复投递只生成一条、MANUAL_ADJUSTMENT 不生成（共 {count} 条）", count == "2")
    dlq = c.wait_until(lambda: (c.queue("reconciliation.billing.dlq") or {}).get("messages", 0) > dlq_before, 180, 3)
    c.check("AC-04", f"缺清算账户引用 → 重投后进 reconciliation.billing.dlq 留存（{(c.queue('reconciliation.billing.dlq') or {}).get('messages')}）", bool(dlq))
    account_dlq = (c.queue("account.billing.dlq") or {}).get("messages", 0)
    c.check("隔离", f"合成事实未进入 account（account.billing.dlq {account_dlq}；夹具只投 reconciliation.billing.hash）", account_dlq == 0)
    record["platformTxns"] = [p.get("platformTxnId"), r.get("platformTxnId")]
    save()


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
    """AC-05 / 06 / 07 / 08 / 11 / 12：S1 账单 → 调整行异常 → CLASSIFY（Maker / Checker）→ RECONCILED；成本差额事件。"""
    template = (c.ROOT / "paytech/reconciliation/docs/formats/examples/channel-statement-S1.csv").read_text()
    csv_text = (template.replace("NI-AE001-USD", ca["clearingAccountCode"]).replace("NI-20261019-001", f"PMX-{RUN}-001")
                .replace("2026-10-19", TODAY).replace("NI-ORD-0001", f"NI-ORD-{RUN}").replace("NI-RFD-0001", f"NI-RFD-{RUN}"))
    bad = csv_text.replace(",68.00,0.45,67.55,", ",69.00,0.45,68.55,").replace(f"PMX-{RUN}-001", f"PMX-{RUN}-BAD")
    status, done, _ = import_statement(maker, bad, "bad.csv")
    c.check("AC-05", f"账单头与明细合计不一致 → 整单 REJECTED 并给原因（{done.get('status')}）",
            status == 200 and done.get("status") == "REJECTED" and (done.get("rejectReason") or done.get("rejections")), done)
    status, done, sid = import_statement(maker, csv_text, "channel-statement-S1.csv")
    if not c.check("AC-05", f"S1 账单分段导入 → MATCHING（{status} {done.get('status')} 行数 {done.get('importedLineCount')}）",
                   status == 200 and done.get("status") == "MATCHING" and done.get("importedLineCount") == 3, done):
        raise SystemExit(1)
    record["statementId"] = sid
    save()

    def exception_open():
        s, page = c.raw(maker, "GET", f"{R}/recon-exceptions?clearingAccountId={ca['clearingAccountId']}&page=1&size=20")
        return [e for e in page.get("items", []) if e["exceptionType"] == "UNEXPLAINED_ADJUSTMENT" and str(e["statementId"]) == str(sid)]
    found = c.wait_until(exception_open, 90)
    c.check("AC-06", f"调整行开 UNEXPLAINED_ADJUSTMENT（阻塞，差异 {found[0]['differenceAmount'] if found else None}）",
            bool(found) and found[0]["blocking"] and Decimal(found[0]["differenceAmount"]) == Decimal("-2.00"), found)
    if not found:
        raise SystemExit(1)
    status, detail = c.raw(maker, "GET", f"{R}/channel-statements/{sid}")
    gate = detail.get("gate") or {}
    c.check("AC-08", f"对平前门禁未满足项可见（{gate}）", status == 200 and gate.get("adjustmentLinesClassified") is False
            and gate.get("noBlockingException") is False and gate.get("transactionLinesMatched") is True, detail)
    exception_id = found[0]["exceptionId"]
    status, ex = c.raw(maker, "GET", f"{R}/recon-exceptions/{exception_id}")
    codes = ex.get("availableClassificationCodes")
    c.check("AC-11", f"账单行异常只列允许的归类码（{codes}）",
            codes == ["CHANNEL_FEE_OTHER", "CHANNEL_RESERVE_HOLD", "CHANNEL_RESERVE_RELEASE", "RECON_DIFFERENCE"], ex)
    status, denied = c.raw(checker, "POST", f"{R}/recon-exceptions/{exception_id}/resolutions",
                           {"resolutionType": "CLASSIFY", "classificationCode": "CHANNEL_FEE_OTHER", "amount": "-2.00", "note": "x"})
    c.check("AC-14", f"只有审批权限的人提交处置 → 403（{status}）", status == 403, denied)
    status, sub = c.raw(maker, "POST", f"{R}/recon-exceptions/{exception_id}/resolutions",
                        {"resolutionType": "CLASSIFY", "classificationCode": "CHANNEL_FEE_OTHER", "amount": "-2.00", "note": "月费 2.00 归渠道其他费用"})
    c.check("AC-11", f"Maker 提交 CLASSIFY → PENDING_CHECK（{status} {sub.get('status')}）", status == 201 and sub.get("status") == "PENDING_CHECK", sub)
    review = {"decision": "APPROVED", "approvedContentHash": sub.get("contentHash")}
    status, denied = c.raw(maker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval", review)
    c.check("AC-14", f"只有操作权限的人复核处置 → 403（{status}）", status == 403, denied)
    status, approved = c.raw(checker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval", review)
    c.check("AC-11", f"Checker 批准 → APPLIED（{status} {approved.get('status')}）", status == 200 and approved.get("status") == "APPLIED", approved)

    def reconciled():
        s, d = c.raw(maker, "GET", f"{R}/channel-statements/{sid}")
        return d if d.get("statement", {}).get("status") == "RECONCILED" else None
    detail = c.wait_until(reconciled, 90)
    if not c.check("AC-08", f"账单 RECONCILED，生成预期银行流水（{(detail or {}).get('expectedMovementId')}）",
                   bool(detail) and detail.get("expectedMovementId"), detail):
        raise SystemExit(1)
    batches = c.await_events(collected, "ChannelBatchReconciled", 1)
    payload = batches[0]["payload"] if batches else {}
    classified = (payload.get("classifiedLines") or [{}])[0]
    c.check("AC-08/AC-12", f"ChannelBatchReconciled：净额 {payload.get('netAmount')} = 应收 − 应付 − 费 + 调整",
            bool(batches) and Decimal(payload["netAmount"]) == Decimal(payload["receivableTotal"]) - Decimal(payload["payableTotal"])
            - Decimal(payload["actualFeeTotal"]) + Decimal(classified["amount"]) == Decimal("67.55")
            and classified.get("classificationCode") == "CHANNEL_FEE_OTHER" and str(classified.get("resolutionId")) == str(sub["resolutionId"]),
            payload)
    c.check("AC-12", "信封：CHANNEL_STATEMENT / 版本 1 / sourceService reconciliation",
            bool(batches) and batches[0]["aggregateType"] == "CHANNEL_STATEMENT" and str(batches[0]["aggregateVersion"]) == "1"
            and batches[0]["sourceService"] == "reconciliation" and str(batches[0]["aggregateId"]) == str(sid), batches[0] if batches else None)
    cost = c.await_events(collected, "ActualChannelCostConfirmed", 1)
    cp = cost[0]["payload"] if cost else {}
    c.check("AC-07/AC-12", f"实际费 0.45 ≠ 预估 0.40 → ActualChannelCostConfirmed（差额 {cp.get('difference')}，聚合 {cost[0]['aggregateType'] if cost else None}）",
            len(cost) == 1 and Decimal(cp.get("expectedFee", "0")) == Decimal("0.40") and Decimal(cp.get("actualFee", "0")) == Decimal("0.45")
            and cost[0]["aggregateType"] == "PLATFORM_TXN", cp)
    adj = [e for e in collected if e["eventType"] == "ReconAdjustmentApproved"]
    c.check("AC-11", f"账单行归类不单独发 ReconAdjustmentApproved（收到 {len(adj)}）", not adj)
    status, again = c.raw(maker, "POST", f"{R}/channel-statements/{sid}/void")
    c.check("AC-05", f"已对平账单不能作废（{again.get('detail')}）", c.rejected(status, again), again)
    return sid, detail["expectedMovementId"]


def bank(maker, checker, cash_id, sid, movement_id, collected):
    """AC-09 / 10 / 11：银行对账单 → 手工匹配 67.40 → ChannelSettlementConfirmed → 预期流水差额 0.15 归 BANK_FEE → ReconAdjustmentApproved。"""
    import csv as csvmod
    import io
    template = (c.ROOT / "paytech/reconciliation/docs/formats/examples/bank-statement-S1.csv").read_text()
    csv_text = (template.replace("ENBD-20261019", f"ENBD-{RUN}").replace("2026-10-19", TODAY).replace("ENBD-TXN-0001", f"ENBD-TXN-{RUN}")
                .replace("NI-20261019-001", f"PMX-{RUN}-001"))
    rows = list(csvmod.reader(io.StringIO(csv_text)))
    header = dict(zip(rows[0][1:], rows[1][1:]))
    lines = [dict(zip(rows[2], r)) for r in rows[3:] if r]

    def run_import(head, body_lines, name):
        status, started = c.raw(maker, "POST", R + "/bank-statements", {
            "cashAccountId": cash_id, "bankAccountLast4": head["bank_account_last4"], "statementRef": head["statement_ref"],
            "periodStart": head["period_start"], "periodEnd": head["period_end"], "currency": head["currency"],
            "openingBalance": head["opening_balance"], "closingBalance": head["closing_balance"], "fileName": name,
            "fileContentHash": c.sha256_text(name + json.dumps(body_lines)), "declaredLineCount": len(body_lines)})
        if status != 201:
            return status, started
        remember("bankStatements", started["bankStatementId"])
        c.raw(maker, "POST", f"{R}/bank-statements/{started['bankStatementId']}/lines", {"lines": [
            {"lineNo": int(l["line_no"]), "bankTransactionRef": l["bank_transaction_ref"], "valueDate": l["value_date"], "direction": l["direction"],
             "amount": l["amount"], "counterpartyName": l["counterparty_name"], "referenceText": l["reference_text"]} for l in body_lines]})
        return c.raw(maker, "POST", f"{R}/bank-statements/{started['bankStatementId']}/completion")

    status, done = run_import({**header, "statement_ref": f"ENBD-{RUN}-BAD", "closing_balance": "1067.41"}, lines, "bad-bank.csv")
    c.check("AC-09", f"期初 + 流水 ≠ 期末 → 整单拒绝（{done.get('status')}）", status == 200 and done.get("status") == "REJECTED", done)
    status, done = run_import(header, lines, "bank-statement-S1.csv")
    if not c.check("AC-09", f"银行对账单导入 → IMPORTED（{status} {done.get('status')} 行 {done.get('importedLineCount')}）",
                   status == 200 and done.get("status") == "IMPORTED" and done.get("importedLineCount") == 1, done):
        raise SystemExit(1)
    bank_statement_id = done["bankStatementId"]
    status, page = c.raw(maker, "GET", f"{R}/bank-statements/{bank_statement_id}/lines?page=1&size=10")
    line = page["items"][0]
    time.sleep(3)
    status, matches = c.raw(maker, "GET", f"{R}/bank-matches?bankStatementLineId={line['bankStatementLineId']}")
    c.check("AC-10", f"金额 67.40 ≠ 预期 67.55 → 没有一对一建议（{len(matches.get('items', []))} 条）", status == 200 and not matches.get("items"), matches)
    over = {"bankStatementLineId": line["bankStatementLineId"], "allocations": [{"expectedMovementId": movement_id, "amount": "67.41"}]}
    status, body = c.raw(maker, "POST", R + "/bank-matches", over)
    c.check("AC-10", f"手工匹配合计超出流水金额被拒（{body.get('detail')}）", c.rejected(status, body), body)
    status, body = c.raw(maker, "POST", R + "/bank-matches", {**over, "allocations": [{"expectedMovementId": movement_id, "amount": "67.40"}]})
    c.check("AC-10", f"手工匹配 67.40 → CONFIRMED（{status}）", status == 201, body)
    settled = c.await_events(collected, "ChannelSettlementConfirmed", 1)
    p = settled[0]["payload"] if settled else {}
    c.check("AC-10/AC-12", f"ChannelSettlementConfirmed 一条（{p.get('matchedAmount')}，聚合 {settled[0]['aggregateType'] if settled else None}）",
            len(settled) == 1 and Decimal(p.get("matchedAmount", "0")) == Decimal("67.40") and settled[0]["aggregateType"] == "BANK_MATCH", p)
    status, opened = c.raw(maker, "POST", R + "/recon-exceptions", {"sourceType": "EXPECTED_BANK_MOVEMENT", "sourceId": movement_id})
    c.check("AC-11", f"人工开 BANK_AMOUNT_MISMATCH，系统算差异 0.15（{status} {opened.get('differenceAmount')}）",
            status == 201 and opened.get("exceptionType") == "BANK_AMOUNT_MISMATCH" and Decimal(opened.get("differenceAmount", "0")) == Decimal("0.15"), opened)
    status, sub = c.raw(maker, "POST", f"{R}/recon-exceptions/{opened['exceptionId']}/resolutions",
                        {"resolutionType": "CLASSIFY", "classificationCode": "BANK_FEE", "amount": "0.15", "note": "银行扣费 0.15"})
    c.check("AC-11", f"提交 CLASSIFY BANK_FEE 0.15（{status} {sub.get('status')}）", status == 201, sub)
    status, approved = c.raw(checker, "POST", f"{R}/recon-resolutions/{sub['resolutionId']}/approval",
                             {"decision": "APPROVED", "approvedContentHash": sub.get("contentHash")})
    c.check("AC-11", f"Checker 批准 → APPLIED（{status} {approved.get('status')}）", status == 200 and approved.get("status") == "APPLIED", approved)
    adj = c.await_events(collected, "ReconAdjustmentApproved", 1)
    p = adj[0]["payload"] if adj else {}
    c.check("AC-11/AC-12", f"ReconAdjustmentApproved 一条（{p.get('sourceType')} {p.get('classificationCode')} {p.get('amount')}；maker≠checker）",
            len(adj) == 1 and p.get("sourceType") == "EXPECTED_BANK_MOVEMENT" and p.get("classificationCode") == "BANK_FEE"
            and Decimal(p.get("amount", "0")) == Decimal("0.15") and p.get("makerId") != p.get("checkerId"), p)
    status, st = c.raw(maker, "GET", f"{R}/channel-statements/{sid}")
    s = st.get("statement", {})
    c.check("AC-10", f"账单 CASH_CONFIRMED，已到账 {s.get('cashConfirmedAmount')}",
            s.get("status") == "CASH_CONFIRMED" and Decimal(s.get("cashConfirmedAmount") or "0") == Decimal("67.55"), s)
    return bank_statement_id


def wait_expiry(maker):
    """AC-13：等待 0 天的清算账户上导入 1 行无对应平台流水的账单 → XXL 触发 channelLineWaitExpiry → CHANNEL_LINE_UNMATCHED；再触发不重复。"""
    wait_ca = record["waitClearingAccount"]
    csv_text = (f"#HEADER,clearing_account_code,channel_batch_ref,settlement_date,currency,gross_amount,fee_amount,net_amount,cash_direction\n"
                f"#HEADER,{wait_ca['clearingAccountCode']},PMX-{RUN}-W01,{TODAY},USD,10.00,0.00,10.00,CREDIT\n"
                "line_no,line_type,direction,amount,fee_amount,reference_type,external_transaction_id,transaction_date\n"
                f"1,PAYMENT,CREDIT,10.00,0,NI_ORDER_REF,NI-ORD-NOMATCH-{RUN},{TODAY}\n")
    status, done, sid = import_statement(maker, csv_text, "wait-expiry.csv")
    if not c.check("AC-13", f"导入无对应流水的账单 → MATCHING（{status} {done.get('status')}）", status == 200 and done.get("status") == "MATCHING", done):
        return
    record["waitStatementId"] = sid
    save()
    time.sleep(10)  # 等匹配任务把行留在 UNMATCHED

    def unmatched_exceptions():
        s, page = c.raw(maker, "GET", f"{R}/recon-exceptions?clearingAccountId={wait_ca['clearingAccountId']}&page=1&size=20")
        return [e for e in page.get("items", []) if e["exceptionType"] == "CHANNEL_LINE_UNMATCHED"]
    c.check("AC-13", f"触发前没有 CHANNEL_LINE_UNMATCHED（{len(unmatched_exceptions())}）", not unmatched_exceptions())
    admin = xxl.Admin()
    xxl.trigger(admin, "channelLineWaitExpiry")
    first = c.wait_until(unmatched_exceptions, 60, 3)
    c.check("AC-13", f"第一次触发后开 1 条 CHANNEL_LINE_UNMATCHED（{len(first or [])}）", len(first or []) == 1, first)
    xxl.trigger(admin, "channelLineWaitExpiry")
    time.sleep(10)
    second = unmatched_exceptions()
    c.check("AC-13", f"再次触发不重复开异常（仍 {len(second)} 条）", len(second) == 1, second)


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
        maker, maker_id = c.synthetic_person(admin, "MAKER", [c.PERM["RECON_READ"], c.PERM["RECON_OPERATE"], c.PERM["ACCOUNTING_READ"]], RUN, record)
        checker, checker_id = c.synthetic_person(admin, "CHECKER", [c.PERM["RECON_READ"], c.PERM["RECON_APPROVE"], c.PERM["ACCOUNTING_READ"]], RUN, record)
        save()
    else:
        people = record["people"]
        maker, maker_id = c.login(people["MAKER"]["email"], people["MAKER"]["password"]), people["MAKER"]["accountId"]
        checker, checker_id = c.login(people["CHECKER"]["email"], people["CHECKER"]["password"]), people["CHECKER"]["accountId"]
    status, nav = c.raw(maker, "GET", "/authorization/v1/navigation")
    c.check("AC-V1", f"MAKER 导航含对账分组（{status}）", status == 200 and "OPERATIONS_FINANCE_RECON" in json.dumps(nav), None)
    ids = record.get("channel") if record.get("channel", {}).get("midAE001") else channel_setup(admin)
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
        c.check("F5", f"汇总（对平待到账）{got}", ok)
    sid, movement_id = record["statementId"], record["expectedMovementId"]
    if "bankStatementId" not in record:
        record["bankStatementId"] = bank(maker, checker, cash_id, sid, movement_id, collected)
        save()
    if "waitStatementId" not in record:
        wait_expiry(maker)
    types = sorted({e["eventType"] for e in collected} | {e["eventType"] for e in record.get("events", [])})
    c.check("AC-16", f"四类事件均出现在 MQ（{types}）", types == ["ActualChannelCostConfirmed", "ChannelBatchReconciled",
                                                              "ChannelSettlementConfirmed", "ReconAdjustmentApproved"])
    record.setdefault("events", []).extend({"eventType": e["eventType"], "aggregateType": e["aggregateType"], "aggregateId": e["aggregateId"],
                                            "payload": e["payload"]} for e in collected)
    record.setdefault("results", []).extend(c.results)
    save()
    passed = sum(1 for r in c.results if r[2])
    print(f"\n本次合计 {passed}/{len(c.results)} 通过；记录 {RECORD}（含合成人员口令，验收后由 stage7_cleanup.py 删除人员）")


if __name__ == "__main__":
    main()
