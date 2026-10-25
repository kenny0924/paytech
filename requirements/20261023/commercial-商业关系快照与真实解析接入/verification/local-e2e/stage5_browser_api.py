"""阶段 5 浏览器验收（AC-12）的接口辅助：经运营入口（nginx 18444 → Gateway → ops-bff）读取种子状态、制造版本冲突、恢复原有效期。
每个命令只输出一行 JSON（stage5_browser.mjs 读最后一行）；不打印口令与令牌。
用法：python3 stage5_browser_api.py state | register-psp | restore-psp
"""
import json
import sys

import relsnap_http as h
from stage5_ops import PSP_CLIENT, PAYFAC, inner, qualification_of

ORIGINAL_VALID_FROM = "2026-10-01T00:00:00Z"  # 阶段 5 后端联调登记 PSP Client 时使用的有效期起点


def psp(ops):
    row = qualification_of(ops, "CLIENT", PSP_CLIENT) or {}
    return row.get("qualification") or row  # B8 行为 {qualification, subjectName}


def decision(ops, version, valid_from):
    return ops.call("POST", "/qualification-admin/v1/qualifications/manual-decisions", {
        "roleType": "CLIENT", "roleId": PSP_CLIENT, "qualificationType": "ORGANIZATION_KYB", "validFrom": valid_from, "validTo": None,
        "reasonCode": "STAGE5_BROWSER_E2E", "basisNote": "阶段 5 浏览器验收：接口侧登记（制造版本冲突 / 恢复原有效期）",
        "expectedVersion": version})


def state(ops):
    q = psp(ops)
    _, entity = ops.call("GET", "/entity-admin/v1/operating-entities/AE001")
    _, partner = ops.call("GET", f"/entity-admin/v1/local-partners/{PAYFAC}")
    return {"psp": {k: q.get(k) for k in ("status", "effectiveStatus", "version", "validFrom", "validTo")},
            "ae001": {k: inner(entity).get(k) for k in ("acquiringQualification", "changeVersion")},
            "payfac": {k: inner(partner).get(k) for k in ("roles", "changeVersion")}}


def main(command):
    ops = h.Operations()
    if command == "state":
        return state(ops)
    if command == "register-psp":
        status, body = decision(ops, psp(ops).get("version"), ORIGINAL_VALID_FROM)
        return {"status": status, "version": body.get("version"), "qualificationStatus": body.get("status"), "code": body.get("code")}
    if command == "restore-psp":
        current = psp(ops)
        if (current.get("validFrom") or "").startswith(ORIGINAL_VALID_FROM[:19]) and current.get("status") == "APPROVED":
            return {"status": "unchanged", "validFrom": current.get("validFrom")}
        status, body = decision(ops, current.get("version"), ORIGINAL_VALID_FROM)
        return {"status": status, "validFrom": body.get("validFrom"), "qualificationStatus": body.get("status"), "code": body.get("code"),
                "detail": body.get("detail")}
    raise SystemExit(f"unknown command {command}")


if __name__ == "__main__":
    print(json.dumps(main(sys.argv[1]), ensure_ascii=False))
