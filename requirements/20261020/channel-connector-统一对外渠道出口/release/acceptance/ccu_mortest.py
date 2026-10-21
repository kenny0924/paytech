"""统一对外渠道出口第一段 · mor-test 验收脚本（阶段 6 只写不执行；阶段 7 按 release.md 第 6、8 节在部署前 / 后各跑一次）。

入口：运营 http://localhost:28080（Nginx → Gateway → ops-bff → Owner），私网调用经一次性探针容器（docker run --rm，接入 mor-test-backend，
镜像固定为回滚 platform 镜像 ID，不用 :development）。HTTP / 数据库辅助复用 20261017 e2e_http.py（E2E_TARGET=test，原文件不改）。
管理员口令运行时从 test-environment/private/access.md 读取，不打印、不落盘。

子命令（<label> 取 before / after；输出文件写到本目录 results/，写盘前扫描 .env.test 全部值与管理员口令，命中即中止）：
  snapshot <label>        A  只读：外部 API 管理全部 GET（编辑能力、标准 API、接入方及其原子 API / 脚本 / 实现 / 流程上下文、验证账号 /
                             用例 / 运行，列表与详情）。parameters、credentials、trace、resolvedInput 只保留 SHA-256 前 16 位。
  compare <a> <b>         A  对照两次快照（逐字段，含 ID）；b 中多出的本次验收新建对象单列。
  rerun <label>           B  写：7 个非变更型沙箱用例（查询不存在 / 退款不存在）各运行一次 → 外部沙箱只收到查询类请求。
  chain <label>           C1 只读：DEMO_NI 冻结坐标经 /channel-connector/v1/operations 发 REFUND（实现不存在）与 QUERY（标准 API 不存在），
                             期望两次均 NOT_SENT 且无 Vault Reveal；有 results/payermax-state.json 时追加 C2 REFUND（真实 PayerMax UAT，
                             合成凭据）与可选 C3 PAY（card 读卡，--with-card）。
  setup-payermax          C2 写：登记验收渠道 PAYERMAX（AE001 技术账号 + 合成 RSA 密钥经 Vault Protect + MID + CARD/USD/MCC 0742 能力组，
                             MCC 选非常用值避免被其他会话的真实下单选中）；ID 写 results/payermax-state.json（不含密钥）。
  cleanup-sql             C2 打印按 ID 清理验收渠道的 SQL（先 SELECT 后 DELETE，channel_connector_app 执行；不自动执行）。
  write <label>           D  写：复制 PAYERMAX/UAT_QUERY_NOT_FOUND 的步骤新建用例 CCU_S7_<label>，校验、运行一次，再按版本删除；
                             核对 t_channel_config_change（新版本）或 Platform 审计（旧版本）。
"""
import base64
import hashlib
import json
import os
import random
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
ROOT = HERE.parents[4]
RESULTS = HERE / "results"
sys.dont_write_bytecode = True  # 不在 20261017 目录生成 __pycache__
os.environ.setdefault("E2E_TARGET", "test")
# 探针镜像固定为回滚 platform 镜像（JRE 25）；:development 会被其他会话重建。
os.environ.setdefault("E2E_PROBE_IMAGE", "sha256:3d8bc3752c37da4f57e6bfad764a6712758bef45b484980262ddbe68414ebd72")
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as h  # noqa: E402

EXTERNAL_ADMIN = "/external-api-admin/v1"
CHANNEL_ADMIN = "/channel-admin/v1"
REASON = "统一对外渠道出口第一段 mor-test 阶段 7 验收"
STATE = RESULTS / "payermax-state.json"
ENTITY_NO = "AE001"
ACCEPT_MCC = "0742"
VOLATILE = {"createTime", "updateTime", "startTime", "finishTime", "startedAt", "finishedAt", "requestId", "elapsedMs",
            "updatedAt", "createdAt", "timestamp", "lastRunTime", "lastRunAt"}
