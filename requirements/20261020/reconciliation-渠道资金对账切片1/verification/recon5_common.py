"""对账切片 1 阶段 5 本机验收公共件：私有栈运营入口（Nginx 18454 → 私有 Gateway 18090 → 私有 Ops BFF 18211 → reconciliation 18115）、
IAM 合成人员、channel-connector 合成渠道 / MID、BillingFactRecorded 夹具发布（正式发布器）、reconciliation.events 临时观察队列。

不打印口令、Token、密钥或银行账号；运行记录（含合成人员口令）只写会话 scratchpad。
"""
import base64
import json
import os
import re
import secrets as pysecrets
import ssl
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
LOCAL_ENV = ROOT / "local-environment"
STATE = Path(os.environ.get("RECON5_STATE", "/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/"
                            "1a70f5c0-500a-4725-bdcb-fc5d7b9c803d/scratchpad/recon5"))
ORIGIN = "https://127.0.0.1:18454"
TLS = ssl._create_unverified_context()
VHOST = "/mor-recon5"
RMQ = "http://127.0.0.1:15672"
PROBE_QUEUE = "recon5.probe.reconciliation-events"
CHINESE = re.compile(r"[一-鿿]")
PERM = {"RECON_READ": "900000000000123101", "RECON_OPERATE": "900000000000123102", "RECON_APPROVE": "900000000000123103",
        "ACCOUNTING_READ": "900000000000097101"}


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


LOCAL = read_env(LOCAL_ENV / ".env.local")
SECRETS = read_env(LOCAL_ENV / "e2e/secrets.env")

results = []


def check(ac, name, ok, evidence=None):
    results.append((ac, name, bool(ok)))
    suffix = "" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False, default=str)[:500]}"
    print(f"[{'通过' if ok else '失败'}] {ac} {name}{suffix}", flush=True)
    return bool(ok)


def raw(token, method, path, body=None, prefix="/api/operations", key=True):
    """请求私有栈运营入口，返回 (状态, 响应体)。写命令带 Idempotency-Key；429 按 Retry-After 重试。"""
    headers = {"Origin": ORIGIN, "Accept": "application/json"}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        if key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else (b"" if method != "GET" else None)
    for attempt in range(20):
        request = urllib.request.Request(ORIGIN + prefix + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=TLS, timeout=60) as response:
                text = response.read().decode()
                return response.status, json.loads(text) if text else {}
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 19:
                time.sleep(int(failure.headers.get("Retry-After") or 5))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text)
            except ValueError:
                return failure.code, {"raw": text[:300]}


def rejected(status, body, detail_part=None, http=400):
    detail = body.get("detail") or ""
    return status == http and bool(CHINESE.search(detail)) and (detail_part is None or detail_part in detail)


