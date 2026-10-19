"""阶段 5 本机综合验证：channel-connector 渠道成本 → billing 计费事实 → account → ledger（任务卡 E，场景 S0～S5）。

前置（本机，E2E_TARGET=local）：run-service.sh 已按任务卡 E 以新 JAR 启动 channel-connector、billing、ledger、account
（platform、ops-bff、gateway、nginx 由其他会话维持运行，本脚本只经运营入口调用 ledger 基础资料与查询）；
mor_channel_connector_v2e2e 已执行 channel-connector-schema.sql（17 表）与 50-channel-connector-payermax-mock.sql（1 Channel / 1 账号 / 1 MID），
mor_billing_v2e2e 已执行 REQ/db/billing/ddl.sql + dml.sql（11 表、DEFAULT 日历 1 行）。
入口 https://127.0.0.1:18444（运营登录走 Gateway → BFF）；channel-connector 18110、billing 18113、account 18114、ledger 18105 走内部端口直连。
mor-test（E2E_TARGET=test，阶段 7 复用）：入口 http://localhost:28080；库为 mor_channel_connector / mor_billing / mor_account / mor_ledger
（127.0.0.1:23306，应用账号口令只从 test-environment/.env.test 读取）；四个服务的私网接口经 e2e_http 接入 mor-test-backend 的一次性探针容器调用；
断言与本机相同。渠道种子按库中现有 ACTIVE Channel + ACTIVE MID（该 Channel 尚无成本方案）选取，本脚本不插入渠道种子；
mor-test 原本没有渠道与 MID，阶段 7 先以 channel_connector_app 执行 REQ/release/test-channel-seed.sql（1 渠道 / 1 账号 / 1 MID，无能力行，
不参与 Payment 路由），验收后由 `--cleanup --drop-seed` 删除（两个目标共用 @channel_seed 段与同一 ID 段）。
--cleanup 只使用与当前 E2E_TARGET 相同目标的最新运行记录，避免本机记录被用于 mor-test 或反之。
结构、登录、运行记录、清理与基线核对照抄 requirements/20261019/finance-account明细账与ledger控制科目重构/verification/local_acceptance.py。
不打印 Token、口令与密钥。

用法：
  python3 -I local_acceptance.py                         验收；运行记录写入 local-environment/e2e/runtime/billing-fact-acceptance-<ts>.json
  python3 -I local_acceptance.py --cleanup               按最新运行记录执行 acceptance-cleanup-local.sql，核对四库行数回到运行前基线
  python3 -I local_acceptance.py --cleanup --drop-seed   另外删除渠道种子（本机：任务卡 E 第 3 步；mor-test：release/test-channel-seed.sql），
                                                         核对 channel-connector 17 表全部 0 行
  E2E_TARGET=test python3 -I local_acceptance.py [...]    以上各项改对 mor-test 执行（阶段 7）
"""
import base64
import json
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http  # noqa: E402

RUNTIME = ROOT / "local-environment/e2e/runtime"
CLEANUP_SQL = HERE / "acceptance-cleanup-local.sql"
RECORD_GLOB = "billing-fact-acceptance-*.json"
RUN = uuid.uuid4().hex[:6].upper()
CHINESE = re.compile(r"[一-鿿]")
SERVICES = ("channel-connector", "billing", "account", "ledger")
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


def raw(method, path, body=None, token=None):
    """直接请求运营入口以读取状态码；返回 (状态, 响应体)；429 按 Retry-After 重试。"""
    headers = {"Origin": e2e_http.OPERATIONS, "Accept": "application/json", "Content-Type": "application/json",
               "Idempotency-Key": str(uuid.uuid4())}
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
    """四库所有表行数（基线与清理核对用）。"""
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
    used |= {r[0] for r in e2e_http.sql("billing", "SELECT DISTINCT entity_no FROM t_billing_fact")}
    for n in range(int(RUN[:4], 16) % 800 + 100, 1000):
        candidate = f"ZE{n:03d}"
        if candidate in used:
            continue
        if ops.get(f"/finance/ledger-books/v1?entityType=OPERATING_ENTITY&entityId={candidate}&page=1&size=10")["total"] == 0:
            return candidate
    raise SystemExit("找不到未使用的 entityNo")


def random_id(prefix="88"):
    return prefix + str(uuid.uuid4().int)[:16]


