"""阶段 7 mor-test 验收公共辅助：E2E_TARGET=test 下复用 local-e2e/e2e_http.py（运营入口 28080、只读 SQL、私网探针）。

不打印口令；写命令带 Idempotency-Key；结果按名称合并写入同目录 JSON。
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
sys.path.insert(0, str(ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))
import e2e_http as h  # noqa: E402

# Platform 候选镜像（第 2.4 节；探针只用其中的 JDK）
os.environ.setdefault("E2E_PROBE_IMAGE", "96da579bb45a")
h.PROBE_IMAGE = os.environ["E2E_PROBE_IMAGE"]


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def login():
    s = h.secrets()
    return h.call(h.OPERATIONS, "/api/operations/auth/v1/login", "POST",
                  {"loginIdentifier": s["E2E_ADMIN_EMAIL"], "password": s["E2E_ADMIN_PASSWORD"]})["accessToken"]


def ops(token, method, path, body=None, key=True):
    """运营入口 /api/operations + path，返回 (状态, JSON)；429 按 Retry-After 用同一幂等键重试。"""
    headers = {"Origin": h.OPERATIONS, "Accept": "application/json"}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        if key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else (b"" if method != "GET" else None)
    for attempt in range(15):
        request = urllib.request.Request(h.OPERATIONS + "/api/operations" + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
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


def sql(service, statement):
    """只读 SELECT（服务自有账号）。"""
    if not statement.lstrip().upper().startswith(("SELECT", "SHOW", "WITH")):
        raise SystemExit("阶段 7 只允许只读 SQL")
    return h.sql(service, statement)


def save(file_name, name, data):
    path = HERE / file_name
    results = json.loads(path.read_text()) if path.exists() else {}
    results[name] = data
    path.write_text(json.dumps(results, ensure_ascii=False, indent=1) + "\n")
    print(name, json.dumps(data, ensure_ascii=False)[:2000])