def login(identifier, password):
    status, body = raw(None, "POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, key=False)
    if status != 200:
        raise SystemExit(f"登录失败 {status} {body}")
    return body["accessToken"]


def admin_token():
    return login(SECRETS["E2E_ADMIN_EMAIL"], SECRETS["E2E_ADMIN_PASSWORD"])


def synthetic_person(admin, label, permission_ids, run, record):
    """经 IAM 新建合成角色与人员，首登改口令后返回 (token, accountId, email, password)。"""
    status, role = raw(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_RECON_{label}_{run}", "roleName": f"对账验收 {label} {run}", "permissionIds": permission_ids,
        "reason": f"对账切片 1 阶段 5 本机验收：合成角色 {label}"})
    if not check("准备", f"IAM 新建合成角色 {label}（{status}）", status in (200, 201) and role.get("roleId"), role):
        raise SystemExit(1)
    record.setdefault("platformRoles", []).append(role["roleId"])
    email = f"recon-ver-{label.lower()}-{run.lower()}@example.test"
    initial = "Recon initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Recon ver " + pysecrets.token_urlsafe(18) + "!"
    status, created = raw(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
        "reason": f"对账切片 1 阶段 5 本机验收：合成人员 {label}"})
    if not check("准备", f"IAM 新建合成人员 {label}（{status}）", status in (200, 201) and created.get("accountId"), created):
        raise SystemExit(1)
    record.setdefault("platformAccounts", []).append(created["accountId"])
    first = login(email, initial)
    raw(first, "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, key=False)
    record.setdefault("people", {})[label] = {"email": email, "password": password, "accountId": created["accountId"]}
    return login(email, password), created["accountId"], email, password


# ---------- RabbitMQ（临时 vhost，管理接口） ----------

def _rmq(method, path, body=None):
    auth = base64.b64encode(f"{LOCAL['MOR_RABBITMQ_USER']}:{LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    request = urllib.request.Request(RMQ + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        text = response.read().decode()
        return json.loads(text) if text else None


def vhost_path():
    return urllib.request.quote(VHOST, safe="")


def bind_probe():
    """声明观察队列（非持久、2 小时无人使用自动过期）并绑定 reconciliation.events（Fanout）。"""
    _rmq("PUT", f"/api/queues/{vhost_path()}/{PROBE_QUEUE}",
         {"durable": False, "auto_delete": False, "arguments": {"x-expires": 7_200_000}})
    _rmq("POST", f"/api/bindings/{vhost_path()}/e/reconciliation.events/q/{PROBE_QUEUE}", {"routing_key": "", "arguments": {}})


def drain_probe(max_count=200):
    """取出观察队列中的全部信封（ack 后移除），返回解析后的信封列表。"""
    messages = _rmq("POST", f"/api/queues/{vhost_path()}/{PROBE_QUEUE}/get",
                    {"count": max_count, "ackmode": "ack_requeue_false", "encoding": "auto", "truncate": 500000})
    return [json.loads(m["payload"]) for m in messages]


def await_events(collected, event_type, count, timeout=60):
    """持续取观察队列直到收到 count 条 event_type；collected 累积全部已收事件。"""
    deadline = time.time() + timeout
    while time.time() < deadline:
        collected.extend(drain_probe())
        found = [e for e in collected if e["eventType"] == event_type]
        if len(found) >= count:
            return found
        time.sleep(1)
    return [e for e in collected if e["eventType"] == event_type]


def queues(prefix=""):
    return [(q["name"], q.get("messages", 0), q.get("consumers", 0)) for q in _rmq("GET", f"/api/queues/{vhost_path()}")
            if q["name"].startswith(prefix)]


# ---------- BillingFactRecorded 夹具 ----------

def fact(fact_type, clearing_account_id, gross, ext_type=None, ext_id=None, channel_cost=None, client_id="81000001",
         entity_no="AE001", supersedes=None):
    """BillingFactRecorded Schema v1 Payload（与 reconciliation-test BillingEventPublisher.json 同序同格式）。"""
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")
    fact_id = str(int(time.time() * 1_000_000) * 10 + pysecrets.randbelow(10))
    refs = []
    if clearing_account_id:
        refs.append(("CLEARING_ACCOUNT", str(clearing_account_id), None, None))
    if ext_id:
        refs.append(("CHANNEL_TRANSACTION_REF", str(7_000_000_000 + pysecrets.randbelow(10**9)), ext_type, ext_id))
    if fact_type == "PAYMENT":
        refs.append(("PAYMENT_ATTEMPT", str(7_100_000_000 + pysecrets.randbelow(10**9)), None, None))
    if fact_type == "REFUND":
        refs.append(("CHANNEL_REFUND", str(7_200_000_000 + pysecrets.randbelow(10**9)), None, None))
    lines = []
    if channel_cost:
        lines.append({"lineNo": "1", "componentType": "CHANNEL_COST", "payerRole": "OPERATING_ENTITY", "payerId": entity_no,
                      "payeeRole": "CHANNEL", "payeeId": "PAYERMAX_MOCK", "direction": "CHARGE",
                      "postedAmount": channel_cost, "targetBucket": None})
    return {"factId": fact_id, "factVersion": "2" if supersedes else "1", "supersedesFactId": supersedes,
            "parentFactId": None, "factType": fact_type, "originType": "AUTO", "status": "APPROVED",
            "sourceSystem": "payment", "sourceEventKey": "recon5-" + uuid.uuid4().hex,
            "sourceVersion": "1", "sourcePayloadHash": uuid.uuid4().hex + uuid.uuid4().hex, "businessTime": now,
            "recordedTime": now, "balanceAvailableTime": None, "clientId": client_id, "entityNo": entity_no,
            "localPartnerId": None, "financeEntityId": None, "currency": "USD", "grossAmount": gross,
            "refs": [{"refType": t, "refId": i, "externalReferenceType": et, "externalTransactionId": ei} for t, i, et, ei in refs],
            "lines": lines}


def publish_facts(facts, threads=1):
    """以正式发布器（BillingFactPublisher.java）发布到临时 vhost 的 billing.events，返回事件 ID 列表。"""
    env = {**os.environ, "RECON5_RABBIT_USER": LOCAL["MOR_RABBITMQ_USER"], "RECON5_RABBIT_PASSWORD": LOCAL["MOR_RABBITMQ_PASSWORD"],
           "RECON5_RABBIT_VHOST": VHOST, "RECON5_PUBLISH_THREADS": str(threads)}
    payload = "\n".join(json.dumps(f, ensure_ascii=False) for f in facts) + "\n"
    result = subprocess.run(["/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java", "--class-path",
                             f"{STATE}/lib/*", str(HERE / "BillingFactPublisher.java")], input=payload, capture_output=True,
                            text=True, env=env, timeout=1800)
    if result.returncode != 0:
        raise SystemExit("发布失败：" + result.stderr[-1500:])
    print("  publisher:", result.stderr.strip().splitlines()[-1] if result.stderr.strip() else "", flush=True)
    return result.stdout.split()


# ---------- 数据库只读核对 ----------

def recon_db_password():
    if LOCAL.get("MOR_RECONCILIATION_DB_PASSWORD"):
        return LOCAL["MOR_RECONCILIATION_DB_PASSWORD"]
    ddl = ROOT / "paytech/reconciliation/paytech-reconciliation-test/src/test/resources/init/ddl.sql"
    return re.search(r"IDENTIFIED BY '([^']*)'", ddl.read_text()).group(1)


def recon_sql(statement):
    result = subprocess.run(["mysql", "-h127.0.0.1", "-P13306", "--protocol=tcp", "-ureconciliation_app", "-N", "-B",
                             "--default-character-set=utf8mb4", "mor_reconciliation_v2e2e", "-e", statement],
                            env={**os.environ, "MYSQL_PWD": recon_db_password()}, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def wait_until(predicate, timeout=60, step=1.0):
    deadline = time.time() + timeout
    while time.time() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(step)
    return predicate()


def sha256_text(text):
    import hashlib
    return hashlib.sha256(text.encode()).hexdigest()


def parse_channel_csv(text):
    """与页面相同的两行 #HEADER 写法（I-09）：第一行列名、第二行取值，其后明细列名与明细。返回 (header, lines)。"""
    import csv
    import io
    rows = list(csv.reader(io.StringIO(text)))
    names, values = rows[0][1:], rows[1][1:]
    header = dict(zip(names, values))
    columns = rows[2]
    lines = [dict(zip(columns, r)) for r in rows[3:] if r]
    return header, lines
