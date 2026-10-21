"""任务卡 P1-stage5：第一段本机端到端（迁移前旧版本 / 迁移后新版本各跑一次，结果逐项对照）。

本机环境沿用 20261017 local-e2e（run-service.sh 启动的 *_v2e2e 库、Redis db 3、vhost /mor-e2e、合成 PayerMax 127.0.0.1:18190），
HTTP / 数据库辅助复用其 e2e_http.py（原文件不改）。

子命令：
  setup                   A1：经运营控制台（Nginx 18444 → Gateway → ops-bff → channel-connector）登记渠道 PAYERMAX_MOCK、技术账号
                          （真实 Vault Protect）、MID、USD 能力组；ID 写 local-environment/e2e/runtime/ccu-stage5-channel.json。
  snapshot <out.json>     A4：经 ops-bff 调用外部 API 管理全部 GET（列表与详情），规范化后写入（去掉时间、请求 ID 与耗时，
                          验证账号的参数与凭据值替换为 SHA-256 前 16 位）。
  run <label> <out.json>  A2 + A3 + A4：链路（候选查询 → /channel-connector/v1/operations：REFUND、PAY 含读卡、两个 NOT_SENT）、
                          沙箱用例（新建验证账号与用例、运行一次）、管理读取快照。label 为 baseline / after，用于区分本次新建的编码。
输出不含 Token、密钥或口令：写盘前用合成密钥文件、管理员口令与账号 Vault Token 逐值扫描，命中即中止。
"""
import base64
import hashlib
import json
import random
import re
import string
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as h  # noqa: E402

RUNTIME = ROOT / "local-environment/e2e/runtime"
LOGS = ROOT / "local-environment/logs"
KEYS = RUNTIME / "payermax/payermax-mock-keys.json"
JOURNAL = RUNTIME / "payermax/payermax-journal.jsonl"
STATE = RUNTIME / "ccu-stage5-channel.json"
CODE = "PAYERMAX_MOCK"
ENTITY_NO = "AE001"
CHANNEL_ADMIN = "/channel-admin/v1"
EXTERNAL_ADMIN = "/external-api-admin/v1"
REASON = "统一对外渠道出口第一段阶段 5 本机端到端"
TEST_CARD = {"pan": "4444333322221111", "expiryMonth": "03", "expiryYear": "2030", "cardholderName": "James Smith", "cvv": "123"}
MASKED_PAN = "444433******1111"
VOLATILE = {"createTime", "updateTime", "startTime", "finishTime", "startedAt", "finishedAt", "requestId", "elapsedMs",
            "updatedAt", "createdAt", "timestamp", "lastRunTime", "lastRunAt"}


# ---------- HTTP ----------

def ops(token, method, path, body=None):
    """运营入口 /api/operations + path，返回 (状态, JSON)。写命令带 Idempotency-Key（validate 除外），429 按 Retry-After 重试。"""
    headers = {"Origin": h.OPERATIONS, "Accept": "application/json"}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        if not path.endswith("/validate"):
            headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else (b"" if method != "GET" else None)
    for attempt in range(15):
        request = urllib.request.Request(h.OPERATIONS + "/api/operations" + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=h.TLS, timeout=60) as response:
                text = response.read().decode()
                return response.status, (json.loads(text) if text else {})
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 14:
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


def actor_of(token):
    payload = token.split(".")[1]
    payload += "=" * (-len(payload) % 4)
    return json.loads(base64.urlsafe_b64decode(payload))["sub"]


def must(label, status, body, expected):
    if status not in expected:
        raise SystemExit(f"{label} 失败：HTTP {status} {json.dumps(body, ensure_ascii=False)[:400]}")
    return body


# ---------- A1 渠道数据 ----------

