"""阶段 5 本机联调（后端）：资格登记、commercial 直连解析、acquiring REAL 建单与库内核对。结果追加到 stage5-result.json。
用法：python3 stage5_backend.py <步骤>
  qualify-oe-lp        compliance Admin API 直连登记 AE001 ENTITY_UNDERWRITING 与 PayFac Partner PARTNER_DUE_DILIGENCE
  qualify-client <id>  compliance Admin API 直连登记 Client ORGANIZATION_KYB（运营入口不可用时的兜底）
  resolve <名称> <clientId> [期望状态]   commercial 直连解析并核对
  revoke-oe / reregister-oe            撤销 / 重新登记 AE001 资格
  checkout <名称> [期望状态]           acquiring 建单（直连，带 Gateway 头）并核对库
不打印口令与令牌。
"""
import sys
import time
import uuid
from datetime import datetime, timezone

import relsnap_http as h

ACTOR = 900000000001380999  # 合成运营操作者（直连 Owner 时代替 Gateway Actor）
MOR_CLIENT = "237161687952666625"
PSP_CLIENT = "900000000001380201"
PAYFAC = "900000000001370001"
PLATFORM_PARTY = "900000000001380900"
AE001_PARTY = "900000000001380102"


def now():
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%fZ")


def register(role_type, role_id, qualification_type, label):
    status, existing = h.private("compliance", "GET",
                                 f"/compliance-admin/v1/qualifications?roleType={role_type}&roleId={role_id}", h.actor_headers(ACTOR))
    items = (existing or {}).get("items") or []
    version = items[0].get("version") if items else None
    body = {"roleType": role_type, "roleId": role_id, "qualificationType": qualification_type,
            "validFrom": "2026-10-01T00:00:00Z", "validTo": None, "reasonCode": "STAGE5_LOCAL_E2E",
            "basisNote": "阶段 5 本机联调合成主体登记（" + label + "）", "expectedVersion": version}
    status, response = h.private("compliance", "POST", "/compliance-admin/v1/qualifications/manual-decisions",
                                 h.actor_headers(ACTOR), body)
    h.record(f"compliance admin register {label}", status == 200 and response.get("status") == "APPROVED",
             {"list": existing, "request": body, "status": status, "response": response},
             f"HTTP {status} status={response.get('status')} effective={response.get('effectiveStatus')} version={response.get('version')}")
    return response


def resolve(name, client_id, expect=200):
    request = {"clientId": client_id, "orderNo": "RELSNAP" + uuid.uuid4().hex[:12].upper(), "consumerCountryCode": "US",
               "currency": "USD", "locale": "en", "contextAsOfTime": now()}
    status, body = h.private("commercial", "POST", "/checkout-context/v1/resolutions",
                             {"X-Caller-Service": "acquiring-service", "X-Request-Id": "req-relsnap-" + uuid.uuid4().hex[:12]}, request)
    evidence = {"request": request, "status": status, "response": body}
    if expect != 200:
        return h.record(f"resolve {name}", status == expect, evidence, f"HTTP {status} code={body.get('code')}")
    snap = body.get("relationshipSnapshot") or {}
    roles = {b["role"]: b for b in snap.get("bindings") or []}
    seller = (body.get("legal") or {}).get("seller") or {}
    mode = snap.get("businessMode")
    problems = []
    if snap.get("fundFlow") != "THROUGH_CUSTODIAN":
        problems.append("fundFlow")
    if snap.get("effectiveModelVersion") != 0 or snap.get("snapshotModelVersion") != 1:
        problems.append("modelVersion")
    if seller.get("legalName") != "Relsnap Synthetic AE001 FZ-LLC":
        problems.append("seller")
    expected = {"CLIENT": ("CLIENT", client_id), "ACQUIRING_OE": ("OPERATING_ENTITY", "AE001"),
                "OE_PROVIDER": ("LOCAL_PARTNER", PAYFAC), "FUNDS_CUSTODIAN": ("OPERATING_ENTITY", "AE001"),
                "PLATFORM_FEE_PAYER": ("LOCAL_PARTNER", PAYFAC), "PLATFORM": ("PLATFORM", None)}
    if mode == "MOR":
        expected.update(SELLER=("OPERATING_ENTITY", "AE001"), TAX_RESPONSIBLE=("OPERATING_ENTITY", "AE001"))
    else:
        expected.update(SELLER=("CLIENT", client_id), TAX_RESPONSIBLE=("CLIENT", client_id), ACQUIRER_SUB_MERCHANT=("CLIENT", client_id))
    for role, (subject_type, subject_id) in expected.items():
        b = roles.get(role)
        if not b or b.get("subjectType") != subject_type or b.get("subjectId") != subject_id:
            problems.append(role)
    if set(roles) != set(expected):
        problems.append("roleSet=" + ",".join(sorted(roles)))
    if (roles.get("PLATFORM") or {}).get("partyId") != PLATFORM_PARTY or (roles.get("ACQUIRING_OE") or {}).get("partyId") != AE001_PARTY:
        problems.append("partyIds")
    ok = status == 200 and body.get("entityNo", body.get("entity", {}).get("entityNo") if isinstance(body.get("entity"), dict) else None) in (None, "AE001") and not problems
    return h.record(f"resolve {name}", ok, evidence,
                    f"HTTP {status} mode={mode} fundFlow={snap.get('fundFlow')} effective={snap.get('effectiveModelVersion')} "
                    f"snapshot={snap.get('snapshotModelVersion')} roles={len(roles)} problems={problems or 'none'}")


