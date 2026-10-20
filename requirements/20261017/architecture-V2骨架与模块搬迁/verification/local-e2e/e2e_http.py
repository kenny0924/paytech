"""阶段 5 本机全链路（任务卡 14）HTTP / 数据库辅助：经 Nginx 访问 Client、运营、Client API、Checkout 站点，不打印私密值。

由 requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/e2e_http.py 复制改写；本机库为 *_v2e2e 专用库，
私密值读主仓库 local-environment（E2E_LOCAL_ENV 可改，本 worktree 没有 .env.local / e2e/），
账号口令与 run-service.sh 相同规则（.env.local 的 MOR_<X>_DB_PASSWORD 优先，否则取该服务 *-test init/ddl.sql 的本机默认值）。

E2E_TARGET=test（2026-10-18 mor-test V2 部署，requirements/20261018/test-environment-V2部署与数据迁移）：
  运营 http://localhost:28080、Client https://localhost:28443、Client API https://localhost:28444（test CA 校验）；
  私密值读主仓库 test-environment（E2E_TEST_ENV 可改）：管理员凭据 private/access.md，Client 认证 HMAC 密钥
  private/client-onboarding.env，库口令与 RabbitMQ 账号 .env.test；库为 V2 正式库名 mor_<服务>（127.0.0.1:23306）；
  V2 服务没有宿主机端口，私网接口经接入 mor-test-backend 的一次性容器运行 PrivateHttpProbe（请求经 stdin，凭证不进命令行）；
  RabbitMQ 管理接口 127.0.0.1:25673，vhost /mor-test。test 没有 Hosted Checkout 站点（CHECKOUT 为 None）。
"""
import base64
import json
import os
import re
import socket
import ssl
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
LOCAL_ENV = Path(os.environ.get("E2E_LOCAL_ENV", "/Users/kenny/Desktop/Workspace/Code-MoR/local-environment"))
TEST_ENV = Path(os.environ.get("E2E_TEST_ENV", "/Users/kenny/Desktop/Workspace/Code-MoR/test-environment"))
TARGET = os.environ.get("E2E_TARGET", "local")
if TARGET == "test":
    OPERATIONS = "http://localhost:28080"
    CLIENT = "https://localhost:28443"
    CLIENT_API = "https://localhost:28444"
    CHECKOUT = None
    MAILPIT = "http://localhost:28025"
    RABBITMQ_MANAGEMENT = "http://127.0.0.1:25673"
    RABBITMQ_VHOST = "/mor-test"
    TLS = ssl.create_default_context(cafile=str(TEST_ENV / "private/test-ca.crt"))
    # macOS 系统解析器不解析 *.localhost（Chrome 内置解析）；MinIO 预签名地址 minio.mor-test.localhost 指向本机。
    _resolve = socket.getaddrinfo
    socket.getaddrinfo = lambda host, *rest, **kw: _resolve(
        "127.0.0.1" if isinstance(host, str) and host.endswith(".localhost") else host, *rest, **kw)
elif TARGET == "local":
    OPERATIONS = "https://127.0.0.1:18444"
    CLIENT = "https://127.0.0.1:18443"
    CLIENT_API = "https://127.0.0.1:18445"
    CHECKOUT = "https://127.0.0.1:18446"
    MAILPIT = "http://127.0.0.1:18025"
    RABBITMQ_MANAGEMENT = "http://127.0.0.1:15672"
    RABBITMQ_VHOST = os.environ.get("E2E_RABBITMQ_VHOST", "/mor-e2e")
    # 本机自签名证书；只连接 127.0.0.1。
    TLS = ssl._create_unverified_context()
else:
    raise SystemExit("E2E_TARGET 只支持 local 或 test")

