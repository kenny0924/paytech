"""币种主数据阶段 7 mor-test 验收公共件（登录与请求方式同对账切片 1 的 recon7_common.py）。

入口：运营 http://localhost:28080（Nginx → Gateway 权限点 → Operations BFF → platform-support）；管理员取 test-environment/private/access.md。
库：只读核对经 docker exec mor-test-mysql-1（root），口令只经 MYSQL_PWD 传入。
快照证据：ledger / billing 管理端口只绑 127.0.0.1，用 CurrencySnapshotMetricsFetch.class（宿主机 JDK 25 编译）docker cp 到容器 /tmp/ccy7-probe
（tmpfs，容器重启即清空；清理脚本也会删除）后 docker exec 读 Prometheus 出站计数。
运行记录（币种行 ID、合成人员口令）写 CCY7_STATE（默认 ~/Code-MoR-backups/currency-spec-stage7，0700）；不打印口令或 Token。
"""
import json
import os
import re
import secrets as pysecrets
import subprocess
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
TEST_ENV = Path(os.environ.get("MOR_TEST_ENVIRONMENT_DIR", ROOT / "test-environment"))
STATE = Path(os.environ.get("CCY7_STATE", Path.home() / "Code-MoR-backups/currency-spec-stage7"))
STATE.mkdir(parents=True, exist_ok=True)
os.chmod(STATE, 0o700)
ORIGIN = "http://localhost:28080"
JAVA_HOME = Path("/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home")
CHINESE = re.compile(r"[一-鿿]")
SPECS = "/currency-admin/v1/currency-specs"
FINANCE_CURRENCIES = "/finance/currencies/v1"
TEST_CURRENCY = "XTS"  # ISO 4217 保留给测试的代码
PERM_READ = "900000000000125101"
# 父菜单「平台配置」562 的权限；没有它导航不显示其下任何子页。
PERM_PLATFORM_CONFIGURATION_READ = "900000000000000534"
SNAPSHOT_SERVICES = {"ledger-service": "19105", "billing-service": "19113"}
PROBE_DIR = "/tmp/ccy7-probe"


