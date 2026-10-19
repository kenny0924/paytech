"""阶段 5 本机综合验证：billing → account → ledger → Operations 控制台（任务卡 E，场景 S1～S6）。

前置（本机，E2E_TARGET=local）：run-service.sh 已按任务卡 E 启动 platform、billing、ledger、account、ops-bff、gateway、ops-web
（新 JAR）；mor_account_v2e2e 已执行 REQ/db/account/ddl.sql（9 表），mor_ledger_v2e2e 已执行 REQ/db/ledger/ddl.sql + dml.sql
（规则行 36），mor_platform_v2e2e 已执行块 99 与 activation-local.sql；mor_billing_v2e2e 已有 billing-schema.sql。
入口 https://127.0.0.1:18444（运营登录走 Gateway → BFF）；billing 18113、account 18114、ledger 18105 走内部端口直连。
mor-test（E2E_TARGET=test，阶段 7，见 release/release.md）：入口 http://localhost:28080；库为 mor_billing / mor_account / mor_ledger /
mor_platform（127.0.0.1:23306，应用账号口令只从 test-environment/.env.test 读取）；billing / account / ledger 私网接口经 e2e_http
接入 mor-test-backend 的一次性探针容器调用；断言与本机相同，本脚本不读服务日志。前置：release.md 第 4 节 SQL、替换与 test-activate.sql 已完成。
结构、登录、运行记录、清理与基线核对照抄 requirements/20261018/operations-console-财务基础资料与账本余额查询/verification/local_acceptance.py。
不打印 Token、口令与密钥。

用法：
  python3 -I local_acceptance.py            验收；运行记录写入 local-environment/e2e/runtime/subledger-acceptance-<ts>.json
  python3 -I local_acceptance.py --cleanup  按最新运行记录执行 acceptance-cleanup-local.sql，并核对 billing / account / ledger 行数回到基线
"""
import base64
import json
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

RUNTIME = ROOT / "local-environment/e2e/runtime"
CLEANUP_SQL = HERE / "acceptance-cleanup-local.sql"
RUN = uuid.uuid4().hex[:6].upper()
CHINESE = re.compile(r"[一-鿿]")
SERVICES = ("billing", "account", "ledger")
FINANCE_ACCOUNTING_READ = "900000000000097101"
results = []


def check(ac, name, ok):
    results.append((ac, name, bool(ok)))
    print(f"[{'通过' if ok else '失败'}] {ac} {name}")


def status_of(fn):
    """执行调用，返回 (HTTP 状态, 响应体)；成功统一记 200。"""
    try:
        return 200, fn()
    except e2e_http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {}


def raw(method, path, body=None, token=None, extra=None):
    """直接请求运营入口以读取状态码或附加自定义 Header；返回 (状态, 响应体)；429 按 Retry-After 重试。"""
    headers = {"Origin": e2e_http.OPERATIONS, "Accept": "application/json", "Content-Type": "application/json",
               "Idempotency-Key": str(uuid.uuid4()), **(extra or {})}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else (b"" if method == "POST" else None)
    request = urllib.request.Request(e2e_http.OPERATIONS + "/api/operations" + path, data=data, headers=headers, method=method)
    for attempt in range(12):
        try:
            with urllib.request.urlopen(request, context=e2e_http.TLS, timeout=20) as response:
                text = response.read().decode()
                return response.status, json.loads(text) if text else {}
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 11:
                time.sleep(int(failure.headers.get("Retry-After") or 10))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text)
            except ValueError:
                return failure.code, {}


def private(service, method, path, body=None, headers=None):
    """内部端口直连，返回 (状态, JSON 或 {})。"""
    status, text = e2e_http.private_call(service, method, path, headers or {}, body)
    try:
        return status, json.loads(text) if text else {}
    except ValueError:
        return status, {"raw": text[:300]}


def business_error(status, body, code):
    """400 + 指定 code + 中文 detail。"""
    return status == 400 and body.get("code") == code and bool(CHINESE.search(body.get("detail") or ""))


def wait_until(probe, seconds=90):
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        try:
            if probe():
                return True
        except RuntimeError:
            pass
        time.sleep(2)
    return False


def jwt_subject(token):
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))["sub"]


def money(value):
    """Money 对象或十进制字符串 → Decimal；缺失为 None。"""
    if isinstance(value, dict):
        value = value.get("amount")
    return Decimal(value) if value not in (None, "") else None


def scalar(service, statement):
    rows = e2e_http.sql(service, statement)
    return rows[0][0] if rows and rows[0] else None


def table_counts():
    """billing / account / ledger 三库所有表行数（基线与清理核对用）。"""
    counts = {}
    for service in SERVICES:
        database = e2e_http.DATABASES[service][0]
        tables = [r[0] for r in e2e_http.sql(service, f"SELECT table_name FROM information_schema.tables WHERE table_schema='{database}' "
                                                      "AND table_type='BASE TABLE' ORDER BY table_name")]
        union = " UNION ALL ".join(f"SELECT '{t}', COUNT(*) FROM `{t}`" for t in tables)
        counts.update({f"{service}.{name}": int(n) for name, n in e2e_http.sql(service, union)})
    return counts


