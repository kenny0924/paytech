"""阶段 5 Finance 账务内核冒烟（任务卡 14 §2.4 最后一项）：billing → EconomicFactRecorded → ledger 过账 → account 余额 → consistency。

链路（全部私网直连，不经 Gateway；服务由 run-service.sh 启动，库为 *_v2e2e，MQ vhost /mor-e2e）：
  1–4   ledger 前置：为本次随机 OE（entityNo）建 OE 账簿（科目表 STANDARD，ledger-initial-data.sql）、财务日历（UTC）、
        含今日的会计期间并开放。OE 账簿不需要 Finance Entity；账户身份在过账时按规则行自动创建，不预建。
  5–8   billing Maker 提交 MANUAL_ADJUSTMENT（一行 CLIENT_PRINCIPAL：付方 OPERATING_ENTITY/entityNo → 收方 CLIENT/clientId，
        方向 ADJUSTMENT）→ 同内容重放返回原事实 → 编制人自批被拒 → 另一 Checker 批准（APPROVED）。
  9     billing 发布进度：pendingCount=0、maxPublishedFactId=factId（EconomicFactRecorded 已经 Broker 确认）。
  10    ledger.billing.p* 与 account.billing.p* 分区队列排空、有消费者。
  11–13 ledger：Client 应付账户出现分录 → 分录详情借贷相等、来源为本事实、两行分别为 OE RECON_ADJUSTMENT_EXPENSE 借与
        Client CLIENT_PAYABLE/PENDING 贷 → account-totals 中 Client 账户贷方 = 调整金额。
  14–16 account：账簿余额 Client CLIENT_PAYABLE/PENDING 贷方 = 调整金额、lastFactId=factId → Client 余额 pendingAmount = 调整金额
        → consistency appliedFactCount=1、maxAppliedFactId=factId、CLIENT 非零余额 1 个。
运行记录写入 local-environment/e2e/runtime/finance-smoke-<时间>.json；--cleanup <记录文件> 只打印清理 SQL，不执行。
用法：python3 finance_kernel_smoke.py [--amount 25.00] | --cleanup <记录文件>
"""
import datetime
import json
import random
import sys
import time
from pathlib import Path

from e2e_http import ROOT, private_call, rabbit_queues

RUNTIME = ROOT / "local-environment/e2e/runtime"
CURRENCY = "USD"
CHART_CODE = "STANDARD"                    # t_chart_of_accounts（ledger-initial-data.sql，ID 2001）
SOURCE_SYSTEM = "STAGE5_SMOKE"
FACT_TYPE = "MANUAL_ADJUSTMENT"            # EconomicsType.MANUAL_ADJUSTMENT
FEE_TYPE = "CLIENT_PRINCIPAL"              # AmountSource.CLIENT_PRINCIPAL
ROLE_OE, ROLE_CLIENT = "OPERATING_ENTITY", "CLIENT"   # FactPartyRole
DIRECTION = "ADJUSTMENT"                   # FactLineDirection.ADJUSTMENT
BOOK_TYPE = "OPERATING_ENTITY"             # LedgerEntityType.OPERATING_ENTITY
OE_ACCOUNT, CLIENT_ACCOUNT = "RECON_ADJUSTMENT_EXPENSE", "CLIENT_PAYABLE"   # 规则行 5301 / 5316
BUCKET_PENDING, BUCKET_NONE = "PENDING", "NONE"
STATUS_APPROVED, PERIOD_OPEN = "APPROVED", "OPEN"
MAKER_ID, CHECKER_ID = "900001", "900002"
RESULTS = []


def record(name, ok, detail=""):
    RESULTS.append((name, "PASS" if ok else "FAIL", detail))
    print(f"{len(RESULTS):>2}. {'PASS' if ok else 'FAIL'} {name} | {detail}")
    return ok


class Stop(Exception):
    pass


