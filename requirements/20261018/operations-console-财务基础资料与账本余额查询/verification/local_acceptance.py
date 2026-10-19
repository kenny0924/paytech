"""阶段 5 接口验收：经运营入口（Gateway → Operations BFF → ledger / account）走 AC-01～AC-09。

前置（本机，默认 E2E_TARGET=local）：本机 V2 链路已由 run-service.sh 启动；mor_platform_v2e2e 已执行 Finance 注册块 97/98
与 activation-local.sql；mor_ledger_v2e2e 已有 STANDARD 科目表与 USD。入口 https://127.0.0.1:18444；AC-08 操作人日志读
local-environment/logs/ledger.log。

前置（E2E_TARGET=test，mor-test）：入口 http://localhost:28080；库为 mor_platform / mor_ledger / mor_account（127.0.0.1:23306，
口令读 test-environment/.env.test）；mor_platform 已执行 Finance 注册块 97/98，mor_ledger 已有 STANDARD 科目表与 USD；
ledger 私网接口经 e2e_http 的一次性探针容器调用；AC-08 操作人日志读容器日志
（docker logs --since <伪造请求前的时间> mor-test-ledger-service-1，E2E_LEDGER_CONTAINER 可改）。

两种目标都不打印 Token、口令与密钥；连接目标由 E2E_TARGET 决定（见 e2e_http.py）。

用法（E2E_TARGET 缺省为 local）：
  [E2E_TARGET=test] python3 -I local_acceptance.py             验收；运行记录写入 local-environment/e2e/runtime/finance-acceptance-<ts>.json
  [E2E_TARGET=test] python3 -I local_acceptance.py --cleanup   按最新运行记录执行 acceptance-cleanup-local.sql，并核对两库行数回到运行前基线
"""
import base64
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

RUNTIME = ROOT / "local-environment/e2e/runtime"
LEDGER_LOG = ROOT / "local-environment/logs/ledger.log"
# mor-test：ledger 日志只在容器标准输出 / 标准错误中
LEDGER_CONTAINER = os.environ.get("E2E_LEDGER_CONTAINER", "mor-test-ledger-service-1")
CLEANUP_SQL = HERE / "acceptance-cleanup-local.sql"
RUN = uuid.uuid4().hex[:6].upper()
CHINESE = re.compile(r"[一-鿿]")
results = []


def ledger_log_mark():
    """记录读日志的起点：本机为 ledger.log 当前字节数；test 为当前 UTC 时间（回拨 5 秒容忍宿主机与 Docker 虚拟机时钟偏差）。"""
    if e2e_http.TARGET == "test":
        return (datetime.now(timezone.utc) - timedelta(seconds=5)).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    return LEDGER_LOG.stat().st_size if LEDGER_LOG.exists() else 0


def ledger_log_since(mark):
    """读起点之后的 ledger 应用日志文本。"""
    if e2e_http.TARGET == "test":
        result = subprocess.run(["docker", "logs", "--since", mark, LEDGER_CONTAINER], capture_output=True, timeout=30)
        if result.returncode != 0:
            raise RuntimeError(f"读取 {LEDGER_CONTAINER} 日志失败：{result.stderr.decode(errors='ignore').strip()[:300]}")
        return (result.stdout + b"\n" + result.stderr).decode(errors="ignore")
    with LEDGER_LOG.open("rb") as handle:
        handle.seek(mark)
        return handle.read().decode(errors="ignore")


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
    """直接请求运营入口以读取状态码（创建 201）或附加自定义 Header；返回 (状态, 响应体)。"""
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


def business_error(status, body, code):
    """400 + 指定 code + 中文 detail。"""
    return status == 400 and body.get("code") == code and bool(CHINESE.search(body.get("detail") or ""))


def wait_until(probe, seconds=60):
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if probe():
            return True
        time.sleep(2)
    return False


def jwt_subject(token):
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))["sub"]


def table_counts():
    """两库所有业务表行数（基线与清理核对用）。"""
    counts = {}
    for service in ("ledger", "account"):
        database = e2e_http.DATABASES[service][0]
        tables = [r[0] for r in e2e_http.sql(service, f"SELECT table_name FROM information_schema.tables WHERE table_schema='{database}' "
                                                      "AND table_type='BASE TABLE' ORDER BY table_name")]
        union = " UNION ALL ".join(f"SELECT '{t}', COUNT(*) FROM `{t}`" for t in tables)
        counts.update({f"{service}.{name}": int(n) for name, n in e2e_http.sql(service, union)})
    return counts