def setup():
    token = login()
    keys = json.loads(KEYS.read_text())
    existing = h.sql("channel-connector", f"SELECT channel_id, channel_status FROM t_payment_channel WHERE channel_code='{CODE}'")
    if existing:
        raise SystemExit(f"已有渠道 {CODE}：{existing}；沿用 {STATE}，不重复登记")
    run = datetime.now(timezone.utc).strftime("%m%d%H%M")
    channel = must("新建渠道", *ops(token, "POST", CHANNEL_ADMIN + "/channels",
                                  {"channelCode": CODE, "channelName": f"本机合成 PayerMax（阶段 5 {run}）"}), (201,))
    cid = channel["channelId"]
    must("激活渠道", *ops(token, "POST", f"{CHANNEL_ADMIN}/channels/{cid}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    account = must("新建账号", *ops(token, "POST", CHANNEL_ADMIN + "/channel-accounts", {
        "channelId": cid, "entityNo": ENTITY_NO, "accountName": f"PayerMax 合成主账号 {run}", "remark": REASON}), (201,))
    aid = account["channelAccountId"]
    configured = must("写入账号配置", *ops(token, "PUT", f"{CHANNEL_ADMIN}/channel-accounts/{aid}/configuration", {
        "parameters": {k: keys[k] for k in ("appId", "merchantNo", "keyVersion", "payermaxPublicKey")},
        "credentials": {"merchantPrivateKey": keys["merchantPrivateKey"]}}), (200,))
    must("激活账号", *ops(token, "POST", f"{CHANNEL_ADMIN}/channel-accounts/{aid}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    mid = must("新建 MID", *ops(token, "POST", CHANNEL_ADMIN + "/mids", {
        "channelId": cid, "channelAccountId": aid, "externalMidCode": f"PMX-CCU-{run}", "entityNo": ENTITY_NO}), (201,))
    mid_id = mid["midId"]
    must("激活 MID", *ops(token, "POST", f"{CHANNEL_ADMIN}/mids/{mid_id}/status", {"targetStatus": "ACTIVE", "reason": REASON}), (200,))
    capability = must("新建能力组", *ops(token, "POST", CHANNEL_ADMIN + "/mid-capabilities", {
        "midId": mid_id, "paymentMethodCode": "CARD", "schemeCode": "*", "countryCode": "*", "currency": "USD", "mccCode": "5999",
        "details": [{"capabilityCode": "ACTION.PAY", "booleanValue": True}, {"capabilityCode": "ACTION.REFUND", "booleanValue": True},
                    {"capabilityCode": "AMOUNT.MIN", "decimalValue": "0.01"}, {"capabilityCode": "AMOUNT.MAX", "decimalValue": "100000"},
                    {"capabilityCode": "SECURITY.3DS_MODE", "codeValue": "SUPPORTED"}]}), (201,))
    state = {"channelId": cid, "channelAccountId": aid, "midId": mid_id, "midCapabilityId": capability["midCapabilityId"],
             "vaultTokenSuffix": configured.get("vaultTokenSuffix"), "createdAt": datetime.now(timezone.utc).isoformat()}
    STATE.write_text(json.dumps(state, indent=1) + "\n")
    print(json.dumps(state, ensure_ascii=False))


# ---------- A2 链路 ----------

def journal():
    return [json.loads(line) for line in JOURNAL.read_text().splitlines()] if JOURNAL.exists() else []


def vault_events(since):
    rows = h.sql("vault", "SELECT caller_service, action, IFNULL(data_type,''), IFNULL(purpose,''), outcome, result_code "
                          f"FROM t_vault_access_event WHERE create_time >= '{since}' AND data_type='CHANNEL_ACCOUNT_CONFIG_V1' "
                          "ORDER BY create_time")
    return [dict(zip(("callerService", "action", "dataType", "purpose", "outcome", "resultCode"), r)) for r in rows]


def log_offset(name):
    path = LOGS / f"{name}.log"
    return path.stat().st_size if path.exists() else 0


