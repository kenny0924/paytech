"""ledger 会计科目分级与维护：mor-test 阶段 7 验收并重建 AE001 演示数据。

经运营入口（Gateway → Operations BFF → ledger / account）验证科目树、科目维护与冻结、账户号与搜索、科目余额级次汇总；
billing 私网接口登记并批准 Commerce 已支付订单对应的 MANUAL_ADJUSTMENT，走 billing → account → ledger 真实链路。
数据保留作为演示数据（用户 2026-10-19 同意清理旧数据并按新科目表重建）。运行记录写入 local-environment/e2e/runtime/chart-acceptance-<ts>.json。
用法：E2E_TARGET=test python3 -I test_acceptance.py
"""
import json
import sys
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

ROOT = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
sys.path.insert(0, str(ROOT / "requirements/20261019/finance-account明细账与ledger控制科目重构/verification"))
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402
import local_acceptance as la  # noqa: E402

ENTITY_NO = "AE001"
CHART = "/finance/chart-of-accounts/v1/STANDARD"
RECORD = ROOT / f"local-environment/e2e/runtime/chart-acceptance-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")
    return ok


def main():
    s = e2e_http.secrets()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    post = lambda path, body=None: la.raw("POST", path, body, ops.token)
    get = lambda path: la.status_of(lambda: ops.get(path))
    record = {"entityNo": ENTITY_NO, "startedAt": datetime.now(timezone.utc).isoformat(), "factIds": []}
    RECORD.parent.mkdir(parents=True, exist_ok=True)
    save = lambda: RECORD.write_text(json.dumps(record, ensure_ascii=False, indent=2))

    # ---------- 科目树（AC-01、AC-02、AC-03） ----------
    ready = la.wait_until(lambda: get(f"{CHART}/changes?page=1&size=10")[0] == 200, 90)
    check("AC-18", "Gateway 已加载块 91 路由：变更记录接口 200", ready)
    code, page = get(f"{CHART}/accounts")
    items = page.get("items") if isinstance(page, dict) and "items" in page else page
    accounts = items if isinstance(items, list) else []
    by_no = {a["accountNo"]: a for a in accounts}
    by_id = {a["chartAccountId"]: a for a in accounts}
    summary = [a for a in accounts if not a["postingAllowed"]]
    leaves = [a for a in accounts if a["postingAllowed"]]
    levels = sorted({a["level"] for a in accounts})
    check("AC-02", f"STANDARD 74 个科目三级：一级 19、二级 26、三级末级 29（{code}，{len(accounts)}，级次 {levels}）",
          code == 200 and len(accounts) == 74 and levels == [1, 2, 3] and len(leaves) == 29
          and sum(1 for a in accounts if a["level"] == 1) == 19 and sum(1 for a in accounts if a["level"] == 2) == 26)
    def well_formed(a):
        if a["level"] == 1:
            return a["parentChartAccountId"] is None and len(a["accountNo"]) == 4
        parent = by_id.get(a["parentChartAccountId"], {})
        return (not parent.get("postingAllowed", True) and parent.get("accountClass") == a["accountClass"]
                and len(a["accountNo"]) == len(parent.get("accountNo", "")) + 2 and a["accountNo"].startswith(parent.get("accountNo", "-")))
    check("AC-01", "编号 4-2-2 分级、下级以上级为前缀、上级为同类别汇总科目、末级全部在三级、首位与类别一致",
          all(well_formed(a) for a in accounts) and all(a["level"] == 3 and len(a["accountNo"]) == 8 for a in leaves)
          and all(a["accountNo"][0] == "12345"[["ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"].index(a["accountClass"])] for a in accounts))
    check("AC-02", "系统代码不变：22010101 = CLIENT_PAYABLE、10010101 = BANK_CASH",
          by_no.get("22010101", {}).get("accountCode") == "CLIENT_PAYABLE" and by_no.get("10010101", {}).get("accountCode") == "BANK_CASH")

    # ---------- 科目维护（未采用前，AC-04、AC-07） ----------
    status, body = post(f"{CHART}/accounts", {"accountNo": "22010199", "accountCode": "BAD_NO", "accountName": "x", "accountClass": "ASSET",
                                              "normalSide": "DEBIT", "postingAllowed": True, "parentAccountNo": "220101"})
    check("AC-01", f"类别与上级不一致 / 编号首位不符被拒（{status} {body.get('code')}）", status == 400)
    status, created = post(f"{CHART}/accounts", {"accountNo": "22010102", "accountCode": "CLIENT_RESERVE_PAYABLE", "accountName": "Client 风险准备金",
                                                 "accountClass": "LIABILITY", "normalSide": "CREDIT", "postingAllowed": True, "parentAccountNo": "220101"})
    reserve_id = created.get("chartAccountId")
    record["createdChartAccountId"] = reserve_id
    save()
    check("AC-07", f"新增三级末级科目 22010102 直接生效 201（{status}）", status == 201 and reserve_id)
    status, body = post(f"{CHART}/accounts", {"accountNo": "22010102", "accountCode": "DUP_NO", "accountName": "dup", "accountClass": "LIABILITY",
                                              "normalSide": "CREDIT", "postingAllowed": True, "parentAccountNo": "220101"})
    check("AC-01", f"编号重复 → CHART_ACCOUNT_NO_DUPLICATE（{body.get('code')}）", la.business_error(status, body, "CHART_ACCOUNT_NO_DUPLICATE"))
    status, moved = post(f"{CHART}/accounts/{reserve_id}/update", {"accountName": "Client 风险准备金", "accountNo": "22110102", "parentAccountNo": "221101"})
    check("AC-04", f"未采用时可调整上级与编号（22010102 → 22110102，{status}）", status == 200 and moved.get("accountNo") == "22110102")
    status, body = post(f"{CHART}/accounts/{by_no['22010101']['chartAccountId']}/disable")
    check("AC-06", f"被账户类型目录引用的 CLIENT_PAYABLE 不能停用 → CHART_ACCOUNT_IN_USE（{body.get('code')}）",
          la.business_error(status, body, "CHART_ACCOUNT_IN_USE"))
    status, body = post(f"{CHART}/accounts/{by_no['2201']['chartAccountId']}/disable")
    check("AC-06", f"汇总科目有启用下级不能停用（{body.get('code')}）", la.business_error(status, body, "CHART_ACCOUNT_IN_USE"))

    # ---------- 基础资料：日历、期间、账簿（账簿采用 STANDARD） ----------
    status, cal = post("/finance/fiscal-calendars/v1", {"entityType": "OPERATING_ENTITY", "entityId": ENTITY_NO, "calendarCode": "AE001-STD",
                                                        "timezone": "Asia/Dubai", "yearStartMonth": 1})
    record["fiscalCalendarId"] = cal.get("fiscalCalendarId")
    periods = []
    for no, start, end in ((10, "2026-10-01", "2026-10-31"), (11, "2026-11-01", "2026-11-30")):
        _, p = post(f"/finance/fiscal-calendars/v1/{cal.get('fiscalCalendarId')}/periods",
                    {"fiscalYear": 2026, "periodNo": no, "startDate": start, "endDate": end})
        periods.append(p.get("accountingPeriodId"))
    record["accountingPeriodIds"] = periods
    _, opened = post(f"/finance/accounting-periods/v1/{periods[0]}/open")
    status, book = post("/finance/ledger-books/v1", {"entityType": "OPERATING_ENTITY", "entityId": ENTITY_NO, "currency": "USD", "chartCode": "STANDARD"})
    book_id = book.get("ledgerBookId")
    record["ledgerBookId"] = book_id
    save()
    check("SETUP", f"日历、10 月期间开放、AE001 / USD 账簿（{status} {opened.get('status')}）", status == 201 and opened.get("status") == "OPEN")

    # ---------- 采用后冻结（AC-05） ----------
    status, body = post(f"{CHART}/accounts/{reserve_id}/update", {"accountName": "Client 风险准备金", "accountNo": "22110103", "parentAccountNo": "221101"})
    check("AC-05", f"采用后改编号 → CHART_STRUCTURE_FROZEN（{body.get('code')}）", la.business_error(status, body, "CHART_STRUCTURE_FROZEN"))
    status, renamed = post(f"{CHART}/accounts/{reserve_id}/update", {"accountName": "Client 风险准备金（演示）", "accountNo": "22110102", "parentAccountNo": "221101"})
    check("AC-05", f"采用后仍可改名称（{status}）", status == 200 and renamed.get("accountName") == "Client 风险准备金（演示）")
    status, disabled = post(f"{CHART}/accounts/{reserve_id}/disable")
    check("AC-06", f"停用未被引用的末级科目（{status} {disabled.get('accountStatus')}）", status == 200 and disabled.get("accountStatus") == "INACTIVE")
    status, body = post(f"/finance/ledger-books/v1/{book_id}/accounts", {"accountCode": "CLIENT_RESERVE_PAYABLE", "ownerType": "OPERATING_ENTITY",
                                                                        "ownerId": ENTITY_NO, "balanceBucket": "NONE"})
    check("AC-06", f"停用科目不能新建账户 → CHART_ACCOUNT_INACTIVE（{body.get('code')}）", la.business_error(status, body, "CHART_ACCOUNT_INACTIVE"))
    code, changes = get(f"{CHART}/changes?chartAccountId={reserve_id}&page=1&size=10")
    types = [c.get("changeType") for c in changes.get("items") or []]
    check("AC-07", f"变更记录 4 条且有操作人（{types}）",
          code == 200 and sorted(types) == ["CREATE", "DISABLE", "UPDATE", "UPDATE"] and all(c.get("operatorId") for c in changes["items"]))

    # ---------- billing → account → ledger（AC-09、AC-11） ----------
    orders = e2e_http.sql("acquiring", "SELECT order_no, client_id, total_amount FROM t_commerce_order "
                                       f"WHERE order_status='PAID' AND entity_no='{ENTITY_NO}' AND currency='USD' ORDER BY paid_at")
    maker, checker = la.random_id("77"), la.random_id("78")
    record.update(makerId=maker, checkerId=checker)
    approved_count = 0
    for order_no, client_id, total in orders:
        principal = Decimal(total).quantize(Decimal("0.01"))
        fee = (principal * Decimal("0.035") + Decimal("0.30")).quantize(Decimal("0.01"), ROUND_HALF_UP)
        body = {"factType": "MANUAL_ADJUSTMENT", "sourceSystem": "OPERATIONS", "sourceReference": f"SEED-{order_no}", "clientId": client_id,
                "entityNo": ENTITY_NO, "currency": "USD", "makerId": maker,
                "lines": [{"componentType": "CLIENT_PRINCIPAL", "payerRole": "OPERATING_ENTITY", "payerId": ENTITY_NO, "payeeRole": "CLIENT",
                           "payeeId": client_id, "amount": str(principal), "direction": "CHARGE"},
                          {"componentType": "CLIENT_FEE", "payerRole": "CLIENT", "payerId": client_id, "payeeRole": "OPERATING_ENTITY",
                           "payeeId": ENTITY_NO, "amount": str(fee), "direction": "CHARGE"}]}
        status, fact = la.private("billing", "POST", "/billing/v1/facts", body)
        if fact.get("factId"):
            record["factIds"].append(fact["factId"])
            save()
        status2, approved = la.private("billing", "POST", f"/billing/v1/facts/{fact.get('factId')}/approve", {"checkerId": checker})
        approved_count += approved.get("status") == "APPROVED"
        if status != 201 or approved.get("status") != "APPROVED":
            print("  billing 响应", status, json.dumps(fact, ensure_ascii=False)[:300], status2, json.dumps(approved, ensure_ascii=False)[:300])
    check("AC-09", f"{len(orders)} 条事实登记并批准（{approved_count}）", orders and approved_count == len(orders))
    instructions = f"SELECT COUNT(*) FROM t_accounting_instruction WHERE ledger_book_id={int(book_id or 0)} AND origin_type='ACCOUNT_POSTING'"
    check("AC-09", "ledger 收到全部 AccountPosted 指令", la.wait_until(lambda: int(la.scalar("ledger", instructions) or 0) == len(orders), 180))

    code, owners = get(f"/finance/owner-accounts/v1?entityType=OPERATING_ENTITY&entityId={ENTITY_NO}&currency=USD&page=1&size=50")
    owner_items = owners.get("items") or []
    nos = sorted(o.get("accountNo") or "" for o in owner_items)
    check("AC-11", f"明细账账户号格式 AE001-USD-22010101-S00000n（{nos[:2]}…，{len(nos)}）",
          code == 200 and len(nos) == len(orders) and all(n.startswith("AE001-USD-22010101-S") and len(n) == 26 for n in nos) and len(set(nos)) == len(nos))
    if nos:
        code, hit = get(f"/finance/owner-accounts/v1?entityType=OPERATING_ENTITY&entityId={ENTITY_NO}&currency=USD&accountNo={nos[0]}&page=1&size=10")
        check("AC-11", f"按明细账账户号精确搜索命中 1 条（{code} {hit.get('total')}）", code == 200 and hit.get("total") == 1)
    code, ledger_accounts = get(f"/finance/ledger-books/v1/{book_id}/accounts?page=1&size=50")
    la_items = ledger_accounts.get("items") or []
    control = next((a for a in la_items if a.get("accountCode") == "CLIENT_PAYABLE"), {})
    check("AC-11", f"总账控制账户号 AE001-USD-22010101-0001 且带科目名称（{control.get('accountNo')} {control.get('accountName')}）",
          control.get("accountNo") == "AE001-USD-22010101-0001" and control.get("accountName"))
    code, hit = get(f"/finance/ledger-books/v1/{book_id}/accounts?accountNo=AE001-USD-22010101-0001&page=1&size=10")
    check("AC-11", f"按总账账户号精确搜索命中 1 条（{code} {hit.get('total')}）", code == 200 and hit.get("total") == 1)

    # ---------- 级次汇总（AC-08） ----------
    code, balances = get(f"/finance/ledger-books/v1/{book_id}/periods/{periods[0]}/account-balances")
    lines = {l["accountNo"]: l for l in balances.get("lines") or []}
    dec = lambda v: Decimal(v or "0")
    children = [l for l in lines.values() if l.get("parentChartAccountId") == lines.get("2201", {}).get("chartAccountId")]
    check("AC-08", f"2201 本期贷方 = 下级合计（{lines.get('2201', {}).get('periodCredit')} = {sum(dec(c['periodCredit']) for c in children)}）",
          code == 200 and "2201" in lines and dec(lines["2201"]["periodCredit"]) == sum(dec(c["periodCredit"]) for c in children) > 0)
    mid = lines.get("220101", {}); leaf = lines.get("22010101", {})
    check("AC-08", f"三级汇总：220101 = 22010101，且三者期末一致（{lines.get('2201', {}).get('closingBalance')} / {mid.get('closingBalance')} / {leaf.get('closingBalance')}）",
          mid.get("level") == 2 and leaf.get("level") == 3 and mid.get("closingBalance") == leaf.get("closingBalance") == lines.get("2201", {}).get("closingBalance"))
    level1 = [l for l in lines.values() if l.get("level") == 1]
    check("AC-08", f"一级科目本期借贷平衡（{sum(dec(l['periodDebit']) for l in level1)} / {sum(dec(l['periodCredit']) for l in level1)}）",
          level1 and sum(dec(l["periodDebit"]) for l in level1) == sum(dec(l["periodCredit"]) for l in level1))
    code, runs = get(f"/finance/ledger-books/v1/{book_id}/periods/{periods[0]}/trial-balances?page=1&size=10")
    check("AC-08", f"试算记录列表可查询（{code}，{runs.get('total')} 条）", code == 200)

    record["finishedAt"] = datetime.now(timezone.utc).isoformat()
    record["results"] = [{"ac": a, "name": n, "ok": ok} for a, n, ok in results]
    save()
    passed = sum(1 for r in results if r[2])
    print(f"\n运行记录：{RECORD}\n{passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
