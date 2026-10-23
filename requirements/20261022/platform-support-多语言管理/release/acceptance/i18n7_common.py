"""多语言管理阶段 7 mor-test 验收公共件（登录、请求、上传、匿名读取、只读 SQL；写法同文件公开桶 pb7_common.py）。

入口：运营 http://localhost:28080、客户 https://localhost:28443（Nginx → Gateway → BFF → platform-support）。
管理员取 test-environment/private/access.md，不打印。只读核对与清理 SQL 经 docker exec mor-test-mysql-1（root），口令只经 MYSQL_PWD。
对象直传与匿名读取：MinIO 地址 https://minio.mor-test.localhost:29400 在宿主机由 127.0.0.1:29400 承接，按原 Host 与 SNI 连接、
只信任 test-environment/private/test-ca.crt；其他主机一律拒绝。
运行记录写 I18N7_STATE（默认 ~/Code-MoR-backups/i18n-stage7，目录 0700、文件 0600），含合成人员口令，不含存储密钥与预签名地址。
"""
import hashlib
import http.client
import json
import os
import re
import secrets as pysecrets
import socket
import ssl
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
RELEASE = HERE.parent
REQ = RELEASE.parent
ROOT = HERE.parents[4]
TEST_ENV = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", ROOT / "test-environment"))
STATE = Path(os.environ.get("I18N7_STATE", Path.home() / "Code-MoR-backups/i18n-stage7"))
OPS = "http://localhost:28080"
CLIENT = "https://localhost:28443"
ADMIN = "/localization-admin/v1"
OPS_MANIFEST = "/api/operations/i18n/v1/manifest"
CLIENT_MANIFEST = "/api/client/i18n/v1/manifest"
MINIO_HOST, MINIO_PORT = "minio.mor-test.localhost", 29400
PUBLIC_BASE = f"https://{MINIO_HOST}:{MINIO_PORT}/mor-platform-public"
CA_FILE = TEST_ENV / "private/test-ca.crt"
CHINESE = re.compile(r"[一-鿿]")
SERVICES = ("platform-support-service", "operations-console-bff", "client-console-bff", "operations-console-web",
            "client-console-web")
JAVA_SERVICES = SERVICES[:3]
# 块 135 权限（I18N_VIEW / EDIT / PUBLISH）
PERM_VIEW, PERM_EDIT, PERM_PUBLISH = "900000000000135101", "900000000000135102", "900000000000135103"
# 首批语言包：两个应用的命名空间（与各 web 的 src/i18n/en/*.json 一致）
APP_NAMESPACES = {
    "operations-console": ["i18n-admin", "enum"],
    "client-console": ["common", "auth", "onboarding", "workspace", "team", "invitation", "developer", "enum"],
}
NAMESPACE_NAMES = {"i18n-admin": "多语言管理", "enum": "枚举展示名", "common": "通用", "auth": "登录与注册",
                   "onboarding": "入驻", "workspace": "工作台", "team": "团队", "invitation": "邀请",
                   "developer": "开发者"}
BUNDLE_DIR = RELEASE / "i18n-bundles"

results = []


def ensure_state():
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE, 0o700)


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


def env():
    return read_env(TEST_ENV / ".env.test")


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def check(ac, name, ok, evidence=None):
    results.append((ac, name, bool(ok)))
    suffix = "" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False, default=str)[:600]}"
    print(f"[{'通过' if ok else '失败'}] {ac} {name}{suffix}", flush=True)
    return bool(ok)


def unverified(ac, name, reason):
    """环境无法构造的场景：如实记录“未能验证”，不计入失败（同公开桶 AC-13 的做法）。"""
    results.append((ac, name + "（未能验证）", None))
    print(f"[未能验证] {ac} {name} | {reason}", flush=True)


def summary(title):
    passed = sum(1 for _, _, ok in results if ok is True)
    failed = [(ac, name) for ac, name, ok in results if ok is False]
    skipped = sum(1 for _, _, ok in results if ok is None)
    print(f"\n== {title}：通过 {passed}，失败 {len(failed)}，未能验证 {skipped}")
    for ac, name in failed:
        print(f"   失败：{ac} {name}")
    return 0 if not failed else 1


def save(name, record):
    ensure_state()
    path = STATE / name
    path.write_text(json.dumps(record, ensure_ascii=False, indent=1, default=str))
    os.chmod(path, 0o600)
    return path


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


# ---------- 入口请求 ----------

