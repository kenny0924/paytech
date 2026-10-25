"""阶段 5 本机联调：运营入口 B1—B11 冒烟（nginx 18444 → Gateway → operations-console-bff → client-master / compliance），
穿插 commercial 直连解析验证资格与收单资质对解析的影响（PSP Client 900000000001380201）。结果追加到 stage5-result.json。
用法：python3 stage5_ops.py；运营管理员口令只从 local-environment/e2e/secrets.env 读入，不打印令牌。
"""
import relsnap_http as h
from stage5_backend import PAYFAC, PSP_CLIENT, MOR_CLIENT, resolve

REASON = "阶段 5 本机联调（运营入口冒烟）"


def check(ops, name, method, path, body=None, expect=200, summary=None):
    status, payload = ops.call(method, path, body)
    note = f"HTTP {status}" + (f" {summary(payload)}" if summary and status == expect else f" code={payload.get('code')}")
    h.record(name, status == expect, {"method": method, "path": "/api/operations" + path, "request": body, "status": status,
                                      "response": payload}, note)
    return payload


def qualification_of(ops, role_type, role_id):
    _, page = ops.call("GET", f"/qualification-admin/v1/qualifications?roleType={role_type}&roleId={role_id}")
    return ((page or {}).get("items") or [None])[0]


def inner(p):
    """BFF 聚合详情把主档放在 entity / partner 下；转发写接口为 client-master 详情原样。"""
    for key in ("entity", "partner", "localPartner"):
        if isinstance((p or {}).get(key), dict):
            return p[key]
    return p or {}


def register_body(version):
    return {"roleType": "CLIENT", "roleId": PSP_CLIENT, "qualificationType": "ORGANIZATION_KYB", "validFrom": "2026-10-01T00:00:00Z",
            "validTo": None, "reasonCode": "STAGE5_LOCAL_E2E", "basisNote": "阶段 5 本机联调：经运营入口登记 PSP 合成 Client",
            "expectedVersion": version}


def main(part="all"):
    ops = h.Operations()
    q = "/qualification-admin/v1"
    e = "/entity-admin/v1"
    if part in ("all", "qualification"):
        qualification_part(ops, q)
    if part in ("all", "entity"):
        entity_part(ops, e)


def qualification_part(ops, q):
    check(ops, "B7 subject lookup CLIENT", "GET", f"{q}/subjects?roleType=CLIENT&subjectRef={PSP_CLIENT}",
          summary=lambda p: f"displayName={p.get('displayName')} status={p.get('status')}")
    check(ops, "B7 subject lookup unknown CLIENT", "GET", f"{q}/subjects?roleType=CLIENT&subjectRef=900000000001389999", expect=404)
    existing = qualification_of(ops, "CLIENT", PSP_CLIENT)
    if existing and existing.get("status") == "APPROVED":
        check(ops, "B11 revoke PSP client (reset)", "POST", f"{q}/qualifications/{existing['qualificationId']}/revocation",
              {"expectedVersion": existing["version"], "reasonCode": "STAGE5_LOCAL_E2E", "basisNote": REASON})
        existing = qualification_of(ops, "CLIENT", PSP_CLIENT)
    resolve("psp-client-not-qualified", PSP_CLIENT, 422)
    created = check(ops, "B10 manual decision PSP client", "POST", f"{q}/qualifications/manual-decisions",
                    register_body(existing.get("version") if existing else None),
                    summary=lambda p: f"status={p.get('status')} version={p.get('version')}")
    resolve("psp-c2", PSP_CLIENT)
    revoked = check(ops, "B11 revoke PSP client", "POST", f"{q}/qualifications/{created['qualificationId']}/revocation",
                    {"expectedVersion": created["version"], "reasonCode": "STAGE5_LOCAL_E2E", "basisNote": REASON},
                    summary=lambda p: f"status={p.get('status')} version={p.get('version')}")
    resolve("psp-client-revoked", PSP_CLIENT, 422)
    check(ops, "B10 stale version conflict", "POST", f"{q}/qualifications/manual-decisions", register_body(created["version"]), expect=409)
    check(ops, "B10 re-register PSP client", "POST", f"{q}/qualifications/manual-decisions", register_body(revoked["version"]),
          summary=lambda p: f"status={p.get('status')} version={p.get('version')}")
    check(ops, "B9 qualification history", "GET", f"{q}/qualifications/{created['qualificationId']}/history",
          summary=lambda p: f"items={len(p.get('items') or p.get('changes') or [])}")
    check(ops, "B8 qualification list", "GET", f"{q}/qualifications?page=1&size=20",
          summary=lambda p: f"total={p.get('total')} items={len(p.get('items') or [])}")


