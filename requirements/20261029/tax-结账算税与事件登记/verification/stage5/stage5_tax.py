"""tax 结账算税与事件登记 · 阶段 5 本机联调（后端）场景驱动。

链路：Client 侧私网直连 acquiring Client API（带 Gateway 可信 Header X-Gateway-Client-Id / X-Gateway-Credential-Id，与 20261022、20261023
阶段 5 相同；本机没有可用的 Client API ck- 凭证）→ acquiring 建单暂估（tax POST /tax/v1/calculations）→ Customer API 保存消费者资料
（FINAL 重算）→ 法律接受 + 卡支付（Payment → card / risk-decision / channel-connector → Platform → 合成 PayerMax 18190）→ PaymentSucceeded
→ acquiring PAID → OrderPaymentSucceeded（acquiring.events）→ billing / tax 各自消费 → account → ledger。
库读取一律经 mor-local-mysql-1 root 只读查询（*_v2e2e）；不写业务库。不打印口令、卡号与 Checkout Public Token。

用法：python3 -I stage5_tax.py <场景>...   场景：s1 s2 s4pay s4check s5 probe
结果合并写入同目录 stage5-tax-result.json。
"""
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ENV_LOCAL = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local")
OUT = HERE / "stage5-tax-result.json"
ACQ = "http://127.0.0.1:18102/commerce"
CLIENT_API = ACQ + "/client-gateway-api/v1"
CUSTOMER_API = ACQ + "/customer-gateway-api/v1/checkouts"
TAX = "http://127.0.0.1:18116"
PLATFORM = "http://127.0.0.1:18106/platform-support"
CLIENT_ID = "237161687952666625"  # 本机验收 Client（20261022 / 20261023 阶段 5 同一个）
# 声明国家：MOCK 期间用 AE（暂估 AE 5%）；acquiring REAL 后该 Client 的 OE Scope 只覆盖 US（AE → NO_ELIGIBLE_ENTITY），改用 US。
DECLARED = __import__("os").environ.get("TAX5_DECLARED", "AE")
TEST_CARD = {"pan": "4444333322221111", "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}
results = {}
tokens = set()


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def root_password():
    for line in ENV_LOCAL.read_text().splitlines():
        if line.startswith("MOR_MYSQL_ROOT_PASSWORD="):
            return line.split("=", 1)[1]
    raise RuntimeError("MOR_MYSQL_ROOT_PASSWORD missing")


PW = root_password()


def sql(db, statement):
    """只读查询，返回行列表（字符串）；NULL 为 'NULL'。"""
    done = subprocess.run(["docker", "exec", "-i", "-e", "MYSQL_PWD=" + PW, "mor-local-mysql-1", "mysql", "-uroot", "-N", "-B",
                           "--default-character-set=utf8mb4", db, "-e", statement], capture_output=True, text=True, timeout=60)
    if done.returncode != 0:
        raise RuntimeError(f"sql {db}: {done.stderr.strip()[:300]}")
    return [line.split("\t") for line in done.stdout.splitlines() if line]


def rows(db, statement, keys):
    return [dict(zip(keys, r)) for r in sql(db, statement)]


def http(method, url, headers=None, body=None, timeout=40):
    headers = dict(headers or {})
    headers.setdefault("Accept", "application/json")
    headers.setdefault("X-Request-Id", "req-tax5-" + uuid.uuid4().hex[:12])
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode()
    elif method == "POST":
        data = b""
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            text = response.read()
            ctype = response.headers.get("Content-Type", "")
            return response.status, (json.loads(text) if text and "json" in ctype else {"_bytes": len(text), "_type": ctype})
    except urllib.error.HTTPError as failure:
        text = failure.read().decode(errors="replace")
        try:
            return failure.code, json.loads(text)
        except ValueError:
            return failure.code, {"raw": text[:300]}


def commerce(method, path, body=None):
    return http(method, CLIENT_API + path, {"X-Gateway-Client-Id": CLIENT_ID, "X-Gateway-Credential-Id": "1"}, body)


def customer(method, path, body=None):
    return http(method, CUSTOMER_API + path, {"User-Agent": "Mozilla/5.0 (Code-MoR tax stage5 local e2e)"}, body)


def token_of(url):
    token = (url or "").rstrip("/").split("/")[-1].split("?")[0]
    tokens.add(token)
    return token


def wait_for(fn, seconds=40, step=1.0):
    deadline = time.time() + seconds
    while time.time() < deadline:
        value = fn()
        if value:
            return value
        time.sleep(step)
    return fn()


def record(name, data):
    results[name] = data
    print(name, json.dumps(data, ensure_ascii=False)[:3000])


# ---------- 动作 ----------

def create_checkout(label, declared="AE", unit="100.00", category="SAAS_AUTOMATED", quantity="1"):
    client_order_id = f"tax5-{label}-{time.strftime('%H%M%S')}-{uuid.uuid4().hex[:6]}"
    request = {"clientOrderId": client_order_id, "currency": "USD", "consumerCountryCode": declared,
               "items": [{"clientItemId": "line-1", "name": "Automated SaaS subscription", "productTypeCode": "SERVICE",
                          "taxCategoryCode": category, "quantity": quantity, "unitAmount": unit}]}
    status, body = commerce("POST", "/checkouts", request)
    body = body or {}
    url = body.get("checkoutUrl")
    if url:
        token_of(url)
    return {"http": status, "code": body.get("code"), "orderNo": body.get("orderNo"), "amountStatus": body.get("amountStatus"),
            "amounts": body.get("amounts"), "orderStatus": body.get("orderStatus"), "clientOrderId": client_order_id}, url


def save_consumer(url, country):
    address = {"SA": {"countryCode": "SA", "addressLine1": "King Fahd Road 1", "city": "Riyadh", "postalCode": "12211"},
               "KW": {"countryCode": "KW", "addressLine1": "Gulf Road 1", "city": "Kuwait City", "postalCode": "13001"},
               "AE": {"countryCode": "AE", "addressLine1": "Sheikh Zayed Road 1", "city": "Dubai", "postalCode": "00000"}}[country]
    status, body = customer("PUT", f"/{token_of(url)}/consumer", {
        "fullName": "Stage Five Consumer", "email": f"consumer-{uuid.uuid4().hex[:8]}@example.invalid", "billingAddress": address})
    body = body or {}
    return {"http": status, "code": body.get("code"), "keys": sorted(body)[:30], "amountStatus": body.get("amountStatus"),
            "amounts": body.get("amounts"), "tax": body.get("tax"), "payment": body.get("payment"), "blockReasons": body.get("blockReasons")}


def view(url):
    status, body = customer("GET", f"/{token_of(url)}")
    body = body or {}
    return status, body


def brief_view(url):
    status, body = view(url)
    return {"http": status, "code": body.get("code"), "amountStatus": body.get("amountStatus"), "amounts": body.get("amounts"),
            "tax": body.get("tax"), "allowedActions": body.get("allowedActions"), "payment": body.get("payment"),
            "contractId": (body.get("legal") or {}).get("consumerContractId"), "preparationStatus": body.get("preparationStatus")}


def submit_payment(url):
    contract = wait_for(lambda: (lambda r: (r[1].get("legal") or {}).get("consumerContractId") if r[0] == 200 else None)(view(url)), 30)
    status, submitted = customer("POST", f"/{token_of(url)}/payments",
                                 {"legalConfirmation": {"consumerContractId": contract, "confirmed": True}, "card": TEST_CARD})
    submitted = submitted or {}
    out = {"http": status, "code": submitted.get("code"), "contractId": contract, "submissionId": submitted.get("submissionId"),
           "status": submitted.get("status"), "detail": submitted.get("detail"), "errors": submitted.get("errors")}
    if status not in (200, 202) or not submitted.get("submissionId"):
        return out
    sid = submitted["submissionId"]
    final = wait_for(lambda: (lambda r: r[1] if r[0] == 200 and (r[1] or {}).get("status") not in ("PROCESSING", "AWAITING_CARD", None)
                              else None)(customer("GET", f"/{token_of(url)}/payment-submissions/{sid}")), 60, 1)
    final = final or {}
    out.update(finalStatus=final.get("status"), attemptId=final.get("attemptId"), failure=(final.get("failure") or {}).get("code"))
    return out


# ---------- 事实读取 ----------

def order_facts(order_no):
    o = rows("mor_acquiring_v2e2e", "SELECT order_id, order_status, amount_status, consumer_country_code, net_amount, tax_amount, "
             f"total_amount, version, IFNULL(paid_at,'NULL') FROM t_commerce_order WHERE order_no='{order_no}'",
             ("orderId", "status", "amountStatus", "declaredCountry", "net", "taxAmount", "total", "version", "paidAt"))
    if not o:
        return None
    facts = o[0]
    oid = facts["orderId"]
    facts["taxCalculations"] = rows("mor_acquiring_v2e2e",
                                    "SELECT revision, tax_calculation_id, calculation_stage, tax_status, IFNULL(basis_country_code,'NULL'), "
                                    "IFNULL(tax_amount,'NULL'), IFNULL(total_amount,'NULL'), IFNULL(block_reason_code,'NULL'), "
                                    f"IFNULL(expires_at,'NULL') FROM t_order_tax_calculation WHERE order_id={oid} ORDER BY revision",
                                    ("revision", "taxCalculationId", "stage", "status", "basisCountry", "taxAmount", "total",
                                     "blockReason", "expiresAt"))
    facts["initialTaxCalculationId"] = (sql("mor_acquiring_v2e2e", "SELECT IFNULL(initial_tax_calculation_id,'NULL') FROM t_order_context "
                                            f"WHERE order_id={oid}") or [[None]])[0][0]
    facts["contracts"] = rows("mor_acquiring_v2e2e", "SELECT consumer_contract_id, IFNULL(tax_calculation_id,'NULL') FROM t_consumer_contract "
                              f"WHERE order_id={oid} ORDER BY consumer_contract_id", ("contractId", "taxCalculationId"))
    facts["projection"] = rows("mor_acquiring_v2e2e", "SELECT payment_intent_id, intent_status, IFNULL(successful_attempt_id,'NULL'), "
                               f"captured_amount FROM t_order_payment_projection WHERE order_id={oid}",
                               ("intentId", "intentStatus", "successfulAttemptId", "captured"))
    return facts


def tax_facts(order_no):
    calcs = rows("mor_tax_v2e2e", "SELECT id, source_revision, calculation_stage, status, IFNULL(charged_tax_amount,'NULL'), net_amount, "
                 "IFNULL(gross_amount,'NULL'), IFNULL(tax_subject_party_id,'NULL'), IFNULL(block_reasons_json,'NULL') "
                 f"FROM t_tax_calculation WHERE source_object_no='{order_no}' ORDER BY source_revision",
                 ("calculationId", "revision", "stage", "status", "taxAmount", "net", "gross", "subjectPartyId", "blockReasons"))
    ids = ",".join(c["calculationId"] for c in calcs) or "0"
    comps = rows("mor_tax_v2e2e", "SELECT id, calculation_id, jurisdiction_code, tax_type, treatment, rate, tax_base, charged_tax_amount "
                 f"FROM t_tax_calculation_component WHERE calculation_id IN ({ids}) ORDER BY id",
                 ("componentId", "calculationId", "jurisdiction", "taxType", "treatment", "rate", "base", "taxAmount"))
    txs = rows("mor_tax_v2e2e", "SELECT id, calculation_id, source_fact_type, source_fact_id, origin, transaction_kind, reporting_status, "
               f"IFNULL(reporting_block_reason,'NULL'), registered_at FROM t_tax_transaction WHERE calculation_id IN ({ids})",
               ("transactionId", "calculationId", "factType", "factId", "origin", "kind", "reportingStatus", "reportingBlockReason", "registeredAt"))
    tids = ",".join(t["transactionId"] for t in txs) or "0"
    entries = rows("mor_tax_v2e2e", "SELECT id, entry_role, source_tax_base, source_tax_amount, source_currency, "
                   "IFNULL(reporting_tax_amount,'NULL'), reporting_currency, IFNULL(fx_rate,'NULL') "
                   f"FROM t_tax_reporting_entry WHERE transaction_id IN ({tids})",
                   ("entryId", "role", "sourceTaxBase", "sourceTaxAmount", "sourceCurrency", "reportingTaxAmount", "reportingCurrency", "fxRate"))
    docs = rows("mor_tax_v2e2e", "SELECT id, series_code, IFNULL(document_number,'NULL'), issue_status, IFNULL(document_file_address,'NULL'), "
                f"IFNULL(issued_at,'NULL'), IFNULL(document_digest,'NULL') FROM t_tax_document WHERE transaction_id IN ({tids})",
                ("documentId", "series", "number", "issueStatus", "fileAddress", "issuedAt", "digest"))
    tasks = rows("mor_tax_v2e2e", "SELECT event_type, execute_status, execute_count FROM t_compensation_event", ("type", "status", "count"))
    return {"calculations": calcs, "components": comps, "transactions": txs, "entries": entries, "documents": docs, "compensation": tasks}


def billing_facts(order_id):
    fact_ids = [r[0] for r in sql("mor_billing_v2e2e", f"SELECT fact_id FROM t_billing_fact_ref WHERE ref_type='ORDER' AND ref_id={order_id}")]
    ids = ",".join(fact_ids) or "0"
    facts = rows("mor_billing_v2e2e", "SELECT id, fact_type, status, currency, gross_amount, source_event_key FROM t_billing_fact "
                 f"WHERE id IN ({ids})", ("factId", "type", "status", "currency", "gross", "sourceEventKey"))
    lines = rows("mor_billing_v2e2e", "SELECT fact_id, line_no, component_type, payer_role, payee_role, IFNULL(basis_amount,'NULL'), "
                 "IFNULL(rate_value,'NULL'), posted_amount, effect_direction, IFNULL(target_bucket,'NULL'), IFNULL(source_evidence,'NULL') "
                 f"FROM t_billing_fact_line WHERE fact_id IN ({ids}) ORDER BY fact_id, line_no",
                 ("factId", "lineNo", "component", "payer", "payee", "basis", "rate", "posted", "direction", "bucket", "evidence"))
    refs = rows("mor_billing_v2e2e", f"SELECT fact_id, ref_type, ref_id FROM t_billing_fact_ref WHERE fact_id IN ({ids}) ORDER BY id",
                ("factId", "refType", "refId"))
    return {"facts": facts, "lines": lines, "refs": [r for r in refs if r["refType"] in ("ORDER", "TAX_CALCULATION", "TAX_SNAPSHOT")]}


def account_facts(fact_ids):
    ids = ",".join(fact_ids) or "0"
    postings = rows("mor_account_v2e2e", "SELECT id, entity_type, entity_id, currency, source_type, fact_id, entry_count FROM t_account_posting "
                    f"WHERE fact_id IN ({ids})", ("postingId", "entityType", "entityId", "currency", "sourceType", "factId", "entryCount"))
    pids = ",".join(p["postingId"] for p in postings) or "0"
    entries = rows("mor_account_v2e2e", "SELECT e.posting_id, e.line_no, a.account_code, a.owner_type, a.balance_bucket, e.entry_side, e.amount "
                   f"FROM t_account_entry e JOIN t_owner_account a ON a.id = e.account_id WHERE e.posting_id IN ({pids}) ORDER BY e.posting_id, e.line_no",
                   ("postingId", "lineNo", "accountCode", "ownerType", "bucket", "side", "amount"))
    return {"postings": postings, "entries": entries}


def ledger_facts(fact_ids):
    ids = ",".join(fact_ids) or "0"
    instr = rows("mor_ledger_v2e2e", "SELECT id, instruction_type, source_system, status, economic_fact_id FROM t_accounting_instruction "
                 f"WHERE economic_fact_id IN ({ids})", ("instructionId", "type", "sourceSystem", "status", "economicFactId"))
    iids = ",".join(i["instructionId"] for i in instr) or "0"
    lines = rows("mor_ledger_v2e2e", "SELECT j.instruction_id, l.line_no, c.account_code, la.owner_type, la.balance_bucket, l.entry_side, l.amount "
                 "FROM t_journal_entry j JOIN t_journal_line l ON l.journal_entry_id = j.id JOIN t_ledger_account la ON la.id = l.account_id "
                 f"JOIN t_chart_account c ON c.id = la.chart_account_id WHERE j.instruction_id IN ({iids}) ORDER BY j.id, l.line_no",
                 ("instructionId", "lineNo", "accountCode", "ownerType", "bucket", "side", "amount"))
    return {"instructions": instr, "journalLines": lines}


def table_columns(db, table):
    return [r[0] for r in sql(db, f"SELECT column_name FROM information_schema.columns WHERE table_schema='{db}' AND table_name='{table}' "
                                  "ORDER BY ordinal_position")]


XXL_TOKEN_FILE = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/runtime/expiry/xxl-token")
XXL_TAX_LOG_DIR = Path("/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/runtime/tax/xxl-tax")


def tax_compensation_scan(timeout=60):
    """本机无 XXL 调度中心：按 Admin 协议向 tax 执行器 127.0.0.1:19993 POST /run 触发 compensationEventScan（与 20261022 阶段 5 同法）。"""
    log_id = int(time.time() * 1000)
    body = {"jobId": 9101, "executorHandler": "compensationEventScan", "executorParams": "", "executorBlockStrategy": "SERIAL_EXECUTION",
            "executorTimeout": 0, "logId": log_id, "logDateTime": log_id, "glueType": "BEAN", "glueSource": "", "glueUpdatetime": 0,
            "broadcastIndex": 0, "broadcastTotal": 1}
    request = urllib.request.Request("http://127.0.0.1:19993/run", method="POST", data=json.dumps(body).encode(),
                                     headers={"XXL-JOB-ACCESS-TOKEN": XXL_TOKEN_FILE.read_text().strip(), "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        accepted = json.loads(response.read())
    handle_code, text = None, ""
    deadline = time.time() + timeout
    while time.time() < deadline and handle_code is None:
        for path in XXL_TAX_LOG_DIR.glob(f"*/{log_id}.log"):
            text = path.read_text(errors="replace")
            ended = re.search(r"execute end[^\n]*handleCode=(\d+)", text)
            handle_code = int(ended.group(1)) if ended else None
        if handle_code is None:
            time.sleep(1)
    return {"logId": log_id, "runAccepted": accepted, "handleCode": handle_code}


def platform_file(file_address):
    """经 Platform 内部文件接口核对税票：tax-service 身份读元数据；以 ops-bff 身份（DUAL 回退头 + 合成操作人，等同运营下载链路的
    BFF → Platform 一步）签发 60 秒下载地址并直接 GET，核对 PDF 魔数与摘要。只记录状态与长度，不记录地址。"""
    import hashlib
    import ssl
    file_id = file_address.rsplit("/", 1)[-1]
    status, meta = http("GET", f"{PLATFORM}/internal/files/v1/files/{file_id}", {"X-Caller-Service": "tax-service"})
    out = {"metadata": {"http": status, **{k: (meta or {}).get(k) for k in ("fileId", "status", "mimeType", "size", "sizeBytes",
                                                                             "accessLevel", "sourceSystem", "reasonCode", "code")}}}
    status, auth = http("POST", f"{PLATFORM}/internal/files/v1/files/{file_id}/download?disposition=ATTACHMENT",
                        {"X-Caller-Service": "operations-console-bff", "X-Operator-Account-Id": "900000000000000001"})
    auth = auth or {}
    out["authorize"] = {"http": status, "code": auth.get("code"), "disposition": auth.get("disposition"), "expiresAt": auth.get("expiresAt"),
                        "keys": sorted(auth)}
    url = auth.get("url") or auth.get("downloadUrl") or ((auth.get("authorization") or {}).get("url"))
    if url:
        context = ssl.create_default_context(cafile="/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/runtime/tax/minio-ca.pem")
        with urllib.request.urlopen(urllib.request.Request(url, method="GET"), timeout=20, context=context) as response:
            content = response.read()
            out["download"] = {"http": response.status, "contentType": response.headers.get("Content-Type"), "bytes": len(content),
                               "pdfMagic": content[:5] == b"%PDF-", "sha256": hashlib.sha256(content).hexdigest(),
                               "hasArabic": any(ord(c) > 0x600 for c in content.decode("latin-1", errors="ignore")[:0])}
            text = content.decode("latin-1", errors="replace")
            out["download"]["mentions"] = {k: (k in text) for k in ("SA-RCPT-2026", "DEMO-SA-TRN-0001", "15.00", "115.00", "SAR", "USD")}
    return out


# ---------- 场景 ----------

def probe():
    """只读探测各库关键表列，供场景取数。"""
    out = {}
    for db, table in (("mor_billing_v2e2e", "t_billing_fact"), ("mor_billing_v2e2e", "t_billing_fact_line"),
                      ("mor_billing_v2e2e", "t_billing_fact_ref"), ("mor_account_v2e2e", "t_account_entry"),
                      ("mor_ledger_v2e2e", "t_journal_line"), ("mor_tax_v2e2e", "t_tax_calculation"),
                      ("mor_tax_v2e2e", "t_tax_transaction"), ("mor_tax_v2e2e", "t_tax_document"),
                      ("mor_acquiring_v2e2e", "t_order_tax_calculation"), ("mor_acquiring_v2e2e", "t_commerce_order")):
        out[f"{db}.{table}"] = table_columns(db, table)
    record("probe", out)


def downstream(order_no, order_id, wait=40):
    """billing → account → ledger 链路取数（等 billing 事实出现最多 wait 秒）。"""
    billing = wait_for(lambda: (lambda b: b if b["facts"] else None)(billing_facts(order_id)), wait, 2) or billing_facts(order_id)
    fact_ids = [f["factId"] for f in billing["facts"]]
    account = wait_for(lambda: (lambda a: a if a["postings"] else None)(account_facts(fact_ids)), 20 if fact_ids else 0, 2) \
        or account_facts(fact_ids)
    ledger = wait_for(lambda: (lambda l: l if l["journalLines"] else None)(ledger_facts(fact_ids)), 20 if fact_ids else 0, 2) \
        or ledger_facts(fact_ids)
    return {"billing": billing, "account": account, "ledger": ledger}


def queue_depths():
    done = subprocess.run(["docker", "exec", "mor-local-rabbitmq-1", "rabbitmqctl", "list_queues", "-p", "/mor-e2e", "name", "messages",
                           "consumers"], capture_output=True, text=True, timeout=60)
    keep = ("tax.acquiring", "billing.acquiring", "account.billing")
    return {r[0]: {"messages": r[1], "consumers": r[2]} for r in (l.split("\t") for l in done.stdout.splitlines())
            if len(r) == 3 and r[0].startswith(keep)}


def sa_order(label):
    """S1 / S4 共用：建单（声明 AE，暂估）→ 资料 SA（最终 15%）→ 支付；返回 (out, orderNo)。"""
    out = {}
    created, url = create_checkout(label, declared=DECLARED)
    out["created"] = created
    order_no = created["orderNo"]
    out["afterCreate"] = order_facts(order_no)
    out["saveConsumerSA"] = save_consumer(url, "SA")
    out["viewBeforePay"] = brief_view(url)
    out["afterSave"] = order_facts(order_no)
    out["payment"] = submit_payment(url)
    out["paid"] = wait_for(lambda: (lambda f: f if f and f["status"] == "PAID" else None)(order_facts(order_no)), 40) or order_facts(order_no)
    return out, order_no


def s1():
    """S1 沙特 MoR 全流程（AC-01、AC-03、AC-07、AC-09、AC-11）。"""
    out, order_no = sa_order("s1")
    time.sleep(3)
    out["taxAfterPay"] = tax_facts(order_no)
    if any(t["status"] == "ERROR" for t in out["taxAfterPay"]["compensation"]):
        out["scan"] = tax_compensation_scan()
    out["tax"] = wait_for(lambda: (lambda t: t if t["documents"] and t["documents"][0]["issueStatus"] == "ISSUED" else None)(
        tax_facts(order_no)), 30, 2) or tax_facts(order_no)
    docs = out["tax"]["documents"]
    if docs and docs[0]["fileAddress"] != "NULL":
        out["receiptFile"] = platform_file(docs[0]["fileAddress"])
    out["taxOrderApi"] = (lambda r: {"http": r[0], "calculations": len((r[1] or {}).get("calculations") or []),
                                     "transactions": len((r[1] or {}).get("transactions") or []),
                                     "currentStatus": (((r[1] or {}).get("calculations") or [{}])[-1]).get("status")})(
        http("GET", f"{TAX}/tax/v1/orders/{order_no}"))
    out["downstream"] = downstream(order_no, out["paid"]["orderId"])
    out["queues"] = queue_depths()
    final = out["afterSave"]["taxCalculations"][-1] if out["afterSave"]["taxCalculations"] else {}
    tax_final = [c for c in out["tax"]["calculations"] if c["stage"] == "FINAL"]
    txs = out["tax"]["transactions"]
    lines = out["downstream"]["billing"]["lines"]
    seller_tax = [l for l in lines if l["component"] == "SELLER_TAX"]
    out["verdict"] = {
        "createProvisional": out["afterCreate"]["taxCalculations"][0]["stage"] == "PROVISIONAL"
                             and out["afterCreate"]["taxCalculations"][0]["basisCountry"] == DECLARED
                             and (DECLARED != "AE" or (out["created"]["amounts"] or {}).get("taxAmount") == "5.00"),
        "finalSA15": out["saveConsumerSA"]["amountStatus"] == "FINAL" and (out["saveConsumerSA"]["amounts"] or {}).get("totalAmount") == "115.00"
                     and final.get("basisCountry") == "SA" and final.get("stage") == "FINAL",
        "contractRerendered": len(out["afterSave"]["contracts"]) == 2 and out["afterSave"]["contracts"][-1]["taxCalculationId"] == final.get("taxCalculationId"),
        "paid115": out["paid"]["status"] == "PAID" and (out["paid"]["projection"] or [{}])[0].get("captured") == "115.00000000",
        "formalFactOnFinal": len(txs) == 1 and tax_final and txs[0]["calculationId"] == tax_final[-1]["calculationId"],
        "reportingPendingFx": len(txs) == 1 and txs[0]["reportingStatus"] == "PENDING" and txs[0]["reportingBlockReason"] == "FX_RATE_MISSING",
        "receiptIssued": bool(docs) and docs[0]["issueStatus"] == "ISSUED",
        "receiptDownloadable": (out.get("receiptFile") or {}).get("download", {}).get("pdfMagic") is True
                               and out["receiptFile"]["download"]["sha256"] == docs[0]["digest"],
        "billingSellerTax15": len(seller_tax) >= 1 and sum(float(l["posted"]) for l in seller_tax) == 15.0,
        "ledgerTaxPayable": any("TAX" in l["accountCode"] for l in out["downstream"]["ledger"]["journalLines"])}
    record("S1", out | {"orderNo": order_no})


def s2():
    """S2 被阻断（AC-05）：账单国家 KW → BLOCKED，支付被拒，订单保持开放。"""
    out = {}
    created, url = create_checkout("s2")
    out["created"] = created
    order_no = created["orderNo"]
    out["saveConsumerKW"] = save_consumer(url, "KW")
    out["view"] = brief_view(url)
    out["payment"] = submit_payment(url)
    time.sleep(2)
    out["facts"] = order_facts(order_no)
    out["tax"] = tax_facts(order_no)
    last = out["facts"]["taxCalculations"][-1]
    out["verdict"] = {
        "finalBlocked": last["stage"] == "FINAL" and last["status"] == "BLOCKED" and last["basisCountry"] == "KW",
        "payRejected": out["payment"]["http"] >= 400 and not out["payment"].get("submissionId"),
        "orderOpen": out["facts"]["status"] == "OPEN" and not out["facts"]["projection"],
        "noFormalFact": not out["tax"]["transactions"]}
    record("S2", out | {"orderNo": order_no})


def s5():
    """S5 存量订单（AC-12）：替换前已支付的订单，Client GET 与 Customer 查看正常；tax 无该订单数据（不导入）。"""
    out = {"orders": []}
    for order_no, order_id, paid_at in sql("mor_acquiring_v2e2e", "SELECT order_no, order_id, paid_at FROM t_commerce_order "
                                           "WHERE order_status='PAID' AND create_time < '2026-10-29 18:21:00' ORDER BY paid_at"):
        status, body = commerce("GET", f"/orders/{order_no}")
        body = body or {}
        tax_status, tax_body = http("GET", f"{TAX}/tax/v1/orders/{order_no}")
        out["orders"].append({"orderNo": order_no, "paidAt": paid_at, "clientGet": {"http": status, "orderStatus": body.get("orderStatus"),
                              "paymentStatus": body.get("paymentStatus"), "amountStatus": body.get("amountStatus"),
                              "amounts": body.get("amounts"), "code": body.get("code")},
                              "taxCalculationRows": sql("mor_acquiring_v2e2e", f"SELECT COUNT(*) FROM t_order_tax_calculation WHERE order_id={order_id}")[0][0],
                              "taxOrderApi": {"http": tax_status, "code": (tax_body or {}).get("code")}})
    out["verdict"] = {"allClientGet200": all(o["clientGet"]["http"] == 200 for o in out["orders"]),
                      "amountStatusFinal": all(o["clientGet"]["amountStatus"] == "FINAL" for o in out["orders"]),
                      "noTaxImport": all(o["taxOrderApi"]["http"] == 404 for o in out["orders"])}
    record("S5", out)


PSP_CLIENT = "900000000001380201"  # 20261023 relsnap 本机种子：PSP 合同，TAX_RESPONSIBLE = Client 自身 Party


def s3():
    """S3 NOT_MANAGED（AC-04）：acquiring 临时 REAL 模式下 PSP Client 订单；tax 无该纳税主体准入 → NOT_MANAGED，可付、无正式事实。"""
    global CLIENT_ID
    saved, CLIENT_ID = CLIENT_ID, PSP_CLIENT
    try:
        out = {}
        created, url = create_checkout("s3", declared="US")  # PSP 合同的 OE Scope 不含 AE 声明国家（AE → NO_ELIGIBLE_ENTITY）
        out["created"] = created
        order_no = created["orderNo"]
        if created["http"] != 201:
            record("S3", out)
            return
        out["saveConsumerSA"] = save_consumer(url, "SA")
        out["view"] = brief_view(url)
        out["payment"] = submit_payment(url)
        out["paid"] = wait_for(lambda: (lambda f: f if f and f["status"] == "PAID" else None)(order_facts(order_no)), 40) or order_facts(order_no)
        time.sleep(5)
        out["tax"] = tax_facts(order_no)
        out["taxCalculationApi"] = (lambda r: {"http": r[0], "status": (r[1] or {}).get("status"),
                                               "notManagedReason": (r[1] or {}).get("notManagedReason"),
                                               "taxSubjectPartyId": (r[1] or {}).get("taxSubjectPartyId"),
                                               "taxAmount": (r[1] or {}).get("taxAmount")})(
            http("GET", f"{TAX}/tax/v1/calculations/{out['tax']['calculations'][-1]['calculationId']}"))
        out["downstream"] = downstream(order_no, out["paid"]["orderId"], 20)
        last = out["paid"]["taxCalculations"][-1]
        out["verdict"] = {
            "notManaged": last["status"] == "NOT_MANAGED" and out["taxCalculationApi"]["notManagedReason"] == "SUBJECT_NOT_REGISTERED",
            "paid": out["paid"]["status"] == "PAID",
            "noFormalFact": not out["tax"]["transactions"] and not out["tax"]["documents"],
            "noSellerTax": not any(l["component"] == "SELLER_TAX" for l in out["downstream"]["billing"]["lines"])}
        record("S3", out | {"orderNo": order_no})
    finally:
        CLIENT_ID = saved


SVC = "/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/efbf8c50-ce10-40da-924c-05707194ea2e/scratchpad/svc.py"
TAX_ENV = "/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/efbf8c50-ce10-40da-924c-05707194ea2e/scratchpad/env/tax.json"


def tax_ready():
    try:
        with urllib.request.urlopen("http://127.0.0.1:19116/actuator/health/readiness", timeout=2) as response:
            return json.loads(response.read()).get("status") == "UP"
    except (urllib.error.URLError, OSError, ValueError):
        return False


def s4():
    """S4 AC-10：tax 停机时付款 → billing 照常入账（含 SELLER_TAX）→ tax 恢复后从队列补登记，金额与 billing 一致。
    建单与资料提交需要 tax 报价，先在 tax 运行时完成；支付门禁只读 acquiring 已存报价，不回调 tax（X-1）。"""
    out = {"prepared": []}
    # 合成 PayerMax 对尝试 ID 末位 2/3/4 返回错误（webhook_flow 已知行为），先在 tax 运行时备好 3 笔报价完成的订单，停机后依次支付直到成功。
    prepared = []
    for i in range(3):
        created, url = create_checkout(f"s4-{i + 1}", declared=DECLARED)
        prepared.append((created, url, save_consumer(url, "SA")))
        out["prepared"].append({"orderNo": created["orderNo"], "amountStatus": prepared[-1][2]["amountStatus"],
                                "total": (prepared[-1][2]["amounts"] or {}).get("totalAmount")})
    out["taxStop"] = {"at": now(), "result": subprocess.run(["python3", SVC, "stop", "tax"], capture_output=True, text=True).stdout.strip()}
    out["taxReadyWhileStopped"] = tax_ready()
    out["paymentTries"] = []
    for created, url, saved in prepared:
        result = submit_payment(url)
        out["paymentTries"].append({"orderNo": created["orderNo"], **result})
        if result.get("finalStatus") == "SUCCEEDED":
            break
    out["created"], out["saveConsumerSA"], out["payment"] = created, saved, result
    order_no = created["orderNo"]
    out["paid"] = wait_for(lambda: (lambda f: f if f and f["status"] == "PAID" else None)(order_facts(order_no)), 40) or order_facts(order_no)
    out["downstreamWhileTaxDown"] = downstream(order_no, out["paid"]["orderId"], 40)
    out["queuesWhileTaxDown"] = queue_depths()
    out["taxWhileDown"] = tax_facts(order_no)
    out["taxStart"] = {"at": now(), "result": subprocess.run(["python3", SVC, "start", "tax", TAX_ENV], capture_output=True, text=True).stdout.strip()}
    out["taxReadyAfterStart"] = bool(wait_for(tax_ready, 60, 2))
    out["tax"] = wait_for(lambda: (lambda t: t if t["transactions"] else None)(tax_facts(order_no)), 60, 2) or tax_facts(order_no)
    if any(t["status"] == "ERROR" for t in out["tax"]["compensation"]):
        out["scan"] = tax_compensation_scan()
    out["tax"] = wait_for(lambda: (lambda t: t if t["documents"] and t["documents"][0]["issueStatus"] == "ISSUED" else None)(
        tax_facts(order_no)), 30, 2) or tax_facts(order_no)
    out["queuesAfter"] = queue_depths()
    seller_tax = [l for l in out["downstreamWhileTaxDown"]["billing"]["lines"] if l["component"] == "SELLER_TAX"]
    final = [c for c in out["tax"]["calculations"] if c["stage"] == "FINAL"]
    components = [c for c in out["tax"]["components"] if final and c["calculationId"] == final[-1]["calculationId"]]
    txs = out["tax"]["transactions"]
    out["verdict"] = {
        "taxWasDown": out["taxReadyWhileStopped"] is False and not out["taxWhileDown"]["transactions"],
        "paidWhileTaxDown": out["paid"]["status"] == "PAID",
        "billingPostedWhileTaxDown": bool(out["downstreamWhileTaxDown"]["billing"]["facts"]) and bool(seller_tax),
        "taxCaughtUp": len(txs) == 1 and bool(final) and txs[0]["calculationId"] == final[-1]["calculationId"],
        "amountsMatchBilling": bool(seller_tax) and abs(sum(float(l["posted"]) for l in seller_tax)
                                                        - sum(float(c["taxAmount"]) for c in components)) < 1e-9,
        "receiptIssued": bool(out["tax"]["documents"]) and out["tax"]["documents"][0]["issueStatus"] == "ISSUED",
        "noDeadLetter": all(v["messages"] == "0" for k, v in out["queuesAfter"].items() if k.endswith(".dlq") and k.startswith("tax."))}
    record("S4", out | {"orderNo": order_no})


SCENARIOS = {"probe": probe, "s1": s1, "s2": s2, "s3": s3, "s4": s4, "s5": s5}


def main():
    names = sys.argv[1:] or ["probe"]
    for name in names:
        started = now()
        try:
            SCENARIOS[name]()
        except (AssertionError, KeyError, IndexError, TypeError, RuntimeError, OSError) as failure:
            record(name + "_error", {"error": repr(failure)[:800]})
        results.setdefault("_runs", []).append({"scenario": name, "startedAt": started, "finishedAt": now()})
    merged = json.loads(OUT.read_text()) if OUT.exists() else {}
    runs = merged.get("_runs", []) + results.pop("_runs", [])
    merged.update(results)
    merged["_runs"] = runs
    text = json.dumps(merged, ensure_ascii=False, indent=2)
    for secret in [TEST_CARD["pan"]] + [t for t in tokens if t]:
        assert secret not in text, "输出中出现卡号或 Checkout Token"
    OUT.write_text(text + "\n")


if __name__ == "__main__":
    main()