def log_since(name, offset):
    path = LOGS / f"{name}.log"
    if not path.exists():
        return ""
    with path.open("rb") as handle:
        handle.seek(offset)
        return handle.read().decode(errors="ignore")


def card_log_facts(text):
    """card 访问日志的方法与状态（读卡为敏感路径，URI 记为 /**，不记报文）。"""
    return [f"{m.group(1)} {m.group(2)}" for m in re.finditer(r'method="(\w+)" uri="[^"]*" status="(\d+)"', text)]


def card_gate():
    """直连 card 读卡接口，用合成票据分别以三种调用方请求：允许的调用方越过调用方校验后读票据（本机 mor_card_v2e2e 无表 → 500），
    不允许的调用方在读库前按资源不存在返回 404。"""
    gate = {}
    for caller in ("platform-support-service", "channel-connector-service", "other-service"):
        status, text = h.private_call("card", "POST", "/card/v1/channel-card-materials/reveal",
                                      headers={"X-Caller-Service": caller}, body={"ticket": ticket()})
        body = json.loads(text) if text.startswith("{") else {}
        gate[caller] = {"httpStatus": status, "code": body.get("code")}
    return gate


def execute(state, operation, purpose, amount, payload):
    body = {"entityNo": ENTITY_NO, "channelId": state["channelId"], "channelAccountId": state["channelAccountId"],
            "midId": state["midId"], "operation": operation, "purpose": purpose,
            "criteria": {"paymentMethodCode": "CARD", "schemeCode": None, "countryCode": "US", "currency": "USD", "mccCode": "5999"},
            "amount": amount, "input": payload}
    started = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")
    before = len(journal())
    marks = {n: log_offset(n) for n in ("card", "channel-connector", "platform")}
    status, text = h.private_call("channel-connector", "POST", "/channel-connector/v1/operations", body=body, timeout=90)
    time.sleep(1.5)
    response = json.loads(text) if text.startswith("{") else {"raw": text[:300]}
    seen = journal()[before:]
    return {
        "httpStatus": status,
        "outcome": response.get("outcome"), "failureCode": response.get("failureCode"),
        "channelStatus": response.get("channelStatus"), "paymentStatus": response.get("paymentStatus"),
        "hasChannelTransactionRef": bool(response.get("channelTransactionRef")),
        "failureMessage": (response.get("failureMessage") or "")[:160] or None,
        "channelJournal": {"count": len(seen), "entries": [{k: t.get(k) for k in ("operation", "outcome", "code", "signatureValid",
                                                                                  "envelopeValid", "requestTimeValid", "amountValid")}
                                                           for t in seen]},
        "vaultChannelAccountEvents": vault_events(started),
        "cardLog": card_log_facts(log_since("card", marks["card"])),
    }


def ticket():
    return "".join(random.choice(string.ascii_letters + string.digits + "-_") for _ in range(43))


def chain(state):
    out = {}
    status, text = h.private_call("channel-connector", "POST", "/channel-connector/v1/mid-candidates", body={
        "entityNo": ENTITY_NO, "criteria": {"paymentMethodCode": "CARD", "schemeCode": None, "countryCode": "US",
                                            "currency": "USD", "mccCode": "5999"}})
    body = json.loads(text) if text.startswith(("[", "{")) else {}
    items = body if isinstance(body, list) else body.get("candidates") or body.get("items") or []
    mine = [c for c in items if str(c.get("midId")) == str(state["midId"])]
    out["candidates"] = {"httpStatus": status, "count": len(items),
                         "mine": [{k: c.get(k) for k in ("channelId", "channelAccountId", "midId", "supportedOperations",
                                                         "minAmount", "maxAmount")} for c in mine]}
    refund_id = f"RFD{random.randrange(10**11):011d}2"
    out["refund"] = execute(state, "REFUND", "ORIGINAL_TRANSACTION", "5.00", {"refund": {
        "refundId": refund_id, "paymentAttemptId": str(random.randrange(10**15, 10**16)),
        "amount": {"value": "5.00", "currency": "USD"}, "reason": "Stage 5 unified outbound chain"}})
    out["payWithCardRead"] = execute(state, "PAY", "NEW_PAYMENT", "10.00", {
        "card": {"token": ticket()},
        "payment": {"attemptId": str(random.randrange(10**15, 10**16)), "amount": {"value": "10.00", "currency": "USD"},
                    "country": "US", "returnUrl": "https://127.0.0.1:18446/return", "payerReference": "stage5-chain"}})
    out["notSentStandardApiMissing"] = execute(state, "QUERY", "ORIGINAL_TRANSACTION", None,
                                               {"payment": {"attemptId": str(random.randrange(10**15, 10**16))}})
    out["notSentInvalidInput"] = execute(state, "REFUND", "ORIGINAL_TRANSACTION", None,
                                         {"refund": {"paymentAttemptId": "1", "amount": {"value": "5.00", "currency": "USD"}}})
    return out


