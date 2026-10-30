"""tax 结账算税与事件登记 · 阶段 7 mor-test 后端验收（runbook 第 6 节 A1—A7）。由 verification/stage5/stage5_tax.py 改写。

写法同 requirements/20261023/commercial-商业关系快照与真实解析接入/release/acceptance/stage7_relsnap.py：
- E2E_TARGET=test；私网调用经接入 mor-test-backend 的一次性探针容器（e2e_http.private_call；探针镜像缺省为现网 payment 镜像，只用其 JDK）；
- Client 侧：mor-test 没有可用的 Client API 密钥，私网直连 acquiring /client-gateway-api/v1，带 Gateway 注入的可信 Header（同 relsnap / 到期确认验收）；
  Client = DEMO-CL-001（237575207592763393）；acquiring 为 MOCK 商业解析，订单的卖方与纳税主体都是 AE001（Party 880000000702）；
- 库只读 SELECT（服务自有账号，口令经 MYSQL_PWD，不打印）；不打印口令、Checkout Public Token（chk_…）与卡号。
结果合并写入同目录 tax-api-result.json，逐行过程写 tax-api.txt。

步骤（--dry-run 只打印将发送的请求与 SELECT，不连接 mor-test 任何接口或数据库）：
  preflight   只读：6 个服务镜像、tax.acquiring.* 队列与消费者、三个 DLQ 基线、mor_tax 种子、块 146 状态
  before      【发布前】建一笔现网同形演示单（STANDARD 税类、声明 US、12.34 USD）：记录状态码、金额与状态（零税 Mock 时代基线）
  after       【发布后】同一请求再建一笔（期望 201，amountStatus=BLOCKED、MARKET_NOT_ENABLED）+ 声明 AE 的 STANDARD 单
              （期望 BLOCKED、UNKNOWN_TAX_CATEGORY）——即 stage6-report D-1 的影响实证；存量 PAID 订单 Client GET（FINAL、税 0）与 tax 查询 404
  scenario    S1 沙特：SAAS_AUTOMATED 100.00、声明 AE → 暂估 5.00 / 105.00 ESTIMATED；资料提交 SA → FINAL 15.00 / 115.00，合同重渲染；
              S2 科威特：资料提交 KW → BLOCKED MARKET_NOT_ENABLED，付款提交 409；tax 订单查询两笔（计算 2 条、无交易）
  synthetic   为 S1 订单生成 billing B 同款的合成 OrderPaymentSucceeded 参数（params-synthetic.json：真实订单 / 上下文 / FINAL 计算与税务字段 +
              DEMO-CL-001 现有效的 PLATFORM 形商业字段 + 合成支付 ID 段 9999000000000003xx）；默认只打印信封；--publish --yes 才向
              acquiring.events 发布 1 条（tax 与 billing 同时消费）。发布前必须先跑 billing 的上游冒烟（runbook ⑯）——stage6-report D-2
  check       发布后核对：tax 正式交易 + 底稿 PENDING(FX_RATE_MISSING) + 税票 ISSUED（补偿任务）、tax 订单查询含交易与单据；billing PAYMENT
              事实含 SELLER_TAX 15.00（收方 TAX_AUTHORITY 880000000702）与 TAX_CALCULATION 引用；account 入账；ledger TAX_PAYABLE 贷 15.00；
              tax / billing / account DLQ 与基线相比无新增
用法：python3 -I tax_api.py --step <步骤> [--dry-run] [--publish --yes]
  合成事件的 consumerCountryCode 只给 billing 取价用（tax 不读）：缺省 SA；冒烟若 CLIENT_PRICING 对 SA 返回 422，以 TAX_SYN_CONSUMER_COUNTRY=AE
  重新生成并重跑冒烟（billing B 用 AE 通过），在结果中注明。
时间：合成事件的 commercialContextTime / paymentSucceededTime 取执行时刻（≥ DEMO_NI_COST 生效 2026-10-23T07:42:56Z，在 DEMO-CL-001
  CLIENT_PRICING v1 有效期 2026-10-20—2026-11-19 内）；ledger 2026-11 期间为 FUTURE，**必须在 2026-10-31 23:59Z 前执行**，否则过账失败进 DLQ。
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
MOR_ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))
sys.path.insert(0, str(MOR_ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
BILLING_B = MOR_ROOT / "requirements/20261022/billing-支付事实自动登记/release/acceptance"

OUT = HERE / "tax-api-result.json"
LOG = HERE / "tax-api.txt"
PARAMS = HERE / "params-synthetic.json"
PROBE_IMAGE = os.environ.get("TAX_PROBE_IMAGE", "sha256:cf9097ea8685fbb2231fd97f49a1993b1aa4b6f30ba628e76e929dfa155a11c7")  # 现网 payment，只用其 JDK
CLIENT_API = "/client-gateway-api/v1"
CUSTOMER_API = "/customer-gateway-api/v1/checkouts"
CLIENT_ID = "237575207592763393"      # DEMO-CL-001（billing B 合成事件同一 Client；MOCK 下建单不依赖其合同）
AE001_PARTY = "880000000702"
EXISTING_PAID_ORDER_ID = "237575249045078016"   # 2026-10-18 已付存量订单（零税 Mock 快照）
CARD = {"pan": "4012888888881881", "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}
SERVICES = ["tax-service", "acquiring-service", "billing-service", "account-service", "operations-console-bff", "operations-console-web"]
CANDIDATE = {"tax-service": "a5616e20b18c", "acquiring-service": "843382e35d40", "billing-service": "adfd081728ae",
             "account-service": "8a21dab8e54b", "operations-console-bff": "e243d57bd540", "operations-console-web": "748ca7b9c6b4"}
BLOCK = "900000000000146"
DLQS = ["tax.acquiring.dlq", "billing.acquiring.dlq", "account.billing.dlq"]
# billing B（20261022）合成事件中现仍有效的 PLATFORM 形商业字段与渠道坐标（阶段 6 只读核对：CLIENT_PRICING v1 ACTIVE 至 2026-11-19、
# DEMO_NI_COST v1 ACTIVE、清算账户 NI-AE001-USD ACTIVE、AE001 USD 账簿 OPEN、2026-10 期间 OPEN）。
COMMERCIAL = {"entitySourceType": "PARTNER", "localPartnerId": None, "industryCode": "DIGITAL_GOODS", "mccCode": "5816",
              "clientAgreementId": "238331035912310784", "clientPricingVersionId": "238331390620405760", "partnerAgreementId": None,
              "partnerAgreementOperatingEntityId": None, "partnerShareVersionId": None, "platformChargeVersionId": None,
              "platformServicePartyId": None}
CHANNEL = {"paymentMethodCode": "APPLE_PAY", "channelId": "238508277900615680", "channelAccountId": "238508278357794816",
           "midId": "238508278684950528", "channelReferenceType": "PAY", "cardBrand": None, "fundingType": None, "cardLevel": None}
SYNTHETIC = {"paymentIntentId": "999900000000000301", "paymentAttemptId": "999900000000000302",
             "channelTransactionRefId": "999900000000000303", "channelTransactionRef": "SYN-TAX-P-0001"}
DRY = False
SECRETS = set()


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")[:-4] + "Z"


def scrub(text):
    text = str(text)
    for secret in SECRETS:
        if secret:
            text = text.replace(secret, "<redacted>")
    text = re.sub(r"chk_[A-Za-z0-9]{8,}", "chk_<redacted>", text)
    return text.replace(CARD["pan"], "<pan>")


def log(line):
    line = scrub(f"{now()} {line}")
    print(line, flush=True)
    if not DRY:
        with LOG.open("a") as handle:
            handle.write(line + "\n")


def load():
    return json.loads(OUT.read_text()) if OUT.exists() else {}


def save(name, data):
    if DRY:
        log(f"[dry-run] {name} 不写结果文件")
        return
    results = load()
    results[name] = data
    OUT.write_text(scrub(json.dumps(results, ensure_ascii=False, indent=1)) + "\n")
    log(f"{name} saved verdict={json.dumps(data.get('verdict'), ensure_ascii=False)}")


def verdict(checks):
    failed = [name for name, ok in checks.items() if not ok]
    return "PASS" if not failed else "FAIL: " + ", ".join(failed)


def h():
    import e2e_http as module  # 延迟导入：--help / --dry-run 不需要它
    module.PROBE_IMAGE = PROBE_IMAGE
    return module


# ---------- 只读与调用 ----------

def sql(service, statement):
    if not statement.lstrip().upper().startswith("SELECT"):
        raise SystemExit("阶段 7 验收只允许只读 SELECT")
    if DRY:
        log(f"[dry-run] SQL {service}: {statement[:220]}")
        return []
    return h().sql(service, statement)


def rows(service, statement, keys):
    return [dict(zip(keys, r)) for r in sql(service, statement)]


def call(service, method, path, headers=None, body=None):
    headers = dict(headers or {})
    headers.setdefault("Accept", "application/json")
    headers.setdefault("X-Request-Id", "req-tax7-" + uuid.uuid4().hex[:12])
    if DRY:
        log(f"[dry-run] {method} {service}{path} headers={sorted(headers)} body={json.dumps(body, ensure_ascii=False)[:300]}")
        return 0, {}
    try:
        status, text = h().private_call(service, method, path, headers=headers, body=body)
    except RuntimeError as failure:
        raise RuntimeError(scrub(failure)) from None
    try:
        return status, (json.loads(text) if text else {})
    except ValueError:
        return status, {"raw": scrub(text[:300])}


def client(method, path, body=None):
    return call("acquiring", method, CLIENT_API + path, {"X-Gateway-Client-Id": CLIENT_ID, "X-Gateway-Credential-Id": "1"}, body)


def customer(method, token, path="", body=None):
    return call("acquiring", method, f"{CUSTOMER_API}/{token}{path}", {"User-Agent": "Mozilla/5.0 (Code-MoR tax stage7 mor-test)"}, body)


def wait_for(fn, seconds=60, step=2.0):
    deadline = time.time() + seconds
    while time.time() < deadline:
        value = fn()
        if value:
            return value
        if DRY:
            return None
        time.sleep(step)
    return fn()


def queues():
    if DRY:
        return {}
    return {name: {"messages": m, "consumers": c} for name, m, c in h().rabbit_queues("")
            if name.startswith(("tax.acquiring", "billing.acquiring", "account.billing"))}


# ---------- 动作 ----------

def create_checkout(label, declared, category="SAAS_AUTOMATED", unit="100.00"):
    client_order_id = f"tax-s7-{label.lower()}-{uuid.uuid4().hex[:8]}"
    body = {"clientOrderId": client_order_id, "currency": "USD", "consumerCountryCode": declared,
            "items": [{"clientItemId": "line-1", "name": "Automated SaaS subscription (tax stage 7)", "productTypeCode": "SERVICE",
                       "taxCategoryCode": category, "quantity": "1", "unitAmount": unit}]}
    status, response = client("POST", "/checkouts", body)
    response = response or {}
    url = response.get("checkoutUrl") or ""
    token = url.rstrip("/").split("/")[-1].split("?")[0] if url else None
    if token:
        SECRETS.add(token)
    return token, {"clientOrderId": client_order_id, "http": status, "code": response.get("code"), "orderNo": response.get("orderNo"),
                   "orderStatus": response.get("orderStatus"), "amountStatus": response.get("amountStatus"),
                   "amounts": response.get("amounts"), "taxBlockReason": response.get("taxBlockReason")}


def save_consumer(token, country):
    address = {"SA": {"countryCode": "SA", "addressLine1": "King Fahd Road 1", "city": "Riyadh", "postalCode": "12211"},
               "KW": {"countryCode": "KW", "addressLine1": "Gulf Road 1", "city": "Kuwait City", "postalCode": "13001"}}[country]
    status, body = customer("PUT", token, "/consumer", {"fullName": "Stage Seven Consumer",
                                                        "email": f"tax-s7-{uuid.uuid4().hex[:8]}@example.invalid",
                                                        "billingAddress": address})
    body = body or {}
    return {"http": status, "code": body.get("code"), "amountStatus": body.get("amountStatus"), "amounts": body.get("amounts"),
            "taxBlockReason": body.get("taxBlockReason"), "allowedActions": body.get("allowedActions")}


def view(token):
    status, body = customer("GET", token)
    body = body or {}
    return {"http": status, "amountStatus": body.get("amountStatus"), "amounts": body.get("amounts"),
            "allowedActions": body.get("allowedActions"), "contractId": (body.get("legal") or {}).get("consumerContractId")}


def order_facts(order_no):
    o = rows("acquiring", "SELECT order_id, order_status, amount_status, IFNULL(tax_amount,'NULL'), IFNULL(total_amount,'NULL'), version "
                          f"FROM t_commerce_order WHERE order_no='{order_no}'",
             ("orderId", "status", "amountStatus", "taxAmount", "total", "version"))
    if not o:
        return None
    facts = o[0]
    oid = facts["orderId"]
    facts["taxCalculations"] = rows("acquiring", "SELECT revision, tax_calculation_id, calculation_stage, tax_status, basis_country_code, "
                                                 "IFNULL(tax_amount,'NULL'), IFNULL(total_amount,'NULL'), IFNULL(block_reason_code,'NULL'), "
                                                 "IFNULL(components_json,'NULL') FROM t_order_tax_calculation "
                                                 f"WHERE order_id={oid} ORDER BY revision",
                                    ("revision", "taxCalculationId", "stage", "status", "basisCountry", "taxAmount", "total",
                                     "blockReason", "components"))
    facts["contracts"] = rows("acquiring", "SELECT consumer_contract_id, IFNULL(tax_calculation_id,'NULL') FROM t_consumer_contract "
                                           f"WHERE order_id={oid} ORDER BY consumer_contract_id", ("contractId", "taxCalculationId"))
    ctx = rows("acquiring", "SELECT order_context_id, IFNULL(finance_tax_snapshot_id,'NULL'), IFNULL(initial_tax_calculation_id,'NULL'), "
                            f"consumer_country_code FROM t_order_context WHERE order_id={oid}",
               ("orderContextId", "financeTaxSnapshotId", "initialTaxCalculationId", "consumerCountryCode"))
    facts["context"] = ctx[0] if ctx else None
    return facts


def tax_facts(order_no):
    calcs = rows("tax", "SELECT id, source_object_id, source_revision, calculation_stage, status, IFNULL(charged_tax_amount,'NULL'), "
                        "IFNULL(gross_amount,'NULL'), IFNULL(tax_subject_party_id,'NULL'), IFNULL(block_reasons_json,'NULL') "
                        f"FROM t_tax_calculation WHERE source_object_no='{order_no}' ORDER BY source_revision",
                 ("calculationId", "orderId", "revision", "stage", "status", "taxAmount", "gross", "subjectPartyId", "blockReasons"))
    ids = ",".join(c["calculationId"] for c in calcs) or "0"
    txs = rows("tax", "SELECT id, calculation_id, source_fact_id, origin, reporting_status, IFNULL(reporting_block_reason,'NULL') "
                      f"FROM t_tax_transaction WHERE calculation_id IN ({ids})",
               ("transactionId", "calculationId", "factId", "origin", "reportingStatus", "reportingBlockReason"))
    tids = ",".join(t["transactionId"] for t in txs) or "0"
    entries = rows("tax", f"SELECT id, entry_role, source_tax_amount FROM t_tax_reporting_entry WHERE transaction_id IN ({tids})",
                   ("entryId", "role", "sourceTaxAmount"))
    docs = rows("tax", "SELECT id, series_code, IFNULL(document_number,'NULL'), issue_status, IFNULL(document_file_address,'NULL') "
                       f"FROM t_tax_document WHERE transaction_id IN ({tids})", ("documentId", "series", "number", "issueStatus", "fileAddress"))
    return {"calculations": calcs, "transactions": txs, "entries": entries, "documents": docs}


def billing_facts(order_id):
    fact_ids = [r[0] for r in sql("billing", f"SELECT fact_id FROM t_billing_fact_ref WHERE ref_type='ORDER' AND ref_id={order_id}")]
    ids = ",".join(fact_ids) or "0"
    facts = rows("billing", f"SELECT id, fact_type, status, currency, gross_amount FROM t_billing_fact WHERE id IN ({ids})",
                 ("factId", "type", "status", "currency", "gross"))
    lines = rows("billing", "SELECT fact_id, line_no, component_type, payer_role, payee_role, IFNULL(payee_id,'NULL'), posted_amount, "
                            f"IFNULL(source_evidence,'NULL') FROM t_billing_fact_line WHERE fact_id IN ({ids}) ORDER BY fact_id, line_no",
                 ("factId", "lineNo", "component", "payer", "payee", "payeeId", "posted", "evidence"))
    refs = rows("billing", f"SELECT fact_id, ref_type, ref_id FROM t_billing_fact_ref WHERE fact_id IN ({ids}) ORDER BY id",
                ("factId", "refType", "refId"))
    return {"factIds": fact_ids, "facts": facts, "lines": lines, "refs": refs}


def account_facts(fact_ids):
    ids = ",".join(fact_ids) or "0"
    postings = rows("account", f"SELECT id, entity_type, entity_id, currency, fact_id, entry_count FROM t_account_posting WHERE fact_id IN ({ids})",
                    ("postingId", "entityType", "entityId", "currency", "factId", "entryCount"))
    return {"postings": postings}


def ledger_facts(fact_ids):
    ids = ",".join(fact_ids) or "0"
    instr = rows("ledger", "SELECT id, instruction_type, status, economic_fact_id FROM t_accounting_instruction "
                           f"WHERE economic_fact_id IN ({ids})", ("instructionId", "type", "status", "economicFactId"))
    iids = ",".join(i["instructionId"] for i in instr) or "0"
    lines = rows("ledger", "SELECT j.instruction_id, l.line_no, c.account_code, l.entry_side, l.amount FROM t_journal_entry j "
                           "JOIN t_journal_line l ON l.journal_entry_id = j.id JOIN t_ledger_account la ON la.id = l.account_id "
                           f"JOIN t_chart_account c ON c.id = la.chart_account_id WHERE j.instruction_id IN ({iids}) ORDER BY j.id, l.line_no",
                 ("instructionId", "lineNo", "accountCode", "side", "amount"))
    return {"instructions": instr, "journalLines": lines}


def image(service):
    if DRY:
        return "<dry-run>"
    return subprocess.run(["docker", "inspect", f"mor-test-{service}-1", "--format", "{{.Image}}"],
                          capture_output=True, text=True, check=True).stdout.strip()


# ---------- 步骤 ----------

def step_preflight():
    images = {s: image(s) for s in SERVICES}
    q = queues()
    seeds = sql("tax", "SELECT (SELECT COUNT(*) FROM t_tax_category), (SELECT COUNT(*) FROM t_tax_market WHERE collection_status='ENABLED'), "
                       "(SELECT COUNT(*) FROM t_tax_registration), (SELECT COUNT(DISTINCT subject_party_id) FROM t_tax_market), "
                       "(SELECT MIN(subject_party_id) FROM t_tax_market)")
    block = sql("platform", f"SELECT status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN {BLOCK}401 AND {BLOCK}402 GROUP BY status")
    out = {"at": now(), "images": images, "queues": q, "taxSeeds": seeds, "block146Routes": block,
           "dlqBaseline": {d: (q.get(d) or {}).get("messages") for d in DLQS}}
    if not DRY:
        partitions = [q.get(f"tax.acquiring.p{i}", {}).get("consumers", 0) for i in range(4)]
        out["verdict"] = verdict({
            "images = 候选": all(images[s].startswith("sha256:" + CANDIDATE[s]) for s in SERVICES),
            "tax.acquiring.p0..p3 各 1 消费者": partitions == [1, 1, 1, 1],
            "tax 种子 1/3/3/1 主体 880000000702": bool(seeds) and seeds[0] == ["1", "3", "3", "1", AE001_PARTY],
            "块 146 路由 ACTIVE 2": block == [["ACTIVE", "2"]]})
    save("preflight", out)


def step_before():
    token, created = create_checkout("BEFORE", "US", category="STANDARD", unit="12.34")
    save("before", {"at": now(), "create": created, "note": "发布前基线：零税 Mock，amountStatus 由旧代码推导",
                    "verdict": "RECORDED" if created["http"] == 201 else f"UNEXPECTED {created['http']}"})


def step_after():
    _, us = create_checkout("AFTER-US", "US", category="STANDARD", unit="12.34")
    _, ae = create_checkout("AFTER-AE", "AE", category="STANDARD", unit="12.34")
    old = rows("acquiring", "SELECT order_no, amount_status, tax_amount, total_amount FROM t_commerce_order "
                            f"WHERE order_id={EXISTING_PAID_ORDER_ID}", ("orderNo", "amountStatus", "taxAmount", "total"))
    tax_status = None
    if old:
        tax_status, _ = call("tax", "GET", f"/tax/v1/orders/{old[0]['orderNo']}")
    before = (load().get("before") or {}).get("create") or {}
    out = {"at": now(), "standardUS": us, "standardAE": ae, "before": before, "existingPaidOrder": old, "existingTaxQueryHttp": tax_status}
    if not DRY:
        out["verdict"] = verdict({
            "STANDARD/US 建单 201": us["http"] == 201,
            "STANDARD/US 发布后 BLOCKED（发布前为零税）": us["amountStatus"] == "BLOCKED",
            "STANDARD/AE 建单 201 且 BLOCKED": ae["http"] == 201 and ae["amountStatus"] == "BLOCKED",
            "存量 PAID 订单 FINAL、税 0": bool(old) and old[0]["amountStatus"] == "FINAL" and old[0]["taxAmount"].startswith("0.00"),
            "存量订单 tax 查询 404": tax_status == 404})
    save("after", out)


def step_scenario():
    out = {"at": now()}
    token, created = create_checkout("SA", "AE")
    out["S1"] = {"create": created}
    if token:
        out["S1"]["consumer"] = save_consumer(token, "SA")
        out["S1"]["view"] = wait_for(lambda: (lambda v: v if v.get("contractId") else None)(view(token)), 30) or view(token)
        out["S1"]["acquiring"] = order_facts(created["orderNo"])
        status, body = call("tax", "GET", f"/tax/v1/orders/{created['orderNo']}")
        out["S1"]["taxQuery"] = {"http": status, "calculations": len((body or {}).get("calculations") or []),
                                 "transactions": len((body or {}).get("transactions") or [])}
    token2, created2 = create_checkout("KW", "AE")
    out["S2"] = {"create": created2}
    if token2:
        out["S2"]["consumer"] = save_consumer(token2, "KW")
        contract = view(token2).get("contractId")
        status, body = customer("POST", token2, "/payments", {"legalConfirmation": {"consumerContractId": contract or "0", "confirmed": True},
                                                              "card": CARD})
        out["S2"]["payment"] = {"http": status, "code": (body or {}).get("code")}
        out["S2"]["acquiring"] = order_facts(created2["orderNo"])
    if not DRY:
        s1, s2 = out["S1"], out["S2"]
        calcs = (s1.get("acquiring") or {}).get("taxCalculations") or []
        out["verdict"] = verdict({
            "S1 建单 201 ESTIMATED 105.00": created["http"] == 201 and created["amountStatus"] == "ESTIMATED"
                                           and (created["amounts"] or {}).get("totalAmount") == "105.00",
            "S1 资料 SA → FINAL 15.00 / 115.00": (s1.get("consumer") or {}).get("amountStatus") == "FINAL"
                                                and ((s1.get("consumer") or {}).get("amounts") or {}).get("taxAmount") == "15.00"
                                                and ((s1.get("consumer") or {}).get("amounts") or {}).get("totalAmount") == "115.00",
            "S1 计算 r1 PROVISIONAL / r2 FINAL READY SA": [(c["stage"], c["status"], c["basisCountry"]) for c in calcs]
                                                         == [("PROVISIONAL", "READY", "AE"), ("FINAL", "READY", "SA")],
            "S1 合同 2 份，最新绑定 r2": len((s1.get("acquiring") or {}).get("contracts") or []) == 2,
            "S1 tax 查询 200（2 计算 0 交易）": s1.get("taxQuery") == {"http": 200, "calculations": 2, "transactions": 0},
            "S2 资料 KW → BLOCKED MARKET_NOT_ENABLED": (s2.get("consumer") or {}).get("amountStatus") == "BLOCKED",
            "S2 付款 409": (s2.get("payment") or {}).get("http") == 409})
    save("scenario", out)


def build_params():
    scenario = (load().get("scenario") or {}).get("S1") or {}
    order_no = (scenario.get("create") or {}).get("orderNo")
    if not order_no and not DRY:
        raise SystemExit("先执行 --step scenario（S1 订单）")
    facts = order_facts(order_no or "<S1>") or {}
    final = next((c for c in facts.get("taxCalculations") or [] if c["stage"] == "FINAL" and c["status"] == "READY"), None)
    if not final and not DRY:
        raise SystemExit("S1 订单没有 FINAL READY 计算")
    stamp = datetime.now(timezone.utc).replace(microsecond=0).strftime("%Y-%m-%dT%H:%M:%SZ")
    if not stamp.startswith("2026-10-") and not DRY:
        raise SystemExit("ledger 2026-11 期间为 FUTURE：合成事件必须在 2026-10-31 23:59Z 前发布（stage6-report R-4）")
    components = json.loads(final["components"]) if final and final["components"] != "NULL" else []
    payload = {"orderId": facts.get("orderId"), "orderContextId": (facts.get("context") or {}).get("orderContextId"),
               "clientId": CLIENT_ID, "entityNo": "AE001", **{k: COMMERCIAL[k] for k in ("entitySourceType", "localPartnerId", "industryCode", "mccCode")},
               "orderAmount": final["total"] if final else None, "currency": "USD", "commercialContextTime": stamp,
               **{k: COMMERCIAL[k] for k in ("clientAgreementId", "clientPricingVersionId", "partnerAgreementId",
                                             "partnerAgreementOperatingEntityId", "partnerShareVersionId", "platformChargeVersionId",
                                             "platformServicePartyId")},
               "financeTaxSnapshotId": None, "consumerCountryCode": os.environ.get("TAX_SYN_CONSUMER_COUNTRY", "SA"),
               "paymentIntentId": SYNTHETIC["paymentIntentId"], "paymentAttemptId": SYNTHETIC["paymentAttemptId"],
               "capturedAmount": final["total"] if final else None, "paymentSucceededTime": stamp,
               **{k: CHANNEL[k] for k in ("paymentMethodCode", "channelId", "channelAccountId", "midId")},
               "channelTransactionRef": SYNTHETIC["channelTransactionRef"], "channelTransactionRefId": SYNTHETIC["channelTransactionRefId"],
               **{k: CHANNEL[k] for k in ("channelReferenceType", "cardBrand", "fundingType", "cardLevel")},
               "taxCalculationId": final["taxCalculationId"] if final else None, "taxStatus": "READY", "taxScopeKey": "SALE",
               "taxAmount": final["taxAmount"] if final else None, "taxComponents": components}
    for amount_field in ("orderAmount", "capturedAmount", "taxAmount"):
        if payload[amount_field]:
            payload[amount_field] = f"{float(payload[amount_field]):.2f}"
    for component in components:
        component["taxAmount"] = f"{float(component['taxAmount']):.2f}"
    version = int(facts.get("version") or 0) + 1
    return {"_comment": "tax 阶段 7 合成事件（stage6-report D-2）：S1 真实订单 / 上下文 / FINAL 计算 + DEMO-CL-001 PLATFORM 形商业字段 + 合成支付 ID 段 9999000000000003xx",
            "aggregateVersion": version, "instanceId": "acquiring-acceptance", "payload": payload}


def canonical(value):
    """RFC 8785：本载荷只含 ASCII 字符串、null 与对象数组，按键排序、无空白序列化即为规范形（同 billing B 脚本，扩展到嵌套）。"""
    text = json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
    if not text.isascii():
        raise SystemExit("payload 含非 ASCII 字符，不能用简化 RFC 8785")
    return text.encode()


def envelope_of(params):
    payload = params["payload"]
    occurred = datetime.strptime(payload["paymentSucceededTime"], "%Y-%m-%dT%H:%M:%SZ").strftime("%Y-%m-%dT%H:%M:%S.%f") + "Z"
    return {"eventId": f"ORDER:{payload['orderId']}:OrderPaymentSucceeded:{params['aggregateVersion']}",
            "eventType": "OrderPaymentSucceeded", "schemaVersion": 1, "aggregateType": "ORDER", "aggregateId": payload["orderId"],
            "aggregateVersion": params["aggregateVersion"], "occurredAt": occurred, "sourceService": "acquiring",
            "instanceId": params["instanceId"], "payloadHash": "sha256:" + hashlib.sha256(canonical(payload)).hexdigest(), "payload": payload}


def publish_envelope(envelope):
    """经 RabbitMQ 管理接口发布（属性与 RabbitDomainEventPublisher / billing B 脚本相同：message_id、type、content_encoding、x-aggregate-id）。"""
    import base64
    import urllib.request
    module = h()
    auth = base64.b64encode(f"{module.LOCAL['MOR_RABBITMQ_USER']}:{module.LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    vhost = urllib.request.quote(module.RABBITMQ_VHOST, safe="")
    body = {"properties": {"message_id": envelope["eventId"], "type": envelope["eventType"], "content_type": "application/json",
                           "content_encoding": "UTF-8", "delivery_mode": 2, "headers": {"x-aggregate-id": envelope["aggregateId"]}},
            "routing_key": envelope["aggregateId"], "payload": json.dumps(envelope, separators=(",", ":"), ensure_ascii=False),
            "payload_encoding": "string"}
    request = urllib.request.Request(f"{module.RABBITMQ_MANAGEMENT}/api/exchanges/{vhost}/acquiring.events/publish",
                                     data=json.dumps(body).encode(), method="POST",
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read()).get("routed") is True


def step_synthetic(publish, yes):
    params = build_params()
    if not DRY:
        PARAMS.write_text(json.dumps(params, ensure_ascii=False, indent=2) + "\n")
    envelope = envelope_of(params) if not DRY else {"payload": params["payload"]}
    log("envelope " + json.dumps({k: v for k, v in envelope.items() if k != "payload"}, ensure_ascii=False))
    log("payload " + json.dumps(params["payload"], ensure_ascii=False))
    log("上游冒烟（只读，发布前必须 4 项 200）：docker run --rm --network mor-test-backend -v "
        f"\"{BILLING_B}:/b:ro\" -v \"{HERE}:/a:ro\" python:3.13-alpine python -I /b/smoke_upstreams.py /a/params-synthetic.json")
    if not publish:
        log("未发布（加 --publish --yes）")
        return
    if not yes:
        raise SystemExit("--publish 需要同时给 --yes（阶段 7 用户授权后）")
    q = queues()
    for name in [f"tax.acquiring.p{i}" for i in range(4)] + [f"billing.acquiring.p{i}" for i in range(4)]:
        if (q.get(name) or {}).get("consumers", 0) < 1:
            raise SystemExit(f"{name} 没有消费者，停止")
    routed = publish_envelope(envelope)
    save("synthetic", {"at": now(), "eventId": envelope["eventId"], "payloadHash": envelope["payloadHash"], "routed": routed,
                       "dlqBefore": {d: (q.get(d) or {}).get("messages") for d in DLQS}, "verdict": "PUBLISHED" if routed else "NOT ROUTED"})


def step_check():
    synthetic = load().get("synthetic") or {}
    params = json.loads(PARAMS.read_text()) if PARAMS.exists() else {"payload": {}}
    p = params["payload"]
    order_no = ((load().get("scenario") or {}).get("S1") or {}).get("create", {}).get("orderNo")
    tax = wait_for(lambda: (lambda t: t if t["documents"] and t["documents"][0]["issueStatus"] == "ISSUED" else None)(tax_facts(order_no or "<S1>")),
                   120, 5) or tax_facts(order_no or "<S1>")
    billing = wait_for(lambda: (lambda b: b if b["facts"] else None)(billing_facts(p.get("orderId") or 0)), 120, 5) or billing_facts(p.get("orderId") or 0)
    account = wait_for(lambda: (lambda a: a if a["postings"] else None)(account_facts(billing["factIds"])), 60, 5) or account_facts(billing["factIds"])
    ledger = wait_for(lambda: (lambda l: l if l["journalLines"] else None)(ledger_facts(billing["factIds"])), 60, 5) or ledger_facts(billing["factIds"])
    status, body = call("tax", "GET", f"/tax/v1/orders/{order_no}") if order_no else (None, {})
    q = queues()
    out = {"at": now(), "tax": tax, "billing": billing, "account": account, "ledger": ledger,
           "taxQuery": {"http": status, "transactions": len((body or {}).get("transactions") or []),
                        "documents": sum(len(x.get("documents") or []) for x in ((body or {}).get("transactions") or []))},  # 单据挂在 transactions[].documents（2026-10-30 阶段 7 修正）
           "dlq": {d: (q.get(d) or {}).get("messages") for d in DLQS}}
    if not DRY:
        seller_tax = [line for line in billing["lines"] if line["component"] == "SELLER_TAX"]
        tax_payable = [line for line in ledger["journalLines"] if line["accountCode"] == "TAX_PAYABLE"]
        before = synthetic.get("dlqBefore") or {}
        out["verdict"] = verdict({
            "tax 正式交易 1（AUTO / ORIGINAL）": len(tax["transactions"]) == 1,
            "tax 底稿 PENDING(FX_RATE_MISSING)": bool(tax["transactions"]) and tax["transactions"][0]["reportingStatus"] == "PENDING",
            "税票 ISSUED（SA-RCPT-2026-*）": bool(tax["documents"]) and tax["documents"][0]["issueStatus"] == "ISSUED"
                                            and tax["documents"][0]["number"].startswith("SA-RCPT-2026-"),
            "billing PAYMENT 事实 1": len(billing["facts"]) == 1,
            "SELLER_TAX 15.00 → TAX_AUTHORITY 880000000702": len(seller_tax) == 1 and seller_tax[0]["posted"].startswith("15.00")
                                                             and seller_tax[0]["payee"] == "TAX_AUTHORITY" and seller_tax[0]["payeeId"] == AE001_PARTY,
            "TAX_CALCULATION 引用 = 冻结计算": any(r["refType"] == "TAX_CALCULATION" and r["refId"] == p.get("taxCalculationId") for r in billing["refs"]),
            "account 入账": bool(account["postings"]),
            "ledger TAX_PAYABLE 贷 15.00": any(line["side"] == "CREDIT" and line["amount"].startswith("15.00") for line in tax_payable),
            "tax 订单查询含交易与单据": out["taxQuery"] == {"http": 200, "transactions": 1, "documents": 1},
            "DLQ 无新增": all((out["dlq"].get(d) or 0) <= (before.get(d) or 0) for d in DLQS)})
    save("check", out)


def main():
    global DRY
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--step", required=True, choices=["preflight", "before", "after", "scenario", "synthetic", "check"])
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    DRY = args.dry_run
    log(f"===== step {args.step}{' (dry-run)' if DRY else ''}")
    {"preflight": step_preflight, "before": step_before, "after": step_after, "scenario": step_scenario,
     "synthetic": lambda: step_synthetic(args.publish, args.yes), "check": step_check}[args.step]()


if __name__ == "__main__":
    main()
