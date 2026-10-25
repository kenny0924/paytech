"""阶段 5 本机联调公共工具：私网直连、运营入口（Gateway 18444）、只读 SQL、证据记录。
不打印口令与令牌；root 口令与运营管理员口令只从主仓库 local-environment/.env.local、local-environment/e2e/secrets.env 读入。
"""
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
LOCAL = Path(os.environ.get("E2E_LOCAL_ENV", "/Users/kenny/Desktop/Workspace/Code-MoR/local-environment"))
RESULT = HERE / "stage5-result.json"
EVIDENCE = HERE / "evidence"
OPERATIONS_ORIGIN = "https://127.0.0.1:18444"

PRIVATE = {
    "client-master": "http://127.0.0.1:18101",
    "commercial": "http://127.0.0.1:18108",
    "compliance": "http://127.0.0.1:18112",
    "acquiring": "http://127.0.0.1:18102/commerce",
    "ops-bff": "http://127.0.0.1:18201",
}
_TLS = ssl._create_unverified_context()  # 本机 nginx 自签证书


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


def _secrets():
    values = read_env(LOCAL / ".env.local")
    values.update(read_env(LOCAL / "e2e/secrets.env"))
    return values


def http(method, url, headers=None, body=None, timeout=40):
    """返回 (status, json 或 {"raw": 文本})。"""
    headers = dict(headers or {})
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers.setdefault("Content-Type", "application/json")
    elif method in ("POST", "PUT"):
        data = b""
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout, context=_TLS) as response:
            return response.status, _parse(response.read().decode())
    except urllib.error.HTTPError as failure:
        return failure.code, _parse(failure.read().decode())


def _parse(text):
    if not text:
        return {}
    try:
        return json.loads(text)
    except ValueError:
        return {"raw": text[:500]}


def private(service, method, path, headers=None, body=None):
    return http(method, PRIVATE[service] + path, headers, body)


def actor_headers(actor_id, session="relsnap-stage5-session"):
    """Gateway 转发给 BFF / Owner 的可信操作者头（直连时手工补上）。"""
    return {"X-Gateway-Actor-Subject": str(actor_id), "X-Gateway-Session-Id": session, "X-Request-Id": "req-relsnap-" + uuid.uuid4().hex[:12]}


class Operations:
    """运营会话：经 nginx 18444 → Gateway → ops-bff，携带 Bearer 与 Origin。"""

    def __init__(self):
        s = _secrets()
        status, body = self._raw("POST", "/auth/v1/login", {"loginIdentifier": s["E2E_ADMIN_EMAIL"], "password": s["E2E_ADMIN_PASSWORD"]}, key=False)
        if status != 200:
            raise SystemExit(f"运营登录失败 HTTP {status} {body.get('code') or body.get('detail')}")
        self.token = body["accessToken"]

    def _raw(self, method, path, body=None, key=True, token=None):
        headers = {"Origin": OPERATIONS_ORIGIN, "Accept": "application/json", "X-Request-Id": "req-relsnap-" + uuid.uuid4().hex[:12]}
        if method != "GET" and key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
        if token:
            headers["Authorization"] = "Bearer " + token
        for attempt in range(10):
            status, payload = http(method, OPERATIONS_ORIGIN + "/api/operations" + path, headers, body if method != "GET" else None)
            if status != 429 or attempt == 9:
                return status, payload
            time.sleep(6)

    def call(self, method, path, body=None):
        return self._raw(method, path, body, token=self.token)


def sql(database, statement):
    """root 只读查询，返回行（制表符分列）。"""
    env = dict(os.environ, MYSQL_PWD=read_env(LOCAL / ".env.local")["MOR_MYSQL_ROOT_PASSWORD"])
    out = subprocess.run(["mysql", "-h127.0.0.1", "-P13306", "-uroot", "--default-character-set=utf8mb4", "-N", "-B", database,
                          "-e", statement], capture_output=True, text=True, env=env, check=True).stdout
    return [line.split("\t") for line in out.splitlines()]


def load():
    return json.loads(RESULT.read_text()) if RESULT.exists() else {"checks": []}


def record(name, ok, evidence=None, note=None):
    data = load()
    data["checks"] = [c for c in data["checks"] if c["name"] != name]
    entry = {"name": name, "ok": bool(ok), "at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    if note:
        entry["note"] = note
    if evidence is not None:
        EVIDENCE.mkdir(exist_ok=True)
        file = EVIDENCE / (name.replace(" ", "_").replace("/", "_") + ".json")
        # Checkout Public Token 是持有者凭证，不落证据文件。
        file.write_text(re.sub(r"chk_[A-Za-z0-9]+", "chk_<redacted>", json.dumps(evidence, ensure_ascii=False, indent=2)))
        entry["evidence"] = str(file.relative_to(HERE))
    data["checks"].append(entry)
    RESULT.write_text(json.dumps(data, ensure_ascii=False, indent=2))
    print(("PASS " if ok else "FAIL ") + name + (f" — {note}" if note else ""))
    return ok