def http(service, method, path, body=None):
    status, text = private_call(service, method, path, {"Accept": "application/json"}, body)
    try:
        value = json.loads(text) if text else None
    except ValueError:
        value = {"raw": text[:300]}
    return status, value


def require(service, method, path, body=None, expected=(200, 201), name=""):
    status, value = http(service, method, path, body)
    if status not in expected:
        record(name or f"{method} {path}", False, f"HTTP {status} {json.dumps(value, ensure_ascii=False)[:300]}")
        raise Stop(f"{service} {method} {path} HTTP {status}")
    return status, value


def wait_for(probe, timeout=60, interval=2):
    deadline = time.time() + timeout
    while True:
        value = probe()
        if value or time.time() >= deadline:
            return value
        time.sleep(interval)


def amount(value):
    """Money 序列化为对象或字符串：统一取金额文本。"""
    if isinstance(value, dict):
        value = value.get("amount")
    return None if value is None else f"{float(value):.2f}"


def free_entity_no():
    """随机 OE 编号（[A-Z]{2}[0-9]{3}，前缀 SM），选 ledger 中尚无 USD 账簿的一个。"""
    for _ in range(20):
        entity_no = f"SM{random.randint(100, 999)}"
        _, page = require("ledger", "GET", f"/ledger-books/v1?entityType={BOOK_TYPE}&entityId={entity_no}&currency={CURRENCY}")
        if not (page or {}).get("items"):
            return entity_no
    raise Stop("20 次未找到空闲的 SM 开头 entityNo")


def setup_ledger(run):
    entity_no = free_entity_no()
    run["entityNo"] = entity_no
    _, book = require("ledger", "POST", "/ledger-books/v1",
                      {"entityType": BOOK_TYPE, "entityId": entity_no, "currency": CURRENCY, "chartCode": CHART_CODE},
                      expected=(201,), name="建 OE 账簿")
    run["ledgerBookId"] = book["ledgerBookId"]
    record("建 OE 账簿（STANDARD 科目表）", True, f"entityNo={entity_no} ledgerBookId={run['ledgerBookId']}")

    _, calendar = require("ledger", "POST", "/fiscal-calendars/v1",
                          {"entityType": BOOK_TYPE, "entityId": entity_no, "calendarCode": f"SMOKE-{entity_no}",
                           "timezone": "UTC", "yearStartMonth": 1}, expected=(201,), name="建财务日历")
    run["fiscalCalendarId"] = calendar["fiscalCalendarId"]
    record("建财务日历（UTC，1 月起）", True, f"fiscalCalendarId={run['fiscalCalendarId']}")

    today = datetime.datetime.now(datetime.UTC).date()
    start = today.replace(day=1)
    end = (start + datetime.timedelta(days=32)).replace(day=1) - datetime.timedelta(days=1)
    _, period = require("ledger", "POST", f"/fiscal-calendars/v1/{run['fiscalCalendarId']}/periods",
                        {"fiscalYear": today.year, "periodNo": today.month, "startDate": start.isoformat(),
                         "endDate": end.isoformat()}, expected=(201,), name="建会计期间")
    run["accountingPeriodId"] = period["accountingPeriodId"]
    record("建会计期间（含今日，FUTURE）", True, f"accountingPeriodId={run['accountingPeriodId']} {start}..{end}")

    _, opened = require("ledger", "POST", f"/accounting-periods/v1/{run['accountingPeriodId']}/open", name="开放期间")
    record("开放会计期间", (opened or {}).get("status") == PERIOD_OPEN, f"status={(opened or {}).get('status')}")