def iso(moment):
    return moment.astimezone(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def main():
    s = e2e_http.secrets()
    baseline = table_counts()
    ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
    actor = jwt_subject(ops.token)
    post = lambda path, body=None: raw("POST", path, body, ops.token)
    get = lambda path: status_of(lambda: ops.get(path))
    record = {"run": RUN, "target": e2e_http.TARGET, "startedAt": datetime.now(timezone.utc).isoformat(), "baseline": baseline,
              "factIds": []}
    record_path = RUNTIME / f"billing-fact-acceptance-{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
    RUNTIME.mkdir(parents=True, exist_ok=True)

    def save():
        record_path.write_text(json.dumps(record, ensure_ascii=False, indent=2))

    save()

    # ---------- AC-01 运行库结构 ----------
    for service, expected in (("billing", 11), ("channel-connector", 17)):
        database = e2e_http.DATABASES[service][0]
        count = int(scalar(service, f"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='{database}'"))
        check("AC-01", f"{database} 表数 {count}/{expected}", count == expected)
    legacy = scalar("billing", "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                               "AND (table_name LIKE 't_economic_fact%' OR table_name LIKE 't_channel_cost%')")
    check("AC-05", f"billing 库无 t_economic_fact* / t_channel_cost* 表（{legacy}）", legacy == "0")
    calendar_row = e2e_http.sql("billing", "SELECT calendar_code, timezone, status FROM t_business_calendar")
    check("AC-01", f"billing 日历 DEFAULT / UTC / ACTIVE 1 行（{calendar_row}）", calendar_row == [["DEFAULT", "UTC", "ACTIVE"]])

    # ---------- S0 基础资料：新 OE 日历、当月 OPEN 期间、OE / USD 账簿 ----------
    entity_no = fresh_entity_no(ops)
    client_id = random_id()
    record.update(entityNo=entity_no, clientId=client_id)
    save()
    calendar = {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "calendarCode": f"E2E-BF-{RUN}", "timezone": "Asia/Dubai",
                "yearStartMonth": 1}
    status, created = post("/finance/fiscal-calendars/v1", calendar)
    calendar_id = created.get("fiscalCalendarId")
    record["fiscalCalendarId"] = calendar_id
    save()
    check("S0", f"新建日历 201（{entity_no}，{status}）", status == 201 and calendar_id)
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
    check("S0", f"当月期间 201 并开放为 OPEN（{status}/{status_open}）", status == 201 and opened.get("status") == "OPEN")
    status, created = post("/finance/ledger-books/v1", {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "currency": "USD",
                                                        "chartCode": "STANDARD"})
    book_id = created.get("ledgerBookId")
    record["ledgerBookId"] = book_id
    save()
    check("S0", f"新建 OE / USD 账簿 201（{status}）", status == 201 and book_id)

    # ---------- S1 渠道成本：方案 → 版本 → 提交 → 批准 → 命中（channel-connector 直连） ----------
    seed = e2e_http.sql("channel-connector", "SELECT m.mid_id, m.channel_id, m.entity_no FROM t_channel_mid m "
                                             "JOIN t_payment_channel c ON c.channel_id = m.channel_id "
                                             "WHERE m.mid_status = 'ACTIVE' AND c.channel_status = 'ACTIVE' "
                                             "AND NOT EXISTS (SELECT 1 FROM t_channel_cost_plan p WHERE p.channel_id = m.channel_id) "
                                             "ORDER BY m.mid_id LIMIT 1")
    if not seed:
        raise SystemExit("没有可用的 ACTIVE Channel + MID（或该 Channel 已有成本方案）；先执行任务卡第 3 步的渠道种子或清理旧方案")
    mid_id, channel_id, mid_entity = seed[0]
    plan_code = f"E2E_CC_{RUN}"
    cost_maker, cost_checker = random_id("71"), random_id("72")
    record.update(planCode=plan_code, channelId=channel_id, midId=mid_id, midEntityNo=mid_entity)
    save()
    base = "/channel-connector/v1/channel-cost-plans"
    status, plan = private("channel-connector", "POST", base, {"channelId": channel_id, "planCode": plan_code})
    check("AC-04", f"创建方案 201 DRAFT（{status} {plan.get('status')}）", status == 201 and plan.get("status") == "DRAFT"
          and plan.get("planCode") == plan_code and plan.get("channelId") == channel_id)
    status, body = private("channel-connector", "POST", base, {"channelId": channel_id, "planCode": plan_code + "_DUP"})
    check("AC-04", f"同一 Channel 第二个方案被拒 400（{status} {body.get('code')}）", status == 400)

    effective_from = datetime.now(timezone.utc) - timedelta(hours=1)
    scope = {"scopeNo": 1, "midId": mid_id, "entityNo": mid_entity, "channelAgreementRef": f"E2E-AGR-{RUN}", "countryCode": "US",
             "currency": "USD", "paymentMethodCode": "CARD", "actionType": "PAY",
             "components": [{"componentCode": "PROCESSING", "componentType": "PROCESSING_FEE", "calculationBase": "TRANSACTION_AMOUNT",
                             "rateValue": "0.029", "fixedAmount": "0.30", "currency": "USD", "roundingMode": "HALF_UP"}]}
    status, version = private("channel-connector", "POST", f"{base}/{plan_code}/versions",
                              {"makerId": cost_maker, "effectiveFrom": iso(effective_from), "scopes": [scope]})
    content_hash = version.get("contentHash") or ""
    check("AC-04", f"创建版本 201：DRAFT、costVer 1、内容摘要 64 位（{status} {version.get('status')}）",
          status == 201 and version.get("status") == "DRAFT" and version.get("costVer") == "1"
          and re.fullmatch(r"[0-9a-f]{64}", content_hash))
    status, submitted = private("channel-connector", "POST", f"{base}/{plan_code}/versions/1/submit", {"makerId": cost_maker})
    check("AC-04", f"提交 200 → PENDING_APPROVAL（{status} {submitted.get('status')}）",
          status == 200 and submitted.get("status") == "PENDING_APPROVAL")
    status, body = private("channel-connector", "POST", f"{base}/{plan_code}/versions/1/approve",
                           {"checkerId": cost_maker, "approvedContentHash": content_hash})
    check("AC-04", f"复核人 = 编制人 → 400 BUSINESS_CHECK_FAILED（{status} {body.get('code')}）",
          business_error(status, body, "BUSINESS_CHECK_FAILED"))
    status, body = private("channel-connector", "POST", f"{base}/{plan_code}/versions/1/approve",
                           {"checkerId": cost_checker, "approvedContentHash": "0" * 64})
    check("AC-04", f"内容摘要不一致 → 400 BUSINESS_CHECK_FAILED（{status} {body.get('code')}）",
          business_error(status, body, "BUSINESS_CHECK_FAILED"))
    status, approved = private("channel-connector", "POST", f"{base}/{plan_code}/versions/1/approve",
                               {"checkerId": cost_checker, "approvedContentHash": content_hash})
    check("AC-04", f"批准 200：生效时间已过 → ACTIVE、checkerId（{status} {approved.get('status')}）",
          status == 200 and approved.get("status") == "ACTIVE" and approved.get("checkerId") == cost_checker)
    status, plan = private("channel-connector", "GET", f"{base}/{plan_code}")
    check("AC-04", f"方案详情 ACTIVE、当前版本 1、版本摘要 1 条（{status} {plan.get('status')} {plan.get('currentCostVer')}）",
          status == 200 and plan.get("status") == "ACTIVE" and plan.get("currentCostVer") == "1" and len(plan.get("versions") or []) == 1)
    status, detail = private("channel-connector", "GET", f"{base}/{plan_code}/versions/1")
    detail_component = ((detail.get("scopes") or [{}])[0].get("components") or [{}])[0]
    check("AC-04", f"版本详情：范围 MID / OE 与组成 componentId（{status} {detail_component.get('componentId')}）",
          status == 200 and detail.get("contentHash") == content_hash and detail["scopes"][0].get("midId") == mid_id
          and detail["scopes"][0].get("entityNo") == mid_entity and re.fullmatch(r"[1-9][0-9]{0,18}", detail_component.get("componentId") or ""))
    plan_row = e2e_http.sql("channel-connector", f"SELECT p.status, v.status, v.cost_ver, p.current_ver_id = v.id FROM t_channel_cost_plan p "
                                                 f"JOIN t_channel_cost_plan_ver v ON v.plan_id = p.id WHERE p.plan_code = '{plan_code}'")
    check("AC-04", f"库中方案指针指向 ACTIVE 版本 1（{plan_row}）", plan_row == [["ACTIVE", "ACTIVE", "1", "1"]])

    resolve = {"channelId": channel_id, "midId": mid_id, "entityNo": mid_entity, "countryCode": "US", "currency": "USD",
               "paymentMethodCode": "CARD", "actionType": "PAY", "asOfTime": iso(datetime.now(timezone.utc))}
    status, hit = private("channel-connector", "POST", "/channel-connector/v1/channel-costs/resolutions", resolve)
    components = hit.get("components") or []
    component = components[0] if components else {}
    check("AC-04", f"命中 200：方案 / 版本 1 / 内容摘要 / 范围 1 / 1 个组件（{status} {hit.get('costVer')} {len(components)}）",
          status == 200 and hit.get("planCode") == plan_code and hit.get("costVer") == "1" and hit.get("contentHash") == content_hash
          and hit.get("scopeNo") == 1 and hit.get("channelAgreementRef") == scope["channelAgreementRef"] and hit.get("currency") == "USD"
          and len(components) == 1 and re.fullmatch(r"[1-9][0-9]{0,18}", hit.get("costVerId") or "")
          and re.fullmatch(r"[1-9][0-9]{0,18}", hit.get("scopeId") or ""))
    check("AC-04", f"命中组件 PROCESSING_FEE、rateValue 0.029、fixedAmount 0.3、HALF_UP（{component.get('rateValue')} / {component.get('fixedAmount')}）",
          component.get("componentType") == "PROCESSING_FEE" and component.get("rateValue") == "0.029"
          and component.get("fixedAmount") == "0.3" and component.get("roundingMode") == "HALF_UP"
          and component.get("componentId") == detail_component.get("componentId"))
    record["costResolution"] = hit
    save()
    status, body = private("channel-connector", "POST", "/channel-connector/v1/channel-costs/resolutions", {**resolve, "countryCode": "GB"})
    check("AC-04", f"不同国家 → 400 CHANNEL_COST_NOT_RESOLVED（{status} {body.get('code')}）",
          business_error(status, body, "CHANNEL_COST_NOT_RESOLVED"))
    status, body = private("channel-connector", "POST", "/channel-connector/v1/channel-costs/resolutions",
                           {**resolve, "asOfTime": iso(effective_from - timedelta(hours=1))})
    check("AC-04", f"asOfTime 早于生效时间 → 400 CHANNEL_COST_NOT_RESOLVED（{status} {body.get('code')}）",
          business_error(status, body, "CHANNEL_COST_NOT_RESOLVED"))
    status, body = private("channel-connector", "POST", "/channel-connector/v1/channel-costs/resolutions",
                           {**resolve, "midId": random_id("93")})
    check("AC-04", f"命中时 MID 不存在 → 400 CHANNEL_COST_NOT_RESOLVED（{status} {body.get('code')}）",
          business_error(status, body, "CHANNEL_COST_NOT_RESOLVED"))
    status, body = private("channel-connector", "POST", f"{base}/{plan_code}/versions",
                           {"makerId": cost_maker, "effectiveFrom": iso(datetime.now(timezone.utc) + timedelta(days=1)),
                            "scopes": [{**scope, "midId": random_id("93")}]})
    versions = scalar("channel-connector", f"SELECT COUNT(*) FROM t_channel_cost_plan_ver v JOIN t_channel_cost_plan p ON p.id = v.plan_id "
                                           f"WHERE p.plan_code = '{plan_code}'")
    check("AC-04", f"新版本范围 MID 不存在 → 400 BUSINESS_CHECK_FAILED 且不落版本（{status} {body.get('code')}，版本数 {versions}）",
          business_error(status, body, "BUSINESS_CHECK_FAILED") and "MID" in (body.get("detail") or "") and versions == "1")
    status, body = private("channel-connector", "POST", f"{base}/{plan_code}/versions",
                           {"makerId": cost_maker, "effectiveFrom": iso(datetime.now(timezone.utc) + timedelta(days=1)),
                            "scopes": [{**scope, "entityNo": "ZZ998"}]})
    check("AC-04", f"新版本范围 OE 与 MID 主体不一致 → 400 BUSINESS_CHECK_FAILED（{status} {body.get('code')}）",
          business_error(status, body, "BUSINESS_CHECK_FAILED"))

    # ---------- S2 计费事实：登记 → 幂等 → 批准 → 查询 → 发布进度（billing 直连） ----------
    maker, checker = random_id("77"), random_id("78")
    record.update(makerId=maker, checkerId=checker)
    save()

    def fact_body(amount, reference, ticket, supersedes=None):
        body = {"factType": "MANUAL_ADJUSTMENT", "sourceSystem": "OPERATIONS", "sourceReference": reference, "clientId": client_id,
                "entityNo": entity_no, "currency": "USD", "makerId": maker, "refs": [{"refType": "TICKET", "refId": ticket}],
                "lines": [{"componentType": "CLIENT_PRINCIPAL", "payerRole": "OPERATING_ENTITY", "payerId": entity_no,
                           "payeeRole": "CLIENT", "payeeId": client_id, "amount": amount, "direction": "CHARGE",
                           "targetBucket": "AVAILABLE"}]}
        if supersedes:
            body["supersedesFactId"] = supersedes
        return body

    def remember(fact):
        if fact.get("factId") and fact["factId"] not in record["factIds"]:
            record["factIds"].append(fact["factId"])
            save()

    reference1, ticket1 = f"E2E-BF-{RUN}-1", f"T-{RUN}-1"
    status, fact1 = private("billing", "POST", "/billing/v1/facts", fact_body("100.00", reference1, ticket1))
    remember(fact1)
    fact1_id = fact1.get("factId")
    check("AC-02", f"登记 201：DRAFT、MANUAL、factVersion 0、sourceEventKey = 来源引用（{status} {fact1.get('status')} {fact1.get('factVersion')}）",
          status == 201 and fact1.get("status") == "DRAFT" and fact1.get("originType") == "MANUAL" and fact1.get("factVersion") == "0"
          and fact1.get("sourceEventKey") == reference1 and fact1.get("makerId") == maker)
    status, replay = private("billing", "POST", "/billing/v1/facts", fact_body("100.00", reference1, ticket1))
    check("AC-02", f"同来源同内容重放 200 返回同一事实（{status} {replay.get('factId') == fact1_id}）",
          status == 200 and replay.get("factId") == fact1_id and replay.get("sourcePayloadHash") == fact1.get("sourcePayloadHash"))
    status, body = private("billing", "POST", "/billing/v1/facts", fact_body("90.00", reference1, ticket1))
    check("AC-02", f"同来源改金额 → 400（{status} {body.get('code')}）", status == 400 and bool(CHINESE.search(body.get("detail") or "")))
    bad = fact_body("10.00", f"E2E-BF-{RUN}-X", ticket1)
    bad["lines"][0]["payeeRole"], bad["lines"][0]["payeeId"] = "OPERATING_ENTITY", entity_no
    bad["lines"][0]["payerRole"], bad["lines"][0]["payerId"] = "CLIENT", client_id
    status, body = private("billing", "POST", "/billing/v1/facts", bad)
    remember(body)
    check("AC-02", f"targetBucket 给非 Owner 收方 → 400（{status} {body.get('code')}）", status == 400 and not body.get("factId"))
    no_party = fact_body("10.00", f"E2E-BF-{RUN}-Y", ticket1)
    del no_party["lines"][0]["payeeRole"]
    status, body = private("billing", "POST", "/billing/v1/facts", no_party)
    remember(body)
    check("AC-02", f"行缺收方 → 422 / 400（{status} {body.get('code')}）", status in (400, 422) and not body.get("factId"))
    status, body = private("billing", "POST", f"/billing/v1/facts/{fact1_id}/approve", {"checkerId": maker})
    check("AC-02", f"复核人 = 提交人 → 400 BUSINESS_CHECK_FAILED（{status} {body.get('code')}）",
          business_error(status, body, "BUSINESS_CHECK_FAILED"))
    status, approved1 = private("billing", "POST", f"/billing/v1/facts/{fact1_id}/approve", {"checkerId": checker})
    check("AC-02", f"批准 200：APPROVED、factVersion 1、businessTime = recordedTime = approvedTime（{status} {approved1.get('status')}）",
          status == 200 and approved1.get("status") == "APPROVED" and approved1.get("factVersion") == "1"
          and approved1.get("checkerId") == checker and approved1.get("approvedTime")
          and approved1.get("businessTime") == approved1.get("approvedTime") == approved1.get("recordedTime"))
    status, again = private("billing", "POST", f"/billing/v1/facts/{fact1_id}/approve", {"checkerId": checker})
    check("AC-02", f"同一复核人重复批准 200 返回原结果（{status} {again.get('factVersion')}）",
          status == 200 and again.get("status") == "APPROVED" and again.get("factVersion") == "1"
          and again.get("approvedTime") == approved1.get("approvedTime"))

    status, got = private("billing", "GET", f"/billing/v1/facts/{fact1_id}")
    line = (got.get("lines") or [{}])[0]
    check("AC-02", f"按 ID 读取：refs [TICKET]、1 行（CLIENT_PRINCIPAL OE→CLIENT 100、targetBucket AVAILABLE）、links 空、pricing 空（{status}）",
          status == 200 and got.get("refs") == [{"refType": "TICKET", "refId": ticket1}] and len(got.get("lines") or []) == 1
          and line.get("componentType") == "CLIENT_PRINCIPAL" and line.get("payerRole") == "OPERATING_ENTITY"
          and line.get("payerId") == entity_no and line.get("payeeRole") == "CLIENT" and line.get("payeeId") == client_id
          and money(line.get("postedAmount")) == Decimal("100") and line.get("direction") == "CHARGE"
          and line.get("targetBucket") == "AVAILABLE" and got.get("links") == [] and got.get("pricing") is None
          and money(got.get("grossAmount")) == Decimal("100") and got.get("clientId") == client_id)
    status, by_ref = private("billing", "GET", f"/billing/v1/facts/by-ref?refType=TICKET&refId={ticket1}")
    check("AC-02", f"按引用查 TICKET 命中 1 条（{status} {[i.get('factId') for i in by_ref.get('items') or []]}）",
          status == 200 and [i.get("factId") for i in by_ref.get("items") or []] == [fact1_id])
    status, body = private("billing", "GET", f"/billing/v1/facts/by-ref?refType=NOT_A_REF&refId={ticket1}")
    check("AC-02", f"按引用查未知引用类型 → 400 INVALID_REQUEST（{status} {body.get('code')}）", business_error(status, body, "INVALID_REQUEST"))
    status, empty = private("billing", "GET", f"/billing/v1/facts/by-ref?refType=TICKET&refId=T-{RUN}-NONE")
    check("AC-02", f"按引用查无匹配 → 200 空列表（{status}）", status == 200 and empty.get("items") == [])
    published = wait_until(lambda: private("billing", "GET", f"/billing/v1/facts/progress?entityType=OPERATING_ENTITY&entityId={entity_no}"
                                                         "&currency=USD")[1].get("publishedFactCount") == "1", 60)
    status, progress = private("billing", "GET", f"/billing/v1/facts/progress?entityType=OPERATING_ENTITY&entityId={entity_no}&currency=USD")
    check("AC-03", f"进度：pendingCount 0、publishedFactCount 1、maxPublishedFactId = 事实（{status} {progress}）",
          published and status == 200 and progress.get("pendingCount") == "0" and progress.get("maxPublishedFactId") == fact1_id)
    status, listed = private("billing", "GET", f"/billing/v1/facts?entityType=OPERATING_ENTITY&entityId={entity_no}&currency=USD&limit=10")
    check("AC-02", f"按账簿流列表 1 条（{status}）", status == 200 and [i.get("factId") for i in listed.get("items") or []] == [fact1_id])
    task = e2e_http.sql("billing", f"SELECT event_type, execute_status FROM t_compensation_event WHERE event_key = "
                                   f"'BILLING_FACT:{fact1_id}:BillingFactRecorded:1'")
    published_at = scalar("billing", f"SELECT published_at IS NOT NULL FROM t_billing_fact WHERE id = {int(fact1_id or 0)}")
    check("AC-03", f"补偿任务 BILLING_FACT_RECORDED 执行成功后删除、published_at 非空（剩余 {task}，{published_at}）",
          task == [] and published_at == "1")

    # ---------- S3 account / ledger ----------
    owner_path = f"/owner-accounts/v1?entityType=OPERATING_ENTITY&entityId={entity_no}&currency=USD&ownerType=CLIENT&ownerId={client_id}"
    payable_sql = (f"SELECT net_balance FROM t_owner_account WHERE entity_type='OPERATING_ENTITY' AND entity_id='{entity_no}' "
                   f"AND currency='USD' AND account_code='CLIENT_PAYABLE' AND owner_type='CLIENT' AND owner_id='{client_id}' "
                   "AND balance_bucket='PENDING'")
    check("AC-03", "account 消费 BillingFactRecorded：CLIENT_PAYABLE / CLIENT / PENDING 净额 100.00",
          wait_until(lambda: Decimal(scalar("account", payable_sql) or "0") == Decimal("100")))
    consumed = scalar("account", f"SELECT COUNT(*) FROM t_consumed_event WHERE aggregate_type='BILLING_FACT' AND aggregate_id='{fact1_id}' "
                                 "AND event_type='BillingFactRecorded'")
    check("AC-03", f"account 消费幂等记录 BillingFactRecorded / BILLING_FACT 1 条（{consumed}）", consumed == "1")
    status, page = private("account", "GET", owner_path + "&page=1&size=10")
    owner_account = (page.get("items") or [{}])[0]
    record["ownerAccountId"] = owner_account.get("accountId")
    save()
    check("AC-03", f"Owner 账户：CLIENT_PAYABLE / PENDING（本期不消费 targetBucket）、贷 100.00、净 100.00（{status}）",
          status == 200 and page.get("total") == 1 and owner_account.get("accountCode") == "CLIENT_PAYABLE"
          and owner_account.get("balanceBucket") == "PENDING" and money(owner_account.get("creditTotal")) == Decimal("100")
          and money(owner_account.get("netBalance")) == Decimal("100"))
    status, entries = private("account", "GET", f"/owner-accounts/v1/{owner_account.get('accountId')}/entries?page=1&size=20")
    entry1 = (entries.get("items") or [{}])[0]
    check("AC-03", f"流水 1 行：贷 100.00、事实 v1、MANUAL_ADJUSTMENT（{status} {entry1.get('sourceType')}）",
          status == 200 and entries.get("total") == 1 and entry1.get("entrySide") == "CREDIT" and money(entry1.get("amount")) == Decimal("100")
          and entry1.get("factId") == fact1_id and entry1.get("factVersion") == "1" and entry1.get("factType") == "MANUAL_ADJUSTMENT")
    status, progress = private("account", "GET", f"/account-postings/v1/progress?entityType=OPERATING_ENTITY&entityId={entity_no}&currency=USD")
    check("AC-03", f"account 进度 appliedFactCount 1、maxAppliedFactId = 事实（{status}）",
          status == 200 and str(progress.get("appliedFactCount")) == "1" and progress.get("maxAppliedFactId") == fact1_id)

    instruction_sql = (f"SELECT COUNT(*) FROM t_accounting_instruction WHERE ledger_book_id={int(book_id or 0)} "
                       "AND origin_type='ACCOUNT_POSTING'")
    check("AC-03", "ledger 消费 AccountPosted：本账簿 1 条 ACCOUNT_POSTING 指令",
          wait_until(lambda: int(scalar("ledger", instruction_sql) or 0) == 1))
    fact_link = scalar("ledger", f"SELECT economic_fact_id FROM t_accounting_instruction WHERE ledger_book_id={int(book_id or 0)}")
    check("AC-03", f"ledger 指令关联事实 = billing 事实（经 /billing/v1/facts/{{id}} 读取，{fact_link}）", fact_link == fact1_id)
    accounts_path = f"/finance/ledger-books/v1/{book_id}/accounts"
    code, page = get(f"{accounts_path}?accountCode=CLIENT_PAYABLE&page=1&size=10")
    control = (page.get("items") or [{}])[0]
    record["controlAccountId"] = control.get("accountId")
    save()
    check("AC-03", f"ledger 控制账户 CLIENT_PAYABLE：Owner OE {entity_no}、桶 NONE、净 100.00（{code}）",
          code == 200 and page.get("total") == 1 and control.get("ownerType") == "OPERATING_ENTITY" and control.get("ownerId") == entity_no
          and control.get("balanceBucket") == "NONE" and money(control.get("netBalance")) == Decimal("100"))
    code, lines = get(f"{accounts_path}/{control.get('accountId')}/journal-lines?page=1&size=20")
    line1 = (lines.get("items") or [{}])[0]
    code_detail, detail = get(f"/finance/ledger-books/v1/{book_id}/journal-entries/{line1.get('journalEntryId')}")
    detail_lines = detail.get("lines") or []
    total = lambda side, rows: sum((money(l.get("amount")) for l in rows if l.get("entrySide") == side), Decimal("0"))
    check("AC-03", f"分录 originType ACCOUNT_POSTING、控制科目行 accountEntryId = 流水、借贷平衡（{code}/{code_detail}）",
          line1.get("originType") == "ACCOUNT_POSTING" and detail.get("originType") == "ACCOUNT_POSTING"
          and any(l.get("accountCode") == "CLIENT_PAYABLE" and l.get("accountEntryId") == entry1.get("entryId") for l in detail_lines)
          and detail_lines and total("DEBIT", detail_lines) == total("CREDIT", detail_lines) == Decimal("100"))

    # ---------- S4 替代：80.00 替代 100.00 ----------
    reference2, ticket2 = f"E2E-BF-{RUN}-2", f"T-{RUN}-2"
    status, fact2 = private("billing", "POST", "/billing/v1/facts", fact_body("80.00", reference2, ticket2, fact1_id))
    remember(fact2)
    fact2_id = fact2.get("factId")
    status_a, approved2 = private("billing", "POST", f"/billing/v1/facts/{fact2_id}/approve", {"checkerId": checker})
    check("S4", f"替代事实登记 201、批准 200：factVersion 2、supersedesFactId（{status}/{status_a} {approved2.get('factVersion')}）",
          status == 201 and status_a == 200 and approved2.get("status") == "APPROVED" and approved2.get("factVersion") == "2"
          and approved2.get("supersedesFactId") == fact1_id)
    status, old = private("billing", "GET", f"/billing/v1/facts/{fact1_id}")
    check("AC-02", f"被替代事实 SUPERSEDED（{status} {old.get('status')}）", status == 200 and old.get("status") == "SUPERSEDED")
    check("AC-03", "account 替代后 CLIENT_PAYABLE / PENDING 净额 80.00",
          wait_until(lambda: Decimal(scalar("account", payable_sql) or "0") == Decimal("80")))
    status, entries = private("account", "GET", f"/owner-accounts/v1/{owner_account.get('accountId')}/entries?page=1&size=20")
    rows = entries.get("items") or []
    reversal = next((r for r in rows if r.get("reversesEntryId")), {})
    fresh = next((r for r in rows if r.get("postingSequence") == "2" and not r.get("reversesEntryId")), {})
    check("AC-03", f"account 批次 2：冲回行借 100.00（reversesEntryId = 原流水）+ 新行贷 80.00（{status} {entries.get('total')}）",
          status == 200 and entries.get("total") == 3 and reversal.get("reversesEntryId") == entry1.get("entryId")
          and reversal.get("entrySide") == "DEBIT" and money(reversal.get("amount")) == Decimal("100") and reversal.get("postingSequence") == "2"
          and fresh.get("entrySide") == "CREDIT" and money(fresh.get("amount")) == Decimal("80") and fresh.get("factId") == fact2_id
          and fresh.get("factVersion") == "2")
    check("AC-03", "ledger 第 2 条 ACCOUNT_POSTING 指令", wait_until(lambda: int(scalar("ledger", instruction_sql) or 0) == 2))
    code, page = get(f"{accounts_path}?accountCode=CLIENT_PAYABLE&page=1&size=10")
    control = (page.get("items") or [{}])[0]
    check("AC-03", f"ledger 控制账户 CLIENT_PAYABLE 净 80.00（{code} {control.get('netBalance')}）",
          code == 200 and money(control.get("netBalance")) == Decimal("80"))
    published = wait_until(lambda: private("billing", "GET", f"/billing/v1/facts/progress?entityType=OPERATING_ENTITY&entityId={entity_no}"
                                                         "&currency=USD")[1].get("publishedFactCount") == "2", 60)
    check("AC-03", "billing 进度 publishedFactCount 2、pendingCount 0", published)

    # ---------- S5 ledger 追平与关账检查（经 /billing/v1/facts/progress） ----------
    gateway_headers = {"X-Gateway-Actor-Subject": actor, "X-Gateway-Session-Id": random_id("99")}
    status, caught = private("ledger", "POST", "/posting-streams/v1/catch-up",
                             {"entityType": "OPERATING_ENTITY", "entityId": entity_no, "currency": "USD", "shardNo": 0}, gateway_headers)
    check("AC-03", f"catch-up 200：caughtUp=true、lastSequence=2（{status} {caught}）",
          status == 200 and caught.get("caughtUp") is True and str(caught.get("lastSequence")) == "2")
    status, close = private("ledger", "POST", f"/accounting-periods/v1/{period_id}/closes", {"makerId": maker})
    close_no = close.get("closeNo")
    record["periodClose"] = {"periodId": period_id, "closeNo": close_no, "status": close.get("status")}
    save()
    status_detail, close_detail = private("ledger", "GET", f"/accounting-periods/v1/{period_id}/closes/{close_no}")
    steps = {s_.get("stepType"): s_.get("status") for s_ in close_detail.get("steps") or []}
    record["periodClose"]["steps"] = steps
    save()
    check("AC-03", f"发起关账 201，全部步骤 SUCCEEDED（含读取 billing 进度的步骤）（{status} {close.get('status')} {steps}）",
          status == 201 and close.get("status") == "PENDING_CHECK" and steps and all(v == "SUCCEEDED" for v in steps.values()))
    if close.get("status") == "PENDING_CHECK":
        status, rejected = private("ledger", "POST", f"/accounting-periods/v1/{period_id}/closes/{close_no}/reject",
                                   {"checkerId": checker, "approvedCloseHash": close_detail.get("closeHash")})
        record["periodClose"]["rejected"] = rejected.get("status")
        save()
        check("S5", f"Checker 驳回 → REJECTED，期间回到 OPEN（{status} {rejected.get('status')}）",
              status == 200 and rejected.get("status") == "REJECTED"
              and scalar("ledger", f"SELECT status FROM t_accounting_period WHERE id={int(period_id)}") == "OPEN")

    # ---------- AC-05 旧接口已删除 ----------
    status, _ = private("billing", "GET", f"/billing/v1/economic-facts/{fact1_id}")
    status2, _ = private("billing", "GET", f"/channel-cost-plans/v1/{plan_code}")
    check("AC-05", f"billing 旧路径 /billing/v1/economic-facts 与 /channel-cost-plans/v1 返回 404（{status} / {status2}）",
          status == 404 and status2 == 404)

    record["finishedAt"] = datetime.now(timezone.utc).isoformat()
    record["results"] = [{"ac": a, "name": n, "ok": ok} for a, n, ok in results]
    save()
    passed = sum(1 for r in results if r[2])
    print(f"\n运行记录：{record_path.relative_to(ROOT)}")
    print(f"{passed}/{len(results)} 通过")
    return 0 if passed == len(results) else 1


def latest_record():
    records = [(path, json.loads(path.read_text())) for path in sorted(RUNTIME.glob(RECORD_GLOB))]
    records = [(path, record) for path, record in records if record.get("target", "local") == e2e_http.TARGET]
    if not records:
        raise SystemExit(f"没有 E2E_TARGET={e2e_http.TARGET} 的运行记录")
    return records[-1]


def cleanup(drop_seed):
    path, record = latest_record()
    numeric = lambda value: str(int(value)) if value and re.fullmatch(r"[1-9][0-9]{0,18}", str(value)) else "NULL"
    entity_no = record.get("entityNo") or ""
    if not re.fullmatch(r"[A-Z]{2}[0-9]{3}", entity_no):
        raise SystemExit("运行记录缺少 entityNo")
    plan_code = record.get("planCode") or ""
    plan_literal = f"'{plan_code}'" if re.fullmatch(r"E2E_CC_[0-9A-F]{6}", plan_code) else "NULL"
    facts = [numeric(f) for f in record.get("factIds") or [] if numeric(f) != "NULL"]
    variables = {"book": numeric(record.get("ledgerBookId")), "calendar": numeric(record.get("fiscalCalendarId")), "plan_code": plan_literal}
    prefix = "".join(f"SET @{k} = {v};\n" for k, v in variables.items()) + f"SET @entity_no = '{entity_no}';\n"
    fact_list = ",".join(facts) or "NULL"
    text = CLEANUP_SQL.read_text().replace("/*FACT_IDS*/", fact_list)
    sections = dict(re.findall(r"-- @(\w+)\n(.*?)(?=\n-- @\w+\n|\Z)", text, re.S))
    for service, section in (("ledger", "ledger"), ("account", "account"), ("billing", "billing"), ("channel-connector", "channel")):
        e2e_http.sql(service, prefix + sections[section])
    after = table_counts()
    diff = {k: (record["baseline"].get(k), v) for k, v in after.items() if record["baseline"].get(k) != v}
    print(f"清理 {path.name}：book={variables['book']} calendar={variables['calendar']} entityNo={entity_no} plan={plan_literal} facts={fact_list}")
    rc = 0
    if diff:
        print(f"[失败] 与运行前基线不同：{diff}")
        rc = 1
    else:
        print(f"[通过] {' / '.join(SERVICES)} 共 {len(after)} 张表行数回到运行前基线")
    if drop_seed:
        e2e_http.sql("channel-connector", sections["channel_seed"])
        left = {k: v for k, v in table_counts().items() if k.startswith("channel-connector.") and v}
        if left:
            print(f"[失败] 删除种子后 channel-connector 仍有行：{left}")
            rc = 1
        else:
            print("[通过] 已删除渠道种子：channel-connector 17 张表全部 0 行")
    return rc


if __name__ == "__main__":
    sys.exit(cleanup("--drop-seed" in sys.argv) if "--cleanup" in sys.argv else main())