def raw(token, method, path, body=None, prefix="/api/operations", base=OPS, origin=None, key=True, typed_empty=False,
        headers=None, data=None):
    """请求 mor-test 入口，返回 (状态, JSON 或 {}, 原文, 响应头)。写命令带 Idempotency-Key 与 JSON Content-Type；429 按 Retry-After 重试。
    typed_empty=True：声明 JSON 内容类型的空正文（ops-web 对无正文写命令的调用方式，如完成上传）。data：直接给出原始请求体字节。"""
    hdrs = {"Origin": origin or base, "Accept": "application/json"}
    if method != "GET":
        if method != "DELETE":  # 块 135 的 DELETE 路由只放行 authorization、origin、idempotency-key
            hdrs["Content-Type"] = "application/json"
        if key:
            hdrs["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        hdrs["Authorization"] = "Bearer " + token
    hdrs.update(headers or {})
    if data is None:
        if method in ("GET", "DELETE") or typed_empty:
            data = None if method in ("GET", "DELETE") else b""
        else:
            data = json.dumps(body if body is not None else {}, ensure_ascii=False).encode()
    context = ssl.create_default_context(cafile=str(CA_FILE)) if base.startswith("https") else None
    for attempt in range(20):
        request = urllib.request.Request(base + prefix + path, data=data, headers=hdrs, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60, context=context) as response:
                text = response.read().decode()
                return response.status, (json.loads(text) if text else {}), text, dict(response.headers)
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 19:
                time.sleep(int(failure.headers.get("Retry-After") or 5))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text), text, dict(failure.headers)
            except ValueError:
                return failure.code, {"raw": text[:300]}, text, dict(failure.headers)


def ops(token, method, path, body=None, **kwargs):
    return raw(token, method, ADMIN + path, body, **kwargs)


def rejected(status, body, http_status, code=None):
    """业务拒绝：状态码一致、detail 或 errors 含中文；指定 code 时一并核对。"""
    detail = (body.get("detail") or "") + " ".join(e.get("message", "") for e in body.get("errors") or [])
    return status == http_status and bool(CHINESE.search(detail)) and (code is None or body.get("code") == code)


def login(identifier, password):
    status, body, _, _ = raw(None, "POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, key=False)
    if status != 200:
        raise SystemExit(f"登录失败 {status} {body.get('detail') or body.get('code')}")
    return body["accessToken"]


def admin_token():
    text = (TEST_ENV / "private/access.md").read_text()
    return login(re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1))