def register_fact(run, value):
    body = {"factType": FACT_TYPE, "sourceSystem": SOURCE_SYSTEM, "sourceReference": run["sourceReference"],
            "clientId": run["clientId"], "entityNo": run["entityNo"], "currency": CURRENCY, "makerId": MAKER_ID,
            "lines": [{"feeTypeCode": FEE_TYPE, "payerRole": ROLE_OE, "payerId": run["entityNo"], "payeeRole": ROLE_CLIENT,
                       "payeeId": run["clientId"], "amount": value, "direction": DIRECTION}]}
    status, fact = require("billing", "POST", "/billing/v1/economic-facts", body, expected=(201,), name="Maker 提交")
    run["factId"] = fact["factId"]
    record("Maker 提交 MANUAL_ADJUSTMENT（DRAFT）", status == 201 and fact.get("status") == "DRAFT",
           f"factId={fact['factId']} status={fact.get('status')} grossAmount={fact.get('grossAmount')}")

    status, replay = http("billing", "POST", "/billing/v1/economic-facts", body)
    record("同来源同内容重放返回原事实（200）", status == 200 and (replay or {}).get("factId") == run["factId"],
           f"HTTP {status} factId={(replay or {}).get('factId')}")

    status, rejected = http("billing", "POST", f"/billing/v1/economic-facts/{run['factId']}/approve", {"checkerId": MAKER_ID})
    record("编制人自批被拒（Maker-Checker）", 400 <= status < 500,
           f"HTTP {status} code={(rejected or {}).get('code')}")

    status, approved = require("billing", "POST", f"/billing/v1/economic-facts/{run['factId']}/approve",
                               {"checkerId": CHECKER_ID}, expected=(200,), name="Checker 批准")
    record("另一 Checker 批准（APPROVED）", approved.get("status") == STATUS_APPROVED and approved.get("checkerId") == CHECKER_ID,
           f"status={approved.get('status')} factVersion={approved.get('factVersion')} approvedTime={approved.get('approvedTime')}")
    run["factVersion"] = approved.get("factVersion")


def check_publication(run):
    query = f"bookEntityType={BOOK_TYPE}&bookEntityId={run['entityNo']}&currency={CURRENCY}"

    def published():
        status, pending = http("billing", "GET", f"/billing/v1/economic-facts/pending?{query}")
        if status == 200 and pending.get("pendingCount") == "0" and pending.get("maxPublishedFactId") == run["factId"]:
            return pending
        return None
    pending = wait_for(published, 60)
    _, last = http("billing", "GET", f"/billing/v1/economic-facts/pending?{query}")
    record("EconomicFactRecorded 已发布（pendingCount=0）", bool(pending),
           f"pending={json.dumps(pending or last, ensure_ascii=False)}")

    def drained():
        queues = [q for q in rabbit_queues("ledger.billing.p") + rabbit_queues("account.billing.p")]
        return queues if queues and all(m == 0 and c >= 1 for _, m, c in queues) else None
    queues = wait_for(drained, 60)
    shown = queues or rabbit_queues("ledger.billing.p") + rabbit_queues("account.billing.p")
    record("ledger / account 分区队列已排空且有消费者", bool(queues),
           " ".join(f"{n}:{m}/{c}" for n, m, c in shown) or "未声明队列")


def client_account(run):
    _, page = http("ledger", "GET", f"/ledger-books/v1/{run['ledgerBookId']}/accounts?ownerType={ROLE_CLIENT}&ownerId={run['clientId']}")
    items = (page or {}).get("items") or []
    return items[0] if items else None