# ---------- A3 沙箱 ----------

def sandbox(token, state, label):
    keys = json.loads(KEYS.read_text())
    base = f"{EXTERNAL_ADMIN}/integrations/{CODE}"
    account_code, case_code = f"S5_{label.upper()}_ACCT", f"S5_{label.upper()}_CASE"
    result = {"accountCode": account_code, "caseCode": case_code}
    status, body = ops(token, "POST", base + "/verify-accounts", {
        "accountCode": account_code, "accountName": f"阶段 5 {label} 合成账号", "channelCode": CODE,
        "channelAccountId": str(state["channelAccountId"]), "entityNo": ENTITY_NO,
        "parameters": {k: keys[k] for k in ("appId", "merchantNo", "keyVersion", "payermaxPublicKey")},
        "credentials": {"merchantPrivateKey": keys["merchantPrivateKey"], **{"testCard." + k: v for k, v in TEST_CARD.items()}},
        "reason": REASON})
    result["createAccount"] = {"httpStatus": status, "version": body.get("version"), "code": body.get("code")}
    steps = [{"id": "pay", "standardApiCode": "PAY", "input": {
        "channelAccount": "${account.identity}", "card": {"token": "sandbox-test-card"},
        "payment": {"attemptId": "PMXC${random.digits(11)}1", "amount": {"value": "10.00", "currency": "USD"}, "country": "US",
                    "returnUrl": "https://merchant.local.invalid/return", "subject": "SUCCESS", "payerReference": "payer-ref-verify",
                    "clientIp": "203.0.113.10", "userAgent": "Mozilla/5.0 (sandbox verification)"}},
        "expect": {"executeStatus": "SUCCESS", "returnId": "succeeded", "outputs": [
            {"path": "paymentStatus", "match": "EQUALS", "value": "SUCCEEDED"}, {"path": "channelTransactionRef", "match": "EXISTS"},
            {"path": "nextAction", "match": "ABSENT"}]}}]
    status, body = ops(token, "POST", base + "/verify-cases", {"caseCode": case_code, "caseName": f"阶段 5 {label} 卡直付成功",
                                                               "description": None, "accountCode": account_code, "steps": steps,
                                                               "reason": REASON})
    result["createCase"] = {"httpStatus": status, "version": body.get("version"), "code": body.get("code")}
    before = len(journal())
    status, started = ops(token, "POST", f"{base}/verify-cases/{case_code}/runs", {"reason": REASON})
    result["startRun"] = {"httpStatus": status, "runStatus": started.get("runStatus"), "code": started.get("code")}
    detail = {}
    for _ in range(60):
        if status not in (200, 201):
            break
        status_get, detail = ops(token, "GET", f"{base}/verify-runs/{started['runCode']}")
        if detail.get("runStatus") != "RUNNING":
            break
        time.sleep(2)
    result["runCode"] = started.get("runCode")
    result["runStatus"] = detail.get("runStatus")
    result["failureSummary"] = detail.get("failureSummary")
    steps_out = []
    for step in detail.get("steps") or []:
        res = step.get("result") or {}
        outputs = res.get("outputs") or {}
        card = {}
        for node in (step.get("trace") or {}).get("nodes", []):
            text = ((node.get("exchange") or {}).get("request") or {}).get("body", {}).get("text", "")
            if text:
                card = json.loads(text).get("data", {}).get("paymentDetail", {}).get("cardInfo", {}) or card
        steps_out.append({"stepNo": step.get("stepNo"), "stepStatus": step.get("stepStatus"),
                          "executeStatus": res.get("executeStatus"), "returnId": res.get("returnId"),
                          "outputs": {k: ("[present]" if k in ("channelTransactionRef",) and v else v) for k, v in outputs.items()},
                          "assertions": [[a.get("target"), a.get("passed")] for a in step.get("assertions") or []],
                          "evidenceCardNumber": card.get("cardIdentifierNo"),
                          "evidenceContainsRawCard": TEST_CARD["pan"] in json.dumps(detail)})
    result["steps"] = steps_out
    seen = journal()[before:]
    result["channelJournal"] = {"count": len(seen), "entries": [{k: t.get(k) for k in ("operation", "outcome", "code",
                                                                                     "signatureValid", "cardValid")} for t in seen]}
    return result