DIGESTED = {"parameters", "credentials", "trace", "resolvedInput"}
RERUN_CASES = [("PAYERMAX", "UAT_QUERY_NOT_FOUND"), ("PAYERMAX", "UAT_REFUND_QUERY_NOT_FOUND"), ("PAYERMAX", "UAT_REFUND_NOT_EXIST"),
               ("PAYBY", "UAT_PAY_QUERY_NOT_FOUND"), ("DLOCAL", "SBX_PAY_QUERY_NOT_FOUND"),
               ("ALIPAY_PLUS", "SBX_PAY_QUERY_NOT_FOUND"), ("TOSS_PAY", "SBX_PAY_QUERY_NOT_FOUND")]
TEST_CARD = {"pan": "4444333322221111", "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}


# ---------- HTTP ----------

def ops(token, method, path, body=None):
    """运营入口 /api/operations + path，返回 (状态, JSON)。写命令带 Idempotency-Key（validate 除外），429 按 Retry-After 重试。"""
    headers = {"Origin": h.OPERATIONS, "Accept": "application/json", "Authorization": "Bearer " + token}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        if not path.endswith("/validate"):
            headers["Idempotency-Key"] = str(uuid.uuid4())
    data = json.dumps(body).encode() if body is not None else (b"" if method != "GET" else None)
    for attempt in range(20):
        request = urllib.request.Request(h.OPERATIONS + "/api/operations" + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=h.TLS, timeout=60) as response:
                text = response.read().decode()
                return response.status, (json.loads(text) if text else {})
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 19:
                time.sleep(min(30, int(failure.headers.get("Retry-After") or 6)))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text)
            except ValueError:
                return failure.code, {"raw": text[:300]}


def login():
    s = h.secrets()
    return h.call(h.OPERATIONS, "/api/operations/auth/v1/login", "POST",
                  {"loginIdentifier": s["E2E_ADMIN_EMAIL"], "password": s["E2E_ADMIN_PASSWORD"]})["accessToken"]


def must(label, status, body, expected):
    if status not in expected:
        raise SystemExit(f"{label} 失败：HTTP {status} {json.dumps(body, ensure_ascii=False)[:400]}")
    return body


def now_sql():
    return datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")


# ---------- 输出 ----------

def forbidden_values():
    secret_key = re.compile(r"PASSWORD|SECRET|TOKEN|KEY|HMAC|MATERIAL|CREDENTIAL")
    values = [v for k, v in h.read_env(ROOT / "test-environment/.env.test").items() if secret_key.search(k)]
    values.append(h.secrets()["E2E_ADMIN_PASSWORD"])
    values += [TEST_CARD["pan"]]
    return [str(v) for v in values if v and len(str(v)) >= 8]


def write(name, data):
    RESULTS.mkdir(exist_ok=True)
    text = json.dumps(data, ensure_ascii=False, indent=1)
    leaked = [i for i, v in enumerate(forbidden_values()) if v in text]
    if leaked:
        raise SystemExit(f"输出含私密值（序号 {leaked}），未写入 {name}")
    path = RESULTS / name
    path.write_text(text + "\n")
    print(f"写入 {path}（{len(text)} 字节）")


def digest(value):
    return "sha256:" + hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]


def normalize(value):
    if isinstance(value, dict):
        return {k: (digest(v) if k in DIGESTED else normalize(v)) for k, v in value.items() if k not in VOLATILE}
    if isinstance(value, list):
        return [normalize(v) for v in value]
    return value


# ---------- A 管理读取快照 ----------

def paged(token, path):
    items, page = [], 1
    while True:
        sep = "&" if "?" in path else "?"
        status, body = ops(token, "GET", f"{path}{sep}page={page}&size=50")
        if status != 200:
            return status, body, items
        batch = body.get("items") or []
        items.extend(batch)
        if not batch or len(items) >= (body.get("total") or 0):
            return 200, body, items
        page += 1