def check_ledger(run, value):
    account = wait_for(lambda: (lambda a: a if a and a.get("lastJournalSequence") else None)(client_account(run)), 60)
    record("ledger 过账：Client 应付账户出现分录", bool(account),
           f"accountId={(account or {}).get('accountId')} creditTotal={(account or {}).get('creditTotal')}")
    if not account:
        raise Stop("ledger 未过账（看 ledger 日志与 ledger.billing.dlq）")
    run["clientLedgerAccountId"] = account["accountId"]
    _, lines = require("ledger", "GET", f"/ledger-books/v1/{run['ledgerBookId']}/accounts/{account['accountId']}/journal-lines")
    entry_id = ((lines or {}).get("items") or [{}])[0].get("journalEntryId")
    _, entry = require("ledger", "GET", f"/ledger-books/v1/{run['ledgerBookId']}/journal-entries/{entry_id}")
    run["journalEntryId"], run["instructionId"] = entry_id, entry.get("instructionId")
    debit = sum(float(l["amount"]) for l in entry["lines"] if l["entrySide"] == "DEBIT")
    credit = sum(float(l["amount"]) for l in entry["lines"] if l["entrySide"] == "CREDIT")
    sides = sorted((l["entrySide"], l["accountCode"], l["ownerType"], l["balanceBucket"]) for l in entry["lines"])
    expected = sorted([("DEBIT", OE_ACCOUNT, ROLE_OE, BUCKET_NONE), ("CREDIT", CLIENT_ACCOUNT, ROLE_CLIENT, BUCKET_PENDING)])
    record("分录借贷相等且为 OE 调整费用借 / Client 应付贷",
           abs(debit - credit) < 1e-9 and f"{debit:.2f}" == value and sides == expected
           and entry.get("instructionType") == "ECONOMIC_ADJUSTMENT" and entry.get("sourceSystem") == SOURCE_SYSTEM
           and entry.get("sourceEventKey") == run["sourceReference"],
           f"journalEntryId={entry_id} instructionType={entry.get('instructionType')} source={entry.get('sourceSystem')}/"
           f"{entry.get('sourceEventKey')} Dr={debit:.2f} Cr={credit:.2f} lines={sides}")
    _, totals = require("ledger", "GET", f"/ledger/v1/books/{run['ledgerBookId']}/account-totals")
    client = [t for t in totals.get("items", []) if t["ownerType"] == ROLE_CLIENT and t["ownerId"] == run["clientId"]]
    record("ledger account-totals：Client 贷方 = 调整金额",
           len(client) == 1 and amount(client[0]["creditTotal"]) == value,
           f"items={len(totals.get('items', []))} client={client}")


def check_account(run, value):
    def balance():
        _, book = http("account", "GET", f"/balances/v1/books/{run['ledgerBookId']}/accounts")
        rows = [b for b in (book or {}).get("items", []) if b["ownerType"] == ROLE_CLIENT and b["ownerId"] == run["clientId"]]
        return rows[0] if rows and rows[0].get("lastFactId") == run["factId"] else None
    row = wait_for(balance, 60)
    record("account 账簿余额：Client CLIENT_PAYABLE/PENDING 贷方 = 调整金额",
           bool(row) and row["accountCode"] == CLIENT_ACCOUNT and row["balanceBucket"] == BUCKET_PENDING
           and amount(row["creditTotal"]) == value,
           f"balance={row}")
    _, client = require("account", "GET",
                        f"/balances/v1/clients/{run['clientId']}?entityNo={run['entityNo']}&currency={CURRENCY}")
    record("Client 余额 pendingAmount = 调整金额", amount(client.get("pendingAmount")) == value,
           f"pending={amount(client.get('pendingAmount'))} available={amount(client.get('availableAmount'))}")

    def consistent():
        _, c = http("account", "GET", f"/balances/v1/books/{run['ledgerBookId']}/consistency")
        return c if c and c.get("maxAppliedFactId") == run["factId"] else None
    c = wait_for(consistent, 60) or http("account", "GET", f"/balances/v1/books/{run['ledgerBookId']}/consistency")[1]
    counts = {x["ownerType"]: x["count"] for x in (c or {}).get("nonZeroAccountCounts") or []}
    record("consistency 追平：appliedFactCount=1、maxAppliedFactId=factId、CLIENT 非零 1",
           (c or {}).get("appliedFactCount") == "1" and (c or {}).get("maxAppliedFactId") == run["factId"]
           and counts.get(ROLE_CLIENT) == "1", f"consistency={json.dumps(c, ensure_ascii=False)}")


