"""REQ-01 阶段 5：经本机运营入口（Nginx 18444 → Gateway → ops-bff → commercial）为演示合同准备四种执行状态，并核对接口回执。

合同骨架由 10-commercial-gate-seed.sql 写入；本脚本只调用运营 API（真实门禁链路）：
  1 新增 CARD（USD）、TOKEN_STORAGE（功能项，无条件）、APPLE_PAY（USD）、GOOGLE_PAY（USD）→ 补偿任务提交后核对：CARD / TOKEN_STORAGE 已开通，
    APPLE_PAY / GOOGLE_PAY 等前置（价格未覆盖）
  2 编辑 CARD 增加 SAR → 立即核对 → CARD 前置失效暂停（价格未覆盖）
  3 暂停 GOOGLE_PAY（手动暂停）
用法：python3 -I stage5_setup.py；结果写 stage5-setup-result.json。凭据读 local-environment/e2e/secrets.env，不打印。
"""
import json
import site
import sys
import time
from pathlib import Path

sys.path.append(site.getusersitepackages())
sys.path.insert(0, "/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e")
import e2e_http  # noqa: E402

AGREEMENT = "900000000001390001"
BASE = f"/commercial-admin/v1/agreements/{AGREEMENT}"
HERE = Path(__file__).resolve().parent
s = e2e_http.secrets()
ops = e2e_http.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])
log = []


def detail():
    return ops.get(BASE)["agreement"]


def by_code(agreement):
    return {c["capabilityCode"]: c for c in agreement["capabilities"]}


def versioned(method, path, body):
    """写命令带最新合同版本；门禁补偿任务在提交后推进版本，409 时重读版本重试（最多 5 次）。"""
    for attempt in range(5):
        time.sleep(2)
        try:
            return ops.send(method, path, {**body, "expectedChangeVersion": detail()["changeVersion"]})
        except e2e_http.Failure as failure:
            if "409" not in str(failure) or attempt == 4:
                raise


def add(code, currencies):
    body = {"entityNo": "AE001", "capabilityCode": code,
            "effectiveFrom": "2026-10-20T00:00:00Z",
            "conditions": [{"dimensionCode": "CURRENCY", "values": currencies}] if currencies else [], "require3ds": False}
    reply = versioned("POST", BASE + "/capabilities", body)
    log.append({"step": f"add {code}", "reply": reply})
    return reply


def wait_settled(codes, timeout=40):
    for _ in range(timeout):
        caps = by_code(detail())
        if all(caps.get(code, {}).get("executionStatus") not in (None, "PENDING_PREREQUISITES") or code in ("APPLE_PAY", "GOOGLE_PAY")
               for code in codes):
            return caps
        time.sleep(1)
    return by_code(detail())


existing = by_code(detail())
for code in ("CARD", "TOKEN_STORAGE", "APPLE_PAY", "GOOGLE_PAY"):
    if code not in existing:
        add(code, [] if code == "TOKEN_STORAGE" else ["USD"])
time.sleep(3)
caps = wait_settled(["CARD", "TOKEN_STORAGE"])
log.append({"step": "after add", "executionStatus": {k: v["executionStatus"] for k, v in caps.items()}})

card = caps["CARD"]
if "SAR" not in json.dumps(card["conditions"]):
    body = {"status": "ACTIVE", "effectiveFrom": card["effectiveFrom"],
            "conditions": [{"dimensionCode": "CURRENCY", "values": ["USD", "SAR"]}], "require3ds": False}
    log.append({"step": "edit CARD +SAR", "reply": versioned("PUT", f"{BASE}/capabilities/{card['agreementCapabilityId']}", body)})
check = ops.send("POST", BASE + "/prerequisite-checks", None)
log.append({"step": "prerequisite-checks", "reply": check})

caps = by_code(detail())
google = caps["GOOGLE_PAY"]
if google["executionStatus"] != "SUSPENDED_MANUALLY":
    log.append({"step": "suspend GOOGLE_PAY", "reply": versioned("POST", f"{BASE}/capabilities/{google['agreementCapabilityId']}/suspension",
                                                                 {"reason": "Client 要求暂停 Google Pay，等待商户域名续期"})})
final = detail()
log.append({"step": "final", "changeVersion": final["changeVersion"],
            "capabilities": {c["capabilityCode"]: [c["agreementCapabilityId"], c["status"], c["executionStatus"]] for c in final["capabilities"]}})
(HERE / "stage5-setup-result.json").write_text(json.dumps(log, ensure_ascii=False, indent=2))
print(json.dumps(log[-1], ensure_ascii=False, indent=2))
