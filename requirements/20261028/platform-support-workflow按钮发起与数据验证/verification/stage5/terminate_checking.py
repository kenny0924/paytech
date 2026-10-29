"""REQ-05 阶段 5 收尾：经运营 API 终止走查时为拍「检查中」发起的实例（业务关联键 E2E:REQ05:CHECKING:*，只读查库取 ID）。
凭据读 local-environment/e2e/secrets.env，不打印。用法：python3 -I terminate_checking.py"""
import site
import subprocess
import sys

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

QUERY = "/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/3e08b044-fee7-45d0-ba22-072a21cafc69/scratchpad/stage5/q.sh"
sql = "SELECT instance_id FROM t_workflow_instance WHERE business_key LIKE 'E2E:REQ05:CHECKING:%' AND status = 'RUNNING' ORDER BY instance_id"
ids = subprocess.run([QUERY, "mor_platform_v2e2e", "-N", "-e", sql], check=True, capture_output=True, text=True).stdout.split()
s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
for instance_id in ids:
    result = ops.send("POST", f"/workflow/v1/instances/{instance_id}/termination", {"reason": "REQ-05 阶段 5 走查演示实例清理（检查中截图用）"})
    print(instance_id, result.get("status"))
print("terminated", len(ids))
