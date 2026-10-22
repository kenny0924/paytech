"""文件多存储并存第二段阶段 7 mor-test 验收公共件。

复用 V2 本机 / mor-test 全链路脚本（requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e，E2E_TARGET=test）：
  · Client 站点 https://localhost:28443（test CA 校验，Refresh Cookie 只在内存 Cookie Jar），运营 http://localhost:28080；
  · 私网接口经接入 mor-test-backend 的一次性容器运行 PrivateHttpProbe（--rm，不写入任何运行中的容器）；
  · 管理员凭据读 test-environment/private/access.md，不打印。
本模块只补：不中断的 check()、运行记录（FS2_STATE，默认 ~/Code-MoR-backups/filestore-s2-stage7，目录 0700、文件 0600）、
只读 SQL（root，口令经 MYSQL_PWD）、容器健康与日志检查。
"""
import hashlib
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
E2E = ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
sys.path.insert(0, str(E2E))
import acceptance_flow as af  # noqa: E402  Client、register_and_login、start_application、fill_profile、PDF、presigned
import e2e_http as http  # noqa: E402  Operations、private_call、Failure、secrets

STATE = Path(os.environ.get("FS2_STATE", Path.home() / "Code-MoR-backups/filestore-s2-stage7"))
STATE.mkdir(parents=True, exist_ok=True)
os.chmod(STATE, 0o700)
TEST_ENV = ROOT / "test-environment"
SERVICES = ("platform-support-service", "operations-console-bff", "client-master-service", "client-console-bff",
            "client-console-web")
results = []


def now_iso():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def check(step, ok, evidence=""):
    results.append((step, bool(ok), evidence))
    print(("PASS " if ok else "FAIL ") + step + (" | " + str(evidence)[:400] if evidence else ""), flush=True)
    return bool(ok)


def save(name, record):
    path = STATE / name
    path.write_text(json.dumps(record, ensure_ascii=False, indent=1, default=str))
    os.chmod(path, 0o600)
    return path


def root_password():
    for line in (TEST_ENV / ".env.test").read_text().splitlines():
        if line.startswith("MOR_MYSQL_ROOT_PASSWORD="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("MOR_MYSQL_ROOT_PASSWORD 缺失")


def sql(statement, database="mysql"):
    result = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={root_password()}", "mor-test-mysql-1", "mysql", "-uroot",
                             "-N", "-B", "--default-character-set=utf8mb4", database, "-e", statement],
                            capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(result.stderr[:300])
    return [row.split("\t") for row in result.stdout.splitlines() if row]


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def client_call(client, path, method="GET", body=None, key=False):
    """Client 站点请求，返回 (status, body)；4xx/5xx 不抛异常，body 为 Problem。"""
    try:
        if key:
            return client.command(path, method, body)
        return client.call(path, method, body)
    except http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {"raw": failure.body[:200]}


def ops_call(ops, method, path, body=None, key=True):
    try:
        return 200, (ops.get(path) if method == "GET" else ops.send(method, path, body, key))
    except http.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {"raw": failure.body[:200]}


def platform_internal(method, path, caller=None, operator=None):
    """Platform 内部文件接口（私网，经一次性探针容器）。返回 (status, body dict)。"""
    headers = {"Accept": "application/json"}
    if caller:
        headers["X-Caller-Service"] = caller
    if operator:
        headers["X-Operator-Account-Id"] = operator
    status, text = http.private_call("platform", method, path, headers)
    try:
        return status, (json.loads(text) if text else {})
    except ValueError:
        return status, {"raw": text[:200]}


def health(service):
    return subprocess.run(["docker", "inspect", "-f", "{{.State.Health.Status}} {{.RestartCount}}", f"mor-test-{service}-1"],
                          capture_output=True, text=True).stdout.strip()


def errors_since(service, since_iso):
    result = subprocess.run(["docker", "logs", "--since", since_iso, f"mor-test-{service}-1"], capture_output=True, text=True)
    return [line[:300] for line in (result.stdout + result.stderr).splitlines() if " ERROR " in line]


def summary():
    passed = sum(1 for _, ok, _ in results if ok)
    print(f"\n合计 {passed}/{len(results)} 通过", flush=True)
    return passed == len(results)
