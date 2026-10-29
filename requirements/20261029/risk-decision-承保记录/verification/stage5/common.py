"""REQ-03 阶段 5 公共件：复用 V2 e2e 公共件 e2e_http.py（E2E_TARGET=local）与切片 1 验收脚本 onboarding_acceptance.py（运营审核步骤）。

不打印口令与令牌；本阶段新建的运营账号凭据写 ~/Code-MoR-backups/rq03-stage5/accounts.json（0600）。
"""
import json
import os
import subprocess
import sys
from pathlib import Path

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
WORKTREE = HERE.parents[4]
ACCEPTANCE = WORKTREE / "requirements/20261027/client-master-全球合规入驻与资格切片1/assets/acceptance"
V2_E2E = WORKTREE / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"
STATE = Path.home() / "Code-MoR-backups/rq03-stage5"
ROOT_SQL = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/3e08b044-fee7-45d0-ba22-072a21cafc69/scratchpad/rootsql.sh")
os.environ["E2E_TARGET"] = "local"
os.environ.setdefault("ONB_STATE", str(Path.home() / "Code-MoR-backups/onboarding-slice1-acceptance"))
sys.path.insert(0, str(V2_E2E))
sys.path.insert(0, str(ACCEPTANCE))
import e2e_http as E  # noqa: E402
import acceptance_flow as V2  # noqa: E402
import onboarding_acceptance as OA  # noqa: E402

OA.E, OA.V2 = E, V2
UW = "/risk-underwriting/v1"


def root_sql(statement, database=None, read_only=True):
    """root 执行（只读时开启只读事务），返回行字典列表。"""
    prefix = "SET SESSION TRANSACTION READ ONLY;\n" if read_only else ""
    out = subprocess.run([str(ROOT_SQL), "-B"] + ([database] if database else []), input=prefix + statement,
                         capture_output=True, text=True)
    if out.returncode != 0:
        raise RuntimeError(out.stderr.strip()[:300])
    lines = [line for line in out.stdout.splitlines() if line]
    head = lines[0].split("\t") if lines else []
    return [dict(zip(head, line.split("\t"))) for line in lines[1:]]


def admin():
    s = E.secrets()
    return OA.ops_login(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])


def accounts():
    target = STATE / "accounts.json"
    return json.loads(target.read_text()) if target.exists() else {}


def save_accounts(data):
    STATE.mkdir(parents=True, exist_ok=True)
    os.chmod(STATE, 0o700)
    target = STATE / "accounts.json"
    target.write_text(json.dumps(data, indent=1) + "\n")
    os.chmod(target, 0o600)