def listed(body, *keys):
    if isinstance(body, list):
        return body
    for key in keys:
        if isinstance(body.get(key), list):
            return body[key]
    return []


def snapshot(token):
    snap, calls = {}, []

    def get(name, path):
        status, body = ops(token, "GET", path)
        calls.append([name, status])
        snap[name] = {"status": status, "body": normalize(body)}
        return status, body

    def get_paged(name, path):
        status, body, items = paged(token, path)
        calls.append([name, status])
        snap[name] = {"status": status, "total": body.get("total") if status == 200 else None,
                      "items": normalize(items) if status == 200 else normalize(body)}
        return items

    get("editor-capabilities", EXTERNAL_ADMIN + "/editor-capabilities")
    standards = get_paged("standard-apis", EXTERNAL_ADMIN + "/standard-apis")
    for item in standards:
        get(f"standard-apis/{item['standardApiCode']}", f"{EXTERNAL_ADMIN}/standard-apis/{item['standardApiCode']}")
    for integration in get_paged("integrations", EXTERNAL_ADMIN + "/integrations"):
        code = integration["integrationCode"]
        base = f"{EXTERNAL_ADMIN}/integrations/{code}"
        get(f"integrations/{code}", base)
        for item in get_paged(f"integrations/{code}/apis", base + "/apis"):
            get(f"integrations/{code}/apis/{item['apiCode']}", f"{base}/apis/{item['apiCode']}")
        for item in get_paged(f"integrations/{code}/scripts", base + "/scripts"):
            get(f"integrations/{code}/scripts/{item['scriptCode']}", f"{base}/scripts/{item['scriptCode']}")
        _, impls = get(f"integrations/{code}/implementations", base + "/implementations")
        for item in listed(impls, "items", "implementations"):
            get(f"integrations/{code}/implementations/{item['standardApiCode']}", f"{base}/implementations/{item['standardApiCode']}")
        for std in standards:
            get(f"integrations/{code}/flow-editor-context?{std['standardApiCode']}",
                f"{base}/flow-editor-context?standardApiCode={std['standardApiCode']}")
        _, accounts = get(f"integrations/{code}/verify-accounts", base + "/verify-accounts")
        for item in listed(accounts, "items", "accounts"):
            get(f"integrations/{code}/verify-accounts/{item['accountCode']}", f"{base}/verify-accounts/{item['accountCode']}")
        for item in get_paged(f"integrations/{code}/verify-cases", base + "/verify-cases"):
            get(f"integrations/{code}/verify-cases/{item['caseCode']}", f"{base}/verify-cases/{item['caseCode']}")
        for item in get_paged(f"integrations/{code}/verify-runs", base + "/verify-runs"):
            get(f"integrations/{code}/verify-runs/{item['runCode']}", f"{base}/verify-runs/{item['runCode']}")
    statuses = {}
    for _, status in calls:
        statuses[status] = statuses.get(status, 0) + 1
    return {"calls": len(calls), "statuses": statuses, "responses": snap}


def compare(a_name, b_name):
    a = json.loads((RESULTS / a_name).read_text())["snapshot"]["responses"]
    b = json.loads((RESULTS / b_name).read_text())["snapshot"]["responses"]
    diffs, extra = [], sorted(set(b) - set(a))
    for key in sorted(set(a) & set(b)):
        if a[key] != b[key]:
            diffs.append(key)
    missing = sorted(set(a) - set(b))
    result = {"a": a_name, "b": b_name, "common": len(set(a) & set(b)), "different": diffs, "missingInB": missing, "extraInB": extra}
    write(f"compare-{Path(a_name).stem}-vs-{Path(b_name).stem}.json", result)
    print(json.dumps({k: (v if not isinstance(v, list) else len(v)) for k, v in result.items()}, ensure_ascii=False))


# ---------- B 沙箱用例重跑 ----------

