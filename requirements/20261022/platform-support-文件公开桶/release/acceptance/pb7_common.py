"""文件公开桶阶段 7 mor-test 验收公共件（登录、请求与清理方式同文件多存储并存的 fs7_common.py）。

入口：运营 http://localhost:28080（Nginx → Gateway 权限点 → Operations BFF → platform-support）；管理员取 test-environment/private/access.md，不打印。
库：只读核对与清理经 docker exec mor-test-mysql-1（root），口令只经 MYSQL_PWD 传入。
内部文件接口：Platform 只在私网监听，经接入 mor-test-backend 的一次性容器（--rm，只读挂载）运行宿主机 JDK 25 编译的 PrivateHttpProbe，
带 X-Caller-Service（mor-test internal-auth 为 DUAL），不向运行中的容器写入任何文件。
对象直传与匿名读取：MinIO 地址 https://minio.mor-test.localhost:29400 在宿主机由 127.0.0.1:29400 承接，按原 Host 与 SNI 连接、只信任 test-ca.crt；
AWS 地址（*.amazonaws.com）直连、系统信任库。其他主机一律拒绝。
运行记录写 PB7_STATE（默认 ~/Code-MoR-backups/pubbucket-stage7，0700，文件 0600），含合成人员口令，不含存储密钥与预签名地址。
"""
import hashlib
import http.client
import json
import os
import re
import secrets as pysecrets
import socket
import ssl
import struct
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib
from datetime import datetime, timedelta, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
TEST_ENV = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", ROOT / "test-environment"))
STATE = Path(os.environ.get("PB7_STATE", Path.home() / "Code-MoR-backups/pubbucket-stage7"))
STATE.mkdir(parents=True, exist_ok=True)
os.chmod(STATE, 0o700)
ORIGIN = "http://localhost:28080"
JAVA_HOME = Path("/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home")
CHINESE = re.compile(r"[一-鿿]")
ADMIN = "/storage-admin/v1"
INTERNAL = "http://platform-support-service:18106/platform-support/internal/files/v1"
MINIO_HOST, MINIO_PORT = "minio.mor-test.localhost", 29400
CA_FILE = TEST_ENV / "private/test-ca.crt"
PLATFORM = "platform-support-service"
SERVICES = ("card-service", "client-master-service", "client-console-bff", "operations-console-bff", "platform-support-service",
            "operations-console-web")
JAVA_SERVICES = SERVICES[:-1]
# 权限：存储管理查看 / 维护（块 130）、文件记录查看 / 下载（块 134）、父菜单「平台配置」562 的读权限（没有它导航不显示子页）。
PERM_STORAGE_READ = "900000000000130101"
PERM_FILE_RECORD_READ = "900000000000134101"
PERM_FILE_RECORD_DOWNLOAD = "900000000000134102"
PERM_PLATFORM_CONFIGURATION_READ = "900000000000000534"
# 存量写入规则与桶（阶段 6 预检 D09—D13）。
PRIVATE_BUCKETS = (("MINIO", "mor-platform-files"), ("S3_TEST", "mor-test-platform-files-k7q2"))
EXISTING_ROUTES = {("*", "PRIVATE"): ("MINIO", "mor-platform-files", "CLIENT_ONBOARDING_FORMAT"),
                   ("client-console-bff", "PRIVATE"): ("S3_TEST", "mor-test-platform-files-k7q2", "CLIENT_ONBOARDING_FORMAT"),
                   ("operations-console-bff", "PRIVATE"): ("MINIO", "mor-platform-files", "CARD_BIN_CSV")}
# 合成来源系统：只用于验收的内部调用身份，写入规则与文件都按它识别和清理。
QA_SOURCE = "pubbucket-qa"
QA_NO_ROUTE_SOURCE = "pubbucket-qa-noroute"
QA_POLICY_SOURCE = "pubbucket-qa-policy"
QA_REASON = "PUBBUCKET_ACCEPTANCE"
PDF = (b"%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
       b"3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n")


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


def iso(moment):
    return moment.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


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


def png(rgb):
    """生成 1×1 的合法 PNG（不同颜色得到不同字节），供格式检查器识别为 image/png。"""
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw_row = b"\x00" + bytes(rgb)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw_row)) + chunk(b"IEND", b""))


def sha256_hex(data):
    return hashlib.sha256(data).hexdigest()


