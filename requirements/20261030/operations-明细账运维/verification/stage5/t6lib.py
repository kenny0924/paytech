"""明细账运维阶段 5（T6）本机综合验证公共函数：运营登录、BFF 调用（返回状态码 + 响应体）、私网调用、root 只读/写 SQL、状态文件。
只连本机 *_v2e2e 库与 127.0.0.1 服务；不打印 Token、口令。"""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "local"
HERE = Path(__file__).resolve().parent
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http as E  # noqa: E402

STATE = Path.home() / "Code-MoR-backups/plops-stage5-t6/state.json"  # 含合成账号口令，不放仓库
ROOT_PWD = E.read_env(E.LOCAL_ENV / ".env.local")["MOR_MYSQL_ROOT_PASSWORD"]
ALLOWED_DB = {"mor_account_v2e2e", "mor_ledger_v2e2e", "mor_billing_v2e2e", "mor_platform_v2e2e"}


def load():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save(state):
    STATE.write_text(json.dumps(state, ensure_ascii=False, indent=2))


def q(db, statement):
    """root 执行（只允许 *_v2e2e 四个库），返回行列表。"""
    assert db in ALLOWED_DB, db
    r = subprocess.run(["docker", "exec", "-e", "MYSQL_PWD", "mor-local-mysql-1", "mysql", "-uroot", "-N", "-B",
                        "--default-character-set=utf8mb4", db, "-e", statement],
                       env={**os.environ, "MYSQL_PWD": ROOT_PWD}, capture_output=True, text=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.strip()[:400])
    return [row.split("\t") for row in r.stdout.splitlines() if row]


def login(email=None, password=None):
    if email is None:
        s = E.secrets()
        email, password = s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"]
    return E.Operations(email, password).token


def ops(method, path, token, body=None, key=True, timeout=60):
    """经 Nginx 18444 → Gateway → ops-bff；返回 (状态, JSON 或文本, 耗时秒)。429 按 Retry-After 重试。"""
    headers = {"Origin": E.OPERATIONS, "Accept": "application/json", "Authorization": "Bearer " + token}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
    if method != "GET":
        headers["Content-Type"] = "application/json"
        if data is None:
            data = b""
        if key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
    for attempt in range(12):
        req = urllib.request.Request(E.OPERATIONS + "/api/operations" + path, data=data, headers=headers, method=method)
        t0 = time.monotonic()
        try:
            with urllib.request.urlopen(req, context=E.TLS, timeout=timeout) as resp:
                raw = resp.read().decode()
                return resp.status, (json.loads(raw) if raw else None), time.monotonic() - t0
        except urllib.error.HTTPError as f:
            raw = f.read().decode()
            if f.code == 429 and attempt < 11:
                time.sleep(int(f.headers.get("Retry-After") or 10))
                continue
            try:
                return f.code, json.loads(raw), time.monotonic() - t0
            except ValueError:
                return f.code, raw[:300], time.monotonic() - t0


def private(service, method, path, body=None, headers=None, timeout=60):
    status, text = E.private_call(service, method, path, headers or {}, body, timeout)
    try:
        return status, (json.loads(text) if text else None)
    except ValueError:
        return status, text[:300]


def actor_headers(actor):
    return {"X-Caller-Service": "operations-console-bff", "X-Gateway-Actor-Subject": str(actor),
            "X-Gateway-Session-Id": "plops-t6-" + uuid.uuid4().hex}