def run_case(token, code, case):
    base = f"{EXTERNAL_ADMIN}/integrations/{code}"
    status, started = ops(token, "POST", f"{base}/verify-cases/{case}/runs", {"reason": REASON})
    out = {"integration": code, "case": case, "startStatus": status, "runCode": started.get("runCode"), "code": started.get("code")}
    detail = {}
    for _ in range(60):
        if status not in (200, 201):
            break
        _, detail = ops(token, "GET", f"{base}/verify-runs/{started['runCode']}")
        if detail.get("runStatus") != "RUNNING":
            break
        time.sleep(2)
    out["runStatus"] = detail.get("runStatus")
    out["failureSummary"] = detail.get("failureSummary")
    out["steps"] = [{"stepNo": s.get("stepNo"), "stepStatus": s.get("stepStatus"),
                     "executeStatus": (s.get("result") or {}).get("executeStatus"), "returnId": (s.get("result") or {}).get("returnId"),
                     "assertions": [[x.get("target"), x.get("passed")] for x in s.get("assertions") or []]}
                    for s in detail.get("steps") or []]
    run_id = str(started.get("runCode") or "")
    if run_id.isdigit():
        out["rowIn"] = {"mor_platform": bool(h.sql("platform", f"SELECT 1 FROM t_integration_verify_run WHERE verify_run_id={run_id}"))
                        if platform_has_tables() else None,
                        "mor_channel_connector": bool(h.sql("channel-connector",
                                                            f"SELECT 1 FROM t_integration_verify_run WHERE verify_run_id={run_id}"))
                        if channel_connector_has_tables() else None}
    return out


def platform_has_tables():
    return bool(h.sql("platform", "SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() "
                                  "AND table_name='t_integration_verify_run'"))


def channel_connector_has_tables():
    return bool(h.sql("channel-connector", "SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() "
                                           "AND table_name='t_integration_verify_run'"))


# ---------- C 支付链路 ----------

def vault_events(since):
    rows = h.sql("vault", "SELECT caller_service, action, IFNULL(data_type,''), IFNULL(purpose,''), outcome "
                          f"FROM t_vault_access_event WHERE create_time >= '{since}' "
                          "AND data_type IN ('CHANNEL_ACCOUNT_CONFIG_V1','PAYMENT_CARD_DEK') ORDER BY create_time")
    return [dict(zip(("callerService", "action", "dataType", "purpose", "outcome"), r)) for r in rows]


def execute(target, operation, purpose, amount, payload):
    body = {"entityNo": target["entityNo"], "channelId": target["channelId"], "channelAccountId": target["channelAccountId"],
            "midId": target["midId"], "operation": operation, "purpose": purpose,
            "criteria": {"paymentMethodCode": "CARD", "schemeCode": None, "countryCode": "US", "currency": "USD",
                         "mccCode": target["mccCode"]}, "amount": amount, "input": payload}
    since = now_sql()
    status, text = h.private_call("channel-connector", "POST", "/channel-connector/v1/operations", body=body, timeout=90)
    time.sleep(1.5)
    response = json.loads(text) if text.startswith("{") else {"raw": text[:300]}
    return {"httpStatus": status, "outcome": response.get("outcome"), "failureCode": response.get("failureCode"),
            "channelStatus": response.get("channelStatus"), "paymentStatus": response.get("paymentStatus"),
            "hasChannelTransactionRef": bool(response.get("channelTransactionRef")), "vaultEvents": vault_events(since)}


def demo_ni_target():
    rows = h.sql("channel-connector", "SELECT c.channel_id, a.channel_account_id, a.entity_no, m.mid_id FROM t_payment_channel c "
                                      "JOIN t_channel_account a ON a.channel_id=c.channel_id JOIN t_channel_mid m "
                                      "ON m.channel_account_id=a.channel_account_id WHERE c.channel_code='DEMO_NI' "
                                      "AND m.mid_status='ACTIVE' ORDER BY m.mid_id LIMIT 1")
    if not rows:
        return None
    channel_id, account_id, entity_no, mid_id = rows[0]
    return {"channelId": channel_id, "channelAccountId": account_id, "entityNo": entity_no, "midId": mid_id, "mccCode": "5999"}