def read_env(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        if "=" in line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip().strip('"')
    return values


ENV = read_env(TEST_ENV / ".env.test")
results = []


def check(ac, name, ok, evidence=None):
    results.append((ac, name, bool(ok)))
    suffix = "" if ok or evidence is None else f" | {json.dumps(evidence, ensure_ascii=False, default=str)[:500]}"
    print(f"[{'通过' if ok else '失败'}] {ac} {name}{suffix}", flush=True)
    return bool(ok)


def raw(token, method, path, body=None, prefix="/api/operations", key=True):
    """请求 mor-test 运营入口，返回 (状态, 响应体)。写命令带 Idempotency-Key；429 按 Retry-After 重试。"""
    headers = {"Origin": ORIGIN, "Accept": "application/json"}
    if method != "GET":
        headers["Content-Type"] = "application/json"
        if key:
            headers["Idempotency-Key"] = str(uuid.uuid4())
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else (b"" if method != "GET" else None)
    for attempt in range(20):
        request = urllib.request.Request(ORIGIN + prefix + path, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                text = response.read().decode()
                return response.status, json.loads(text) if text else {}
        except urllib.error.HTTPError as failure:
            if failure.code == 429 and attempt < 19:
                time.sleep(int(failure.headers.get("Retry-After") or 5))
                continue
            text = failure.read().decode()
            try:
                return failure.code, json.loads(text)
            except ValueError:
                return failure.code, {"raw": text[:300]}


def rejected(status, body, http, detail_part=None):
    detail = body.get("detail") or ""
    return status == http and bool(CHINESE.search(detail)) and (detail_part is None or detail_part in detail)


def login(identifier, password):
    status, body = raw(None, "POST", "/auth/v1/login", {"loginIdentifier": identifier, "password": password}, key=False)
    if status != 200:
        raise SystemExit(f"登录失败 {status} {body.get('detail') or body.get('code')}")
    return body["accessToken"]


def admin_token():
    text = (TEST_ENV / "private/access.md").read_text()
    return login(re.search(r"账号：(\S+)", text).group(1), re.search(r"密码：(\S+)", text).group(1))


def synthetic_reader(admin, run, record):
    """经 IAM 新建只有币种读权限的合成角色与人员，首登改口令后返回 token。"""
    status, role = raw(admin, "POST", "/iam/v1/roles", {
        "roleCode": f"QA_CCY7_READ_{run}", "roleName": f"币种 mor-test 验收只读 {run}", "permissionIds": [PERM_READ, PERM_PLATFORM_CONFIGURATION_READ],
        "reason": "币种主数据阶段 7 mor-test 验收：合成只读角色（验收后删除）"})
    if not check("准备", f"IAM 新建合成只读角色（{status}）", status in (200, 201) and role.get("roleId"), role):
        raise SystemExit(1)
    record.setdefault("platformRoles", []).append(role["roleId"])
    email = f"ccy7-read-{run.lower()}@example.test"
    initial = "Ccy initial " + pysecrets.token_urlsafe(18) + "!"
    password = "Ccy seven " + pysecrets.token_urlsafe(18) + "!"
    status, created = raw(admin, "POST", "/iam/v1/accounts", {
        "email": email, "initialPassword": initial, "roleIds": [role["roleId"]],
        "reason": "币种主数据阶段 7 mor-test 验收：合成只读人员（验收后删除）"})
    if not check("准备", f"IAM 新建合成只读人员（{status}）", status in (200, 201) and created.get("accountId"), created):
        raise SystemExit(1)
    record.setdefault("platformAccounts", []).append(created["accountId"])
    first = login(email, initial)
    raw(first, "POST", "/auth/v1/password", {"currentPassword": initial, "newPassword": password}, key=False)
    record.setdefault("people", {})["READ"] = {"email": email, "password": password, "accountId": created["accountId"]}
    return login(email, password)


def sql(database, statement):
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={ENV['MOR_MYSQL_ROOT_PASSWORD']}", "mor-test-mysql-1", "mysql",
                             "-uroot", "-N", "-B", "--default-character-set=utf8mb4", database, "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


# ---------- ledger / billing 快照证据 ----------

def install_probe():
    """编译指标读取类到本机运行目录；读取时用旁路容器共享目标容器的网络命名空间，只读挂载，不写入运行中的容器。"""
    out = STATE / "probe"
    out.mkdir(exist_ok=True)
    subprocess.run([str(JAVA_HOME / "bin/javac"), "--release", "25", "-d", str(out), str(HERE / "CurrencySnapshotMetricsFetch.java")],
                   check=True, capture_output=True)


def remove_probe():
    """旁路容器以 --rm 运行，退出即删除；容器内不留任何文件。"""


def platform_fetch_count(service):
    """返回 {status: 次数}：该服务调用 GET /platform/v1/currency-specs 的累计次数（http_client_requests_seconds_count）。"""
    container = f"mor-test-{service}-1"
    image = subprocess.run(["docker", "inspect", "-f", "{{.Image}}", container], capture_output=True, text=True, check=True).stdout.strip()
    result = subprocess.run(["docker", "run", "--rm", "--network", f"container:{container}", "-v", f"{STATE / 'probe'}:/probe:ro",
                             "--entrypoint", "java", "--user", "10001:10001", image, "-cp", "/probe", "CurrencySnapshotMetricsFetch",
                             SNAPSHOT_SERVICES[service]], capture_output=True, text=True, timeout=60)
    if result.returncode != 0:
        raise RuntimeError(f"{service} 指标读取失败: {result.stderr.strip()[:200]}")
    counts = {}
    for line in result.stdout.splitlines():
        status = re.search(r'status="(\d+|[A-Z_]+)"', line)
        value = float(line.rsplit(" ", 1)[1])
        key = status.group(1) if status else "?"
        counts[key] = counts.get(key, 0) + value
    return counts


def refresh_failures_since(service, since_iso):
    """该服务自 since 起的快照刷新失败 WARN 行数。"""
    result = subprocess.run(["docker", "logs", "--since", since_iso, f"mor-test-{service}-1"], capture_output=True, text=True)
    text = result.stdout + result.stderr
    return sum(1 for line in text.splitlines() if "Currency snapshot refresh failed" in line)


def errors_since(service, since_iso):
    result = subprocess.run(["docker", "logs", "--since", since_iso, f"mor-test-{service}-1"], capture_output=True, text=True)
    text = result.stdout + result.stderr
    return [line[:300] for line in text.splitlines() if " ERROR " in line]


def health(service):
    return subprocess.run(["docker", "inspect", "-f", "{{.State.Health.Status}} {{.RestartCount}}", f"mor-test-{service}-1"],
                          capture_output=True, text=True).stdout.strip()
