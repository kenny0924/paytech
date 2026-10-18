"""阶段 5 本机全链路（任务卡 14）HTTP / 数据库辅助：经 Nginx 访问 Client、运营、Client API、Checkout 站点，不打印私密值。

由 requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/e2e_http.py 复制改写：只支持本机（V2 不跑 mor-test）；
私密值读主仓库 local-environment（E2E_LOCAL_ENV 可改，本 worktree 没有 .env.local / e2e/）；库为 *_v2e2e 专用库，
账号口令与 run-service.sh 相同规则（.env.local 的 MOR_<X>_DB_PASSWORD 优先，否则取该服务 *-test init/ddl.sql 的本机默认值）。
"""
import base64
import json
import os
import re
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
TARGET = "local"
OPERATIONS = "https://127.0.0.1:18444"
CLIENT = "https://127.0.0.1:18443"
CLIENT_API = "https://127.0.0.1:18445"
CHECKOUT = "https://127.0.0.1:18446"
MAILPIT = "http://127.0.0.1:18025"
RABBITMQ_VHOST = os.environ.get("E2E_RABBITMQ_VHOST", "/mor-e2e")
# 本机自签名证书；只连接 127.0.0.1。
TLS = ssl._create_unverified_context()

# 私网直连（不经 Gateway）：服务 → 基址（含 context path）
PRIVATE = {
    "platform": "http://127.0.0.1:18106/platform-support",
    "client-master": "http://127.0.0.1:18101",
    "commercial": "http://127.0.0.1:18108",
    "compliance": "http://127.0.0.1:18112",
    "risk-decision": "http://127.0.0.1:18104",
    "consumer": "http://127.0.0.1:18109",
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
    return read_env(LOCAL_ENV / "e2e/secrets.env")


LOCAL = read_env(LOCAL_ENV / ".env.local")


# ---------- 数据库（只供验收夹具与核对；口令经环境变量传入 mysql，不打印） ----------

# 服务 → (库, 账号, .env.local 口令键)
DATABASES = {
    "platform": ("mor_platform_v2e2e", "platform_app", "MOR_PLATFORM_DB_PASSWORD"),
    "payment": ("mor_payment_v2e2e", "payment_app", "MOR_PAYMENT_DB_PASSWORD"),
    "vault": ("mor_vault", "vault_app", "MOR_VAULT_DB_PASSWORD"),
}
for _svc in ("client-master", "commercial", "compliance", "risk-decision", "acquiring", "consumer", "card",
             "channel-connector", "billing", "ledger", "account", "reconciliation", "tax"):
    _key = _svc.replace("-", "_")
    DATABASES[_svc] = (f"mor_{_key}_v2e2e", f"{_key}_app", f"MOR_{_key.upper()}_DB_PASSWORD")


def db_password(service):
    _, _, key = DATABASES[service]
    if LOCAL.get(key):
        return LOCAL[key]
    ddl = ROOT / f"paytech/{service}/paytech-{service}-test/src/test/resources/init/ddl.sql"
    match = re.search(r"IDENTIFIED BY '([^']*)'", ddl.read_text())
    if not match:
        raise SystemExit(f"找不到 {service} 账号口令：{key} 未设置且 {ddl} 无默认值")
    return match.group(1)


def sql(service, statement):
    """以服务自有账号执行一条语句，返回行（制表符分隔的字符串列表）。"""
    database, user, _ = DATABASES[service]
    result = subprocess.run(["mysql", "-h127.0.0.1", "-P13306", f"-u{user}", "-N", "-B", "--default-character-set=utf8mb4",
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
    request = urllib.request.Request(PRIVATE[service] + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as failure:
        return failure.code, failure.read().decode()


def platform_call(method, path, headers=None, body=None):
    """兼容旧脚本：Platform 私网接口（如 PLT-I03、Webhook 受理）。"""
    return private_call("platform", method, path, headers, body)


def rabbit_publish(exchange, routing_key, body, message_id, headers=None):
    """经 RabbitMQ 管理接口向本步 vhost 发布一条消息，返回是否被路由。只供夹具使用。"""
    auth = base64.b64encode(f"{LOCAL['MOR_RABBITMQ_USER']}:{LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    vhost = urllib.request.quote(RABBITMQ_VHOST, safe="")
    payload = {"properties": {"message_id": message_id, "content_type": "application/json", "delivery_mode": 2,
                              "headers": headers or {}},
               "routing_key": routing_key, "payload": body, "payload_encoding": "string"}
    request = urllib.request.Request(f"http://127.0.0.1:15672/api/exchanges/{vhost}/{exchange}/publish",
                                     data=json.dumps(payload).encode(),
                                     headers={"Authorization": "Basic " + auth, "Content-Type": "application/json"},
                                     method="POST")
    with urllib.request.urlopen(request, timeout=10) as response:
        return json.loads(response.read()).get("routed") is True


def rabbit_queues(prefix=""):
    """列出本步 vhost 中名称以 prefix 开头的队列：[(name, messages, consumers)]。"""
    auth = base64.b64encode(f"{LOCAL['MOR_RABBITMQ_USER']}:{LOCAL['MOR_RABBITMQ_PASSWORD']}".encode()).decode()
    vhost = urllib.request.quote(RABBITMQ_VHOST, safe="")
    request = urllib.request.Request(f"http://127.0.0.1:15672/api/queues/{vhost}", headers={"Authorization": "Basic " + auth})
    with urllib.request.urlopen(request, timeout=10) as response:
        return [(q["name"], q.get("messages", 0), q.get("consumers", 0)) for q in json.loads(response.read())
                if q["name"].startswith(prefix)]


def mint_signup_link(email, since_utc):
    """本机兜底：Platform 认证邮件经 Delivery（只有 SES 适配器）发送，本机收不到验证邮件。

    在 mor_platform_v2e2e 找该邮箱（按脱敏值与创建时间）最新的 PENDING 注册挑战，用本机 CLIENT_LOCAL_HMAC_KEY_BASE64
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
