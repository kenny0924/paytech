"""对账切片 1 阶段 7 mor-test 验收公共件（由阶段 5 verification/recon5_common.py 改写到 mor-test）。

入口：运营 http://localhost:28080（Nginx → Gateway 权限点 → Operations BFF → reconciliation）；管理员取 test-environment/private/access.md。
MQ：RabbitMQ 127.0.0.1:25672 / 管理 25673，vhost /mor-test。合成 BillingFactRecorded 只投 reconciliation.billing.hash（account 收不到）；
reconciliation.events 绑定临时观察队列 recon7.probe.reconciliation-events（非持久、2 小时无人使用自动过期，清理时删除）。
库：只读核对经 docker exec mor-test-mysql-1（reconciliation_app / 其他库 root），口令只经 MYSQL_PWD 传入。
运行记录（ID、合成人员口令）写 RECON7_STATE（默认 ~/Code-MoR-backups/recon-slice1-stage7，0700）；不打印口令、Token 或银行账号。
"""
import base64
import json
import os
import re
import secrets as pysecrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import zipfile
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
TEST_ENV = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", ROOT / "test-environment"))
STATE = Path(os.environ.get("RECON7_STATE", Path.home() / "Code-MoR-backups/recon-slice1-stage7"))
STATE.mkdir(parents=True, exist_ok=True)
os.chmod(STATE, 0o700)
ORIGIN = "http://localhost:28080"
VHOST = "/mor-test"
RMQ = "http://127.0.0.1:25673"
PROBE_QUEUE = "recon7.probe.reconciliation-events"
CANDIDATE_IMAGE = "sha256:f181a04cad2db3fc41eaadca51d04c7a3ca586264d08c62bd9e7a264f7b4b353"
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


ENV = read_env(TEST_ENV / ".env.test")
results = []


def check(ac, name, ok, evidence=None):
    results.append((ac, name, bool(ok)))
    suffix = "" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False, default=str)[:500]}"
    print(f"[{'通过' if ok else '失败'}] {ac} {name}{suffix}", flush=True)
    return bool(ok)


def raw(token, method, path, body=None, prefix="/api/operations", key=True):
    """请求 mor-test 运营入口，返回 (状态, 响应体)。写命令带 Idempotency-Key；429 按 Retry-After 重试。"""
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
            with urllib.request.urlopen(request, timeout=60) as response:
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
        raise SystemExit(f"登录失败 {status} {body.get('detail') or body.get('code')}")
    return body["accessToken"]


def admin_token():
    text = (TEST_ENV / "private/access.md").read_text()
    return login(re.search(r"账号：(\S+)", text).group(1), re.search(r"密码：(\S+)", text).group(1))


def synthetic_person(admin, label, permission_ids, run, record):
    """经 IAM 新建合成角色与人员，首登改口令后返回 (token, accountId)。"""
    status, role = raw(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_RECON7_{label}_{run}", "roleName": f"对账 mor-test 验收 {label} {run}", "permissionIds": permission_ids,
        "reason": f"对账切片 1 阶段 7 mor-test 验收：合成角色 {label}（验收后删除）"})
    if not check("准备", f"IAM 新建合成角色 {label}（{status}）", status in (200, 201) and role.get("roleId"), role):
        raise SystemExit(1)
    record.setdefault("platformRoles", []).append(role["roleId"])
    email = f"recon7-{label.lower()}-{run.lower()}@example.test"
    initial = "Recon initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Recon seven " + pysecrets.token_urlsafe(18) + "!"
    status, created = raw(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
        "reason": f"对账切片 1 阶段 7 mor-test 验收：合成人员 {label}（验收后删除）"})
    if not check("准备", f"IAM 新建合成人员 {label}（{status}）", status in (200, 201) and created.get("accountId"), created):
        raise SystemExit(1)
    record.setdefault("platformAccounts", []).append(created["accountId"])
    first = login(email, initial)
    raw(first, "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, key=False)
    record.setdefault("people", {})[label] = {"email": email, "password": password, "accountId": created["accountId"]}
    return login(email, password), created["accountId"]


# ---------- RabbitMQ（/mor-test，管理接口） ----------

def rmq(method, path, body=None):
    auth = base64.b64encode(f"{ENV['MOR_RABBITMQ_USER']}:{ENV['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    request = urllib.request.Request(RMQ + path, method=method, data=None if body is None else json.dumps(body).encode(),
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        text = response.read().decode()
        return json.loads(text) if text else None


def vhost_path():
    return urllib.request.quote(VHOST, safe="")


def bind_probe():
    """声明观察队列并绑定 reconciliation.events（Fanout）；交换机由新 reconciliation 启动时声明。"""
    rmq("PUT", f"/api/queues/{vhost_path()}/{PROBE_QUEUE}", {"durable": False, "auto_delete": False, "arguments": {"x-expires": 7_200_000}})
    rmq("POST", f"/api/bindings/{vhost_path()}/e/reconciliation.events/q/{PROBE_QUEUE}", {"routing_key": "", "arguments": {}})


def drain_probe(max_count=200):
    messages = rmq("POST", f"/api/queues/{vhost_path()}/{PROBE_QUEUE}/get",
                   {"count": max_count, "ackmode": "ack_requeue_false", "encoding": "auto", "truncate": 500000})
    return [json.loads(m["payload"]) for m in messages]


def await_events(collected, event_type, count, timeout=90):
    deadline = time.time() + timeout
    while time.time() < deadline:
        collected.extend(drain_probe())
        found = [e for e in collected if e["eventType"] == event_type]
        if len(found) >= count:
            return found
        time.sleep(1)
    return [e for e in collected if e["eventType"] == event_type]


def queue(name):
    try:
        return rmq("GET", f"/api/queues/{vhost_path()}/{urllib.request.quote(name, safe='')}")
    except urllib.error.HTTPError:
        return None


# ---------- BillingFactRecorded 夹具 ----------

def fact(fact_type, clearing_account_id, gross, ext_type=None, ext_id=None, channel_cost=None, client_id="81000001",
         entity_no="AE001", payee_id="PAYERMAX"):
    """BillingFactRecorded Schema v1 Payload（与阶段 5 夹具同序同格式）。"""
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
                      "payeeRole": "CHANNEL", "payeeId": payee_id, "direction": "CHARGE",
                      "postedAmount": channel_cost, "targetBucket": None})
    return {"factId": fact_id, "factVersion": "1", "supersedesFactId": None,
            "parentFactId": None, "factType": fact_type, "originType": "AUTO", "status": "APPROVED",
            "sourceSystem": "payment", "sourceEventKey": "recon7-" + uuid.uuid4().hex,
            "sourceVersion": "1", "sourcePayloadHash": uuid.uuid4().hex + uuid.uuid4().hex, "businessTime": now,
            "recordedTime": now, "balanceAvailableTime": None, "clientId": client_id, "entityNo": entity_no,
            "localPartnerId": None, "financeEntityId": None, "currency": "USD", "grossAmount": gross,
            "refs": [{"refType": t, "refId": i, "externalReferenceType": et, "externalTransactionId": ei} for t, i, et, ei in refs],
            "lines": lines}


