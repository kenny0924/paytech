"""REQ-03 阶段 5（用户 2026-10-29 选 A）：给本机 uw / lead / ro 测试角色加 RISK_DECISION_CONFIG_READ（风控菜单目录 OPERATIONS_RISK_MANAGEMENT 的权限）。
经本机运营入口（Nginx 18444 → Gateway → ops-bff → Platform IAM）PUT /iam/v1/roles/{roleId}，记录前后权限集合；回滚：python3 -I stage5_role_directory.py rollback。
凭据：管理员读 local-environment/e2e/secrets.env，角色 ID 读 ~/Code-MoR-backups/rq03-stage5/accounts.json；不打印。
"""
import json
import site
import sys
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

DIRECTORY_PERMISSION = "900000000000000536"  # RISK_DECISION_CONFIG_READ
ROLLBACK = len(sys.argv) > 1 and sys.argv[1] == "rollback"
accounts = json.loads((Path.home() / "Code-MoR-backups/rq03-stage5/accounts.json").read_text())
s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
for who in ("uw", "lead", "ro"):
    role_id = accounts[who]["roleId"]
    role = ops.get(f"/iam/v1/roles/{role_id}")
    before = sorted(role["permissionIds"])
    after = sorted(set(before) - {DIRECTORY_PERMISSION}) if ROLLBACK else sorted(set(before) | {DIRECTORY_PERMISSION})
    if after == before:
        print(who, role_id, "unchanged", before)
        continue
    reason = "REQ-03 stage 5 rollback: remove risk directory read" if ROLLBACK else "REQ-03 stage 5: underwriting roles also need the Risk directory permission (RISK_DECISION_CONFIG_READ)"
    result = ops.send("PUT", f"/iam/v1/roles/{role_id}", {"roleName": role["roleName"], "permissionIds": after, "expectedVersion": role["version"], "reason": reason})
    print(who, role_id, "version", role["version"], "->", result.get("version"), "before", before, "after", sorted(result["permissionIds"]))
