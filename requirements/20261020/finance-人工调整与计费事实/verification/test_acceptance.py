"""财务控制台三项：mor-test 阶段 7 接口验收（人工调整与计费事实 / 期间关账与试算 / 记账规则包）。

制单与"本人不能复核"经运营入口（Gateway → Operations BFF → ledger / billing）以运营管理员身份验证；
复核人用另一个操作人 ID 直接调 ledger / billing 私网接口（mor-test 只有一个运营账号），验证真实的制单 / 复核分离规则。
数据写在 AE001 演示账簿上并保留；关账走完 驳回 → 再发起 → 审批 → 重开 → 审批，最后 10 月期间回到开放。
用法：E2E_TARGET=test python3 -I test_acceptance.py
"""
import json
import sys
import time
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

ROOT = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(ROOT / "requirements/20261019/finance-account明细账与ledger控制科目重构/verification"))
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402
import local_acceptance as la  # noqa: E402

RECORD = ROOT / f"local-environment/e2e/runtime/fin-console-acceptance-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
CHECKER = "790000000000000001"
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")
    return ok


def checker_headers():
    return {"X-Gateway-Actor-Subject": CHECKER, "X-Gateway-Session-Id": "acceptance-checker", "Content-Type": "application/json"}


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    post = lambda path, body=None: la.raw("POST", path, body, ops.token)
    get = lambda path: la.status_of(lambda: ops.get(path))
    priv = lambda svc, method, path, body=None: la.private(svc, method, path, body, checker_headers())
    record = {"startedAt": datetime.now(timezone.utc).isoformat()}
    save = lambda: RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=2))
    RECORD.parent.mkdir(parents=True, exist_ok=True)

    check("REG", "Gateway 已加载块 120—122：规则包列表 200", la.wait_until(lambda: get("/finance/accounting-rule-bundles/v1")[0] == 200, 90))
    code, books = get("/finance/ledger-books/v1?entityType=OPERATING_ENTITY&entityId=AE001&page=1&size=10")
    book = (books.get("items") or [{}])[0]
    book_id = book.get("ledgerBookId")
    code, cals = get("/finance/fiscal-calendars/v1?entityType=OPERATING_ENTITY&entityId=AE001&page=1&size=10")
    cal_id = (cals.get("items") or [{}])[0].get("fiscalCalendarId")
    code, periods = get(f"/finance/fiscal-calendars/v1/{cal_id}/periods?page=1&size=20")
    oct_period = next((p for p in periods.get("items") or [] if p.get("periodNo") in (10, "10")), {})
    period_id = oct_period.get("accountingPeriodId")
    record.update(ledgerBookId=book_id, fiscalCalendarId=cal_id, periodId=period_id)
    check("SETUP", f"AE001 账簿与 10 月期间（{book_id} / {period_id} {oct_period.get('status')}）", book_id and period_id and oct_period.get("status") == "OPEN")

    # ---------- 需求 1：人工调整 ----------
    code, accts = get(f"/finance/ledger-books/v1/{book_id}/accounts?page=1&size=50")
    by_code = {a["accountCode"]: a for a in accts.get("items") or []}
    expense, revenue = by_code.get("RECON_ADJUSTMENT_EXPENSE", {}), by_code.get("CLIENT_FEE_REVENUE", {})
    status, adj = post("/finance/manual-adjustments/v1", {"ledgerBookId": book_id, "accountingPeriodId": period_id, "currency": "USD",
                                                         "reasonCode": "ACCOUNTING_CORRECTION",
                                                         "lines": [{"accountId": expense.get("accountId"), "entrySide": "DEBIT", "amount": "5.00", "lineDescription": "验收：手续费更正 OPS-ACC-1"},
                                                                   {"accountId": revenue.get("accountId"), "entrySide": "CREDIT", "amount": "5.00", "lineDescription": "验收：手续费更正 OPS-ACC-1"}]})
    adj_id = adj.get("manualAdjustmentId")
    record["manualAdjustmentId"] = adj_id
    save()
    check("1/AC-02", f"经运营入口提交人工调整 201，状态待复核（{status} {adj.get('status')}）", status == 201 and adj.get("status") == "SUBMITTED")
    status, body = post("/finance/manual-adjustments/v1", {"ledgerBookId": book_id, "accountingPeriodId": period_id, "currency": "USD", "reasonCode": "OTHER",
                                                          "lines": [{"accountId": expense.get("accountId"), "entrySide": "DEBIT", "amount": "5.00", "lineDescription": "x"},
                                                                    {"accountId": revenue.get("accountId"), "entrySide": "CREDIT", "amount": "4.00", "lineDescription": "x"}]})
    check("1/AC-02", f"借贷不平衡 → ADJUSTMENT_UNBALANCED（{status} {body.get('code')}）", status == 400 and body.get("code") == "ADJUSTMENT_UNBALANCED")
    code, page = get(f"/finance/manual-adjustments/v1?ledgerBookId={book_id}&status=SUBMITTED&page=1&size=10")
    check("1/AC-01", f"列表按账簿 + 状态筛选含新调整（{code} {page.get('total')}）", code == 200 and any(i.get("manualAdjustmentId") == adj_id for i in page.get("items") or []))
    status, body = post(f"/finance/manual-adjustments/v1/{adj_id}/approve", {"approvedContentHash": adj.get("contentHash")})
    check("1/AC-03", f"制单人本人复核 → SELF_REVIEW_FORBIDDEN（{status} {body.get('code')}）", status == 400 and body.get("code") == "SELF_REVIEW_FORBIDDEN")
    status, approved = priv("ledger", "POST", f"/manual-adjustments/v1/{adj_id}/approve", {"approvedContentHash": adj.get("contentHash")})
    check("1/AC-03", f"另一操作人复核过账（{status} {approved.get('status')} 分录 {approved.get('journalEntryId')}）", status == 200 and approved.get("status") == "POSTED" and approved.get("journalEntryId"))
    code, detail = get(f"/finance/manual-adjustments/v1/{adj_id}")
    roles = sorted(a.get("approvalRole") for a in detail.get("approvals") or [])
    check("1/AC-07", f"详情：制单人为登录账号、复核记录为另一操作人（{roles}）", code == 200 and detail.get("makerId") != CHECKER and roles == ["CHECKER", "MAKER"])

    # ---------- 需求 1：计费事实 ----------
    client = e2e_http.sql("acquiring", "SELECT client_id FROM t_commerce_order WHERE order_status='PAID' AND entity_no='AE001' ORDER BY paid_at LIMIT 1")[0][0]
    fact_body = lambda amount: {"factType": "MANUAL_ADJUSTMENT", "clientId": client, "entityNo": "AE001", "currency": "USD",
                                "lines": [{"componentType": "CLIENT_PRINCIPAL", "payerRole": "OPERATING_ENTITY", "payerId": "AE001", "payeeRole": "CLIENT",
                                           "payeeId": client, "amount": amount, "direction": "CHARGE"}]}
    status, fact = post("/finance/billing-facts/v1", fact_body("10.00"))
    fact_id = fact.get("factId")
    record["factId"] = fact_id
    save()
    check("1/AC-05", f"经运营入口登记计费事实为草稿，来源为 OPERATIONS（{status} {fact.get('status')} {fact.get('sourceSystem')}）",
          status == 201 and fact.get("status") == "DRAFT" and fact.get("sourceSystem") == "OPERATIONS")
    code, page = get(f"/finance/billing-facts/v1?entityNo=AE001&currency=USD&status=DRAFT&page=1&size=10")
    check("1/AC-04", f"管理列表可看到草稿（{code} {page.get('total')}）", code == 200 and any(i.get("factId") == fact_id for i in page.get("items") or []))
    status, body = post(f"/finance/billing-facts/v1/{fact_id}/approve")
    check("1/AC-03", f"计费事实本人批准 → SELF_REVIEW_FORBIDDEN（{status} {body.get('code')}）", status == 400 and body.get("code") == "SELF_REVIEW_FORBIDDEN")
    before = la.scalar("account", f"SELECT COALESCE(SUM(net_balance),0) FROM t_owner_account WHERE entity_id='AE001' AND owner_id='{client}' AND account_code='CLIENT_PAYABLE'")
    status, approved = priv("billing", "POST", f"/billing/v1/facts/{fact_id}/approve")
    check("1/AC-05", f"另一操作人批准 → APPROVED（{status} {approved.get('status')}）", status == 200 and approved.get("status") == "APPROVED")
    grew = la.wait_until(lambda: Decimal(la.scalar("account", f"SELECT COALESCE(SUM(net_balance),0) FROM t_owner_account WHERE entity_id='AE001' AND owner_id='{client}' AND account_code='CLIENT_PAYABLE'") or 0) == Decimal(before or 0) + Decimal("10"), 120)
    check("1/AC-05", f"Client 应付余额增加 10.00（{before} →）", grew)
    status, draft2 = post("/finance/billing-facts/v1", fact_body("3.00"))
    status_v, voided = post(f"/finance/billing-facts/v1/{draft2.get('factId')}/void")
    status_a, body = post(f"/finance/billing-facts/v1/{draft2.get('factId')}/approve")
    check("1/AC-06", f"作废草稿 → VOIDED，之后不能批准（{status_v} {voided.get('status')} / {body.get('code')}）",
          status_v == 200 and voided.get("status") == "VOIDED" and status_a == 400 and body.get("code") == "FACT_NOT_DRAFT")

    # ---------- 需求 2：发起试算与关账 ----------
    status, run = post(f"/finance/ledger-books/v1/{book_id}/periods/{period_id}/trial-balances")
    check("2/AC-02", f"经运营入口发起试算 201（{status} {run.get('status')} 借 {run.get('totalDebit')} 贷 {run.get('totalCredit')}）",
          status == 201 and run.get("status") == "SUCCEEDED" and run.get("totalDebit") == run.get("totalCredit"))
    code, board = get(f"/finance/period-close/v1/board?fiscalCalendarId={cal_id}")
    check("2/AC-01", f"关账看板 200，含 10 月期间（{code} {len(board.get('periods') or [])} 期）", code == 200 and any((p.get("period") or {}).get("accountingPeriodId") == period_id for p in board.get("periods") or []))
    nov = next((p for p in periods.get("items") or [] if p.get("periodNo") in (11, "11")), {})
    status, body = post(f"/finance/accounting-periods/v1/{nov.get('accountingPeriodId')}/closes")
    check("2/AC-03", f"11 月（非开放）发起关账被拒（{status} {body.get('code')}）", status == 400 and body.get("code") in ("PERIOD_NOT_OPEN", "PERIOD_CLOSE_ORDER_INVALID"))
    t0 = time.monotonic()
    status, close = post(f"/finance/accounting-periods/v1/{period_id}/closes")
    elapsed = time.monotonic() - t0
    record["closeStartSeconds"] = round(elapsed, 2)
    check("2/AC-03", f"经运营入口发起关账 201（{status} {close.get('status')} 失败步骤 {close.get('failedStepType')}，耗时 {elapsed:.2f}s）",
          status == 201 and close.get("status") == "PENDING_CHECK")
    close_no = close.get("closeNo")
    code, detail = get(f"/finance/accounting-periods/v1/{period_id}/closes/{close_no}")
    status, body = post(f"/finance/accounting-periods/v1/{period_id}/closes/{close_no}/approve", {"approvedCloseHash": detail.get("closeHash")})
    check("2/AC-04", f"关账本人复核 → SELF_REVIEW_FORBIDDEN（{body.get('code')}）", status == 400 and body.get("code") == "SELF_REVIEW_FORBIDDEN")
    status, rejected = priv("ledger", "POST", f"/accounting-periods/v1/{period_id}/closes/{close_no}/reject", {"approvedCloseHash": detail.get("closeHash")})
    period_status = lambda: la.scalar("ledger", f"SELECT status FROM t_accounting_period WHERE id={int(period_id)}")
    check("2/AC-04", f"驳回后期间回到开放（{status} {rejected.get('status')} / {period_status()}）", status == 200 and rejected.get("status") == "REJECTED" and period_status() == "OPEN")
    status, close2 = post(f"/finance/accounting-periods/v1/{period_id}/closes")
    code, detail2 = get(f"/finance/accounting-periods/v1/{period_id}/closes/{close2.get('closeNo')}")
    status, approved = priv("ledger", "POST", f"/accounting-periods/v1/{period_id}/closes/{close2.get('closeNo')}/approve", {"approvedCloseHash": detail2.get("closeHash")})
    check("2/AC-04", f"再次发起并由另一操作人审批 → 已关账（{status} {approved.get('status')} / {period_status()}）", status == 200 and approved.get("status") == "COMPLETED" and period_status() == "CLOSED")
    status, body = post("/finance/manual-adjustments/v1", {"ledgerBookId": book_id, "accountingPeriodId": period_id, "currency": "USD", "reasonCode": "OTHER",
                                                          "lines": [{"accountId": expense.get("accountId"), "entrySide": "DEBIT", "amount": "1.00", "lineDescription": "x"},
                                                                    {"accountId": revenue.get("accountId"), "entrySide": "CREDIT", "amount": "1.00", "lineDescription": "x"}]})
    check("2/AC-06", f"已关账期间不能登记人工调整 → ADJUSTMENT_PERIOD_NOT_OPEN（{body.get('code')}）", status == 400 and body.get("code") == "ADJUSTMENT_PERIOD_NOT_OPEN")
    status, reopen = post(f"/finance/accounting-periods/v1/{period_id}/reopens", {"reasonCode": "ACCOUNTING_CORRECTION", "requestRef": "OPS-ACC-REOPEN-1"})
    code, rdetail = get(f"/finance/accounting-periods/v1/{period_id}/closes/{reopen.get('closeNo')}")
    status2, rapproved = priv("ledger", "POST", f"/accounting-periods/v1/{period_id}/closes/{reopen.get('closeNo')}/approve", {"approvedCloseHash": rdetail.get("closeHash")})
    check("2/AC-05", f"重开发起并审批 → 期间回到开放（{status} / {status2} {rapproved.get('status')} / {period_status()}）",
          status == 201 and status2 == 200 and rapproved.get("status") == "COMPLETED" and period_status() == "OPEN")

    # ---------- 需求 3：记账规则包 ----------
    code, bundles = get("/finance/accounting-rule-bundles/v1")
    codes = sorted(b.get("bundleCode") for b in bundles.get("items") or [])
    check("3/AC-01", f"规则包列表（{codes}）", code == 200 and codes == ["STANDARD_ADJUSTMENT", "STANDARD_TRANSACTION"])
    code, bundle = get("/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT")
    current_ver = bundle.get("currentBundleVer")
    code, current = get(f"/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions/{current_ver}")
    lines = [{k: l.get(k) for k in ("instructionType", "lineNo", "lineCode", "amountSourceCode", "ownerRole", "accountCode", "balanceBucket", "entrySide", "requiredFlag")}
             for l in current.get("lines") or []]
    now = datetime.now(timezone.utc)
    status, draft = post("/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions", {"effectiveFrom": now.isoformat().replace("+00:00", "Z"), "lines": lines})
    status_v, voided = post(f"/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions/{draft.get('bundleVer')}/void")
    check("3/AC-03", f"以现行版本为模板新建草稿并作废（{status} / {status_v} {voided.get('status')}）", status == 201 and status_v == 200 and voided.get("status") == "VOIDED")
    future = (now + timedelta(seconds=75)).isoformat().replace("+00:00", "Z")
    status, draft2 = post("/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions", {"effectiveFrom": future, "lines": lines})
    ver2 = draft2.get("bundleVer")
    record["ruleVersion"] = ver2
    save()
    status_s, submitted = post(f"/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions/{ver2}/submit")
    status_b, body = post(f"/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions/{ver2}/approve")
    check("3/AC-04", f"制单人提交；本人审批 → SELF_REVIEW_FORBIDDEN（{status_s} {submitted.get('status')} / {body.get('code')}）",
          status_s == 200 and submitted.get("status") == "PENDING_APPROVAL" and body.get("code") == "SELF_REVIEW_FORBIDDEN")
    status, approved = priv("ledger", "POST", f"/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions/{ver2}/approve")
    check("3/AC-06", f"未来生效版本审批后为待生效（{status} {approved.get('status')}）", status == 200 and approved.get("status") == "APPROVED")
    activated = la.wait_until(lambda: get(f"/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions/{ver2}")[1].get("status") == "ACTIVE", 200)
    code, after = get("/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT")
    old_status = get(f"/finance/accounting-rule-bundles/v1/STANDARD_ADJUSTMENT/versions/{current_ver}")[1].get("status")
    check("3/AC-05/06", f"定时任务到时生效：新版本 ACTIVE、当前版本切换、旧版本 RETIRED（{after.get('currentBundleVer')} / {old_status}）",
          activated and after.get("currentBundleVer") == ver2 and old_status == "RETIRED")

    record["finishedAt"] = datetime.now(timezone.utc).isoformat()
    record["results"] = [{"ac": a, "name": n, "ok": ok} for a, n, ok in results]
    save()
    passed = sum(1 for r in results if r[2])
    print(f"\n运行记录：{RECORD}\n{passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