def publisher_lib():
    """从候选 reconciliation 镜像取出 exec JAR 并展开 BOOT-INF/lib（只在本机 STATE 目录，不碰 mor-test 容器）。"""
    lib = STATE / "lib"
    if lib.exists() and any(lib.iterdir()):
        return lib
    lib.mkdir(parents=True, exist_ok=True)
    container = subprocess.run(["docker", "create", CANDIDATE_IMAGE], capture_output=True, text=True, check=True).stdout.strip()
    try:
        subprocess.run(["docker", "cp", f"{container}:/app/application.jar", str(STATE / "reconciliation.jar")], check=True,
                       capture_output=True)
    finally:
        subprocess.run(["docker", "rm", container], capture_output=True)
    with zipfile.ZipFile(STATE / "reconciliation.jar") as jar:
        for name in jar.namelist():
            if name.startswith("BOOT-INF/lib/") and name.endswith(".jar"):
                (lib / Path(name).name).write_bytes(jar.read(name))
    return lib


def publish_facts(facts):
    """以正式发布器（ReconBillingFactPublisher.java）只投 reconciliation.billing.hash，返回事件 ID 列表。"""
    lib = publisher_lib()
    env = {**os.environ, "RECON7_RABBIT_USER": ENV["MOR_RABBITMQ_USER"], "RECON7_RABBIT_PASSWORD": ENV["MOR_RABBITMQ_PASSWORD"],
           "RECON7_RABBIT_VHOST": VHOST, "RECON7_RABBIT_HOST": "127.0.0.1", "RECON7_RABBIT_PORT": "25672"}
    payload = "\n".join(json.dumps(f, ensure_ascii=False) for f in facts) + "\n"
    result = subprocess.run(["/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java", "--class-path", f"{lib}/*",
                             str(HERE / "ReconBillingFactPublisher.java")], input=payload, capture_output=True, text=True, env=env,
                            timeout=600)
    if result.returncode != 0:
        raise SystemExit("发布失败：" + result.stderr[-1500:])
    print("  publisher:", result.stderr.strip().splitlines()[-1] if result.stderr.strip() else "", flush=True)
    return result.stdout.split()


# ---------- 数据库只读核对 ----------

def sql(database, statement, user="root"):
    password = ENV["MOR_MYSQL_ROOT_PASSWORD"] if user == "root" else ENV["MOR_RECONCILIATION_DB_PASSWORD"]
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={password}", "mor-test-mysql-1", "mysql", f"-u{user}", "-N", "-B",
                             "--default-character-set=utf8mb4", database, "-e", statement], capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def recon_sql(statement):
    return sql("mor_reconciliation", statement, user="reconciliation_app")


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
    """两行 #HEADER 写法（I-09）：第一行列名、第二行取值，其后明细列名与明细。返回 (header, lines)。"""
    import csv
    import io
    rows = list(csv.reader(io.StringIO(text)))
    header = dict(zip(rows[0][1:], rows[1][1:]))
    columns = rows[2]
    return header, [dict(zip(columns, r)) for r in rows[3:] if r]