def fresh_entity_no(ops):
    """选一个账簿与日历都未使用的 entityNo（[A-Z]{2}[0-9]{3}）。"""
    used = {r[0] for r in e2e_http.sql("ledger", "SELECT entity_id FROM t_ledger_book UNION SELECT entity_id FROM t_fiscal_calendar")}
    for n in range(int(RUN[:4], 16) % 900 + 100, 1000):
        candidate = f"ZE{n:03d}"
        if candidate in used:
            continue
        listed = ops.get(f"/finance/ledger-books/v1?entityType=OPERATING_ENTITY&entityId={candidate}&page=1&size=10")
        if listed["total"] == 0:
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
    record = {"run": RUN, "startedAt": datetime.now(timezone.utc).isoformat(), "baseline": baseline}
    record_path = RUNTIME / f"finance-acceptance-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
    RUNTIME.mkdir(parents=True, exist_ok=True)

    def save():
        record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))

    save()

    # AC-01 导航与 Gateway 快照
    check("AC-01", "Gateway 已加载财务路由，币种列表经运营入口 200", wait_until(lambda: get("/finance/currencies/v1")[0] == 200))
    navigation = json.dumps(ops.get("/authorization/v1/navigation"), ensure_ascii=False)
    codes = ["OPERATIONS_FINANCE", "OPERATIONS_FINANCE_SETUP", "OPERATIONS_FINANCE_LEDGER", "OPERATIONS_FINANCE_BALANCES",
             "OPERATIONS_FINANCE_SETUP_MANAGE"]
    check("AC-01", "导航含财务目录、三个页面与维护按钮", all(f'"{c}"' in navigation for c in codes))

    # AC-04 启用项
    _, charts = get("/finance/chart-of-accounts/v1")
    check("AC-04", "科目表列表含 ACTIVE 的 STANDARD", any(c["chartCode"] == "STANDARD" and c["status"] == "ACTIVE" for c in charts.get("items", [])))
    _, postable = get("/finance/chart-of-accounts/v1/STANDARD/accounts?postingAllowed=true")
    items = postable.get("items", [])
    check("AC-04", f"postingAllowed=true 只返回可过账科目（{len(items)} 个）", items and all(i["postingAllowed"] is True for i in items))
    _, currencies = get("/finance/currencies/v1?status=ACTIVE")
    check("AC-04", "ACTIVE 币种含 USD（2 位小数）", any(c["currency"] == "USD" and c["minorUnit"] == 2 for c in currencies.get("items", [])))
    code, body = get("/finance/chart-of-accounts/v1/NO_SUCH_CHART/accounts")
    check("AC-04", f"不存在的科目表返回 CHART_NOT_ACTIVE（{code}）", business_error(code, body, "CHART_NOT_ACTIVE"))

    # AC-02 财务主体
    entity_code = f"E2E-FIN-{RUN}"
    party = random_id()
    status, created = post("/finance/finance-entities/v1", {"entityCode": entity_code, "legalPartyId": party, "displayName": f"验收财务主体 {RUN}"})
    record["financeEntityId"] = created.get("financeEntityId")
    save()
    check("AC-02", f"新建财务主体 201（{status}）", status == 201 and re.fullmatch(r"[1-9][0-9]{0,18}", created.get("financeEntityId") or ""))
    _, listed = get("/finance/finance-entities/v1?status=ACTIVE&page=1&size=200")
    check("AC-02", "财务主体列表立即出现新记录且为 ACTIVE",
          any(i["financeEntityId"] == record["financeEntityId"] and i["status"] == "ACTIVE" for i in listed.get("items", [])))
    status, body = post("/finance/finance-entities/v1", {"entityCode": entity_code, "legalPartyId": random_id(), "displayName": "重复代码"})
    check("AC-02", f"重复代码 → FINANCE_ENTITY_ALREADY_EXISTS（{body.get('detail')}）", business_error(status, body, "FINANCE_ENTITY_ALREADY_EXISTS"))
    status, body = post("/finance/finance-entities/v1", {"entityCode": f"E2E-FIN-{RUN}-B", "legalPartyId": party, "displayName": "重复 Party"})
    check("AC-02", "Party 已映射 → FINANCE_ENTITY_ALREADY_EXISTS", business_error(status, body, "FINANCE_ENTITY_ALREADY_EXISTS"))

    # AC-02 日历（新 OE）
    entity_no = fresh_entity_no(ops)
    record["entityNo"] = entity_no
    calendar = {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "calendarCode": f"E2E-{RUN}", "timezone": "Asia/Dubai",
                "yearStartMonth": 1}
    status, created = post("/finance/fiscal-calendars/v1", calendar)
    calendar_id = created.get("fiscalCalendarId")
    record["fiscalCalendarId"] = calendar_id
    save()
    check("AC-02", f"新建日历 201（{entity_no}）", status == 201 and calendar_id)
    _, listed = get(f"/finance/fiscal-calendars/v1?entityType=OPERATING_ENTITY&entityId={entity_no}&page=1&size=10")
    check("AC-02", "日历按主体筛选立即可见", listed.get("total") == 1 and listed["items"][0]["fiscalCalendarId"] == calendar_id)
    status, body = post("/finance/fiscal-calendars/v1", {**calendar, "calendarCode": f"E2E-{RUN}-B"})
    check("AC-02", "同主体再建日历 → FISCAL_CALENDAR_ALREADY_EXISTS", business_error(status, body, "FISCAL_CALENDAR_ALREADY_EXISTS"))

    # AC-02 期间：当月与下月，重叠与期号重复
    today = date.today()
    first = today.replace(day=1)
    second = (first + timedelta(days=32)).replace(day=1)
    third = (second + timedelta(days=32)).replace(day=1)
    periods_path = f"/finance/fiscal-calendars/v1/{calendar_id}/periods"
    period = lambda no, start, end: {"fiscalYear": first.year, "periodNo": no, "startDate": start.isoformat(), "endDate": end.isoformat()}
    status1, p1 = post(periods_path, period(first.month, first, second - timedelta(days=1)))
    status2, p2 = post(periods_path, period(first.month + 1, second, third - timedelta(days=1)))
    record["accountingPeriodIds"] = [p1.get("accountingPeriodId"), p2.get("accountingPeriodId")]
    save()
    check("AC-02", f"新建两个连续月期间 201（{status1}/{status2}）", status1 == 201 and status2 == 201)
    status, body = post(periods_path, period(90, first + timedelta(days=10), second + timedelta(days=10)))
    check("AC-02", "日期重叠 → ACCOUNTING_PERIOD_OVERLAP", business_error(status, body, "ACCOUNTING_PERIOD_OVERLAP"))
    status, body = post(periods_path, period(first.month, third + timedelta(days=40), third + timedelta(days=60)))
    check("AC-02", "同财年期号重复 → ACCOUNTING_PERIOD_NO_DUPLICATE", business_error(status, body, "ACCOUNTING_PERIOD_NO_DUPLICATE"))
    status, body = post(periods_path, period(91, second, first))
    check("AC-02", f"起止倒置 → 400 INVALID_REQUEST（{body.get('code')}）", status == 400 and body.get("code") == "INVALID_REQUEST")

    # AC-03 开放期间
    _, listed = get(f"{periods_path}?fiscalYear={first.year}&page=1&size=10")
    statuses = {i["accountingPeriodId"]: i["status"] for i in listed.get("items", [])}
    check("AC-03", "两个期间初始均为 FUTURE、按期号升序", list(statuses.values()) == ["FUTURE", "FUTURE"]
          and [i["periodNo"] for i in listed["items"]] == sorted(i["periodNo"] for i in listed["items"]))
    status, opened = post(f"/finance/accounting-periods/v1/{p1.get('accountingPeriodId')}/open")
    check("AC-03", f"开放第一个期间 → OPEN（{status}）", status == 200 and opened.get("status") == "OPEN")
    status, again = post(f"/finance/accounting-periods/v1/{p1.get('accountingPeriodId')}/open")
    check("AC-03", f"重复开放幂等返回 OPEN（{status} {again.get('code', '')}）", status == 200 and again.get("status") == "OPEN")
    _, listed = get(f"{periods_path}?page=1&size=10")
    statuses = {i["accountingPeriodId"]: i["status"] for i in listed.get("items", [])}
    check("AC-03", "第二个期间未开放前保持 FUTURE", statuses.get(p2.get("accountingPeriodId")) == "FUTURE"
          and statuses.get(p1.get("accountingPeriodId")) == "OPEN")
    code, body = get("/finance/fiscal-calendars/v1/1/periods?page=1&size=10")
    check("AC-03", "不存在的日历查期间 → FISCAL_CALENDAR_NOT_FOUND", business_error(code, body, "FISCAL_CALENDAR_NOT_FOUND"))

    # AC-02 账簿
    book = {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "currency": "USD", "chartCode": "STANDARD"}
    status, created = post("/finance/ledger-books/v1", book)
    book_id = created.get("ledgerBookId")
    record["ledgerBookId"] = book_id
    save()
    check("AC-02", "新建账簿 201", status == 201 and book_id)
    status, body = post("/finance/ledger-books/v1", book)
    check("AC-02", "同主体同币种 → LEDGER_BOOK_ALREADY_EXISTS", business_error(status, body, "LEDGER_BOOK_ALREADY_EXISTS"))
    status, body = post("/finance/ledger-books/v1", {**book, "currency": "XXX"})
    check("AC-02", "未启用币种 → CURRENCY_NOT_ACTIVE", business_error(status, body, "CURRENCY_NOT_ACTIVE"))
    status, body = post("/finance/ledger-books/v1", {**book, "chartCode": "NO_SUCH_CHART"})
    check("AC-02", "不存在的科目表 → CHART_NOT_ACTIVE", business_error(status, body, "CHART_NOT_ACTIVE"))

    # AC-02 账户；AC-08 浏览器伪造操作人 Header
    client_id = random_id()
    record["clientId"] = client_id
    accounts_path = f"/finance/ledger-books/v1/{book_id}/accounts"
    payable = lambda bucket: {"accountCode": "CLIENT_PAYABLE", "ownerType": "CLIENT", "ownerId": client_id, "balanceBucket": bucket}
    forged = {"X-Gateway-Actor-Subject": "999999999999999999", "X-Gateway-Session-Id": "999999999999999998"}
    log_mark = ledger_log_mark()
    status_available, available = post(accounts_path, payable("AVAILABLE"), forged)
    forged_rejected = status_available != 201
    if forged_rejected:
        # Gateway 拒绝未登记 Header 时，改用正常请求建账户，仍核对操作人日志
        status_available, available = post(accounts_path, payable("AVAILABLE"))
    status_pending, pending = post(accounts_path, payable("PENDING"))
    oe = lambda code: {"accountCode": code, "ownerType": "OPERATING_ENTITY", "ownerId": entity_no, "balanceBucket": "NONE"}
    status_debit, debit_account = post(accounts_path, oe("RECON_SUSPENSE_ASSET"))
    status_credit, credit_account = post(accounts_path, oe("UNIDENTIFIED_CASH"))
    record["accountIds"] = [a.get("accountId") for a in (available, pending, debit_account, credit_account)]
    save()
    check("AC-02", f"新建 4 个账户 201（CLIENT_PAYABLE AVAILABLE/PENDING，两个 OE NONE 账户）",
          [status_available, status_pending, status_debit, status_credit] == [201] * 4)
    status, body = post(accounts_path, payable("AVAILABLE"))
    check("AC-02", "重复账户 → ACCOUNT_ALREADY_EXISTS", business_error(status, body, "ACCOUNT_ALREADY_EXISTS"))
    status, body = post(accounts_path, {**oe("NO_SUCH_ACCOUNT_CODE")})
    check("AC-02", "科目表中不存在 / 不可过账的科目 → CHART_ACCOUNT_NOT_POSTABLE", business_error(status, body, "CHART_ACCOUNT_NOT_POSTABLE"))
    status, body = post(accounts_path, {**payable("NONE")})
    check("AC-02", "应付科目用 NONE 余额桶 → ACCOUNT_IDENTITY_INVALID", business_error(status, body, "ACCOUNT_IDENTITY_INVALID"))
    status, body = post(accounts_path, {**oe("RECON_SUSPENSE_ASSET"), "ownerType": "CLIENT", "ownerId": client_id})
    check("AC-02", "平台科目挂 CLIENT → ACCOUNT_IDENTITY_INVALID", business_error(status, body, "ACCOUNT_IDENTITY_INVALID"))
    status, body = post("/finance/ledger-books/v1/1/accounts", payable("LOCKED"))
    check("AC-02", "不存在的账簿建账户 → LEDGER_BOOK_NOT_FOUND", business_error(status, body, "LEDGER_BOOK_NOT_FOUND"))

    # AC-08 操作人 Header
    def operator_line():
        """ledger 应用日志中该账户的操作人行（日志异步落盘，轮询最多 15 秒；只读这一行）。"""
        text = ledger_log_since(log_mark)
        return next((l for l in text.splitlines() if "财务基础资料命令" in l and "CREATE_ACCOUNT" in l
                     and f"resourceId={available.get('accountId', '-')}" in l), "")

    wait_until(lambda: operator_line() != "", 15)
    line = operator_line()
    check("AC-08", f"{'Gateway 拒绝浏览器伪造的操作人 Header；' if forged_rejected else ''}ledger 日志 actor 为会话账号而非伪造值",
          f"actor={actor}" in line and "999999999999999999" not in line)
    status, _ = raw("POST", "/finance/finance-entities/v1", {"entityCode": f"E2E-FIN-{RUN}-X", "legalPartyId": random_id(), "displayName": "x"})
    check("AC-08", f"无会话的写请求 → 401（{status}）", status == 401)
    status, text = e2e_http.private_call("ledger", "POST", "/finance-entities/v1", {"Content-Type": "application/json"},
                                         {"entityCode": f"E2E-FIN-{RUN}-Y", "legalPartyId": random_id(), "displayName": "x"})
    check("AC-08", f"直连 ledger 缺操作人 Header → 400（{status}）", status == 400)
    status, text = e2e_http.private_call("ledger", "POST", f"/accounting-periods/v1/{p2.get('accountingPeriodId')}/open",
                                         {"X-Gateway-Actor-Subject": actor})
    check("AC-08", f"直连 ledger 缺会话 Header 开放期间 → 400（{status}）", status == 400)

    # AC-05 列表筛选与分页
    _, page = get(f"/finance/ledger-books/v1?entityType=OPERATING_ENTITY&entityId={entity_no}&currency=USD&page=1&size=10")
    check("AC-05", "账簿按主体类型 / 主体 / 币种筛选命中 1 本", page.get("total") == 1 and page["items"][0]["ledgerBookId"] == book_id)
    _, page = get(f"/finance/ledger-books/v1?entityType=FINANCE_ENTITY&entityId={entity_no}&page=1&size=10")
    check("AC-05", "主体类型不匹配时为空", page.get("total") == 0)
    code, body = get("/finance/ledger-books/v1?page=1&size=7")
    check("AC-05", f"size=7 → 400 INVALID_REQUEST（{body.get('detail')}）", code == 400 and body.get("code") == "INVALID_REQUEST")
    code, body = get("/finance/ledger-books/v1?page=1&size=10&foo=bar")
    check("AC-05", f"未登记的查询参数 → 400（{code} {body.get('code')}）", code == 400)
    _, page = get(f"{accounts_path}?page=1&size=10")
    check("AC-05", "账户分页共 4 个", page.get("total") == 4)
    _, page = get(f"{accounts_path}?ownerType=CLIENT&ownerId={client_id}&balanceBucket=PENDING&page=1&size=10")
    check("AC-05", "账户按核算对象 + 余额桶筛选命中 1 个", page.get("total") == 1 and page["items"][0]["accountId"] == pending.get("accountId"))
    _, page = get(f"{accounts_path}?accountCode=CLIENT_PAYABLE&page=1&size=20")
    check("AC-05", "账户按科目筛选命中 2 个", page.get("total") == 2)
    for size in (10, 20, 50, 100, 200):
        if get(f"{accounts_path}?page=1&size={size}")[0] != 200:
            check("AC-05", f"size={size} 被拒", False)
            break
    else:
        check("AC-05", "size 10/20/50/100/200 均可用", True)

    # AC-06 经 ledger 私网接口播种一笔已过账人工调整（只涉及平台侧 OE 账户）
    amount = "123.45"
    maker, checker = random_id("77"), random_id("78")
    submit = {"ledgerBookId": book_id, "accountingPeriodId": p1.get("accountingPeriodId"), "currency": "USD", "reasonCode": "E2E_ACCEPTANCE",
              "makerId": maker, "lines": [
                  {"accountId": debit_account.get("accountId"), "entrySide": "DEBIT", "amount": amount, "lineDescription": f"验收 {RUN} 借方"},
                  {"accountId": credit_account.get("accountId"), "entrySide": "CREDIT", "amount": amount, "lineDescription": f"验收 {RUN} 贷方"}]}
    status, text = e2e_http.private_call("ledger", "POST", "/manual-adjustments/v1", {}, submit)
    submitted = json.loads(text) if text.startswith("{") else {}
    record["manualAdjustmentId"] = submitted.get("manualAdjustmentId")
    save()
    check("AC-06", f"私网提交人工调整（{status}）", status in (200, 201) and submitted.get("contentHash"))
    status, text = e2e_http.private_call("ledger", "POST", f"/manual-adjustments/v1/{submitted.get('manualAdjustmentId')}/approve", {},
                                         {"checkerId": checker, "approvedContentHash": submitted.get("contentHash")})
    approved = json.loads(text) if text.startswith("{") else {}
    entry_id = approved.get("journalEntryId")
    record["journalEntryId"] = entry_id
    save()
    check("AC-06", f"Checker 批准后过账（{status} {approved.get('status')}）", status == 200 and entry_id)
    lines_path = f"{accounts_path}/{credit_account.get('accountId')}/journal-lines"
    _, lines = get(f"{lines_path}?page=1&size=20")
    line = (lines.get("items") or [{}])[0]
    check("AC-06", f"贷方账户流水 1 行，行后余额 = {amount}（{line.get('balanceAfter')}）",
          lines.get("total") == 1 and line.get("balanceAfter") in (amount, amount + "000000", "123.45000000"))
    check("AC-06", f"流水来源 MANUAL 且带人工调整单号（{line.get('adjustmentNo')}）",
          line.get("originType") == "MANUAL" and line.get("adjustmentNo") and line.get("journalEntryId") == entry_id)
    tomorrow = (today + timedelta(days=1)).isoformat()
    _, future = get(f"{lines_path}?postingDateFrom={tomorrow}&page=1&size=20")
    _, ranged = get(f"{lines_path}?postingDateFrom={line.get('postingDate')}&postingDateTo={line.get('postingDate')}&page=1&size=20")
    check("AC-06", "过账日期从明天起筛选为空", future.get("total") == 0)
    check("AC-06", "按过账日期筛选后行后余额不变（全量累计）",
          ranged.get("total") == 1 and ranged["items"][0]["balanceAfter"] == line.get("balanceAfter"))
    code, detail = get(f"/finance/ledger-books/v1/{book_id}/journal-entries/{entry_id}")
    entry_lines = detail.get("lines", [])
    total = lambda side: sum(float(l["amount"]) for l in entry_lines if l["entrySide"] == side)
    check("AC-06", f"分录详情 2 行，借贷合计相等（{total('DEBIT')} / {total('CREDIT')}）",
          code == 200 and len(entry_lines) == 2 and total("DEBIT") == total("CREDIT") == float(amount))
    check("AC-06", "分录详情含人工调整单号与 MANUAL 来源",
          detail.get("originType") == "MANUAL" and detail.get("adjustmentNo") == line.get("adjustmentNo"))
    _, page = get(f"{accounts_path}?accountCode=UNIDENTIFIED_CASH&page=1&size=10")
    acc = (page.get("items") or [{}])[0]
    check("AC-06", f"账户列表投影净余额 = {amount}（{acc.get('netBalance')}）", acc.get("netBalance") and float(acc["netBalance"]) == float(amount))
    code, body = get(f"/finance/ledger-books/v1/{book_id}/journal-entries/1")
    check("AC-06", f"不存在的分录 → 400（{code} {body.get('code')}）", code == 400)

    # AC-07 余额查询
    code, balance = get(f"/finance/balances/v1/clients/{client_id}?entityNo={entity_no}&currency=USD")
    money = ["pendingAmount", "availableAmount", "lockedAmount", "securityDepositAmount", "recoveryReceivableAmount"]
    check("AC-07", f"Client 余额 200，五个 Money 字段（{code}）", code == 200 and all(
        isinstance(balance.get(k), dict) and balance[k].get("currency") == "USD" and "amount" in balance[k] for k in money))
    code, balance = get(f"/finance/balances/v1/partners/{client_id}?entityNo={entity_no}&currency=USD")
    check("AC-07", f"Partner 余额 200 且无 securityDepositAmount（{code}）", code == 200 and "securityDepositAmount" not in balance
          and all(isinstance(balance.get(k), dict) for k in money if k != "securityDepositAmount"))
    code, body = get(f"/finance/balances/v1/clients/{client_id}?entityNo=ZZ999&currency=USD")
    check("AC-07", f"未知 entityNo → LEDGER_BOOK_NOT_FOUND（{body.get('detail')}）", business_error(code, body, "LEDGER_BOOK_NOT_FOUND"))
    code, body = get(f"/finance/balances/v1/clients/{client_id}?entityNo={entity_no}")
    check("AC-07", f"缺币种 → 400（{code}）", code == 400)

    # AC-09 注册数据
    tables = [("t_permission", "permission_id", 2), ("t_role_permission", "role_permission_id", 2),
              ("t_console_resource", "resource_id", 5), ("t_gateway_route", "route_id", 18),
              ("t_gateway_route_header", "route_header_id", 60), ("t_gateway_route_query_param", "route_query_param_id", 31),
              ("t_gateway_guard_rule", "guard_rule_id", 108)]
    in_block = lambda column: f"{column} BETWEEN 900000000000097000 AND 900000000000098999"
    total_rows = 0
    for table, column, expected in tables:
        count = int(e2e_http.platform_sql(f"SELECT COUNT(*) FROM {table} WHERE {in_block(column)}")[0][0])
        total_rows += count
        check("AC-09", f"{table} 注册行 {count}/{expected}", count == expected)
    check("AC-09", f"注册行合计 {total_rows}/226", total_rows == 226)
    inactive = e2e_http.platform_sql(
        f"SELECT 'permission', COUNT(*) FROM t_permission WHERE {in_block('permission_id')} AND status<>'ACTIVE' "
        f"UNION ALL SELECT 'grant', COUNT(*) FROM t_role_permission WHERE {in_block('role_permission_id')} AND status<>'ACTIVE' "
        f"UNION ALL SELECT 'resource', COUNT(*) FROM t_console_resource WHERE {in_block('resource_id')} AND status<>'ACTIVE' "
        f"UNION ALL SELECT 'route', COUNT(*) FROM t_gateway_route WHERE {in_block('route_id')} AND status<>'ACTIVE'")
    check("AC-09", f"权限、授权、菜单、路由均为 ACTIVE（非 ACTIVE 行数：{ {r[0]: int(r[1]) for r in inactive} }）",
          all(int(r[1]) == 0 for r in inactive))

    record["finishedAt"] = datetime.now(timezone.utc).isoformat()
    record["results"] = [{"ac": a, "name": n, "ok": ok} for a, n, ok in results]
    save()
    passed = sum(1 for r in results if r[2])
    print(f"\n运行记录：{record_path.relative_to(ROOT)}")
    print(f"{passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


def latest_record():
    records = sorted(RUNTIME.glob("finance-acceptance-*.json"))
    if not records:
        raise SystemExit("没有运行记录")
    return records[-1], json.loads(records[-1].read_text())


def cleanup():
    path, record = latest_record()
    numeric = lambda value: str(int(value)) if value and re.fullmatch(r"[1-9][0-9]{0,18}", str(value)) else "NULL"
    variables = {"book": numeric(record.get("ledgerBookId")), "calendar": numeric(record.get("fiscalCalendarId")),
                 "finance_entity": numeric(record.get("financeEntityId"))}
    entity_no = record.get("entityNo") or ""
    if not re.fullmatch(r"[A-Z]{2}[0-9]{3}", entity_no):
        raise SystemExit("运行记录缺少 entityNo")
    text = CLEANUP_SQL.read_text()
    ledger_part, account_part = text.split("-- @account", 1)
    prefix = "".join(f"SET @{k} = {v};\n" for k, v in variables.items()) + f"SET @entity_no = '{entity_no}';\n"
    e2e_http.sql("ledger", prefix + ledger_part)
    e2e_http.sql("account", prefix + account_part)
    after = table_counts()
    diff = {k: (record["baseline"].get(k), v) for k, v in after.items() if record["baseline"].get(k) != v}
    print(f"清理 {path.name}：book={variables['book']} calendar={variables['calendar']} entity={variables['finance_entity']} entityNo={entity_no}")
    if diff:
        print(f"[失败] 与运行前基线不同：{diff}")
        return 1
    print(f"[通过] ledger / account 共 {len(after)} 张表行数回到运行前基线")
    return 0


if __name__ == "__main__":
    sys.exit(cleanup() if "--cleanup" in sys.argv else main())