def fresh_entity_no(ops):
    """选一个 ledger 账簿 / 日历、account 入账流、billing 事实都未使用的 entityNo（ZE + 三位数字）。"""
    used = {r[0] for r in e2e_http.sql("ledger", "SELECT entity_id FROM t_ledger_book UNION SELECT entity_id FROM t_fiscal_calendar")}
    used |= {r[0] for r in e2e_http.sql("account", "SELECT entity_id FROM t_account_posting_stream UNION SELECT entity_id FROM t_owner_account")}
    used |= {r[0] for r in e2e_http.sql("billing", "SELECT DISTINCT entity_no FROM t_economic_fact")}
    for n in range(int(RUN[:4], 16) % 800 + 100, 1000):
        candidate = f"ZE{n:03d}"
        if candidate in used:
            continue
        if ops.get(f"/finance/ledger-books/v1?entityType=OPERATING_ENTITY&entityId={candidate}&page=1&size=10")["total"] == 0:
            return candidate
    raise SystemExit("找不到未使用的 entityNo")


def random_id(prefix="88"):
    return prefix + str(uuid.uuid4().int)[:16]


def main():
    s = e2e_http.secrets()
    baseline = table_counts()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    actor = jwt_subject(ops.token)
    post = lambda path, body=None, extra=None: raw("POST", path, body, ops.token, extra)
    get = lambda path: status_of(lambda: ops.get(path))
    record = {"run": RUN, "startedAt": datetime.now(timezone.utc).isoformat(), "baseline": baseline, "factIds": []}
    record_path = RUNTIME / f"subledger-acceptance-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
    RUNTIME.mkdir(parents=True, exist_ok=True)

    def save():
        record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))

    save()

    # ---------- S1 基础资料：新 OE 日历、当月 OPEN 期间、OE / USD 账簿 ----------
    check("AC-18", "Gateway 已加载明细账路由：R19 经运营入口可访问（缺参 400 而非 404）",
          wait_until(lambda: get("/finance/owner-accounts/v1?entityType=OPERATING_ENTITY&entityId=AE001")[0] == 400, 60))
    navigation = json.dumps(ops.get("/authorization/v1/navigation"), ensure_ascii=False)
    check("AC-17", "导航含 OPERATIONS_FINANCE_SUBLEDGER（明细账查询）", '"OPERATIONS_FINANCE_SUBLEDGER"' in navigation)

    entity_no = fresh_entity_no(ops)
    client_id = random_id()
    record.update(entityNo=entity_no, clientId=client_id)
    save()
    calendar = {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "calendarCode": f"E2E-SUB-{RUN}", "timezone": "Asia/Dubai",
                "yearStartMonth": 1}
    status, created = post("/finance/fiscal-calendars/v1", calendar)
    calendar_id = created.get("fiscalCalendarId")
    record["fiscalCalendarId"] = calendar_id
    save()
    check("S1", f"新建日历 201（{entity_no}，{status}）", status == 201 and calendar_id)
    today = datetime.now(timezone(timedelta(hours=4))).date()
    first = today.replace(day=1)
    second = (first + timedelta(days=32)).replace(day=1)
    status, period = post(f"/finance/fiscal-calendars/v1/{calendar_id}/periods",
                          {"fiscalYear": first.year, "periodNo": first.month, "startDate": first.isoformat(),
                           "endDate": (second - timedelta(days=1)).isoformat()})
    period_id = period.get("accountingPeriodId")
    record["accountingPeriodIds"] = [period_id]
    save()
    status_open, opened = post(f"/finance/accounting-periods/v1/{period_id}/open")
    check("S1", f"当月期间 201 并开放为 OPEN（{status}/{status_open}）", status == 201 and opened.get("status") == "OPEN")
    status, created = post("/finance/ledger-books/v1", {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "currency": "USD",
                                                        "chartCode": "STANDARD"})
    book_id = created.get("ledgerBookId")
    record["ledgerBookId"] = book_id
    save()
    check("S1", f"新建 OE / USD 账簿 201（{status}）", status == 201 and book_id)

    accounts_path = f"/finance/ledger-books/v1/{book_id}/accounts"
    channel_owner = random_id("66")
    status, channel_account = post(accounts_path, {"accountCode": "CHANNEL_UNSETTLED_RECEIVABLE", "ownerType": "CHANNEL_ACCOUNT",
                                                   "ownerId": channel_owner, "balanceBucket": "NONE"})
    check("AC-14", f"平台类 Owner（CHANNEL_ACCOUNT / NONE）新建账户 201（{status}）", status == 201 and channel_account.get("accountId"))
    status, body = post(accounts_path, {"accountCode": "CLIENT_PAYABLE", "ownerType": "CLIENT", "ownerId": client_id,
                                        "balanceBucket": "PENDING"})
    check("AC-14", f"R11 Owner 类型 CLIENT 被 BFF 拒绝（{status} {body.get('code')}）",
          status in (400, 422) and body.get("code") in ("VALIDATION_ERROR", "INVALID_REQUEST", "ACCOUNT_IDENTITY_INVALID"))
    status, body = post(accounts_path, {"accountCode": "PARTNER_PAYABLE", "ownerType": "LOCAL_PARTNER", "ownerId": client_id,
                                        "balanceBucket": "NONE"})
    check("AC-14", f"R11 Owner 类型 LOCAL_PARTNER 被 BFF 拒绝（{status} {body.get('code')}）",
          status in (400, 422) and body.get("code") in ("VALIDATION_ERROR", "INVALID_REQUEST", "ACCOUNT_IDENTITY_INVALID"))
    status, body = post(accounts_path, {"accountCode": "CLIENT_PAYABLE", "ownerType": "OPERATING_ENTITY", "ownerId": entity_no,
                                        "balanceBucket": "PENDING"})
    check("AC-14", "控制科目用非 NONE 余额桶 → ACCOUNT_IDENTITY_INVALID", business_error(status, body, "ACCOUNT_IDENTITY_INVALID"))
    status, body = post(accounts_path, {"accountCode": "CLIENT_PAYABLE", "ownerType": "OPERATING_ENTITY", "ownerId": "ZZ998",
                                        "balanceBucket": "NONE"})
    check("AC-14", "控制科目 Owner 不是账簿主体 → ACCOUNT_IDENTITY_INVALID", business_error(status, body, "ACCOUNT_IDENTITY_INVALID"))
    code, body = get(f"{accounts_path}?ownerType=CLIENT&page=1&size=10")
    check("AC-14", f"R10 按 ownerType=CLIENT 查询被 BFF 400 拒绝（{code} {body.get('code')}）", code == 400)

    # ---------- S2 链路：billing 登记并批准 MANUAL_ADJUSTMENT 100.00 ----------
    maker, checker = random_id("77"), random_id("78")
    record.update(makerId=maker, checkerId=checker)

    def submit_fact(amount, reference, supersedes=None):
        body = {"factType": "MANUAL_ADJUSTMENT", "sourceSystem": "OPERATIONS", "sourceReference": reference, "clientId": client_id,
                "entityNo": entity_no, "currency": "USD", "makerId": maker,
                "lines": [{"feeTypeCode": "CLIENT_PRINCIPAL", "payerRole": "OPERATING_ENTITY", "payerId": entity_no,
                           "payeeRole": "CLIENT", "payeeId": client_id, "amount": amount, "direction": "CHARGE"}]}
        if supersedes:
            body["supersedesFactId"] = supersedes
        status_submit, fact = private("billing", "POST", "/billing/v1/economic-facts", body)
        if fact.get("factId"):
            record["factIds"].append(fact["factId"])
            save()
        status_approve, approved = private("billing", "POST", f"/billing/v1/economic-facts/{fact.get('factId')}/approve",
                                           {"checkerId": checker})
        return status_submit, status_approve, fact, approved

    s_submit, s_approve, fact1, approved1 = submit_fact("100.00", f"E2E-SUB-{RUN}-1")
    fact1_id = fact1.get("factId")
    check("S2", f"billing 登记 201 并批准 200 为 APPROVED（{s_submit}/{s_approve} {approved1.get('status')}）",
          s_submit == 201 and s_approve == 200 and approved1.get("status") == "APPROVED")

    owner_query = f"/finance/owner-accounts/v1?entityType=OPERATING_ENTITY&entityId={entity_no}&currency=USD"
    payable_sql = (f"SELECT net_balance FROM t_owner_account WHERE entity_type='OPERATING_ENTITY' AND entity_id='{entity_no}' "
                   f"AND currency='USD' AND account_code='CLIENT_PAYABLE' AND owner_type='CLIENT' AND owner_id='{client_id}' "
                   "AND balance_bucket='PENDING'")
    check("AC-01", "account 消费事实：CLIENT_PAYABLE / CLIENT / PENDING 净额 100.00 出现",
          wait_until(lambda: Decimal(scalar("account", payable_sql) or "0") == Decimal("100")))
    instruction_sql = (f"SELECT COUNT(*) FROM t_accounting_instruction WHERE ledger_book_id={int(book_id or 0)} "
                       "AND origin_type='ACCOUNT_POSTING'")
    check("AC-03", "ledger 消费 AccountPosted：本账簿出现 1 条 ACCOUNT_POSTING 指令",
          wait_until(lambda: int(scalar("ledger", instruction_sql) or 0) == 1))

    code, page = get(f"{owner_query}&ownerType=CLIENT&ownerId={client_id}&page=1&size=10")
    items = page.get("items") or []
    owner_account = items[0] if items else {}
    record["ownerAccountId"] = owner_account.get("accountId")
    save()
    check("AC-17", f"R19 列出 1 个 Owner 账户：CLIENT_PAYABLE / CLIENT / PENDING、ACTIVE、贷 100.00、净 100.00（{code}）",
          code == 200 and page.get("total") == 1 and owner_account.get("accountCode") == "CLIENT_PAYABLE"
          and owner_account.get("balanceBucket") == "PENDING" and owner_account.get("accountStatus") == "ACTIVE"
          and money(owner_account.get("creditTotal")) == Decimal("100") and money(owner_account.get("netBalance")) == Decimal("100"))
    code, body = get(f"{owner_query}&ownerType=OPERATING_ENTITY&page=1&size=10")
    check("AC-17", f"R19 ownerType 不在明细账三值内 → 400（{code}）", code == 400)

    entries_path = f"/finance/owner-accounts/v1/{owner_account.get('accountId')}/entries?page=1&size=20"
    code, entries = get(entries_path)
    entry1 = (entries.get("items") or [{}])[0]
    check("AC-17", f"R20 流水 1 行：贷 100.00、批次序号 1、事实 {fact1_id} v1、MANUAL_ADJUSTMENT（{code}）",
          code == 200 and entries.get("total") == 1 and entry1.get("entrySide") == "CREDIT" and money(entry1.get("amount")) == Decimal("100")
          and entry1.get("postingSequence") == "1" and entry1.get("factId") == fact1_id and entry1.get("factVersion") == "1"
          and entry1.get("sourceType") == "ECONOMIC_FACT" and entry1.get("factType") == "MANUAL_ADJUSTMENT")
    posting1_id = entry1.get("postingId")
    record["postingIds"] = [posting1_id]
    save()

    published = wait_until(lambda: scalar("account", f"SELECT published_time IS NOT NULL FROM t_account_posting WHERE id={int(posting1_id or 0)}") == "1", 60)
    code, posting1 = get(f"/finance/account-postings/v1/{posting1_id}")
    check("AC-03", f"R21 批次详情 sourceType=ECONOMIC_FACT、APPLIED、publishedTime 非空、1 行流水（{code}，Broker 确认 {published}）",
          code == 200 and posting1.get("sourceType") == "ECONOMIC_FACT" and posting1.get("publishedTime")
          and posting1.get("factApplyStatus") == "APPLIED" and len(posting1.get("entries") or []) == 1
          and posting1["entries"][0].get("entryId") == entry1.get("entryId"))

    status, progress = private("account", "GET", f"/account-postings/v1/progress?entityType=OPERATING_ENTITY&entityId={entity_no}&currency=USD")
    streams = progress.get("streams") or []
    check("AC-01", f"account 进度 appliedFactCount=1、maxAppliedFactId=事实、流 shard 0 序号 1（{status}）",
          status == 200 and str(progress.get("appliedFactCount")) == "1" and progress.get("maxAppliedFactId") == fact1_id
          and len(streams) == 1 and str(streams[0].get("shardNo")) == "0" and str(streams[0].get("maxPostingSequence")) == "1")

    code, page = get(f"{accounts_path}?accountCode=CLIENT_PAYABLE&page=1&size=10")
    control = (page.get("items") or [{}])[0]
    record["controlAccountId"] = control.get("accountId")
    save()
    check("AC-04", f"R10 出现 CLIENT_PAYABLE 控制账户：Owner OPERATING_ENTITY {entity_no}、桶 NONE、净 100.00（{code}）",
          code == 200 and page.get("total") == 1 and control.get("ownerType") == "OPERATING_ENTITY" and control.get("ownerId") == entity_no
          and control.get("balanceBucket") == "NONE" and money(control.get("netBalance")) == Decimal("100"))
    code, lines = get(f"{accounts_path}/{control.get('accountId')}/journal-lines?page=1&size=20")
    line1 = (lines.get("items") or [{}])[0]
    check("AC-15", f"R12 流水 originType=ACCOUNT_POSTING、accountPostingId=批次、序号 1（{code}）",
          code == 200 and lines.get("total") == 1 and line1.get("originType") == "ACCOUNT_POSTING"
          and line1.get("accountPostingId") == posting1_id and line1.get("accountPostingSequence") == "1")
    journal1_id = line1.get("journalEntryId")
    code, detail = get(f"/finance/ledger-books/v1/{book_id}/journal-entries/{journal1_id}")
    detail_lines = detail.get("lines") or []
    total = lambda side, rows: sum((money(l.get("amount")) for l in rows if l.get("entrySide") == side), Decimal("0"))
    control_lines = [l for l in detail_lines if l.get("accountCode") == "CLIENT_PAYABLE"]
    rule_lines = [l for l in detail_lines if l.get("accountCode") != "CLIENT_PAYABLE"]
    check("AC-15", f"R13 分录详情 ACCOUNT_POSTING、sourceSystem=account、sourceEventKey=批次 ID（{code}）",
          code == 200 and detail.get("originType") == "ACCOUNT_POSTING" and detail.get("sourceSystem") == "account"
          and detail.get("sourceEventKey") == posting1_id and detail.get("accountPostingId") == posting1_id)
    check("AC-03", f"R13 控制科目行 accountEntryId = R20 流水 ID（{[l.get('accountEntryId') for l in control_lines]}）",
          len(control_lines) == 1 and control_lines[0].get("accountEntryId") == entry1.get("entryId")
          and control_lines[0].get("entrySide") == "CREDIT" and money(control_lines[0].get("amount")) == Decimal("100"))
    check("AC-03", f"非 Owner 行按规则推导（RECON_ADJUSTMENT_EXPENSE 借 100.00）且无 accountEntryId（{[l.get('accountCode') for l in rule_lines]}）",
          len(rule_lines) == 1 and rule_lines[0].get("accountCode") == "RECON_ADJUSTMENT_EXPENSE" and rule_lines[0].get("entrySide") == "DEBIT"
          and not rule_lines[0].get("accountEntryId"))
    check("AC-03", f"分录借贷平衡（{total('DEBIT', detail_lines)} / {total('CREDIT', detail_lines)}）",
          detail_lines and total("DEBIT", detail_lines) == total("CREDIT", detail_lines) == Decimal("100"))

    code, balance = get(f"/finance/balances/v1/clients/{client_id}?entityNo={entity_no}&currency=USD")
    check("AC-16", f"R17 Client 余额 pending 100.00、其余桶 0（{code}）",
          code == 200 and money(balance.get("pendingAmount")) == Decimal("100")
          and all(money(balance.get(k)) == 0 for k in ("availableAmount", "lockedAmount", "securityDepositAmount", "recoveryReceivableAmount")))
    code, body = get(f"/finance/balances/v1/clients/{random_id()}?entityNo={entity_no}&currency=USD")
    check("AC-16", f"未知 Owner → 400 OWNER_ACCOUNT_NOT_FOUND（{body.get('detail')}）", business_error(code, body, "OWNER_ACCOUNT_NOT_FOUND"))
    code, body = get(f"/finance/balances/v1/partners/{client_id}?entityNo={entity_no}&currency=USD")
    check("AC-16", f"同一 ID 作为 Partner 无账户 → 400 OWNER_ACCOUNT_NOT_FOUND（{code}）", business_error(code, body, "OWNER_ACCOUNT_NOT_FOUND"))

    # ---------- S3 第二笔：替代事实 80.00 ----------
    s_submit, s_approve, fact2, approved2 = submit_fact("80.00", f"E2E-SUB-{RUN}-2", fact1_id)
    fact2_id = fact2.get("factId")
    check("S3", f"billing 替代登记（supersedesFactId={fact1_id}）201 并批准 200（{s_submit}/{s_approve} {approved2.get('status')}）",
          s_submit == 201 and s_approve == 200 and approved2.get("status") == "APPROVED" and fact2.get("supersedesFactId") == fact1_id)
    check("AC-02", "account 替代后 CLIENT_PAYABLE / PENDING 净额 80.00",
          wait_until(lambda: Decimal(scalar("account", payable_sql) or "0") == Decimal("80")))
    check("AC-03", "ledger 第 2 条 ACCOUNT_POSTING 指令", wait_until(lambda: int(scalar("ledger", instruction_sql) or 0) == 2))
    code, entries = get(entries_path)
    rows = entries.get("items") or []
    reversal = next((r for r in rows if r.get("reversesEntryId")), {})
    fresh = next((r for r in rows if r.get("postingSequence") == "2" and not r.get("reversesEntryId")), {})
    check("AC-02", f"R20 流水 3 行：批次 2 含冲回行（借 100.00，reversesEntryId=原流水）与新行（贷 80.00）（{code}，{entries.get('total')}）",
          code == 200 and entries.get("total") == 3 and reversal.get("reversesEntryId") == entry1.get("entryId")
          and reversal.get("entrySide") == "DEBIT" and money(reversal.get("amount")) == Decimal("100") and reversal.get("postingSequence") == "2"
          and fresh.get("entrySide") == "CREDIT" and money(fresh.get("amount")) == Decimal("80") and fresh.get("factId") == fact2_id)
    posting2_id = fresh.get("postingId")
    record["postingIds"].append(posting2_id)
    save()
    code, posting2 = get(f"/finance/account-postings/v1/{posting2_id}")
    check("AC-02", f"R21 批次 2：替代事实 {fact2_id} v1、supersedesFactId、2 行流水（{code}）",
          code == 200 and posting2.get("factId") == fact2_id and posting2.get("supersedesFactId") == fact1_id
          and posting2.get("postingSequence") == "2" and len(posting2.get("entries") or []) == 2)
    code, page = get(f"{owner_query}&ownerType=CLIENT&ownerId={client_id}&page=1&size=10")
    acc = (page.get("items") or [{}])[0]
    check("AC-02", f"R19 Owner 账户借 100.00 / 贷 180.00 / 净 80.00（{acc.get('debitTotal')} / {acc.get('creditTotal')} / {acc.get('netBalance')}）",
          money(acc.get("debitTotal")) == Decimal("100") and money(acc.get("creditTotal")) == Decimal("180")
          and money(acc.get("netBalance")) == Decimal("80"))
    code, page = get(f"{accounts_path}?accountCode=CLIENT_PAYABLE&page=1&size=10")
    control = (page.get("items") or [{}])[0]
    owner_total = sum((Decimal(r[0]) for r in e2e_http.sql("account", "SELECT net_balance FROM t_owner_account WHERE entity_type='OPERATING_ENTITY' "
                                                                     f"AND entity_id='{entity_no}' AND currency='USD' AND account_code='CLIENT_PAYABLE'")), Decimal("0"))
    check("AC-04", f"控制账户净额 = account 该主体 + 币种 + 科目全部 Owner 账户合计（{control.get('netBalance')} = {owner_total}）",
          money(control.get("netBalance")) == owner_total == Decimal("80"))
    code, lines = get(f"{accounts_path}/{control.get('accountId')}/journal-lines?page=1&size=20")
    journal2_id = next((l.get("journalEntryId") for l in lines.get("items") or [] if l.get("accountPostingSequence") == "2"), None)
    code, detail2 = get(f"/finance/ledger-books/v1/{book_id}/journal-entries/{journal2_id}")
    lines2 = detail2.get("lines") or []
    control2 = {l.get("accountEntryId"): l for l in lines2 if l.get("accountCode") == "CLIENT_PAYABLE"}
    recon2 = sorted((l.get("entrySide"), money(l.get("amount"))) for l in lines2 if l.get("accountCode") == "RECON_ADJUSTMENT_EXPENSE")
    check("AC-02", "R13 分录 2：控制科目行逐行带 accountEntryId（冲回行借 100.00、新行贷 80.00）",
          set(control2) == {reversal.get("entryId"), fresh.get("entryId")}
          and control2.get(reversal.get("entryId"), {}).get("entrySide") == "DEBIT"
          and control2.get(fresh.get("entryId"), {}).get("entrySide") == "CREDIT")
    check("AC-02", f"R13 分录 2：规则行净额替代（RECON_ADJUSTMENT_EXPENSE {recon2}）且借贷平衡 "
                   f"（{total('DEBIT', lines2)} / {total('CREDIT', lines2)}）",
          recon2 == [("CREDIT", Decimal("100")), ("DEBIT", Decimal("80"))] and total("DEBIT", lines2) == total("CREDIT", lines2))
    postings = e2e_http.sql("account", "SELECT posting_sequence, source_type, fact_id, entry_count FROM t_account_posting WHERE "
                                       f"entity_type='OPERATING_ENTITY' AND entity_id='{entity_no}' AND currency='USD' AND shard_no=0 ORDER BY posting_sequence")
    check("AC-01", f"每条已批准事实恰好一条批次，序号连续（{[(p[0], p[2][-4:], p[3]) for p in postings]}）",
          [p[0] for p in postings] == ["1", "2"] and [p[2] for p in postings] == [fact1_id, fact2_id])
    published = wait_until(lambda: scalar("account", f"SELECT COUNT(*) FROM t_account_posting WHERE entity_id='{entity_no}' AND published_time IS NULL") == "0", 60)
    check("AC-03", "两条批次的 AccountPosted 均已获 Broker 确认（published_time 非空）", published)

    # ---------- S4 ledger 追平 ----------
    gateway_headers = {"X-Gateway-Actor-Subject": actor, "X-Gateway-Session-Id": random_id("99")}
    status, caught = private("ledger", "POST", "/posting-streams/v1/catch-up",
                             {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "currency": "USD", "shardNo": 0}, gateway_headers)
    check("AC-08", f"catch-up 200：caughtUp=true、processedCount=0、lastSequence=2（{status} {caught}）",
          status == 200 and caught.get("caughtUp") is True and str(caught.get("processedCount")) == "0" and str(caught.get("lastSequence")) == "2")
    status, body = private("ledger", "POST", "/posting-streams/v1/catch-up",
                           {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "currency": "USD", "shardNo": 0})
    check("AC-08", f"catch-up 缺操作人 Header → 400（{status}）", status == 400)
    ledger_version = scalar("ledger", f"SELECT last_version FROM t_consumed_aggregate_version WHERE aggregate_type='ACCOUNT_POSTING' "
                                      f"AND aggregate_id='OE.{entity_no}.USD.0'")
    check("AC-07", f"ledger 流进度 OE.{entity_no}.USD.0 = account 最大序号 2（{ledger_version}）", ledger_version == "2")

    # ---------- 附加：AC-05 桶移动拒绝、AC-10 人工调整拒绝控制科目、AC-11 重建、AC-12 删除的接口 ----------
    status, body = private("account", "POST", "/balance-movements/v1", {
        "sourceType": "PAYOUT_LOCK", "sourceRef": f"E2E-SUB-{RUN}-LOCK", "entityType": "OPERATING_ENTITY", "entityId": entity_no,
        "currency": "USD", "accountCode": "CLIENT_PAYABLE", "ownerType": "CLIENT", "ownerId": client_id, "fromBucket": "AVAILABLE",
        "toBucket": "LOCKED", "amount": {"amount": "10.00", "currency": "USD"}, "operatorId": actor})
    check("AC-05", f"AVAILABLE 无余额时锁定被拒（{status} {body.get('code')}）",
          status == 400 and body.get("code") in ("ACCOUNT_NOT_FOUND", "BALANCE_INSUFFICIENT"))
    code, page = get(f"{accounts_path}?accountCode=RECON_ADJUSTMENT_EXPENSE&page=1&size=10")
    recon_account = (page.get("items") or [{}])[0]
    status, body = private("ledger", "POST", "/manual-adjustments/v1", {
        "ledgerBookId": book_id, "accountingPeriodId": period_id, "currency": "USD", "reasonCode": "E2E_ACCEPTANCE", "makerId": maker,
        "lines": [{"accountId": control.get("accountId"), "entrySide": "DEBIT", "amount": "1.00", "lineDescription": f"验收 {RUN} 控制科目"},
                  {"accountId": recon_account.get("accountId"), "entrySide": "CREDIT", "amount": "1.00", "lineDescription": f"验收 {RUN} 对方"}]})
    check("AC-10", f"人工调整涉及 CLIENT_PAYABLE 控制科目 → 400 OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN（{status} {body.get('code')}）",
          business_error(status, body, "OWNER_ACCOUNT_ADJUSTMENT_FORBIDDEN"))
    before = e2e_http.sql("account", "SELECT id, net_balance, balance_version FROM t_owner_account WHERE "
                                     f"entity_type='OPERATING_ENTITY' AND entity_id='{entity_no}' ORDER BY id")
    status, rebuilt = private("account", "POST", "/account-postings/v1/streams/rebuild",
                              {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "currency": "USD", "operatorId": actor})
    after = e2e_http.sql("account", "SELECT id, net_balance, balance_version FROM t_owner_account WHERE "
                                    f"entity_type='OPERATING_ENTITY' AND entity_id='{entity_no}' ORDER BY id")
    check("AC-11", f"按流重建 200：账户 1、流水 3、最大序号 2；余额不变、balance_version 加一（{status} {rebuilt}）",
          status == 200 and str(rebuilt.get("accountCount")) == "1" and str(rebuilt.get("entryCount")) == "3"
          and str(rebuilt.get("maxPostingSequence")) == "2" and len(before) == len(after) == 1
          and before[0][1] == after[0][1] and int(after[0][2]) == int(before[0][2]) + 1)
    status, _ = private("ledger", "GET", f"/ledger/v1/books/{book_id}/account-totals")
    status2, _ = private("account", "GET", f"/balances/v1/books/{book_id}/consistency")
    check("AC-12", f"ledger account-totals 与 account consistency 接口已删除（{status} / {status2}）", status == 404 and status2 == 404)

    # ---------- S5 关账检查：发起关账（Maker），读步骤结果，再驳回（Checker）使期间回到 OPEN ----------
    status, close = private("ledger", "POST", f"/accounting-periods/v1/{period_id}/closes", {"makerId": maker})
    close_no = close.get("closeNo")
    record["periodClose"] = {"periodId": period_id, "closeNo": close_no, "status": close.get("status")}
    save()
    status_detail, close_detail = private("ledger", "GET", f"/accounting-periods/v1/{period_id}/closes/{close_no}")
    steps = {s.get("stepType"): s.get("status") for s in close_detail.get("steps") or []}
    record["periodClose"]["steps"] = steps
    save()
    check("AC-07", f"发起关账 201（{status} {close.get('status')}，失败步骤 {close.get('failedStepType')}）",
          status == 201 and close.get("status") in ("PENDING_CHECK", "FAILED"))
    check("AC-07", f"CHECK_ACCOUNT_POSTING_PROGRESS = SUCCEEDED（{steps.get('CHECK_ACCOUNT_POSTING_PROGRESS')}；全部步骤 {steps}）",
          steps.get("CHECK_ACCOUNT_POSTING_PROGRESS") == "SUCCEEDED" and "RECONCILE_ACCOUNT_BALANCES" not in steps)
    if close.get("status") == "PENDING_CHECK":
        status, rejected = private("ledger", "POST", f"/accounting-periods/v1/{period_id}/closes/{close_no}/reject",
                                   {"checkerId": checker, "approvedCloseHash": close_detail.get("closeHash")})
        record["periodClose"]["rejected"] = rejected.get("status")
        save()
        check("AC-07", f"Checker 驳回 → REJECTED，期间回到 OPEN（{status} {rejected.get('status')}）",
              status == 200 and rejected.get("status") == "REJECTED"
              and scalar("ledger", f"SELECT status FROM t_accounting_period WHERE id={int(period_id)}") == "OPEN")

    # ---------- S6 权限 ----------
    status, _ = raw("GET", f"{owner_query}&page=1&size=10")
    check("S6", f"无会话访问 R19 → 401（{status}）", status == 401)
    bound = e2e_http.platform_sql("SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000099401 AND 900000000000099403 "
                                  f"AND authorization_mode='HUMAN_PERMISSION' AND permission_id={FINANCE_ACCOUNTING_READ} AND status='ACTIVE'")
    check("S6", f"R19～R21 均为 HUMAN_PERMISSION + FINANCE_ACCOUNTING_READ 且 ACTIVE（{bound[0][0]}/3）", bound[0][0] == "3")

    # ---------- AC-18 注册数据 ----------
    tables = [("t_console_resource", "resource_id", 1), ("t_gateway_route", "route_id", 3), ("t_gateway_route_header", "route_header_id", 6),
              ("t_gateway_route_query_param", "route_query_param_id", 11), ("t_gateway_guard_rule", "guard_rule_id", 18)]
    in_block = lambda column: f"{column} BETWEEN 900000000000099000 AND 900000000000099999"
    total_rows = 0
    for table, column, expected in tables:
        count = int(e2e_http.platform_sql(f"SELECT COUNT(*) FROM {table} WHERE {in_block(column)}")[0][0])
        total_rows += count
        check("AC-18", f"块 99 {table} {count}/{expected}", count == expected)
    check("AC-18", f"块 99 注册行合计 {total_rows}/39", total_rows == 39)
    inactive = e2e_http.platform_sql(f"SELECT COUNT(*) FROM t_gateway_route WHERE {in_block('route_id')} AND status<>'ACTIVE' UNION ALL "
                                     f"SELECT COUNT(*) FROM t_console_resource WHERE {in_block('resource_id')} AND status<>'ACTIVE'")
    check("AC-18", "本机激活后 3 条路由与 1 个菜单均为 ACTIVE", all(r[0] == "0" for r in inactive))

    record["finishedAt"] = datetime.now(timezone.utc).isoformat()
    record["results"] = [{"ac": a, "name": n, "ok": ok} for a, n, ok in results]
    save()
    passed = sum(1 for r in results if r[2])
    print(f"\n运行记录：{record_path.relative_to(ROOT)}")
    print(f"{passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


def latest_record():
    records = sorted(RUNTIME.glob("subledger-acceptance-*.json"))
    if not records:
        raise SystemExit("没有运行记录")
    return records[-1], json.loads(records[-1].read_text())


def cleanup():
    path, record = latest_record()
    numeric = lambda value: str(int(value)) if value and re.fullmatch(r"[1-9][0-9]{0,18}", str(value)) else "NULL"
    entity_no = record.get("entityNo") or ""
    if not re.fullmatch(r"[A-Z]{2}[0-9]{3}", entity_no):
        raise SystemExit("运行记录缺少 entityNo")
    facts = [numeric(f) for f in record.get("factIds") or [] if numeric(f) != "NULL"]
    variables = {"book": numeric(record.get("ledgerBookId")), "calendar": numeric(record.get("fiscalCalendarId"))}
    prefix = "".join(f"SET @{k} = {v};\n" for k, v in variables.items()) + f"SET @entity_no = '{entity_no}';\n"
    fact_list = ",".join(facts) or "NULL"
    text = CLEANUP_SQL.read_text().replace("/*FACT_IDS*/", fact_list)
    sections = dict(re.findall(r"-- @(\w+)\n(.*?)(?=\n-- @\w+\n|\Z)", text, re.S))
    for service in ("ledger", "account", "billing"):
        e2e_http.sql(service, prefix + sections[service])
    after = table_counts()
    diff = {k: (record["baseline"].get(k), v) for k, v in after.items() if record["baseline"].get(k) != v}
    print(f"清理 {path.name}：book={variables['book']} calendar={variables['calendar']} entityNo={entity_no} facts={fact_list}")
    if diff:
        print(f"[失败] 与运行前基线不同：{diff}")
        return 1
    print(f"[通过] billing / account / ledger 共 {len(after)} 张表行数回到运行前基线")
    return 0


if __name__ == "__main__":
    sys.exit(cleanup() if "--cleanup" in sys.argv else main())