# test：私网地址为 compose 服务名（在 mor-test-backend 内解析）
PRIVATE_TEST = {
    "platform": "http://platform-support-service:18106/platform-support",
    "client-master": "http://client-master-service:18101",
    "commercial": "http://commercial-service:18108",
    "compliance": "http://compliance-service:18112",
    "risk-decision": "http://risk-decision-service:18104",
    "counterparty": "http://counterparty-service:18109",
    "card": "http://card-service:18111",
    "channel-connector": "http://channel-connector-service:18110",
    "payment": "http://payment-service:18103/payment",
    "acquiring": "http://acquiring-service:18102/commerce",
    "billing": "http://billing-service:18113",
    "ledger": "http://ledger-service:18105",
    "account": "http://account-service:18114",
    "reconciliation": "http://reconciliation-service:18115",
    "tax": "http://tax-service:18116",
}
PROBE_IMAGE = os.environ.get("E2E_PROBE_IMAGE", "mor-test/platform-support-service:development")

# 私网直连（不经 Gateway）：服务 → 基址（含 context path）
PRIVATE = PRIVATE_TEST if TARGET == "test" else {
    "platform": "http://127.0.0.1:18106/platform-support",
    "client-master": "http://127.0.0.1:18101",
    "commercial": "http://127.0.0.1:18108",
    "compliance": "http://127.0.0.1:18112",
    "risk-decision": "http://127.0.0.1:18104",
    "counterparty": "http://127.0.0.1:18109",
    "card": "http://127.0.0.1:18111",
    "channel-connector": "http://127.0.0.1:18110",
    "payment": "http://127.0.0.1:18103/payment",
    "acquiring": "http://127.0.0.1:18102/commerce",
    "billing": "http://127.0.0.1:18113",
    "ledger": "http://127.0.0.1:18105",
    "account": "http://127.0.0.1:18114",
    "reconciliation": "http://127.0.0.1:18115",
    "tax": "http://127.0.0.1:18116",
}


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


def secrets():
    if TARGET == "test":
        # 管理员凭据（access.md）+ Client 认证本机 HMAC 密钥（mint_signup_link / team_flow 用 CLIENT_LOCAL_HMAC_KEY_BASE64）
        text = (TEST_ENV / "private/access.md").read_text()
        values = read_env(TEST_ENV / "private/client-onboarding.env")
        values.update(E2E_ADMIN_EMAIL=re.search(r"账号：(\S+)", text).group(1),
                      E2E_ADMIN_PASSWORD=re.search(r"密码：(\S+)", text).group(1))
        return values
    return read_env(LOCAL_ENV / "e2e/secrets.env")


# LOCAL：库口令与 RabbitMQ 账号的来源（test 为 .env.test，键名相同）
LOCAL = read_env(TEST_ENV / ".env.test") if TARGET == "test" else read_env(LOCAL_ENV / ".env.local")
MYSQL_PORT = "23306" if TARGET == "test" else "13306"
DB_SUFFIX = "" if TARGET == "test" else "_v2e2e"


# ---------- 数据库（只供验收夹具与核对；口令经环境变量传入 mysql，不打印） ----------

# 服务 → (库, 账号, .env.local / .env.test 口令键)
DATABASES = {
    "platform": (f"mor_platform{DB_SUFFIX}", "platform_app", "MOR_PLATFORM_DB_PASSWORD"),
    "payment": (f"mor_payment{DB_SUFFIX}", "payment_app", "MOR_PAYMENT_DB_PASSWORD"),
    "vault": ("mor_vault", "vault_app", "MOR_VAULT_DB_PASSWORD"),
}
for _svc in ("client-master", "commercial", "compliance", "risk-decision", "acquiring", "counterparty", "card",
             "channel-connector", "billing", "ledger", "account", "reconciliation", "tax"):
    _key = _svc.replace("-", "_")
    DATABASES[_svc] = (f"mor_{_key}{DB_SUFFIX}", f"{_key}_app", f"MOR_{_key.upper()}_DB_PASSWORD")


def db_password(service):
    _, _, key = DATABASES[service]
    if LOCAL.get(key):
        return LOCAL[key]
    if TARGET == "test":
        raise SystemExit(f"找不到 {service} 账号口令：.env.test 缺少 {key}")
    ddl = ROOT / f"paytech/{service}/paytech-{service}-test/src/test/resources/init/ddl.sql"
    match = re.search(r"IDENTIFIED BY '([^']*)'", ddl.read_text())
    if not match:
        raise SystemExit(f"找不到 {service} 账号口令：{key} 未设置且 {ddl} 无默认值")
    return match.group(1)


