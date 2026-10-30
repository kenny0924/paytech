"""REQ-04 mor-test 发布脚本公共件（E2E_TARGET=test）：运营入口 http://localhost:28080（e2e_http + onboarding_acceptance 的会话与记录）、
私网接口经 e2e_http.private_call（一次性探针容器接入 mor-test-backend）、root 只读 SQL（docker exec mor-test-mysql-1，READ ONLY 会话）。
不打印口令与令牌；写操作脚本一律要求 --yes，且须已获用户对阶段 7 的明确授权。
"""
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.dont_write_bytecode = True
os.environ.setdefault("E2E_TARGET", "test")
# 私网探针镜像：e2e_http 缺省用 mor-test/platform-support-service:development，本机已无该 tag；改用 REQ-04 Platform 候选（= 现网，含 Java）。
os.environ.setdefault("E2E_PROBE_IMAGE", "sha256:af13e77a05c3d30bf9652523d52ba302dc59d2ee2b2221500ece647215560012")
HERE = Path(__file__).resolve().parent
RELEASE = HERE.parent
REQ = RELEASE.parent
MAIN = Path("/Users/kenny/Desktop/Workspace/Code-MoR")
TEST_ENV = MAIN / "test-environment"
ACCEPTANCE = MAIN / "requirements/20261027/client-master-全球合规入驻与资格切片1/assets/acceptance"
V2_E2E = MAIN / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
STATE = Path.home() / "Code-MoR-backups/rq04-stage7"
os.environ.setdefault("ONB_STATE", str(Path.home() / "Code-MoR-backups/onboarding-slice1-acceptance"))
sys.path.insert(0, str(V2_E2E))
sys.path.insert(0, str(ACCEPTANCE))
import e2e_http as E  # noqa: E402
import acceptance_flow as V2  # noqa: E402
import onboarding_acceptance as OA  # noqa: E402

OA.E, OA.V2 = E, V2
UW, LT = "/risk-underwriting/v1", "/risk-limit-tiers/v1"
EXECUTIONS = "/decision-releases/v1/executions"


def require_test():
    if E.TARGET != "test":
        raise SystemExit("只用于 mor-test（E2E_TARGET=test）")


def env_value(path, key):
    return next(line.split("=", 1)[1].strip() for line in Path(path).read_text().splitlines() if line.startswith(key + "="))


def root_ro(database, statement):
    """mor-test root 只读会话，返回行字典列表。"""
    pw = env_value(TEST_ENV / ".env.test", "MOR_MYSQL_ROOT_PASSWORD")
    out = subprocess.run(["docker", "exec", "-i", "-e", f"MYSQL_PWD={pw}", "mor-test-mysql-1", "mysql", "-uroot", "-B",
                          "--default-character-set=utf8mb4"] + ([database] if database else []),
                         input="SET SESSION TRANSACTION READ ONLY;\n" + statement, capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[:300])
    lines = [line for line in out.stdout.splitlines() if line]
    head = lines[0].split("\t") if lines else []
    return [dict(zip(head, line.split("\t"))) for line in lines[1:]]


def admin():
    s = E.secrets()
    return OA.ops_login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])


def send(ops, method, path, body=None, ok=(200, 201)):
    status, reply = ops.call(method, path, body, json_empty=body is None and method != "GET")
    if status not in ok:
        raise SystemExit(f"FAIL {method} {path} HTTP {status} {OA.problem(reply)}")
    return reply


def private(service, method, path, body=None, headers=None):
    """私网调用（不经 Gateway），返回 (status, JSON)。"""
    status, text = E.private_call(service, method, path, headers=headers, body=body)
    try:
        return status, (json.loads(text) if text and text.strip() else {})
    except ValueError:
        return status, {"raw": text[:300]}


def now():
    return datetime.now(timezone.utc)


def write_result(name, data):
    out = HERE / f"result-{name}-{now().strftime('%Y-%m-%dT%H%M%SZ')}.json"
    out.write_text(json.dumps(data, ensure_ascii=False, indent=1, default=str) + "\n")
    return out


def save_state(name, data):
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE, 0o700)
    target = STATE / name
    target.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n")
    os.chmod(target, 0o600)


def load_state(name):
    target = STATE / name
    return json.loads(target.read_text()) if target.exists() else {}