def refund_payload():
    return {"refund": {"refundId": f"RFD{random.randrange(10**11):011d}2", "paymentAttemptId": str(random.randrange(10**15, 10**16)),
                       "amount": {"value": "5.00", "currency": "USD"}, "reason": "CCU mor-test acceptance"}}


def card_pay(target):
    """C3：以 payment-service 身份在 card 建一次性手输卡工具并签发读卡票据，再发 PAY；只记录是否读卡成功，不输出卡数据。"""
    def card_call(path, caller, body):
        status, text = h.private_call("card", "POST", "/card/v1" + path, headers={"X-Caller-Service": caller}, body=body)
        return status, (json.loads(text) if text.startswith("{") else {})
    started = datetime.now(timezone.utc)
    status, instrument = card_call("/instruments", "payment-service", {
        "clientId": str(random.randrange(10**14, 10**15)), "sourceSubmissionId": str(random.randrange(10**15, 10**16)),
        "card": TEST_CARD, "consentToSave": False,
        "expiresAt": datetime.fromtimestamp(started.timestamp() + 1800, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")})
    out = {"instrumentStatus": status, "instrumentRef": instrument.get("instrumentRef")}
    if status != 200:
        return out
    attempt_id, action_id = str(random.randrange(10**15, 10**16)), str(random.randrange(10**15, 10**16))
    status, issued = card_call("/material-tickets", "payment-service", {
        "instrumentRef": instrument["instrumentRef"], "paymentAttemptId": attempt_id, "paymentActionId": action_id,
        "purpose": "CHANNEL_EXECUTION", "ttlSeconds": 90})
    out["ticketStatus"] = status
    if not issued.get("ticket"):
        return out
    out["pay"] = execute(target, "PAY", "NEW_PAYMENT", "10.00", {
        "card": {"token": issued["ticket"]},
        "payment": {"attemptId": attempt_id, "amount": {"value": "10.00", "currency": "USD"}, "country": "US",
                    "returnUrl": "https://localhost:28443/return", "payerReference": "ccu-mortest",
                    "clientIp": "203.0.113.10", "userAgent": "Mozilla/5.0 (ccu mor-test acceptance)"}})
    rows = h.sql("card", "SELECT caller, purpose, used_at IS NOT NULL FROM t_card_material_ticket "
                         f"WHERE instrument_ref='{instrument['instrumentRef']}'")
    out["ticketRow"] = [dict(zip(("issuedBy", "purpose", "used"), r)) for r in rows]
    return out


def chain(label, with_card):
    result = {"label": label, "startedAt": datetime.now(timezone.utc).isoformat()}
    demo = demo_ni_target()
    result["demoNi"] = demo and {
        "refundImplementationMissing": execute(demo, "REFUND", "ORIGINAL_TRANSACTION", "5.00", refund_payload()),
        "queryStandardApiMissing": execute(demo, "QUERY", "ORIGINAL_TRANSACTION", None,
                                           {"payment": {"attemptId": str(random.randrange(10**15, 10**16))}})}
    if STATE.exists():
        target = json.loads(STATE.read_text())
        result["payermaxUatRefund"] = execute(target, "REFUND", "ORIGINAL_TRANSACTION", "5.00", refund_payload())
        if with_card:
            result["payermaxUatCardPay"] = card_pay(target)
    result["finishedAt"] = datetime.now(timezone.utc).isoformat()
    write(f"chain-{label}.json", result)


def rsa_pair():
    private = subprocess.run(["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-outform", "DER"],
                             capture_output=True, check=True).stdout
    public = subprocess.run(["openssl", "pkey", "-inform", "DER", "-pubout", "-outform", "DER"], input=private,
                            capture_output=True, check=True).stdout
    return base64.b64encode(private).decode(), base64.b64encode(public).decode()


def setup_payermax():
    if STATE.exists():
        raise SystemExit(f"已有 {STATE}，不重复登记")
    if h.sql("channel-connector", "SELECT channel_id FROM t_payment_channel WHERE channel_code='PAYERMAX'"):
        raise SystemExit("mor-test 已有渠道 PAYERMAX，停止（不覆盖他人数据）")
    token = login()
    run = datetime.now(timezone.utc).strftime("%m%d%H%M")
    merchant_private, _ = rsa_pair()
    _, payermax_public = rsa_pair()
    channel = must("新建渠道", *ops(token, "POST", CHANNEL_ADMIN + "/channels",
                                  {"channelCode": "PAYERMAX", "channelName": f"PayerMax（统一出口验收 {run}）"}), (201,))
    cid = channel["channelId"]
    must("激活渠道", *ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    account = must("新建账号", *ops(token, "POST", CHANNEL_ADMIN + "/channel-accounts", {
        "channelId": cid, "entityNo": ENTITY_NO, "accountName": f"PayerMax 合成验收账号 {run}", "remark": REASON}), (201,))
    aid = account["channelAccountId"]
    must("写入账号配置", *ops(token, "PUT", f"{CHANNEL_ADMIN}/channel-accounts/{aid}/configuration", {
        "parameters": {"appId": "ccu" + uuid.uuid4().hex[:15], "merchantNo": "S" + str(random.randrange(10**7, 10**8)),
                       "keyVersion": "1", "payermaxPublicKey": payermax_public},
        "credentials": {"merchantPrivateKey": merchant_private}}), (200,))
    merchant_private = None
    must("激活账号", *ops(token, "POST", f"{CHANNEL_ADMIN}/channel-accounts/{aid}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    mid = must("新建 MID", *ops(token, "POST", CHANNEL_ADMIN + "/mids", {
        "channelId": cid, "channelAccountId": aid, "externalMidCode": f"PMX-CCU-{run}", "entityNo": ENTITY_NO}), (201,))
    must("激活 MID", *ops(token, "POST", f"{CHANNEL_ADMIN}/mids/{mid['midId']}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    capability = must("新建能力组", *ops(token, "POST", CHANNEL_ADMIN + "/mid-capabilities", {
        "midId": mid["midId"], "paymentMethodCode": "CARD", "schemeCode": "*", "countryCode": "*", "currency": "USD",
        "mccCode": ACCEPT_MCC, "details": [{"capabilityCode": "ACTION.PAY", "booleanValue": True},
                                           {"capabilityCode": "ACTION.REFUND", "booleanValue": True},
                                           {"capabilityCode": "AMOUNT.MIN", "decimalValue": "0.01"},
                                           {"capabilityCode": "AMOUNT.MAX", "decimalValue": "100000"}]}), (201,))
    state = {"channelId": str(cid), "channelAccountId": str(aid), "midId": str(mid["midId"]),
             "midCapabilityId": str(capability["midCapabilityId"]), "entityNo": ENTITY_NO, "mccCode": ACCEPT_MCC,
             "createdAt": datetime.now(timezone.utc).isoformat()}
    write(STATE.name, state)


def cleanup_sql():
    s = json.loads(STATE.read_text())
    ids = {k: int(s[k]) for k in ("channelId", "channelAccountId", "midId", "midCapabilityId")}
    print(f"""-- 验收渠道 PAYERMAX 清理（mor_channel_connector，channel_connector_app；先核对 SELECT 结果再执行 DELETE）。
SELECT 'channel', COUNT(*) FROM t_payment_channel WHERE channel_id={ids['channelId']} AND channel_code='PAYERMAX'
UNION ALL SELECT 'account', COUNT(*) FROM t_channel_account WHERE channel_account_id={ids['channelAccountId']}
UNION ALL SELECT 'mid', COUNT(*) FROM t_channel_mid WHERE mid_id={ids['midId']}
UNION ALL SELECT 'capability', COUNT(*) FROM t_mid_capability WHERE mid_capability_id={ids['midCapabilityId']}
UNION ALL SELECT 'changes', COUNT(*) FROM t_channel_config_change WHERE target_id IN ({ids['channelId']},{ids['channelAccountId']},{ids['midId']},{ids['midCapabilityId']});
START TRANSACTION;
DELETE FROM t_mid_capability_detail WHERE mid_capability_id={ids['midCapabilityId']};
DELETE FROM t_mid_capability WHERE mid_capability_id={ids['midCapabilityId']};
DELETE FROM t_channel_mid WHERE mid_id={ids['midId']};
DELETE FROM t_channel_account WHERE channel_account_id={ids['channelAccountId']};
DELETE FROM t_payment_channel WHERE channel_id={ids['channelId']} AND channel_code='PAYERMAX';
DELETE FROM t_channel_config_change WHERE target_id IN ({ids['channelId']},{ids['channelAccountId']},{ids['midId']},{ids['midCapabilityId']});
COMMIT;""")


# ---------- D 写入抽样 ----------

def write_sample(label):
    token = login()
    base = f"{EXTERNAL_ADMIN}/integrations/PAYERMAX"
    source = must("读取模板用例", *ops(token, "GET", f"{base}/verify-cases/UAT_QUERY_NOT_FOUND"), (200,))
    case = f"CCU_S7_{label.upper()}"
    since = now_sql()
    body = {"caseCode": case, "caseName": f"统一出口验收 {label}", "description": None,
            "accountCode": source.get("accountCode"), "steps": source.get("steps"), "reason": REASON}
    out = {"case": case}
    out["validate"] = ops(token, "POST", f"{base}/verify-cases/validate", {k: body[k] for k in ("accountCode", "steps")})[0]
    status, created = ops(token, "POST", f"{base}/verify-cases", body)
    out["create"] = {"httpStatus": status, "version": created.get("version"), "code": created.get("code")}
    if status in (200, 201):
        out["run"] = run_case(token, "PAYERMAX", case)
        status, deleted = ops(token, "DELETE", f"{base}/verify-cases/{case}", {"expectedVersion": created.get("version") or 1,
                                                                               "reason": REASON})
        out["delete"] = {"httpStatus": status, "code": deleted.get("code")}
        out["getAfterDelete"] = ops(token, "GET", f"{base}/verify-cases/{case}")[0]
    if channel_connector_has_tables():
        rows = h.sql("channel-connector", "SELECT target_type, change_type, change_detail IS NOT NULL FROM t_channel_config_change "
                                          f"WHERE create_time >= '{since}' AND target_type LIKE 'VERIFY_%' ORDER BY id")
        out["channelConfigChanges"] = [dict(zip(("targetType", "changeType", "hasDetail"), r)) for r in rows]
    write(f"write-{label}.json", out)


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command == "snapshot":
        token = login()
        return write(f"snapshot-{sys.argv[2]}.json", {"takenAt": datetime.now(timezone.utc).isoformat(), "snapshot": snapshot(token)})
    if command == "compare":
        return compare(sys.argv[2], sys.argv[3])
    if command == "rerun":
        token = login()
        runs = [run_case(token, code, case) for code, case in RERUN_CASES]
        return write(f"rerun-{sys.argv[2]}.json", {"takenAt": datetime.now(timezone.utc).isoformat(), "runs": runs})
    if command == "chain":
        return chain(sys.argv[2], "--with-card" in sys.argv)
    if command == "setup-payermax":
        return setup_payermax()
    if command == "cleanup-sql":
        return cleanup_sql()
    if command == "write":
        return write_sample(sys.argv[2])
    raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