def cleanup_sql(run):
    """打印本次运行产生行的清理 SQL（不执行）。表与列取自各服务 *-schema.sql。"""
    f, book, cal, period = run.get("factId"), run.get("ledgerBookId"), run.get("fiscalCalendarId"), run.get("accountingPeriodId")
    entity = run.get("entityNo")
    print(f"-- Finance 冒烟清理（{run.get('startedAt')}，entityNo={entity}，factId={f}）。审阅后由主会话执行。")
    if f:
        print(f"""USE mor_billing_v2e2e;
DELETE FROM t_compensation_event WHERE event_key LIKE 'ECONOMIC_FACT:{f}:%';
DELETE FROM t_economic_fact_line WHERE transaction_economics_id = {f};
DELETE FROM t_economic_fact WHERE id = {f};""")
    if book:
        print(f"""USE mor_ledger_v2e2e;
DELETE FROM t_journal_line WHERE journal_entry_id IN (SELECT id FROM t_journal_entry WHERE ledger_book_id = {book});
DELETE FROM t_journal_entry WHERE ledger_book_id = {book};
DELETE FROM t_instruction_line WHERE instruction_id IN (SELECT id FROM t_accounting_instruction WHERE ledger_book_id = {book});
DELETE FROM t_accounting_instruction WHERE ledger_book_id = {book};
DELETE FROM t_ledger_period_balance WHERE ledger_book_id = {book};
DELETE FROM t_ledger_account WHERE ledger_book_id = {book};
DELETE FROM t_book_sequence WHERE ledger_book_id = {book};
DELETE FROM t_ledger_book WHERE id = {book};""")
        if period:
            print(f"DELETE FROM t_accounting_period WHERE id = {period};")
        if cal:
            print(f"DELETE FROM t_fiscal_calendar WHERE id = {cal};")
        if f:
            print(f"DELETE FROM t_consumed_event WHERE aggregate_id = '{f}';\n"
                  f"DELETE FROM t_consumed_aggregate_version WHERE aggregate_id = '{f}';")
    if f:
        print(f"""USE mor_account_v2e2e;
DELETE FROM t_balance_applied_entry WHERE fact_id = {f};
DELETE FROM t_balance_applied_fact WHERE fact_id = {f};
DELETE FROM t_account_balance WHERE book_entity_type = '{BOOK_TYPE}' AND book_entity_id = '{entity}' AND currency = '{CURRENCY}';
DELETE FROM t_consumed_event WHERE aggregate_id = '{f}';
DELETE FROM t_consumed_aggregate_version WHERE aggregate_id = '{f}';""")


def main():
    if "--cleanup" in sys.argv:
        cleanup_sql(json.loads(Path(sys.argv[sys.argv.index("--cleanup") + 1]).read_text()))
        return
    value = sys.argv[sys.argv.index("--amount") + 1] if "--amount" in sys.argv else "25.00"
    RUNTIME.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d%H%M%S")
    run = {"startedAt": stamp, "clientId": str(random.randint(10**15, 10**16 - 1)),
           "sourceReference": f"stage5-finance-smoke-{stamp}-{random.randint(1000, 9999)}", "amount": value}
    run_file = RUNTIME / f"finance-smoke-{stamp}.json"
    try:
        setup_ledger(run)
        register_fact(run, value)
        check_publication(run)
        check_ledger(run, value)
        check_account(run, value)
    except Stop as stop:
        print(f"中止于：{stop}")
        RESULTS.append(("中止", "FAIL", str(stop)))
    finally:
        run_file.write_text(json.dumps(run, ensure_ascii=False, indent=1))
    print("DATA", json.dumps(run, ensure_ascii=False))
    print(f"运行记录：{run_file}（清理 SQL：python3 {Path(__file__).name} --cleanup {run_file}）")
    passed = sum(1 for r in RESULTS if r[1] == "PASS")
    print(f"\n合计 {passed}/{len(RESULTS)} 通过")
    raise SystemExit(0 if passed == len(RESULTS) else 1)


if __name__ == "__main__":
    main()