def oe_qualification():
    status, body = h.private("compliance", "GET", "/compliance-admin/v1/qualifications?roleType=OPERATING_ENTITY&roleId=AE001",
                             h.actor_headers(ACTOR))
    return (body.get("items") or [None])[0]


def revoke_oe():
    q = oe_qualification()
    body = {"expectedVersion": q["version"], "reasonCode": "STAGE5_LOCAL_E2E", "basisNote": "阶段 5 本机联调：验证撤销后解析落空"}
    status, response = h.private("compliance", "POST", f"/compliance-admin/v1/qualifications/{q['qualificationId']}/revocation",
                                 h.actor_headers(ACTOR), body)
    h.record("compliance admin revoke AE001", status == 200 and response.get("status") == "REVOKED",
             {"request": body, "status": status, "response": response}, f"HTTP {status} status={response.get('status')}")


def checkout(name, expect=201, client_id=MOR_CLIENT):
    client_order_id = "relsnap-" + name + "-" + uuid.uuid4().hex[:8]
    request = {"clientOrderId": client_order_id, "currency": "USD", "consumerCountryCode": "US",
               "items": [{"clientItemId": "line-1", "name": "Relsnap stage five item", "productTypeCode": "SERVICE",
                          "taxCategoryCode": "STANDARD", "quantity": "1", "unitAmount": "12.34"}]}
    headers = {"Accept": "application/json", "X-Request-Id": "req-relsnap-" + uuid.uuid4().hex[:12],
               "X-Gateway-Client-Id": client_id, "X-Gateway-Credential-Id": "1"}
    status, body = h.private("acquiring", "POST", "/client-gateway-api/v1/checkouts", headers, request)
    evidence = {"entry": "direct acquiring /commerce/client-gateway-api/v1/checkouts with Gateway headers X-Gateway-Client-Id / X-Gateway-Credential-Id",
                "request": request, "status": status, "response": body}
    if expect != 201:
        return h.record(f"checkout {name}", status == expect, evidence, f"HTTP {status} code={body.get('code')}")
    order_no = body.get("orderNo") or (body.get("order") or {}).get("orderNo")
    rows = {}
    order = h.sql("mor_acquiring_v2e2e", f"SELECT order_id, entity_no, entity_source_type, local_partner_id FROM t_commerce_order "
                  f"WHERE client_id={client_id} AND client_order_id='{client_order_id}'")
    rows["t_commerce_order"] = order
    problems = []
    if not order:
        problems.append("order missing")
    else:
        oid = order[0][0]
        rows["t_order_context"] = h.sql("mor_acquiring_v2e2e", "SELECT fund_flow, effective_relationship_model_version, "
                                        f"snapshot_model_version, partner_share_version_id, platform_charge_version_id FROM t_order_context WHERE order_id={oid}")
        rows["t_order_relationship_binding"] = h.sql("mor_acquiring_v2e2e", "SELECT role_code, subject_type, IFNULL(subject_id,'NULL'), "
                                                     f"IFNULL(party_id,'NULL'), snapshot_model_version FROM t_order_relationship_binding WHERE order_id={oid} ORDER BY role_code")
        rows["t_order_legal"] = h.sql("mor_acquiring_v2e2e", "SELECT public_legal_name, public_registration_number, public_tax_registration_number, "
                                      f"public_address_text, public_support_email, public_support_phone, partner_agreement_id, seller_disclosure_id FROM t_order_legal l "
                                      f"JOIN t_order_context c ON c.order_context_id = l.order_context_id WHERE c.order_id={oid}")
        if order[0][1:] != ["AE001", "PARTNER", PAYFAC]:
            problems.append("order source")
        ctx = rows["t_order_context"][0] if rows["t_order_context"] else []
        if ctx[:3] != ["THROUGH_CUSTODIAN", "0", "1"]:
            problems.append("context " + str(ctx[:3]))
        if len(rows["t_order_relationship_binding"]) != 8:
            problems.append(f"bindings={len(rows['t_order_relationship_binding'])}")
        legal = rows["t_order_legal"][0] if rows["t_order_legal"] else []
        if legal[:2] != ["Relsnap Synthetic AE001 FZ-LLC", "RELSNAP-REG-AE001"]:
            problems.append("legal " + str(legal[:2]))
    evidence["rows"] = rows
    return h.record(f"checkout {name}", status == 201 and not problems, evidence,
                    f"HTTP {status} orderNo={order_no} problems={problems or 'none'}")


if __name__ == "__main__":
    step = sys.argv[1]
    if step == "qualify-oe-lp":
        register("OPERATING_ENTITY", "AE001", "ENTITY_UNDERWRITING", "AE001")
        register("LOCAL_PARTNER", PAYFAC, "PARTNER_DUE_DILIGENCE", "PayFac Partner")
    elif step == "qualify-client":
        register("CLIENT", sys.argv[2], "ORGANIZATION_KYB", "Client " + sys.argv[2])
    elif step == "resolve":
        resolve(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 200)
    elif step == "revoke-oe":
        revoke_oe()
    elif step == "reregister-oe":
        register("OPERATING_ENTITY", "AE001", "ENTITY_UNDERWRITING", "AE001 again")
    elif step == "checkout":
        checkout(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 201)
    else:
        raise SystemExit(__doc__)