def sql(service, statement):
    """以服务自有账号执行一条语句，返回行（制表符分隔的字符串列表）。"""
    database, user, _ = DATABASES[service]
    result = subprocess.run(["mysql", "-h127.0.0.1", f"-P{MYSQL_PORT}", "--protocol=tcp", f"-u{user}", "-N", "-B", "--default-character-set=utf8mb4",
                             database, "-e", statement], env={**os.environ, "MYSQL_PWD": db_password(service)},
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"{service} SQL 失败：{result.stderr.strip()[:300]}")
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def platform_sql(statement):
    return sql("platform", statement)


# ---------- HTTP ----------

class Failure(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body[:400]}")
        self.status, self.body = status, body


def call(origin, path, method="GET", body=None, token=None, key=False):
    headers = {"Origin": origin, "Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
    if body is not None or method not in ("GET", "HEAD", "DELETE"):
        # 与前端一致：无请求体的写命令也声明 JSON 内容类型。
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = str(uuid.uuid4())
    # Gateway 对管理路由限流：429 时按 Retry-After（缺省 10 秒）等待后用同一幂等键重试。
    for attempt in range(12):
        request = urllib.request.Request(origin + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, context=TLS, timeout=20) as response:
                raw = response.read().decode()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 11:
                time.sleep(int(failure.headers.get("Retry-After") or 10))
                continue
            raise Failure(failure.code, failure.read().decode()) from None


class Operations:
    """运营会话：登录后携带 Bearer 调用 /api/operations 下的接口。"""

    def __init__(self, login, password):
        tokens = call(OPERATIONS, "/api/operations/auth/v1/login", "POST", {"loginIdentifier": login, "password": password})
        self.token = tokens["accessToken"]

    def get(self, path):
        return call(OPERATIONS, "/api/operations" + path, token=self.token)

    def send(self, method, path, body=None, key=True):
        return call(OPERATIONS, "/api/operations" + path, method, body, self.token, key)


def private_call(service, method, path, headers=None, body=None, timeout=40):
    """调用服务私网接口（不经 Gateway），返回 (status, text)。body 为 dict 时按 JSON 发送。"""
    headers = dict(headers or {})
    data = None
    if isinstance(body, (dict, list)):
        data = json.dumps(body).encode()
        headers.setdefault("Content-Type", "application/json")
    elif body is not None:
        data = body.encode()
    elif method == "POST":
        data = b""
    if TARGET == "test":
        return _probe_call(method, PRIVATE[service] + path, headers, data, timeout)
    request = urllib.request.Request(PRIVATE[service] + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode()


def platform_call(method, path, headers=None, body=None):
    """兼容旧脚本：Platform 私网接口（如 PLT-I03、Webhook 受理）。"""
    return private_call("platform", method, path, headers, body)


_PROBE_DIR = None


def _probe_classes():
    """编译 PrivateHttpProbe 到临时目录（只编译一次）；用 JDK 25（E2E_JDK25_HOME 可改）。"""
    global _PROBE_DIR
    if _PROBE_DIR is None:
        import tempfile
        _PROBE_DIR = tempfile.mkdtemp(prefix="e2e-probe-")
        javac = os.path.join(os.environ.get("E2E_JDK25_HOME", "/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home"),
                             "bin/javac")
        subprocess.run([javac, "--release", "25", "-d", _PROBE_DIR, str(HERE / "PrivateHttpProbe.java")], check=True)
        os.chmod(_PROBE_DIR, 0o755)
    return _PROBE_DIR


def _probe_call(method, url, headers, data, timeout):
    """mor-test：在接入 mor-test-backend 的一次性容器内发请求，返回 (status, text)。"""
    lines = [method, url] + [f"{k}: {v}" for k, v in (headers or {}).items()]
    payload = "\n".join(lines) + "\n\n" + ((data or b"").decode())
    result = subprocess.run(["docker", "run", "--rm", "-i", "--network", "mor-test-backend", "--entrypoint", "java",
                             "-v", f"{_probe_classes()}:/probe:ro", PROBE_IMAGE, "-cp", "/probe", "PrivateHttpProbe"],
                            input=payload, capture_output=True, text=True, timeout=timeout + 30)
    if result.returncode != 0:
        raise RuntimeError(f"私网探针失败（{url}）：{result.stderr.strip()[:300]}")
    status, _, text = result.stdout.partition("\n")
    return int(status), text


def rabbit_publish(exchange, routing_key, body, message_id, headers=None):
    """经 RabbitMQ 管理接口向本步 vhost 发布一条消息，返回是否被路由。只供夹具使用。"""
    auth = base64.b64encode(f"{LOCAL['MOR_RABBITMQ_USER']}:{LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    vhost = urllib.request.quote(RABBITMQ_VHOST, safe="")
    payload = {"properties": {"message_id": message_id, "content_type": "application/json", "delivery_mode": 2,
                              "headers": headers or {}},
               "routing_key": routing_key, "payload": body, "payload_encoding": "string"}
    request = urllib.request.Request(f"{RABBITMQ_MANAGEMENT}/api/exchanges/{vhost}/{exchange}/publish",
                                     data=json.dumps(payload).encode(),
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"},
                                     method="POST")
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read()).get("routed") is True


def rabbit_queues(prefix=""):
    """列出本步 vhost 中名称以 prefix 开头的队列：[(name, messages, consumers)]。"""
    auth = base64.b64encode(f"{LOCAL['MOR_RABBITMQ_USER']}:{LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    vhost = urllib.request.quote(RABBITMQ_VHOST, safe="")
    request = urllib.request.Request(f"{RABBITMQ_MANAGEMENT}/api/queues/{vhost}", headers={"Authorization": "Basic " + auth})
    with urllib.request.urlopen(request, timeout=10) as response:
        return [(q["name"], q.get("messages", 0), q.get("consumers", 0)) for q in json.loads(response.read())
                if q["name"].startswith(prefix)]


def mint_signup_link(email, since_utc):
    """本机兜底：Platform 认证邮件经 Delivery（只有 SES 适配器）发送，本机收不到验证邮件。

    在 Platform 库（本机 mor_platform_v2e2e，test mor_platform）找该邮箱（按脱敏值与创建时间）最新的 PENDING 注册挑战，用本机 CLIENT_LOCAL_HMAC_KEY_BASE64
    为自生成的秘密重算 secret_hmac 并写回，返回 (challengeNo, secret)。HMAC 输入与 LocalHumanAuthenticationCryptography /
    ClientEmailChallengePolicy.proofInput 一致。不打印任何秘密。
    """
    import hashlib
    import hmac
    import secrets as pysecrets

    masked = email[0] + "***@" + email.split("@", 1)[1]
    local = secrets()
    rows = platform_sql("SELECT challenge_id,challenge_no,active_scope_hash FROM t_authentication_challenge "
                        f"WHERE realm='CUSTOMER' AND purpose='SIGN_UP_EMAIL' AND status='PENDING' AND email_masked='{masked}' "
                        f"AND create_time>='{since_utc}' ORDER BY create_time DESC LIMIT 1")
    if not rows:
        return None, None
    challenge_id, challenge_no, scope_hash = rows[0]
    secret = base64.urlsafe_b64encode(pysecrets.token_bytes(32)).decode().rstrip("=")

    def tuple_(*values):
        return "".join(f"{len(v.encode())}:{v}" for v in values)

    proof = tuple_("CUSTOMER-EMAIL-CHALLENGE", challenge_no, "SIGN_UP_EMAIL", scope_hash, secret)
    digest = hmac.new(base64.b64decode(local["CLIENT_LOCAL_HMAC_KEY_BASE64"]),
                      ("client-email-challenge-v1" + "\\0" + proof).encode(), hashlib.sha256).hexdigest()
    platform_sql(f"UPDATE t_authentication_challenge SET secret_hmac='{digest}' WHERE challenge_id={int(challenge_id)} "
                 "AND status='PENDING'")
    return challenge_no, secret