# ---------- A4 管理读取快照 ----------

def digest(value):
    return "sha256:" + hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]


def normalize(value, key=None):
    if isinstance(value, dict):
        out = {}
        for k, v in value.items():
            if k in VOLATILE:
                continue
            if k in ("parameters", "credentials") and isinstance(v, dict):
                out[k] = {pk: digest(pv) for pk, pv in v.items()}
                continue
            out[k] = normalize(v, k)
        return out
    if isinstance(value, list):
        return [normalize(v) for v in value]
    return value


def paged(token, path):
    items, page, statuses = [], 1, []
    while True:
        sep = "&" if "?" in path else "?"
        status, body = ops(token, "GET", f"{path}{sep}page={page}&size=50")
        statuses.append(status)
        if status != 200:
            return status, body, items
        batch = body.get("items") or []
        items.extend(batch)
        if not batch or len(items) >= (body.get("total") or 0):
            return 200, body, items
        page += 1


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
    for item in get_paged("standard-apis", EXTERNAL_ADMIN + "/standard-apis"):
        get(f"standard-apis/{item['standardApiCode']}", f"{EXTERNAL_ADMIN}/standard-apis/{item['standardApiCode']}")
    standard_codes = [i["standardApiCode"] for i in snap["standard-apis"].get("items") or []]
    for integration in get_paged("integrations", EXTERNAL_ADMIN + "/integrations"):
        code = integration["integrationCode"]
        base = f"{EXTERNAL_ADMIN}/integrations/{code}"
        get(f"integrations/{code}", base)
        for item in get_paged(f"integrations/{code}/apis", base + "/apis"):
            get(f"integrations/{code}/apis/{item['apiCode']}", f"{base}/apis/{item['apiCode']}")
        for item in get_paged(f"integrations/{code}/scripts", base + "/scripts"):
            get(f"integrations/{code}/scripts/{item['scriptCode']}", f"{base}/scripts/{item['scriptCode']}")
        status, impls = get(f"integrations/{code}/implementations", base + "/implementations")
        impl_items = impls if isinstance(impls, list) else (impls.get("items") or impls.get("implementations") or [])
        for item in impl_items:
            get(f"integrations/{code}/implementations/{item['standardApiCode']}", f"{base}/implementations/{item['standardApiCode']}")
        for std in standard_codes:
            get(f"integrations/{code}/flow-editor-context?{std}", f"{base}/flow-editor-context?standardApiCode={std}")
        status, accounts = get(f"integrations/{code}/verify-accounts", base + "/verify-accounts")
        account_items = accounts if isinstance(accounts, list) else (accounts.get("items") or accounts.get("accounts") or [])
        for item in account_items:
            get(f"integrations/{code}/verify-accounts/{item['accountCode']}", f"{base}/verify-accounts/{item['accountCode']}")
        for item in get_paged(f"integrations/{code}/verify-cases", base + "/verify-cases"):
            get(f"integrations/{code}/verify-cases/{item['caseCode']}", f"{base}/verify-cases/{item['caseCode']}")
        for item in get_paged(f"integrations/{code}/verify-runs", base + "/verify-runs"):
            get(f"integrations/{code}/verify-runs/{item['runCode']}", f"{base}/verify-runs/{item['runCode']}")
    return {"calls": calls, "responses": snap}