# ---------- 运营入口 ----------

def raw(token, method, path, body=None, prefix="/api/operations", key=True, typed_empty=False):
    """请求 mor-test 运营入口，返回 (状态, 响应体, 原文)。写命令带 Idempotency-Key 与 JSON Content-Type；429 按 Retry-After 重试。
    typed_empty=True：声明 JSON 内容类型的空正文（ops-web 对无正文写命令的调用方式，如桶检查、文件下载）。"""
    headers = {"Origin": ORIGIN, "Accept": "application/json"}
    if method != "GET":
        headers["Content-Type"] = "application/json"
    if method != "GET" and key:
        headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    if typed_empty or method == "GET":
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


def rejected(status, body, http_status, detail_part=None):
    detail = body.get("detail") or ""
    return status == http_status and bool(CHINESE.search(detail)) and (detail_part is None or detail_part in detail)


def login(identifier, password):
    status, body, _ = raw(None, "POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, key=False)
    if status != 200:
        raise SystemExit(f"登录失败 {status} {body.get('detail') or body.get('code')}")
    return body["accessToken"]


def admin_token():
    text = (TEST_ENV / "private/access.md").read_text()
    return login(re.search(r"账号[:：]\s*(\S+)", text).group(1), re.search(r"密码[:：]\s*(\S+)", text).group(1))


def synthetic_person(admin, run, record, label, permission_ids):
    """经 IAM 新建合成角色与人员（验收后由 cleanup 删除），首登改口令后返回 token。"""
    reason = f"文件公开桶阶段 7 mor-test 验收：合成{label}（验收后删除）"
    status, role, _ = raw(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_PB7_{label.upper()}_{run}", "roleName": f"文件公开桶验收 {label} {run}",
        "permissionIds": list(permission_ids), "reason": reason})
    if not check("准备", f"IAM 新建合成角色 {label}（{status}）", status in (200, 201) and role.get("roleId"), role):
        raise SystemExit(1)
    record.setdefault("platformRoles", []).append(role["roleId"])
    save(f"run-{run}.json", record)
    email = f"pb7-{label.lower()}-{run.lower()}@example.test"
    initial = "Pb initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Pb seven " + pysecrets.token_urlsafe(18) + "!"
    status, created, _ = raw(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]], "reason": reason})
    if not check("准备", f"IAM 新建合成人员 {label}（{status}）", status in (200, 201) and created.get("accountId"), created):
        raise SystemExit(1)
    record.setdefault("platformAccounts", []).append(created["accountId"])
    record.setdefault("people", {})[label] = {"email": email, "password": password, "accountId": created["accountId"]}
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
    return status, {(item.get("sourceSystem"), item.get("accessLevel")): item for item in body.get("items") or []}, text


def bucket_of(instance, name):
    return next((b for b in (instance or {}).get("buckets") or [] if b.get("bucketName") == name), None)


def segment(value):
    return urllib.parse.quote(value, safe="*")


# ---------- 内部文件接口（私网探针） ----------

_probe_dir = None


def _probe_classes():
    global _probe_dir
    if _probe_dir is None:
        out = STATE / "probe"
        out.mkdir(exist_ok=True)
        subprocess.run([str(JAVA_HOME / "bin/javac"), "--release", "25", "-d", str(out), str(HERE / "PrivateHttpProbe.java")],
                       check=True, capture_output=True)
        os.chmod(out, 0o755)
        _probe_dir = out
    return _probe_dir


def probe_image():
    """探针用 Platform 当前运行镜像（含 JRE 25）；可用 PB7_PROBE_IMAGE 覆盖。"""
    return os.environ.get("PB7_PROBE_IMAGE") or subprocess.run(
        ["docker", "inspect", "-f", "{{.Image}}", f"mor-test-{PLATFORM}-1"], capture_output=True, text=True, check=True).stdout.strip()


