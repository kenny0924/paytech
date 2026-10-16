"""全链路 HTTP 辅助：经 Nginx 访问 Client/运营站点 API，不打印私密值。

默认目标是本机全链路（读取 local-environment/e2e/secrets.env）；设置 E2E_TARGET=test 时改为 mor-test
（运营 http://localhost:28080、Client https://localhost:28443，用 test CA 校验证书，管理员凭据读 private/access.md）。
"""
import json
import os
import re
import socket
import ssl
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[5]
TARGET = os.environ.get("E2E_TARGET", "local")
if TARGET == "test":
    OPERATIONS = "http://localhost:28080"
    CLIENT = "https://localhost:28443"
    MAILPIT = "http://localhost:28025"
    TLS = ssl.create_default_context(cafile=str(ROOT / "test-environment/private/test-ca.crt"))
    # macOS 系统解析器不解析 *.localhost（Chrome 内置解析）；MinIO 预签名地址 minio.mor-test.localhost 指向本机。
    _resolve = socket.getaddrinfo
    socket.getaddrinfo = lambda host, *rest, **kw: _resolve(
        "127.0.0.1" if isinstance(host, str) and host.endswith(".localhost") else host, *rest, **kw)
elif TARGET == "local":
    OPERATIONS = "https://127.0.0.1:18444"
    CLIENT = "https://127.0.0.1:18443"
    MAILPIT = "http://127.0.0.1:18025"
    # 本机自签名证书；只连接 127.0.0.1。
    TLS = ssl._create_unverified_context()
else:
    raise SystemExit("E2E_TARGET 只支持 local 或 test")


def secrets():
    if TARGET == "test":
        text = (ROOT / "test-environment/private/access.md").read_text()
        return {"E2E_ADMIN_EMAIL": re.search(r"账号：(\S+)", text).group(1),
                "E2E_ADMIN_PASSWORD": re.search(r"密码：(\S+)", text).group(1)}
    values = {}
    for line in (ROOT / "local-environment/e2e/secrets.env").read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


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


def mint_signup_link(email, since_utc):
    """本机兜底：Platform 认证邮件改经 Delivery（只有 SES 适配器）后本机收不到验证邮件。

    E2E_TARGET=local：在 mor_platform_e2e 找该邮箱（按脱敏值与创建时间）最新的 PENDING 注册挑战，
    用本机 CLIENT_LOCAL_HMAC_KEY_BASE64 为自生成的秘密重算 secret_hmac 并写回，返回 (challengeNo, secret)。
    E2E_TARGET=test（2026-10-16）：同样处理 mor-test 的 mor_platform（经 mor-test-mysql-1 容器），
    密钥取 test-environment/private/client-onboarding.env，数据库凭据取 test-environment/.env.test。
    HMAC 输入与 LocalHumanAuthenticationCryptography / ClientEmailChallengePolicy.proofInput 一致。不打印任何秘密。
    """
    if TARGET not in ("local", "test"):
        return None, None
    import base64
    import hashlib
    import hmac
    import secrets as pysecrets

    masked = email[0] + "***@" + email.split("@", 1)[1]
    local = read_env(ROOT / "test-environment/private/client-onboarding.env") if TARGET == "test" else secrets()
    sql = platform_sql

    rows = sql("SELECT challenge_id,challenge_no,active_scope_hash FROM t_authentication_challenge "
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
    sql(f"UPDATE t_authentication_challenge SET secret_hmac='{digest}' WHERE challenge_id={int(challenge_id)} "
        "AND status='PENDING'")
    return challenge_no, secret


def read_env(path):
    values = {}
    for line in path.read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


def platform_sql(statement):
    """以 platform_app 执行 Platform 库语句并返回行：本机 mor_platform_e2e；mor-test 经 mor-test-mysql-1 的 mor_platform。

    只供验收夹具使用；密码经环境变量传入，不打印。
    """
    import subprocess
    if TARGET == "test":
        env = read_env(ROOT / "test-environment/.env.test")
        command = ["docker", "exec", "-i", "-e", "MYSQL_PWD", "mor-test-mysql-1", "mysql", "-uplatform_app", "-N", "-B",
                   "--default-character-set=utf8mb4", "mor_platform"]
    else:
        env = read_env(ROOT / "local-environment/.env.local")
        command = ["mysql", "-h127.0.0.1", "-P13306", "-uplatform_app", "-N", "-B", "--default-character-set=utf8mb4",
                   "mor_platform_e2e"]
    result = subprocess.run(command + ["-e", statement], env={**os.environ, "MYSQL_PWD": env["MOR_PLATFORM_DB_PASSWORD"]},
                            capture_output=True, text=True, check=True)
    return [row.split("\t") for row in result.stdout.splitlines() if row]


PLATFORM_IMAGE = os.environ.get("E2E_PLATFORM_IMAGE", "mor-test/platform-support-service:client-console-20261016")


def platform_call(method, path, headers=None, body=None):
    """调用 Platform 私网接口（如 PLT-I03、Webhook 受理），返回 (status, text)。

    本机直连 127.0.0.1:18106；mor-test 的 Platform 没有宿主机端口，用 Platform 镜像起一次性容器接入
    mor-test-backend，运行 PrivateHttpProbe（请求经 stdin 传入，凭证不出现在命令行参数）。
    """
    if TARGET != "test":
        data = body.encode() if body is not None else (b"" if method == "POST" else None)
        request = urllib.request.Request("http://127.0.0.1:18106/platform-support" + path, data=data,
                                         headers=headers or {}, method=method)
        try:
            with urllib.request.urlopen(request, timeout=20) as response:
                return response.status, response.read().decode()
        except urllib.error.HTTPError as failure:
            return failure.code, failure.read().decode()
    import subprocess
    probe = _probe_classes()
    lines = [method, "http://platform-support-service:18106/platform-support" + path]
    lines += [f"{k}: {v}" for k, v in (headers or {}).items()]
    payload = "\n".join(lines) + "\n\n" + (body or "")
    result = subprocess.run(["docker", "run", "--rm", "-i", "--network", "mor-test-backend", "--entrypoint", "java",
                             "-v", f"{probe}:/probe:ro", PLATFORM_IMAGE, "-cp", "/probe", "PrivateHttpProbe"],
                            input=payload, capture_output=True, text=True, timeout=60, check=True)
    status, _, text = result.stdout.partition("\n")
    return int(status), text


_PROBE_DIR = None


def _probe_classes():
    global _PROBE_DIR
    if _PROBE_DIR is None:
        import subprocess
        import tempfile
        _PROBE_DIR = tempfile.mkdtemp(prefix="e2e-probe-")
        javac = os.path.join(os.environ.get("E2E_JDK25_HOME", "/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home"), "bin/javac")
        subprocess.run([javac, "--release", "25", "-d", _PROBE_DIR, str(Path(__file__).resolve().parent / "PrivateHttpProbe.java")],
                       check=True)
        os.chmod(_PROBE_DIR, 0o755)
    return _PROBE_DIR
