"""文件多存储并存阶段 7 mor-test 验收公共件（登录与请求方式同币种发布的 ccy7_common.py）。

入口：运营 http://localhost:28080（Nginx → Gateway 权限点 → Operations BFF → platform-support）；管理员取 test-environment/private/access.md。
库：只读核对与清理经 docker exec mor-test-mysql-1（root），口令只经 MYSQL_PWD 传入。
存储账号：test-environment/.env.test 的 MOR_MINIO_PLATFORM_ACCESS_KEY / SECRET_KEY，只在内存中使用，不打印、不写入任何文件。
下载：浏览器地址 https://minio.mor-test.localhost:29400 在宿主机由 127.0.0.1:29400 承接；按原 Host 与 SNI 连接，只信任 test-ca.crt。
内部接口：Platform 只在私网监听，用旁路容器（--network container:<platform>，只读挂载，--rm）运行宿主机 JDK 25 编译的探针，
不向运行中的容器写入任何文件。
运行记录写 FS7_STATE（默认 ~/Code-MoR-backups/filestore-stage7，0700，文件 0600）；不含口令原文以外的密钥。
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
ROOT = HERE.parents[4]
TEST_ENV = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", ROOT / "test-environment"))
STATE = Path(os.environ.get("FS7_STATE", Path.home() / "Code-MoR-backups/filestore-stage7"))
STATE.mkdir(parents=True, exist_ok=True)
os.chmod(STATE, 0o700)
ORIGIN = "http://localhost:28080"
JAVA_HOME = Path("/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home")
CHINESE = re.compile(r"[一-鿿]")
ADMIN = "/storage-admin/v1"
INSTANCE = "MINIO"
BUCKET = "mor-platform-files"
ENDPOINT = "https://minio.mor-test.localhost:29400"
MINIO_HOST, MINIO_PORT = "minio.mor-test.localhost", 29400
REGION = "us-east-1"
SCAN_POLICY = "CLIENT_ONBOARDING_FORMAT"
UPLOAD_SECONDS = 900  # 与旧 files.onboarding.upload-seconds 默认值相同
DEFAULT_ROUTE = "*"
CA_FILE = TEST_ENV / "private/test-ca.crt"
PERM_READ = "900000000000130101"
# 父菜单「平台配置」562 的权限；没有它导航不显示其下任何子页（币种发布 2026-10-21 的发现）。
PERM_PLATFORM_CONFIGURATION_READ = "900000000000000534"
PLATFORM = "platform-support-service"
SERVICES = ("platform-support-service", "operations-console-bff", "operations-console-web")


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


ENV = read_env(TEST_ENV / ".env.test")
results = []


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def storage_secret():
    """mor-test 现有 MinIO Platform 账号（compose 原 FILES_ONBOARDING_MINIO_* 的来源）；只返回内存值。"""
    return ENV["MOR_MINIO_PLATFORM_ACCESS_KEY"], ENV["MOR_MINIO_PLATFORM_SECRET_KEY"]


def check(ac, name, ok, evidence=None):
    results.append((ac, name, bool(ok)))
    suffix = "" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False, default=str)[:500]}"
    print(f"[{'通过' if ok else '失败'}] {ac} {name}{suffix}", flush=True)
    return bool(ok)


def save(name, record):
    path = STATE / name
    path.write_text(json.dumps(record, ensure_ascii=False, indent=1, default=str))
    os.chmod(path, 0o600)
    return path


def raw(token, method, path, body=None, prefix="/api/operations", key=True, empty_body=False, typed_empty=False):
    """请求 mor-test 运营入口，返回 (状态, 响应体, 原文)。写命令带 Idempotency-Key；429 按 Retry-After 重试。
    empty_body=True：无 Content-Type 的空正文（桶检查路由正文上限 0、不登记 content-type）；
    typed_empty=True：声明 JSON 内容类型的空正文（同 ops-web 审核下载的调用方式）。"""
    headers = {"Origin": ORIGIN, "Accept": "application/json"}
    if method != "GET" and not empty_body:
        headers["Content-Type"] = "application/json"
    if method != "GET" and key:
        headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    if empty_body or typed_empty or method == "GET":
        data = None if method == "GET" else b""
    else:
        data = json.dumps(body if body is not None else {}).encode()
    for attempt in range(20):
        request = urllib.request.Request(ORIGIN + prefix + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                text = response.read().decode()
                return response.status, (json.loads(text) if text else {}), text
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 19:
                time.sleep(int(failure.headers.get("Retry-After") or 5))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text), text
            except ValueError:
                return failure.code, {"raw": text[:300]}, text


def rejected(status, body, http, detail_part=None):
    detail = body.get("detail") or ""
    return status == http and bool(CHINESE.search(detail)) and (detail_part is None or detail_part in detail)


def login(identifier, password):
    status, body, _ = raw(None, "POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, key=False)
    if status != 200:
        raise SystemExit(f"登录失败 {status} {body.get('detail') or body.get('code')}")
    return body["accessToken"]


def admin_token():
    text = (TEST_ENV / "private/access.md").read_text()
    return login(re.search(r"账号：(\S+)", text).group(1), re.search(r"密码：(\S+)", text).group(1))


def synthetic_reader(admin, run, record):
    """经 IAM 新建只有存储管理查看权限（及父菜单读权限）的合成角色与人员，首登改口令后返回 token。"""
    status, role, _ = raw(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_FS7_READ_{run}", "roleName": f"存储管理 mor-test 验收只读 {run}",
        "permissionIds": [PERM_READ, PERM_PLATFORM_CONFIGURATION_READ],
        "reason": "文件多存储并存阶段 7 mor-test 验收：合成只读角色（验收后删除）"})
    if not check("准备", f"IAM 新建合成只读角色（{status}）", status in (200, 201) and role.get("roleId"), role):
        raise SystemExit(1)
    record.setdefault("platformRoles", []).append(role["roleId"])
    save(f"run-{run}.json", record)
    email = f"fs7-read-{run.lower()}@example.test"
    initial = "Fs initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Fs seven " + pysecrets.token_urlsafe(18) + "!"
    status, created, _ = raw(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
        "reason": "文件多存储并存阶段 7 mor-test 验收：合成只读人员（验收后删除）"})
    if not check("准备", f"IAM 新建合成只读人员（{status}）", status in (200, 201) and created.get("accountId"), created):
        raise SystemExit(1)
    record.setdefault("platformAccounts", []).append(created["accountId"])
    record.setdefault("people", {})["READ"] = {"email": email, "password": password, "accountId": created["accountId"]}
    save(f"run-{run}.json", record)
    first = login(email, initial)
    raw(first, "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, key=False)
    return login(email, password)


def sql(database, statement):
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={ENV['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql",
                             "-uroot", "-N", "-B", "--default-character-set=utf8mb4", database, "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def instances(token):
    status, body, text = raw(token, "GET", ADMIN + "/instances")
    return status, {item.get("instanceCode"): item for item in body.get("items") or []}, text


def routes(token):
    status, body, text = raw(token, "GET", ADMIN + "/routes")
    return status, {item.get("sourceSystem"): item for item in body.get("items") or []}, text


def bucket_of(instance, name):
    return next((b for b in (instance or {}).get("buckets") or [] if b.get("bucketName") == name), None)


def path_segment(value):
    return urllib.parse.quote(value, safe="*")


# ---------- 下载与内部探针 ----------

class _PinnedHttpsConnection(http.client.HTTPSConnection):
    """连接 127.0.0.1，但 SNI 与 Host 使用预签名地址里的主机名（宿主机解析不到 *.localhost）。"""

    def connect(self):
        sock = socket.create_connection(("127.0.0.1", self.port), self.timeout)
        self.sock = self._context.wrap_socket(sock, server_hostname=self.host)


def fetch_presigned(url):
    """按预签名地址取对象，返回 (HTTP 状态, 字节)。只接受 mor-test MinIO 地址。"""
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https" or parsed.hostname != MINIO_HOST or parsed.port != MINIO_PORT:
        raise ValueError(f"下载地址不是 mor-test MinIO: {parsed.scheme}://{parsed.hostname}:{parsed.port}")
    context = ssl.create_default_context(cafile=str(CA_FILE))
    connection = _PinnedHttpsConnection(parsed.hostname, parsed.port, context=context, timeout=30)
    target = parsed.path + ("?" + parsed.query if parsed.query else "")
    connection.request("GET", target, headers={"Host": parsed.netloc})
    response = connection.getresponse()
    data = response.read()
    connection.close()
    return response.status, data


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


def compile_probe():
    out = STATE / "probe"
    out.mkdir(exist_ok=True)
    subprocess.run([str(JAVA_HOME / "bin/javac"), "--release", "25", "-d", str(out), str(HERE / "InternalFileProbe.java")],
                   check=True, capture_output=True)
    return out


def internal_probe():
    """返回 {场景: HTTP 状态}：内部文件接口在私网内、没有内部调用 Token 时的响应。"""
    out = compile_probe()
    container = f"mor-test-{PLATFORM}-1"
    image = subprocess.run(["docker", "inspect", "-f", "{{.Image}}", container], capture_output=True, text=True, check=True).stdout.strip()
    result = subprocess.run(["docker", "run", "--rm", "--network", f"container:{container}", "-v", f"{out}:/probe:ro",
                             "--entrypoint", "java", "--user", "10001:10001", image, "-cp", "/probe", "InternalFileProbe",
                             "http://127.0.0.1:18106/platform-support"], capture_output=True, text=True, timeout=90)
    if result.returncode != 0:
        raise RuntimeError(f"内部接口探针失败: {result.stderr.strip()[:300]}")
    return dict(line.split(" ", 1) for line in result.stdout.splitlines() if " " in line)


# ---------- 容器状态 ----------

def logs_since(service, since_iso):
    result = subprocess.run(["docker", "logs", "--since", since_iso, f"mor-test-{service}-1"], capture_output=True, text=True)
    return result.stdout + result.stderr


def errors_since(service, since_iso):
    return [line[:300] for line in logs_since(service, since_iso).splitlines() if " ERROR " in line]


def health(service):
    return subprocess.run(["docker", "inspect", "-f", "{{.State.Health.Status}} {{.RestartCount}}", f"mor-test-{service}-1"],
                          capture_output=True, text=True).stdout.strip()


def summary():
    passed = sum(1 for _, _, ok in results if ok)
    print(f"\n合计 {passed}/{len(results)} 通过", flush=True)
    return passed == len(results)
