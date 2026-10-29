"""REQ-05 阶段 5 浏览器走查专用：按编码发起 ACC_REQ05_CHECK_FAILURE（新业务关联键），打印实例 ID，用于在首查完成前拍「检查中」。
凭据读 local-environment/e2e/secrets.env，不打印。用法：python3 -I start_checking.py <标签>"""
import site
import sys
import time

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
label = sys.argv[1] if len(sys.argv) > 1 else "run"
result = ops.send("POST", "/workflow/v1/instances", {"definitionCode": "ACC_REQ05_CHECK_FAILURE", "title": f"检查中 · {label}",
                                                     "businessKey": f"E2E:REQ05:CHECKING:{label}:{int(time.time())}",
                                                     "startValues": {}, "approvers": [], "executors": []})
print(result["objectId"], result.get("created"))