# ---------- 输出 ----------

def forbidden_values(state):
    """密钥、管理员口令与账号 Vault Token：出现即中止。"""
    keys = json.loads(KEYS.read_text())
    values = [v for k, v in keys.items() if k.endswith("Key")]
    s = h.secrets()
    values += [s.get("E2E_ADMIN_PASSWORD")]
    token_rows = h.sql("channel-connector", f"SELECT IFNULL(account_vault_token,'') FROM t_channel_account "
                                            f"WHERE channel_account_id={int(state['channelAccountId'])}") if state else []
    values += [r[0] for r in token_rows]
    return [str(v) for v in values if v and len(str(v)) >= 8]


def identifiers():
    """合成账号的标识类参数（appId、merchantNo）：不是密钥，但出现在渠道请求轨迹中，写盘前替换为占位符。"""
    keys = json.loads(KEYS.read_text())
    return {f"[{k}]": str(keys[k]) for k in ("appId", "merchantNo") if keys.get(k)}


def write(path, data, state):
    text = json.dumps(data, ensure_ascii=False, indent=1, sort_keys=False)
    leaked = [i for i, v in enumerate(forbidden_values(state)) if v in text]
    if leaked:
        raise SystemExit(f"输出含私密值（序号 {leaked}），未写入 {path}")
    for placeholder, value in identifiers().items():
        text = text.replace(value, placeholder)
    Path(path).write_text(text + "\n")
    print(f"写入 {path}（{len(text)} 字节）")


def main():
    command = sys.argv[1]
    if command == "setup":
        return setup()
    state = json.loads(STATE.read_text())
    token = login()
    if command == "snapshot":
        return write(sys.argv[2], {"takenAt": datetime.now(timezone.utc).isoformat(), "snapshot": snapshot(token)}, state)
    if command == "cardgate":
        path = Path(sys.argv[2])
        data = json.loads(path.read_text())
        text = (LOGS / "card.log").read_text(errors="ignore")
        window = [line for line in text.splitlines()
                  if data["startedAt"][:19] <= line[:19] <= data["finishedAt"][:19]]
        data["cardGate"] = card_gate()
        data["cardLogDuringRun"] = card_log_facts("\n".join(window))
        return write(path, data, state)
    if command == "run":
        label, out = sys.argv[2], sys.argv[3]
        data = {"label": label, "startedAt": datetime.now(timezone.utc).isoformat(), "adminActor": actor_of(token),
                "channel": state}
        data["chain"] = chain(state)
        data["cardGate"] = card_gate()
        data["sandbox"] = sandbox(token, state, label)
        data["snapshot"] = snapshot(token)
        data["finishedAt"] = datetime.now(timezone.utc).isoformat()
        text = (LOGS / "card.log").read_text(errors="ignore")
        data["cardLogDuringRun"] = card_log_facts("\n".join(
            line for line in text.splitlines() if data["startedAt"][:19] <= line[:19] <= data["finishedAt"][:19]))
        return write(out, data, state)
    raise SystemExit("用法：stage5_e2e.py setup | snapshot <out> | run <label> <out> | cardgate <已有运行记录>")


if __name__ == "__main__":
    main()
