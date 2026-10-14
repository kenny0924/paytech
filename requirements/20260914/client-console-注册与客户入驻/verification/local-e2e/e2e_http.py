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
