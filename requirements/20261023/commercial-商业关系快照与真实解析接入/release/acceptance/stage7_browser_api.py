"""阶段 7 浏览器验收（B-1 / AC-12）的接口辅助：经运营入口 http://localhost:28080 → Gateway → ops-bff 读取状态、制造版本冲突、恢复原有效期。
写法同阶段 5 的 verification/local-e2e/stage5_browser_api.py，改为 e2e_http（E2E_TARGET=test，管理员取 test-environment/private/access.md）。
每个命令只输出一行 JSON（stage7_browser.mjs 读最后一行）；不打印口令与令牌。
用法：python3 -I stage7_browser_api.py state | snapshot | register-conflict | restore
  snapshot           记录撤销用 Client 的原资格（有效期起点、类型）到 stage7-browser-original.json（只在不存在时写）
  register-conflict  以页面持有的旧版本之外的当前版本登记一次，使页面的 expectedVersion 过期（Q6）
  restore            把撤销用 Client 的资格恢复为 snapshot 记下的有效期起点（APPROVED）；已一致时不写
"""
import json
import os
import sys
from pathlib import Path

sys.dont_write_bytecode = True
os.environ["E2E_TARGET"] = "test"
HERE = Path(__file__).resolve().parent
MOR_ROOT = Path(os.environ.get("MOR_ROOT", "/Users/kenny/Desktop/Workspace/Code-MoR"))
sys.path.insert(0, str(MOR_ROOT / "requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e"))

REVOKE_CLIENT = "236087245667409921"  # 已回填 INDIVIDUAL_KYC 的 ACTIVE Client，没有 commercial 合同，不是 AC-11 的 DEMO-CL-001
PAYFAC = "900000000001370001"
ORIGINAL = HERE / "stage7-browser-original.json"
REASON = "MORTEST_STAGE7_ACCEPTANCE"


def ops():
    import e2e_http as h
    s = h.secrets()
    return h.Operations(s["E2E_ADMIN_EMAIL"], s["E2E_ADMIN_PASSWORD"])


def inner(p):
    for key in ("entity", "partner", "localPartner"):
        if isinstance((p or {}).get(key), dict):
            return p[key]
    return p or {}


def client_qualification(session):
    page = session.get(f"/qualification-admin/v1/qualifications?roleType=CLIENT&roleId={REVOKE_CLIENT}") or {}
    rows = [r.get("qualification") or r for r in page.get("items") or []]
    return next((r for r in rows if r.get("qualificationType") == "INDIVIDUAL_KYC"), rows[0] if rows else {})


def decision(session, version, valid_from, note):
    import e2e_http as h
    try:
        body = session.send("POST", "/qualification-admin/v1/qualifications/manual-decisions", {
            "roleType": "CLIENT", "roleId": REVOKE_CLIENT, "qualificationType": "INDIVIDUAL_KYC", "validFrom": valid_from, "validTo": None,
            "reasonCode": REASON, "basisNote": note, "expectedVersion": version})
        return 200, body or {}
    except h.Failure as failure:
        try:
            return failure.status, json.loads(failure.body)
        except ValueError:
            return failure.status, {"raw": failure.body[:200]}


def state(session):
    q = client_qualification(session)
    entity = inner(session.get("/entity-admin/v1/operating-entities/AE001"))
    partner = inner(session.get(f"/entity-admin/v1/local-partners/{PAYFAC}"))
    return {"client": {k: q.get(k) for k in ("status", "effectiveStatus", "version", "validFrom", "validTo", "decisionSource")},
            "ae001": {k: entity.get(k) for k in ("acquiringQualification", "changeVersion")},
            "payfac": {k: partner.get(k) for k in ("roles", "changeVersion")}}


def main(command):
    session = ops()
    if command == "state":
        return state(session)
    if command == "snapshot":
        if not ORIGINAL.exists():
            q = client_qualification(session)
            ORIGINAL.write_text(json.dumps({k: q.get(k) for k in ("qualificationId", "status", "validFrom", "validTo", "version")}) + "\n")
        return json.loads(ORIGINAL.read_text())
    original = json.loads(ORIGINAL.read_text())
    if command == "register-conflict":
        status, body = decision(session, client_qualification(session).get("version"), original["validFrom"],
                                "阶段 7 浏览器验收：接口侧登记，制造版本冲突（Q6）")
        return {"status": status, "version": body.get("version"), "qualificationStatus": body.get("status"), "code": body.get("code")}
    if command == "restore":
        current = client_qualification(session)
        if current.get("status") == "APPROVED" and (current.get("validFrom") or "")[:19] == (original.get("validFrom") or "")[:19]:
            return {"status": "unchanged", "validFrom": current.get("validFrom")}
        status, body = decision(session, current.get("version"), original["validFrom"], "阶段 7 浏览器验收：恢复验收前的资格有效期")
        return {"status": status, "validFrom": body.get("validFrom"), "qualificationStatus": body.get("status"), "code": body.get("code")}
    raise SystemExit(__doc__)


if __name__ == "__main__":
    if len(sys.argv) != 2 or sys.argv[1] in ("-h", "--help"):
        print(__doc__)
        sys.exit(0)
    print(json.dumps(main(sys.argv[1]), ensure_ascii=False))