def internal(method, path, caller, body=None, operator=None, query=None):
    """调用 /internal/files/v1（不经 Gateway），返回 (状态, 响应 dict)。caller 为 X-Caller-Service（合成来源系统或被验收的消费方）。"""
    headers = {"Accept": "application/json"}
    if caller:
        headers["X-Caller-Service"] = caller
    if operator:
        headers["X-Operator-Account-Id"] = operator
    data = ""
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body)
    url = INTERNAL + path + ("?" + urllib.parse.urlencode(query) if query else "")
    payload = "\n".join([method, url] + [f"{k}: {v}" for k, v in headers.items()]) + "\n\n" + data
    result = subprocess.run(["docker", "run", "--rm", "-i", "--network", "mor-test-backend", "--entrypoint", "java",
                             "--user", "10001:10001", "-v", f"{_probe_classes()}:/probe:ro", probe_image(),
                             "-cp", "/probe", "PrivateHttpProbe"], input=payload, capture_output=True, text=True, timeout=90)
    if result.returncode != 0:
        raise RuntimeError(f"私网探针失败（{method} {path}）：{result.stderr.strip()[:300]}")
    status, _, text = result.stdout.partition("\n")
    try:
        return int(status), (json.loads(text) if text.strip() else {})
    except ValueError:
        return int(status), {"raw": text[:300]}


def get_file(caller, file_id):
    return internal("GET", f"/files/{file_id}", caller)


def wait_file(caller, file_id, predicate, seconds=90):
    """轮询文件元数据直到 predicate 成立（扫描由补偿执行器异步完成），返回最后一次 (状态, 元数据)。"""
    deadline = time.time() + seconds
    status, body = get_file(caller, file_id)
    while not (status == 200 and predicate(body)) and time.time() < deadline:
        time.sleep(2)
        status, body = get_file(caller, file_id)
    return status, body


# ---------- 对象直传与读取 ----------

class _PinnedHttpsConnection(http.client.HTTPSConnection):
    """连接 127.0.0.1，但 SNI 与 Host 使用地址里的主机名（宿主机解析不到 *.localhost）。"""

    def connect(self):
        sock = socket.create_connection(("127.0.0.1", self.port), self.timeout)
        self.sock = self._context.wrap_socket(sock, server_hostname=self.host)


def object_request(method, url, headers=None, data=None):
    """对 MinIO / AWS 地址发请求（预签名 PUT / GET 或匿名 GET），返回 (HTTP 状态, 字节)。只接受这两类主机。"""
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "https":
        raise ValueError(f"对象地址不是 https: {parsed.scheme}://{parsed.hostname}")
    target = parsed.path + ("?" + parsed.query if parsed.query else "")
    headers = dict(headers or {})
    if parsed.hostname == MINIO_HOST and parsed.port == MINIO_PORT:
        context = ssl.create_default_context(cafile=str(CA_FILE))
        connection = _PinnedHttpsConnection(parsed.hostname, parsed.port, context=context, timeout=30)
    elif parsed.hostname and parsed.hostname.endswith(".amazonaws.com"):
        connection = http.client.HTTPSConnection(parsed.hostname, parsed.port or 443, context=ssl.create_default_context(),
                                                 timeout=30)
    else:
        raise ValueError(f"对象地址主机不在允许范围: {parsed.hostname}")
    headers.setdefault("Host", parsed.netloc)
    connection.request(method, target, body=data, headers=headers)
    response = connection.getresponse()
    payload = response.read()
    connection.close()
    return response.status, payload


def put_upload(grant, data):
    """按开始上传 / 替换返回的授权（method、url、headers）直传字节，返回 HTTP 状态。"""
    authorization = grant.get("authorization") or {}
    return object_request(authorization.get("method", "PUT"), authorization["url"], authorization.get("headers") or {}, data)[0]


# ---------- 容器状态 ----------

def logs_since(service, since_iso):
    result = subprocess.run(["docker", "logs", "--since", since_iso, f"mor-test-{service}-1"], capture_output=True, text=True)
    return result.stdout + result.stderr


def errors_since(service, since_iso):
    return [line[:300] for line in logs_since(service, since_iso).splitlines() if " ERROR " in line]


def health(service):
    return subprocess.run(["docker", "inspect", "-f", "{{.State.Health.Status}} {{.RestartCount}}", f"mor-test-{service}-1"],
                          capture_output=True, text=True).stdout.strip()


def created_window(minutes_before=60, minutes_after=60):
    now = datetime.now(timezone.utc)
    return iso(now - timedelta(minutes=minutes_before)), iso(now + timedelta(minutes=minutes_after))


def summary():
    passed = sum(1 for _, _, ok in results if ok)
    print(f"\n合计 {passed}/{len(results)} 通过", flush=True)
    return passed == len(results)