def entity_part(ops, e):
    check(ops, "B1 operating entity list", "GET", f"{e}/operating-entities?sourceType=PARTNER&page=1&size=20",
          summary=lambda p: f"total={p.get('total')} first={((p.get('items') or [{}])[0]).get('entityNo')}")
    detail = check(ops, "B2 operating entity detail", "GET", f"{e}/operating-entities/AE001",
                   summary=lambda p: f"qualification={inner(p).get('acquiringQualification')} changeVersion={inner(p).get('changeVersion')}")
    changed = check(ops, "B3 acquiring qualification -> MERCHANT_RESALE_APPROVED", "PUT", f"{e}/operating-entities/AE001/acquiring-qualification",
                    {"acquiringQualification": "MERCHANT_RESALE_APPROVED", "expectedChangeVersion": inner(detail)["changeVersion"], "reason": REASON},
                    summary=lambda p: f"qualification={inner(p).get('acquiringQualification')} changeVersion={inner(p).get('changeVersion')}")
    resolve("psp-under-resale-approved", PSP_CLIENT, 422)
    resolve("mor-under-resale-approved", MOR_CLIENT)
    check(ops, "B3 stale changeVersion conflict", "PUT", f"{e}/operating-entities/AE001/acquiring-qualification",
          {"acquiringQualification": "PAYFAC", "expectedChangeVersion": inner(detail)["changeVersion"], "reason": REASON}, expect=409)
    check(ops, "B3 acquiring qualification -> PAYFAC", "PUT", f"{e}/operating-entities/AE001/acquiring-qualification",
          {"acquiringQualification": "PAYFAC", "expectedChangeVersion": inner(changed)["changeVersion"], "reason": REASON},
          summary=lambda p: f"qualification={inner(p).get('acquiringQualification')} changeVersion={inner(p).get('changeVersion')}")
    resolve("psp-c2-after-payfac", PSP_CLIENT)

    check(ops, "B4 local partner list", "GET", f"{e}/local-partners?role=OE_PROVIDER&page=1&size=20",
          summary=lambda p: f"total={p.get('total')}")
    partner = check(ops, "B5 local partner detail", "GET", f"{e}/local-partners/{PAYFAC}",
                    summary=lambda p: f"roles={inner(p).get('roles')} changeVersion={inner(p).get('changeVersion')}")
    check(ops, "B6 remove OE_PROVIDER while providing rejected", "PUT", f"{e}/local-partners/{PAYFAC}/roles",
          {"roles": ["REFERRER"], "expectedChangeVersion": inner(partner)["changeVersion"], "reason": REASON}, expect=422)
    added = check(ops, "B6 roles -> OE_PROVIDER+REFERRER", "PUT", f"{e}/local-partners/{PAYFAC}/roles",
                  {"roles": ["OE_PROVIDER", "REFERRER"], "expectedChangeVersion": inner(partner)["changeVersion"], "reason": REASON},
                  summary=lambda p: f"roles={inner(p).get('roles')} changeVersion={inner(p).get('changeVersion')}")
    check(ops, "B6 roles -> OE_PROVIDER", "PUT", f"{e}/local-partners/{PAYFAC}/roles",
          {"roles": ["OE_PROVIDER"], "expectedChangeVersion": inner(added)["changeVersion"], "reason": REASON},
          summary=lambda p: f"roles={inner(p).get('roles')} changeVersion={inner(p).get('changeVersion')}")
    resolve("mor-c1-after-ops-changes", MOR_CLIENT)


if __name__ == "__main__":
    import sys
    main(sys.argv[1] if len(sys.argv) > 1 else "all")