def synthetic_person(admin, run, record, label, permission_ids):
    """经 IAM 新建合成角色与人员（验收后由 stage7_cleanup.py 删除），首登改口令后返回 token。"""
    reason = f"多语言管理阶段 7 mor-test 验收：合成{label}（验收后删除）"
    status, role, _, _ = raw(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_I18N7_{label.upper()}_{run}", "roleName": f"多语言验收 {label} {run}",
        "permissionIds": list(permission_ids), "reason": reason})
    if not check("准备", f"IAM 新建合成角色 {label}（{status}）", status in (200, 201) and role.get("roleId"), role):
        raise SystemExit(1)
    record.setdefault("platformRoles", []).append(role["roleId"])
    save(f"run-{run}.json", record)
    email = f"i18n7-{label.lower()}-{run.lower()}@example.test"
    initial = "I18n initial " + pysecrets.token_urlsafe(18) + "!"
    password = "I18n seven " + pysecrets.token_urlsafe(18) + "!"
    status, created, _, _ = raw(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]], "reason": reason})
    if not check("准备", f"IAM 新建合成人员 {label}（{status}）", status in (200, 201) and created.get("accountId"), created):
        raise SystemExit(1)
    record.setdefault("platformAccounts", []).append(created["accountId"])
    record.setdefault("people", {})[label] = {"email": email, "password": password, "accountId": created["accountId"]}
    save(f"run-{run}.json", record)
    first = login(email, initial)
    raw(first, "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, key=False)
    return login(email, password)


# ---------- 对象存储 ----------

class _PinnedHttps(http.client.HTTPSConnection):
    """连接 127.0.0.1:29400，TLS SNI 与 Host 仍为 minio.mor-test.localhost（宿主机解析不到 *.localhost 子域）。"""

    def connect(self):
        sock = socket.create_connection(("127.0.0.1", MINIO_PORT), self.timeout)
        self.sock = self._context.wrap_socket(sock, server_hostname=MINIO_HOST)


def _connection(url):
    parts = urllib.parse.urlsplit(url)
    if parts.scheme != "https":
        raise SystemExit(f"拒绝非 HTTPS 存储地址: {parts.scheme}://{parts.hostname}")
    if parts.hostname == MINIO_HOST and (parts.port or 443) == MINIO_PORT:
        context = ssl.create_default_context(cafile=str(CA_FILE))
        return _PinnedHttps(MINIO_HOST, MINIO_PORT, context=context, timeout=60), parts
    if parts.hostname and parts.hostname.endswith(".amazonaws.com"):
        return http.client.HTTPSConnection(parts.hostname, parts.port or 443, timeout=60), parts
    raise SystemExit(f"拒绝未知存储主机: {parts.hostname}")


def storage_request(method, url, data=None, headers=None):
    """返回 (状态, 正文字节, 响应头 dict 小写键)。"""
    connection, parts = _connection(url)
    target = parts.path + (("?" + parts.query) if parts.query else "")
    try:
        connection.request(method, target, body=data, headers=headers or {})
        response = connection.getresponse()
        body = response.read()
        return response.status, body, {k.lower(): v for k, v in response.getheaders()}
    finally:
        connection.close()


def public_get(url, origin=None):
    """不带凭据读取公开地址（可带 Origin 观察 CORS 头）。"""
    return storage_request("GET", url, headers={"Origin": origin} if origin else {})


def upload_json(token, purpose, file_name, data):
    """导入文件上传三步：开始（BFF 201 返回 PUT 授权）→ 按授权直传 → 完成（无正文）。返回完成回执。"""
    status, started, _, _ = ops(token, "POST", "/uploads", {"purpose": purpose, "fileName": file_name,
                                                            "sizeBytes": str(len(data))})
    if status != 201:
        raise RuntimeError(f"开始上传失败 {status} {started}")
    grant = started["authorization"]
    put_status, put_body, _ = storage_request(grant["method"], grant["url"], data=data, headers=dict(grant.get("headers") or {}))
    if put_status not in (200, 201):
        raise RuntimeError(f"直传失败 {put_status} {put_body[:200]!r}")
    status, completed, _, _ = ops(token, "POST", f"/uploads/{started['uploadId']}/completion", typed_empty=True)
    if status != 200:
        raise RuntimeError(f"完成上传失败 {status} {completed}")
    return completed


def with_file_ready(call, attempts=12):
    """导入调用遇 409 I18N_IMPORT_FILE_NOT_READY 时按 1、2、3、4、5…秒重试（同 ops-web withScanRetry）。返回最后一次结果与是否遇到过 409。"""
    seen_not_ready = False
    for attempt in range(attempts):
        status, body, text, headers = call()
        if status == 409 and body.get("code") == "I18N_IMPORT_FILE_NOT_READY":
            seen_not_ready = True
            time.sleep(min(attempt + 1, 5))
            continue
        return status, body, seen_not_ready
    return status, body, seen_not_ready


def wait_release(token, app, release_no, timeout=300):
    """轮询发布详情直到 READY / FAILED 或超时；返回最后一次详情。"""
    deadline = time.time() + timeout
    detail = {}
    while time.time() < deadline:
        status, detail, _, _ = ops(token, "GET", f"/applications/{app}/releases/{release_no}")
        if status == 200 and detail.get("release", {}).get("status") in ("READY", "FAILED"):
            return detail
        time.sleep(2)
    return detail


# ---------- 数据库与容器（只读） ----------

def sql(database, statement):
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={env()['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql",
                             "-uroot", "-N", "-B", "--default-character-set=utf8mb4", database, "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def container(service, fmt):
    return subprocess.run(["docker", "inspect", f"mor-test-{service}-1", "--format", fmt], capture_output=True, text=True).stdout.strip()


def compose_images(path):
    """读覆盖文件中的 image: sha256:…（不依赖 PyYAML）。"""
    images, current = {}, None
    for line in Path(path).read_text().splitlines():
        match = re.match(r"^  ([a-z0-9-]+):\s*$", line)
        if match:
            current = match.group(1)
        match = re.match(r"^    image:\s*(sha256:[0-9a-f]{64})", line)
        if match and current:
            images[current] = match.group(1)
    return images


def errors_since(service, since_iso):
    output = subprocess.run(["docker", "logs", "--since", since_iso, f"mor-test-{service}-1"], capture_output=True, text=True)
    return [line for line in (output.stdout + output.stderr).splitlines() if " ERROR " in line]
